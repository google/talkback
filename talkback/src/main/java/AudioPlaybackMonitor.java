/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import com.google.android.accessibility.utils.BuildVersionUtils;
import java.util.List;

/** Monitors usage of AudioPlayback. */
public class AudioPlaybackMonitor {

  /** Interface for AudioPlayback */
  public interface AudioPlaybackStateChangedListener {
    void onAudioPlaybackActivated();
  }

  private enum PlaybackSource {
    USAGE_ASSISTANCE_NAVIGATION_GUIDANCE(
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, "Navigation Guidance"),
    USAGE_MEDIA(AudioAttributes.USAGE_MEDIA, "Usage Media"),
    USAGE_VOICE_COMMUNICATION(AudioAttributes.USAGE_VOICE_COMMUNICATION, "Voice Communication"),
    USAGE_ASSISTANT(AudioAttributes.USAGE_ASSISTANT, "Usage Assistant");

    private final int mId;
    private final String mName;

    PlaybackSource(int id, String name) {
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
  private final AudioManager.AudioPlaybackCallback mAudioPlaybackCallback;

  private AudioPlaybackStateChangedListener mListener;
  private boolean mIsPlaying = false;

  @TargetApi(Build.VERSION_CODES.O)
  public AudioPlaybackMonitor(Context context) {
    mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (BuildVersionUtils.isAtLeastO()) {
      mAudioPlaybackCallback =
          new AudioManager.AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
              super.onPlaybackConfigChanged(configs);
              final boolean isPlaying = containsAudioPlaybackSources(configs);
              if (!mIsPlaying && isPlaying) {
                mListener.onAudioPlaybackActivated();
              }
              mIsPlaying = isPlaying;
            }
          };
    } else {
      mAudioPlaybackCallback = null;
    }
  }

  public boolean isAudioPlaybackActive() {
    if (mAudioPlaybackCallback != null) {
      return mIsPlaying;
    } else {
      return false;
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public void onResumeInfrastructure() {
    if (mAudioPlaybackCallback != null) {
      mIsPlaying = false;
      mAudioManager.registerAudioPlaybackCallback(mAudioPlaybackCallback, null);
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public void onSuspendInfrastructure() {
    if (mAudioPlaybackCallback != null) {
      mAudioManager.unregisterAudioPlaybackCallback(mAudioPlaybackCallback);
    }
  }

  public void setAudioPlaybackStateChangedListener(AudioPlaybackStateChangedListener listener) {
    mListener = listener;
  }

  /** Returns status summary for logging only. */
  public String getStatusSummary() {
    if (!BuildVersionUtils.isAtLeastO()) {
      return "";
    }
    String result = "";
    result += "[";
    for (PlaybackSource source : PlaybackSource.values()) {
      result += source.getName() + " status: ";
      result += (isPlaybackSourceActive(source) ? "active" : "inactive");
      result += ";";
    }
    result += "]";
    return result;
  }

  @TargetApi(Build.VERSION_CODES.O)
  private static boolean containsAudioPlaybackSources(List<AudioPlaybackConfiguration> configs) {
    if (configs == null) {
      return false;
    }
    // Try to find a target audio source in the playback configurations.
    for (PlaybackSource source : PlaybackSource.values()) {
      int sourceId = source.getId();
      for (AudioPlaybackConfiguration config : configs) {
        if (sourceId == config.getAudioAttributes().getUsage()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isPlaybackSourceActive(PlaybackSource source) {
    if (!BuildVersionUtils.isAtLeastO() || source == null) {
      return false;
    }
    List<AudioPlaybackConfiguration> configs = mAudioManager.getActivePlaybackConfigurations();
    for (AudioPlaybackConfiguration config : configs) {
      if (config.getAudioAttributes().getUsage() == source.getId()) {
        return true;
      }
    }
    return false;
  }
}
