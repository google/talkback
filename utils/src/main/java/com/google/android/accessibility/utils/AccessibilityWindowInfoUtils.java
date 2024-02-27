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

import static android.view.accessibility.AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY;
import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_ACCESSIBILITY_OVERLAY;
import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_APPLICATION;
import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD;
import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_SPLIT_SCREEN_DIVIDER;
import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_SYSTEM;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalField;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalInt;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalSubObj;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalTag;
import static com.google.android.accessibility.utils.StringBuilderUtils.optionalText;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Provides a series of utilities for interacting with {@link AccessibilityWindowInfo} objects. */
public class AccessibilityWindowInfoUtils {

  private AccessibilityWindowInfoUtils() {} // Static methods only

  /** Unknown window id. Must match private variable AccessibilityWindowInfo.UNDEFINED_WINDOW_ID */
  public static final int WINDOW_ID_NONE = -1;

  public static final int WINDOW_TYPE_NONE = -1;

  /** Window types, including base & compat & custom values. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TYPE_ACCESSIBILITY_OVERLAY,
    TYPE_APPLICATION,
    TYPE_INPUT_METHOD,
    TYPE_SPLIT_SCREEN_DIVIDER,
    TYPE_SYSTEM,
    TYPE_MAGNIFICATION_OVERLAY,
    WINDOW_TYPE_NONE
  })
  public @interface WindowType {}

  private static final String TAG = "AccessibilityWindowInfoUtils";

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
      if (root == null) {
        return null;
      }
      Rect bounds = new Rect();
      root.getBoundsInScreen(bounds);
      return bounds;
    }
  }

  /** Returns window region in screen. */
  public static @Nullable Region getRegionInScreen(AccessibilityWindowInfo window) {
    if (BuildVersionUtils.isAtLeastR()) {
      Region windowRegion = new Region();
      window.getRegionInScreen(windowRegion);
      return windowRegion;
    } else {
      Rect bounds = getBounds(window);
      if (bounds != null) {
        return new Region(bounds);
      } else {
        return null;
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
      if (getType(window) == TYPE_SPLIT_SCREEN_DIVIDER) {
        windows.remove(window);
        return;
      }
    }
  }

  @WindowType
  public static int getType(@Nullable AccessibilityWindowInfo windowInfo) {
    if (windowInfo == null) {
      return WINDOW_TYPE_NONE;
    }

    @WindowType int windowType = windowInfo.getType();
    if (shouldCheckRootForGmsAppWindow(windowType)) {
      @Nullable AccessibilityNodeInfo root = windowInfo.getRoot();
      @Nullable CharSequence rootPackage = (root == null) ? null : root.getPackageName();
      if (isGmsAppWindow(windowType, rootPackage)) {
        return TYPE_APPLICATION;
      }
    }
    return windowType;
  }

  @WindowType
  public static int getType(@Nullable AccessibilityWindowInfoCompat windowInfo) {
    if (windowInfo == null) {
      return WINDOW_TYPE_NONE;
    }

    @WindowType int windowType = windowInfo.getType();
    if (shouldCheckRootForGmsAppWindow(windowType)) {
      @Nullable AccessibilityNodeInfoCompat root = windowInfo.getRoot();
      @Nullable CharSequence rootPackage = (root == null) ? null : root.getPackageName();
      if (isGmsAppWindow(windowType, rootPackage)) {
        return TYPE_APPLICATION;
      }
    }
    return windowType;
  }

  public static boolean isImeWindow(@Nullable AccessibilityWindowInfo windowInfo) {
    return getType(windowInfo) == TYPE_INPUT_METHOD;
  }

  public static boolean isImeWindow(@Nullable AccessibilityWindowInfoCompat windowInfo) {
    return getType(windowInfo) == TYPE_INPUT_METHOD;
  }

  private static boolean shouldCheckRootForGmsAppWindow(@WindowType int windowType) {
    return (Build.VERSION.SDK_INT == 33) && (windowType == WINDOW_TYPE_NONE);
  }

  private static boolean isGmsAppWindow(
      @WindowType int windowType, @Nullable CharSequence rootPackage) {
    // Also read b/235456507, describing the missing window-type for some GMS windows.
    return shouldCheckRootForGmsAppWindow(windowType)
        && TextUtils.equals("com.google.android.gms", rootPackage);
  }

  @TargetApi(VERSION_CODES.O)
  public static boolean isPictureInPicture(@Nullable AccessibilityWindowInfo window) {
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
    return (root != null) && root.isVisibleToUser();
  }

  public static @Nullable AccessibilityNodeInfoCompat getRootCompat(AccessibilityWindowInfo w) {
    return AccessibilityNodeInfoUtils.toCompat(getRoot(w));
  }

  /** Returns the root node of the tree of {@code windowInfo}. */
  public static @Nullable AccessibilityNodeInfo getRoot(AccessibilityWindowInfo windowInfo) {
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
  public static @Nullable AccessibilityNodeInfoCompat getRoot(
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

  /**
   * Returns the window title from {@link AccessibilityWindowInfo}.
   *
   * <p>Before android-N, even {@link AccessibilityWindowInfoCompat#getTitle} will always return
   * null.
   */
  @TargetApi(VERSION_CODES.N)
  public static @Nullable CharSequence getTitle(AccessibilityWindowInfo windowInfo) {
    if (windowInfo != null && FeatureSupport.supportGetTitleFromWindows()) {
      return windowInfo.getTitle();
    }
    return null;
  }

  /**
   * Returns the ID of the display this window is on. If the platform doesn't support multi-display,
   * it returns {@link Display.DEFAULT_DISPLAY}
   */
  @TargetApi(VERSION_CODES.R)
  public static int getDisplayId(@NonNull AccessibilityWindowInfo windowInfo) {
    if (FeatureSupport.supportMultiDisplay()) {
      return windowInfo.getDisplayId();
    }
    return Display.DEFAULT_DISPLAY;
  }

  /**
   * Gets the node that anchors this window to another.
   *
   * @param window the target window to check
   * @return The anchor node, or {@code null} if none exists.
   */
  @TargetApi(VERSION_CODES.N)
  public static @Nullable AccessibilityNodeInfoCompat getAnchor(AccessibilityWindowInfo window) {
    AccessibilityNodeInfoCompat nodeInfo = null;
    if (window == null || !BuildVersionUtils.isAtLeastN()) {
      return null;
    }

    try {
      nodeInfo = AccessibilityNodeInfoUtils.toCompat(window.getAnchor());
    } catch (SecurityException e) {
      LogUtils.e(
          TAG, "SecurityException occurred at AccessibilityWindowInfoUtils#getAnchor(): %s", e);
    }
    return nodeInfo;
  }

  /**
   * Returns the window anchored by the given node.
   *
   * @param anchor the anchor node
   * @return windowInfo of the anchored window
   */
  public static @Nullable AccessibilityWindowInfo getAnchoredWindow(
      @Nullable AccessibilityNodeInfoCompat anchor) {
    return (anchor == null) ? null : getAnchoredWindow(anchor.unwrap());
  }

  /**
   * Returns the window anchored by the given node.
   *
   * @param anchor the anchor node
   * @return windowInfo of the anchored window
   */
  public static @Nullable AccessibilityWindowInfo getAnchoredWindow(
      @Nullable AccessibilityNodeInfo anchor) {
    @Nullable AccessibilityNodeInfoCompat node;
    AccessibilityWindowInfo window = AccessibilityNodeInfoUtils.getWindow(anchor);
    if (anchor == null || window == null || !BuildVersionUtils.isAtLeastN()) {
      return null;
    }
    for (int i = 0; i < window.getChildCount(); i++) {
      AccessibilityWindowInfo windowInfo = window.getChild(i);
      node = AccessibilityWindowInfoUtils.getAnchor(windowInfo);
      if (node != null && anchor.equals(node.unwrap())) {
        return windowInfo;
      }
    }
    return null;
  }

  @NonNull
  public static String toStringShort(@Nullable AccessibilityWindowInfo window) {
    // Cannot un/wrap AccessibilityWindowInfo/Compat, so this whole method is duplicated.
    if (window == null) {
      return "null";
    }
    Rect windowBounds = new Rect();
    window.getBoundsInScreen(windowBounds);
    return StringBuilderUtils.joinFields(
        "AccessibilityWindowInfo",
        optionalField("type", typeToString(window.getType())),
        optionalInt("id", window.getId(), -1),
        optionalText("title", window.getTitle()),
        optionalSubObj("bounds", windowBounds),
        optionalTag("active", window.isActive()),
        optionalTag("focused", window.isFocused()),
        optionalTag("hasParent", (window.getParent() != null)),
        optionalTag("anchored", (window.getAnchor() != null)),
        optionalInt("numChildren", window.getChildCount(), 0));
  }

  @NonNull
  public static String toStringShort(@Nullable AccessibilityWindowInfoCompat window) {
    if (window == null) {
      return "null";
    }
    Rect windowBounds = new Rect();
    window.getBoundsInScreen(windowBounds);
    return StringBuilderUtils.joinFields(
        "AccessibilityWindowInfoCompat",
        optionalField("type", typeToString(window.getType())),
        optionalInt("id", window.getId(), -1),
        optionalText("title", window.getTitle()),
        optionalSubObj("bounds", windowBounds),
        optionalTag("active", window.isActive()),
        optionalTag("focused", window.isFocused()),
        optionalTag("hasParent", (window.getParent() != null)),
        optionalTag("anchored", (window.getAnchor() != null)),
        optionalInt("numChildren", window.getChildCount(), 0));
  }

  public static String typeToString(@WindowType int windowType) {
    switch (windowType) {
      case TYPE_ACCESSIBILITY_OVERLAY:
        return "TYPE_ACCESSIBILITY_OVERLAY";
      case TYPE_APPLICATION:
        return "TYPE_APPLICATION";
      case TYPE_INPUT_METHOD:
        return "TYPE_INPUT_METHOD";
      case TYPE_SPLIT_SCREEN_DIVIDER:
        return "TYPE_SPLIT_SCREEN_DIVIDER";
      case TYPE_SYSTEM:
        return "TYPE_SYSTEM";
      case AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY:
        return "TYPE_MAGNIFICATION_OVERLAY";
      case WINDOW_TYPE_NONE:
        return "WINDOW_TYPE_NONE";
      default:
        return "(unhandled)";
    }
  }
}
