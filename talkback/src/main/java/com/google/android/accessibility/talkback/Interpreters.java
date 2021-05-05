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

import androidx.annotation.Nullable;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Pipeline.SyntheticEvent;
import com.google.android.accessibility.talkback.actor.voicecommands.VoiceCommandProcessor;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorAccessibilityHints;
import com.google.android.accessibility.talkback.interpreters.AccessibilityFocusInterpreter;
import com.google.android.accessibility.talkback.interpreters.AutoScrollInterpreter;
import com.google.android.accessibility.talkback.interpreters.DirectionNavigationInterpreter;
import com.google.android.accessibility.talkback.interpreters.FullScreenReadInterpreter;
import com.google.android.accessibility.talkback.interpreters.InputFocusInterpreter;
import com.google.android.accessibility.talkback.interpreters.PassThroughModeInterpreter;
import com.google.android.accessibility.talkback.interpreters.ScrollPositionInterpreter;
import com.google.android.accessibility.talkback.interpreters.StateChangeEventInterpreter;
import com.google.android.accessibility.utils.Performance.EventId;

/** Wrapper around all event-interpreters, for use in Pipeline. */
public class Interpreters {

  //////////////////////////////////////////////////////////////////////////////////
  // Member data
  // TODO: Move all event-interpreters into pipeline-interpreters.

  private final InputFocusInterpreter inputFocusInterpreter;
  private final AutoScrollInterpreter autoScrollInterpreter; // Listens to ScrollEventInterpreter
  private final ScrollPositionInterpreter
      scrollPositionInterpreter; // Listens ScrollEventInterpreter
  private final AccessibilityFocusInterpreter accessibilityFocusInterpreter;
  private final FullScreenReadInterpreter continuousReadInterpreter;
  private final StateChangeEventInterpreter stateChangeEventInterpreter;
  private final DirectionNavigationInterpreter directionNavigationInterpreter;
  private final ProcessorAccessibilityHints processorAccessibilityHints;
  private final VoiceCommandProcessor voiceCommandProcessor;
  @Nullable private final PassThroughModeInterpreter passThroughModeInterpreter;

  private final int eventTypeMask; // Union of all sub-interpreter masks

  //////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Interpreters(
      InputFocusInterpreter inputFocusInterpreter,
      AutoScrollInterpreter autoScrollInterpreter,
      ScrollPositionInterpreter scrollPositionInterpreter,
      AccessibilityFocusInterpreter accessibilityFocusInterpreter,
      FullScreenReadInterpreter continuousReadInterpreter,
      StateChangeEventInterpreter stateChangeEventInterpreter,
      DirectionNavigationInterpreter directionNavigationInterpreter,
      ProcessorAccessibilityHints processorAccessibilityHints,
      VoiceCommandProcessor voiceCommandProcessor,
      PassThroughModeInterpreter passThroughModeInterpreter) {

    this.inputFocusInterpreter = inputFocusInterpreter;
    this.autoScrollInterpreter = autoScrollInterpreter;
    this.scrollPositionInterpreter = scrollPositionInterpreter;
    this.accessibilityFocusInterpreter = accessibilityFocusInterpreter;
    this.continuousReadInterpreter = continuousReadInterpreter;
    this.stateChangeEventInterpreter = stateChangeEventInterpreter;
    this.directionNavigationInterpreter = directionNavigationInterpreter;
    this.processorAccessibilityHints = processorAccessibilityHints;
    this.voiceCommandProcessor = voiceCommandProcessor;
    this.passThroughModeInterpreter = passThroughModeInterpreter;

    eventTypeMask =
        inputFocusInterpreter.getEventTypes()
            | continuousReadInterpreter.getEventTypes()
            | stateChangeEventInterpreter.getEventTypes()
            | ((passThroughModeInterpreter != null)
                ? passThroughModeInterpreter.getEventTypes()
                : 0);
  }

  public void setActorState(ActorState actorState) {
    inputFocusInterpreter.setActorState(actorState);
    autoScrollInterpreter.setActorState(actorState);
    accessibilityFocusInterpreter.setActorState(actorState);
    continuousReadInterpreter.setActorState(actorState);
    stateChangeEventInterpreter.setActorState(actorState);
    directionNavigationInterpreter.setActorState(actorState);
    if (passThroughModeInterpreter != null) {
      passThroughModeInterpreter.setActorState(actorState);
    }
  }

  public void setPipelineInterpretationReceiver(Pipeline.InterpretationReceiver pipeline) {
    inputFocusInterpreter.setPipeline(pipeline);
    autoScrollInterpreter.setPipelineInterpretationReceiver(pipeline);
    scrollPositionInterpreter.setPipeline(pipeline);
    accessibilityFocusInterpreter.setPipeline(pipeline);
    continuousReadInterpreter.setPipeline(pipeline);
    stateChangeEventInterpreter.setPipeline(pipeline);
    directionNavigationInterpreter.setPipeline(pipeline);
    processorAccessibilityHints.setPipelineInterpretationReceiver(pipeline);
    voiceCommandProcessor.setPipelineInterpretationReceiver(pipeline);
    if (passThroughModeInterpreter != null) {
      passThroughModeInterpreter.setPipeline(pipeline);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Methods

  public int getEventTypes() {
    return eventTypeMask;
  }

  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    inputFocusInterpreter.onAccessibilityEvent(event, eventId);
    continuousReadInterpreter.onAccessibilityEvent(event, eventId);
    stateChangeEventInterpreter.onAccessibilityEvent(event, eventId);
    if (passThroughModeInterpreter != null) {
      passThroughModeInterpreter.onAccessibilityEvent(event, eventId);
    }
  }

  /** Handles internally-generated events, asynchronously sends interpretations to pipeline. */
  public void interpret(EventId eventId, SyntheticEvent event) {
    if (event.eventType == SyntheticEvent.Type.SCROLL_TIMEOUT) {
      autoScrollInterpreter.handleAutoScrollFailed();
    }
  }

  /** Handles accessibility-events, asynchronously returning interpretations to pipeline. */
  public void interpret(EventId eventId, AccessibilityEvent event) {
    if (continuousReadInterpreter.matches(event)) {
      continuousReadInterpreter.onAccessibilityEvent(event, eventId);
    }
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Interpreter pass-through methods, to keep interpreters private

}
