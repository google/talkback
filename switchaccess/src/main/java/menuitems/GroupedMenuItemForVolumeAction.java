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
import android.os.Build;
import android.os.Build.VERSION_CODES;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.menuitems.VolumeAdjustmentMenuItem.VolumeAdjustmentType;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Grouped menu item that represents the grouping of volume buttons that leads to volume adjustment
 * menu pages.
 */
public class GroupedMenuItemForVolumeAction extends GroupedMenuItem {

  // Represent a volume's stream type, as recognized by AudioManager, such as
  // AudioManager.STREAM_ACCESSIBILITY.
  private final int volumeStreamType;
  private Context context;
  AudioManager audioManager;

  @VisibleForTesting
  protected GroupedMenuItemForVolumeAction(
      Context context,
      OverlayController overlayController,
      AudioManager audioManager,
      int volumeStreamType,
      @Nullable SelectMenuItemListener selectMenuItemListener) {
    super(
        overlayController, getMenuItemEnumFromVolumeType(volumeStreamType), selectMenuItemListener);
    this.volumeStreamType = volumeStreamType;
    this.context = context;
    this.audioManager = audioManager;
  }

  @Override
  public List<MenuItem> getSubMenuItems() {
    List<MenuItem> subMenuItems = new ArrayList<>();
    subMenuItems.add(
        new VolumeAdjustmentMenuItem(
            context, audioManager, VolumeAdjustmentType.DECREASE, volumeStreamType));
    subMenuItems.add(
        new VolumeAdjustmentMenuItem(
            context, audioManager, VolumeAdjustmentType.INCREASE, volumeStreamType));
    return subMenuItems;
  }

  @Override
  public GroupedMenuItemHeader getHeader() {
    // Certain volume stream types (e.g. call volume) cannot be 100% muted. If that is the case,
    // don't show a mute button.
    return canStreamBeMuted()
        ? new GroupedMenuItemHeader(
            getText(),
            new VolumeAdjustmentMenuItem(
                context, audioManager, VolumeAdjustmentType.TOGGLE_MUTE, volumeStreamType))
        : new GroupedMenuItemHeader(getText());
  }

  @Override
  public int getIconResource() {
    switch (volumeStreamType) {
      case AudioManager.STREAM_MUSIC:
        return R.drawable.quantum_ic_music_note_white_24;
      case AudioManager.STREAM_VOICE_CALL:
        return R.drawable.quantum_ic_phone_white_24;
      case AudioManager.STREAM_RING:
        return R.drawable.quantum_ic_vibration_white_24;
      case AudioManager.STREAM_ALARM:
        return R.drawable.quantum_ic_alarm_white_24;
      case AudioManager.STREAM_ACCESSIBILITY:
        return R.drawable.quantum_ic_accessibility_new_white_24;
    }
    return 0;
  }

  @Override
  public String getText() {
    switch (volumeStreamType) {
      case AudioManager.STREAM_MUSIC:
        return context.getString(R.string.volume_menu_media);
      case AudioManager.STREAM_VOICE_CALL:
        return context.getString(R.string.volume_menu_call);
      case AudioManager.STREAM_RING:
        return context.getString(R.string.volume_menu_ring);
      case AudioManager.STREAM_ALARM:
        return context.getString(R.string.volume_menu_alarm);
      case AudioManager.STREAM_ACCESSIBILITY:
        return context.getString(R.string.volume_menu_accessibility);
    }
    return "";
  }

  @Override
  public boolean shouldPopulateLayoutDynamically() {
    return false;
  }

  public int getVolumeStreamType() {
    return volumeStreamType;
  }

  static List<MenuItem> getVolumeTypeMenuItems(
      SwitchAccessService service, @Nullable SelectMenuItemListener selectMenuItemListener) {
    return getVolumeTypeMenuItems(service, service.getOverlayController(), selectMenuItemListener);
  }

  /*
   * We need a method that explicitly takes OverlayController as a parameter for testing purposes,
   * since mocking an accessibility service isn't allowed.
   */
  @VisibleForTesting
  static List<MenuItem> getVolumeTypeMenuItems(
      SwitchAccessService service,
      OverlayController overlayController,
      @Nullable SelectMenuItemListener selectMenuItemListener) {
    List<MenuItem> menuItems = new ArrayList<>();
    AudioManager audioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
    // Return an empty list if the audio manager is null.
    if (audioManager != null) {
      menuItems.add(
          new GroupedMenuItemForVolumeAction(
              service,
              overlayController,
              audioManager,
              AudioManager.STREAM_MUSIC,
              selectMenuItemListener));
      menuItems.add(
          new GroupedMenuItemForVolumeAction(
              service,
              overlayController,
              audioManager,
              AudioManager.STREAM_VOICE_CALL,
              selectMenuItemListener));
      menuItems.add(
          new GroupedMenuItemForVolumeAction(
              service,
              overlayController,
              audioManager,
              AudioManager.STREAM_RING,
              selectMenuItemListener));
      menuItems.add(
          new GroupedMenuItemForVolumeAction(
              service,
              overlayController,
              audioManager,
              AudioManager.STREAM_ALARM,
              selectMenuItemListener));

      // Only add the accessibility volume adjustment if using O or greater.
      if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
        menuItems.add(
            new GroupedMenuItemForVolumeAction(
                service,
                overlayController,
                audioManager,
                AudioManager.STREAM_ACCESSIBILITY,
                selectMenuItemListener));
      }
    }
    return menuItems;
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemEnumFromVolumeType(int volumeType) {
    switch (volumeType) {
      case AudioManager.STREAM_MUSIC:
        return SwitchAccessMenuItemEnum.MenuItem.VOLUME_SUBMENU_MEDIA;
      case AudioManager.STREAM_VOICE_CALL:
        return SwitchAccessMenuItemEnum.MenuItem.VOLUME_SUBMENU_CALL;
      case AudioManager.STREAM_RING:
        return SwitchAccessMenuItemEnum.MenuItem.VOLUME_SUBMENU_RING;
      case AudioManager.STREAM_ALARM:
        return SwitchAccessMenuItemEnum.MenuItem.VOLUME_SUBMENU_ALARM;
      case AudioManager.STREAM_ACCESSIBILITY:
        return SwitchAccessMenuItemEnum.MenuItem.VOLUME_SUBMENU_ACCESSIBILITY;
    }
    return SwitchAccessMenuItemEnum.MenuItem.ITEM_UNSPECIFIED;
  }

  private boolean canStreamBeMuted() {
    // TODO: Instead of hiding the mute button for alarm and accessibility, investigate
    // a more elegant way of determining is a volume stream can be muted and/or manually setting
    // the volume to 0 instead of using ADJUST_STREAM_MUTE. There's a bug in AudioManager that
    // causes some volume types on certain versions to not be muteable. Alarm and accessibility
    // volume are generally not considered "muteable" volume types, so manually hiding the mute
    // button for these streams doesn't significantly impact user experience.
    boolean isValidStreamType =
        (volumeStreamType != AudioManager.STREAM_ALARM)
            && (volumeStreamType != AudioManager.STREAM_ACCESSIBILITY);
    return isValidStreamType
        && ((audioManager != null) && (audioManager.getStreamMinVolume(volumeStreamType) == 0));
  }
}
