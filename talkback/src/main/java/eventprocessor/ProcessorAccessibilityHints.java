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
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventInterpretation;
import com.google.android.accessibility.compositor.HintEventInterpretation;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.feedback.AccessibilityHintsManager;
import com.google.android.accessibility.utils.feedback.HintEventListener;
import com.google.android.accessibility.utils.output.SpeechController;

/**
 * Implements {@link AccessibilityEventListener} and manages accessibility-focus events. When an
 * accessibility-focus event happened and hints are enabled, schedules hints for the event.
 */
public class ProcessorAccessibilityHints extends AccessibilityHintsManager
    implements AccessibilityEventListener, ServiceKeyEventListener, HintEventListener {
  /** Event types that are handled by ProcessorAccessibilityHints. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | TYPE_VIEW_FOCUSED;

  private Compositor compositor;
  private AccessibilityFocusManager accessibilityFocusManager;

  public ProcessorAccessibilityHints(
      SpeechController speechController,
      Compositor compositor,
      AccessibilityFocusManager accessibilityFocusManager) {
    super(speechController);
    this.compositor = compositor;
    this.accessibilityFocusManager = accessibilityFocusManager;
    setHintEventListener(this);
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
      AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
      AccessibilityNodeInfoCompat source = record.getSource();
      if (source != null) {
        postHintForNode(event, source); // postHintForNode() will recycle source node.
        return;
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
          accessibilityFocusManager.getFocusActionInfoFromEvent(event);

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
        setNodeHintForcedFeedbackAudioPlaybackActive(
            (focusActionInfo != null) && focusActionInfo.isForcedFeedbackAudioPlaybackActive());
        setNodeHintForcedFeedbackMicrophoneActive(
            (focusActionInfo != null) && focusActionInfo.isForcedFeedbackMicrophoneActive());

        AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());
        if (source != null) {
          postHintForNode(event, source);
        }
    }
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      // Mainly to prevent hints from being activated when typing, attempting to perform
      // shortcuts, etc. Doesn't cancel in-progress hint, user can use normal actions to
      // cancel (e.g. Ctrl).
      cancelA11yHint();
    }
    return false;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }

  @Override
  public void onFocusHint(
      int eventType,
      AccessibilityNodeInfoCompat compat,
      boolean hintForcedFeedbackAudioPlaybackActive,
      boolean hintForcedFeedbackMicrophoneActive) {
    @HintEventInterpretation.HintType
    int hintEventType =
        (eventType == TYPE_VIEW_FOCUSED)
            ? HintEventInterpretation.HINT_TYPE_INPUT_FOCUS
            : HintEventInterpretation.HINT_TYPE_ACCESSIBILITY_FOCUS;
    HintEventInterpretation hintInterp = new HintEventInterpretation(hintEventType);
    hintInterp.setForceFeedbackAudioPlaybackActive(hintForcedFeedbackAudioPlaybackActive);
    hintInterp.setForceFeedbackMicropphoneActive(hintForcedFeedbackMicrophoneActive);
    EventInterpretation eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
    eventInterp.setHint(hintInterp);

    // Send event to compositor to speak feedback.
    EventId eventId = EVENT_ID_UNTRACKED; // Hints occur after other feedback.
    compositor.handleEvent(compat, eventId, eventInterp);
  }

  @Override
  public void onScreenHint(CharSequence text) {
    HintEventInterpretation hintInterp =
        new HintEventInterpretation(HintEventInterpretation.HINT_TYPE_SCREEN);
    hintInterp.setText(text);
    EventInterpretation eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
    eventInterp.setHint(hintInterp);
    compositor.handleEvent(EVENT_ID_UNTRACKED, eventInterp);
  }
}
