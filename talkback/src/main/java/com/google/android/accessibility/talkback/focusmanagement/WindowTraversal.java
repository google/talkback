/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.accessibility.talkback.focusmanagement;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.WindowUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provide a list of the current {@link AccessibilityWindowInfo} from {@link
 * AccessibilityService#getWindows()} by orders for traversal.
 */
public class WindowTraversal {

  private static final int WRONG_INDEX = -1;

  private final boolean mIsInRTL;
  private final List<AccessibilityWindowInfo> mWindows = new ArrayList<AccessibilityWindowInfo>();

  /**
   * The comparator for {@link AccessibilityWindowInfo} by traverse priority:
   *
   * <ul>
   *   <li>Sort by display id in ascending.
   *   <li>Move {@code AccessibilityWindowInfo#TYPE_SYSTEM} to the end.
   *   <li>Sort by position in vertical then horizontal.
   * </ul>
   */
  private static class WindowOrderComparator implements Comparator<AccessibilityWindowInfo> {
    private final boolean mIsInRTL;

    public WindowOrderComparator(boolean isInRTL) {
      mIsInRTL = isInRTL;
    }

    @Override
    public int compare(AccessibilityWindowInfo windowA, AccessibilityWindowInfo windowB) {
      // compare display -> type -> bounds.
      if (FeatureSupport.supportMultiDisplay()
          && (windowA.getDisplayId() != windowB.getDisplayId())) {
        return windowA.getDisplayId() - windowB.getDisplayId();
      }

      if (windowA.getType() == windowB.getType()) {
        return compareBounds(windowA, windowB, mIsInRTL);
      } else if (windowA.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
        return 1;
      } else if (windowB.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
        return -1;
      } else {
        return compareBounds(windowA, windowB, mIsInRTL);
      }
    }

    /** Sorts orders by vertical, then horizontal position. */
    private static int compareBounds(
        AccessibilityWindowInfo windowA, AccessibilityWindowInfo windowB, boolean isRTL) {
      Rect rectA = new Rect();
      Rect rectB = new Rect();
      windowA.getBoundsInScreen(rectA);
      windowB.getBoundsInScreen(rectB);

      if (rectA.top != rectB.top) {
        return rectA.top - rectB.top;
      } else {
        return isRTL ? rectB.right - rectA.right : rectA.left - rectB.left;
      }
    }
  }

  public WindowTraversal(AccessibilityService service) {
    mIsInRTL = WindowUtils.isScreenLayoutRTL(service);
    setWindows(AccessibilityServiceCompatUtils.getWindows(service));
  }

  /** Set windows and sort by {@code WindowOrderComparator}. */
  private void setWindows(List<AccessibilityWindowInfo> windows) {
    // Copy list not to sort the original one
    mWindows.clear();
    Optional.ofNullable(windows).ifPresent(mWindows::addAll);
    Collections.sort(mWindows, new WindowOrderComparator(mIsInRTL));
  }

  /**
   * Whether the {@code baseWindow} is the last window to traverse.
   *
   * @param baseWindow the current window to iterate.
   * @param windowFilter Filters {@link AccessibilityWindowInfo}
   * @return true if there is no window can navigate after baseWindow.
   */
  public boolean isLastWindow(
      AccessibilityWindowInfo baseWindow, Filter<AccessibilityWindowInfo> windowFilter) {
    int index = getWindowIndex(baseWindow);
    if (index == WRONG_INDEX) {
      return true;
    }

    int count = mWindows.size();
    for (int i = index + 1; i < count; i++) {
      AccessibilityWindowInfo window = mWindows.get(i);
      if (windowFilter.accept(window)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether the {@code baseWindow} is the first window to traverse.
   *
   * @param baseWindow the current window to iterate.
   * @param windowFilter Filters {@link AccessibilityWindowInfo}
   * @return true if there is no window can navigate before baseWindow.
   */
  public boolean isFirstWindow(
      AccessibilityWindowInfo baseWindow, Filter<AccessibilityWindowInfo> windowFilter) {
    int index = getWindowIndex(baseWindow);
    if (index <= 0) {
      return true;
    }

    for (int i = index - 1; i >= 0; i--) {
      AccessibilityWindowInfo window = mWindows.get(i);
      if (windowFilter.accept(window)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns window that is previous relatively to {@code currentWindow}. If there is no previous
   * window it returns null.
   */
  public @Nullable AccessibilityWindowInfo getPreviousWindow(
      AccessibilityWindowInfo currentWindow) {
    if (currentWindow == null) {
      return null;
    }
    int resultIndex = getPreviousWindowIndex(getWindowIndex(currentWindow));
    if (resultIndex == WRONG_INDEX) {
      return null;
    }
    return mWindows.get(resultIndex);
  }

  /**
   * Returns window that is next relatively to {@code currentWindow}. If there is no next window it
   * returns null.
   */
  public @Nullable AccessibilityWindowInfo getNextWindow(AccessibilityWindowInfo currentWindow) {
    if (currentWindow == null) {
      return null;
    }
    int resultIndex = getNextWindowIndex(getWindowIndex(currentWindow));
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

}
