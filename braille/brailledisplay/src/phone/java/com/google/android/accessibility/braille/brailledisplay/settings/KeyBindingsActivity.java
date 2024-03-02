/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.settings;

import static com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category.BASIC;
import static com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category.BRAILLE_SETTINGS;
import static com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category.EDITING;
import static com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category.NAVIGATION;
import static com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category.SYSTEM_ACTIONS;
import static com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category.TALKBACK_FEATURES;
import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleKeyBinding;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/** Shows the category of key bindings for the currently connected Braille display. */
public class KeyBindingsActivity extends PreferencesActivity {
  public static final String PROPERTY_KEY = "property_key";
  private static final String TAG = "KeyBindingsActivity";

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new KeyBindingsFragment();
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  /** Fragment that holds the key binding preference. */
  public static class KeyBindingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.key_bindings);
      BrailleDisplayProperties props = getActivity().getIntent().getParcelableExtra(PROPERTY_KEY);
      for (Map.Entry<SupportedCommand.Category, Integer> entry : createCategoryMap().entrySet()) {
        if (props == null) {
          setPreferenceClickListener(entry.getKey(), entry.getValue());
        } else {
          if (isCategoryCommandSupported(props, entry.getKey())) {
            setPreferenceClickListener(entry.getKey(), entry.getValue());
          } else {
            getPreferenceScreen().removePreferenceRecursively(getString(entry.getValue()));
          }
        }
      }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
        case android.R.id.home:
          Intent intent = new Intent(getContext(), BrailleDisplaySettingsActivity.class);
          intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
          startActivity(intent);
          return true;
        default:
          return super.onOptionsItemSelected(item);
      }
    }

    private void setPreferenceClickListener(SupportedCommand.Category category, int keyId) {
      BrailleDisplayProperties displayProperties =
          getActivity().getIntent().getParcelableExtra(PROPERTY_KEY);
      Preference preference = findPreference(getString(keyId));
      preference.setOnPreferenceClickListener(
          preference1 -> {
            Intent intent = new Intent(getContext(), KeyBindingsCommandActivity.class);
            intent.putExtra(KeyBindingsCommandActivity.TITLE_KEY, preference.getTitle());
            intent.putExtra(KeyBindingsCommandActivity.TYPE_KEY, category.name());
            intent.putExtra(PROPERTY_KEY, displayProperties);
            getContext().startActivity(intent);
            return true;
          });
    }

    private boolean isCategoryCommandSupported(
        BrailleDisplayProperties displayProperties, SupportedCommand.Category category) {
      ArrayList<BrailleKeyBinding> sortedBindings =
          BrailleKeyBindingUtils.getSortedBindingsForDisplay(displayProperties);
      return BrailleKeyBindingUtils.getSupportedCommands(getContext()).stream()
          .filter(supportedCommand -> supportedCommand.getCategory().equals(category))
          .anyMatch(
              supportedCommand ->
                  BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                          supportedCommand.getCommand(), sortedBindings)
                      != null);
    }

    private Map<SupportedCommand.Category, Integer> createCategoryMap() {
      Map<SupportedCommand.Category, Integer> categoryMap =
          new EnumMap<>(SupportedCommand.Category.class);
      categoryMap.put(BASIC, R.string.pref_key_bd_keybindings_basic);
      categoryMap.put(NAVIGATION, R.string.pref_key_bd_keybindings_navigation);
      categoryMap.put(SYSTEM_ACTIONS, R.string.pref_key_bd_keybindings_system_actions);
      categoryMap.put(TALKBACK_FEATURES, R.string.pref_key_bd_keybindings_talkback_features);
      categoryMap.put(BRAILLE_SETTINGS, R.string.pref_key_bd_keybindings_braille_settings);
      categoryMap.put(EDITING, R.string.pref_key_bd_keybindings_editing);
      return categoryMap;
    }
  }
}
