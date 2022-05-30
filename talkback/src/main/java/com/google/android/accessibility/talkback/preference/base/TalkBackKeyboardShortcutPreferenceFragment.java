/*
 * Copyright 2015 Google Inc.
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

package com.google.android.accessibility.talkback.preference.base;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.utils.AlertDialogUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.keyboard.DefaultKeyComboModel;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.keyboard.KeyComboModel;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Panel holding a set of keyboard shortcut preferences. */
public class TalkBackKeyboardShortcutPreferenceFragment extends TalkbackBaseFragment {
  private static final String TAG = "TalkBackKeyboardShortcutPreferenceFragment";

  private static final int[] HIDDEN_SHORTCUT_KEY_IDS_IN_ARC = {
    R.string.keycombo_shortcut_global_suspend,
    R.string.keycombo_shortcut_global_home,
    R.string.keycombo_shortcut_global_recents,
    R.string.keycombo_shortcut_global_notifications,
    R.string.keycombo_shortcut_navigate_next_window,
    R.string.keycombo_shortcut_navigate_previous_window
  };

  private static final int[] HIDDEN_SHORTCUT_KEY_IDS_IN_NON_ARC = {
    R.string.keycombo_shortcut_open_manage_keyboard_shortcuts,
    R.string.keycombo_shortcut_open_talkback_settings
  };

  private String keymap;
  private SharedPreferences prefs;
  private @Nullable String triggerModifierToBeSet;

  public TalkBackKeyboardShortcutPreferenceFragment() {
    super(R.xml.key_combo_preferences);
  }

  public static String getFragmentName() {
    return TAG;
  }

  private static void focusCancelButton(AlertDialog alertDialog) {
    Button cancelButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    cancelButton.setFocusableInTouchMode(true);
    cancelButton.requestFocus();
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_manage_keyboard_shortcuts);
  }

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    if (preference instanceof KeyboardShortcutDialogPreference) {
      PreferenceDialogFragmentCompat dialogFragment =
          ((KeyboardShortcutDialogPreference) preference).createDialogFragment();
      dialogFragment.setTargetFragment(this, 0);
      dialogFragment.show(getParentFragmentManager(), preference.getKey());
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }

  // TODO: Refactor KeyComboManager.
  /** Updates fragment whenever their values change. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (SharedPreferences prefs, String key) -> {
        if (TextUtils.equals(key, getString(R.string.pref_select_keymap_key))) {
          keymap = getKeymap();
          // Set new key combo model.
          KeyComboManager keyComboManager = getKeyComboManager();
          keyComboManager.setKeyComboModel(keyComboManager.createKeyComboModelFor(keymap));
          updateFragment();
        } else if (TextUtils.equals(
            key, getString(R.string.pref_default_keymap_trigger_modifier_key))) {
          updateFragment();
        }
      };

  private final DialogInterface.OnClickListener chooseTriggerModifierConfirmDialogPositive =
      (DialogInterface dialogInterface, int i) -> {
        resetKeymap();

        KeyComboModel keyComboModel = getKeyComboManager().getKeyComboModel();

        // Update preference.
        String preferenceKeyForTriggerModifier = keyComboModel.getPreferenceKeyForTriggerModifier();
        ListPreference listPreference =
            (ListPreference) findPreference(preferenceKeyForTriggerModifier);
        listPreference.setValue(triggerModifierToBeSet);

        // Update KeyComboModel.
        keyComboModel.notifyTriggerModifierChanged();

        // Update UI.
        Set<String> keySet = getKeyComboManager().getKeyComboModel().getKeyComboCodeMap().keySet();
        for (String key : keySet) {
          KeyboardShortcutDialogPreference preference =
              (KeyboardShortcutDialogPreference) findPreference(key);
          preference.onTriggerModifierChanged();
        }

        // Announce that trigger modifier has changed.
        CharSequence[] entries = listPreference.getEntries();
        CharSequence newTriggerModifier =
            entries[listPreference.findIndexOfValue(triggerModifierToBeSet)];
        PreferencesActivityUtils.announceText(
            getString(R.string.keycombo_menu_announce_new_trigger_modifier, newTriggerModifier),
            getActivity());

        triggerModifierToBeSet = null;
      };

  private final DialogInterface.OnClickListener resetKeymapConfirmDialogPositive =
      (DialogInterface dialogInterface, int i) -> {
        resetKeymap();

        dialogInterface.dismiss();

        PreferencesActivityUtils.announceText(
            getString(R.string.keycombo_menu_announce_reset_keymap), getActivity());
      };

  private final Preference.OnPreferenceClickListener resetKeymapPreferenceClickListener =
      (Preference preference) -> {
        // Show confirm dialog.
        AlertDialog dialog =
            AlertDialogUtils.builder(getActivity())
                .setTitle(getString(R.string.keycombo_menu_reset_keymap))
                .setMessage(getString(R.string.message_in_reset_keymap_confirm_dialog))
                .setPositiveButton(
                    R.string.reset_button_in_reset_keymap_confirm_dialog,
                    resetKeymapConfirmDialogPositive)
                .setNegativeButton(
                    android.R.string.cancel,
                    (DialogInterface dialogInterface, int i) -> dialogInterface.cancel())
                .create();
        dialog.show();

        focusCancelButton(dialog);

        return true;
      };

  private final OnPreferenceChangeListener preferenceChangeListener =
      (Preference preference, Object newValue) -> {
        String preferenceKeyForTriggerModifier =
            getKeyComboManager().getKeyComboModel().getPreferenceKeyForTriggerModifier();
        if (preference instanceof KeyboardShortcutDialogPreference && newValue instanceof Long) {
          KeyboardShortcutDialogPreference keyBoardPreference =
              (KeyboardShortcutDialogPreference) preference;
          keyBoardPreference.setKeyComboCode((Long) newValue);
          keyBoardPreference.notifyChanged();
        } else if (preference.getKey() != null
            && preference.getKey().equals(getString(R.string.pref_select_keymap_key))
            && newValue instanceof String) {
          String newKeymap = (String) newValue;

          // Do nothing if keymap is the same.
          if (keymap.equals(newKeymap)) {
            return false;
          }

          // Announce new keymap.
          PreferencesActivityUtils.announceText(
              String.format(
                  getString(R.string.keycombo_menu_announce_active_keymap),
                  getKeymapName(newKeymap)),
              getActivity());
        } else if (preference.getKey() != null
            && preference.getKey().equals(preferenceKeyForTriggerModifier)
            && newValue instanceof String) {
          triggerModifierToBeSet = (String) newValue;

          ListPreference listPreference = (ListPreference) preference;
          if (listPreference.getValue().equals(triggerModifierToBeSet)) {
            return false;
          }

          CharSequence[] entries = listPreference.getEntries();
          CharSequence newTriggerModifier =
              entries[listPreference.findIndexOfValue(triggerModifierToBeSet)];

          // Show alert dialog.
          AlertDialog dialog =
              AlertDialogUtils.builder(getActivity())
                  .setTitle(R.string.keycombo_menu_alert_title_trigger_modifier)
                  .setMessage(
                      getString(
                          R.string.keycombo_menu_alert_message_trigger_modifier,
                          newTriggerModifier))
                  .setPositiveButton(
                      android.R.string.ok, chooseTriggerModifierConfirmDialogPositive)
                  .setNegativeButton(
                      android.R.string.cancel,
                      (DialogInterface dialogInterface, int i) -> triggerModifierToBeSet = null)
                  .create();
          dialog.show();

          focusCancelButton(dialog);
          return false;
        }
        return true;
      };

  void performClickOnResetKeymapForTesting() {
    Preference resetKeymapPreference = findPreference(getString(R.string.pref_reset_keymap_key));
    resetKeymapPreferenceClickListener.onPreferenceClick(resetKeymapPreference);
  }

  private void resetKeymap() {
    KeyComboModel keyComboModel = getKeyComboManager().getKeyComboModel();

    for (String key : keyComboModel.getKeyComboCodeMap().keySet()) {
      long defaultKeyComboCode = keyComboModel.getDefaultKeyComboCode(key);

      // Do nothing if key combo code is not changed from default one.
      if (defaultKeyComboCode == keyComboModel.getKeyComboCodeForKey(key)) {
        continue;
      }

      // Save with default key combo code.
      keyComboModel.saveKeyComboCode(key, defaultKeyComboCode);

      // Update UI.
      KeyboardShortcutDialogPreference keyboardShortcutDialogPreference =
          (KeyboardShortcutDialogPreference) findPreference(key);
      keyboardShortcutDialogPreference.setKeyComboCode(defaultKeyComboCode);
      keyboardShortcutDialogPreference.notifyChanged();
    }
  }

  private KeyComboManager getKeyComboManager() {
    TalkBackService talkBackService = TalkBackService.getInstance();
    return talkBackService == null
        ? KeyComboManager.create(getActivity())
        : talkBackService.getKeyComboManager();
  }

  private @Nullable String getKeymapName(String keymap) {
    if (keymap.equals(getString(R.string.classic_keymap_entry_value))) {
      return getString(R.string.value_classic_keymap);
    } else if (keymap.equals(getString(R.string.default_keymap_entry_value))) {
      return getString(R.string.value_default_keymap);
    }
    return null;
  }

  private String getKeymap() {
    KeyComboManager keyComboManager = getKeyComboManager();

    return keyComboManager.getKeymap();
  }

  private int getPreferenceResourceId() {
    if (TextUtils.equals(keymap, getContext().getString(R.string.default_keymap_entry_value))) {
      return R.xml.default_key_combo_preferences;
    }
    // In addition to R.string.classic_keymap_entry_value, the others use
    // R.xml.key_combo_preferences
    return R.xml.key_combo_preferences;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);

    keymap = getKeymap();
    setPreferencesFromResource(getPreferenceResourceId(), rootKey);

    prefs = SharedPreferencesUtils.getSharedPreferences(getContext());
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

    updatePreference();
  }

  /** Updates Fragment when KeyComboModel changes or resets keymap. */
  private void updateFragment() {
    PreferenceSettingsUtils.setPreferencesFromResource(this, getPreferenceResourceId(), null);
    updatePreference();
  }

  /** Updates preference, including UI, by KeyComboModel. */
  private void updatePreference() {
    PreferenceScreen resetKeymapPreferenceScreen =
        (PreferenceScreen) findPreference(getString(R.string.pref_reset_keymap_key));
    resetKeymapPreferenceScreen.setOnPreferenceClickListener(resetKeymapPreferenceClickListener);

    boolean isArc = FeatureSupport.isArc();

    // Hide select keymap preference in Arc if current keymap is already set to default
    // keymap.
    if (isArc && getKeyComboManager().getKeyComboModel() instanceof DefaultKeyComboModel) {
      PreferenceCategory keymapPreferenceCategory =
          (PreferenceCategory)
              getPreferenceScreen().findPreference(getString(R.string.pref_keymap_category_key));
      ListPreference keymapListPreference =
          (ListPreference)
              keymapPreferenceCategory.findPreference(getString(R.string.pref_select_keymap_key));
      keymapPreferenceCategory.removePreference(keymapListPreference);
    }

    int[] hiddenShortcutKeyIds =
        isArc ? HIDDEN_SHORTCUT_KEY_IDS_IN_ARC : HIDDEN_SHORTCUT_KEY_IDS_IN_NON_ARC;
    Set<String> hiddenShortcutKeys = new HashSet<>();
    for (int id : hiddenShortcutKeyIds) {
      hiddenShortcutKeys.add(getString(id));
    }

    if (FeatureSupport.hasAccessibilityShortcut(getActivity())) {
      hiddenShortcutKeys.add(getString(R.string.keycombo_shortcut_global_suspend));
    }

    initPreferenceUIs(getPreferenceScreen(), hiddenShortcutKeys);
  }

  /**
   * Initialize preference UIs.
   *
   * @param root Root element of preference UIs.
   * @param hiddenShortcutKeys Set of shortcut keys which will be made hidden. Note that preference
   *     is made hidden only when its shortcut is disabled in the key combo model.
   */
  private void initPreferenceUIs(PreferenceGroup root, Set<String> hiddenShortcutKeys) {
    if (root == null) {
      return;
    }

    final KeyComboModel keyComboModel = getKeyComboManager().getKeyComboModel();
    final String preferenceKeyForTriggerModifier =
        keyComboModel.getPreferenceKeyForTriggerModifier();

    for (int i = 0; i < root.getPreferenceCount(); i++) {
      final Preference preference = root.getPreference(i);
      final String key = preference.getKey();

      if (key != null
          && preference instanceof KeyboardShortcutDialogPreference
          && !keyComboModel.getKeyComboCodeMap().containsKey(key)) {
        // Disable or hide preference of unavailable key combo on this device.
        if (hiddenShortcutKeys.contains(key)) {
          root.removePreference(preference);
          i--;
        } else {
          preference.setEnabled(false);
        }
      } else if (preference instanceof KeyboardShortcutDialogPreference
          || (key != null && key.equals(getString(R.string.pref_select_keymap_key)))
          || (key != null && key.equals(preferenceKeyForTriggerModifier))) {
        // Set onPreferenceChangeListener.
        preference.setOnPreferenceChangeListener(preferenceChangeListener);
      } else if (preference instanceof PreferenceGroup) {
        initPreferenceUIs((PreferenceGroup) preference, hiddenShortcutKeys);
      }
    }
  }
}
