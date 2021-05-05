/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.interpreters;

import static com.google.android.accessibility.talkback.Interpretation.ID.Value.PASS_THROUGH_INTERACTION_END;
import static com.google.android.accessibility.talkback.Interpretation.ID.Value.PASS_THROUGH_INTERACTION_START;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;

/** Manages pass-through mode by specific touch explore events. */
public class PassThroughModeInterpreter implements AccessibilityEventListener {
  private Pipeline.InterpretationReceiver pipeline;
  private ActorState actorState;
  // The interaction end event will cause the disabling of pass-through mode only when a
  // pass-through-gesture is happening.
  private boolean isInteractionPassThrough;

  public void setPipeline(Pipeline.InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
        | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (!FeatureSupport.supportPassthrough()) {
      return;
    }
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        if (isInteractionPassThrough) {
          pipeline.input(eventId, event, new Interpretation.ID(PASS_THROUGH_INTERACTION_END));
        }
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        if (actorState.getPassThroughModeState().isPassThroughModeActive()) {
          pipeline.input(eventId, event, new Interpretation.ID(PASS_THROUGH_INTERACTION_START));
          isInteractionPassThrough = true;
        } else {
          isInteractionPassThrough = false;
        }
        break;
      default: // fall out
    }
  }
}
