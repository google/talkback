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

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;

/** Utility class for image captions. */
public class ImageCaptionUtils {

  /** The height restriction is used to avoid the cases when an OpenGL view is one big leaf node. */
  @VisibleForTesting static final int MAX_CAPTION_ABLE_LEAF_VIEW_HEIGHT_IN_DP = 150;

  private static final boolean ENABLE_CAPTION_FOR_LEAF_VIEW = true;

  private ImageCaptionUtils() {}

  /** Checks if the node needs image captions. */
  public static boolean needImageCaption(
      Context context, @Nullable AccessibilityNodeInfoCompat node) {
    return isCaptionable(context, node)
        && TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
  }

  /** Checks if the node can be captioned. */
  public static boolean isCaptionable(Context context, @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null || !FeatureSupport.canTakeScreenShotByAccessibilityService()) {
      return false;
    }

    @RoleName int role = Role.getRole(node);
    if (role == Role.ROLE_IMAGE || role == Role.ROLE_IMAGE_BUTTON) {
      return true;
    } else if (ENABLE_CAPTION_FOR_LEAF_VIEW && (node.getChildCount() == 0)) {
      Rect rect = new Rect();
      node.getBoundsInScreen(rect);
      if (!rect.isEmpty()
          && rect.height()
              <= context.getResources().getDisplayMetrics().density
                  * MAX_CAPTION_ABLE_LEAF_VIEW_HEIGHT_IN_DP) {
        return true;
      }
    }

    return false;
  }
}
