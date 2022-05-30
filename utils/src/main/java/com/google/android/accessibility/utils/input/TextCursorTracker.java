/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.utils.input;

import android.annotation.TargetApi;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class consumes AccessibilityEvents to track the current state of the text cursor, and
 * provides an interface to query it.
 */
public class TextCursorTracker {

  public static final int NO_POSITION = -1;

  /** Event types that are handled by TextCursorTracker. */
  @TargetApi(16)
  private static final int MASK_EVENTS_HANDLED_BY_TEXT_CURSOR_MANAGER =
      AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
          | AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  private @Nullable AccessibilityNodeInfoCompat mNode;
  private int mCurrentCursorPosition = NO_POSITION;
  private int mPreviousCursorPosition = NO_POSITION;

  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_TEXT_CURSOR_MANAGER;
  }

  public void onAccessibilityEvent(AccessibilityEvent event, @Nullable EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
        processTextSelectionChange(event);
        break;
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
        processViewInputFocused(event);
        break;
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        // To get initial cursor position of EditText
        processViewAccessibilityFocused(event);
        break;
      default:
        break;
    }
  }

  private void processViewAccessibilityFocused(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat source = AccessibilityEventUtils.sourceCompat(event);
    if (source == null || mNode != null || !source.isFocused() || !source.isEditable()) {
      return;
    }
    clear();
    mNode = source;
    mCurrentCursorPosition = source.unwrap().getTextSelectionStart();
  }

  /**
   * Updates cursor position when an empty edit text is focused.
   *
   * <p>TalkBack will not receive the initial {@link
   * AccessibilityEvent#TYPE_VIEW_TEXT_SELECTION_CHANGED} event when an empty edit text is focused,
   * in which case we need to manually update the cursor index.
   */
  private void processViewInputFocused(AccessibilityEvent event) {
    @Nullable AccessibilityNodeInfoCompat source = AccessibilityEventUtils.sourceCompat(event);
    if (AccessibilityNodeInfoUtils.isEmptyEditTextRegardlessOfHint(source)) {
      clear();
      mNode = source;
      mCurrentCursorPosition = 0;
    }
  }

  private void processTextSelectionChange(AccessibilityEvent event) {
    @Nullable AccessibilityNodeInfoCompat compat = AccessibilityEventUtils.sourceCompat(event);
    if (compat == null) {
      clear();
      return;
    }

    if ((mNode != null) && compat.equals(mNode)) {
      mPreviousCursorPosition = mCurrentCursorPosition;
      mCurrentCursorPosition = event.getToIndex();
    } else {
      clear();
      mNode = compat;
      mCurrentCursorPosition = event.getToIndex();
    }
  }

  private void clear() {
    if (mNode != null) {
      mNode = null;
    }

    mCurrentCursorPosition = NO_POSITION;
    mPreviousCursorPosition = NO_POSITION;
  }

  public @Nullable AccessibilityNodeInfoCompat getCurrentNode() {
    return mNode;
  }

  public int getCurrentCursorPosition() {
    return mCurrentCursorPosition;
  }

  public int getPreviousCursorPosition() {
    return mPreviousCursorPosition;
  }

  public void forceSetCursorPosition(int previousCursorPosition, int currentCursorPosition) {
    mPreviousCursorPosition = previousCursorPosition;
    mCurrentCursorPosition = currentCursorPosition;
  }
}
