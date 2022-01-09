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

package com.google.android.accessibility.talkback.preference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.utils.AlertDialogUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferencesActivity;
import com.google.android.accessibility.utils.keyboard.DefaultKeyComboModel;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.keyboard.KeyComboModel;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Activity used to set TalkBack's keyboard shortcut preferences. */
public class TalkBackKeyboardShortcutPreferencesActivity extends PreferencesActivity {

  private static void focusCancelButton(AlertDialog alertDialog) {
    Button cancelButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    cancelButton.setFocusableInTouchMode(true);
    cancelButton.requestFocus();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    String keymap = getKeymap();
    // Sets FragmentFactory when VerbosityPrefFragment uses a non-default constructor to get
    // ContainerId. This results framework sometimes restores VerbosityPrefFragment by default
    // constructor only.
    getSupportFragmentManager()
        .setFragmentFactory(
            new TalkBackKeyboardShortcutFragmentFactory(
                keymap, getPreferenceResourceId(keymap), getContainerId()));
    super.onCreate(savedInstanceState);
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    String keymap = getKeymap();
    return TalkBackKeyboardShortcutPreferenceFragment.createFor(
        keymap, getPreferenceResourceId(keymap), getContainerId());
  }

  private String getKeymap() {
    TalkBackService talkBackService = TalkBackService.getInstance();
    KeyComboManager keyComboManager =
        talkBackService == null
            ? KeyComboManager.create(this)
            : talkBackService.getKeyComboManager();

    return keyComboManager.getKeymap();
  }

  private int getPreferenceResourceId(String keymap) {
    if (TextUtils.equals(keymap, getString(R.string.default_keymap_entry_value))) {
      return R.xml.default_key_combo_preferences;
    }
    // In addition to R.string.classic_keymap_entry_value, the others use
    // R.xml.key_combo_preferences
    return R.xml.key_combo_preferences;
  }

  @VisibleForTesting
  void resetKeymap() {
    TalkBackKeyboardShortcutPreferenceFragment fragment =
        (TalkBackKeyboardShortcutPreferenceFragment)
            getSupportFragmentManager().findFragmentById(getContainerId());
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

  /**
   * A {@code FragmentFactory} which creates TalkBackKeyboardShortcutPreferenceFragment uses a
   * non-default constructor to ensure that this constructor is called when the fragment is
   * re-instantiated.
   */
  private static final class TalkBackKeyboardShortcutFragmentFactory extends FragmentFactory {
    private final String keymap;
    private final int containerId;
    private int resId;

    public TalkBackKeyboardShortcutFragmentFactory(String keymap, int resId, int containerId) {
      super();
      this.keymap = keymap;
      this.resId = resId;
      this.containerId = containerId;
    }

    @NonNull
    @Override
    public Fragment instantiate(@NonNull ClassLoader classLoader, @NonNull String className) {
      Class<? extends Fragment> clazz = loadFragmentClass(classLoader, className);
      if (clazz == TalkBackKeyboardShortcutPreferenceFragment.class) {
        return new TalkBackKeyboardShortcutPreferenceFragment(keymap, resId, containerId);
      } else {
        return super.instantiate(classLoader, className);
      }
    }
  }

  /** Panel holding a set of keyboard shortcut preferences. */
  public static class TalkBackKeyboardShortcutPreferenceFragment extends TalkbackBaseFragment {

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

    public static TalkBackKeyboardShortcutPreferenceFragment createFor(
        String keymap, int resId, int containerId) {
      TalkBackKeyboardShortcutPreferenceFragment preferenceFragment =
          new TalkBackKeyboardShortcutPreferenceFragment(keymap, resId, containerId);

      return preferenceFragment;
    }

    private String keymap;
    private int containerId;

    public TalkBackKeyboardShortcutPreferenceFragment() {
      super(R.xml.key_combo_preferences);
    }

    TalkBackKeyboardShortcutPreferenceFragment(String keymap, int resId, int containerId) {
      super(resId);
      this.keymap = keymap;
      this.containerId = containerId;
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
                  new TalkBackKeyboardShortcutPreferenceFragment(
                      newKeymap,
                      newKeymap.equals(getString(R.string.default_keymap_entry_value))
                          ? R.xml.default_key_combo_preferences
                          : R.xml.key_combo_preferences,
                      containerId);
              getFragmentManager().beginTransaction().replace(containerId, fragment).commit();

              // Set new key combo model.
              KeyComboManager keyComboManager = getKeyComboManager();
              keyComboManager.setKeyComboModel(keyComboManager.createKeyComboModelFor(newKeymap));

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
                          android.R.string.cancel, chooseTriggerModifierConfirmDialogNegative)
                      .create();
              dialog.show();

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
            PreferencesActivityUtils.announceText(
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
                AlertDialogUtils.builder(getActivity())
                    .setTitle(getString(R.string.keycombo_menu_reset_keymap))
                    .setMessage(getString(R.string.message_in_reset_keymap_confirm_dialog))
                    .setPositiveButton(
                        R.string.reset_button_in_reset_keymap_confirm_dialog,
                        resetKeymapConfirmDialogPositive)
                    .setNegativeButton(android.R.string.cancel, resetKeymapConfirmDialogNegative)
                    .create();
            dialog.show();

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

            PreferencesActivityUtils.announceText(
                getString(R.string.keycombo_menu_announce_reset_keymap), getActivity());
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

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);

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
