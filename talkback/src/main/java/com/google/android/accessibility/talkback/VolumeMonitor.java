/*
 * Copyright (C) 2012 Google Inc.
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
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_SOURCE_IS_VOLUME_CONTROL;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.SparseIntArray;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Feedback.Speech.Action;
import com.google.android.accessibility.talkback.monitor.CallStateMonitor;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.broadcast.SameThreadBroadcastReceiver;
import com.google.android.accessibility.utils.compat.media.AudioManagerCompatUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Listens for and responds to volume changes. */
public class VolumeMonitor extends SameThreadBroadcastReceiver {

  private static final String TAG = "VolumeMonitor";

  private static final SparseIntArray STREAM_NAMES = new SparseIntArray();

  static {
    STREAM_NAMES.put(AudioManager.STREAM_VOICE_CALL, R.string.value_stream_voice_call);
    STREAM_NAMES.put(AudioManager.STREAM_SYSTEM, R.string.value_stream_system);
    STREAM_NAMES.put(AudioManager.STREAM_RING, R.string.value_stream_ring);
    STREAM_NAMES.put(AudioManager.STREAM_MUSIC, R.string.value_stream_music);
    STREAM_NAMES.put(AudioManager.STREAM_ALARM, R.string.value_stream_alarm);
    STREAM_NAMES.put(AudioManager.STREAM_NOTIFICATION, R.string.value_stream_notification);
    STREAM_NAMES.put(AudioManager.STREAM_DTMF, R.string.value_stream_dtmf);
    STREAM_NAMES.put(AudioManager.STREAM_ACCESSIBILITY, R.string.value_stream_accessibility);
  }

  /** The number of times to speak the accessibility volume control hint. */
  private static final int A11Y_VOLUME_CONTROL_HINT_TIMES = 3;

  /** Keep track of adjustments made by this class. */
  private final SparseIntArray selfAdjustments = new SparseIntArray(10);

  private Context context;
  private Pipeline.FeedbackReturner pipeline;
  private AudioManager audioManager;
  private final CallStateMonitor callStateMonitor;

  private int cachedAccessibilityStreamVolume = -1;
  private int cachedAccessibilityStreamMaxVolume = -1;

  /** The stream type currently being controlled. */
  private int currentStream = -1;

  /**
   * Performance optimization for checking whether we should hint how to control a11y volume as
   * getA11yVolumeControlHintTimes() reads from storage and can be time consuming.
   */
  private boolean hintA11yVolumeControl = true;

  /** Creates and initializes a new volume monitor. */
  public VolumeMonitor(
      Pipeline.FeedbackReturner pipeline, Context context, CallStateMonitor callStateMonitor) {
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    this.context = context;
    this.pipeline = pipeline;

    // TODO: See if many objects use the same system services and get them once
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    this.callStateMonitor = callStateMonitor;
    this.hintA11yVolumeControl = FeatureSupport.hasAccessibilityAudioStream(context);
  }

  public IntentFilter getFilter() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(AudioManagerCompatUtils.VOLUME_CHANGED_ACTION);
    intentFilter.addAction(AudioManagerCompatUtils.STREAM_MUTE_CHANGED_ACTION);
    return intentFilter;
  }

  private boolean isSelfAdjusted(int streamType, int volume) {
    if (selfAdjustments.indexOfKey(streamType) < 0) {
      return false;
    } else if (selfAdjustments.get(streamType) == volume) {
      selfAdjustments.put(streamType, -1);
      return true;
    }

    return false;
  }

  /**
   * Called after volume changes. Handles acquiring control of the current stream and providing
   * feedback.
   *
   * @param streamType The stream type constant.
   * @param volume The current volume.
   * @param prevVolume The previous volume.
   */
  private void internalOnVolumeChanged(int streamType, int volume, int prevVolume) {
    if (isSelfAdjusted(streamType, volume)) {
      // Ignore self-adjustments.
      return;
    }

    if (FeatureSupport.hasAccessibilityAudioStream(context)
        && streamType == AudioManager.STREAM_ACCESSIBILITY) {
      if (getCachedAccessibilityStreamVolume() != volume) {
        cacheAccessibilityStreamVolume();
        SharedPreferencesUtils.putIntPref(
            SharedPreferencesUtils.getSharedPreferences(context),
            context.getResources(),
            R.string.pref_a11y_volume_key,
            volume);
      }
    }

    if (currentStream < 0) {
      // If the current stream hasn't been set, acquire control.
      currentStream = streamType;
      AudioManagerCompatUtils.forceVolumeControlStream(audioManager, currentStream);
      handler.onControlAcquired(streamType);
      return;
    }

    if (volume == prevVolume) {
      // Ignore ADJUST_SAME if we've already acquired control.
      return;
    }

    handler.releaseControlDelayed();
  }

  /**
   * Called after audio stream muted. Handles current stream and providing feedback. Currently, it
   * works with STREAM_MUSIC only.
   *
   * @param streamType The stream type constant.
   * @param mutedState The current muted state.
   */
  private void internalOnStreamMuted(int streamType, boolean mutedState) {
    if (streamType != AudioManager.STREAM_MUSIC) {
      return;
    }

    String text;
    if (mutedState) {
      text = context.getString(R.string.template_stream_muted_set, getStreamName(streamType));
    } else {
      text = getAnnouncementForStreamType(R.string.template_stream_volume_set, streamType);
    }

    if (!shouldAnnounceStream(streamType)) {
      handler.post(() -> releaseControl());
      return;
    }
    speakWithCompletion(text, streamType, utteranceCompleteRunnable, EVENT_ID_UNTRACKED);
  }

  private void internalSpeakA11yVolumeControlHint() {
    if (!hintA11yVolumeControl) {
      return;
    }

    int a11yVolumeControlHintTimes = getA11yVolumeControlHintTimes();
    if (a11yVolumeControlHintTimes >= A11Y_VOLUME_CONTROL_HINT_TIMES) {
      hintA11yVolumeControl = false;
      return;
    }

    if ((callStateMonitor != null)
        && (callStateMonitor.getCurrentCallState() != TelephonyManager.CALL_STATE_IDLE)) {
      // If the phone is busy, don't speak anything.
      return;
    }

    increaseAndStoreA11yVolumeControlHintTimes(a11yVolumeControlHintTimes);
    // Let's speak the hint even if audio playback is active because the hint is only spoken for
    // 3 times, we don't want users to miss it.
    Speech.Builder speechBuilder =
        Speech.builder()
            .setAction(Action.SPEAK)
            // Set text to space string, otherwise the hint will not be spoken until next speak.
            .setText(" ")
            .setHintSpeakOptions(
                SpeakOptions.create()
                    .setFlags(
                        FeedbackItem.FLAG_NO_HISTORY
                            | FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE))
            .setHint(context.getString(R.string.hint_a11y_volume_control));
    Feedback.Part.Builder part = Feedback.Part.builder().setSpeech(speechBuilder.build());
    pipeline.returnFeedback(EVENT_ID_UNTRACKED, part);
  }

  private int getA11yVolumeControlHintTimes() {
    int times =
        SharedPreferencesUtils.getSharedPreferences(context)
            .getInt(
                context.getResources().getString(R.string.pref_a11y_volume_control_hint_times), 0);
    if (times >= A11Y_VOLUME_CONTROL_HINT_TIMES) {
      hintA11yVolumeControl = false;
    }
    return times;
  }

  private void increaseAndStoreA11yVolumeControlHintTimes(int times) {
    times++;
    SharedPreferencesUtils.putIntPref(
        SharedPreferencesUtils.getSharedPreferences(context),
        context.getResources(),
        R.string.pref_a11y_volume_control_hint_times,
        times);
    if (times >= A11Y_VOLUME_CONTROL_HINT_TIMES) {
      hintA11yVolumeControl = false;
    }
  }

  public void cacheAccessibilityStreamVolume() {
    cachedAccessibilityStreamVolume =
        audioManager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY);
    cachedAccessibilityStreamMaxVolume =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY);
  }

  public int getCachedAccessibilityStreamVolume() {
    return cachedAccessibilityStreamVolume;
  }

  public int getCachedAccessibilityMaxVolume() {
    return cachedAccessibilityStreamMaxVolume;
  }

  /**
   * Called after control of a particular volume stream has been acquired and the audio stream has
   * had a chance to quiet down.
   *
   * @param streamType The stream type over which control has been acquired.
   */
  private void internalOnControlAcquired(int streamType) {
    LogUtils.v(TAG, "Acquired control of stream %d", streamType);
    handler.releaseControlDelayed();
  }

  /**
   * Returns the volume announcement text for the specified stream.
   *
   * @param streamType The stream to announce.
   * @return The volume announcement text for the stream.
   */
  private String getAnnouncementForStreamType(int templateResId, int streamType) {
    // The ringer has special cases for silent and vibrate.
    if (streamType == AudioManager.STREAM_RING) {
      switch (audioManager.getRingerMode()) {
        case AudioManager.RINGER_MODE_VIBRATE:
          return context.getString(R.string.value_ringer_vibrate);
        case AudioManager.RINGER_MODE_SILENT:
          return context.getString(R.string.value_ringer_silent);
        default: // fall out
      }
    }

    final String streamName = getStreamName(streamType);
    final int volume = getStreamVolume(streamType);

    return context.getString(templateResId, streamName, volume);
  }

  /**
   * Called after adjustments have been made and the user has not taken any action for a certain
   * duration. Announces the current volume and releases control of the stream.
   */
  private void internalOnReleaseControl() {
    handler.clearReleaseControl();

    final int streamType = currentStream;
    if (streamType < 0) {
      // Already released!
      return;
    }

    LogUtils.v(TAG, "Released control of stream %d", currentStream);

    if (!shouldAnnounceStream(streamType)) {
      handler.post(() -> releaseControl());
      return;
    }

    final String text =
        getAnnouncementForStreamType(R.string.template_stream_volume_set, streamType);

    EventId eventId = EVENT_ID_UNTRACKED; // Delayed feedback occurs after normal feedback.
    speakWithCompletion(text, streamType, utteranceCompleteRunnable, eventId);
  }

  /** Releases control of the stream. */
  public void releaseControl() {
    currentStream = -1;
    AudioManagerCompatUtils.forceVolumeControlStream(audioManager, -1);
  }

  /**
   * Returns whether a stream type should be announced.
   *
   * @param streamType The stream type.
   * @return True if the stream should be announced.
   */
  private boolean shouldAnnounceStream(int streamType) {
    switch (streamType) {
      case AudioManager.STREAM_MUSIC:
        // Only announce music stream if it's not being used.
        return !audioManager.isMusicActive();
      case AudioManager.STREAM_VOICE_CALL:
        // Never speak voice call volume. Since we only speak when
        // telephony is idle, this check is only necessary for
        // non-telephony voice calls (e.g. Google Talk).
        return false;
      default:
        // Announce all other streams by default. The VOICE_CALL and
        // RING streams are handled by checking the telephony state in
        // speakWithCompletion().
        return true;
    }
  }

  /**
   * Speaks text with a completion action, or just runs the completion action if the volume monitor
   * should be quiet.
   *
   * @param text The text to speak.
   * @param streamType The type of stream.
   * @param completedAction The action to run after speaking.
   */
  private void speakWithCompletion(
      String text,
      int streamType,
      SpeechController.UtteranceCompleteRunnable completedAction,
      EventId eventId) {
    if ((callStateMonitor != null)
        && (callStateMonitor.getCurrentCallState() != TelephonyManager.CALL_STATE_IDLE)) {
      // If the phone is busy, don't speak anything.
      handler.post(() -> releaseControl());
      return;
    }

    SpeechController.SpeakOptions speakOptions =
        SpeechController.SpeakOptions.create()
            .setQueueMode(SpeechController.QUEUE_MODE_QUEUE)
            .setFlags(
                FLAG_SOURCE_IS_VOLUME_CONTROL | FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE)
            .setUtteranceGroup(SpeechController.UTTERANCE_GROUP_DEFAULT)
            .setCompletedAction(completedAction);
    Speech.Builder speechBuilder =
        Speech.builder().setAction(Action.SPEAK).setText(text).setOptions(speakOptions);

    // Let's speak the hint even if audio playback is active because the hint is only spoken for
    // 3 times, we don't want users to miss it.
    if (hintA11yVolumeControl) {
      int a11yVolumeControlHintTimes = getA11yVolumeControlHintTimes();
      if ((a11yVolumeControlHintTimes < A11Y_VOLUME_CONTROL_HINT_TIMES)
          && (streamType != SpeechController.DEFAULT_STREAM)) {
        increaseAndStoreA11yVolumeControlHintTimes(a11yVolumeControlHintTimes);
        speechBuilder
            .setHintSpeakOptions(
                SpeakOptions.create()
                    .setFlags(
                        FeedbackItem.FLAG_NO_HISTORY
                            | FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE))
            .setHint(context.getString(R.string.hint_a11y_volume_control));
      }
    }

    Feedback.Part.Builder part = Feedback.Part.builder().setSpeech(speechBuilder.build());
    pipeline.returnFeedback(eventId, part);
  }

  /**
   * Returns the localized stream name for a given stream type constant.
   *
   * @param streamType A stream type constant.
   * @return The localized stream name.
   */
  private String getStreamName(int streamType) {
    if (FormFactorUtils.getInstance().isAndroidTv()) {
      // On TV, there is a single unified stream, therefore omit the stream name.
      return "";
    }

    final int resId = STREAM_NAMES.get(streamType);
    if (resId <= 0) {
      return "";
    }

    return context.getString(resId);
  }

  @Override
  public void onReceiveIntent(Intent intent) {
    final String action = intent.getAction();

    if (AudioManagerCompatUtils.VOLUME_CHANGED_ACTION.equals(action)) {
      final int type = intent.getIntExtra(AudioManagerCompatUtils.EXTRA_VOLUME_STREAM_TYPE, -1);
      // if there is a valid stream type alias, use it for the volume change, otherwise
      // use the stream type.
      final int typeAlias =
          intent.getIntExtra(AudioManagerCompatUtils.EXTRA_VOLUME_STREAM_TYPE_ALIAS, type);
      final int value = intent.getIntExtra(AudioManagerCompatUtils.EXTRA_VOLUME_STREAM_VALUE, -1);
      final int prevValue =
          intent.getIntExtra(AudioManagerCompatUtils.EXTRA_PREV_VOLUME_STREAM_VALUE, -1);

      if (typeAlias < 0 || value < 0 || prevValue < 0) {
        return;
      }

      handler.onVolumeChanged(typeAlias, value, prevValue);
    } else if (AudioManagerCompatUtils.STREAM_MUTE_CHANGED_ACTION.equals(action)) {
      final int type = intent.getIntExtra(AudioManagerCompatUtils.EXTRA_VOLUME_STREAM_TYPE, -1);
      final boolean state =
          intent.getBooleanExtra(AudioManagerCompatUtils.EXTRA_STREAM_VOLUME_MUTED, false);
      handler.onStreamMuted(type, state);
    }
  }

  /**
   * Returns the stream volume as a percentage of maximum volume.
   *
   * @param streamType A stream type constant.
   * @return The stream volume as a percentage.
   */
  private int getStreamVolume(int streamType) {
    int minVolume = 0;
    // For some stream types other than defined in
    // https://developer.android.com/reference/android/media/AudioManager#getStreamMinVolume(int),
    // AudioManager will trap the getStreamMinVolume.
    if (STREAM_NAMES.get(streamType) > 0) {
      minVolume = audioManager.getStreamMinVolume(streamType);
    }
    final int totalVolume = audioManager.getStreamMaxVolume(streamType) - minVolume;
    final int currentVolume = audioManager.getStreamVolume(streamType) - minVolume;

    if (totalVolume != 0) {
      int result = (int) Math.round(100f * currentVolume / totalVolume);
      if (result < 0) {
        result = 0;
      } else if (result > 100) {
        result = 100;
      }
      return result;
    }
    LogUtils.e(TAG, "Volume of stream-type:%d incorrect", streamType);
    return 0;
  }

  private final VolumeHandler handler = new VolumeHandler(this);

  /**
   * Runnable that hides the volume overlay. Used as a completion action for the "volume set"
   * utterance.
   */
  private final SpeechController.UtteranceCompleteRunnable utteranceCompleteRunnable =
      new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
          releaseControl();
        }
      };

  /** This is to check data of Feedback of Pipeline for test purpose. */
  @VisibleForTesting
  SpeechController.UtteranceCompleteRunnable getUtteranceCompleteRunnable() {
    return utteranceCompleteRunnable;
  }

  public void onVolumeKeyPressed() {
    handler.volumeButtonHintDelayed();
  }

  /**
   * Handler class for the volume monitor. Transfers volume broadcasts to the service thread.
   * Maintains timeout actions, including volume control acquisition and release.
   */
  private static class VolumeHandler extends WeakReferenceHandler<VolumeMonitor> {
    /** Timeout in milliseconds before the volume control disappears. */
    private static final long RELEASE_CONTROL_TIMEOUT = 2000;

    /** Timeout in milliseconds before the audio channel is available. */
    private static final long ACQUIRED_CONTROL_TIMEOUT = 1000;

    private static final int MSG_VOLUME_CHANGED = 1;
    private static final int MSG_CONTROL = 2;
    private static final int MSG_RELEASE_CONTROL = 3;
    private static final int MSG_STREAM_MUTED = 4;
    private static final int MSG_VOLUME_BUTTON_HINT = 5;

    public VolumeHandler(VolumeMonitor parent) {
      super(parent);
    }

    @Override
    public void handleMessage(Message msg, VolumeMonitor parent) {
      switch (msg.what) {
        case MSG_VOLUME_CHANGED:
          {
            final Integer type = (Integer) msg.obj;
            final int value = msg.arg1;
            final int prevValue = msg.arg2;

            parent.internalOnVolumeChanged(type, value, prevValue);
            break;
          }
        case MSG_CONTROL:
          {
            final int streamType = msg.arg1;

            parent.internalOnControlAcquired(streamType);
            break;
          }
        case MSG_RELEASE_CONTROL:
          {
            parent.internalOnReleaseControl();
            break;
          }
        case MSG_STREAM_MUTED:
          {
            // We only support STREAM_MUSIC on TV now.
            final int streamType = msg.arg1;
            final boolean mutedState = (boolean) msg.obj;

            removeMessages(MSG_VOLUME_BUTTON_HINT);
            parent.internalOnStreamMuted(streamType, mutedState);
            break;
          }
        case MSG_VOLUME_BUTTON_HINT:
          {
            parent.internalSpeakA11yVolumeControlHint();
            break;
          }
        default: // fall out
      }
    }

    /**
     * Starts the volume control release timeout.
     *
     * @see #internalOnReleaseControl
     */
    public void releaseControlDelayed() {
      clearReleaseControl();

      final Message msg = obtainMessage(MSG_RELEASE_CONTROL);
      sendMessageDelayed(msg, RELEASE_CONTROL_TIMEOUT);
    }

    public void volumeButtonHintDelayed() {
      removeMessages(MSG_VOLUME_BUTTON_HINT);
      final Message msg = obtainMessage(MSG_VOLUME_BUTTON_HINT);
      sendMessageDelayed(msg, RELEASE_CONTROL_TIMEOUT);
    }

    /** Clears the volume control release timeout. */
    public void clearReleaseControl() {
      removeMessages(MSG_CONTROL);
      removeMessages(MSG_RELEASE_CONTROL);
      // When we announce the volume change, the a11y volume control hint will be in the volume
      // value announcement.
      removeMessages(MSG_VOLUME_BUTTON_HINT);
    }

    /**
     * Starts the volume control acquisition timeout.
     *
     * @param type The stream type.
     * @see #internalOnControlAcquired
     */
    public void onControlAcquired(int type) {
      removeMessages(MSG_CONTROL);
      removeMessages(MSG_RELEASE_CONTROL);

      // There is a small delay before we can speak.
      final Message msg = obtainMessage(MSG_CONTROL, type, 0);
      sendMessageDelayed(msg, ACQUIRED_CONTROL_TIMEOUT);
    }

    /**
     * Transfers volume changed broadcasts to the handler thread.
     *
     * @param type The stream type.
     * @param value The current volume index.
     * @param prevValue The previous volume index.
     */
    public void onVolumeChanged(int type, int value, int prevValue) {
      obtainMessage(MSG_VOLUME_CHANGED, value, prevValue, type).sendToTarget();
    }

    /**
     * Transfers volume mute broadcasts to the handler thread.
     *
     * @param type The stream type.
     * @param state The current mute state.
     */
    public void onStreamMuted(int type, boolean mutedState) {
      obtainMessage(MSG_STREAM_MUTED, type, /*non-sense argument holder*/ 0, mutedState)
          .sendToTarget();
    }
  }
}
