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

import android.content.SharedPreferences;
import android.view.Menu;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.controller.DimScreenController;
import com.google.android.accessibility.talkback.controller.DimScreenControllerApp;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.ScreenMonitor;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Configure dynamic menu items on the global context menu. */
public class GlobalMenuProcessor {

  private static final String TAG = "GlobalMenuProcessor";

  private TalkBackService service;

  public GlobalMenuProcessor(TalkBackService service) {
    this.service = service;
  }

  /**
   * Populates a {@link Menu} with dynamic items relevant to the current global TalkBack state.
   *
   * @param menu The menu to populate.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public boolean prepareMenu(ContextMenu menu) {
    boolean isNotLocked = !ScreenMonitor.isDeviceLocked(service);
    // Do not show talkback settings if phone is locked.
    menu.updateItemAvailability(isNotLocked, R.id.talkback_settings);
    LogUtils.d(TAG, "Talkback settings visibility set to: %s", isNotLocked);

    // If accessibility on/off shortcut is available, hide suspend-talkback option.
    boolean shouldShowTtsSettings = isNotLocked && SettingsUtils.allowLinksOutOfSettings(service);
    menu.updateItemAvailability(shouldShowTtsSettings, R.id.tts_settings);
    LogUtils.d(TAG, "TTS settings visibility set to: %s", shouldShowTtsSettings);

    boolean shouldShowPause = !FeatureSupport.hasAccessibilityShortcut(service);
    menu.updateItemAvailability(shouldShowPause, R.id.pause_feedback);
    LogUtils.d(TAG, "Pause feedback visibility set to: %s", shouldShowPause);

    // Screen search is not supported on watches.
    boolean shouldShowScreenSearch = !FeatureSupport.isWatch(service);
    menu.updateItemAvailability(shouldShowScreenSearch, R.id.screen_search);
    LogUtils.d(TAG, "Screen search visibility set to: %s", shouldShowScreenSearch);

    addLanguageMenuIfValid(menu);
    addDimOrBrightenScreen(menu);

    // Mark items that will pop up an alert dialog.
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    setMenuItemShowsDialog(
        menu,
        R.id.pause_feedback,
        prefs.getBoolean(
            service.getString(R.string.pref_show_suspension_confirmation_dialog), true));
    setMenuItemShowsDialog(
        menu,
        R.id.enable_dimming,
        prefs.getBoolean(
            service.getString(R.string.pref_show_dim_screen_confirmation_dialog), true));

    setMenuItemShowsDialog(
        menu,
        R.id.read_from_top,
        prefs.getBoolean(
            service.getString(R.string.pref_show_continuous_reading_mode_dialog), true));
    setMenuItemShowsDialog(
        menu,
        R.id.read_from_current,
        prefs.getBoolean(
            service.getString(R.string.pref_show_continuous_reading_mode_dialog), true));

    setMenuItemDeferredType(menu, R.id.screen_search, DeferredType.WINDOWS_STABLE);
    setSkipRefocusAndWindowAnnounce(menu, R.id.screen_search, true);

    setMenuItemDeferredType(menu, R.id.read_from_top, DeferredType.WINDOWS_STABLE);
    setSkipRefocusAndWindowAnnounce(menu, R.id.read_from_top, true);

    setMenuItemDeferredType(
        menu, R.id.read_from_current, DeferredType.ACCESSIBILITY_FOCUS_RECEIVED);
    setSkipRefocusAndWindowAnnounce(menu, R.id.read_from_current, true);

    setSkipRefocusAndWindowAnnounce(menu, R.id.spell_last_utterance, true);
    setSkipRefocusAndWindowAnnounce(menu, R.id.repeat_last_utterance, true);
    setSkipRefocusAndWindowAnnounce(menu, R.id.copy_last_utterance_to_clipboard, true);

    return true;
  }

  private void addLanguageMenuIfValid(ContextMenu menu) {
    // If the menu already has languages menu item, remove it. This will avoid multiple language
    // options appearing in the global context menu for radial views. For list views, dismissing
    // the list menu, clears the menu. For radial menu, dismissing the menu just hides it and
    // does not clear it.
    menu.removeItem(R.id.language_menu);
    // The Languages menu item will be shown only if there is more than 1 language installed and
    // is populated dynamically.
    boolean shouldShowLanguageMenu = LanguageMenuProcessor.getInstalledLanguages(service) != null;
    LogUtils.d(TAG, "Language menu added: " + shouldShowLanguageMenu);
    if (shouldShowLanguageMenu) {
      // Add the latest set of languages in the sub menu.
      ContextSubMenu subMenu =
          menu.addSubMenu(
              /* groupId= */ 0,
              /* itemId= */ R.id.language_menu,
              /* order= */ 0,
              service.getString(R.string.language_options));
      LanguageMenuProcessor.prepareLanguageSubMenu(service, menu.getMenuItemBuilder(), subMenu);
    }
  }

  private void addDimOrBrightenScreen(ContextMenu menu) {
    // Add Dim Screen items depending on API level and current device lock state.
    boolean shouldShowDimOrBrightenScreen = DimScreenControllerApp.isSupported(service);
    LogUtils.d(TAG, "Dim or Brighten screen functionality added: " + shouldShowDimOrBrightenScreen);
    final DimScreenController dimScreenController = service.getDimScreenController();
    // Decide whether to display the enable or disable dimming item.
    // We have to re-add them (as opposed to hiding) because they occupy the same
    // physical space in the radial menu.
    // TODO: Create a wrapper function to change visibility and remove item.
    if (dimScreenController.isDimmingEnabled()) {
      if (menu.findItem(R.id.disable_dimming) == null) {
        menu.add(
            R.id.group_corners,
            R.id.disable_dimming,
            getIntResource(R.integer.corner_SW),
            R.string.shortcut_disable_dimming);
      }
      menu.findItem(R.id.disable_dimming).setVisible(shouldShowDimOrBrightenScreen);
    } else {
      if (menu.findItem(R.id.enable_dimming) == null) {
        menu.add(
            R.id.group_corners,
            R.id.enable_dimming,
            getIntResource(R.integer.corner_SW),
            R.string.shortcut_enable_dimming);
      }
      menu.findItem(R.id.enable_dimming).setVisible(shouldShowDimOrBrightenScreen);
    }
  }


  private static void setMenuItemShowsDialog(ContextMenu menu, int itemId, boolean showsDialog) {
    ContextMenuItem item = menu.findItem(itemId);
    if (item != null) {
      item.setShowsAlertDialog(showsDialog);
    }
  }

  private static void setMenuItemDeferredType(
      ContextMenu menu, int itemId, DeferredType deferAction) {
    ContextMenuItem item = menu.findItem(itemId);
    if (item != null) {
      item.setDeferredType(deferAction);
    }
  }

  private static void setSkipRefocusAndWindowAnnounce(ContextMenu menu, int itemId, boolean skip) {
    ContextMenuItem item = menu.findItem(itemId);
    if (item != null) {
      item.setSkipRefocusEvents(skip);
      item.setSkipWindowEvents(skip);
    }
  }

  private int getIntResource(int resourceId) {
    return service.getResources().getInteger(resourceId);
  }
}
