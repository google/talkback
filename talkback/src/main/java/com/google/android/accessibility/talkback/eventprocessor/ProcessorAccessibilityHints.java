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

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
import static com.google.android.accessibility.talkback.Feedback.HINT;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_SPEAK_HINT;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.text.Spannable;
import android.text.style.SuggestionSpan;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.compositor.EventInterpretation;
import com.google.android.accessibility.talkback.compositor.HintEventInterpretation;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implements {@link AccessibilityEventListener} and manages accessibility-focus events. When an
 * accessibility-focus event happened and hints are enabled, schedules hints for the event.
 */
public class ProcessorAccessibilityHints
    implements AccessibilityEventListener, ServiceKeyEventListener {

  ///////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Timeout before reading a hint. */
  public static final long DELAY_HINT = 400; // ms

  /** Event types that are handled by ProcessorAccessibilityHints. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | TYPE_VIEW_FOCUSED
          | TYPE_VIEW_TEXT_CHANGED;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Inner classes

  /** Data-structure that holds a variety of hint data. */
  protected static class HintInfo {
    /** The source node whose hint will be read by the utterance complete action. */
    private @Nullable AccessibilityNodeInfoCompat pendingHintSource;
    /**
     * Whether the current hint is a forced feedback. Set to {@code true} if the hint corresponds to
     * accessibility focus that was not genenerated from unknown source for audioplayback and
     * microphone active. Set to false if ssb is active.
     *
     * @see FeedbackItem#FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
     * @see FeedbackItem#FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
     * @see FeedbackItem#FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE
     */
    private boolean nodeHintForceFeedbackEvenIfAudioPlaybackActive = true;

    private boolean nodeHintForceFeedbackEvenIfMicrophoneActive = true;

    /** The event type for the hint source node. */
    private int pendingHintEventType;

    /** A hint about screen whose hint will be read by the utterance complete action. */
    private @Nullable CharSequence pendingScreenHint;

    /**
     * A hint about selector (quick menu) whose hint will be read by the utterance complete action.
     */
    private @Nullable CharSequence pendingSelectorHint;

    public HintInfo() {}

    /**
     * Sets whether the hint for the hint source node is a forced feedback when audio playback is
     * active.
     */
    public void setNodeHintForceFeedbackEvenIfAudioPlaybackActive(
        boolean nodeHintForceFeedbackAudioPlaybackActive) {
      this.nodeHintForceFeedbackEvenIfAudioPlaybackActive =
          nodeHintForceFeedbackAudioPlaybackActive;
    }

    public boolean getNodeHintForceFeedbackEvenIfAudioPlaybackActive() {
      return nodeHintForceFeedbackEvenIfAudioPlaybackActive;
    }

    /**
     * Sets whether the hint for the hint source node is a forced feedback when microphone is
     * active.
     */
    public void setNodeHintForceFeedbackEvenIfMicrophoneActive(
        boolean nodeHintForcedFeedbackMicrophoneActive) {
      this.nodeHintForceFeedbackEvenIfMicrophoneActive = nodeHintForcedFeedbackMicrophoneActive;
    }

    public boolean getNodeHintForceFeedbackEvenIfMicrophoneActive() {
      return nodeHintForceFeedbackEvenIfMicrophoneActive;
    }

    /**
     * Sets accessibility event type. The default value for the hint event type should be
     * TYPE_VIEW_ACCESSIBILITY_FOCUSED
     */
    public void setPendingHintEventType(int hintEventType) {
      pendingHintEventType = hintEventType;
    }

    public int getPendingHintEventType() {
      return pendingHintEventType;
    }

    /** Sets hint source node. Caller keeps ownership of hintSource. */
    public void setPendingHintSource(AccessibilityNodeInfoCompat hintSource) {
      pendingHintSource = hintSource;
    }

    public @Nullable AccessibilityNodeInfoCompat getPendingHintSource() {
      return pendingHintSource;
    }

    /** Sets a hint about screen. */
    public void setPendingScreenHint(CharSequence screenHint) {
      pendingScreenHint = screenHint;
    }

    public @Nullable CharSequence getPendingScreenHint() {
      return pendingScreenHint;
    }

    /** Sets a hint about selector (quick menu). */
    public void setPendingSelectorHint(@Nullable CharSequence selectorHint) {
      pendingSelectorHint = selectorHint;
    }

    public @Nullable CharSequence getPendingSelectorHint() {
      return pendingSelectorHint;
    }

    /** Clears hint data */
    public void clear() {
      // Clears hint source node and related.
      pendingHintSource = null;
      nodeHintForceFeedbackEvenIfAudioPlaybackActive = true;
      nodeHintForceFeedbackEvenIfMicrophoneActive = true;

      // Clears a hint about screen.
      pendingScreenHint = null;

      // Clears a hint about selector (quick menu).
      pendingSelectorHint = null;
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Member data

  protected final HintInfo hintInfo = new HintInfo();

  private ActorState actorState;
  private Pipeline.InterpretationReceiver pipelineInterpretationReceiver;
  private Pipeline.FeedbackReturner pipeline;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

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

  ///////////////////////////////////////////////////////////////////////////////////////
  // Methods

  private @Nullable EventInterpretation getEventInterpretation() {
    @HintEventInterpretation.HintType int hintEventType;
    @Nullable EventInterpretation eventInterp = null;
    @Nullable HintEventInterpretation hintInterp = null;
    if (hintInfo.getPendingHintSource() != null) {
      switch (hintInfo.getPendingHintEventType()) {
        case TYPE_VIEW_FOCUSED:
          hintEventType = HintEventInterpretation.HINT_TYPE_INPUT_FOCUS;
          break;
        case TYPE_VIEW_TEXT_CHANGED:
          hintEventType = HintEventInterpretation.HINT_TYPE_TEXT_SUGGESTION;
          break;
        default:
          hintEventType = HintEventInterpretation.HINT_TYPE_ACCESSIBILITY_FOCUS;
          break;
      }
      hintInterp = new HintEventInterpretation(hintEventType);
      eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
    } else if (hintInfo.getPendingScreenHint() != null) {
      hintInterp = new HintEventInterpretation(HintEventInterpretation.HINT_TYPE_SCREEN);
      hintInterp.setText(hintInfo.getPendingScreenHint());
      eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
    } else if (hintInfo.getPendingSelectorHint() != null) {
      hintInterp = new HintEventInterpretation(HintEventInterpretation.HINT_TYPE_SELECTOR);
      hintInterp.setText(hintInfo.getPendingSelectorHint());
      eventInterp = new EventInterpretation(Compositor.EVENT_SPEAK_HINT);
    }

    if (hintInterp == null || eventInterp == null) {
      return null;
    }

    hintInterp.setForceFeedbackEvenIfAudioPlaybackActive(
        hintInfo.getNodeHintForceFeedbackEvenIfAudioPlaybackActive());
    hintInterp.setForceFeedbackEvenIfMicrophoneActive(
        hintInfo.getNodeHintForceFeedbackEvenIfMicrophoneActive());
    eventInterp.setHint(hintInterp);
    return eventInterp;
  }

  /** Starts the hint timeout. */
  protected void startHintDelay() {
    EventInterpretation eventInterp = getEventInterpretation();
    if (eventInterp == null) {
      return;
    }

    // TODO: This code should be a feedback-mapper, that directly sends this
    // compositor-event to compositor.
    pipelineInterpretationReceiver.input(
        EVENT_ID_UNTRACKED,
        /* event= */ null,
        new Interpretation.CompositorID(
            EVENT_SPEAK_HINT, eventInterp, hintInfo.getPendingHintSource()));
    hintInfo.clear();
  }

  /** Cancels the pending accessibility hint */
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
      if (source != null) {
        postHintForNode(event, source); // postHintForNode() doesn't take ownership of source.
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
      boolean forceFeedbackEvenIfAudioPlaybackActive =
          (focusActionInfo != null) && focusActionInfo.forceFeedbackEvenIfAudioPlaybackActive();
      boolean forceFeedbackEvenIfMicrophoneActive =
          (focusActionInfo != null) && focusActionInfo.forceFeedbackEvenIfMicrophoneActive();

      if (source != null) {
        // postHintForNode() doesn't take ownership of source.
        postHintForNode(
            event,
            source,
            forceFeedbackEvenIfAudioPlaybackActive,
            forceFeedbackEvenIfMicrophoneActive);
      }
    }

    // Schedule earcon and delayed hints for spelling suggestions.
    // TODO Pipeline ProcessorAccessibilityHints and move this part to
    //  TextEventInterpreter after TextEventInterpreter is pipelined.
    if (eventType == TYPE_VIEW_TEXT_CHANGED) {
      final List<CharSequence> afterTexts = event.getText();
      final CharSequence afterText =
          (afterTexts == null || afterTexts.isEmpty()) ? null : afterTexts.get(0);
      if (!(afterText instanceof Spannable)) {
        return;
      }
      int fromIndex = event.getFromIndex();
      int toIndex = event.getToIndex();
      HashSet<Integer> matchedAfterSpanFlags =
          getExactMatchSuggestionSpanFlags((Spannable) afterText, fromIndex, toIndex);
      if (matchedAfterSpanFlags.size() == 0) {
        return;
      }

      boolean spanAdded = true;
      final CharSequence beforeText = event.getBeforeText();
      if (beforeText instanceof Spannable) {
        HashSet<Integer> matchedBeforeSpanFlags =
            getExactMatchSuggestionSpanFlags((Spannable) beforeText, fromIndex, toIndex);
        // The flags will determine the visual underline. For SuggestionSpans with the same from
        // and to index, we may visually notice it only when a new span with a new color is added.
        spanAdded = !matchedBeforeSpanFlags.containsAll(matchedAfterSpanFlags);
      }
      if (!spanAdded) {
        return;
      }

      AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      if (source != null) {
        postHintForNode(event, source); // postHintForNode() doesn't take ownership of source.
      }
    }
  }

  /**
   * Should be called when the window state changes. This method will cancel the pending hint if
   * deemed appropriate based on the window event.
   */
  public void onScreenStateChanged() {
    cancelA11yHintBasedOnEventType();
  }

  /**
   * Cancels the pending accessibility hint if the hint source is null or if the event that
   * triggered the hint was not a view getting focused or accessibility focused.
   *
   * @return {@code true} if the pending accessibility hint was canceled, {@code false} otherwise.
   */
  @VisibleForTesting
  public boolean cancelA11yHintBasedOnEventType() {
    if (hintInfo.getPendingHintSource() == null
        || (hintInfo.getPendingHintEventType() != TYPE_VIEW_FOCUSED
            && hintInfo.getPendingHintEventType() != TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
      cancelHintDelay();
      hintInfo.clear();
      return true;
    }
    return false;
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

  private HashSet<Integer> getExactMatchSuggestionSpanFlags(
      Spannable spannable, int fromIndex, int toIndex) {
    SuggestionSpan[] spans = spannable.getSpans(fromIndex, toIndex, SuggestionSpan.class);
    HashSet<Integer> matchedSpanFlags = new HashSet<>();
    for (SuggestionSpan span : spans) {
      if ((spannable.getSpanStart(span) == fromIndex) && (spannable.getSpanEnd(span) == toIndex)) {
        matchedSpanFlags.add(span.getFlags());
      }
    }
    return matchedSpanFlags;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Methods for executing hints

  /** Posts a hint about screen. The hint will be spoken after the next utterance is completed. */
  public void postHintForScreen(CharSequence hint) {
    cancelHintDelay();
    hintInfo.clear();

    hintInfo.setPendingScreenHint(hint);

    startHintDelay();
  }

  /**
   * Posts a hint about node. The hint will be spoken after the next utterance is completed. Caller
   * keeps ownership of node.
   */
  public void postHintForNode(AccessibilityEvent event, AccessibilityNodeInfoCompat node) {
    postHintForNode(
        event,
        node,
        /* forceFeedbackEvenIfAudioPlaybackActive= */ false,
        /* forceFeedbackEvenIfMicrophoneActive= */ false);
  }

  /**
   * Posts a hint about node with customized flag {@link
   * HintInfo#nodeHintForceFeedbackEvenIfMicrophoneActive} and {@link
   * HintInfo#nodeHintForceFeedbackEvenIfAudioPlaybackActive}. The hint will be spoken after the
   * next utterance is completed. Caller keeps ownership of node.
   *
   * @param event accessibility event
   * @param node AccessibilityNodeInfoCompat which keeps the hint information
   * @param forceFeedbackEvenIfAudioPlaybackActive force to speak the hint when audio playback
   *     actives
   * @param forceFeedbackEvenIfMicrophoneActive force to speak the hint when micro phone actives
   */
  public void postHintForNode(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      boolean forceFeedbackEvenIfAudioPlaybackActive,
      boolean forceFeedbackEvenIfMicrophoneActive) {
    cancelHintDelay();
    hintInfo.clear();

    // Store info about event that caused pending hint.
    hintInfo.setPendingHintSource(node);
    // The hint for a node is usually posted when the node is getting accessibility focus, thus
    // the default value for the hint event type should be TYPE_VIEW_ACCESSIBILITY_FOCUSED.
    int eventType =
        (event != null)
            ? event.getEventType()
            : AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
    hintInfo.setPendingHintEventType(eventType);
    hintInfo.setNodeHintForceFeedbackEvenIfAudioPlaybackActive(
        forceFeedbackEvenIfAudioPlaybackActive);
    hintInfo.setNodeHintForceFeedbackEvenIfMicrophoneActive(forceFeedbackEvenIfMicrophoneActive);

    startHintDelay();
  }

  /**
   * Posts a hint about selector (quick menu). The hint will be spoken after the next utterance is
   * completed.
   */
  public void postHintForSelector(CharSequence hint) {
    cancelHintDelay();
    hintInfo.clear();
    hintInfo.setPendingSelectorHint(hint);
    startHintDelay();
  }
}
