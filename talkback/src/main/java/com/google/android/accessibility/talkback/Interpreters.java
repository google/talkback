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

import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import com.google.android.accessibility.talkback.Pipeline.SyntheticEvent;
import com.google.android.accessibility.talkback.actor.voicecommands.VoiceCommandProcessor;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.compositor.EventInterpretation;
import com.google.android.accessibility.talkback.interpreters.AccessibilityEventIdleInterpreter;
import com.google.android.accessibility.talkback.interpreters.AccessibilityFocusInterpreter;
import com.google.android.accessibility.talkback.interpreters.AutoScrollInterpreter;
import com.google.android.accessibility.talkback.interpreters.DirectionNavigationInterpreter;
import com.google.android.accessibility.talkback.interpreters.FullScreenReadInterpreter;
import com.google.android.accessibility.talkback.interpreters.HintEventInterpreter;
import com.google.android.accessibility.talkback.interpreters.InputFocusInterpreter;
import com.google.android.accessibility.talkback.interpreters.ManualScrollInterpreter;
import com.google.android.accessibility.talkback.interpreters.PassThroughModeInterpreter;
import com.google.android.accessibility.talkback.interpreters.ScrollPositionInterpreter;
import com.google.android.accessibility.talkback.interpreters.StateChangeEventInterpreter;
import com.google.android.accessibility.talkback.interpreters.SubtreeChangeEventInterpreter;
import com.google.android.accessibility.talkback.interpreters.UiChangeEventInterpreter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter;
import com.google.android.accessibility.utils.input.SelectionEventInterpreter;
import com.google.android.accessibility.utils.input.TextEventInterpretation;
import java.util.Optional;

/** Wrapper around all event-interpreters, for use in Pipeline. */
public class Interpreters {

  //////////////////////////////////////////////////////////////////////////////////
  // Member data
  // TODO: Move all event-interpreters into pipeline-interpreters.

  private final InputFocusInterpreter inputFocusInterpreter;
  private final ScrollEventInterpreter scrollEventInterpreter;
  private final ManualScrollInterpreter manualScrollInterpreter;
  private final AutoScrollInterpreter autoScrollInterpreter; // Listens to ScrollEventInterpreter
  private final ScrollPositionInterpreter
      scrollPositionInterpreter; // Listens to ScrollEventInterpreter
  private final SelectionEventInterpreter selectionInterpreter;
  private final AccessibilityFocusInterpreter accessibilityFocusInterpreter;
  private final FullScreenReadInterpreter continuousReadInterpreter;
  private final StateChangeEventInterpreter stateChangeEventInterpreter;
  private final DirectionNavigationInterpreter directionNavigationInterpreter;
  private final HintEventInterpreter hintEventInterpreter;
  private final VoiceCommandProcessor voiceCommandProcessor;
  @Nullable private final PassThroughModeInterpreter passThroughModeInterpreter;
  private final SubtreeChangeEventInterpreter subtreeChangeEventInterpreter;
  private final AccessibilityEventIdleInterpreter accessibilityEventIdleInterpreter;
  private final UiChangeEventInterpreter uiChangeEventInterpreter;

  private final int eventTypeMask; // Union of all sub-interpreter masks

  private Optional<InterpretationReceiver> pipelineInterpretations = Optional.empty();

  //////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Interpreters(
      InputFocusInterpreter inputFocusInterpreter,
      ScrollEventInterpreter scrollEventInterpreter,
      ManualScrollInterpreter manualScrollInterpreter,
      AutoScrollInterpreter autoScrollInterpreter,
      ScrollPositionInterpreter scrollPositionInterpreter,
      SelectionEventInterpreter selectionInterpreter,
      AccessibilityFocusInterpreter accessibilityFocusInterpreter,
      FullScreenReadInterpreter continuousReadInterpreter,
      StateChangeEventInterpreter stateChangeEventInterpreter,
      DirectionNavigationInterpreter directionNavigationInterpreter,
      HintEventInterpreter hintEventInterpreter,
      VoiceCommandProcessor voiceCommandProcessor,
      PassThroughModeInterpreter passThroughModeInterpreter,
      SubtreeChangeEventInterpreter subtreeChangeEventInterpreter,
      AccessibilityEventIdleInterpreter accessibilityEventIdleInterpreter,
      UiChangeEventInterpreter uiChangeEventInterpreter) {

    this.inputFocusInterpreter = inputFocusInterpreter;
    this.scrollEventInterpreter = scrollEventInterpreter;
    this.manualScrollInterpreter = manualScrollInterpreter;
    this.autoScrollInterpreter = autoScrollInterpreter;
    this.scrollPositionInterpreter = scrollPositionInterpreter;
    this.selectionInterpreter = selectionInterpreter;
    this.accessibilityFocusInterpreter = accessibilityFocusInterpreter;
    this.continuousReadInterpreter = continuousReadInterpreter;
    this.stateChangeEventInterpreter = stateChangeEventInterpreter;
    this.directionNavigationInterpreter = directionNavigationInterpreter;
    this.hintEventInterpreter = hintEventInterpreter;
    this.voiceCommandProcessor = voiceCommandProcessor;
    this.passThroughModeInterpreter = passThroughModeInterpreter;
    this.subtreeChangeEventInterpreter = subtreeChangeEventInterpreter;
    this.accessibilityEventIdleInterpreter = accessibilityEventIdleInterpreter;
    this.uiChangeEventInterpreter = uiChangeEventInterpreter;

    // Event-interpreters are chained:
    // scrollEventInterpreter -> manualScrollInterpreter -> accessibilityFocusInterpreter
    manualScrollInterpreter.setListener(accessibilityFocusInterpreter);
    scrollEventInterpreter.addListener(manualScrollInterpreter);
    scrollEventInterpreter.addListener(scrollPositionInterpreter);
    scrollEventInterpreter.addListener(autoScrollInterpreter);
    selectionInterpreter.addListener(
        (interpretation) -> {
          if (!interpretation.isTransitional) {
            pipelineInterpretations
                .get()
                .input(
                    interpretation.eventId,
                    interpretation.event,
                    new Interpretation.CompositorID(Compositor.EVENT_TYPE_VIEW_SELECTED));
          }
        });

    eventTypeMask =
        inputFocusInterpreter.getEventTypes()
            | scrollEventInterpreter.getEventTypes()
            | selectionInterpreter.getEventTypes()
            | continuousReadInterpreter.getEventTypes()
            | stateChangeEventInterpreter.getEventTypes()
            | subtreeChangeEventInterpreter.getEventTypes()
            | hintEventInterpreter.getEventTypes()
            | ((passThroughModeInterpreter != null)
                ? passThroughModeInterpreter.getEventTypes()
                : 0);
  }

  public void setActorState(ActorState actorState) {
    inputFocusInterpreter.setActorState(actorState);
    scrollEventInterpreter.setScrollActorState(actorState.getScrollerState());
    manualScrollInterpreter.setActorState(actorState);
    autoScrollInterpreter.setActorState(actorState);
    accessibilityFocusInterpreter.setActorState(actorState);
    continuousReadInterpreter.setActorState(actorState);
    stateChangeEventInterpreter.setActorState(actorState);
    directionNavigationInterpreter.setActorState(actorState);
    hintEventInterpreter.setActorState(actorState);
    if (passThroughModeInterpreter != null) {
      passThroughModeInterpreter.setActorState(actorState);
    }
  }

  public void setPipelineInterpretationReceiver(Pipeline.InterpretationReceiver pipeline) {
    pipelineInterpretations = Optional.of(pipeline);

    // Mappers also listen to scroll-event-interpreter.
    scrollEventInterpreter.addListener(
        (event, interpretation, eventId) ->
            pipeline.input(eventId, event, new Interpretation.Scroll(interpretation)));

    autoScrollInterpreter.setPipelineInterpretationReceiver(pipeline);
    scrollPositionInterpreter.setPipeline(pipeline);
    accessibilityFocusInterpreter.setPipeline(pipeline);
    continuousReadInterpreter.setPipeline(pipeline);
    stateChangeEventInterpreter.setPipeline(pipeline);
    directionNavigationInterpreter.setPipeline(pipeline);
    hintEventInterpreter.setPipelineInterpretationReceiver(pipeline);
    voiceCommandProcessor.setPipelineInterpretationReceiver(pipeline);
    if (passThroughModeInterpreter != null) {
      passThroughModeInterpreter.setPipeline(pipeline);
    }
    subtreeChangeEventInterpreter.setPipeline(pipeline);
    accessibilityEventIdleInterpreter.setPipeline(pipeline);
    uiChangeEventInterpreter.setPipeline(pipeline);
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Methods
  // TODO: Ensure the flow of interpreting events goes with one-way.

  public int getEventTypes() {
    return eventTypeMask;
  }

  /** Handles accessibility-event, asynchronously returning interpretations to pipeline. */
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    hintEventInterpreter.onAccessibilityEvent(event, eventId);
    inputFocusInterpreter.onAccessibilityEvent(event, eventId);
    scrollEventInterpreter.onAccessibilityEvent(event, eventId);
    selectionInterpreter.onAccessibilityEvent(event, eventId);
    continuousReadInterpreter.onAccessibilityEvent(event, eventId);
    stateChangeEventInterpreter.onAccessibilityEvent(event, eventId);
    subtreeChangeEventInterpreter.onAccessibilityEvent(event, eventId);
    if (passThroughModeInterpreter != null) {
      passThroughModeInterpreter.onAccessibilityEvent(event, eventId);
    }
  }

  public void onIdle() {
    accessibilityEventIdleInterpreter.onIdle();
  }

  /** Handles internally-generated events, asynchronously sends interpretations to pipeline. */
  public void interpret(EventId eventId, SyntheticEvent event) {
    if (event.eventType == SyntheticEvent.Type.SCROLL_TIMEOUT) {
      autoScrollInterpreter.handleAutoScrollFailed();
    } else if (event.eventType == SyntheticEvent.Type.TEXT_TRAVERSAL) {
      if (pipelineInterpretations.isEmpty()) {
        return;
      }
      EventInterpretation eventInterpreted =
          new EventInterpretation(Compositor.EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL);
      // Extract traversed text and create TextEventInterpretation.
      TextEventInterpretation textEventInterpreted =
          new TextEventInterpretation(Compositor.EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL);
      textEventInterpreted.setTraversedText(event.eventText);
      eventInterpreted.setTextEventInterpretation(textEventInterpreted);
      eventInterpreted.setReadOnly();
      // Request compositor handle text traversal event by pipeline interpretation.
      pipelineInterpretations
          .get()
          .input(
              eventId,
              /* event= */ null,
              new Interpretation.CompositorID(eventInterpreted.getEvent(), eventInterpreted, null),
              /* eventSourceNode= */ null);
    }
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Interpreter pass-through methods, to keep interpreters private

}
