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

package com.google.android.accessibility.utils.monitor;

import static android.media.MediaRecorder.AudioSource.MIC;
import static android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.AudioRecordingCallback;
import android.media.AudioRecordingConfiguration;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.compat.media.AudioSystemCompatUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Monitors usage of microphone. */
public class MediaRecorderMonitor {
  public interface MicrophoneStateChangedListener {
    void onMicrophoneActivated();
  }

  private final @Nullable AudioManager audioManager;
  private final @Nullable AudioRecordingCallback audioRecordingCallback;

  private @Nullable MicrophoneStateChangedListener listener;
  private boolean isRecording = false;
  private boolean isVoiceRecognitionActive = false;

  public MediaRecorderMonitor(Context context) {
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (BuildVersionUtils.isAtLeastN()) {
      audioRecordingCallback =
          new AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
              super.onRecordingConfigChanged(configs);
              isVoiceRecognitionActive = containsAudioSourceVoiceRecog(configs);
              final boolean isRecording = containsAudioSources(configs);
              if (!MediaRecorderMonitor.this.isRecording && isRecording && (listener != null)) {
                listener.onMicrophoneActivated();
              }
              MediaRecorderMonitor.this.isRecording = isRecording;
            }
          };
    } else {
      audioRecordingCallback = null;
    }
  }

  public boolean isMicrophoneActive() {
    if (audioRecordingCallback != null) {
      return isRecording;
    } else {
      if (AudioSystemCompatUtils.isSourceActive(VOICE_RECOGNITION)
          || AudioSystemCompatUtils.isSourceActive(MIC)) {
          return true;
        }
      return false;
    }
  }

  public boolean isVoiceRecognitionActive() {
    if (audioRecordingCallback != null) {
      return isVoiceRecognitionActive;
    } else {
      return AudioSystemCompatUtils.isSourceActive(VOICE_RECOGNITION);
    }
  }

  public void onResumeInfrastructure() {
    if ((audioRecordingCallback != null) && (audioManager != null)) {
      isRecording = false;
      audioManager.registerAudioRecordingCallback(audioRecordingCallback, null);
    }
  }

  public void onSuspendInfrastructure() {
    if ((audioRecordingCallback != null) && (audioManager != null)) {
      audioManager.unregisterAudioRecordingCallback(audioRecordingCallback);
    }
  }

  public void setMicrophoneStateChangedListener(MicrophoneStateChangedListener listener) {
    this.listener = listener;
  }

  private static boolean containsAudioSources(List<AudioRecordingConfiguration> configs) {
    if (configs == null) {
      return false;
    }
    // Try to find a target audio source in the recording configurations.
      for (AudioRecordingConfiguration config : configs) {
      if ((VOICE_RECOGNITION == config.getClientAudioSource())
          || (MIC == config.getClientAudioSource())) {
          return true;
        }
    }
    return false;
  }

  private static boolean containsAudioSourceVoiceRecog(List<AudioRecordingConfiguration> configs) {
    if (configs == null) {
      return false;
    }
    for (AudioRecordingConfiguration config : configs) {
      if (config.getClientAudioSource() == VOICE_RECOGNITION) {
        return true;
      }
    }
    return false;
  }
}
