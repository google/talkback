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

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.hint.AccessibilityFocusHint;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Provides accessibility focused hints for feedback. The usage hint appends clickable hint,
 * long-clickable hint and node role hint for the accessibility focused node.
 */
public class AccessibilityFocusHintForTV extends AccessibilityFocusHint {

  private static final String TAG = "AccessibilityFocusHintForTV";

  private final NodeRoleHintForTV roleHint;

  public AccessibilityFocusHintForTV(Context context, GlobalVariables globalVariables) {
    super(context, globalVariables);

    roleHint = new NodeRoleHintForTV(context, globalVariables);
  }

  /**
   * Returns accessibility focused hints for feedback.
   *
   * <ul>
   *   The hint is composed of below element:
   *   <li>1. Node role hint,
   * </ul>
   */
  @Override
  public CharSequence getHint(AccessibilityNodeInfoCompat node) {
    StringBuilder logString = new StringBuilder();
    boolean enableUsageHint = globalVariables.getUsageHintEnabled();
    boolean isEnabled = node.isEnabled();
    boolean isAccessibilityFocused = node.isAccessibilityFocused();
    logString
        .append(String.format("enableUsageHint=%s", enableUsageHint))
        .append(String.format(", isEnabled=%s", isEnabled))
        .append(String.format(", isAccessibilityFocused=%s", isAccessibilityFocused));

    if (enableUsageHint && isEnabled && isAccessibilityFocused) {
      // Prepare hint for the node role.
      CharSequence nodeRoleHint = roleHint.getHint(node);
      LogUtils.v(
          TAG,
          "tv    getHint: %s",
          logString.append(String.format("\n    nodeRoleHint={%s}", nodeRoleHint)).toString());
      return nodeRoleHint;
    }
    LogUtils.v(TAG, "tv    getHint: %s", logString.toString());
    return "";
  }
}
