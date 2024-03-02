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
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manages key and key combo code.
 *
 * <p>TODO: Rename this class to ClassicKeyComboModel after DefaultKeyComboModel becomes
 * default one.
 */
public class ClassicKeyComboModel implements KeyComboModel {
  // TODO: Migrating currently using (null) shared preference key to
  // classic_key_combo_model.
  public static final String PREF_KEY_PREFIX = "classic_key_combo_model";
  private final Context context;
  private final KeyComboPersister persister;
  private final Map<String, Long> keyComboCodeMap = new TreeMap<>();

  /**
   * Search key (meta key) cannot be used as part of key combination since onKey method of
   * KeyboardShortcutDialogPreference is not called if search key is contained.
   */
  private static final int ELIGIBLE_MODIFIER_MASK =
      KeyEvent.META_SHIFT_ON | KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON;

  private static final int REQUIRED_MODIFIER_MASK = KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON;

  public ClassicKeyComboModel(Context context) {
    this.context = context;
    persister = new KeyComboPersister(this.context, /* no prefix */ null);
    loadCombos();
  }

  @Override
  public int getTriggerModifier() {
    return NO_MODIFIER;
  }

  @Override
  public void notifyTriggerModifierChanged() {}

  @Override
  public String getPreferenceKeyForTriggerModifier() {
    return null;
  }

  @Override
  public Map<String, Long> getKeyComboCodeMap() {
    return keyComboCodeMap;
  }

  @Nullable
  @Override
  public String getKeyForKeyComboCode(long keyComboCode) {
    if (keyComboCode == KEY_COMBO_CODE_UNASSIGNED) {
      return null;
    }

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

  /** Loads default key combinations. */
  private void loadCombos() {
    addCombo(context.getString(R.string.keycombo_shortcut_navigate_next_default));
    addCombo(context.getString(R.string.keycombo_shortcut_navigate_previous_default));
    addCombo(context.getString(R.string.keycombo_shortcut_navigate_first));
    addCombo(context.getString(R.string.keycombo_shortcut_navigate_last));
    addCombo(context.getString(R.string.keycombo_shortcut_perform_click));
    addCombo(context.getString(R.string.keycombo_shortcut_global_back));
    addCombo(context.getString(R.string.keycombo_shortcut_global_home));
    addCombo(context.getString(R.string.keycombo_shortcut_global_recents));
    addCombo(context.getString(R.string.keycombo_shortcut_global_notifications));
    addCombo(context.getString(R.string.keycombo_shortcut_global_play_pause_media));
    addCombo(context.getString(R.string.keycombo_shortcut_global_scroll_forward_reading_menu));
    addCombo(context.getString(R.string.keycombo_shortcut_global_scroll_backward_reading_menu));
    addCombo(context.getString(R.string.keycombo_shortcut_global_adjust_reading_settings_previous));
    addCombo(context.getString(R.string.keycombo_shortcut_global_adjust_reading_setting_next));
    addCombo(context.getString(R.string.keycombo_shortcut_granularity_increase));
    addCombo(context.getString(R.string.keycombo_shortcut_granularity_decrease));
    addCombo(context.getString(R.string.keycombo_shortcut_other_read_from_top));
    addCombo(context.getString(R.string.keycombo_shortcut_other_read_from_next_item));
    addCombo(context.getString(R.string.keycombo_shortcut_other_toggle_search));
    addCombo(context.getString(R.string.keycombo_shortcut_other_talkback_context_menu));
    addCombo(context.getString(R.string.keycombo_shortcut_other_custom_actions));
    addCombo(context.getString(R.string.keycombo_shortcut_other_language_options));
    addCombo(context.getString(R.string.keycombo_shortcut_other_copy_last_spoken_phrase));
  }

  @Override
  public long getDefaultKeyComboCode(String key) {
    if (key == null) {
      return KEY_COMBO_CODE_UNASSIGNED;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_default))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_default))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_LEFT);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_first))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_UP);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_last))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_perform_click))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_ENTER);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_back))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DEL);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_home))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_H);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_recents))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_R);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_notifications))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_N);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_toggle_search))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SLASH);
    }

    if (FeatureSupport.supportMediaControls()
        && key.equals(context.getString(R.string.keycombo_shortcut_global_play_pause_media))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SPACE);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_scroll_forward_reading_menu))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_scroll_backward_reading_menu))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_UP);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_adjust_reading_settings_previous))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_UP);
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_adjust_reading_setting_next))) {
      return KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_granularity_increase))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_EQUALS);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_granularity_decrease))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_MINUS);
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_talkback_context_menu))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_G);
    }

    return KEY_COMBO_CODE_UNASSIGNED;
  }

  @Override
  public void clearKeyComboCode(String key) {
    saveKeyComboCode(key, KEY_COMBO_CODE_UNASSIGNED);
  }

  @Override
  public void saveKeyComboCode(String key, long keyComboCode) {
    persister.saveKeyCombo(key, keyComboCode);

    if (keyComboCodeMap.containsKey(key)) {
      keyComboCodeMap.put(key, keyComboCode);
    }
  }

  @Override
  public boolean isEligibleKeyComboCode(long keyComboCode) {
    if (keyComboCode == KEY_COMBO_CODE_UNASSIGNED) {
      return true;
    }

    int modifier = KeyComboManager.getModifier(keyComboCode);
    if ((modifier & REQUIRED_MODIFIER_MASK) == 0
        || (modifier | ELIGIBLE_MODIFIER_MASK) != ELIGIBLE_MODIFIER_MASK) {
      return false;
    }

    int keyCode = KeyComboManager.getKeyCode(keyComboCode);
    return keyCode != 0
        && keyCode != KeyEvent.KEYCODE_SHIFT_LEFT
        && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
        && keyCode != KeyEvent.KEYCODE_ALT_LEFT
        && keyCode != KeyEvent.KEYCODE_ALT_RIGHT
        && keyCode != KeyEvent.KEYCODE_CTRL_LEFT
        && keyCode != KeyEvent.KEYCODE_CTRL_RIGHT;
  }

  @Override
  public String getDescriptionOfEligibleKeyCombo() {
    return context.getString(R.string.keycombo_assign_dialog_instruction);
  }

  @Override
  public void updateVersion(int previousVersion) {
    // TalkBack 4.4 fixes an issue with the default keyboard shortcuts, but the changes need
    // to be re-persisted for users upgrading from older versions who haven't customized their
    // shortcut keys.
    if (previousVersion < 40400000) {
      changeGranularityKeyCombos();
    }

    // TalkBack 4.5 assigns default key combos for showing talkback context menu. We need
    // to re-persist them if user hasn't changed them from old default ones.
    if (previousVersion < 40500000) {
      // Old shortcut for talkback context menu is unassigned.
      updateKeyCombo(
          context.getString(R.string.keycombo_shortcut_other_talkback_context_menu),
          KEY_COMBO_CODE_UNASSIGNED);
    }
  }

  /**
   * Updates a key combo if the user has not yet changed it from the old default value.
   *
   * @param key the name of the key combo to change
   * @param oldModifier the old default modifier assigned to the key combo
   * @param oldKeyCode the old default keycode assigned to the key combo
   */
  public void updateKeyCombo(String key, int oldModifier, int oldKeyCode) {
    updateKeyCombo(key, KeyComboManager.getKeyComboCode(oldModifier, oldKeyCode));
  }

  /**
   * Updates a key combo if the user has not yet changed it from the old default value.
   *
   * @param key the name of the key combo to change
   * @param oldDefaultKeyComboCode the old default key combo.
   */
  public void updateKeyCombo(String key, long oldDefaultKeyComboCode) {
    final long newKeyComboCode = getDefaultKeyComboCode(key);

    if (getKeyForKeyComboCode(newKeyComboCode) != null) {
      return; // User is already using the new key combo.
    }

    if (persister.contains(key)) {
      final long actualKeyComboCode = persister.getKeyComboCode(key);
      if (oldDefaultKeyComboCode != actualKeyComboCode) {
        return; // User has modified the key combo.
      }
    }

    if (newKeyComboCode != KEY_COMBO_CODE_UNASSIGNED) {
      saveKeyComboCode(key, newKeyComboCode);
    }
  }

  private void addCombo(String key) {
    if (!persister.contains(key)) {
      persister.saveKeyCombo(key, getDefaultKeyComboCode(key));
    }

    long keyComboCode = persister.getKeyComboCode(key);
    keyComboCodeMap.put(key, keyComboCode);
  }

  /**
   * If the user hasn't changed their key combos for changing granularity/navigation settings, we
   * should switch out the existing key combos for the new key combos.
   */
  private void changeGranularityKeyCombos() {
    // Old shortcut for increase granularity was Alt-Plus.
    updateKeyCombo(
        context.getString(R.string.keycombo_shortcut_granularity_increase),
        KeyEvent.META_ALT_ON,
        KeyEvent.KEYCODE_PLUS);

    // Old shortcut for decrease granularity was Alt-Minus.
    updateKeyCombo(
        context.getString(R.string.keycombo_shortcut_granularity_decrease),
        KeyEvent.META_ALT_ON,
        KeyEvent.KEYCODE_MINUS);
  }
}
