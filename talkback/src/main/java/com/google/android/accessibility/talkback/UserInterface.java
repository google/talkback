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

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.ArrayList;
import java.util.List;

/** A wrapper class for all user interfaces which need to response some input events. */
public class UserInterface {
  /** Interface to notify user input events. */
  public interface UserInputEventListener {
    /**
     * Invoked when the accessibility focus changed.
     *
     * @param nodeInfo the new focused node
     * @param interpretation the focused event interpretation
     */
    void newItemFocused(AccessibilityNodeInfo nodeInfo, Interpretation interpretation);

    /**
     * Invoked when the state of selection mode on an editable is changed.
     *
     * @param active whether the selection mode is active or not
     */
    void editTextOrSelectableTextSelected(boolean active);

    /**
     * Invoked when Touch Interaction is active.
     *
     * @param active The state after TYPE_TOUCH_INTERACTION_START and before
     *     TYPE_TOUCH_INTERACTION_END means active.
     */
    void touchInteractionState(boolean active);
  }

  private final List<UserInputEventListener> listeners = new ArrayList<>();

  /** Notifies the state of selection mode on an editable is changed. */
  public void setSelectionMode(boolean active) {
    if (listeners.isEmpty()) {
      return;
    }

    listeners.stream().forEach(listener -> listener.editTextOrSelectableTextSelected(active));
  }

  public void registerListener(UserInputEventListener listener) {
    listeners.add(listener);
  }

  public void unregisterListener(UserInputEventListener listener) {
    listeners.remove(listener);
  }

  public void handleEvent(
      EventId eventId, AccessibilityEvent event, Interpretation eventInterpretation) {
    if (listeners.isEmpty()) {
      return;
    }
    if (event != null && event.getEventType() == TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      if (eventInterpretation instanceof Interpretation.AccessibilityFocused) {
        listeners.stream()
            .forEach(listener -> listener.newItemFocused(event.getSource(), eventInterpretation));
      }
    } else if (eventInterpretation instanceof Interpretation.TouchInteraction) {
      listeners.stream()
          .forEach(
              listener ->
                  listener.touchInteractionState(
                      ((Interpretation.TouchInteraction) eventInterpretation).interactionActive()));
    }
  }
}
