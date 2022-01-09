/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.android.accessibility.utils.caption;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;

/** Utility class for image captions. */
public class ImageCaptionUtils {

  private ImageCaptionUtils() {}

  /**
   * Checks if the node needs image captions.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the node-argument.
   */
  public static boolean needImageCaption(
      AccessibilityEvent event, @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null || !FeatureSupport.canTakeScreenShotByAccessibilityService()) {
      return false;
    }

    @RoleName int role = Role.getRole(node);
    if (role == Role.ROLE_IMAGE || role == Role.ROLE_IMAGE_BUTTON) {
      return TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
    }

    // Spoken words can come from accessibility event if the focus is on ViewGroup that includes
    // several non-focusable views.
    if (role == Role.ROLE_VIEW_GROUP
        && !TextUtils.isEmpty(AccessibilityEventUtils.getEventTextOrDescription(event))) {
      return false;
    }

    // If the non-focusable image which has no text and no content description is in an actionable
    // ViewGroup, it isn't in the accessibility node tree. In this case, trying to perform image
    // caption on the actionable ViewGroup to get more information about the image.
    return node.isEnabled()
        && AccessibilityNodeInfoUtils.isActionableForAccessibility(node)
        && node.getChildCount() == 0
        && TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
  }
}
