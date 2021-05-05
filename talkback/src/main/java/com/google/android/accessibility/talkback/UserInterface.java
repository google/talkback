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

package com.google.android.accessibility.talkback;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Interpretation.ID.Value;
import com.google.android.accessibility.talkback.Mappers.Variables;
import com.google.android.accessibility.talkback.controller.SelectorController;
import com.google.android.accessibility.utils.Performance.EventId;

/** A wrapper class for all user interfaces which need to response some input events. */
public class UserInterface {
  private final SelectorController selectorController;
  private final Context context;

  public UserInterface(Context context, SelectorController selectorController) {
    this.context = context;
    this.selectorController = selectorController;
  }

  public void setActorState(ActorState actorState) {
    selectorController.setActorState(actorState);
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    selectorController.setPipeline(pipeline);
  }

  /** Notify the state of selection mode on an editable is changed. */
  public void setSelectionMode(boolean active) {
    selectorController.editTextSelected(active);
  }

  public void handleEvent(
      EventId eventId, AccessibilityEvent event, Interpretation eventInterpretation) {
    if (selectorController == null
        || event == null
        || event.getEventType() != TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      return;
    }
    if (eventInterpretation instanceof Interpretation.ID) {
      final Variables variables = new Variables(context, event, eventInterpretation);
      try {
        if (variables.interpretationID(0) == Value.ACCESSIBILITY_FOCUSED) {
          // Support the Quick Settings the immediate value adjusting.
          selectorController.newItemFocused(event.getSource());
        }
      } finally {
        variables.recycle();
      }
    }
  }
}
