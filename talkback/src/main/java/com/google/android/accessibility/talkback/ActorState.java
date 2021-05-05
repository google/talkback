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

import static com.google.android.accessibility.talkback.ActorStateWritable.InputFocusActionRecord;

import com.google.android.accessibility.talkback.actor.AutoScrollActor;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.FullScreenReadActor;
import com.google.android.accessibility.talkback.actor.LanguageActor;
import com.google.android.accessibility.talkback.actor.NodeActionPerformer;
import com.google.android.accessibility.talkback.actor.PassThroughModeActor;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Read-only data-structure for feedback from Actors stage to Interpreters stage. Contains
 * writable-data instead of super-classing writable-data, for stronger immutability.
 */
public final class ActorState {

  //////////////////////////////////////////////////////////////////////////
  // Member data

  private final ActorStateWritable writable;

  //////////////////////////////////////////////////////////////////////////
  // Constructor methods

  public ActorState(ActorStateWritable writable) {
    this.writable = writable;
  }

  //////////////////////////////////////////////////////////////////////////
  // Accessor methods

  public DimScreenActor.State getDimScreen() {
    return writable.dimScreen;
  }

  public SpeechControllerImpl.State getSpeechState() {
    return writable.speechState;
  }

  public FullScreenReadActor.State getContinuousRead() {
    return writable.continuousRead;
  }

  public AccessibilityFocusActionHistory.Reader getFocusHistory() {
    return writable.focusHistory;
  }

  public @Nullable InputFocusActionRecord getInputFocusActionRecord() {
    return writable.getInputFocusActionRecord();
  }

  public int getLastWindowId() {
    return writable.getLastWindowId();
  }

  public long getLastWindowIdUptimeMs() {
    return writable.getLastWindowIdUptimeMs();
  }

  public long getOverrideFocusRestoreUptimeMs() {
    return writable.getOverrideFocusRestoreUptimeMs();
  }

  public AutoScrollActor.StateReader getScrollerState() {
    return writable.scrollState;
  }

  public DirectionNavigationActor.StateReader getDirectionNavigation() {
    return writable.directionNavigation;
  }

  public NodeActionPerformer.StateReader getNodeActionPerformerState() {
    return writable.nodeActionState;
  }

  public LanguageActor.State getLanguageState() {
    return writable.languageState;
  }

  public PassThroughModeActor.State getPassThroughModeState() {
    return writable.passThroughModeState;
  }

  public CustomLabelManager.State getCustomLabel() {
    return writable.labelerState;
  }

  //////////////////////////////////////////////////////////////////////////
  // Display methods

  @Override
  public String toString() {
    return writable.toString();
  }
}
