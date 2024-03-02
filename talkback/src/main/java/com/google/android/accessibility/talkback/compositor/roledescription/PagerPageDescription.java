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
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;

/** Role description for {@link Role.ROLE_PAGER}. */
public final class PagerPageDescription implements RoleDescription {

  private final ImageContents imageContents;

  PagerPageDescription(ImageContents imageContents) {
    this.imageContents = imageContents;
  }

  @Override
  public CharSequence nodeName(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.getNodeTextOrLabelDescription(
        node, context, imageContents, globalVariables);
  }

  @Override
  public CharSequence nodeRole(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.getPagerPageRoleDescription(
        node, context, globalVariables);
  }

  @Override
  public CharSequence nodeState(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    CharSequence pagerPageState =
        AccessibilityNodeFeedbackUtils.getNodeStateDescription(
            node,
            context,
            (globalVariables.getUserPreferredLocale() == null)
                ? AccessibilityNodeInfoUtils.getLocalesByNode(node)
                : globalVariables.getUserPreferredLocale());
    if (TextUtils.isEmpty(pagerPageState)) {
      return AccessibilityEventFeedbackUtils.getPagerIndexCount(event, context, globalVariables);
    }
    return pagerPageState;
  }
}
