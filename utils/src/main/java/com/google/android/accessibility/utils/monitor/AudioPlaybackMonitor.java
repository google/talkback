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

package com.google.android.accessibility.utils.monitor;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioManager.AudioPlaybackCallback;
import android.media.AudioPlaybackConfiguration;
import com.google.android.accessibility.utils.BuildVersionUtils;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Monitors usage of AudioPlayback. */
public class AudioPlaybackMonitor {

  /** Interface for AudioPlayback */
  public interface AudioPlaybackStateChangedListener {
    void onAudioPlaybackActivated(List<AudioPlaybackConfiguration> configs);
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
    USAGE_GAME(AudioAttributes.USAGE_GAME, "Usage Game"),
    USAGE_ASSISTANT(AudioAttributes.USAGE_ASSISTANT, "Usage Assistant"),
    USAGE_ALARM(AudioAttributes.USAGE_ALARM, "Usage Alarm");

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

  private final @Nullable Context context;
  private @Nullable AudioManager audioManager;
  private final @Nullable AudioPlaybackCallback audioPlaybackCallback;

  private @Nullable AudioPlaybackStateChangedListener listener;
  private boolean isPlaying = false;

  public AudioPlaybackMonitor(Context context) {
    this.context = context;
    if (BuildVersionUtils.isAtLeastO()) {
      audioPlaybackCallback =
          new AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
              super.onPlaybackConfigChanged(configs);
              List<AudioPlaybackConfiguration> filteredConfigs =
                  extractsAudioPlaybackSources(configs);
              final boolean isPlaying = !filteredConfigs.isEmpty();
              if (listener != null && !AudioPlaybackMonitor.this.isPlaying && isPlaying) {
                listener.onAudioPlaybackActivated(filteredConfigs);
              }
              AudioPlaybackMonitor.this.isPlaying = isPlaying;
            }
          };
    } else {
      audioPlaybackCallback = null;
    }
  }

  private @Nullable AudioManager getAudioManager() {
    if ((audioManager == null) && (context != null)) {
      audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    return audioManager;
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
    @Nullable AudioManager audioManagerNow = getAudioManager();
    if (audioManagerNow == null) {
      return false;
    }
    List<AudioPlaybackConfiguration> configs = audioManagerNow.getActivePlaybackConfigurations();
    for (AudioPlaybackConfiguration config : configs) {
      if (config.getAudioAttributes().getUsage() == source.getId()) {
        return true;
      }
    }
    return false;
  }

  public void onResumeInfrastructure() {
    if (audioPlaybackCallback != null) {
      isPlaying = false;
      @Nullable AudioManager audioManagerNow = getAudioManager();
      if (audioManagerNow == null) {
        return;
      }
      audioManagerNow.registerAudioPlaybackCallback(audioPlaybackCallback, null);
    }
  }

  public void onSuspendInfrastructure() {
    if (audioPlaybackCallback != null) {
      @Nullable AudioManager audioManagerNow = getAudioManager();
      if (audioManagerNow == null) {
        return;
      }
      audioManagerNow.unregisterAudioPlaybackCallback(audioPlaybackCallback);
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

  /** Returns the activated audio playback sources that are desired. */
  private static List<AudioPlaybackConfiguration> extractsAudioPlaybackSources(
      List<AudioPlaybackConfiguration> configs) {
    // Collects the audio playback sources.
    List<AudioPlaybackConfiguration> list = new ArrayList<>();
    for (PlaybackSource source : PlaybackSource.values()) {
      int sourceId = source.getId();
      for (AudioPlaybackConfiguration config : configs) {
        if (sourceId == config.getAudioAttributes().getUsage()) {
          list.add(config);
        }
      }
    }
    return list;
  }
}
