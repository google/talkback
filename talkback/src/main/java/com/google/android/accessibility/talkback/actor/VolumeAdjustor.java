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
import com.google.android.accessibility.talkback.Feedback.AdjustVolume.StreamType;

/**
 * This class supports to notify user while manipulating slider(SeekBar). When the current value is
 * already maximum and user tries to increase it, or the value is minimum and user tries to decrease
 * it, user will get notify about the status.
 */
public class VolumeAdjustor {
  private static final String TAG = "VolumeAdjustor";
  private final Context context;

  public VolumeAdjustor(Context context) {
    this.context = context;
  }

  /**
   * Adjust volume of different stream types. Currently, only the Accessibility stream can be
   * adjusted.
   */
  public void adjustVolume(boolean decrease, StreamType streamType) {
    // TODO: When this actor supports the volume change for various stream types, we
    // should have
    // ProcessorVolumeStream to use this one.
    AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    int streamTypeToAdjust;
    switch (streamType) {
      case STREAM_TYPE_ACCESSIBILITY:
        streamTypeToAdjust = STREAM_ACCESSIBILITY;
        break;
      default:
        // Not supported
        return;
    }
    audioManager.adjustStreamVolume(streamTypeToAdjust, decrease ? ADJUST_LOWER : ADJUST_RAISE, 0);
  }
}
