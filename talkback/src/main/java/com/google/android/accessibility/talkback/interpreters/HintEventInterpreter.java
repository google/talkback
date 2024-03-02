/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static com.google.android.accessibility.utils.input.TextEventInterpretation.SELECTION_MOVE_CURSOR_NO_SELECTION;
import static com.google.android.accessibility.utils.input.TextEventInterpretation.TEXT_ADD;

import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Interpretation.ID.Value;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.utils.TalkbackFeatureSupport;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.TextEventInterpretation;
import com.google.android.accessibility.utils.input.TextEventInterpretation.TextEvent;
import com.google.android.accessibility.utils.input.TextEventInterpreter;
import com.google.android.accessibility.utils.input.TextEventInterpreter.InterpretationConsumer;

/** Filters events that cause hints. */
public class HintEventInterpreter implements AccessibilityEventListener, InterpretationConsumer {

  ///////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Event types that are handled by HintEventInterpreter. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | TYPE_VIEW_FOCUSED;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private ActorState actorState;
  private Pipeline.InterpretationReceiver pipelineInterpretationReceiver;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public HintEventInterpreter() {}

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setPipelineInterpretationReceiver(Pipeline.InterpretationReceiver pipeline) {
    this.pipelineInterpretationReceiver = pipeline;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Methods

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS;
  }

  /** Filters out events that are not hintable, merging actor-state data with event. */
  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {

    boolean forceFeedbackEvenIfAudioPlaybackActive = false;
    boolean forceFeedbackEvenIfMicrophoneActive = false;

    final int eventType = event.getEventType();
    if (eventType == TYPE_VIEW_FOCUSED) {
      if (FormFactorUtils.getInstance().isAndroidTv()) {
        // On TV, we will always sync accessibility-focus to input-focus, so it is sufficient to
        // speak on TYPE_VIEW_ACCESSIBILITY_FOCUSED.
        return;
      }
      AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      if (source == null) {
        return;
      }
    }

    // Schedule delayed hint for accessibility-focus event.
    if (eventType == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      FocusActionInfo focusActionInfo =
          actorState.getFocusHistory().getFocusActionInfoFromEvent(event);

      if (focusActionInfo != null) {
        if (focusActionInfo.forceMuteFeedback) {
          return;
        }
        // We don't announce node hints when navigating with micro granularity.
        if ((focusActionInfo.navigationAction != null)
            && (focusActionInfo.navigationAction.originalNavigationGranularity != null)
            && focusActionInfo.navigationAction.originalNavigationGranularity
                .isMicroGranularity()) {
          return;
        }
      }

      AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      forceFeedbackEvenIfAudioPlaybackActive =
          (focusActionInfo != null) && focusActionInfo.forceFeedbackEvenIfAudioPlaybackActive();
      forceFeedbackEvenIfMicrophoneActive =
          (focusActionInfo != null) && focusActionInfo.forceFeedbackEvenIfMicrophoneActive();

      if (source == null) {
        return;
      }
    }

    pipelineInterpretationReceiver.input(
        eventId,
        event,
        new Interpretation.HintableEvent(
            forceFeedbackEvenIfAudioPlaybackActive, forceFeedbackEvenIfMicrophoneActive));
  }

  /** Handles the interpreted text-change event. */
  @Override
  public void accept(TextEventInterpreter.Interpretation interpretation) {
    if (!TalkbackFeatureSupport.supportTextSuggestion()) {
      return;
    }

    TextEventInterpretation textEventInterpretation = interpretation.interpretation;
    AccessibilityNodeInfoCompat node =
        AccessibilityNodeInfoUtils.toCompat(interpretation.event.getSource());
    if (node == null || textEventInterpretation == null) {
      return;
    }

    @TextEvent int event = textEventInterpretation.getEvent();
    if (event == SELECTION_MOVE_CURSOR_NO_SELECTION
        || (event == TEXT_ADD && node.getTextSelectionStart() == node.getTextSelectionEnd())) {
      // Suggestion span may not be ready when receiving a TYPE_VIEW_TEXT_CHANGED event,
      // so spelling suggestion can’t extract in TextEventInterpreter.
      if (AccessibilityNodeInfoUtils.getSpellingSuggestions(node).isEmpty()) {
        return;
      }
      // Sends a hint for spelling suggestion.
      pipelineInterpretationReceiver.input(
          interpretation.eventId, new Interpretation.ID(Value.SPELLING_SUGGESTION_HINT));
    }
  }
}
