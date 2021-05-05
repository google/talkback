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

import android.view.KeyEvent;
import java.util.Map;

/**
 * Manages key combo code and key. KeyComboModel is responsible for persisting preferences of key
 * combo code.
 */
public interface KeyComboModel {
  int KEY_COMBO_CODE_UNASSIGNED = KeyEvent.KEYCODE_UNKNOWN;
  int KEY_COMBO_CODE_INVALID = -1;
  int NO_MODIFIER = 0;

  /**
   * Returns modifier of this model. If this model doesn't have modifier,
   * KEY_COMBO_MODEL_NO_MODIFIER will be returned.
   */
  int getTriggerModifier();

  /**
   * Notifies the model that preference of trigger modifier has changed. You must call this method
   * when you change preference of trigger modifier since the model might cache the value in it.
   */
  void notifyTriggerModifierChanged();

  /**
   * Returns preference key to be used for storing trigger modifier of this mode. Returns null if
   * this model doesn't support trigger modifier.
   */
  String getPreferenceKeyForTriggerModifier();

  /**
   * Returns map of key and key combo code. Key combo codes in this map don't contain trigger
   * modifier if model has it.
   */
  Map<String, Long> getKeyComboCodeMap();

  /**
   * Gets key for preference that is assigned for keyComboCode if keyComboCode is not
   * KEY_COMBO_CODE_UNASSIGNED. If no preference is assigned or keyComboCode was
   * KEY_COMBO_CODE_UNASSIGNED, returns null.
   *
   * @param keyComboCode key combo code which doesn't contain trigger modifier if model has it.
   */
  String getKeyForKeyComboCode(long keyComboCode);

  /** Gets key combo code for key. KEY_COMBO_CODE_UNASSIGNED will be returned if key is invalid. */
  long getKeyComboCodeForKey(String key);

  /**
   * Gets default key combo code for key. KEY_COMBO_CODE_UNASSIGNED will be returned if no key combo
   * code is assigned to the key or it's invalid.
   */
  long getDefaultKeyComboCode(String key);

  /**
   * Assigns keyComboCode for preference.
   *
   * @param key key of key combo.
   * @param keyComboCode key combo code which doesn't contain trigger modifier if model has it.
   */
  void saveKeyComboCode(String key, long keyComboCode);

  /** Clears key combo code assigned for preference key. */
  void clearKeyComboCode(String key);

  /**
   * Returns true if keyComboCode is eligible combination for this model. This method doesn't check
   * consistency with other key combo codes in this model. e.g. duplicated key combos.
   *
   * @param keyComboCode key combo code which doesn't contain trigger modifier if model has it.
   */
  boolean isEligibleKeyComboCode(long keyComboCode);

  /** Returns description of eligible key combination. This will be shown in the UI. */
  String getDescriptionOfEligibleKeyCombo();

  /** Updates key combo model. This method will be called when TalkBack is updated. */
  void updateVersion(int previousVersion);
}
