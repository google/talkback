/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.Map;
import java.util.TreeMap;

/** Default key combo model. */
public class DefaultKeyComboModel implements KeyComboModel {
  public static final String PREF_KEY_PREFIX = "default_key_combo_model";

  private final Context context;
  private final Map<String, Long> keyComboCodeMap = new TreeMap<>();
  private final KeyComboPersister persister;

  private int triggerModifier = KeyEvent.META_ALT_ON;

  public DefaultKeyComboModel(Context context) {
    this.context = context;
    persister = new KeyComboPersister(context, PREF_KEY_PREFIX);

    loadTriggerModifierFromPreferences();
    addKeyCombos();
  }

  private void loadTriggerModifierFromPreferences() {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);

    if (!prefs.contains(getPreferenceKeyForTriggerModifier())) {
      // Store default value in preferences to show it in preferences UI.
      prefs
          .edit()
          .putString(
              getPreferenceKeyForTriggerModifier(),
              context.getString(R.string.trigger_modifier_alt_entry_value))
          .apply();
    }

    String triggerModifier =
        prefs.getString(
            getPreferenceKeyForTriggerModifier(),
            context.getString(R.string.trigger_modifier_alt_entry_value));
    if (triggerModifier.equals(context.getString(R.string.trigger_modifier_alt_entry_value))) {
      this.triggerModifier = KeyEvent.META_ALT_ON;
    } else if (triggerModifier.equals(
        context.getString(R.string.trigger_modifier_meta_entry_value))) {
      this.triggerModifier = KeyEvent.META_META_ON;
    }
  }

  @Override
  public int getTriggerModifier() {
    return triggerModifier;
  }

  @Override
  public void notifyTriggerModifierChanged() {
    loadTriggerModifierFromPreferences();
  }

  @Override
  public String getPreferenceKeyForTriggerModifier() {
    return context.getString(R.string.pref_default_keymap_trigger_modifier_key);
  }

  @Override
  public Map<String, Long> getKeyComboCodeMap() {
    return keyComboCodeMap;
  }

  @Nullable
  @Override
  public String getKeyForKeyComboCode(long keyComboCode) {
    for (Map.Entry<String, Long> entry : keyComboCodeMap.entrySet()) {
      if (entry.getValue() == keyComboCode) {
        return entry.getKey();
      }
    }

    return null;
  }

  @Override
  public long getKeyComboCodeForKey(String key) {
    if (key != null && keyComboCodeMap.containsKey(key)) {
      return keyComboCodeMap.get(key);
    } else {
      return KEY_COMBO_CODE_UNASSIGNED;
    }
  }

  @Override
  public long getDefaultKeyComboCode(String key) {
    if (key == null) {
      return KEY_COMBO_CODE_UNASSIGNED;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_perform_click))) {
      return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_ENTER);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_perform_long_click))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_ENTER);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_read_from_top))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_ENTER);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_read_from_next_item))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_ENTER);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_talkback_context_menu))) {
      return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_SPACE);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_custom_actions))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_SPACE);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_language_options))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_L);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_toggle_search))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_SLASH);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_home))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_H);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_recents))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_R);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_back))) {
      return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DEL);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_notifications))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_N);
    }

    if (FeatureSupport.supportMediaControls()
        && key.equals(context.getString(R.string.keycombo_shortcut_global_play_pause_media))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SPACE);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_scroll_forward_reading_menu))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_scroll_backward_reading_menu))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_UP);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_adjust_reading_settings_previous))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_UP);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_adjust_reading_setting_next))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_default))) {
      return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_default))) {
      return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_LEFT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_up))) {
      return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_UP);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_down))) {
      return KeyComboManager.getKeyComboCode(NO_MODIFIER, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_first))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_LEFT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_last))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_word))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_word))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_LEFT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_character))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_character))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_LEFT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_button))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_B);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_button))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_B);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_control))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_C);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_control))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_C);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_checkbox))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_checkbox))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_X);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_aria_landmark))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_D);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_aria_landmark))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_D);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_edit_field))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_E);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_edit_field))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_E);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_focusable_item))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_F);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_navigate_previous_focusable_item))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_F);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_graphic))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_G);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_graphic))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_G);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_H);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_H);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_1))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_1);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_1))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_1);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_2))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_2);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_2))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_2);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_3))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_3);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_3))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_3);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_4))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_4);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_4))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_4);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_5))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_5);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_5))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_5);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_6))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_6);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_6))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_6);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_list_item))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_I);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_list_item))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_I);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_link))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_L);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_link))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_L);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_list))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_O);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_list))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_O);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_table))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_T);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_table))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_T);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_combobox))) {
      return KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_Z);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_combobox))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_Z);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_window))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_window))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_UP);
    }

    return KEY_COMBO_CODE_UNASSIGNED;
  }

  @Override
  public void saveKeyComboCode(String key, long keyComboCode) {
    persister.saveKeyCombo(key, keyComboCode);
    keyComboCodeMap.put(key, keyComboCode);
  }

  @Override
  public void clearKeyComboCode(String key) {
    saveKeyComboCode(key, KEY_COMBO_CODE_UNASSIGNED);
  }

  @Override
  public boolean isEligibleKeyComboCode(long keyComboCode) {
    if (keyComboCode == KEY_COMBO_CODE_UNASSIGNED) {
      return true;
    }

    // Do not allow to set key combo which is consisted only with modifiers.
    int keyCode = KeyComboManager.getKeyCode(keyComboCode);
    if (KeyEvent.isModifierKey(keyCode) || keyCode == KeyEvent.KEYCODE_UNKNOWN) {
      return false;
    }

    // It's not allowed to use trigger modifier as part of key combo code.
    return (KeyComboManager.getModifier(keyComboCode) & getTriggerModifier()) == 0;
  }

  @Override
  public String getDescriptionOfEligibleKeyCombo() {
    return context.getString(
        R.string.keycombo_assign_dialog_default_keymap_instruction, getTriggerModifierName());
  }

  @Override
  public void updateVersion(int previousVersion) {
    if (previousVersion < 50200001) {
      // From version 50200001, we've renamed keycombo_shortcut_navigate_next and
      // keycombo_shortcut_navigate_previous to keycombo_shortcut_navigate_next_default and
      // keycombo_shortcut_navigate_previous_default respectively.
      moveKeyComboPreferenceValue(
          context.getString(R.string.keycombo_shortcut_navigate_next),
          context.getString(R.string.keycombo_shortcut_navigate_next_default));
      moveKeyComboPreferenceValue(
          context.getString(R.string.keycombo_shortcut_navigate_previous),
          context.getString(R.string.keycombo_shortcut_navigate_previous_default));
    }
  }

  private String getTriggerModifierName() {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    String triggerModifier =
        prefs.getString(
            getPreferenceKeyForTriggerModifier(),
            context.getString(R.string.trigger_modifier_alt_entry_value));
    String triggerModifierName = "";

    if (triggerModifier.equals(context.getString(R.string.trigger_modifier_alt_entry_value))) {
      triggerModifierName = context.getString(R.string.keycombo_key_modifier_alt);
    } else if (triggerModifier.equals(
        context.getString(R.string.trigger_modifier_meta_entry_value))) {
      triggerModifierName = context.getString(R.string.keycombo_key_modifier_meta);
    }

    return triggerModifierName;
  }

  private void addKeyCombos() {
    addKeyCombo(context.getString(R.string.keycombo_shortcut_perform_click));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_perform_long_click));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_other_read_from_top));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_other_read_from_next_item));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_other_talkback_context_menu));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_other_custom_actions));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_other_language_options));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_other_toggle_search));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_back));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_default));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_default));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_up));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_down));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_first));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_last));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_word));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_word));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_character));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_character));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_button));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_button));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_control));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_control));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_checkbox));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_checkbox));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_aria_landmark));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_aria_landmark));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_edit_field));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_edit_field));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_focusable_item));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_focusable_item));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_graphic));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_graphic));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_heading));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_heading));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_heading_1));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_1));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_heading_2));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_2));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_heading_3));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_3));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_heading_4));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_4));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_heading_5));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_5));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_heading_6));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_6));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_list_item));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_list_item));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_link));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_link));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_list));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_list));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_table));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_table));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_combobox));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_combobox));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_next_window));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_window));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_home));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_recents));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_notifications));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_play_pause_media));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_scroll_forward_reading_menu));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_scroll_backward_reading_menu));
    addKeyCombo(
        context.getString(R.string.keycombo_shortcut_global_adjust_reading_settings_previous));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_adjust_reading_setting_next));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_other_copy_last_spoken_phrase));
    addKeyCombo(context.getString(R.string.keycombo_shortcut_global_hide_or_show_screen));
  }

  private void addKeyCombo(String key) {
    if (!persister.contains(key)) {
      persister.saveKeyCombo(key, getDefaultKeyComboCode(key));
    }

    keyComboCodeMap.put(key, persister.getKeyComboCode(key));
  }

  /**
   * Move key combo preference value from fromKey to toKey. Original value in fromKey is deleted.
   */
  private void moveKeyComboPreferenceValue(String fromKey, String toKey) {
    if (!persister.contains(fromKey)) {
      return;
    }

    saveKeyComboCode(toKey, persister.getKeyComboCode(fromKey));
    persister.remove(fromKey);
  }
}
