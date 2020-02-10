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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import com.google.android.accessibility.talkback.contextmenu.MenuManager;
import com.google.android.accessibility.talkback.controller.TelevisionNavigationController;
import com.google.android.accessibility.talkback.features.ProximitySensorListener;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: Refactor this class into two separate receivers
// with listener interfaces. This will remove the need to hold dependencies
// and call into other classes.
/** {@link BroadcastReceiver} for receiving updates for our context - device state */
public class RingerModeAndScreenMonitor extends BroadcastReceiver {

  private static final String TAG = "RingerModeAndScreenMon";

  /** The intent filter to match phone and screen state changes. */
  private static final IntentFilter STATE_CHANGE_FILTER = new IntentFilter();

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
  private final MenuManager menuManager;
  private final TelephonyManager telephonyManager;
  private final Set<DialogInterface> openDialogs = new HashSet<>();
  private final boolean isWatch;

  /** The current ringer mode. */
  private int ringerMode = AudioManager.RINGER_MODE_NORMAL;

  private boolean isScreenOn;

  /** The list containing screen changed listeners from other function callback. */
  private List<ScreenChangedListener> screenChangedListeners = new ArrayList<>();

  /** Creates a new instance. */
  public RingerModeAndScreenMonitor(
      MenuManager menuManager,
      Pipeline.FeedbackReturner pipeline,
      ProximitySensorListener proximitySensorListener,
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
    televisionNavigationController = service.getTelevisionNavigationController();

    audioManager = (AudioManager) service.getSystemService(Service.AUDIO_SERVICE);
    telephonyManager = (TelephonyManager) service.getSystemService(Service.TELEPHONY_SERVICE);
    // noinspection deprecation
    isScreenOn = ((PowerManager) service.getSystemService(Context.POWER_SERVICE)).isScreenOn();
    isWatch = FeatureSupport.isWatch(service);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!TalkBackService.isServiceActive()) return;

    String action = intent.getAction();
    if (action == null) return;

    EventId eventId = EVENT_ID_UNTRACKED; // Frequently not user-initiated.

    switch (action) {
      case AudioManager.RINGER_MODE_CHANGED_ACTION:
        handleRingerModeChanged(
            intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL));
        break;
      case Intent.ACTION_SCREEN_ON:
        isScreenOn = true;
        handleScreenOn(eventId);
        break;
      case Intent.ACTION_SCREEN_OFF:
        isScreenOn = false;
        handleScreenOff(eventId);
        break;
      case Intent.ACTION_USER_PRESENT:
        handleDeviceUnlocked(eventId);
        break;
    }
  }

  public void updateScreenState() {
    // noinspection deprecation
    isScreenOn = ((PowerManager) service.getSystemService(Context.POWER_SERVICE)).isScreenOn();
  }

  public boolean isScreenOn() {
    return isScreenOn;
  }

  public IntentFilter getFilter() {
    return STATE_CHANGE_FILTER;
  }

  /** Handles when the device is unlocked. Just speaks "unlocked." */
  private void handleDeviceUnlocked(EventId eventId) {
    if (isIdle()) {
      if (isWatch) {
        // : Use chime instead of speech on watches, since it happens on every screen wake
        // whether or not the screen lock is enabled.
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.volume_beep));
      } else {
        final String ttsText = service.getString(R.string.value_device_unlocked);
        SpeakOptions speakOptions =
            SpeakOptions.create().setQueueMode(SpeechController.QUEUE_MODE_INTERRUPT);
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
    // : Do not have any screen off message and any chime for Android Wear.
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
            eventId, Feedback.sound(R.raw.volume_beep, 1.0f, volume).speech(ttsText, speakOptions));
      } else {
        pipeline.returnFeedback(eventId, Feedback.speech(ttsText, speakOptions));
      }
    }

    for (ScreenChangedListener screenChangedListener : screenChangedListeners) {
      screenChangedListener.onScreenChanged(isScreenOn, eventId);
    }
  }

  /**
   * Handles when the screen is turned on. Announces the current time and the current ringer state
   * when phone is idle.
   */
  private void handleScreenOn(EventId eventId) {
    // TODO: This doesn't look right. Should probably be using a listener.
    proximitySensorListener.setScreenIsOn(true);
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
            // Uses QUEUE_MODE_QUEUE so that time announcement does not interrupt "unlocked".
            .setQueueMode(SpeechController.QUEUE_MODE_QUEUE)
            .setFlags(
                // For android wear devices, use forced feedback, since speech is being suppressed
                // when turning on the screen.
                isWatch
                    ? (FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                        | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                        | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)
                    : FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE);

    pipeline.returnFeedback(eventId, Feedback.speech(ttsText, speakOptions));

    for (ScreenChangedListener screenChangedListener : screenChangedListeners) {
      screenChangedListener.onScreenChanged(isScreenOn, eventId);
    }
  }

  /**
   * Return current phone's call state is idle or not.
   *
   * @return true when phone is idle
   */
  private boolean isIdle() {
    return telephonyManager != null
        && telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE;
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

    if (DateFormat.is24HourFormat(service)) {
      timeFlags |= DateUtils.FORMAT_24HOUR;
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
    if (telephonyManager == null) {
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
    void onScreenChanged(boolean isScreenOn, EventId eventId);
  }

  /** Add listener which will be called when received screen on/off intent. */
  public void addScreenChangedListener(ScreenChangedListener listener) {
    screenChangedListeners.add(listener);
  }
}
