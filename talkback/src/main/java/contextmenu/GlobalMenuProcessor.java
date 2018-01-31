/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.contextmenu;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.controller.DimScreenController;
import com.google.android.accessibility.talkback.controller.DimScreenControllerApp;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Configure dynamic menu items on the global context menu. */
public class GlobalMenuProcessor {

  private TalkBackService mService;

  public GlobalMenuProcessor(TalkBackService service) {
    mService = service;
  }

  /**
   * Populates a {@link Menu} with dynamic items relevant to the current global TalkBack state.
   *
   * @param menu The menu to populate.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public boolean prepareMenu(ContextMenu menu) {
    // Decide whether to display TalkBack Settings, TTS Settings, and Dim Screen items.
    KeyguardManager keyguardManager =
        (KeyguardManager) mService.getSystemService(Context.KEYGUARD_SERVICE);
    boolean isUnlocked = !keyguardManager.inKeyguardRestrictedInputMode();

    // The Settings items can simply be hidden because they're not dynamically switched.
    menu.findItem(R.id.talkback_settings).setVisible(isUnlocked);
    menu.findItem(R.id.tts_settings).setVisible(isUnlocked);

    // The Dim Screen items need to be removed because they are dynamically added.
    menu.removeItem(R.id.enable_dimming);
    menu.removeItem(R.id.disable_dimming);

    // If accessibility on/off shortcut is available, hide suspend-talkback option.
    boolean showPause = !FormFactorUtils.getInstance(mService).hasAccessibilityShortcut();
    MenuItem pauseItem = menu.findItem(R.id.pause_feedback);
    if (pauseItem != null) {
      pauseItem.setVisible(showPause);
    }

    // The Languages menu item will be shown only if there is more than 1 language installed and
    // is populated dynamically.
    boolean shouldShowLanguageMenu = LanguageMenuProcessor.getInstalledLanguages(mService) != null;
    // If the menu already has languages menu item, remove it. This will avoid multiple language
    // options appearing in the global context menu for radial views. For list views, dismissing
    // the list menu, clears the menu. For radial menu, dismissing the menu just hides it and
    // does not clear it.
    menu.removeItem(R.id.language_menu);
    if (shouldShowLanguageMenu) {
      // Add the latest set of languages in the sub menu.
      ContextSubMenu subMenu =
          menu.addSubMenu(
              /* groupId= */ 0,
              /* itemId= */ R.id.language_menu,
              /* order= */ 0,
              mService.getString(R.string.language_options));
      LanguageMenuProcessor.prepareLanguageSubMenu(mService, menu.getMenuItemBuilder(), subMenu);
    }

    // Add Dim Screen items depending on API level and current state.
    if (DimScreenControllerApp.isSupported(mService) && isUnlocked) {
      // Decide whether to display the enable or disable dimming item.
      // We have to re-add them (as opposed to hiding) because they occupy the same
      // physical space in the radial menu.
      final DimScreenController dimScreenController = mService.getDimScreenController();
      if (dimScreenController.isDimmingEnabled()) {
        menu.add(
            R.id.group_corners,
            R.id.disable_dimming,
            getIntResource(R.integer.corner_SW),
            R.string.shortcut_disable_dimming);
      } else {
        menu.add(
            R.id.group_corners,
            R.id.enable_dimming,
            getIntResource(R.integer.corner_SW),
            R.string.shortcut_enable_dimming);
      }
    }

    // Global actions should not re-speak the current item description.
    int menuItemCount = menu.size();
    for (int i = 0; i < menuItemCount; ++i) {
      ContextMenuItem menuItem = menu.getItem(i);
      if (menuItem != null) {
        menuItem.setSkipRefocusEvents(true);
      }
    }

    // Mark items that will pop up an alert dialog.
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mService);
    setMenuItemShowsDialog(
        menu,
        R.id.pause_feedback,
        prefs.getBoolean(
            mService.getString(R.string.pref_show_suspension_confirmation_dialog), true));
    setMenuItemShowsDialog(
        menu,
        R.id.enable_dimming,
        prefs.getBoolean(
            mService.getString(R.string.pref_show_dim_screen_confirmation_dialog), true));

    return true;
  }

  private static void setMenuItemShowsDialog(ContextMenu menu, int itemId, boolean showsDialog) {
    ContextMenuItem item = menu.findItem(itemId);
    if (item != null) {
      item.setShowsAlertDialog(showsDialog);
    }
  }

  private int getIntResource(int resourceId) {
    return mService.getResources().getInteger(resourceId);
  }
}
