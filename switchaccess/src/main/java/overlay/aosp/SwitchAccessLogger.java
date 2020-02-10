/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.preference.PreferenceActivity;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceActivity.PreferenceActivityEventListener;
import com.google.android.accessibility.switchaccess.keyboardactions.KeyboardAction;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessBluetoothEventTypeEnum.BluetoothEventType;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum.MenuItem;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardActivity;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardActivity.SetupScreenListener;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.ComparableBluetoothDevice.BluetoothDeviceActionListener;
import com.google.android.accessibility.switchaccess.ui.OverlayController.MenuListener;

/** Singleton instance meant for gathering analytics data. This class currently does nothing. */
public class SwitchAccessLogger
    implements KeyboardAction.KeyboardActionListener,
        ScreenViewListener,
        BluetoothDeviceActionListener,
        SelectMenuItemListener,
        SetupScreenListener,
        MenuListener,
        PreferenceActivityEventListener {

  private static SwitchAccessLogger analytics;

  private SwitchAccessLogger(@SuppressWarnings("unused") Context context) {}

  public static SwitchAccessLogger getOrCreateInstance(SwitchAccessService service) {
    return createSwitchAccessLogger(service);
  }

  public static SwitchAccessLogger getOrCreateInstance(PreferenceActivity activity) {
    return createSwitchAccessLogger(activity);
  }

  public static SwitchAccessLogger getOrCreateInstance(SetupWizardActivity activity) {
    return createSwitchAccessLogger(activity);
  }

  public static SwitchAccessLogger getInstanceIfExists() {
    return analytics;
  }

  public void stop(PreferenceActivity activity) {}

  public void stop(SetupWizardActivity activity) {}

  public void stop(SwitchAccessService service) {}

  @Override
  public void onKeyboardAction(int preferenceIdForAction) {}

  @Override
  public void onScreenShown(String screenName) {}

  @Override
  public void onBluetoothDeviceAction(BluetoothEventType eventType) {}

  @Override
  public void onMenuItemSelected(MenuItem menuItem) {}

  @Override
  public void onSetupScreenShown(SetupScreen setupScreen) {}

  @Override
  public void onMenuShown(MenuType type, int menuId) {}

  @Override
  public void onMenuClosed(int menuId) {}

  @Override
  public void onPreferenceActivityShown() {}

  @Override
  public void onPreferenceChanged(String key) {}

  @Override
  public void onPreferenceActivityHidden() {}

  private static SwitchAccessLogger createSwitchAccessLogger(Context context) {
    if (analytics == null) {
      analytics = new SwitchAccessLogger(context);
    }
    return analytics;
  }
}
