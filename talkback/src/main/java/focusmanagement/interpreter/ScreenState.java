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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
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
  private final HashMap<Integer, AccessibilityWindowInfo> idToWindowInfoMap = new HashMap<>();

  private final AccessibilityWindowInfo activeWindow;

  // Maps from window ID to overridden window title.
  private final SparseArray<CharSequence> overriddenWindowTitles = new SparseArray<>();

  public ScreenState(List<AccessibilityWindowInfo> windows, AccessibilityWindowInfo activeWindow) {
    this.activeWindow = activeWindow;
    if (windows == null || windows.size() == 0) {
      return;
    }
    for (AccessibilityWindowInfo window : windows) {
      idToWindowInfoMap.put(window.getId(), window);
    }
  }

  /**
   * Returns title of window with given window ID.
   *
   * <p><strong>Note: </strong> This method returns null if the window has no title, or the window
   * is not visible, or the window is IME or system window.
   */
  @Nullable
  public CharSequence getWindowTitle(int windowId) {
    AccessibilityWindowInfo window = idToWindowInfoMap.get(windowId);
    if ((window == null)
        || (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        || (window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM)
        || (window.getType() == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER)) {
      // Only return title for application or accessibility windows.
      return null;
    }

    CharSequence eventTitle = overriddenWindowTitles.get(windowId);
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

  public boolean hasIdenticalWindowSetWith(ScreenState another) {
    return (another != null) && idToWindowInfoMap.equals(another.idToWindowInfoMap);
  }

  @Nullable
  public AccessibilityWindowInfo getActiveWindow() {
    return activeWindow;
  }

  public CharSequence getActiveWindowTitle() {
    if (activeWindow == null) {
      return null;
    }
    return getWindowTitle(activeWindow.getId());
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
    if (!idToWindowInfoMap.equals(otherScreen.idToWindowInfoMap)) {
      return false;
    }
    for (int windowId : idToWindowInfoMap.keySet()) {
      if (!TextUtils.equals(getWindowTitle(windowId), otherScreen.getWindowTitle(windowId))) {
        return false;
      }
    }
    return AccessibilityWindowInfoUtils.equals(activeWindow, otherScreen.activeWindow);
  }

  @Override
  public int hashCode() {
    int h = 0;
    Iterator<Entry<Integer, AccessibilityWindowInfo>> iterator =
        idToWindowInfoMap.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<Integer, AccessibilityWindowInfo> entry = iterator.next();
      h += entry.hashCode() ^ Objects.hashCode(getWindowTitle(entry.getKey()));
    }
    return h;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Number of windows:").append(idToWindowInfoMap.size()).append('\n');
    sb.append("System window: ")
        .append(getWindowListString(AccessibilityWindowInfo.TYPE_SYSTEM))
        .append('\n');
    sb.append("Application window: ")
        .append(getWindowListString(AccessibilityWindowInfo.TYPE_APPLICATION))
        .append('\n');
    sb.append("Accessibility window: ")
        .append(getWindowListString(AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY))
        .append('\n');
    sb.append("InputMethod window: ")
        .append(getWindowListString(AccessibilityWindowInfo.TYPE_INPUT_METHOD))
        .append('\n');
    sb.append("PicInPic window: ").append(getPicInPicWindowListString()).append('\n');
    sb.append("isInSplitScreenMode:").append(isInSplitScreenMode());
    return sb.toString();
  }

  /**
   * Conditionally inherits overridden window titles from previous {@link ScreenState}.
   *
   * <p>Developer can update window title by either sending {@link
   * android.view.accessibility.AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} with title as text, or
   * by calling {@link android.view.Window#setTitle(CharSequence)}. If we discover that window title
   * from {@link AccessibilityWindowInfo} has been changed and is not null, we should not inherit
   * out-of-date event title from previous {@link ScreenState}.
   */
  void inheritOverriddenTitlesFromPreviousScreenState(@NonNull ScreenState previousScreenState) {
    for (int windowId : idToWindowInfoMap.keySet()) {
      CharSequence currentWindowInfoTitle = getTitleFromWindowInfo(idToWindowInfoMap.get(windowId));
      CharSequence previousWindowInfoTitle =
          getTitleFromWindowInfo(previousScreenState.idToWindowInfoMap.get(windowId));
      if (!TextUtils.isEmpty(currentWindowInfoTitle)
          && !TextUtils.equals(currentWindowInfoTitle, previousWindowInfoTitle)) {
        continue;
      }
      CharSequence previousOverriddenTitle =
          previousScreenState.overriddenWindowTitles.get(windowId);
      if (!TextUtils.isEmpty(previousOverriddenTitle)) {
        overriddenWindowTitles.put(windowId, previousOverriddenTitle);
      }
    }
  }

  private static CharSequence getTitleFromWindowInfo(AccessibilityWindowInfo window) {
    if ((window == null) || !BuildVersionUtils.isAtLeastN()) {
      return null;
    }
    return window.getTitle();
  }

  void updateOverriddenTitlesFromEvents(@NonNull WindowTransitionInfo windowTransitionInfo) {
    updateOverriddenWindowTitles(windowTransitionInfo.getWindowTitleMap());
  }

  void updateOverriddenWindowTitles(@NonNull SparseArray<CharSequence> windowTitles) {
    for (int windowId : idToWindowInfoMap.keySet()) {
      CharSequence title = windowTitles.get(windowId);
      if (!TextUtils.isEmpty(title)) {
        overriddenWindowTitles.put(windowId, title);
      }
    }
  }

  public HashMap<Integer, AccessibilityWindowInfo> getIdToWindowInfoMap() {
    return idToWindowInfoMap;
  }

  /** Used by {@link #toString()}. */
  private String getWindowDescription(AccessibilityWindowInfo window) {
    if (window == null) {
      return null;
    }
    return String.format(
        "%d-%s%s;",
        window.getId(),
        getWindowTitle(window.getId()),
        window.equals(activeWindow) ? "(active)" : "");
  }

  /** Used by {@link #toString()}. */
  private String getWindowListString(int windowType) {
    StringBuilder sb = new StringBuilder();
    for (AccessibilityWindowInfo window : idToWindowInfoMap.values()) {
      if ((window != null)
          && (window.getType() == windowType)
          && !AccessibilityWindowInfoUtils.isPictureInPicture(window)) {
        sb.append(getWindowDescription(window));
      }
    }
    return sb.toString();
  }

  /** Used by {@link #toString()}. */
  private String getPicInPicWindowListString() {
    StringBuilder sb = new StringBuilder();
    for (AccessibilityWindowInfo window : idToWindowInfoMap.values()) {
      if ((window != null) && AccessibilityWindowInfoUtils.isPictureInPicture(window)) {
        sb.append(getWindowDescription(window));
      }
    }
    return sb.toString();
  }

  /** Used by {@link #toString()}. */
  private boolean isInSplitScreenMode() {
    for (AccessibilityWindowInfo window : idToWindowInfoMap.values()) {
      if ((window != null)
          && window.getType() == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
        return true;
      }
    }
    return false;
  }
}
