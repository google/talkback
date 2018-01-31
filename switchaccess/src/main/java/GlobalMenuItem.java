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

package com.google.android.accessibility.switchaccess;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Holds data required to create items appearing in the global actions menu. */
public class GlobalMenuItem extends MenuItem {

  protected AccessibilityService mService;

  /**
   * Returns a list of global menu items appropriate for the current configuration of Switch Access
   * and Android version.
   *
   * @param service The accessibility service that can be used to perform global actions
   */
  public static List<MenuItem> getGlobalMenuItemList(final AccessibilityService service) {
    List<MenuItem> menu = new ArrayList<>();
    // Back.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_back,
            service.getString(R.string.global_action_back),
            service,
            AccessibilityService.GLOBAL_ACTION_BACK));
    // Home.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_home,
            service.getString(R.string.global_action_home),
            service,
            AccessibilityService.GLOBAL_ACTION_HOME));
    // Overview.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_overview,
            service.getString(R.string.global_action_overview),
            service,
            AccessibilityService.GLOBAL_ACTION_RECENTS));
    // Notifications.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_notifications,
            service.getString(R.string.global_action_notifications),
            service,
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));
    // Quick Settings.
    menu.add(
        new GlobalMenuItem(
            R.drawable.ic_settings,
            service.getString(R.string.global_action_quick_settings),
            service,
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS));

    // Auto-select.
    menu.add(
        new GlobalMenuItem(R.drawable.ic_auto_select, null, service, 0) {
          @Override
          public CharSequence getText() {
            return SwitchAccessPreferenceActivity.isAutoselectEnabled(mService)
                ? service.getString(R.string.switch_access_global_menu_disable_autoselect)
                : service.getString(R.string.switch_access_global_menu_enable_autoselect);
          }

          @Override
          public View.OnClickListener getOnClickListener() {
            return new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                SwitchAccessPreferenceActivity.setAutoselectEnabled(
                    mService, !SwitchAccessPreferenceActivity.isAutoselectEnabled(mService));
              }
            };
          }
        });

    // Scan type (box scan/point scan). Only show this if we're running Android N or higher.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      menu.add(
          new GlobalMenuItem(0, null, service, 0) {
            @Override
            public int getIconResource() {
              return SwitchAccessPreferenceActivity.isPointScanEnabled(mService)
                  ? R.drawable.ic_box_scan
                  : R.drawable.ic_point_scan;
            }

            @Override
            public CharSequence getText() {
              return SwitchAccessPreferenceActivity.isPointScanEnabled(mService)
                  ? service.getString(R.string.box_scan)
                  : service.getString(R.string.point_scan);
            }

            @Override
            public View.OnClickListener getOnClickListener() {
              return new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                  SwitchAccessPreferenceActivity.setPointScanEnabled(
                      mService, !SwitchAccessPreferenceActivity.isPointScanEnabled(mService));
                }
              };
            }
          });
    }

    return menu;
  }

  /**
   * @param iconResource Resource id for the icon that respresents this action
   * @param text Human readable description of this action
   * @param service The accessibility service that will be used to perform the given global action
   * @param action The global action that corresponds to this menu item
   */
  public GlobalMenuItem(
      int iconResource, CharSequence text, final AccessibilityService service, final int action) {
    super(
        iconResource,
        text,
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            service.performGlobalAction(action);
          }
        });
    mService = service;
  }
}
