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

import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
import static com.google.android.accessibility.talkback.Feedback.HINT;
import static com.google.android.accessibility.talkback.compositor.HintEventInterpretation.HINT_TYPE_NONE;
import static com.google.android.accessibility.talkback.compositor.HintEventInterpretation.HINT_TYPE_TEXT_SUGGESTION;
import static com.google.android.accessibility.talkback.compositor.HintEventInterpretation.HINT_TYPE_TYPO;
import static com.google.android.accessibility.utils.AccessibilityEventUtils.UNKNOWN_EVENT_TYPE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_NO_HISTORY;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Mappers;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.compositor.Compositor.TextComposer;
import com.google.android.accessibility.talkback.compositor.EventInterpretation;
import com.google.android.accessibility.talkback.compositor.HintEventInterpretation;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.output.SpeechController;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Maps events to hint-feedback. */
public class ProcessorAccessibilityHints {

  // TODO: Move ProcessorAccessibilityHints to directory feedbackpolicy.

  ///////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String LOG_TAG = "ProcessorAccessibilityHints";

  /** Timeout before reading a hint. */
  public static final long DELAY_HINT = 400; // ms

  ///////////////////////////////////////////////////////////////////////////////////////
  // Inner classes

  /** Data-structure that holds a variety of hint data. */
  private static class HintInfo {
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

    private boolean nodeHintForceFeedbackEvenIfMicrophoneActive = false;

    /** The event type for the hint source node. */
    private int pendingHintEventType;

    /** A hint about spelling suggestion available for text editing. */
    private boolean spellingSuggestionHint = false;

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

    public void setPendingHintSource(@Nullable AccessibilityNodeInfoCompat hintSource) {
      pendingHintSource = hintSource;
    }

    public @Nullable AccessibilityNodeInfoCompat getPendingHintSource() {
      return pendingHintSource;
    }

    /** Sets a hint about screen. */
    public void setPendingScreenHint(@Nullable CharSequence screenHint) {
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
      nodeHintForceFeedbackEvenIfMicrophoneActive = false;

      // Clears a hint about screen.
      pendingScreenHint = null;

      // Clears a hint about selector (quick menu).
      pendingSelectorHint = null;
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  // Not instantiable
  private ProcessorAccessibilityHints() {}

  ///////////////////////////////////////////////////////////////////////////////////////
  // Methods

  private static @Nullable EventInterpretation getEventInterpretation(@NonNull HintInfo hintInfo) {
    @HintEventInterpretation.HintType int hintEventType;
    @Nullable EventInterpretation eventInterp = null;
    @Nullable HintEventInterpretation hintInterp = null;
    if (hintInfo.getPendingHintSource() != null) {
      switch (hintInfo.getPendingHintEventType()) {
        case TYPE_VIEW_FOCUSED:
          hintEventType = HintEventInterpretation.HINT_TYPE_INPUT_FOCUS;
          break;
        case TYPE_VIEW_TEXT_CHANGED:
          hintEventType = HintEventInterpretation.HINT_TYPE_TYPO;
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
    } else if (hintInfo.spellingSuggestionHint) {
      hintEventType = HintEventInterpretation.HINT_TYPE_TEXT_SUGGESTION;
      hintInterp = new HintEventInterpretation(hintEventType);
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

  private static Feedback.Part.@NonNull Builder hintInfoToFeedback(
      @NonNull HintInfo hintInfo, @NonNull Context context, @NonNull TextComposer compositor) {

    // Interrupt existing hints.
    Feedback.Part.Builder feedback = Feedback.interrupt(HINT, /* level= */ 2);

    EventInterpretation eventInterp = getEventInterpretation(hintInfo);
    if (eventInterp == null) {
      return feedback;
    }

    boolean isFocusHint =
        (hintInfo.getPendingHintSource() != null)
            && ((hintInfo.getPendingHintEventType() == TYPE_VIEW_FOCUSED)
                || (hintInfo.getPendingHintEventType() == TYPE_VIEW_ACCESSIBILITY_FOCUSED));

    @Nullable HintEventInterpretation hintInterp = eventInterp.getHint();
    int hintType = hintInterp.getHintType();
    if (hintType != HINT_TYPE_NONE) {
      String hintTTSOutput =
          compositor.parseTTSText(
              hintInfo.getPendingHintSource(), eventInterp.getEvent(), eventInterp);
      if (TextUtils.isEmpty(hintTTSOutput)) {
        return feedback;
      }

      int hintFlags = FLAG_NO_HISTORY;
      if (hintInterp.getForceFeedbackEvenIfAudioPlaybackActive()) {
        hintFlags |= FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE;
      }

      if (hintInterp.getForceFeedbackEvenIfMicrophoneActive()) {
        hintFlags |= FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE;
      }

      feedback.setSpeech(
          Speech.builder()
              .setAction(Speech.Action.SPEAK)
              .setHintSpeakOptions(SpeechController.SpeakOptions.create().setFlags(hintFlags))
              .setHint(hintTTSOutput)
              .setHintInterruptLevel((isFocusHint ? 2 : 1))
              .build());
      if (hintType == HINT_TYPE_TEXT_SUGGESTION || hintType == HINT_TYPE_TYPO) {
        return feedback.sound(R.raw.typo).vibration(R.array.typo_pattern);
      }
    }
    return feedback;
  }

  public static Feedback.Part.@Nullable Builder toFeedback(
      Mappers.@NonNull Variables variables,
      int depth,
      @NonNull Context context,
      @NonNull TextComposer compositor) {

    LogDepth.logFunc(LOG_TAG, ++depth, "toFeedback()");

    @Nullable AccessibilityNodeInfoCompat source = null;

    final int eventType = variables.eventType(depth);
    switch (eventType) {
      case TYPE_VIEW_FOCUSED: // Schedule delayed hint for input-focus event.
      case TYPE_VIEW_TEXT_CHANGED: // Schedule earcon and delayed hints for spelling suggestions.
        source = variables.source(depth);
        if (source != null) {
          return nodeEventToHint(eventType, source, context, compositor);
        }
        break;
      case TYPE_VIEW_ACCESSIBILITY_FOCUSED: // Schedule delayed hint for accessibility-focus event.
        source = variables.source(depth);
        if (source != null) {
          return nodeEventToHint(
              eventType,
              source,
              variables.forceFeedbackEvenIfAudioPlaybackActive(depth),
              variables.forceFeedbackEvenIfMicrophoneActive(depth),
              context,
              compositor);
        }
        break;
      case TYPE_VIEW_CLICKED:
      case TYPE_TOUCH_INTERACTION_START:
        // Clear hints that were generated before a click or in an old window configuration.
        return cancelNonFocusHints();
    }

    return null;
  }

  @VisibleForTesting
  public static Feedback.Part.@NonNull Builder cancelNonFocusHints() {
    // Conditionally cancels only focus-hints delayed in Pipeline.
    // Use Feedback interrupt-level=2 for focus-hints, level=1 for other hints.
    // Alternatively, could use interrupt-groups=HINT_FOCUS vs group=HINT for other hints.
    // Would require Feedback.And to return multiple interrupt-feedbacks at once.
    // This would be more flexible than using interrupt-levels, but a bigger change.
    return Feedback.interrupt(HINT, /* level= */ 1);
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Methods for executing hints

  /** Returns a hint about screen. The hint will be spoken after the next utterance is completed. */
  public static Feedback.Part.@NonNull Builder screenEventToHint(
      @NonNull CharSequence hint, @NonNull Context context, @NonNull TextComposer compositor) {
    @NonNull HintInfo hintInfo = new HintInfo();
    hintInfo.setPendingScreenHint(hint);
    return hintInfoToFeedback(hintInfo, context, compositor);
  }

  /** Returns a hint about node. The hint will be spoken after the next utterance is completed. */
  private static Feedback.Part.@NonNull Builder nodeEventToHint(
      int eventType,
      AccessibilityNodeInfoCompat node,
      @NonNull Context context,
      @NonNull TextComposer compositor) {
    return nodeEventToHint(
        eventType,
        node,
        /* forceFeedbackEvenIfAudioPlaybackActive= */ false,
        /* forceFeedbackEvenIfMicrophoneActive= */ false,
        context,
        compositor);
  }

  /**
   * Returns a hint about node with customized flag {@link
   * HintInfo#nodeHintForceFeedbackEvenIfMicrophoneActive} and {@link
   * HintInfo#nodeHintForceFeedbackEvenIfAudioPlaybackActive}. The hint will be spoken after the
   * next utterance is completed.
   *
   * @param eventType accessibility event
   * @param node AccessibilityNodeInfoCompat which keeps the hint information
   * @param forceFeedbackEvenIfAudioPlaybackActive force to speak the hint when audio playback
   *     actives
   * @param forceFeedbackEvenIfMicrophoneActive force to speak the hint when micro phone actives
   */
  private static Feedback.Part.@NonNull Builder nodeEventToHint(
      int eventType,
      AccessibilityNodeInfoCompat node,
      boolean forceFeedbackEvenIfAudioPlaybackActive,
      boolean forceFeedbackEvenIfMicrophoneActive,
      @NonNull Context context,
      @NonNull TextComposer compositor) {

    // Store info about event that caused pending hint.
    @NonNull HintInfo hintInfo = new HintInfo();
    hintInfo.setPendingHintSource(node);
    // The hint for a node is usually posted when the node is getting accessibility focus, thus
    // the default value for the hint event type should be TYPE_VIEW_ACCESSIBILITY_FOCUSED.
    eventType = (eventType == UNKNOWN_EVENT_TYPE) ? TYPE_VIEW_ACCESSIBILITY_FOCUSED : eventType;
    hintInfo.setPendingHintEventType(eventType);
    hintInfo.setNodeHintForceFeedbackEvenIfAudioPlaybackActive(
        forceFeedbackEvenIfAudioPlaybackActive);
    hintInfo.setNodeHintForceFeedbackEvenIfMicrophoneActive(forceFeedbackEvenIfMicrophoneActive);

    return hintInfoToFeedback(hintInfo, context, compositor);
  }

  /**
   * Returns a hint about selector (quick menu). The hint will be spoken after the next utterance is
   * completed.
   */
  public static Feedback.Part.@NonNull Builder selectorEventToHint(
      CharSequence hint, @NonNull Context context, @NonNull TextComposer compositor) {
    @NonNull HintInfo hintInfo = new HintInfo();
    hintInfo.setPendingSelectorHint(hint);
    return hintInfoToFeedback(hintInfo, context, compositor);
  }

  /**
   * Returns a hint about spelling suggesting. The hint will be spoken after the next utterance is
   * completed.
   */
  public static Feedback.Part.@NonNull Builder suggestionSpanToHint(
      @NonNull Context context, @NonNull TextComposer compositor) {
    @NonNull HintInfo hintInfo = new HintInfo();
    hintInfo.spellingSuggestionHint = true;
    return hintInfoToFeedback(hintInfo, context, compositor);
  }
}
