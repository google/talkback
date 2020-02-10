/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.utils;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class WindowManager {

  public static final int WRONG_WINDOW_TYPE = -1;
  private static final int WRONG_INDEX = -1;

  private static final int PREVIOUS = -1;
  private static final int NEXT = 1;

  private final boolean mIsInRTL;
  private final List<AccessibilityWindowInfo> mWindows = new ArrayList<AccessibilityWindowInfo>();

  public static class WindowPositionComparator implements Comparator<AccessibilityWindowInfo> {

    private final boolean mIsInRTL;
    private Rect mRectA = new Rect();
    private Rect mRectB = new Rect();

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

  public WindowManager(boolean isInRTL) {
    mIsInRTL = isInRTL;
  }

  public WindowManager(AccessibilityService service) {
    mIsInRTL = isScreenLayoutRTL(service);
    setWindows(AccessibilityServiceCompatUtils.getWindows(service));
  }

  public static boolean isScreenLayoutRTL(Context context) {
    Configuration config = context.getResources().getConfiguration();
    if (config == null) {
      return false;
    }
    return (config.screenLayout & Configuration.SCREENLAYOUT_LAYOUTDIR_MASK)
        == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL;
  }

  /**
   * Set windows that would be used by WindowManager
   *
   * @param windows Set the windows on the screen.
   */
  public void setWindows(List<AccessibilityWindowInfo> windows) {
    // Copy list not to sort the original one.
    mWindows.clear();
    mWindows.addAll(windows);

    Collections.sort(mWindows, new WindowPositionComparator(mIsInRTL));
  }

  /**
   * Returns whether accessibility focused window has AccessibilityWindowInfo.TYPE_APPLICATION type.
   */
  public boolean isApplicationWindowFocused() {
    return isFocusedWindowType(AccessibilityWindowInfo.TYPE_APPLICATION);
  }

  /**
   * Returns whether accessibility focused window has
   * AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER type.
   */
  public boolean isSplitScreenDividerFocused() {
    return isFocusedWindowType(AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER);
  }

  private boolean isFocusedWindowType(int windowType) {
    AccessibilityWindowInfo info = getCurrentWindow(false /* useInputFocus */);
    return info != null && info.getType() == windowType;
  }

  /** returns true if there is no window with windowType after baseWindow */
  public boolean isLastWindow(AccessibilityWindowInfo baseWindow, int windowType) {
    int index = getWindowIndex(baseWindow);
    if (index == WRONG_INDEX) {
      return true;
    }

    int count = mWindows.size();
    for (int i = index + 1; i < count; i++) {
      AccessibilityWindowInfo window = mWindows.get(i);
      if (window != null && window.getType() == windowType) {
        return false;
      }
    }

    return true;
  }

  /** returns true if there is no window with windowType before baseWindow */
  public boolean isFirstWindow(AccessibilityWindowInfo baseWindow, int windowType) {
    int index = getWindowIndex(baseWindow);
    if (index <= 0) {
      return true;
    }

    for (int i = index - 1; i > 0; i--) {
      AccessibilityWindowInfo window = mWindows.get(i);
      if (window != null && window.getType() == windowType) {
        return false;
      }
    }

    return true;
  }

  /**
   * @return window that currently accessibilityFocused window. If there is no accessibility focused
   *     window it returns first window that has TYPE_APPLICATION or null if there is no window with
   *     TYPE_APPLICATION type
   */
  public @Nullable AccessibilityWindowInfo getCurrentWindow(boolean useInputFocus) {
    int currentWindowIndex =
        getFocusedWindowIndex(mWindows, AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
    if (currentWindowIndex != WRONG_INDEX) {
      return mWindows.get(currentWindowIndex);
    }

    if (!useInputFocus) {
      return null;
    }

    currentWindowIndex = getFocusedWindowIndex(mWindows, AccessibilityNodeInfo.FOCUS_INPUT);
    if (currentWindowIndex != WRONG_INDEX) {
      return mWindows.get(currentWindowIndex);
    }

    return null;
  }

  /**
   * @return window that is previous relatively currently accessibilityFocused window. If there is
   *     no accessibility focused window it returns first window that has TYPE_APPLICATION or null
   *     if there is no window with TYPE_APPLICATION type
   */
  public @Nullable AccessibilityWindowInfo getPreviousWindow(AccessibilityWindowInfo pivotWindow) {
    return getWindow(pivotWindow, PREVIOUS);
  }

  /** Gets the window whose anchor equals the given node. */
  public @Nullable AccessibilityWindowInfo getAnchoredWindow(
      @Nullable AccessibilityNodeInfoCompat targetAnchor) {
    if (!BuildVersionUtils.isAtLeastN() || targetAnchor == null) {
      return null;
    }

    int windowCount = mWindows.size();
    for (int i = 0; i < windowCount; ++i) {
      AccessibilityWindowInfo window = mWindows.get(i);
      if (window != null) {
        AccessibilityNodeInfo anchor = window.getAnchor();
        if (anchor != null) {
          try {
            if (anchor.equals(targetAnchor.unwrap())) {
              return window;
            }
          } finally {
            anchor.recycle();
          }
        }
      }
    }

    return null;
  }

  public boolean isInputWindowOnScreen() {
    if (mWindows == null) {
      return false;
    }

    for (AccessibilityWindowInfo window : mWindows) {
      if (window != null && window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        return true;
      }
    }

    return false;
  }

  public int getWindowType(int windowId) {
    if (mWindows != null) {
      for (AccessibilityWindowInfo window : mWindows) {
        if (window != null && window.getId() == windowId) {
          return window.getType();
        }
      }
    }

    return WRONG_WINDOW_TYPE;
  }

  public boolean isStatusBar(int windowId) {
    if (mWindows == null || mWindows.size() == 0) {
      return false;
    }

    return mWindows.get(0).getId() == windowId
        && mWindows.get(0).getType() == AccessibilityWindowInfo.TYPE_SYSTEM;
  }

  public boolean isNavigationBar(int windowId) {
    if (mWindows == null || mWindows.size() < 2) {
      return false;
    }

    int lastIndex = mWindows.size() - 1;
    return mWindows.get(lastIndex).getId() == windowId
        && mWindows.get(lastIndex).getType() == AccessibilityWindowInfo.TYPE_SYSTEM;
  }

  /**
   * @return window that is next relatively currently accessibilityFocused window. If there is no
   *     accessibility focused window it returns first window that has TYPE_APPLICATION or null if
   *     there is no window with TYPE_APPLICATION type
   */
  public @Nullable AccessibilityWindowInfo getNextWindow(AccessibilityWindowInfo pivotWindow) {
    return getWindow(pivotWindow, NEXT);
  }

  private @Nullable AccessibilityWindowInfo getWindow(
      AccessibilityWindowInfo pivotWindow, int direction) {
    if (mWindows == null || pivotWindow == null || (direction != NEXT && direction != PREVIOUS)) {
      return null;
    }

    int currentWindowIndex = getWindowIndex(pivotWindow);
    int resultIndex;
    if (direction == NEXT) {
      resultIndex = getNextWindowIndex(currentWindowIndex);
    } else {
      resultIndex = getPreviousWindowIndex(currentWindowIndex);
    }

    if (resultIndex == WRONG_INDEX) {
      return null;
    }

    return mWindows.get(resultIndex);
  }

  private int getNextWindowIndex(int currentIndex) {
    int size = mWindows.size();
    if (size == 0 || currentIndex < 0 || currentIndex >= size) {
      return WRONG_INDEX;
    }

    currentIndex++;
    if (currentIndex > size - 1) {
      currentIndex = 0;
    }
    return currentIndex;
  }

  private int getPreviousWindowIndex(int currentIndex) {
    int size = mWindows.size();
    if (size == 0 || currentIndex < 0 || currentIndex >= size) {
      return WRONG_INDEX;
    }

    currentIndex--;
    if (currentIndex < 0) {
      currentIndex = size - 1;
    }
    return currentIndex;
  }

  private int getWindowIndex(AccessibilityWindowInfo windowInfo) {
    if (mWindows == null || windowInfo == null) {
      return WRONG_INDEX;
    }

    int windowSize = mWindows.size();
    for (int i = 0; i < windowSize; i++) {
      if (windowInfo.equals(mWindows.get(i))) {
        return i;
      }
    }

    return WRONG_INDEX;
  }

  private static int getFocusedWindowIndex(List<AccessibilityWindowInfo> windows, int focusType) {
    if (windows == null) {
      return WRONG_INDEX;
    }

    for (int i = 0, size = windows.size(); i < size; i++) {
      AccessibilityWindowInfo window = windows.get(i);
      if (window == null) {
        continue;
      }

      if (focusType == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
          && window.isAccessibilityFocused()) {
        return i;
      } else if (focusType == AccessibilityNodeInfo.FOCUS_INPUT && window.isFocused()) {
        return i;
      }
    }

    return WRONG_INDEX;
  }
}
