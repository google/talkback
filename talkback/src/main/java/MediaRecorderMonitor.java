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
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.compat.media.AudioSystemCompatUtils;
import java.util.List;

/** Monitors usage of microphone. */
public class MediaRecorderMonitor {
  public interface MicrophoneStateChangedListener {
    void onMicrophoneActivated();
  }

  private enum AudioSource {
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION, "Voice Recognition"),
    MIC(MediaRecorder.AudioSource.MIC, "MIC");

    private final int mId;
    private final String mName;

    AudioSource(int id, String name) {
      mId = id;
      mName = name;
    }

    public int getId() {
      return mId;
    }

    public String getName() {
      return mName;
    }
  }

  private final AudioManager mAudioManager;
  private final AudioManager.AudioRecordingCallback mAudioRecordingCallback;

  private MicrophoneStateChangedListener mListener;
  private boolean mIsRecording = false;
  private boolean mIsVoiceRecognitionActive = false;

  public MediaRecorderMonitor(Context context) {
    mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (BuildVersionUtils.isAtLeastN()) {
      mAudioRecordingCallback =
          new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
              super.onRecordingConfigChanged(configs);
              mIsVoiceRecognitionActive =
                  containsAudioSource(configs, AudioSource.VOICE_RECOGNITION);
              final boolean isRecording = containsAudioSources(configs);
              if (!mIsRecording && isRecording) {
                mListener.onMicrophoneActivated();
              }
              mIsRecording = isRecording;
            }
          };
    } else {
      mAudioRecordingCallback = null;
    }
  }

  public boolean isMicrophoneActive() {
    if (mAudioRecordingCallback != null) {
      return mIsRecording;
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
    if (mAudioRecordingCallback != null) {
      return mIsVoiceRecognitionActive;
    } else {
      return AudioSystemCompatUtils.isSourceActive(AudioSource.VOICE_RECOGNITION.getId());
    }
  }

  @TargetApi(Build.VERSION_CODES.N)
  public void onResumeInfrastructure() {
    if (mAudioRecordingCallback != null) {
      mIsRecording = false;
      mAudioManager.registerAudioRecordingCallback(mAudioRecordingCallback, null);
    }
  }

  @TargetApi(Build.VERSION_CODES.N)
  public void onSuspendInfrastructure() {
    if (mAudioRecordingCallback != null) {
      mAudioManager.unregisterAudioRecordingCallback(mAudioRecordingCallback);
    }
  }

  public void setMicrophoneStateChangedListener(MicrophoneStateChangedListener listener) {
    mListener = listener;
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
