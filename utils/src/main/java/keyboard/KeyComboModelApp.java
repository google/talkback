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

package com.google.android.accessibility.utils.keyboard;

import android.content.Context;
import android.view.KeyEvent;
import com.google.android.accessibility.utils.R;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manages key and key combo code.
 *
 * <p>TODO: Rename this class to ClassicKeyComboModel after DefaultKeyComboModel becomes
 * default one.
 */
public class KeyComboModelApp implements KeyComboModel {
  private final Context mContext;
  private final KeyComboPersister mPersister;
  private final Map<String, Long> mKeyComboCodeMap = new TreeMap<>();

  /**
   * Search key (meta key) cannot be used as part of key combination since onKey method of
   * KeyboardShortcutDialogPreference is not called if search key is contained.
   */
  private static final int ELIGIBLE_MODIFIER_MASK =
      KeyEvent.META_SHIFT_ON | KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON;

  private static final int REQUIRED_MODIFIER_MASK = KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON;

  public KeyComboModelApp(Context context) {
    mContext = context;
    mPersister = new KeyComboPersister(mContext, null /* no prefix */);
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
    return mKeyComboCodeMap;
  }

  @Override
  public String getKeyForKeyComboCode(long keyComboCode) {
    if (keyComboCode == KEY_COMBO_CODE_UNASSIGNED) {
      return null;
    }

    for (Map.Entry<String, Long> entry : mKeyComboCodeMap.entrySet()) {
      if (entry.getValue() == keyComboCode) {
        return entry.getKey();
      }
    }

    return null;
  }

  @Override
  public long getKeyComboCodeForKey(String key) {
    if (key != null && mKeyComboCodeMap.containsKey(key)) {
      return mKeyComboCodeMap.get(key);
    } else {
      return KEY_COMBO_CODE_UNASSIGNED;
    }
  }

  /** Loads default key combinations. */
  private void loadCombos() {
    addCombo(mContext.getString(R.string.keycombo_shortcut_navigate_next));
    addCombo(mContext.getString(R.string.keycombo_shortcut_navigate_previous));
    addCombo(mContext.getString(R.string.keycombo_shortcut_navigate_first));
    addCombo(mContext.getString(R.string.keycombo_shortcut_navigate_last));
    addCombo(mContext.getString(R.string.keycombo_shortcut_perform_click));
    addCombo(mContext.getString(R.string.keycombo_shortcut_global_back));
    addCombo(mContext.getString(R.string.keycombo_shortcut_global_home));
    addCombo(mContext.getString(R.string.keycombo_shortcut_global_recents));
    addCombo(mContext.getString(R.string.keycombo_shortcut_global_notifications));
    addCombo(mContext.getString(R.string.keycombo_shortcut_global_suspend));
    addCombo(mContext.getString(R.string.keycombo_shortcut_granularity_increase));
    addCombo(mContext.getString(R.string.keycombo_shortcut_granularity_decrease));
    addCombo(mContext.getString(R.string.keycombo_shortcut_other_read_from_top));
    addCombo(mContext.getString(R.string.keycombo_shortcut_other_read_from_next_item));
    addCombo(mContext.getString(R.string.keycombo_shortcut_other_toggle_search));
    addCombo(mContext.getString(R.string.keycombo_shortcut_other_local_context_menu));
    addCombo(mContext.getString(R.string.keycombo_shortcut_other_global_context_menu));
    addCombo(mContext.getString(R.string.keycombo_shortcut_other_custom_actions));
    addCombo(mContext.getString(R.string.keycombo_shortcut_other_language_options));
  }

  private void addCombo(String key) {
    if (!mPersister.contains(key)) {
      mPersister.saveKeyCombo(key, getDefaultKeyComboCode(key));
    }

    long keyComboCode = mPersister.getKeyComboCode(key);
    mKeyComboCodeMap.put(key, keyComboCode);
  }

  @Override
  public long getDefaultKeyComboCode(String key) {
    if (key == null) {
      return KEY_COMBO_CODE_UNASSIGNED;
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_next))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_previous))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_LEFT);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_first))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_UP);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_navigate_last))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DPAD_DOWN);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_perform_click))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_ENTER);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_back))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DEL);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_home))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_H);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_recents))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_R);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_notifications))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_N);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_toggle_search))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SLASH);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_global_suspend))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_Z);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_granularity_increase))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_EQUALS);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_granularity_decrease))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_MINUS);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_local_context_menu))) {
      return KeyComboManager.getKeyComboCode(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_L);
    }

    if (key.equals(mContext.getString(R.string.keycombo_shortcut_other_global_context_menu))) {
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
    mPersister.saveKeyCombo(key, keyComboCode);

    if (mKeyComboCodeMap.containsKey(key)) {
      mKeyComboCodeMap.put(key, keyComboCode);
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
    return mContext.getString(R.string.keycombo_assign_dialog_instruction);
  }

  @Override
  public void updateVersion(int previousVersion) {
    // TalkBack 4.4 fixes an issue with the default keyboard shortcuts, but the changes need
    // to be re-persisted for users upgrading from older versions who haven't customized their
    // shortcut keys.
    if (previousVersion < 40400000) {
      changeGranularityKeyCombos();
    }

    // TalkBack 4.5 assigns default key combos for showing local or global context menu. We need
    // to re-persist them if user hasn't changed them from old default ones.
    if (previousVersion < 40500000) {
      changeLocalGlobalContextMenuKeyCombos();
    }
  }

  /**
   * If the user hasn't changed their key combos for changing granularity/navigation settings, we
   * should switch out the existing key combos for the new key combos.
   */
  private void changeGranularityKeyCombos() {
    // Old shortcut for increase granularity was Alt-Plus.
    updateKeyCombo(
        mContext.getString(R.string.keycombo_shortcut_granularity_increase),
        KeyEvent.META_ALT_ON,
        KeyEvent.KEYCODE_PLUS);

    // Old shortcut for decrease granularity was Alt-Minus.
    updateKeyCombo(
        mContext.getString(R.string.keycombo_shortcut_granularity_decrease),
        KeyEvent.META_ALT_ON,
        KeyEvent.KEYCODE_MINUS);
  }

  /**
   * If user hasn't changed key combos for showing local or global context menu, change them to new
   * ones.
   */
  private void changeLocalGlobalContextMenuKeyCombos() {
    // Old shortcut for local context menu is unassigned.
    updateKeyCombo(
        mContext.getString(R.string.keycombo_shortcut_other_local_context_menu),
        KEY_COMBO_CODE_UNASSIGNED);

    // Old shortcut for global context menu is unassigned.
    updateKeyCombo(
        mContext.getString(R.string.keycombo_shortcut_other_global_context_menu),
        KEY_COMBO_CODE_UNASSIGNED);
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

    if (mPersister.contains(key)) {
      final long actualKeyComboCode = mPersister.getKeyComboCode(key);
      if (oldDefaultKeyComboCode != actualKeyComboCode) {
        return; // User has modified the key combo.
      }
    }

    if (newKeyComboCode != KEY_COMBO_CODE_UNASSIGNED) {
      saveKeyComboCode(key, newKeyComboCode);
    }
  }
}
