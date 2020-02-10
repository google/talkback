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

package com.google.android.accessibility.switchaccess;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.libraries.accessibility.utils.eventfilter.AccessibilityEventListener;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Class to detect possible changes to the UI based on AccessibilityEvents */
public class UiChangeDetector implements AccessibilityEventListener {

  /** Event types that are handled by UiChangeDetector. */
  private static final int MASK_EVENTS_HANDLED_BY_UI_CHANGE_DETECTOR =
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_WINDOWS_CHANGED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

  private final PossibleUiChangeListener listener;

  public UiChangeDetector(PossibleUiChangeListener listener) {
    this.listener = listener;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_UI_CHANGE_DETECTOR;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event == null) {
      return;
    }
    int eventType = event.getEventType();
    boolean willClearFocus =
        (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            || (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
            || (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED);

    if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
      /* Ignore changes that don't affect the view hierarchy */
      int changeTypes = event.getContentChangeTypes();
      changeTypes &= ~AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT;
      changeTypes &= ~AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION;
      willClearFocus = (changeTypes != 0);
    }

    if (willClearFocus) {
      listener.onPossibleChangeToUi(event);
    }
  }

  /**
   * Handle user clicks. These will usually cause the screen to change and, as in the case of
   * showing notifications, might not cause accessibility events to be fired.
   */
  public void onUserClick() {
    listener.onPossibleChangeToUi(null);
  }

  /**
   * Listener that is notified when an {@link AccessibilityEvent} might have caused the UI to
   * change.
   */
  public interface PossibleUiChangeListener {
    void onPossibleChangeToUi(@Nullable AccessibilityEvent event);
  }
}
