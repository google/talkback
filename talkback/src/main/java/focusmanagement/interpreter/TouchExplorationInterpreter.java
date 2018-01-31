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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;

/** Interprets {@link AccessibilityEvent} for touch exploration actions. */
public class TouchExplorationInterpreter implements AccessibilityEventListener {
  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER;

  private final AccessibilityFocusManager mA11yFocusManager;

  public TouchExplorationInterpreter(AccessibilityFocusManager accessibilityFocusManager) {
    mA11yFocusManager = accessibilityFocusManager;
  }

  @Override
  public int getEventTypes() {
    return EVENT_MASK;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        dispatchTouchExplorationAction(
            new TouchExplorationAction(
                TouchExplorationAction.TOUCH_INTERACTION_START, /* touchedNode= */ null),
            eventId);
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        dispatchTouchExplorationAction(
            new TouchExplorationAction(
                TouchExplorationAction.TOUCH_INTERACTION_END, /* touchedNode= */ null),
            eventId);
        break;
      default:
        // Hover enter event
        final AccessibilityNodeInfoCompat touchedNode =
            AccessibilityNodeInfoUtils.toCompat(event.getSource());
        if (touchedNode != null) {
          // HoverEnter event without touched node is invalid.
          dispatchTouchExplorationAction(
              new TouchExplorationAction(TouchExplorationAction.HOVER_ENTER, touchedNode), eventId);
        }
        break;
    }
  }

  private void dispatchTouchExplorationAction(TouchExplorationAction action, EventId eventId) {
    // AccessibilityFocusManager should recycle the node after use.
    mA11yFocusManager.sendTouchExplorationAction(action, eventId);
  }
}
