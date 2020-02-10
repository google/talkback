/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.os.SystemClock;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.controller.DirectionNavigationActor;
import com.google.android.accessibility.talkback.focusmanagement.AutoScrollActor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Writable data-structure for feedback from Actors stage to Interpreters stage. */
public class ActorStateWritable {

  /** Caches information of an input focus action. */
  public static final class InputFocusActionRecord {
    public final AccessibilityNodeInfoCompat inputFocusedNode;
    public final long actionTime;

    InputFocusActionRecord(AccessibilityNodeInfoCompat node, long actionTime) {
      this.inputFocusedNode = node;
      this.actionTime = actionTime;
    }
  }

  //////////////////////////////////////////////////////////////////////////
  // Member data

  // TODO: As actors move into pipeline, add needed actor state flags, like
  // scrollReason, inputMode, continuousReading, volumeButtonNavigating, navigatingWithinNode...

  // If actor-state data is only for communication to interpreters, place the state data in
  // ActorState. Else if actor-state data is also used internally by actors, or if actor-state data
  // is derived from actor internal-only data, then put a read-only on-demand data-puller interface
  // in ActorState.

  /** Read-only on-demand data-puller for speaker state data. */
  public final SpeechControllerImpl.State speechState;

  /** Read-only on-demand data-puller for focus history. */
  public final AccessibilityFocusActionHistory.Reader focusHistory;

  /** Last input focus action. This class must recycle contained node. */
  private @Nullable InputFocusActionRecord inputFocusActionRecord;

  /** Window ID from last focus action. */
  private int lastWindowId = -1;

  /** Time of lastWindowId update, to de-duplicate handling of lastWindowId changes. */
  private long lastWindowIdUptimeMs = 0;

  /**
   * Time of control to restore cached focus after the popup window(context menu, dialog) close. It
   * should be set right after close popup window and be called at next window transition.
   */
  private long overrideFocusRestoreUptimeMs = 0;

  /** Read-only on-demand data-puller for scroll state data. */
  public final AutoScrollActor.StateReader scrollState;

  /** Read-only on-demand data-puller for directional navigation. */
  public final DirectionNavigationActor.StateReader directionNavigation;

  //////////////////////////////////////////////////////////////////////////
  // Construction methods

  public ActorStateWritable(
      SpeechControllerImpl.State speechState,
      AutoScrollActor.StateReader scrollState,
      AccessibilityFocusActionHistory.Reader focusHistory,
      DirectionNavigationActor.StateReader directionNavigation) {
    this.speechState = speechState;
    this.scrollState = scrollState;
    this.focusHistory = focusHistory;
    this.directionNavigation = directionNavigation;
  }

  public void recycle() {
    if (inputFocusActionRecord != null) {
      AccessibilityNodeInfoUtils.recycleNodes(inputFocusActionRecord.inputFocusedNode);
      inputFocusActionRecord = null;
    }
  }

  //////////////////////////////////////////////////////////////////////////
  // Data accessor methods

  /** Stores information about completed input-focus action. Caller must recycle node. */
  public void setInputFocus(AccessibilityNodeInfoCompat node) {
    long currentTime = SystemClock.uptimeMillis();
    lastWindowId = node.getWindowId();
    lastWindowIdUptimeMs = currentTime;
    if (inputFocusActionRecord != null) {
      AccessibilityNodeInfoUtils.recycleNodes(inputFocusActionRecord.inputFocusedNode);
    }
    inputFocusActionRecord =
        new InputFocusActionRecord(AccessibilityNodeInfoUtils.obtain(node), currentTime);
  }

  /** Returns nearly immutable focus data-structure. */
  public @Nullable InputFocusActionRecord getInputFocusActionRecord() {
    return inputFocusActionRecord;
  }

  public int getLastWindowId() {
    return lastWindowId;
  }

  public long getLastWindowIdUptimeMs() {
    return lastWindowIdUptimeMs;
  }

  public void setOverrideFocusRestore() {
    overrideFocusRestoreUptimeMs = SystemClock.uptimeMillis();
  }

  public long getOverrideFocusRestoreUptimeMs() {
    return overrideFocusRestoreUptimeMs;
  }

  //////////////////////////////////////////////////////////////////////////
  // Display methods

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        StringBuilderUtils.optionalTag("isSpeaking", speechState.isSpeaking()),
        StringBuilderUtils.optionalTag(
            "isSpeakingOrSpeechQueued", speechState.isSpeakingOrSpeechQueued()),
        StringBuilderUtils.optionalInt("lastWindowId", lastWindowId, -1),
        StringBuilderUtils.optionalInt("lastWindowIdUptimeMs", lastWindowIdUptimeMs, 0),
        StringBuilderUtils.optionalSubObj("inputFocusActionRecord", inputFocusActionRecord),
        StringBuilderUtils.optionalInt(
            "overrideFocusRestoreUptimeMs", overrideFocusRestoreUptimeMs, 0),
        StringBuilderUtils.optionalSubObj("scrollState", scrollState.getAutoScrollRecord()),
        StringBuilderUtils.optionalTag(
            "isSelectionModeActive", directionNavigation.isSelectionModeActive()),
        StringBuilderUtils.optionalField(
            "currentGranularity", directionNavigation.getCurrentGranularity()));
  }
}
