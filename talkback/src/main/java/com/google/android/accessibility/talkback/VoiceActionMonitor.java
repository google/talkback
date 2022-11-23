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
import android.os.Build;
import android.telephony.TelephonyManager;
import com.google.android.accessibility.utils.input.SpeechStateMonitor;
import com.google.android.accessibility.utils.monitor.AudioPlaybackMonitor;
import com.google.android.accessibility.utils.monitor.HeadphoneStateMonitor;
import com.google.android.accessibility.utils.monitor.MediaRecorderMonitor;
import com.google.android.accessibility.utils.monitor.VoiceActionDelegate;

/**
 * Monitors voice actions from other applications. Prevents TalkBack's audio feedback from
 * interfering with voice assist applications.
 */
public class VoiceActionMonitor implements VoiceActionDelegate {
  private final TalkBackService service;
  private final MediaRecorderMonitor mediaRecorderMonitor;
  private final AudioPlaybackMonitor audioPlaybackMonitor;
  private final CallStateMonitor callStateMonitor;
  private final SpeechStateMonitor speechStateMonitor;

  private final MediaRecorderMonitor.MicrophoneStateChangedListener microphoneStateChangedListener =
      new MediaRecorderMonitor.MicrophoneStateChangedListener() {
        @Override
        public void onMicrophoneActivated() {
          if (!isHeadphoneOn()) {
            interruptTalkBackAudio();
          }
        }
      };

  private final AudioPlaybackMonitor.AudioPlaybackStateChangedListener
      audioPlaybackStateChangedListener =
          new AudioPlaybackMonitor.AudioPlaybackStateChangedListener() {
            @Override
            public void onAudioPlaybackActivated() {
              interruptTalkBackAudio();
            }
          };

  private final CallStateMonitor.CallStateChangedListener callStateChangedListener =
      new CallStateMonitor.CallStateChangedListener() {
        @Override
        public void onCallStateChanged(int oldState, int newState) {
          if (newState == TelephonyManager.CALL_STATE_OFFHOOK) {
            interruptTalkBackAudio();
          }
        }
      };

  public VoiceActionMonitor(
      TalkBackService service,
      CallStateMonitor callStateMonitor,
      SpeechStateMonitor speechStateMonitor) {
    this.service = service;

    this.speechStateMonitor = speechStateMonitor;

    mediaRecorderMonitor = new MediaRecorderMonitor(service);
    mediaRecorderMonitor.setMicrophoneStateChangedListener(microphoneStateChangedListener);

    audioPlaybackMonitor = new AudioPlaybackMonitor(service);
    audioPlaybackMonitor.setAudioPlaybackStateChangedListener(audioPlaybackStateChangedListener);

    this.callStateMonitor = callStateMonitor;
    callStateMonitor.addCallStateChangedListener(callStateChangedListener);
  }

  /** Used for test only. Updates phone call state in instrumentation test. */
  // TODO: Revisit this method when instrumentation test is settled down.
  public void onReceivePhoneStateChangedIntent(Context context, Intent intent) {
    if (callStateMonitor != null
        && CallStateMonitor.STATE_CHANGED_FILTER.hasAction(intent.getAction())) {
      callStateMonitor.onReceive(context, intent);
    } else {
      throw new RuntimeException("Unable to send intent.");
    }
  }

  public void onSpeakingForcedFeedback() {
    interruptOtherAudio();
  }

  /** Returns {@code true} if audio play back is active. */
  public boolean isAudioPlaybackActive() {
    return audioPlaybackMonitor.isAudioPlaybackActive() || speechStateMonitor.isSpeaking();
  }

  /** Returns {@code true} if microphone is active and the user is not using a headset. */
  public boolean isMicrophoneActiveAndHeadphoneOff() {
    return isMicrophoneActive() && !isHeadphoneOn();
  }

  /**
   * Returns {@code true} if voice recognition/dictation is active and the user is not using a
   * headset.
   */
  public boolean isSsbActiveAndHeadphoneOff() {
    return mediaRecorderMonitor.isVoiceRecognitionActive() && !isHeadphoneOn();
  }

  /** Returns {@code true} if phone call is active. */
  public boolean isPhoneCallActive() {
    return callStateMonitor != null && callStateMonitor.isPhoneCallActive();
  }

  /**
   * Returns the current device call state. Returns {@link TelephonyManager#CALL_STATE_IDLE} if the
   * device doesn't support telephony feature.
   */
  public int getCurrentCallState() {
    if (callStateMonitor == null) {
      return TelephonyManager.CALL_STATE_IDLE;
    } else {
      return callStateMonitor.getCurrentCallState();
    }
  }

  public void onResumeInfrastructure() {
    mediaRecorderMonitor.onResumeInfrastructure();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioPlaybackMonitor.onResumeInfrastructure();
    }
  }

  public void onSuspendInfrastructure() {
    mediaRecorderMonitor.onSuspendInfrastructure();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioPlaybackMonitor.onSuspendInfrastructure();
    }
  }

  public boolean isHeadphoneOn() {
    return HeadphoneStateMonitor.isHeadphoneOn(service);
  }

  @Override
  public boolean isVoiceRecognitionActive() {
    return mediaRecorderMonitor.isVoiceRecognitionActive();
  }

  @Override
  public boolean isMicrophoneActive() {
    return mediaRecorderMonitor.isMicrophoneActive() || speechStateMonitor.isListening();
  }

  private void interruptTalkBackAudio() {
    service.interruptAllFeedback(false /* stopTtsSpeechCompletely */);
  }

  private void interruptOtherAudio() {}
}
