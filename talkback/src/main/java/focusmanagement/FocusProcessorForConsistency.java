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

package com.google.android.accessibility.talkback.focusmanagement;

import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;

/** The {@link FocusProcessor} to clean up invalid accessibility focus. */
public class FocusProcessorForConsistency extends FocusProcessor {

  /** Event types that are handled by FocusProcessorForConsistency. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_FOR_CONSISTENCY =
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  private final AccessibilityFocusManager mA11yFocusManager;
  private final FollowFocusHandler mFollowFocusHandler;

  FocusProcessorForConsistency(AccessibilityFocusManager accessibilityFocusManager) {
    mA11yFocusManager = accessibilityFocusManager;
    mFollowFocusHandler = new FollowFocusHandler(this);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_FOR_CONSISTENCY;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        mFollowFocusHandler.ensureFocusConsistencyDelayed(eventId);
        break;
      default:
        break;
    }
  }

  /** Attempts to place focus within a new window. */
  private boolean ensureFocusConsistency(EventId eventId) {
    AccessibilityNodeInfoCompat focused = null;

    try {
      // First, see if we've already placed accessibility focus.
      focused = mA11yFocusManager.getAccessibilityFocus();
      if (focused != null) {
        if (AccessibilityNodeInfoUtils.shouldFocusNode(focused)) {
          return true;
        }

        LogUtils.log(Log.VERBOSE, "Clearing focus from invalid node");
        PerformActionUtils.performAction(
            focused, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, eventId);
      }

      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(focused);
    }
  }

  private static final class FollowFocusHandler
      extends WeakReferenceHandler<FocusProcessorForConsistency> {
    private static final int MSG_ENSURE_CONSISTENCY = 0;

    /** Delay after a scroll event before checking focus. */
    private static final long FOCUS_AFTER_CONTENT_CHANGED_DELAY = 500;

    private FollowFocusHandler(FocusProcessorForConsistency parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, FocusProcessorForConsistency parent) {
      if (parent == null) {
        return;
      }
      switch (msg.what) {
        case MSG_ENSURE_CONSISTENCY:
          parent.ensureFocusConsistency((EventId) msg.obj);
          break;
        default:
          break;
      }
    }

    private void ensureFocusConsistencyDelayed(EventId eventId) {
      if (!hasMessages(MSG_ENSURE_CONSISTENCY)) {
        sendMessageDelayed(
            obtainMessage(MSG_ENSURE_CONSISTENCY, eventId), FOCUS_AFTER_CONTENT_CHANGED_DELAY);
      }
    }
  }
}
