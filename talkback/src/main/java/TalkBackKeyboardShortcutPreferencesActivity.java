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

package com.google.android.accessibility.talkback;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.keyboard.DefaultKeyComboModel;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.keyboard.KeyComboModel;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Activity used to set TalkBack's keyboard shortcut preferences. */
public class TalkBackKeyboardShortcutPreferencesActivity extends AppCompatActivity {

  /** Utility method for announcing text via accessibility event. */
  public static void announceText(String text, Context context) {
    AccessibilityManager accessibilityManager =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (accessibilityManager.isEnabled()) {
      AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
      event.setContentDescription(text);
      accessibilityManager.sendAccessibilityEvent(event);
    }
  }

  private static void focusCancelButton(AlertDialog alertDialog) {
    Button cancelButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    cancelButton.setFocusableInTouchMode(true);
    cancelButton.requestFocus();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    TalkBackService talkBackService = TalkBackService.getInstance();
    KeyComboManager keyComboManager =
        talkBackService == null
            ? KeyComboManager.create(this)
            : talkBackService.getKeyComboManager();
    TalkBackKeyboardShortcutPreferenceFragment fragment =
        TalkBackKeyboardShortcutPreferenceFragment.createFor(keyComboManager.getKeymap());
    getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
  }

  @VisibleForTesting
  void resetKeymap() {
    TalkBackKeyboardShortcutPreferenceFragment fragment =
        (TalkBackKeyboardShortcutPreferenceFragment)
            getFragmentManager().findFragmentById(android.R.id.content);
    fragment.resetKeymap();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  public static class TalkBackKeyboardShortcutPreferenceFragment extends PreferenceFragment {
    private static final String BUNDLE_KEYMAP = "bundle_keymap";

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

    public static TalkBackKeyboardShortcutPreferenceFragment createFor(String keymap) {
      TalkBackKeyboardShortcutPreferenceFragment preferenceFragment =
          new TalkBackKeyboardShortcutPreferenceFragment();

      Bundle bundle = new Bundle();
      bundle.putString(BUNDLE_KEYMAP, keymap);
      preferenceFragment.setArguments(bundle);

      return preferenceFragment;
    }

    private String keymap;

    private final OnPreferenceChangeListener preferenceChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            String preferenceKeyForTriggerModifier =
                getKeyComboManager().getKeyComboModel().getPreferenceKeyForTriggerModifier();
            if (preference instanceof KeyboardShortcutDialogPreference
                && newValue instanceof Long) {
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

              // Replace preference fragment.
              TalkBackKeyboardShortcutPreferenceFragment fragment =
                  TalkBackKeyboardShortcutPreferenceFragment.createFor(newKeymap);
              getFragmentManager()
                  .beginTransaction()
                  .replace(android.R.id.content, fragment)
                  .commit();

              // Set new key combo model.
              KeyComboManager keyComboManager = getKeyComboManager();
              keyComboManager.setKeyComboModel(keyComboManager.createKeyComboModelFor(newKeymap));

              // Announce new keymap.
              announceText(
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
                  new AlertDialog.Builder(getActivity())
                      .setTitle(R.string.keycombo_menu_alert_title_trigger_modifier)
                      .setMessage(
                          getString(
                              R.string.keycombo_menu_alert_message_trigger_modifier,
                              newTriggerModifier))
                      .setPositiveButton(
                          android.R.string.ok, chooseTriggerModifierConfirmDialogPositive)
                      .setNegativeButton(
                          android.R.string.cancel, chooseTriggerModifierConfirmDialogNegative)
                      .show();

              focusCancelButton(dialog);

              return false;
            }

            return true;
          }
        };

    @Nullable private String triggerModifierToBeSet;

    private final DialogInterface.OnClickListener chooseTriggerModifierConfirmDialogPositive =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            resetKeymap();

            KeyComboModel keyComboModel = getKeyComboManager().getKeyComboModel();

            // Update preference.
            String preferenceKeyForTriggerModifier =
                keyComboModel.getPreferenceKeyForTriggerModifier();
            ListPreference listPreference =
                (ListPreference) findPreference(preferenceKeyForTriggerModifier);
            listPreference.setValue(triggerModifierToBeSet);

            // Update KeyComboModel.
            keyComboModel.notifyTriggerModifierChanged();

            // Update UI.
            Set<String> keySet =
                getKeyComboManager().getKeyComboModel().getKeyComboCodeMap().keySet();
            for (String key : keySet) {
              KeyboardShortcutDialogPreference preference =
                  (KeyboardShortcutDialogPreference) findPreference(key);
              preference.onTriggerModifierChanged();
            }

            // Announce that trigger modifier has changed.
            CharSequence[] entries = listPreference.getEntries();
            CharSequence newTriggerModifier =
                entries[listPreference.findIndexOfValue(triggerModifierToBeSet)];
            announceText(
                getString(R.string.keycombo_menu_announce_new_trigger_modifier, newTriggerModifier),
                getActivity());

            triggerModifierToBeSet = null;
          }
        };

    private final DialogInterface.OnClickListener chooseTriggerModifierConfirmDialogNegative =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            triggerModifierToBeSet = null;
          }
        };

    private final Preference.OnPreferenceClickListener resetKeymapPreferenceClickListener =
        new Preference.OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            // Show confirm dialog.
            AlertDialog dialog =
                new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.keycombo_menu_reset_keymap))
                    .setMessage(getString(R.string.message_in_reset_keymap_confirm_dialog))
                    .setPositiveButton(
                        R.string.reset_button_in_reset_keymap_confirm_dialog,
                        resetKeymapConfirmDialogPositive)
                    .setNegativeButton(android.R.string.cancel, resetKeymapConfirmDialogNegative)
                    .show();

            focusCancelButton(dialog);

            return true;
          }
        };

    private final DialogInterface.OnClickListener resetKeymapConfirmDialogPositive =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            resetKeymap();

            dialogInterface.dismiss();

            announceText(getString(R.string.keycombo_menu_announce_reset_keymap), getActivity());
          }
        };

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

    private final DialogInterface.OnClickListener resetKeymapConfirmDialogNegative =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            dialogInterface.cancel();
          }
        };

    void performClickOnResetKeymapForTesting() {
      Preference resetKeymapPreference = findPreference(getString(R.string.pref_reset_keymap_key));
      resetKeymapPreferenceClickListener.onPreferenceClick(resetKeymapPreference);
    }

    private KeyComboManager getKeyComboManager() {
      TalkBackService talkBackService = TalkBackService.getInstance();
      return talkBackService == null
          ? KeyComboManager.create(getActivity())
          : talkBackService.getKeyComboManager();
    }

    private String getKeymapName(String keymap) {
      if (keymap.equals(getString(R.string.classic_keymap_entry_value))) {
        return getString(R.string.value_classic_keymap);
      } else if (keymap.equals(getString(R.string.default_keymap_entry_value))) {
        return getString(R.string.value_default_keymap);
      }
      return null;
    }

    private int getPreferenceResourceId(String keymap) {
      if (keymap.equals(getString(R.string.classic_keymap_entry_value))) {
        return R.xml.key_combo_preferences;
      } else if (keymap.equals(getString(R.string.default_keymap_entry_value))) {
        return R.xml.default_key_combo_preferences;
      }
      return R.xml.key_combo_preferences;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      keymap = getArguments().getString(BUNDLE_KEYMAP);

      PreferenceSettingsUtils.addPreferencesFromResource(this, getPreferenceResourceId(keymap));

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

      initPreferenceUIs(getPreferenceScreen(), hiddenShortcutKeys);
    }

    /**
     * Initialize preference UIs.
     *
     * @param root Root element of preference UIs.
     * @param hiddenShortcutKeys Set of shortcut keys which will be made hidden. Note that
     *     preference is made hidden only when its shortcut is disabled in the key combo model.
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
}
