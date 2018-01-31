/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build.VERSION_CODES;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Provides a series of utilities for interacting with {@link AccessibilityWindowInfo} objects. */
public class AccessibilityWindowInfoUtils {

  /**
   * A {@link Comparator} to order window top to bottom then left to right (right to left if the
   * screen layout direction is RTL).
   */
  public static class WindowPositionComparator implements Comparator<AccessibilityWindowInfo> {

    private final boolean mIsInRTL;
    private final Rect mRectA = new Rect();
    private final Rect mRectB = new Rect();

    public WindowPositionComparator(boolean isInRTL) {
      mIsInRTL = isInRTL;
    }

    @Override
    public int compare(AccessibilityWindowInfo windowA, AccessibilityWindowInfo windowB) {
      windowA.getBoundsInScreen(mRectA);
      windowB.getBoundsInScreen(mRectB);

      if (mRectA.top != mRectB.top) {
        return mRectA.top - mRectB.top;
      } else {
        return mIsInRTL ? mRectB.right - mRectA.right : mRectA.left - mRectB.left;
      }
    }
  }

  /**
   * Reorders the list of {@link AccessibilityWindowInfo} objects based on window positions on the
   * screen. Removes the {@link AccessibilityWindowInfo} for the split screen divider in
   * multi-window mode.
   */
  public static void sortAndFilterWindows(List<AccessibilityWindowInfo> windows, boolean isInRTL) {
    if (windows == null) {
      return;
    }

    Collections.sort(windows, new WindowPositionComparator(isInRTL));
    for (AccessibilityWindowInfo window : windows) {
      if (window.getType() == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
        windows.remove(window);
        return;
      }
    }
  }

  @TargetApi(VERSION_CODES.O)
  public static boolean isPictureInPicture(AccessibilityWindowInfo window) {
    return BuildVersionUtils.isAtLeastO() && (window != null) && window.isInPictureInPictureMode();
  }
}
