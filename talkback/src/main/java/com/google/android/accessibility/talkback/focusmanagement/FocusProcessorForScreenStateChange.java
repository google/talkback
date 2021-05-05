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

import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.talkback.interpreters.AccessibilityFocusInterpreter;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** The event-interpreter for window-events affecting focus. */
public class FocusProcessorForScreenStateChange {
  protected static final String TAG = "FocusInterpForScreen";

  /** Focus result for {@code onScreenStateChanged}. */
  public enum FocusResult {
    FAIL_NO_ACTIVE_WINDOW(-3),
    FAIL_HAS_INTERACTION(-2),
    FAIL_HAS_VALID_FOCUS(-1),
    FAIL_DEFAULT(0),
    SUCCESS(1);
    final int value;
    FocusResult(int result) {
      this.value = result;
    }
  }

  private Pipeline.InterpretationReceiver pipeline;
  private ActorState actorState;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  @VisibleForTesting long handledOverrideFocusRestoreUptimeMs = 0;

  public FocusProcessorForScreenStateChange(AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  public void setPipeline(Pipeline.InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  /**
   * Called by {@link AccessibilityFocusInterpreter} to process focus and return the result of focus
   * status.
   *
   * @param screenState current screen state
   * @param eventId event id
   * @return return {@code true} if event is interpreted as a valid focus-event
   */
  public boolean onScreenStateChanged(ScreenState screenState, EventId eventId) {
    FocusResult result = onScreenStateChangedInternal(screenState, eventId);
    LogUtils.d(
        TAG,
        "Screen state changed with result=%s : \nDuration=%s\nFrom: %s",
        result,
        SystemClock.uptimeMillis() - screenState.getScreenTransitionStartTime(),
        screenState);
    return (result == FocusResult.SUCCESS);
  }

  @VisibleForTesting
  FocusResult onScreenStateChangedInternal(ScreenState screenState, EventId eventId) {
    try {
      AccessibilityWindowInfo currentActiveWindow = screenState.getActiveWindow();
      if (currentActiveWindow == null) {
        return FocusResult.FAIL_NO_ACTIVE_WINDOW;
      }

      int activeWindowId = currentActiveWindow.getId();
      CharSequence activeWindowTitle = screenState.getWindowTitle(activeWindowId);

      AccessibilityFocusActionHistory.Reader history = actorState.getFocusHistory();
      // REFERTO. Initial focus will be skipped if user interacts on old window. So we
      // only skip initial focus if the interaction is happened on active window to ensure it can
      // grant focus.
      FocusActionRecord lastRecordOnActiveWindow =
          history.getLastFocusActionRecordInWindow(activeWindowId, activeWindowTitle);
      if ((lastRecordOnActiveWindow != null)
          && (lastRecordOnActiveWindow.getActionTime()
              > screenState.getScreenTransitionStartTime())) {
        int sourceAction = lastRecordOnActiveWindow.getExtraInfo().sourceAction;
        if ((sourceAction == FocusActionInfo.TOUCH_EXPLORATION)
            || (sourceAction == FocusActionInfo.LOGICAL_NAVIGATION)) {
          // User changes accessibility focus on active window during window transition, don't set
          // initial focus here.
          return FocusResult.FAIL_HAS_INTERACTION;
        }
      }

      if (hasValidAccessibilityFocusInWindow(currentActiveWindow)) {
        return FocusResult.FAIL_HAS_VALID_FOCUS;
      }

      boolean forceRestoreFocus =
          (actorState.getOverrideFocusRestoreUptimeMs() > handledOverrideFocusRestoreUptimeMs);

      pipeline.input(
          eventId,
          /* event= */ null,
          Interpretation.WindowChange.create(forceRestoreFocus, screenState));

      return FocusResult.SUCCESS;

    } finally {
      /**
       * Refresh the update-time whenever window transition finished. Sometimes focus won't restore
       * in special cases, like dismiss dialog when screen off. If we don't refresh the flag, it
       * won't restore focus at screen off stage but restore focus at next visible window transition
       * instead.
       */
      handledOverrideFocusRestoreUptimeMs = actorState.getOverrideFocusRestoreUptimeMs();
    }
  }

  private boolean hasValidAccessibilityFocusInWindow(AccessibilityWindowInfo window) {
    AccessibilityNodeInfoCompat currentFocus = null;
    try {
      currentFocus =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
      return (currentFocus != null)
          && AccessibilityNodeInfoUtils.isVisible(currentFocus)
          && (currentFocus.getWindowId() == window.getId());
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(currentFocus);
    }
  }

}
