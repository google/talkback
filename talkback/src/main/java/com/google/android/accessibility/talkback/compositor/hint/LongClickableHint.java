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
package com.google.android.accessibility.talkback.compositor.hint;

import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_KEYBOARD;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_NON_ALPHABETIC_KEYBOARD;

import android.content.Context;
import android.text.TextUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Provides usage hints for long-clickable nodes. */
public class LongClickableHint {

  private static final String TAG = "LongClickableHint";

  protected final Context context;
  protected final GlobalVariables globalVariables;

  public LongClickableHint(Context context, GlobalVariables globalVariables) {
    this.context = context;
    this.globalVariables = globalVariables;
  }

  /**
   * Provides the long-click hint text.
   *
   * <ul>
   *   Long-click hints in priority order:
   *   <li>actionLongClickHint,
   *   <li>longClickableHint,
   * </ul>
   */
  public CharSequence getHint(AccessibilityNodeInfoCompat node) {
    CharSequence hint = actionLongClickHint(node);
    if (!TextUtils.isEmpty(hint)) {
      LogUtils.v(TAG, " actionLongClickHint={%s}", hint);
      return hint;
    }
    hint = longClickableHint(node);
    if (!TextUtils.isEmpty(hint)) {
      LogUtils.v(TAG, " longClickableHint={%s}", hint);
      return hint;
    }
    return "";
  }

  /** Returns action long-click hint if the node has long-click action label. */
  private CharSequence actionLongClickHint(AccessibilityNodeInfoCompat node) {
    CharSequence actionLabel =
        AccessibilityNodeInfoUtils.getActionLabelById(
            node, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
    if (TextUtils.isEmpty(actionLabel)) {
      return "";
    }

    int inputMode = globalVariables.getGlobalInputMode();
    if (inputMode == INPUT_MODE_KEYBOARD
        && globalVariables.getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_long_click)
            != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return context.getString(
          R.string.template_custom_hint_for_actions_keyboard,
          globalVariables.getKeyComboStringRepresentation(
              R.string.keycombo_shortcut_perform_long_click),
          actionLabel);
    }
    if (inputMode == INPUT_MODE_NON_ALPHABETIC_KEYBOARD) {
      return context.getString(
          R.string.template_custom_hint_for_long_clickable_actions,
          context.getString(R.string.value_press_select),
          actionLabel);
    }
    if (!AccessibilityNodeInfoUtils.isKeyboard(node)) {
      return context.getString(
          R.string.template_custom_hint_for_long_clickable_actions,
          context.getString(R.string.value_double_tap),
          actionLabel);
    }
    return "";
  }

  /** Returns long-clickable hint if the node is long-clickable or has long-click action. */
  private CharSequence longClickableHint(AccessibilityNodeInfoCompat node) {
    if (!AccessibilityNodeInfoUtils.isLongClickable(node)) {
      return "";
    }

    int inputMode = globalVariables.getGlobalInputMode();
    if (inputMode == INPUT_MODE_KEYBOARD
        && globalVariables.getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_long_click)
            != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return context.getString(
          R.string.template_hint_long_clickable_keyboard,
          globalVariables.getKeyComboStringRepresentation(
              R.string.keycombo_shortcut_perform_long_click));
    }
    if (inputMode == INPUT_MODE_NON_ALPHABETIC_KEYBOARD) {
      return context.getString(
          R.string.template_hint_long_clickable, context.getString(R.string.value_press_select));
    }
    if (!AccessibilityNodeInfoUtils.isKeyboard(node)) {
      return context.getString(
          R.string.template_hint_long_clickable, context.getString(R.string.value_double_tap));
    }
    return "";
  }
}
