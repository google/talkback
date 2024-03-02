/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback;

import static android.content.Context.RECEIVER_EXPORTED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Display;
import androidx.annotation.IntDef;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.talkback.TalkBackService.ProximitySensorListener;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.controller.TelevisionNavigationController;
import com.google.android.accessibility.talkback.monitor.CallStateMonitor;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.monitor.DisplayMonitor;
import com.google.android.accessibility.utils.monitor.DisplayMonitor.DisplayStateChangedListener;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO: Refactor this class into two separate receivers
// with listener interfaces. This will remove the need to hold dependencies
// and call into other classes.
/** {@link BroadcastReceiver} for receiving updates for our context - device state */
public class RingerModeAndScreenMonitor extends BroadcastReceiver
    implements DisplayStateChangedListener {

  private static final String TAG = "RingerModeAndScreenMon";

  /** The intent filter to match phone and screen state changes. */
  private static final IntentFilter STATE_CHANGE_FILTER = new IntentFilter();

  /** IDs of time feedback formats in advanced settings. */
  @IntDef({
    TIME_FEEDBACK_FORMAT_UNDEFINED,
    TIME_FEEDBACK_FORMAT_12_HOURS,
    TIME_FEEDBACK_FORMAT_24_HOURS
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface TimeFeedbackFormat {}

  public static final int TIME_FEEDBACK_FORMAT_UNDEFINED = 0;
  public static final int TIME_FEEDBACK_FORMAT_12_HOURS = 1;
  public static final int TIME_FEEDBACK_FORMAT_24_HOURS = 2;

  static {
    STATE_CHANGE_FILTER.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
    STATE_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_ON);
    STATE_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_OFF);
    STATE_CHANGE_FILTER.addAction(Intent.ACTION_USER_PRESENT);
  }

  private final TalkBackService service;
  private final Pipeline.FeedbackReturner pipeline;
  private final ProximitySensorListener proximitySensorListener;
  private final TelevisionNavigationController televisionNavigationController;
  private final AudioManager audioManager;
  private final ListMenuManager menuManager;
  private final CallStateMonitor callStateMonitor;
  private final Set<DialogInterface> openDialogs = new HashSet<>();
  private final boolean isWatch;

  /** The current ringer mode. */
  private int ringerMode = AudioManager.RINGER_MODE_NORMAL;

  /** The time format of time feedback. */
  @TimeFeedbackFormat
  private int timeFormat = RingerModeAndScreenMonitor.TIME_FEEDBACK_FORMAT_UNDEFINED;

  // The system will send a screen on or screen off broadcast whenever the interactive state of the
  // device changes.
  private boolean isInteractive;
  // Records the default display state.
  private boolean defaultDisplayOn;
  private final DisplayMonitor displayMonitor;

  /** The list containing screen changed listeners from other function callback. */
  private final List<ScreenChangedListener> screenChangedListeners = new CopyOnWriteArrayList<>();

  /** The list containing device unlocked listeners from other function callback. */
  private final List<DeviceUnlockedListener> deviceUnlockedListeners = new CopyOnWriteArrayList<>();

  private boolean monitoring = false;

  private final ExecutorService executor;

  @Override
  public void onDisplayStateChanged(boolean displayOn) {
    final boolean preDisplayOn = defaultDisplayOn;
    defaultDisplayOn = displayOn;
    if (defaultDisplayOn && !preDisplayOn) {
      // When the display turns on, we announce the current time and the current ringer
      // state when phone is idle.
      speakForDisplayOn(EVENT_ID_UNTRACKED);
    }
  }

  /** Creates a new instance. */
  public RingerModeAndScreenMonitor(
      ListMenuManager menuManager,
      Pipeline.FeedbackReturner pipeline,
      ProximitySensorListener proximitySensorListener,
      CallStateMonitor callStateMonitor,
      DisplayMonitor displayMonitor,
      TalkBackService service) {
    if (menuManager == null) {
      throw new IllegalStateException();
    }
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    if (proximitySensorListener == null) {
      throw new IllegalStateException();
    }

    this.service = service;
    this.menuManager = menuManager;
    this.pipeline = pipeline;
    this.proximitySensorListener = proximitySensorListener;
    this.callStateMonitor = callStateMonitor;
    this.displayMonitor = displayMonitor;
    televisionNavigationController = service.getTelevisionNavigationController();

    audioManager = (AudioManager) service.getSystemService(Service.AUDIO_SERVICE);
    // noinspection deprecation
    isInteractive =
        ((PowerManager) service.getSystemService(Context.POWER_SERVICE)).isInteractive();
    isWatch = FormFactorUtils.getInstance().isAndroidWear();
    executor = Executors.newSingleThreadExecutor();
  }

  public void startMonitoring(Context context) {
    if (!monitoring) {
      ContextCompat.registerReceiver(context, this, STATE_CHANGE_FILTER, RECEIVER_EXPORTED);
      updateScreenState();
      displayMonitor.addDisplayStateChangedListener(this);
      monitoring = true;
    }
  }

  public void stopMonitoring(Context context) {
    if (monitoring) {
      context.unregisterReceiver(this);
      displayMonitor.removeDisplayStateChangedListener(this);
      monitoring = false;
    }
  }

  public void clearListeners() {
    screenChangedListeners.clear();
    deviceUnlockedListeners.clear();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!TalkBackService.isServiceActive()) {
      return;
    }

    String action = intent.getAction();
    if (action == null) {
      return;
    }

    EventId eventId = EVENT_ID_UNTRACKED; // Frequently not user-initiated.

    switch (action) {
      case AudioManager.RINGER_MODE_CHANGED_ACTION:
        handleRingerModeChanged(
            intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL));
        break;
      case Intent.ACTION_SCREEN_ON:
        isInteractive = true;
        handleScreenOn(eventId);
        break;
      case Intent.ACTION_SCREEN_OFF:
        isInteractive = false;
        handleScreenOff(eventId);
        break;
      case Intent.ACTION_USER_PRESENT:
        handleDeviceUnlocked(eventId);
        break;
    }
  }

  public void updateScreenState() {
    // noinspection deprecation
    isInteractive =
        ((PowerManager) service.getSystemService(Context.POWER_SERVICE)).isInteractive();
  }

  public boolean isInteractive() {
    return isInteractive;
  }

  /**
   * The state of the default {@link Display} is on or not.
   *
   * <p>Note: The value returned by {@link PowerManager#isInteractive()} only indicates whether the
   * device is in an interactive state which may have nothing to do with the screen being on or off.
   * To determine the actual state of the screen, use {@link Display#getState()}.
   */
  public boolean isDefaultDisplayOn() {
    return defaultDisplayOn;
  }

  /** Handles when the device is unlocked. Just speaks "unlocked." */
  private void handleDeviceUnlocked(EventId eventId) {
    if (isIdle()) {
      if (isWatch) {
        // REFERTO: Use chime instead of speech on watches, since it happens on every
        // screen wake whether or not the screen lock is enabled.
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.volume_beep));
      } else {
        final String ttsText = service.getString(R.string.value_device_unlocked);
        SpeakOptions speakOptions =
            SpeakOptions.create()
                .setQueueMode(
                    SpeechController.QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH)
                .setFlags(FeedbackItem.FLAG_SKIP_DUPLICATE);
        pipeline.returnFeedback(eventId, Feedback.speech(ttsText, speakOptions));
      }
    }
    for (DeviceUnlockedListener deviceUnlockedListener : deviceUnlockedListeners) {
      deviceUnlockedListener.onDeviceUnlocked();
    }
  }

  private void handleScreenOffInBackgroundThread(EventId eventId) {
    final SpannableStringBuilder ttsText =
        new SpannableStringBuilder(service.getString(R.string.value_screen_off));
    // Only announce ringer state if we're not in a call.
    if (isIdle()) {
      appendRingerStateAnnouncement(ttsText);
    }

    // Always reset the television remote mode to the standard (navigate) mode on screen off.
    if (televisionNavigationController != null) {
      televisionNavigationController.resetToNavigateMode();
    }

    // Stop queued speech and events. AccessibilityEventProcessor will block new events.
    service.clearQueues();

    // Speak "screen off".
    // REFERTO: Do not have any screen off message and any chime for Android Wear.
    if (!isWatch) {
      SpeakOptions speakOptions =
          SpeakOptions.create()
              .setQueueMode(SpeechController.QUEUE_MODE_INTERRUPT)
              .setFlags(FeedbackItem.FLAG_NO_HISTORY);
      final float volume;
      if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
        // Gets TalkBack's default earcon volume from FeedbackController.
        final float talkbackVolume = getStreamVolume(FeedbackController.DEFAULT_STREAM);
        if ((talkbackVolume > 0)
            && (audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn())) {
          // TODO: refactor the following lines.
          // Play the ringer beep on the default (music) stream to avoid
          // issues with ringer audio (e.g. no speech on ICS and
          // interruption of music on JB). Adjust playback volume to
          // compensate for music volume.
          final float ringVolume = getStreamVolume(AudioManager.STREAM_RING);
          volume = Math.min(1.0f, (ringVolume / talkbackVolume));
        } else {
          volume = 1.0f;
        }
        // Normally we'll play the volume beep on the ring stream.
        pipeline.returnFeedback(
            eventId,
            Feedback.part()
                .setSound(Feedback.Sound.create(R.raw.volume_beep, 1.0f, volume))
                .speech(ttsText, speakOptions));
      } else {
        pipeline.returnFeedback(eventId, Feedback.speech(ttsText, speakOptions));
      }
    }
  }

  /**
   * Handles when the screen is turned off. Announces "screen off" and suspends the proximity
   * sensor.
   */
  @SuppressWarnings("deprecation")
  private void handleScreenOff(EventId eventId) {
    proximitySensorListener.setScreenIsOn(false);
    menuManager.dismissAll();

    // Iterate over a copy because dialog dismiss handlers might try to unregister dialogs.
    List<DialogInterface> openDialogsCopy = new ArrayList<>(openDialogs);
    for (DialogInterface dialog : openDialogsCopy) {
      dialog.cancel();
    }
    openDialogs.clear();

    for (ScreenChangedListener screenChangedListener : screenChangedListeners) {
      if (screenChangedListener != null) {
        screenChangedListener.onScreenChanged(isInteractive, eventId);
      }
    }

    // Move non-ui tasks to background thread
    executor.execute(() -> handleScreenOffInBackgroundThread(eventId));
  }

  /** Handles when the screen is interactive. */
  private void handleScreenOn(EventId eventId) {
    // TODO: This doesn't look right. Should probably be using a listener.
    proximitySensorListener.setScreenIsOn(true);

    for (ScreenChangedListener screenChangedListener : screenChangedListeners) {
      if (screenChangedListener != null) {
        screenChangedListener.onScreenChanged(isInteractive, eventId);
      }
    }
  }

  /**
   * Handles when the display is turned on. Announces the current time and the current ringer state
   * when phone is idle.
   */
  private void speakForDisplayOn(EventId eventId) {
    final SpannableStringBuilder ttsText = new SpannableStringBuilder();

    if (isIdle()) {
      // Need the old version to support version older than JB_MR1
      // noinspection deprecation
      if (Settings.Secure.getInt(
              service.getContentResolver(), Settings.Secure.DEVICE_PROVISIONED, 0)
          != 0) {
        appendCurrentTimeAnnouncement(ttsText);
      } else {
        // Device is not ready, just speak screen on
        ttsText.append(service.getString(R.string.value_screen_on));
      }
    }

    SpeakOptions speakOptions =
        SpeakOptions.create()
            // Uses QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH so that time announcement does not
            // interrupt DeviceUnlocked speech and REFERTO won't be interrupted by the
            // following selected text.
            .setQueueMode(SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
            .setFlags(FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE);

    pipeline.returnFeedback(eventId, Feedback.speech(ttsText, speakOptions));
  }

  /**
   * Return current phone's call state is idle or not.
   *
   * @return true when phone is idle
   */
  private boolean isIdle() {
    return callStateMonitor != null
        && callStateMonitor.getCurrentCallState() == TelephonyManager.CALL_STATE_IDLE;
  }

  /** Handles when the ringer mode (ex. volume) changes. Announces the current ringer state. */
  private void handleRingerModeChanged(int ringerMode) {
    this.ringerMode = ringerMode;
  }

  /**
   * Appends the current time announcement to a {@link StringBuilder}.
   *
   * @param builder The string to append to.
   */
  @SuppressWarnings("deprecation")
  private void appendCurrentTimeAnnouncement(SpannableStringBuilder builder) {
    int timeFlags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;

    switch (timeFormat) {
      case TIME_FEEDBACK_FORMAT_12_HOURS:
        timeFlags |= DateUtils.FORMAT_12HOUR;
        break;
      case TIME_FEEDBACK_FORMAT_24_HOURS:
        timeFlags |= DateUtils.FORMAT_24HOUR;
        break;
      default:
        if (DateFormat.is24HourFormat(service)) {
          timeFlags |= DateUtils.FORMAT_24HOUR;
        } else {
          timeFlags |= DateUtils.FORMAT_12HOUR;
        }
        break;
    }

    final CharSequence dateTime =
        DateUtils.formatDateTime(service, System.currentTimeMillis(), timeFlags);

    StringBuilderUtils.appendWithSeparator(builder, dateTime);
  }

  /**
   * Appends the ringer state announcement to a {@link StringBuilder}.
   *
   * @param builder The string to append to.
   */
  private void appendRingerStateAnnouncement(SpannableStringBuilder builder) {
    if (callStateMonitor == null) {
      return;
    }

    final String announcement;

    switch (ringerMode) {
      case AudioManager.RINGER_MODE_SILENT:
        announcement = service.getString(R.string.value_ringer_silent);
        break;
      case AudioManager.RINGER_MODE_VIBRATE:
        announcement = service.getString(R.string.value_ringer_vibrate);
        break;
      case AudioManager.RINGER_MODE_NORMAL:
        return;
      default:
        LogUtils.e(TAG, "Unknown ringer mode: %d", ringerMode);
        return;
    }

    StringBuilderUtils.appendWithSeparator(builder, announcement);
  }

  /**
   * Returns the volume a stream as a fraction of its maximum volume.
   *
   * @param streamType The stream type for which to return the volume.
   * @return The stream volume as a fraction of its maximum volume.
   */
  private float getStreamVolume(int streamType) {
    final int currentVolume = audioManager.getStreamVolume(streamType);
    final int maxVolume = audioManager.getStreamMaxVolume(streamType);
    return (currentVolume / (float) maxVolume);
  }

  /** Registers a dialog to be auto-cancelled when the screen turns off. */
  public void registerDialog(DialogInterface dialog) {
    openDialogs.add(dialog);
  }

  /** Removes a dialog from the list of dialogs to be auto-cancelled. */
  public void unregisterDialog(DialogInterface dialog) {
    openDialogs.remove(dialog);
  }

  /** Listener interface to callback when screen on/off. */
  public interface ScreenChangedListener {
    void onScreenChanged(boolean isInteractive, EventId eventId);
  }

  /** Add listener which will be called when received screen on/off intent. */
  public void addScreenChangedListener(ScreenChangedListener listener) {
    screenChangedListeners.add(listener);
  }

  /** Listener interface to callback when device is unlocked. */
  public interface DeviceUnlockedListener {
    void onDeviceUnlocked();
  }

  /** Adds listener which will be called when device is unlocked. */
  public void addDeviceUnlockedListener(DeviceUnlockedListener listener) {
    deviceUnlockedListeners.add(listener);
  }

  /** Sets time feedback format. */
  public void setTimeFeedbackFormat(@TimeFeedbackFormat int value) {
    timeFormat = value;
  }

  /** Converts the time feedback preference string value to the variable. */
  @TimeFeedbackFormat
  public static int prefValueToTimeFeedbackFormat(Resources resources, String value) {
    if (TextUtils.equals(
        value, resources.getString(R.string.pref_time_feedback_format_values_12_hour))) {
      return RingerModeAndScreenMonitor.TIME_FEEDBACK_FORMAT_12_HOURS;
    } else if (TextUtils.equals(
        value, resources.getString(R.string.pref_time_feedback_format_values_24_hour))) {
      return RingerModeAndScreenMonitor.TIME_FEEDBACK_FORMAT_24_HOURS;
    }

    return RingerModeAndScreenMonitor.TIME_FEEDBACK_FORMAT_UNDEFINED;
  }
}
