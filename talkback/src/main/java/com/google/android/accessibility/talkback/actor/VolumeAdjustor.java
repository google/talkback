/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.STREAM_ACCESSIBILITY;

import android.content.Context;
import android.media.AudioManager;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.Feedback.AdjustVolume.StreamType;
import com.google.android.accessibility.utils.FormFactorUtils;

/**
 * This class supports to notify user while manipulating slider(SeekBar). When the current value is
 * already maximum and user tries to increase it, or the value is minimum and user tries to decrease
 * it, user will get notify about the status.
 */
public class VolumeAdjustor {
  private static final String TAG = "VolumeAdjustor";
  // For wearable, which does not have dedicate key to change volume. If, for some reason, the
  // stream volume is down to a level which user has trouble to hear the feedback,we provides the
  // stream volume to be restored to this percentage level when user enables TalkBack.
  private static final int MIN_VOLUME_PERCENTAGE = 30;
  private final Context context;
  private final FormFactorUtils formFactorUtils;

  public VolumeAdjustor(Context context) {
    this.context = context;
    formFactorUtils = FormFactorUtils.getInstance();
    if (formFactorUtils.isAndroidWear()) {
      resetVolume();
    }
  }

  /**
   * Adjust volume of different stream types. Currently, only the Accessibility stream can be
   * adjusted.
   *
   * @return false if volume does not change for any reason, true otherwise.
   */
  public boolean adjustVolume(boolean decrease, StreamType streamType) {
    // TODO: When this actor supports the volume change for various stream types, we
    // should have ProcessorVolumeStream to use this one.
    @Nullable
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audioManager == null) {
      return false;
    }
    int streamTypeToAdjust;
    switch (streamType) {
      case STREAM_TYPE_ACCESSIBILITY:
        streamTypeToAdjust = STREAM_ACCESSIBILITY;
        break;
      default:
        // Not supported
        return false;
    }
    int maxVolume = Math.max(audioManager.getStreamMaxVolume(streamTypeToAdjust), 1);
    int minVolume = Math.max(audioManager.getStreamMinVolume(streamTypeToAdjust), 1);
    int currentVolume = audioManager.getStreamVolume(streamTypeToAdjust);

    if ((maxVolume <= minVolume)
        || (decrease && currentVolume <= minVolume)
        || (!decrease && currentVolume >= maxVolume)) {
      return false;
    }
    audioManager.adjustStreamVolume(streamTypeToAdjust, decrease ? ADJUST_LOWER : ADJUST_RAISE, 0);
    return true;
  }

  /**
   * This method will restore the stream volume to a predefined level, when the current volume
   * settings is lower than that level. This is especially important for Accessibility Stream in
   * Wear device which does not have dedicate volume keys;
   */
  @VisibleForTesting
  protected void resetVolume() {
    @Nullable
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audioManager == null) {
      return;
    }
    int maxVolume = Math.max(audioManager.getStreamMaxVolume(STREAM_ACCESSIBILITY), 1);
    int minVolume = Math.max(audioManager.getStreamMinVolume(STREAM_ACCESSIBILITY), 1);
    int currentVolume = audioManager.getStreamVolume(STREAM_ACCESSIBILITY);
    if (maxVolume <= minVolume) {
      return;
    }
    int minAllowedVolume =
        formFactorUtils.isAndroidWear()
            ? (((maxVolume - minVolume) * MIN_VOLUME_PERCENTAGE) / 100) + minVolume
            : minVolume;

    if (currentVolume < minAllowedVolume) {
      audioManager.setStreamVolume(STREAM_ACCESSIBILITY, minAllowedVolume, 0);
    }
  }
}
