/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess.utils;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import com.google.android.accessibility.utils.widget.SimpleOverlay;

/** Utility class for creating overlays for Switch Access. */
public class OverlayUtils {

  /**
   * Updates the {@link WindowManager.LayoutParams} for the given overlay.
   *
   * @param overlay The {@link SimpleOverlay} for which to set layout params
   */
  public static void setLayoutParamsForFullScreenAccessibilityOverlay(SimpleOverlay overlay) {
    LayoutParams params = overlay.getParams();
    params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    overlay.setParams(params);
  }

  /**
   * Checks whether the given bounds form a point or not.
   *
   * @param bounds The bounds to check
   * @return {@code true} if the given bounds are a point (top == bottom and left == right)
   */
  public static boolean areBoundsAPoint(Rect bounds) {
    return (bounds.top == bounds.bottom) && (bounds.left == bounds.right);
  }

  /**
   * Checks whether the height of the given bounds is larger than half the height of the given
   * screen.
   *
   * @param bounds The bounds whose height to measure
   * @param screenSize The size of the screen to with which to compare height
   * @return {@code true} if the height of the given bounds is greater than half the height of the
   *     screen
   */
  public static boolean areBoundsLargerThanHalfScreenHeight(Rect bounds, Point screenSize) {
    return bounds.height() > (screenSize.y / 2);
  }

  /**
   * Checks whether there is more space in the given screen above or below the given bounds.
   *
   * @param bounds The bounds to check
   * @param screenSize The size of the screen to measure how much space there is
   * @return {@code true} if there is more space above the given bounds than below them
   */
  public static boolean isSpaceAboveBoundsGreater(Rect bounds, Point screenSize) {
    return bounds.top > (screenSize.y - bounds.bottom);
  }

  /** Returns a position below the top of the given bounds using the given padding. */
  public static int getInsideTopBounds(Rect bounds, int paddingInsideBounds) {
    return bounds.top + paddingInsideBounds;
  }

  /** Returns a position above the top of the given bounds using the given padding. */
  public static int getAboveBounds(Rect bounds, int paddingAboveBounds) {
    return bounds.top - paddingAboveBounds;
  }

  /** Returns a position below the bottom of the given bounds using the given padding. */
  public static int getBelowBounds(Rect bounds, int paddingBelowBounds) {
    return bounds.bottom + paddingBelowBounds;
  }
}
