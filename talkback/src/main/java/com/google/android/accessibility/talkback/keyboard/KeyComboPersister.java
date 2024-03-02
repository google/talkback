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
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Key value store to make key combo code persistent. */
public class KeyComboPersister {
  private static final String PREFIX_CONCATENATOR = "|";

  private final SharedPreferences prefs;
  private final String keyPrefix;

  public KeyComboPersister(Context context, String keyPrefix) {
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    this.keyPrefix = keyPrefix;
  }

  public void saveKeyCombo(String key, long keyComboCode) {
    prefs.edit().putLong(getPrefixedKey(key), keyComboCode).apply();
  }

  public void remove(String key) {
    prefs.edit().remove(getPrefixedKey(key)).apply();
  }

  public boolean contains(String key) {
    return prefs.contains(getPrefixedKey(key));
  }

  public Long getKeyComboCode(String key) {
    return prefs.getLong(getPrefixedKey(key), KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);
  }

  private String getPrefixedKey(String key) {
    if (keyPrefix == null) {
      return key;
    } else {
      return keyPrefix + PREFIX_CONCATENATOR + key;
    }
  }
}
