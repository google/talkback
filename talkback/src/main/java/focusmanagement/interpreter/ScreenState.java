/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * A class describes the current window state of screen.
 *
 * <p><strong>Note: </strong>This class is not responsible for recycling AccessibilityWindowInfos.
 */
// TODO: Expose more getters when the relative FocusProcessor is implemented.
public final class ScreenState {

  // Maps from window ID to AccessibilityWindowInfo.
  private HashMap<Integer, AccessibilityWindowInfo> mIdToWindowInfoMap = new HashMap<>();

  private List<AccessibilityWindowInfo> mSystemWindows = new ArrayList<>();
  private List<AccessibilityWindowInfo> mApplicationWindows = new ArrayList<>();
  private List<AccessibilityWindowInfo> mAccessibilityWindows = new ArrayList<>();
  private List<AccessibilityWindowInfo> mInputMethodWindows = new ArrayList<>();
  private List<AccessibilityWindowInfo> mPicInPicWindows = new ArrayList<>();

  private boolean mIsInSplitScreenMode = false;

  // Maps from window ID to overridden window title.
  private SparseArray<CharSequence> mOverriddenWindowTitles = new SparseArray<>();

  public ScreenState(List<AccessibilityWindowInfo> windows) {
    if (windows == null || windows.size() == 0) {
      return;
    }
    for (AccessibilityWindowInfo window : windows) {
      boolean shouldIgnoreWindow = false;
      if (AccessibilityWindowInfoUtils.isPictureInPicture(window)) {
        mPicInPicWindows.add(window);
      } else {
        switch (window.getType()) {
          case AccessibilityWindowInfo.TYPE_SYSTEM:
            mSystemWindows.add(window);
            break;
          case AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER:
            mIsInSplitScreenMode = true;
            shouldIgnoreWindow = true;
            break;
          case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY:
            mAccessibilityWindows.add(window);
            break;
          case AccessibilityWindowInfo.TYPE_APPLICATION:
            mApplicationWindows.add(window);
            break;
          case AccessibilityWindowInfo.TYPE_INPUT_METHOD:
            mInputMethodWindows.add(window);
            break;
          default:
            shouldIgnoreWindow = true;
            break;
        }
      }

      // Ignores split screen divider window, and windows with unidentified types.
      if (!shouldIgnoreWindow) {
        mIdToWindowInfoMap.put(window.getId(), window);
      }
    }
  }

  /**
   * Returns title of window with given window ID.
   *
   * <p><strong>Note: </strong> This method returns null if the window has no title or the window is
   * not visible.
   */
  @Nullable
  public CharSequence getWindowTitle(int windowId) {
    AccessibilityWindowInfo window = mIdToWindowInfoMap.get(windowId);
    if (window == null) {
      // Return null if the queried window is not displayed on screen.
      return null;
    }
    CharSequence eventTitle = mOverriddenWindowTitles.get(windowId);
    if (!TextUtils.isEmpty(eventTitle)) {
      return eventTitle;
    }

    if (BuildVersionUtils.isAtLeastN()) {
      // AccessibilityWindowInfo.getTitle() is available since API 24.
      CharSequence infoTitle = window.getTitle();
      if (!TextUtils.isEmpty(infoTitle)) {
        return infoTitle;
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ScreenState)) {
      return false;
    }
    if (other == this) {
      return true;
    }

    ScreenState otherScreen = (ScreenState) other;
    if (!mIdToWindowInfoMap.equals(otherScreen.mIdToWindowInfoMap)) {
      return false;
    }
    for (int windowId : mIdToWindowInfoMap.keySet()) {
      if (!TextUtils.equals(getWindowTitle(windowId), otherScreen.getWindowTitle(windowId))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int h = 0;
    Iterator<Entry<Integer, AccessibilityWindowInfo>> iterator =
        mIdToWindowInfoMap.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<Integer, AccessibilityWindowInfo> entry = iterator.next();
      h += entry.hashCode() ^ Objects.hashCode(getWindowTitle(entry.getKey()));
    }
    return h;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Number of windows:").append(mIdToWindowInfoMap.size()).append('\n');
    sb.append("System window: ").append(getWindowListString(mSystemWindows)).append('\n');
    sb.append("Application window: ").append(getWindowListString(mApplicationWindows)).append('\n');
    sb.append("Accessibility window: ")
        .append(getWindowListString(mAccessibilityWindows))
        .append('\n');
    sb.append("InputMethod window: ").append(getWindowListString(mInputMethodWindows)).append('\n');
    sb.append("PicInPic window: ").append(getWindowListString(mPicInPicWindows)).append('\n');
    sb.append("isInSplitScreenMode:").append(mIsInSplitScreenMode);
    return sb.toString();
  }

  void updateOverriddenWindowTitles(@NonNull ScreenState anotherScreen) {
    updateOverriddenWindowTitles(anotherScreen.mOverriddenWindowTitles);
  }

  void updateOverriddenWindowTitles(@NonNull WindowTransitionInfo windowTransitionInfo) {
    updateOverriddenWindowTitles(windowTransitionInfo.getWindowTitleMap());
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  void updateOverriddenWindowTitles(@NonNull SparseArray<CharSequence> windowTitles) {
    for (int windowId : mIdToWindowInfoMap.keySet()) {
      CharSequence title = windowTitles.get(windowId);
      if (!TextUtils.isEmpty(title)) {
        mOverriddenWindowTitles.put(windowId, title);
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  HashMap<Integer, AccessibilityWindowInfo> getIdToWindowInfoMap() {
    return mIdToWindowInfoMap;
  }

  /** Used for debug only. */
  private String getWindowListString(List<AccessibilityWindowInfo> windows) {
    if (windows == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (AccessibilityWindowInfo window : windows) {
      if (window != null) {
        sb.append(window.getId()).append('-').append(getWindowTitle(window.getId())).append(';');
      }
    }
    return sb.toString();
  }
}
