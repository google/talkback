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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.AudioRecordingCallback;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.compat.media.AudioSystemCompatUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Monitors usage of microphone. */
public class MediaRecorderMonitor {
  public interface MicrophoneStateChangedListener {
    void onMicrophoneActivated();
  }

  private enum AudioSource {
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION, "Voice Recognition"),
    MIC(MediaRecorder.AudioSource.MIC, "MIC");

    private final int id;
    private final String name;

    AudioSource(int id, String name) {
      this.id = id;
      this.name = name;
    }

    public int getId() {
      return id;
    }

    public String getName() {
      return name;
    }
  }

  private final AudioManager audioManager;
  @Nullable private final AudioRecordingCallback audioRecordingCallback;

  private MicrophoneStateChangedListener listener;
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
              isVoiceRecognitionActive =
                  containsAudioSource(configs, AudioSource.VOICE_RECOGNITION);
              final boolean isRecording = containsAudioSources(configs);
              if (!MediaRecorderMonitor.this.isRecording && isRecording) {
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
      for (AudioSource source : AudioSource.values()) {
        if (AudioSystemCompatUtils.isSourceActive(source.getId())) {
          return true;
        }
      }
      return false;
    }
  }

  public boolean isVoiceRecognitionActive() {
    if (audioRecordingCallback != null) {
      return isVoiceRecognitionActive;
    } else {
      return AudioSystemCompatUtils.isSourceActive(AudioSource.VOICE_RECOGNITION.getId());
    }
  }

  @TargetApi(Build.VERSION_CODES.N)
  public void onResumeInfrastructure() {
    if (audioRecordingCallback != null) {
      isRecording = false;
      audioManager.registerAudioRecordingCallback(audioRecordingCallback, null);
    }
  }

  @TargetApi(Build.VERSION_CODES.N)
  public void onSuspendInfrastructure() {
    if (audioRecordingCallback != null) {
      audioManager.unregisterAudioRecordingCallback(audioRecordingCallback);
    }
  }

  public void setMicrophoneStateChangedListener(MicrophoneStateChangedListener listener) {
    this.listener = listener;
  }

  /** Returns status summary for logging only. */
  public String getStatusSummary() {
    String result = "";
    result += "[";
    for (AudioSource source : AudioSource.values()) {
      result += source.getName() + " status: ";
      result += AudioSystemCompatUtils.isSourceActive(source.getId()) ? "active" : "inactive";
      result += ";";
    }
    result += "]";
    return result;
  }

  @TargetApi(Build.VERSION_CODES.N)
  private boolean containsAudioSources(List<AudioRecordingConfiguration> configs) {
    if (configs == null) {
      return false;
    }
    // Try to find a target audio source in the recording configurations.
    for (AudioSource source : AudioSource.values()) {
      int sourceId = source.getId();
      for (AudioRecordingConfiguration config : configs) {
        if (sourceId == config.getClientAudioSource()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean containsAudioSource(
      List<AudioRecordingConfiguration> configs, AudioSource targetSource) {
    if (configs == null || targetSource == null) {
      return false;
    }
    for (AudioRecordingConfiguration config : configs) {
      if (config.getClientAudioSource() == targetSource.getId()) {
        return true;
      }
    }
    return false;
  }
}
