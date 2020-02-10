/*
 * Copyright (C) 2016 Google Inc.
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

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Holds data required to create items appearing in the global actions menu. */
public class GlobalMenuItem extends MenuItem {

  private static final String TAG = "GlobalMenuItem";

  final AccessibilityService service;
  private final int iconResource;
  private final String text;
  private final MenuItemOnClickListener onClickListener;

  /**
   * Returns a list of global menu items appropriate for the current configuration of Switch Access
   * and Android version.
   *
   * @param service The accessibility service that can be used to perform global actions
   */
  public static List<MenuItem> getGlobalMenuItemList(
      final AccessibilityService service,
      @Nullable final SelectMenuItemListener selectMenuItemListener) {
    List<MenuItem> menu = new ArrayList<>();
    // Back.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_back,
            service.getString(R.string.global_action_back),
            service,
            AccessibilityService.GLOBAL_ACTION_BACK,
            selectMenuItemListener));
    // Home.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_home,
            service.getString(R.string.global_action_home),
            service,
            AccessibilityService.GLOBAL_ACTION_HOME,
            selectMenuItemListener));
    // Overview.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_overview,
            service.getString(R.string.global_action_overview),
            service,
            AccessibilityService.GLOBAL_ACTION_RECENTS,
            selectMenuItemListener));
    // Notifications.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_notifications,
            service.getString(R.string.global_action_notifications),
            service,
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            selectMenuItemListener));
    // Quick Settings.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_settings,
            service.getString(R.string.global_action_quick_settings),
            service,
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
            selectMenuItemListener));
    // Volume controls
      List<MenuItem> volumeSubMenuItems =
          GroupedMenuItemForVolumeAction.getVolumeTypeMenuItems(
              (SwitchAccessService) service, selectMenuItemListener);
      volumeSubMenuItems.add(
          new GlobalMenuItem(
              R.drawable.ic_volume_settings,
              service.getString(R.string.volume_settings),
              service,
              0,
              selectMenuItemListener) {
            @Override
            public MenuItemOnClickListener getOnClickListener() {
              return new MenuItemOnClickListener() {
                @Override
                public void onClick() {
                  Intent intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
                  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                  try {
                    service.startActivity(intent);
                  } catch (ActivityNotFoundException e) {
                    LogUtils.e(
                        TAG,
                        "Sound settings activity not found- unable to launch volume settings.");
                  }
                  if (selectMenuItemListener != null) {
                    selectMenuItemListener.onMenuItemSelected(
                        SwitchAccessMenuItemEnum.MenuItem.VOLUME_SUBMENU_VOLUME_SETTINGS);
                  }
                }
              };
            }
          });

      menu.add(
          new SimpleGroupedMenuItem(
              ((SwitchAccessService) service).getOverlayController(),
              R.drawable.quantum_ic_volume_up_white_24,
              service.getString(R.string.global_menu_volume_heading),
              volumeSubMenuItems,
              SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_VOLUME,
              selectMenuItemListener));
    // Power Dialog.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_power_options,
            service.getString(R.string.global_action_power_options),
            service,
            AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
            selectMenuItemListener));

    // Auto-select.
    String autoSelectText =
        SwitchAccessPreferenceUtils.isAutoselectEnabled(service)
            ? service.getString(R.string.switch_access_global_menu_disable_autoselect)
            : service.getString(R.string.switch_access_global_menu_enable_autoselect);
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_auto_select, autoSelectText, service, 0, selectMenuItemListener) {

          @Override
          public MenuItemOnClickListener getOnClickListener() {
            return new MenuItemOnClickListener() {

              @Override
              public void onClick() {
                boolean autoSelectEnabled =
                    SwitchAccessPreferenceUtils.isAutoselectEnabled(service);
                SwitchAccessPreferenceUtils.setAutoselectEnabled(service, !autoSelectEnabled);

                if (selectMenuItemListener != null) {
                  if (autoSelectEnabled) {
                    selectMenuItemListener.onMenuItemSelected(
                        SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_DISABLE_AUTOSELECT);
                  } else {
                    selectMenuItemListener.onMenuItemSelected(
                        SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_ENABLE_AUTOSELECT);
                  }
                }
              }
            };
          }
        });

    // Scan type (box scan/point scan). Only show this if we're running Android N or higher and
    // animations are enabled.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        && ((Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            || ValueAnimator.areAnimatorsEnabled()))) {
      String pointScanText =
          SwitchAccessPreferenceUtils.isPointScanEnabled(service)
              ? service.getString(R.string.box_scan)
              : service.getString(R.string.point_scan);
      menu.add(
          new GlobalMenuItem(0, pointScanText, service, 0, selectMenuItemListener) {
            @Override
            public int getIconResource() {
              return SwitchAccessPreferenceUtils.isPointScanEnabled(this.service)
                  ? R.drawable.ic_box_scan
                  : R.drawable.ic_point_scan;
            }

            @Override
            public MenuItemOnClickListener getOnClickListener() {
              return new MenuItemOnClickListener() {

                @Override
                public void onClick() {
                  boolean pointScanEnabled =
                      SwitchAccessPreferenceUtils.isPointScanEnabled(service);
                  SwitchAccessPreferenceUtils.setPointScanEnabled(service, !pointScanEnabled);

                  if (selectMenuItemListener != null) {
                    if (pointScanEnabled) {
                      selectMenuItemListener.onMenuItemSelected(
                          SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_EXIT_POINT_SCAN);
                    } else {
                      selectMenuItemListener.onMenuItemSelected(
                          SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_POINT_SCAN);
                    }
                  }
                }
              };
            }
          });
    }

    // Disable Screen Switch. Only show this if the screen is currently being used a switch.
    if (SwitchAccessPreferenceUtils.isScreenSwitchEnabled(service)) {
      // TODO: Create or find a unique icon for a button that resumes normal
      // screen usage from screen-as-a-switch.
      String disableScreenSwitchText =
          service.getString(R.string.switch_access_global_menu_disable_screen_switch);
      menu.add(
          new GlobalMenuItem(-1, disableScreenSwitchText, service, 0, selectMenuItemListener) {

            @Override
            public MenuItemOnClickListener getOnClickListener() {
              return new MenuItemOnClickListener() {
                @Override
                public void onClick() {
                  SwitchAccessPreferenceUtils.disableScreenSwitch(service);
                }
              };
            }
          });
    }
    return menu;
  }

  /**
   * @param iconResource Resource id for the icon that represents this action
   * @param text Human readable description of this action
   * @param service The accessibility service that will be used to perform the given global action
   * @param action The global action that corresponds to this menu item
   */
  private GlobalMenuItem(
      int iconResource,
      String text,
      final AccessibilityService service,
      final int action,
      @Nullable final SelectMenuItemListener selectMenuItemListener) {
    onClickListener =
        new MenuItemOnClickListener() {

          @Override
          public void onClick() {
            service.performGlobalAction(action);

            if (selectMenuItemListener != null) {
              switch (action) {
                case AccessibilityService.GLOBAL_ACTION_BACK:
                  selectMenuItemListener.onMenuItemSelected(
                      SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_BACK);
                  break;
                case AccessibilityService.GLOBAL_ACTION_HOME:
                  selectMenuItemListener.onMenuItemSelected(
                      SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_HOME);
                  break;
                case AccessibilityService.GLOBAL_ACTION_RECENTS:
                  selectMenuItemListener.onMenuItemSelected(
                      SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_RECENTS);
                  break;
                case AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS:
                  selectMenuItemListener.onMenuItemSelected(
                      SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_NOTIFICATIONS);
                  break;
                case AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS:
                  selectMenuItemListener.onMenuItemSelected(
                      SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_QUICK_SETTINGS);
                  break;
                case AccessibilityService.GLOBAL_ACTION_POWER_DIALOG:
                  selectMenuItemListener.onMenuItemSelected(
                      SwitchAccessMenuItemEnum.MenuItem.GLOBAL_MENU_POWER_DIALOG);
                  break;
                default:
                  // This should never happen.
                  // TODO: Use
                  // com.google.android.libraries.accessibility.utils.log.LogUtils
                  // because it has more convenience methods
                  LogUtils.e(TAG, "Invalid global action: %d", action);
              }
            }
          }
        };
    this.service = service;
    this.iconResource = iconResource;
    this.text = text;
  }

  @Override
  public int getIconResource() {
    return iconResource;
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public MenuItemOnClickListener getOnClickListener() {
    return onClickListener;
  }
}
