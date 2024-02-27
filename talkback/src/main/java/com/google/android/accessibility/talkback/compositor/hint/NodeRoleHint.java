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

import static com.google.android.accessibility.talkback.compositor.CompositorUtils.PRUNE_EMPTY;
import static com.google.android.accessibility.utils.Role.ROLE_DROP_DOWN_LIST;
import static com.google.android.accessibility.utils.Role.ROLE_EDIT_TEXT;
import static com.google.android.accessibility.utils.Role.ROLE_SEEK_CONTROL;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.utils.TalkbackFeatureSupport;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Provides node role hints for feedback. */
public class NodeRoleHint {

  protected final Context context;
  protected final GlobalVariables globalVariables;

  private final ClickableHint clickableHint;
  private final LongClickableHint longClickableHint;

  public NodeRoleHint(Context context, GlobalVariables globalVariables) {
    this.context = context;
    this.globalVariables = globalVariables;

    clickableHint = new ClickableHint(context, globalVariables);
    longClickableHint = new LongClickableHint(context, globalVariables);
  }

  /** Returns the hint by the node role. */
  public CharSequence getHint(AccessibilityNodeInfoCompat node) {
    int role = Role.getRole(node);
    switch (role) {
      case ROLE_DROP_DOWN_LIST:
        return clickableHint.getSpinnerClickableHint(node);
      case ROLE_EDIT_TEXT:
        return getEditTextHint(node, context, globalVariables, clickableHint, longClickableHint);
      case ROLE_SEEK_CONTROL:
        return getSeekBarHint(node, globalVariables, clickableHint, longClickableHint);
      default:
        return getDefaultHint(node, clickableHint, longClickableHint);
    }
  }

  private CharSequence getDefaultHint(
      AccessibilityNodeInfoCompat node,
      ClickableHint clickableHint,
      LongClickableHint longClickableHint) {
    return CompositorUtils.joinCharSequences(
        clickableHint.getHint(node), longClickableHint.getHint(node));
  }

  /**
   * Returns edit-text hint.
   *
   * <ul>
   *   The hint is composed of below elements:
   *   <li>1. Custom clickable hint,
   *   <li>2. Long-clickable hint,
   *   <li>3. Typo hint,
   * </ul>
   */
  private CharSequence getEditTextHint(
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables,
      ClickableHint clickableHint,
      LongClickableHint longClickableHint) {
    List<CharSequence> joinList = new ArrayList<>();
    // Prepare custom clickable hint.
    boolean isFocused = node.isFocused();
    if (!isFocused) {
      joinList.add(clickableHint.getEditTextClickableHint(node));
    }

    // Prepare long-clickable hint.
    joinList.add(longClickableHint.getHint(node));

    // Prepare typo hint.
    int typoCount = AccessibilityNodeInfoUtils.getTypoCount(node);
    if (typoCount > 0 && TalkbackFeatureSupport.supportTextSuggestion()) {
      joinList.add(
          context.getString(
              R.string.template_hint_edit_text_containing_typo,
              typoCount,
              globalVariables.getGestureStringForReadingMenuNextSetting()));
    }

    return CompositorUtils.joinCharSequences(joinList, CompositorUtils.getSeparator(), PRUNE_EMPTY);
  }

  private CharSequence getSeekBarHint(
      AccessibilityNodeInfoCompat node,
      GlobalVariables globalVariables,
      ClickableHint clickableHint,
      LongClickableHint longClickableHint) {
    if ((AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
        || AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD))) {
      return globalVariables.getGlobalAdjustableHint();
    }
    return getDefaultHint(node, clickableHint, longClickableHint);
  }
}
