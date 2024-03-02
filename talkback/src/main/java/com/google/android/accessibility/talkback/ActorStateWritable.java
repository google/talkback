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
import com.google.android.accessibility.talkback.actor.AutoScrollActor;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.FullScreenReadActor;
import com.google.android.accessibility.talkback.actor.LanguageActor;
import com.google.android.accessibility.talkback.actor.NodeActionPerformer;
import com.google.android.accessibility.talkback.actor.PassThroughModeActor;
import com.google.android.accessibility.talkback.actor.SpeechRateActor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.labeling.LabelManager;
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

  public final DimScreenActor.State dimScreen;

  /** Read-only on-demand data-puller for speaker state data. */
  public final SpeechControllerImpl.State speechState;

  /** Read-only on-demand data-reader for continuous-reading state data. */
  public final FullScreenReadActor.State continuousRead;

  /** Read-only on-demand data-puller for focus history. */
  public final AccessibilityFocusActionHistory.Reader focusHistory;

  /** Last input focus action. */
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

  /** Last performed system action ID. */
  private int lastSystemAction = 0;

  /** Read-only on-demand data-puller for scroll state data. */
  public final AutoScrollActor.StateReader scrollState;

  /** Read-only on-demand data-puller for directional navigation. */
  public final DirectionNavigationActor.StateReader directionNavigation;

  /** Read-only on-demand data-puller for node action state data. */
  public final NodeActionPerformer.StateReader nodeActionState;

  /** Read-only on-demand data-puller for language state data. */
  public final LanguageActor.State languageState;

  /** Read-only on-demand data-puller for speech rate state data. */
  public final SpeechRateActor.State speechRateState;

  /** Read-only on-demand data-puller for pass-through mode state data. */
  public final PassThroughModeActor.State passThroughModeState;

  /** Read-only on-demand data-puller for CustomLabelManager state data. */
  public final LabelManager.State labelerState;

  //////////////////////////////////////////////////////////////////////////
  // Construction methods

  public ActorStateWritable(
      DimScreenActor.State dimScreen,
      SpeechControllerImpl.State speechState,
      FullScreenReadActor.State continuousRead,
      AutoScrollActor.StateReader scrollState,
      AccessibilityFocusActionHistory.Reader focusHistory,
      DirectionNavigationActor.StateReader directionNavigation,
      NodeActionPerformer.StateReader nodeActionState,
      LanguageActor.State languageState,
      SpeechRateActor.State speechRateState,
      PassThroughModeActor.State passThroughModeState,
      LabelManager.State labelerState) {
    this.dimScreen = dimScreen;
    this.speechState = speechState;
    this.continuousRead = continuousRead;
    this.scrollState = scrollState;
    this.focusHistory = focusHistory;
    this.directionNavigation = directionNavigation;
    this.nodeActionState = nodeActionState;
    this.languageState = languageState;
    this.speechRateState = speechRateState;
    this.passThroughModeState = passThroughModeState;
    this.labelerState = labelerState;
  }

  //////////////////////////////////////////////////////////////////////////
  // Data accessor methods

  /** Stores information about completed input-focus action. */
  public void setInputFocus(AccessibilityNodeInfoCompat node, long currentTime) {
    lastWindowId = node.getWindowId();
    lastWindowIdUptimeMs = currentTime;
    inputFocusActionRecord = new InputFocusActionRecord(node, currentTime);
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

  public int getLastSystemAction() {
    return lastSystemAction;
  }

  public void setLastSystemAction(int action) {
    lastSystemAction = action;
  }

  //////////////////////////////////////////////////////////////////////////
  // Display methods

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        StringBuilderUtils.optionalTag("isSpeaking", speechState.isSpeaking()),
        StringBuilderUtils.optionalTag(
            "isSpeakingOrQueuedAndNotSourceIsVolumeAnnouncment",
            speechState.isSpeakingOrQueuedAndNotSourceIsVolumeAnnouncment()),
        StringBuilderUtils.optionalInt("lastWindowId", lastWindowId, -1),
        StringBuilderUtils.optionalInt("lastWindowIdUptimeMs", lastWindowIdUptimeMs, 0),
        StringBuilderUtils.optionalSubObj("inputFocusActionRecord", inputFocusActionRecord),
        StringBuilderUtils.optionalInt(
            "overrideFocusRestoreUptimeMs", overrideFocusRestoreUptimeMs, 0),
        StringBuilderUtils.optionalSubObj("scrollState", scrollState.get()),
        StringBuilderUtils.optionalTag(
            "isSelectionModeActive", directionNavigation.isSelectionModeActive()),
        StringBuilderUtils.optionalField(
            "currentGranularity", directionNavigation.getCurrentGranularity()),
        StringBuilderUtils.optionalTag("allowSelectLanguage", languageState.allowSelectLanguage()),
        StringBuilderUtils.optionalInt(
            "speechRatePercent", speechRateState.getSpeechRatePercentage(), 100),
        StringBuilderUtils.optionalTag(
            "passThroughModeState", passThroughModeState.isPassThroughModeActive()));
  }
}
