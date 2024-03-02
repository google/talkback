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
package com.google.android.accessibility.talkback.compositor.roledescription;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;
import java.util.Locale;

/** Role description for {@link Role.ROLE_SWITCH}. */
public final class SwitchDescription implements RoleDescription {

  private final ImageContents imageContents;

  SwitchDescription(ImageContents imageContents) {
    this.imageContents = imageContents;
  }

  @Override
  public CharSequence nodeName(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return nameDescription(node, context, imageContents, globalVariables);
  }

  @Override
  public CharSequence nodeRole(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.defaultRoleDescription(node, context, globalVariables);
  }

  @Override
  public CharSequence nodeState(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    return stateDescription(node, context, globalVariables);
  }

  /** Returns switch node name description text. */
  private static CharSequence nameDescription(
      AccessibilityNodeInfoCompat node,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables) {
    Locale locale =
        (globalVariables.getUserPreferredLocale() != null)
            ? globalVariables.getUserPreferredLocale()
            : AccessibilityNodeInfoUtils.getLocalesByNode(node);
    CharSequence contentDescription =
        AccessibilityNodeFeedbackUtils.getNodeContentDescription(node, context, locale);
    if (!TextUtils.isEmpty(contentDescription)) {
      return contentDescription;
    }
    // Fallbacks to node text if stateDescription is not set (android version < R).
    // To prevent node state description string speeched repeatedly by SwitchDescription nodeName()
    // and nodeState(). stateDescription() should replace the content with node text string here
    // if stateDescription() already returns node state description string.
    CharSequence stateDescription =
        AccessibilityNodeFeedbackUtils.getNodeStateDescription(node, context, locale);
    if (!TextUtils.isEmpty(stateDescription)) {
      return AccessibilityNodeFeedbackUtils.getNodeText(node, context, locale);
    }
    // Fallbacks to node label.
    return AccessibilityNodeFeedbackUtils.getNodeLabelText(node, imageContents);
  }

  /** Returns switch node state description text. */
  private static CharSequence stateDescription(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    Locale locale =
        (globalVariables.getUserPreferredLocale() != null)
            ? globalVariables.getUserPreferredLocale()
            : AccessibilityNodeInfoUtils.getLocalesByNode(node);
    CharSequence stateDescription =
        AccessibilityNodeFeedbackUtils.getNodeStateDescription(node, context, locale);
    if (!TextUtils.isEmpty(stateDescription)) {
      return stateDescription;
    }
    // Fallbacks to node text.
    CharSequence nodeText = AccessibilityNodeFeedbackUtils.getNodeText(node, context, locale);
    if (!TextUtils.isEmpty(nodeText)) {
      return nodeText;
    }

    // Text of switch widget is "on"/"off" state.
    return node.isChecked()
        ? context.getString(R.string.value_on)
        : context.getString(R.string.value_off);
  }
}
