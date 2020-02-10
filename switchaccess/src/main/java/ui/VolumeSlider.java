/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.android.accessibility.switchaccess.ui;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.RangeInfo;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.menuitems.VolumeAdjustmentMenuItem;
import com.google.android.accessibility.switchaccess.menuitems.VolumeAdjustmentMenuItem.VolumeChangeListener;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Slider used for volume adjustments in the Switch Access global menu. */
public class VolumeSlider extends LinearLayout implements VolumeChangeListener {
  private final SeekBar seekBar;
  private final MenuButton decreaseVolumeButton;
  private final MenuButton increaseVolumeButton;
  private int volumeStreamType;

  public VolumeSlider(Context context) {
    this(context, null);
  }

  public VolumeSlider(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VolumeSlider(Context context, @Nullable AttributeSet attrs, int defStyleRes) {
    this(context, attrs, defStyleRes, 0);
  }

  public VolumeSlider(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    this(context, attrs, defStyleAttr, defStyleRes, R.layout.switch_access_volume_slider);
  }

  // #inflate expects a fully initialized ViewGroup but we need to inflate it here in the
  // constructor. VolumeAdjustmentMenuItem#addVolumeChangeListener also expects a fully initialized
  // VolumeSlider, but this needs to be registered in the constructor.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public VolumeSlider(
      Context context,
      @Nullable AttributeSet attrs,
      int defStyleAttr,
      int defStyleRes,
      int layoutResource) {
    super(context, attrs, defStyleAttr, defStyleRes);

    LayoutInflater.from(context).inflate(layoutResource, this);
    seekBar = findViewById(R.id.seekbar);
    // This ensures that a volume percentage is spoken if volume changes when spoken feedback is
    // enabled or TalkBack is on. Note that if TalkBack is on, certain volume types may be spoken
    // twice, as TalkBack has fallback behavior for announcing volume changes (e.g. ringtone).
    // Since TalkBack calculates volume percentage differently than the system, the volume
    // percentage spoken by Switch Access and TalkBack may differ. The percentage spoken by Switch
    // Access should be the same as the percentage in Android Settings for volume.
    seekBar.setAccessibilityDelegate(new VolumeAccessibilityDelegate());
    seekBar.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    increaseVolumeButton = findViewById(R.id.increase_volume_button);
    decreaseVolumeButton = findViewById(R.id.decrease_volume_button);
    reset();
    VolumeAdjustmentMenuItem.addVolumeChangeListener(this);
    updateProgressForVolume();
  }

  /** Reset all data associated with this slider and disable buttons. */
  public void reset() {
    setOnClickListener(null);
    setEnabled(false);
  }

  @Override
  public void setEnabled(boolean enabled) {
    seekBar.setEnabled(false);
    if (enabled) {
      setVisibility(VISIBLE);
      updateProgressForVolume();
    } else {
      setVisibility(GONE);
    }
    increaseVolumeButton.setEnabled(enabled);
    decreaseVolumeButton.setEnabled(enabled);
    invalidate();
  }

  public void setVolumeStreamType(int streamType) {
    volumeStreamType = streamType;
  }

  @Override
  public void onAudioStreamVolumeChanged(int volumeStreamType) {
    if (this.volumeStreamType == volumeStreamType) {
      updateProgressForVolume();
    }
  }

  @Override
  public void onRequestDoNotDisturbPermission() {
    // Do nothing.
  }

  private void updateProgressForVolume() {
    AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    if (audioManager == null) {
      return;
    }
    if (audioManager.isStreamMute(volumeStreamType)) {
      seekBar.setProgress(0);
    } else {
      int streamVolume = audioManager.getStreamVolume(volumeStreamType);
      int minVolume = audioManager.getStreamMinVolume(volumeStreamType);
      int maxVolume = audioManager.getStreamMaxVolume(volumeStreamType);
      int maxBar = seekBar.getMax();

      // Represent the current volume percentage proportionally on the slider. Offset the slider
      // progress by the minimum allowed volume, to allow non-muteable volume stream types to be
      // represented with progress of 0 at their minimum volume. This corresponds to
      // representation in Android settings.
      int progressSet = (((streamVolume * maxBar) / (maxVolume - 1)));
      int minVolumeOffset = (((minVolume * maxBar) / (maxVolume - 1)));
      seekBar.setProgress(progressSet - minVolumeOffset);
    }
  }

  private class VolumeAccessibilityDelegate extends AccessibilityDelegate {

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(host, info);
      info.setClassName(ProgressBar.class.getName());
      AudioManager audioManager =
          (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
      if ((audioManager == null) || (VERSION.SDK_INT < VERSION_CODES.N)) {
        return;
      }

      // Setting the range info on a device pre-N causes the volume percent to be always announced
      // as "0 percent." Not setting the range info on M-devices results in the percentage
      // corresponding to the volume slider to be announced. This is slightly different from the
      // actual volume percentage due to how progress bars are set. Therefore, we should prefer the
      // range info spoken feedback when possible, but when not available (i.e. on M-devices) use
      // the spoken feedback corresponding to the progress bar progress instead.
      float minVolume = audioManager.getStreamMinVolume(volumeStreamType);
      float maxVolume = audioManager.getStreamMaxVolume(volumeStreamType);
      float currentVolume = audioManager.getStreamVolume(volumeStreamType);

      // Note that this corresponds to the actual volume percentage given by the Volume Settings
      // page, which may be different than what TalkBack uses. Volume Settings calculates percentage
      // of total volume and not percentage of the valid volume range, otherwise we would subtract
      // minimum volume from the current volume.
      float percent = ((currentVolume) / (maxVolume - minVolume)) * 100;

      AccessibilityNodeInfo.RangeInfo rangeInfo =
          AccessibilityNodeInfo.RangeInfo.obtain(RangeInfo.RANGE_TYPE_PERCENT, 0, 100, percent);
      info.setRangeInfo(rangeInfo);
    }
  }
}
