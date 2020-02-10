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

import com.google.android.accessibility.talkback.Pipeline.SyntheticEvent;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.AutoScrollInterpreter;
import com.google.android.accessibility.utils.Performance.EventId;

/** Wrapper around all event-interpreters, for use in Pipeline. */
public class Interpreters {

  private static final String LOG = "Interpreters";

  //////////////////////////////////////////////////////////////////////////////////
  // Member data
  // TODO: Move all event-interpreters into pipeline-interpreters.

  private final AutoScrollInterpreter autoScrollInterpreter;

  //////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Interpreters(AutoScrollInterpreter autoScrollInterpreter) {
    this.autoScrollInterpreter = autoScrollInterpreter;
  }

  public void setActorState(ActorState actorState) {
    this.autoScrollInterpreter.setActorState(actorState);
  }

  public void setPipelineFeedbackReturner(Pipeline.FeedbackReturner pipeline) {
    autoScrollInterpreter.setPipelineFeedbackReturner(pipeline);
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Methods

  // TODO: Add interpret(AccessibilityEvent, EventId), passing events to inner
  // interpreters.

  /** Handles fake internally-generated events. */
  public void interpret(EventId eventId, SyntheticEvent event) {
    if (event.eventType == SyntheticEvent.Type.SCROLL_TIMEOUT) {
      autoScrollInterpreter.handleAutoScrollFailed();
    }
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Interpreter pass-through methods, to keep interpreters private

}
