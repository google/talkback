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

package com.google.android.accessibility.talkback.eventprocessor;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static com.google.android.accessibility.compositor.Compositor.EVENT_SPEAK_HINT;
import static com.google.android.accessibility.talkback.Feedback.HINT;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventInterpretation;
import com.google.android.accessibility.compositor.HintEventInterpretation;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.feedback.AbstractAccessibilityHintsManager;

/**
 * Implements {@link AccessibilityEventListener} and manages accessibility-focus events. When an
 * accessibility-focus event happened and hints are enabled, schedules hints for the event.
 * ProcessorScreen is for TalkBack only to use Pipeline to speak hint.
 */
public class ProcessorAccessibilityHints extends AbstractAccessibilityHintsManager
    implements AccessibilityEventListener, ServiceKeyEventListener {
  /** Event types that are handled by ProcessorAccessibilityHints. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | TYPE_VIEW_FOCUSED;

  private ActorState actorState;
  private Pipeline.InterpretationReceiver pipelineInterpretationReceiver;
  private Pipeline.FeedbackReturner pipeline;

  public ProcessorAccessibilityHints() {}

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setPipelineInterpretationReceiver(Pipeline.InterpretationReceiver pipeline) {
    this.pipelineInterpretationReceiver = pipeline;
  }

  private EventInterpretation getEventInterpretation() {
    @HintEventInterpretation.HintType int hintEventType;
    EventInterpretation eventInterp = null;
    if (hintInfo.getPendingHintSource() != null) {
      hintEventType =
          (hintInfo.getPendingHintEventType() == TYPE_VIEW_FOCUSED)
              ? HintEventInterpretation.HINT_TYPE_INPUT_FOCUS
              : HintEventInterpretation.HINT_TYPE_ACCESSIBILITY_FOCUS;
      HintEventInterpretation hintInterp = new HintEventInterpretation(hintEventType);
      eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
      eventInterp.setHint(hintInterp);

    } else if (hintInfo.getPendingScreenHint() != null) {
      HintEventInterpretation hintInterp =
          new HintEventInterpretation(HintEventInterpretation.HINT_TYPE_SCREEN);
      hintInterp.setText(hintInfo.getPendingScreenHint());
      eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
      eventInterp.setHint(hintInterp);
    } else if (hintInfo.getPendingSelectorHint() != null) {
      HintEventInterpretation hintEvent =
          new HintEventInterpretation(HintEventInterpretation.HINT_TYPE_SELECTOR);
      hintEvent.setText(hintInfo.getPendingSelectorHint());
      eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
      eventInterp.setHint(hintEvent);
    }
    return eventInterp;
  }

  /** Starts the hint timeout. */
  @Override
  protected void startHintDelay() {
    EventInterpretation eventInterp = getEventInterpretation();
    if (eventInterp == null) {
      return;
    }

    pipelineInterpretationReceiver.input(
        EVENT_ID_UNTRACKED,
        /* event= */ null,
        new Interpretation.CompositorID(
            EVENT_SPEAK_HINT, eventInterp, hintInfo.getPendingHintSource()));
    hintInfo.clear();
  }

  /** Cancels the pending accessibility hint */
  @Override
  protected void cancelHintDelay() {
    pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.interrupt(HINT, /* level= */ 1));
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    // Schedule delayed hint for input-focus event.
    final int eventType = event.getEventType();
    if (eventType == TYPE_VIEW_FOCUSED) {
      AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      try {
        if (source != null) {
          postHintForNode(event, source); // postHintForNode() doesn't take ownership of source.
          return;
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(source);
      }
    }

    // Clear hints that were generated before a click or in an old window configuration.
    if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        || eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
      if (cancelA11yHintBasedOnEventType()) {
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

      hintInfo.setNodeHintForcedFeedbackAudioPlaybackActive(
          (focusActionInfo != null) && focusActionInfo.isForcedFeedbackAudioPlaybackActive());
      hintInfo.setNodeHintForcedFeedbackMicrophoneActive(
          (focusActionInfo != null) && focusActionInfo.isForcedFeedbackMicrophoneActive());

        AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());

      try {
        if (source != null) {
          postHintForNode(event, source); // postHintForNode() doesn't take ownership of source.
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(source);
      }
    }
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      // Mainly to prevent hints from being activated when typing, attempting to perform
      // shortcuts, etc. Doesn't cancel in-progress hint, user can use normal actions to
      // cancel (e.g. Ctrl).
      cancelHintDelay();
      hintInfo.clear();
    }
    return false;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }
}
