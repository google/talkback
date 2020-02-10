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
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Provides a series of utilities for interacting with {@link AccessibilityWindowInfo} objects. */
public class AccessibilityWindowInfoUtils {

  private static final String TAG = "AccessibilityWindowInfoUtils";

  // TODO: Move these navigation filters to FocusProcessorForLogicalNavigation, since
  // they are only used by that class.
  /** Filters target window when performing directional navigation across windows. */
  public static final Filter<AccessibilityWindowInfo> FILTER_WINDOW_DIRECTIONAL_NAVIGATION =
      new Filter<AccessibilityWindowInfo>() {
        @Override
        public boolean accept(AccessibilityWindowInfo window) {
          if (window == null) {
            return false;
          }
          int type = window.getType();
          return (type == AccessibilityWindowInfoCompat.TYPE_APPLICATION)
              || (type == AccessibilityWindowInfoCompat.TYPE_SPLIT_SCREEN_DIVIDER);
        }
      };
  /** Filters target window when performing window navigation with keyboard shortcuts. */
  public static final Filter<AccessibilityWindowInfo> FILTER_WINDOW_FOR_WINDOW_NAVIGATION =
      new Filter<AccessibilityWindowInfo>() {
        @Override
        public boolean accept(AccessibilityWindowInfo window) {
          if (window == null) {
            return false;
          }
          int type = window.getType();
          return (type == AccessibilityWindowInfo.TYPE_APPLICATION)
              || (type == AccessibilityWindowInfo.TYPE_SYSTEM);
        }
      };

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

  /** Returns window bounds. */
  public static @Nullable Rect getBounds(AccessibilityWindowInfo window) {
    if (BuildVersionUtils.isAtLeastO()) {
      // Rely on window bounds in newer android. Root view may be larger than window, particularly
      // for the split-screen window with launcher in window-2 position.
      Rect bounds = new Rect();
      window.getBoundsInScreen(bounds);
      return bounds;
    } else {
      // Get bounds from root view, because some window bounds may be inaccurate on older android.
      AccessibilityNodeInfo root = getRoot(window);
      try {
        if (root == null) {
          return null;
        }
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        return bounds;
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(root);
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

  public static boolean equals(AccessibilityWindowInfo window1, AccessibilityWindowInfo window2) {
    if (window1 == null) {
      return window2 == null;
    } else {
      return window1.equals(window2);
    }
  }

  public static boolean isWindowContentVisible(AccessibilityWindowInfo window) {
    if (window == null) {
      return false;
    }
    AccessibilityNodeInfo root = getRoot(window);
    boolean isVisible = (root != null) && root.isVisibleToUser();
    if (root != null) {
      root.recycle();
    }
    return isVisible;
  }

  /** Returns the root node of the tree of {@code windowInfo}. */
  @Nullable
  public static AccessibilityNodeInfo getRoot(AccessibilityWindowInfo windowInfo) {
    AccessibilityNodeInfo nodeInfo = null;
    if (windowInfo == null) {
      return null;
    }

    try {
      nodeInfo = windowInfo.getRoot();
    } catch (SecurityException e) {
      LogUtils.e(
          TAG, "SecurityException occurred at AccessibilityWindowInfoUtils#getRoot(): %s", e);
    }
    return nodeInfo;
  }

  /** Returns the root node of the tree of {@code windowInfoCompat}. */
  @Nullable
  public static AccessibilityNodeInfoCompat getRoot(
      AccessibilityWindowInfoCompat windowInfoCompat) {
    AccessibilityNodeInfoCompat nodeInfoCompat = null;
    if (windowInfoCompat == null) {
      return null;
    }

    try {
      nodeInfoCompat = windowInfoCompat.getRoot();
    } catch (SecurityException e) {
      LogUtils.e(
          TAG, "SecurityException occurred at AccessibilityWindowInfoUtils#getRoot(): %s", e);
    }

    return nodeInfoCompat;
  }
}
