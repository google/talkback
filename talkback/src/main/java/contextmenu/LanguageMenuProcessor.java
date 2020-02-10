/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.FailoverTextToSpeech;
import com.google.android.accessibility.utils.ScreenMonitor;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configure dynamic menu items on the language context menu. */
public class LanguageMenuProcessor {

  private static final String TAG = "LanguageMenuProcessor";

  private static List<ContextMenuItem> getMenuItems(
      TalkBackService service, ContextMenuItemBuilder menuItemBuilder) {
    if (service == null) {
      return null;
    }

    HashMap<Locale, String> languagesAvailable = getInstalledLanguages(service);

    if (languagesAvailable == null) {
      return null;
    }
    LanguageMenuItemClickListener clickListener =
        new LanguageMenuItemClickListener(service, languagesAvailable);
    List<ContextMenuItem> menuItems = new ArrayList<>();

    for (String value : languagesAvailable.values()) {
      ContextMenuItem val =
          menuItemBuilder.createMenuItem(service, R.id.group_language, Menu.NONE, Menu.NONE, value);
      val.setOnMenuItemClickListener(clickListener);
      menuItems.add(val);
    }

    return menuItems;
  }

  /**
   * Populates a {@link Menu} with dynamic items relevant to the current global TalkBack state. This
   * is called when language menu is opened via a gesture or keyboard shortcut.
   *
   * @param menu The menu to populate.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public static boolean prepareLanguageMenu(TalkBackService service, ContextMenu menu) {
    List<ContextMenuItem> menuItems = getMenuItems(service, menu.getMenuItemBuilder());
    if (menuItems == null) {
      return false;
    }
    for (ContextMenuItem menuItem : menuItems) {
      menu.add(menuItem);
    }

    return menu.size() != 0;
  }

  /**
   * Populates a {@link android.view.SubMenu} with dynamic items relevant to the current global
   * TalkBack state. This is called when language menu is opened via global context menu.
   *
   * @param subMenu The subMenu to populate.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public static boolean prepareLanguageSubMenu(
      TalkBackService service, ContextMenuItemBuilder menuItemBuilder, ContextSubMenu subMenu) {
    List<ContextMenuItem> menuItems = getMenuItems(service, menuItemBuilder);
    if (menuItems == null) {
      return false;
    }
    for (ContextMenuItem menuItem : menuItems) {
      subMenu.add(menuItem);
    }
    return true;
  }

  /**
   * This method returns null if Accessibility service is null, if its the lock screen or if the
   * number of languages installed on the device is not more than 1.
   */
  public static HashMap<Locale, String> getInstalledLanguages(TalkBackService service) {
    if (service == null) {
      return null;
    }
    // We do not want to show languages menu if the user is on the lock screen.
    if (ScreenMonitor.isDeviceLocked(service)) {
      return null;
    }

    HashMap<Locale, String> languagesAvailable = new HashMap<Locale, String>();

    languagesAvailable.put(null, service.getString(R.string.reset_user_language_preference));

    FailoverTextToSpeech mTts = service.getSpeechController().getFailoverTts();
    Set<Voice> voices;
    try {
      voices = mTts.getEngineInstance().getVoices();
    } catch (Exception e) {
      LogUtils.e(TAG, "TTS client crashed while generating language menu items");
      e.printStackTrace();
      return null;
    }
    if (voices == null) {
      return null;
    }

    for (Voice voice : voices) {
      Set<String> features = voice.getFeatures();
      // Filtering the installed voices to add to the menu
      if ((features != null)
          && !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
          && !voice.isNetworkConnectionRequired()) {

        String country = voice.getLocale().getDisplayCountry();
        if (!TextUtils.isEmpty(country)) {
          String menuItem =
              service.getString(
                  R.string.template_language_options_menu_item,
                  voice.getLocale().getDisplayLanguage(),
                  voice.getLocale().getDisplayCountry());
          languagesAvailable.put(voice.getLocale(), menuItem);
        } else {
          languagesAvailable.put(voice.getLocale(), voice.getLocale().getDisplayLanguage());
        }
      }
    }
    // Do not populate the menu if there is just one language installed.
    // In the language menu, we have one default menu item labelled as "Reset". We want to ignore
    // that item while deciding if we want to populate the menu. The menu would be populated only if
    // we have 3 or more items in the list (including Reset).
    if (languagesAvailable.size() <= 2) {
      return null;
    }
    return languagesAvailable;
  }

  private static class LanguageMenuItemClickListener implements MenuItem.OnMenuItemClickListener {
    private final HashMap<Locale, String> res;
    private TalkBackService service;

    public LanguageMenuItemClickListener(TalkBackService service, HashMap<Locale, String> result) {
      res = result;
      this.service = service;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      Locale locale = null;
      if (item == null) {
        return false;
      }
      final CharSequence value = item.getTitle();

      // Scanning all entries is fine because the collection of locales is small.
      for (Map.Entry<Locale, String> entry : res.entrySet()) {
        if (TextUtils.equals(value, entry.getValue())) {
          locale = entry.getKey();
          break;
        }
      }
      service.setUserPreferredLocale(locale);
      return true;
    }
  }
}
