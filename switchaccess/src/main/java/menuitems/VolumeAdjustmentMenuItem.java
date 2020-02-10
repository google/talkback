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

package com.google.android.accessibility.switchaccess.menuitems;

import android.content.Context;
import android.media.AudioManager;
import com.google.android.accessibility.switchaccess.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all data required to construct a menu item for adjusting volume within the Switch Access
 * menu.
 */
public class VolumeAdjustmentMenuItem extends MenuItem {

  private final VolumeAdjustmentType volumeAdjustmentType;

  /**
   * The maximum number of clicks it should take to go from minimum to maximum volume. This is to
   * reduce the number of clicks for volumes with high granularity. {@link
   * AudioManager#ADJUST_LOWER} and {@link AudioManager#ADJUST_RAISE} don't guarantee volume
   * adjustments in a reasonable number of steps. Note that the actual maximum number of steps may
   * be slightly higher or lower due to rounding.
   */
  protected static final int NUM_VOLUME_STEPS = 5;

  private static List<VolumeChangeListener> volumeChangeListeners = new ArrayList<>();

  /**
   * Registers a listener to be notified of volume control changes within the volume adjustment
   * menu.
   *
   * @param volumeChangeListener The listener to be notified of volume changes
   */
  public static void addVolumeChangeListener(VolumeChangeListener volumeChangeListener) {
    volumeChangeListeners.add(volumeChangeListener);
  }

  /**
   * Unregisters a listener to be notified of volume control changes within the volume adjustment
   * menu.
   *
   * @param volumeChangeListener The listener to stop notifying of volume changes
   */
  public static void removeVolumeChangeListener(VolumeChangeListener volumeChangeListener) {
    volumeChangeListeners.remove(volumeChangeListener);
  }

  // Represent a volume's stream type, as recognized by AudioManager, such as
  // AudioManager.STREAM_ACCESSIBILITY.
  private final int volumeStreamType;
  private final Context context;
  private AudioManager audioManager;
  private int volumeChangeStepSize;

  /** Describes how volume should be adjusted. */
  public enum VolumeAdjustmentType {
    TOGGLE_MUTE,
    INCREASE,
    DECREASE
  }

  public VolumeAdjustmentMenuItem(
      Context context,
      AudioManager audioManager,
      VolumeAdjustmentType volumeAdjustmentType,
      int volumeStreamType) {
    this.audioManager = audioManager;
    this.volumeAdjustmentType = volumeAdjustmentType;
    this.volumeStreamType = volumeStreamType;
    this.context = context;

    int differenceBetweenMaxAndMinVolume =
        (audioManager.getStreamMaxVolume(volumeStreamType)
            - audioManager.getStreamMinVolume(volumeStreamType));
    volumeChangeStepSize = Math.max(1, differenceBetweenMaxAndMinVolume / NUM_VOLUME_STEPS);
  }

  @Override
  public int getIconResource() {
    switch (volumeAdjustmentType) {
      case TOGGLE_MUTE:
        if (audioManager.isStreamMute(volumeStreamType)) {
          return R.drawable.quantum_ic_volume_mute_white_24;
        } else {
          return R.drawable.quantum_ic_volume_off_white_24;
        }
      case DECREASE:
        return R.drawable.quantum_ic_volume_down_white_24;
      case INCREASE:
        return R.drawable.quantum_ic_volume_up_white_24;
      default:
        return 0;
    }
  }

  @Override
  public String getText() {
    switch (volumeAdjustmentType) {
      case TOGGLE_MUTE:
        if (audioManager.isStreamMute(volumeStreamType)) {
          return context.getString(R.string.volume_menu_unmute);
        } else {
          return context.getString(R.string.volume_menu_mute);
        }
      case DECREASE:
        return context.getString(R.string.volume_menu_decrease);
      case INCREASE:
        return context.getString(R.string.volume_menu_increase);
      default:
        return "";
    }
  }

  public MenuItemOnClickListener getOnClickListener() {
    return new MenuItemOnClickListener() {
      @Override
      public void onClick() {
        switch (volumeAdjustmentType) {
          case INCREASE:
            adjustStreamVolume(volumeChangeStepSize);
            break;
          case DECREASE:
            adjustStreamVolume(volumeChangeStepSize * -1);
            break;
          case TOGGLE_MUTE:
            if (audioManager.isStreamMute(volumeStreamType)) {
              try {
                audioManager.adjustStreamVolume(
                    volumeStreamType, AudioManager.ADJUST_UNMUTE, 0 /* flags */);
              } catch (SecurityException e) {
                // Security exception due to Do Not Disturb access.
                requestDoNotDisturbPermission();
              }
              onVolumeChange();
            } else {
              try {
                audioManager.adjustStreamVolume(
                    volumeStreamType, AudioManager.ADJUST_MUTE, 0 /* flags */);
              } catch (SecurityException e) {
                // Security exception due to Do Not Disturb access.
                requestDoNotDisturbPermission();
              }
              onVolumeChange();
            }
        }
      }
    };
  }

  private void adjustStreamVolume(int volumeDelta) {
    int currentStreamVolume = audioManager.getStreamVolume(volumeStreamType);
    try {
      // Setting the stream volume to a negative number can cause volume to increase. Therefore,
      // don't attempt to set the volume to anything less than 0. Additionally, clamp the volume
      // to the maximum volume as well.
      int newVolume =
          Math.min(
              Math.max(
                  audioManager.getStreamMinVolume(volumeStreamType),
                  currentStreamVolume + volumeDelta),
              audioManager.getStreamMaxVolume(volumeStreamType));
      audioManager.setStreamVolume(volumeStreamType, newVolume, 0 /* flags */);
      onVolumeChange();
    } catch (SecurityException e) {
      // Security exception due to Do Not Disturb access.
      requestDoNotDisturbPermission();
    }
  }

  private void onVolumeChange() {
    for (VolumeChangeListener volumeChangeListener : volumeChangeListeners) {
      volumeChangeListener.onAudioStreamVolumeChanged(volumeStreamType);
    }
  }

  private static void requestDoNotDisturbPermission() {
    for (VolumeChangeListener volumeChangeListener : volumeChangeListeners) {
      volumeChangeListener.onRequestDoNotDisturbPermission();
    }
  }

  /** Listener for volume changes. */
  public interface VolumeChangeListener {

    /**
     * Notify that the given volume stream has changed.
     *
     * @param volumeStreamType The type of volume that has changed
     */
    void onAudioStreamVolumeChanged(int volumeStreamType);

    /**
     * Notify that the user has attempted to adjust ring volume when Do Not Disturb mode is on and
     * request that the user grant the Do Not Disturb permission.
     */
    void onRequestDoNotDisturbPermission();
  }
}
