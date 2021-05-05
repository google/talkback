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
import android.content.SharedPreferences;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Key value store to make key combo code persistent. */
public class KeyComboPersister {
  private static final String PREFIX_CONCATENATOR = "|";

  private SharedPreferences mPrefs;
  private final String mKeyPrefix;

  public KeyComboPersister(Context context, String keyPrefix) {
    mPrefs = SharedPreferencesUtils.getSharedPreferences(context);
    mKeyPrefix = keyPrefix;
  }

  private String getPrefixedKey(String key) {
    if (mKeyPrefix == null) {
      return key;
    } else {
      return mKeyPrefix + PREFIX_CONCATENATOR + key;
    }
  }

  public void saveKeyCombo(String key, long keyComboCode) {
    mPrefs.edit().putLong(getPrefixedKey(key), keyComboCode).apply();
  }

  public void remove(String key) {
    mPrefs.edit().remove(getPrefixedKey(key)).apply();
  }

  public boolean contains(String key) {
    return mPrefs.contains(getPrefixedKey(key));
  }

  public Long getKeyComboCode(String key) {
    return mPrefs.getLong(getPrefixedKey(key), KeyComboModel.KEY_COMBO_CODE_UNASSIGNED);
  }
}
