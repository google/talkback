/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.compositor;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;

/** Utils class that provides common methods for keyComboManager. */
public final class KeyComboManagerUtils {

  /** Returns user friendly string representations of key combo code */
  public static String getKeyComboStringRepresentation(
      int keyStringResId, @Nullable KeyComboManager keyComboManager, Context context) {
    if (keyComboManager == null) {
      return "";
    }
    long keyComboCode = getKeyComboCodeForKey(keyStringResId, keyComboManager, context);
    KeyComboModel keyComboModel = keyComboManager.getKeyComboModel();
    long keyComboCodeWithTriggerModifier =
        KeyComboManager.getKeyComboCode(
            KeyComboManager.getModifier(keyComboCode) | keyComboModel.getTriggerModifier(),
            KeyComboManager.getKeyCode(keyComboCode));

    return keyComboManager.getKeyComboStringRepresentation(keyComboCodeWithTriggerModifier);
  }

  /** Gets key combo code for key. KEY_COMBO_CODE_UNASSIGNED will be returned if key is invalid. */
  public static long getKeyComboCodeForKey(
      @StringRes int keyStringResId, @Nullable KeyComboManager keyComboManager, Context context) {
    if (keyComboManager == null) {
      return KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
    } else {
      return keyComboManager
          .getKeyComboModel()
          .getKeyComboCodeForKey(context.getString(keyStringResId));
    }
  }

  private KeyComboManagerUtils() {}
}
