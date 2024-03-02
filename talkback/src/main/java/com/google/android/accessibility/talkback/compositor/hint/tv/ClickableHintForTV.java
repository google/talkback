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
package com.google.android.accessibility.talkback.compositor.hint.tv;

import static com.google.android.accessibility.utils.monitor.CollectionState.SELECTION_SINGLE;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_KEYBOARD;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.StringRes;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.hint.ClickableHint;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Provides usage hints for clickable nodes. */
public class ClickableHintForTV extends ClickableHint {

  private static final String TAG = "ClickableHintForTV";

  public ClickableHintForTV(Context context, GlobalVariables globalVariables) {
    super(context, globalVariables);
  }

  /**
   * Provides the click hint text.
   *
   * <ul>
   *   Click hints in priority order:
   *   <li>actionClickHint,
   *   <li>singleSelectionClickHint,
   *   <li>checkableHint,
   *   <li>clickableHint,
   * </ul>
   */
  @Override
  public CharSequence getHint(AccessibilityNodeInfoCompat node) {
    CharSequence hint = actionClickHint(node);
    if (!TextUtils.isEmpty(hint)) {
      LogUtils.v(TAG, " actionClickHint={%s}", hint);
      return hint;
    }
    hint = singleSelectionClickHint(node);
    if (!TextUtils.isEmpty(hint)) {
      LogUtils.v(TAG, " singleSelectionClickHint={%s}", hint);
      return hint;
    }
    hint = checkableHint(node);
    if (!TextUtils.isEmpty(hint)) {
      LogUtils.v(TAG, " checkableHint={%s}", hint);
      return hint;
    }
    hint = clickableHint(node);
    if (!TextUtils.isEmpty(hint)) {
      LogUtils.v(TAG, " clickableHint={%s}", hint);
      return hint;
    }
    return "";
  }

  /** Returns clickable hint for edit text. */
  @Override
  public CharSequence getEditTextClickableHint(AccessibilityNodeInfoCompat node) {
    return getClickHint(
        R.string.template_hint_edit_text_keyboard, R.string.template_hint_edit_text);
  }

  /** Returns clickable hint for spinner view. */
  @Override
  public CharSequence getSpinnerClickableHint(AccessibilityNodeInfoCompat node) {
    return getClickHint(R.string.template_hint_spinner_keyboard, R.string.template_hint_spinner);
  }

  /** Returns action click hint if the node has click action label. */
  private CharSequence actionClickHint(AccessibilityNodeInfoCompat node) {
    CharSequence actionLabel =
        AccessibilityNodeInfoUtils.getActionLabelById(
            node, AccessibilityNodeInfoCompat.ACTION_CLICK);
    if (TextUtils.isEmpty(actionLabel)) {
      return "";
    }

    int inputMode = globalVariables.getGlobalInputMode();
    if (inputMode == INPUT_MODE_KEYBOARD
        && globalVariables.getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_click)
            != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return context.getString(
          R.string.template_custom_hint_for_actions_keyboard,
          globalVariables.getKeyComboStringRepresentation(R.string.keycombo_shortcut_perform_click),
          actionLabel);
    }
    return context.getString(
        R.string.template_custom_hint_for_actions,
        context.getString(R.string.value_press_select),
        actionLabel);
  }

  /** Returns single-selection click hint if the node is single selection. */
  private CharSequence singleSelectionClickHint(AccessibilityNodeInfoCompat node) {
    if (globalVariables.getCollectionSelectionMode() != SELECTION_SINGLE) {
      return "";
    }

    return getClickHint(
        R.string.template_hint_single_selection_item_keyboard,
        R.string.template_hint_single_selection_item);
  }

  /** Returns checkable click hint if the node is checkable. */
  private CharSequence checkableHint(AccessibilityNodeInfoCompat node) {
    if (!node.isCheckable()) {
      return "";
    }

    return getClickHint(
        R.string.template_hint_checkable_keyboard, R.string.template_hint_checkable);
  }

  /** Returns clickable hint if the node is clickable or has click action. */
  private CharSequence clickableHint(AccessibilityNodeInfoCompat node) {
    if (!AccessibilityNodeInfoUtils.isClickable(node)) {
      return "";
    }

    return getClickHint(
        R.string.template_hint_clickable_keyboard, R.string.template_hint_clickable);
  }

  private CharSequence getClickHint(@StringRes int keyBoardHintString, @StringRes int hintString) {
    int inputMode = globalVariables.getGlobalInputMode();
    if (inputMode == INPUT_MODE_KEYBOARD
        && globalVariables.getKeyComboCodeForKey(R.string.keycombo_shortcut_perform_click)
            != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return context.getString(
          keyBoardHintString,
          globalVariables.getKeyComboStringRepresentation(
              R.string.keycombo_shortcut_perform_click));
    }
    return context.getString(hintString, context.getString(R.string.value_press_select));
  }
}
