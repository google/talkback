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
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;

/**
 * Role description for default node roles.
 *
 * <p>Note: This class is for the general node roles that are not customized in TalkBack compositor.
 * The following roles are excluded from the default node role.
 *
 * <ul>
 *   <li>{@link Role.ROLE_EDIT_TEXT}
 *   <li>{@link Role.ROLE_IMAGE}, {@link Role.ROLE_IMAGE_BUTTON}
 *   <li>{@link Role.ROLE_PAGER}
 *   <li>{@link Role.ROLE_SEEK_CONTROL}
 *   <li>{@link Role.ROLE_SWITCH}
 * </ul>
 */
public class DefaultDescription implements RoleDescription {

  private final ImageContents imageContents;

  DefaultDescription(ImageContents imageContents) {
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
    return AccessibilityNodeFeedbackUtils.defaultRoleDescription(node, context, globalVariables);
  }

  @Override
  public CharSequence nodeState(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.getNodeStateDescription(
        node,
        context,
        (globalVariables.getUserPreferredLocale() != null)
            ? globalVariables.getUserPreferredLocale()
            : AccessibilityNodeInfoUtils.getLocalesByNode(node));
  }
}
