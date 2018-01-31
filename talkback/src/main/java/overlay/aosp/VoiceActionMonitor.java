/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import com.google.android.accessibility.compositor.EventFilter;
import com.google.android.accessibility.utils.HeadphoneStateMonitor;

/**
 * Monitors voice actions from other applications. Prevents TalkBack's audio feedback from
 * interfering with voice assist applications.
 */
public class VoiceActionMonitor implements EventFilter.VoiceActionDelegate {
  private final TalkBackService mService;
  private final MediaRecorderMonitor mMediaRecorderMonitor;
  private final AudioPlaybackMonitor mAudioPlaybackMonitor;
  @Nullable private final CallStateMonitor mCallStateMonitor;

  private final MediaRecorderMonitor.MicrophoneStateChangedListener
      mMicrophoneStateChangedListener =
          new MediaRecorderMonitor.MicrophoneStateChangedListener() {
            @Override
            public void onMicrophoneActivated() {
              if (!isHeadphoneOn()) {
                interruptTalkBackAudio();
              }
            }
          };

  private final AudioPlaybackMonitor.AudioPlaybackStateChangedListener
      mAudioPlaybackStateChangedListener =
          new AudioPlaybackMonitor.AudioPlaybackStateChangedListener() {
            @Override
            public void onAudioPlaybackActivated() {
              interruptTalkBackAudio();
            }
          };

  private final CallStateMonitor.CallStateChangedListener mCallStateChangedListener =
      new CallStateMonitor.CallStateChangedListener() {
        @Override
        public void onCallStateChanged(int oldState, int newState) {
          // TODO: ShakeDetector is irrelevant to voice action/feedback. Consider to move it
          // somewhere else.
          if (newState == TelephonyManager.CALL_STATE_OFFHOOK) {
            interruptTalkBackAudio();
            mService.getShakeDetector().setEnabled(false);
          } else if (newState == TelephonyManager.CALL_STATE_IDLE) {
            mService.getShakeDetector().setEnabled(true);
          }
        }
      };

  public VoiceActionMonitor(TalkBackService service) {
    mService = service;

    mMediaRecorderMonitor = new MediaRecorderMonitor(service);
    mMediaRecorderMonitor.setMicrophoneStateChangedListener(mMicrophoneStateChangedListener);

    mAudioPlaybackMonitor = new AudioPlaybackMonitor(service);
    mAudioPlaybackMonitor.setAudioPlaybackStateChangedListener(mAudioPlaybackStateChangedListener);

    if (service.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
      mCallStateMonitor = new CallStateMonitor(service);
      mCallStateMonitor.addCallStateChangedListener(mCallStateChangedListener);
    } else {
      mCallStateMonitor = null;
    }
  }

  /** Used for test only. Updates phone call state in instrumentation test. */
  // TODO: Revisit this method when instrumentation test is settled down.
  public void onReceivePhoneStateChangedIntent(Context context, Intent intent) {
    if (mCallStateMonitor != null
        && CallStateMonitor.STATE_CHANGED_FILTER.hasAction(intent.getAction())) {
      mCallStateMonitor.onReceive(context, intent);
    } else {
      throw new RuntimeException("Unable to send intent.");
    }
  }

  public void onSpeakingForcedFeedback() {
    interruptOtherAudio();
  }

  /**
   * Returns {@code true} if we should suppress passive feedback. Paassive feedback is the TalkBack
   * utterance feedback introduced from any approach other than ExploreByTouch.
   *
   * <p>Suppresses passive feedback in the following cases:
   *
   * <ul>
   *   <li>Microphone is active and the user is not using a headset.
   *   <li>Some other special voice assist app is playing speech audio.
   * </ul>
   */
  public boolean shouldSuppressPassiveFeedback() {
    boolean result =
        (mMediaRecorderMonitor.isMicrophoneActive() && !isHeadphoneOn())
            || mAudioPlaybackMonitor.isAudioPlaybackActive()
            || (mCallStateMonitor != null && mCallStateMonitor.isPhoneCallActive());
    return result;
  }

  /**
   * Returns the current device call state. Returns {@link TelephonyManager#CALL_STATE_IDLE} if the
   * device doesn't support telephony feature.
   */
  public int getCurrentCallState() {
    if (mCallStateMonitor == null) {
      return TelephonyManager.CALL_STATE_IDLE;
    } else {
      return mCallStateMonitor.getCurrentCallState();
    }
  }

  public void onResumeInfrastructure() {
    mMediaRecorderMonitor.onResumeInfrastructure();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      mAudioPlaybackMonitor.onResumeInfrastructure();
    }
    if (mCallStateMonitor != null) {
      mCallStateMonitor.startMonitor();
    }
  }

  public void onSuspendInfrastructure() {
    mMediaRecorderMonitor.onSuspendInfrastructure();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      mAudioPlaybackMonitor.onSuspendInfrastructure();
    }
    if (mCallStateMonitor != null) {
      mCallStateMonitor.stopMonitor();
    }
  }

  public boolean isHeadphoneOn() {
    return HeadphoneStateMonitor.isHeadphoneOn(mService);
  }

  @Override
  public boolean isVoiceRecognitionActive() {
    return mMediaRecorderMonitor.isVoiceRecognitionActive();
  }

  private void interruptTalkBackAudio() {
    mService.interruptAllFeedback(false /* stopTtsSpeechCompletely */);
  }

  private void interruptOtherAudio() {}
}
