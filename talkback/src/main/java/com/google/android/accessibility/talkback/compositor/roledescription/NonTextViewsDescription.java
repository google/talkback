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

/**
 * Role description for non-text-view {@link Role.ROLE_IMAGE} and {@link Role.ROLE_IMAGE_BUTTON}.
 */
public final class NonTextViewsDescription implements RoleDescription {

  private final ImageContents imageContents;

  NonTextViewsDescription(ImageContents imageContents) {
    this.imageContents = imageContents;
  }

  @Override
  public CharSequence nodeName(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.getNodeTextOrLabelOrIdDescription(
        node, context, imageContents, globalVariables);
  }

  @Override
  public CharSequence nodeRole(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    if (!globalVariables.getSpeakRoles() || node == null) {
      return "";
    }

    CharSequence nodeRoleDescription =
        AccessibilityNodeFeedbackUtils.getNodeRoleDescription(node, context, globalVariables);
    if (!TextUtils.isEmpty(nodeRoleDescription)) {
      return nodeRoleDescription;
    }

    if (Role.getRole(node) == Role.ROLE_IMAGE
        && AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityNodeInfoCompat.ACTION_SELECT)) {
      return node.isAccessibilityFocused() ? context.getString(R.string.value_image) : "";
    } else {
      return AccessibilityNodeFeedbackUtils.getNodeRoleName(node, context);
    }
  }

  @Override
  public CharSequence nodeState(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    Locale preferredLocale = globalVariables.getPreferredLocaleByNode(node);
    CharSequence nodeStateDescription =
        AccessibilityNodeFeedbackUtils.getNodeStateDescription(node, context, preferredLocale);
    if (!TextUtils.isEmpty(nodeStateDescription)) {
      return nodeStateDescription;
    }

    CharSequence nodeTextOrLabelOrId =
        AccessibilityNodeFeedbackUtils.getNodeTextOrLabelOrIdDescription(
            node, context, imageContents, globalVariables);
    if (TextUtils.isEmpty(nodeTextOrLabelOrId)) {
      return context.getString(R.string.value_unlabelled);
    }
    return "";
  }
}
