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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseIntArray;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.compat.media.AudioManagerCompatUtils;
import com.google.android.accessibility.utils.output.SpeechController;

/** Listens for and responds to volume changes. */
public class VolumeMonitor extends BroadcastReceiver {
  /** Pseudo stream type for master volume. */
  private static final int STREAM_MASTER = -100;

  private static final SparseIntArray STREAM_NAMES = new SparseIntArray();

  static {
    STREAM_NAMES.put(STREAM_MASTER, R.string.value_stream_master);
    STREAM_NAMES.put(AudioManager.STREAM_VOICE_CALL, R.string.value_stream_voice_call);
    STREAM_NAMES.put(AudioManager.STREAM_SYSTEM, R.string.value_stream_system);
    STREAM_NAMES.put(AudioManager.STREAM_RING, R.string.value_stream_ring);
    STREAM_NAMES.put(AudioManager.STREAM_MUSIC, R.string.value_stream_music);
    STREAM_NAMES.put(AudioManager.STREAM_ALARM, R.string.value_stream_alarm);
    STREAM_NAMES.put(AudioManager.STREAM_NOTIFICATION, R.string.value_stream_notification);
    STREAM_NAMES.put(AudioManager.STREAM_DTMF, R.string.value_stream_dtmf);
    if (BuildVersionUtils.isAtLeastO()) {
      STREAM_NAMES.put(AudioManager.STREAM_ACCESSIBILITY, R.string.value_stream_accessibility);
    }
  }

  /** Keep track of adjustments made by this class. */
  private final SparseIntArray mSelfAdjustments = new SparseIntArray(10);

  private Context mContext;
  private SpeechController mSpeechController;
  private AudioManager mAudioManager;
  private TelephonyManager mTelephonyManager;

  private int mCachedAccessibilityStreamVolume = -1;
  private int mCachedAccessibilityStreamMaxVolume = -1;

  /** The stream type currently being controlled. */
  private int mCurrentStream = -1;

  /** Creates and initializes a new volume monitor. */
  public VolumeMonitor(SpeechController speechController, Context context) {
    if (speechController == null) throw new IllegalStateException();
    mContext = context;
    mSpeechController = speechController;

    // TODO: See if many objects use the same system services and get them once
    mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
  }

  public IntentFilter getFilter() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(AudioManagerCompatUtils.VOLUME_CHANGED_ACTION);
    intentFilter.addAction(AudioManagerCompatUtils.MASTER_VOLUME_CHANGED_ACTION);
    return intentFilter;
  }

  private boolean isSelfAdjusted(int streamType, int volume) {
    if (mSelfAdjustments.indexOfKey(streamType) < 0) {
      return false;
    } else if (mSelfAdjustments.get(streamType) == volume) {
      mSelfAdjustments.put(streamType, -1);
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

    if (FormFactorUtils.hasAcessibilityAudioStream(mContext)
        && streamType == AudioManager.STREAM_ACCESSIBILITY) {
      cacheAccessibilityStreamVolume();
    }

    if (mCurrentStream < 0) {
      // If the current stream hasn't been set, acquire control.
      mCurrentStream = streamType;
      AudioManagerCompatUtils.forceVolumeControlStream(mAudioManager, mCurrentStream);
      mHandler.onControlAcquired(streamType);
      return;
    }

    if (volume == prevVolume) {
      // Ignore ADJUST_SAME if we've already acquired control.
      return;
    }

    mHandler.releaseControlDelayed();
  }

  public void cacheAccessibilityStreamVolume() {
    mCachedAccessibilityStreamVolume =
        mAudioManager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY);
    mCachedAccessibilityStreamMaxVolume =
        mAudioManager.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY);
  }

  public int getCachedAccessibilityStreamVolume() {
    return mCachedAccessibilityStreamVolume;
  }

  public int getCachedAccessibilityMaxVolume() {
    return mCachedAccessibilityStreamMaxVolume;
  }

  /**
   * Called after control of a particular volume stream has been acquired and the audio stream has
   * had a chance to quiet down.
   *
   * @param streamType The stream type over which control has been acquired.
   */
  private void internalOnControlAcquired(int streamType) {
    LogUtils.log(this, Log.VERBOSE, "Acquired control of stream %d", streamType);
    mHandler.releaseControlDelayed();
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
      switch (mAudioManager.getRingerMode()) {
        case AudioManager.RINGER_MODE_VIBRATE:
          return mContext.getString(R.string.value_ringer_vibrate);
        case AudioManager.RINGER_MODE_SILENT:
          return mContext.getString(R.string.value_ringer_silent);
        default: // fall out
      }
    }

    final String streamName = getStreamName(streamType);
    final int volume = getStreamVolume(streamType);

    return mContext.getString(templateResId, streamName, volume);
  }

  /**
   * Called after adjustments have been made and the user has not taken any action for a certain
   * duration. Announces the current volume and releases control of the stream.
   */
  private void internalOnReleaseControl() {
    mHandler.clearReleaseControl();

    final int streamType = mCurrentStream;
    if (streamType < 0) {
      // Already released!
      return;
    }

    LogUtils.log(this, Log.VERBOSE, "Released control of stream %d", mCurrentStream);

    if (!shouldAnnounceStream(streamType)) {
      mHandler.post(
          new SpeechController.CompletionRunner(
              mReleaseControl, SpeechController.STATUS_INTERRUPTED));
      return;
    }

    final String text =
        getAnnouncementForStreamType(R.string.template_stream_volume_set, streamType);

    EventId eventId = EVENT_ID_UNTRACKED; // Delayed feedback occurs after normal feedback.
    speakWithCompletion(text, mReleaseControl, eventId);
  }

  /** Releases control of the stream. */
  public void releaseControl() {
    mCurrentStream = -1;
    AudioManagerCompatUtils.forceVolumeControlStream(mAudioManager, -1);
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
        return !mAudioManager.isMusicActive();
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
   * @param completedAction The action to run after speaking.
   */
  private void speakWithCompletion(
      String text, SpeechController.UtteranceCompleteRunnable completedAction, EventId eventId) {
    if ((mTelephonyManager != null)
        && (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)) {
      // If the phone is busy, don't speak anything.
      mHandler.post(
          new SpeechController.CompletionRunner(
              completedAction, SpeechController.STATUS_INTERRUPTED));
      return;
    }

    mSpeechController.speak(
        text, /* text */
        null, /* earcons */
        null, /* haptics */
        SpeechController.QUEUE_MODE_QUEUE, /* queueMode */
        0, /* flags */
        SpeechController.UTTERANCE_GROUP_DEFAULT, /* utteranceGroup */
        null, /* speechParams */
        null, /* nonSpeechParams */
        null, /* startAction */
        null, /* rangeStartCallback */
        completedAction, /* completeAction */
        eventId);
  }

  /**
   * Returns the localized stream name for a given stream type constant.
   *
   * @param streamType A stream type constant.
   * @return The localized stream name.
   */
  private String getStreamName(int streamType) {
    final int resId = STREAM_NAMES.get(streamType);
    if (resId <= 0) {
      return "";
    }

    return mContext.getString(resId);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
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

      mHandler.onVolumeChanged(typeAlias, value, prevValue);
    } else if (AudioManagerCompatUtils.MASTER_VOLUME_CHANGED_ACTION.equals(action)) {
      final int value = intent.getIntExtra(AudioManagerCompatUtils.EXTRA_MASTER_VOLUME_VALUE, -1);
      final int prevValue =
          intent.getIntExtra(AudioManagerCompatUtils.EXTRA_PREV_MASTER_VOLUME_VALUE, -1);

      if (value < 0 || prevValue < 0) {
        return;
      }

      mHandler.onVolumeChanged(STREAM_MASTER, value, prevValue);
    }
  }

  /**
   * Returns the stream volume as a percentage of maximum volume in increments of 5%, e.g. 73% is
   * returned as 70.
   *
   * @param streamType A stream type constant.
   * @return The stream volume as a percentage.
   */
  private int getStreamVolume(int streamType) {
    final int currentVolume = mAudioManager.getStreamVolume(streamType);
    final int maxVolume = mAudioManager.getStreamMaxVolume(streamType);
    return 5 * (int) (20 * currentVolume / maxVolume + 0.5);
  }

  private final VolumeHandler mHandler = new VolumeHandler(this);

  /**
   * Runnable that hides the volume overlay. Used as a completion action for the "volume set"
   * utterance.
   */
  private final SpeechController.UtteranceCompleteRunnable mReleaseControl =
      new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
          releaseControl();
        }
      };

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

    /** Clears the volume control release timeout. */
    public void clearReleaseControl() {
      removeMessages(MSG_CONTROL);
      removeMessages(MSG_RELEASE_CONTROL);
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
     * Transfers volume broadcasts to the handler thread.
     *
     * @param type The stream type.
     * @param value The current volume index.
     * @param prevValue The previous volume index.
     */
    public void onVolumeChanged(int type, int value, int prevValue) {
      obtainMessage(MSG_VOLUME_CHANGED, value, prevValue, type).sendToTarget();
    }
  }
}
