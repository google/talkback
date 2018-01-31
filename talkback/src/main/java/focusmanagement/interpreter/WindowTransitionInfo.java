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
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashSet;
import java.util.List;

/**
 * A data structure to cache information during window transition.
 *
 * <p><strong>Window transition</strong> happens when the user opens/closes an application, dialog,
 * soft keyboard, etc. An accessibility service might receive <b>multiple</b> {@link
 * AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} events during the transition. This class caches two
 * kinds of information from the events:
 *
 * <ul>
 *   <li>ID of window being changed, retrieved by {@link AccessibilityEvent#getWindowId()}
 *   <li>Title of window, retrieved by {@link AccessibilityEvent#getText()}
 * </ul>
 */
public class WindowTransitionInfo {
  private static final int WINDOW_ID_NONE = -1;

  // We ignore TYPE_WINDOWS_CHANGED event here because it doesn't contain window ID/title
  // information.
  private static final int EVENT_MASK = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  /**
   * Caches window title retrieved from TYPE_WINDOW_STATE_CHANGED events during the window
   * transition.
   */
  private SparseArray<CharSequence> mCachedWindowTitlesFromEvents = new SparseArray<>();

  /** Caches ID of windows changed during the transition. */
  private HashSet<Integer> mStateChangedWindows = new HashSet<>();

  /** Caches window title and ID from events. */
  public void updateTransitionInfoFromEvent(@NonNull AccessibilityEvent event) {
    if ((event.getEventType() & EVENT_MASK) == 0) {
      return;
    }
    cacheWindowIdUnderTransition(event);
    // For some apps, the title information only resides in TYPE_WINDOW_STATE_CHANGED events. Thus
    // we need to cache and propagate the title properly.
    cacheWindowTitleFromEvent(event);
  }

  /** Returns whether the window with given windowId is changed during transition. */
  public boolean isWindowStateRecentlyChanged(int windowId) {
    return mStateChangedWindows.contains(windowId);
  }

  /** Returns the window title retrieved from accessibility events. */
  public CharSequence getWindowTitle(int windowId) {
    return mCachedWindowTitlesFromEvents.get(windowId);
  }

  /** Clears all the cached information during the transition. */
  public void clear() {
    mCachedWindowTitlesFromEvents.clear();
    mStateChangedWindows.clear();
  }

  @NonNull
  SparseArray<CharSequence> getWindowTitleMap() {
    return mCachedWindowTitlesFromEvents;
  }

  private void cacheWindowIdUnderTransition(@NonNull AccessibilityEvent event) {
    int windowId = event.getWindowId();
    if (windowId != WINDOW_ID_NONE) {
      mStateChangedWindows.add(windowId);
    }
  }

  private void cacheWindowTitleFromEvent(@NonNull AccessibilityEvent event) {
    int windowId = event.getWindowId();
    if (windowId == WINDOW_ID_NONE) {
      return;
    }
    List<CharSequence> texts = event.getText();
    if (texts == null || texts.size() == 0) {
      return;
    }
    // Same logic as in ProcessorScreen. The event text could be a list of text gathered from the
    // node tree inside the window(e.g. AlertDialog). We aggressively assume the first text to be
    // the window title.
    CharSequence title = texts.get(0);
    if (!TextUtils.isEmpty(title)) {
      mCachedWindowTitlesFromEvents.put(windowId, title);
    }
  }
}
