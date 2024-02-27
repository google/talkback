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
import static com.google.android.accessibility.utils.Role.ROLE_NUMBER_PICKER;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides accessibility focused hints for feedback. The usage hint appends clickable hint,
 * long-clickable hint and node role hint for the accessibility focused node.
 */
public class AccessibilityFocusHint {

  private static final String TAG = "AccessibilityFocusHint";

  protected final Context context;
  protected final GlobalVariables globalVariables;
  private final NodeRoleHint roleHint;

  public AccessibilityFocusHint(Context context, GlobalVariables globalVariables) {
    this.context = context;
    this.globalVariables = globalVariables;

    roleHint = new NodeRoleHint(context, globalVariables);
  }

  /**
   * Returns accessibility focused hints for feedback.
   *
   * <ul>
   *   The hint is composed of below elements:
   *   <li>1. Hint for adjustable child,
   *   <li>2. Node role hint,
   *   <li>3. Node actions hint that is high verbosity,
   * </ul>
   */
  public CharSequence getHint(AccessibilityNodeInfoCompat node) {
    StringBuilder logString = new StringBuilder();
    List<CharSequence> joinList = new ArrayList<>();
    boolean enableUsageHint = globalVariables.getUsageHintEnabled();
    boolean isEnabled = node.isEnabled();
    boolean isAccessibilityFocused = node.isAccessibilityFocused();
    logString
        .append(String.format("enableUsageHint=%s", enableUsageHint))
        .append(String.format(", isEnabled=%s", isEnabled))
        .append(String.format(", isAccessibilityFocused=%s", isAccessibilityFocused));
    if (enableUsageHint && isEnabled && isAccessibilityFocused) {
      // Prepare hint for adjustable child.
      CharSequence hintForAdjustableChild = getHintForAdjustableChild(node, globalVariables);
      logString.append(String.format("\n    hintForAdjustableChild={%s}", hintForAdjustableChild));
      joinList.add(hintForAdjustableChild);

      // Prepare hint for the node role.
      CharSequence nodeRoleHint = roleHint.getHint(node);
      logString.append(String.format("\n    nodeRoleHint={%s}", nodeRoleHint));
      joinList.add(nodeRoleHint);

      // Prepare node actions hint that is in high verbosity.
      CharSequence nodeActionsHintHighVerbosity =
          AccessibilityNodeFeedbackUtils.getHintForNodeActions(node, context, globalVariables);
      logString.append(
          String.format("\n    nodeActionsHintHighVerbosity={%s}", nodeActionsHintHighVerbosity));
      joinList.add(nodeActionsHintHighVerbosity);

      LogUtils.v(TAG, "    getHint: %s", logString.toString());
      LogUtils.v(TAG, "    isLongClickable: %s", node.isLongClickable());
      return CompositorUtils.joinCharSequences(
          joinList, CompositorUtils.getSeparator(), PRUNE_EMPTY);
    }
    LogUtils.v(TAG, "    getHint: %s", logString.toString());
    return "";
  }

  private static CharSequence getHintForAdjustableChild(
      AccessibilityNodeInfoCompat node, GlobalVariables globalVariables) {
    AccessibilityNodeInfoCompat parentNode = node.getParent();
    if (parentNode == null) {
      LogUtils.w(TAG, "getHintForAdjustableChild: error  Parent node is null.");
      return "";
    }
    int role = Role.getRole(parentNode);
    if (role == ROLE_NUMBER_PICKER
        && (AccessibilityNodeInfoUtils.supportsAction(
                parentNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            || AccessibilityNodeInfoUtils.supportsAction(
                parentNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD))) {
      return globalVariables.getGlobalAdjustableHint();
    }
    return "";
  }
}
