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

package com.google.android.accessibility.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import androidx.annotation.Nullable;
import java.util.List;

/** Monitors usage of AudioPlayback. */
public class AudioPlaybackMonitor {

  /** Interface for AudioPlayback */
  public interface AudioPlaybackStateChangedListener {
    void onAudioPlaybackActivated();
  }

  /**
   * Brief explanation for why a given sound is playing.
   *
   * <p>This is essentially a type-safe wrapper around AudioAttribute's usage enum for the values
   * most relevant to Android Accessibility.
   */
  public enum PlaybackSource {
    USAGE_ASSISTANCE_NAVIGATION_GUIDANCE(
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, "Navigation Guidance"),
    USAGE_MEDIA(AudioAttributes.USAGE_MEDIA, "Usage Media"),
    USAGE_ASSISTANT(AudioAttributes.USAGE_ASSISTANT, "Usage Assistant");

    private final int id;
    private final String name;

    PlaybackSource(int id, String name) {
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
  @Nullable private final AudioManager.AudioPlaybackCallback audioPlaybackCallback;

  @Nullable private AudioPlaybackStateChangedListener listener;
  private boolean isPlaying = false;

  @TargetApi(Build.VERSION_CODES.O)
  public AudioPlaybackMonitor(Context context) {
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (BuildVersionUtils.isAtLeastO()) {
      audioPlaybackCallback =
          new AudioManager.AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
              super.onPlaybackConfigChanged(configs);
              final boolean isPlaying = containsAudioPlaybackSources(configs);
              if (listener != null && !AudioPlaybackMonitor.this.isPlaying && isPlaying) {
                listener.onAudioPlaybackActivated();
              }
              AudioPlaybackMonitor.this.isPlaying = isPlaying;
            }
          };
    } else {
      audioPlaybackCallback = null;
    }
  }

  public boolean isAudioPlaybackActive() {
    if (audioPlaybackCallback != null) {
      return isPlaying;
    } else {
      return false;
    }
  }

  public boolean isPlaybackSourceActive(PlaybackSource source) {
    if (!BuildVersionUtils.isAtLeastO() || source == null) {
      return false;
    }
    List<AudioPlaybackConfiguration> configs = audioManager.getActivePlaybackConfigurations();
    for (AudioPlaybackConfiguration config : configs) {
      if (config.getAudioAttributes().getUsage() == source.getId()) {
        return true;
      }
    }
    return false;
  }

  @TargetApi(Build.VERSION_CODES.O)
  public void onResumeInfrastructure() {
    if (audioPlaybackCallback != null) {
      isPlaying = false;
      audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, null);
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  public void onSuspendInfrastructure() {
    if (audioPlaybackCallback != null) {
      audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback);
    }
  }

  public void setAudioPlaybackStateChangedListener(AudioPlaybackStateChangedListener listener) {
    this.listener = listener;
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
}
