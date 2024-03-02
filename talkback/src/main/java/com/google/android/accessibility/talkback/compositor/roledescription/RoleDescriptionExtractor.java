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
import androidx.annotation.IntDef;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Class provides Compositor node role description text. */
public class RoleDescriptionExtractor {

  /** IDs of description orders in verbosity setting. */
  @IntDef({
    DESC_ORDER_ROLE_NAME_STATE_POSITION,
    DESC_ORDER_STATE_NAME_ROLE_POSITION,
    DESC_ORDER_NAME_ROLE_STATE_POSITION
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface DescriptionOrder {}

  public static final int DESC_ORDER_ROLE_NAME_STATE_POSITION = 0;
  public static final int DESC_ORDER_STATE_NAME_ROLE_POSITION = 1;
  public static final int DESC_ORDER_NAME_ROLE_STATE_POSITION = 2;

  private final Context context;
  private final ImageContents imageContents;
  private final GlobalVariables globalVariables;

  private final DefaultDescription defaultDescription;
  private final EditTextDescription editTextDescription;
  private final NonTextViewsDescription nonTextViewsDescription;
  private final PagerPageDescription pagerPageDescription;
  private final SeekBarDescription seekBarDescription;
  private final SwitchDescription switchDescription;

  public RoleDescriptionExtractor(
      Context context, ImageContents imageContents, GlobalVariables globalVariables) {
    this.context = context;
    this.imageContents = imageContents;
    this.globalVariables = globalVariables;
    defaultDescription = new DefaultDescription(imageContents);
    editTextDescription = new EditTextDescription(imageContents);
    nonTextViewsDescription = new NonTextViewsDescription(imageContents);
    pagerPageDescription = new PagerPageDescription(imageContents);
    seekBarDescription = new SeekBarDescription(imageContents);
    switchDescription = new SwitchDescription(imageContents);
  }

  /**
   * Returns the node role description text in the description order.
   *
   * @param node the node for description
   * @param event the event for description
   */
  public CharSequence nodeRoleDescriptionText(
      AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
    return nodeRoleDescriptionText(node, event, globalVariables.getDescriptionOrder());
  }

  /**
   * Returns the node role description text in the description order.
   *
   * <p>Note: Returns node unlabelled state if the node needs label but doesn't have text info.
   *
   * @param node the node for description
   * @param event the event for description
   * @param descriptionOrder the order of the role description
   */
  public CharSequence nodeRoleDescriptionText(
      AccessibilityNodeInfoCompat node,
      AccessibilityEvent event,
      @DescriptionOrder int descriptionOrder) {
    CharSequence nodeUnlabelledState =
        AccessibilityNodeFeedbackUtils.getUnlabelledNodeDescription(
            Role.getRole(node), node, context, imageContents, globalVariables);
    if (!TextUtils.isEmpty(nodeUnlabelledState)) {
      return nodeUnlabelledState;
    }

    RoleDescription roleDescription = getRoleDescription(node);
    if (roleDescription.shouldIgnoreDescription(node)) {
      return "";
    }
    switch (descriptionOrder) {
      case DESC_ORDER_NAME_ROLE_STATE_POSITION:
        return CompositorUtils.dedupJoin(
            roleDescription.nodeName(node, context, globalVariables),
            roleDescription.nodeRole(node, context, globalVariables),
            roleDescription.nodeState(event, node, context, globalVariables));
      case DESC_ORDER_ROLE_NAME_STATE_POSITION:
        return CompositorUtils.dedupJoin(
            roleDescription.nodeRole(node, context, globalVariables),
            roleDescription.nodeName(node, context, globalVariables),
            roleDescription.nodeState(event, node, context, globalVariables));
      case DESC_ORDER_STATE_NAME_ROLE_POSITION:
        return CompositorUtils.dedupJoin(
            roleDescription.nodeState(event, node, context, globalVariables),
            roleDescription.nodeName(node, context, globalVariables),
            roleDescription.nodeRole(node, context, globalVariables));
      default:
        return "";
    }
  }

  /** Returns seek bar state description text. */
  public CharSequence getSeekBarStateDescription(
      AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
    return seekBarDescription.nodeState(event, node, context, globalVariables);
  }

  /** Returns the node role description of the role. */
  RoleDescription getRoleDescription(AccessibilityNodeInfoCompat node) {
    int role = Role.getRole(node);
    switch (role) {
      case Role.ROLE_SWITCH:
      case Role.ROLE_TOGGLE_BUTTON:
        return switchDescription;
      case Role.ROLE_EDIT_TEXT:
        return editTextDescription;
      case Role.ROLE_IMAGE:
      case Role.ROLE_IMAGE_BUTTON:
        return nonTextViewsDescription;
      case Role.ROLE_SEEK_CONTROL:
      case Role.ROLE_PROGRESS_BAR:
        return seekBarDescription;
      case Role.ROLE_PAGER:
        return pagerPageDescription;
      case Role.ROLE_DROP_DOWN_LIST:
        return defaultDescription;
      default:
        // If the parent node is pager, use the pager node description.
        AccessibilityNodeInfoCompat parentNode = node.getParent();
        if (parentNode != null
            && Role.getRole(parentNode) == Role.ROLE_PAGER
            && AccessibilityNodeInfoUtils.countVisibleChildren(parentNode) == 1) {
          return pagerPageDescription;
        }
        return defaultDescription;
    }
  }
}
