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

import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.ICON_LABEL;
import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.IMAGE_DESCRIPTION;
import static java.lang.Math.max;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ClassLoadingCache;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;

// TODO: Only TalkBack uses the class, it can be moved into the TalkBack folder.
/** Utility class for image captions. */
public class ImageCaptionUtils {

  /** Type of image caption. */
  public enum CaptionType {
    OCR,
    ICON_LABEL,
    IMAGE_DESCRIPTION,
  }

  /** The height restriction is used to avoid the cases when an OpenGL view is one big leaf node. */
  @VisibleForTesting static final int MAX_CAPTION_ABLE_LEAF_VIEW_HEIGHT_IN_DP = 150;

  @VisibleForTesting static final int MIN_IMAGE_SIZE_FOR_AUTOMATIC_IMAGE_CAPTIONING_IN_DP = 60;

  private static final boolean ENABLE_CAPTION_FOR_LEAF_VIEW = true;

  private ImageCaptionUtils() {}

  /**
   * Checks if the node needs automatic image captioning.
   *
   * <p>The nodes without text and content description need image captioning.
   */
  @VisibleForTesting
  static boolean needImageCaption(Context context, @Nullable AccessibilityNodeInfoCompat node) {
    return needImageCaption(
        context,
        node,
        /* enableAutomaticImageCaptionForAllImages= */ false,
        /* needSizeCheck= */ false);
  }

  /**
   * Checks if the node needs automatic image captioning.
   *
   * <p>All image nodes need image captioning if {@code enableAutomaticImageCaptionForAllImages} is
   * true.
   *
   * <p>For non-image view, only the nodes without text and content description need image
   * captioning.
   */
  public static boolean needImageCaption(
      Context context,
      @Nullable AccessibilityNodeInfoCompat node,
      boolean enableAutomaticImageCaptionForAllImages,
      boolean needSizeCheck) {
    return needImageCaptionForUnlabelledView(context, node)
        || (enableAutomaticImageCaptionForAllImages
            && isCaptionable(context, node)
            && (!needSizeCheck || isLargeImage(context, node)));
  }

  /**
   * Checks if the node which has no text and context description needs automatic image captioning.
   */
  public static boolean needImageCaptionForUnlabelledView(
      Context context, @Nullable AccessibilityNodeInfoCompat node) {
    if (!isCaptionable(context, node)) {
      return false;
    }

    @RoleName int role = Role.getRole(node);
    // Do not perform image captions for the view which has slider percent or state description to
    // prevent the additional feedback, like the sign “-” for seekbars and the letter “O” for
    // radio buttons.
    if (role == Role.ROLE_SEEK_CONTROL
        || role == Role.ROLE_PROGRESS_BAR
        || !TextUtils.isEmpty(node.getStateDescription())) {
      return false;
    }

    return TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
  }

  private static boolean isImageOrImageButton(@RoleName int role) {
    return role == Role.ROLE_IMAGE || role == Role.ROLE_IMAGE_BUTTON;
  }

  /**
   * Checks if the node is not a small {@link ImageView} which means its height or width is larger
   * than {@link #MIN_IMAGE_SIZE_FOR_AUTOMATIC_IMAGE_CAPTIONING_IN_DP}.
   */
  private static boolean isLargeImage(Context context, @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    // IMAGE_BUTTON shouldn't been performed auto-captioning but it's difficult to distinguish
    // between ImageView and ImageButton by the Role.java, because in TalkBack ImageButton just
    // means the image is clickable. So instead of using Role.java, here the code checks the class
    // name directly.
    CharSequence className = node.getClassName();
    if (!ClassLoadingCache.checkInstanceOf(className, ImageView.class)
        || ClassLoadingCache.checkInstanceOf(className, ImageButton.class)) {
      return false;
    }

    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    return !rect.isEmpty()
        && max(rect.height(), rect.width())
            > context.getResources().getDisplayMetrics().density
                * MIN_IMAGE_SIZE_FOR_AUTOMATIC_IMAGE_CAPTIONING_IN_DP;
  }

  /**
   * Checks if the node can be captioned.
   *
   * <p>Both images and small leaf views can be captioned.
   */
  public static boolean isCaptionable(Context context, @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null || !FeatureSupport.canTakeScreenShotByAccessibilityService()) {
      return false;
    }

    @RoleName int role = Role.getRole(node);
    if (isImageOrImageButton(role)) {
      return true;
    } else if (ENABLE_CAPTION_FOR_LEAF_VIEW && (node.getChildCount() == 0)) {
      return isSmallSizeNode(context, node);
    }

    return false;
  }

  /**
   * Returns the preferred image caption module for the given node.
   *
   * @return returns icon-detection module is the node size is small. Otherwise, returns
   *     image-description module.
   */
  public static CaptionType getPreferredModuleOnNode(
      Context context, AccessibilityNodeInfoCompat node) {
    return isSmallSizeNode(context, node) ? ICON_LABEL : IMAGE_DESCRIPTION;
  }

  /** Returns {@code true} if the size of the given node is small. */
  private static boolean isSmallSizeNode(Context context, AccessibilityNodeInfoCompat node) {
    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    return !rect.isEmpty()
        && rect.height()
            <= context.getResources().getDisplayMetrics().density
                * MAX_CAPTION_ABLE_LEAF_VIEW_HEIGHT_IN_DP;
  }
}
