/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.accessibility.utils.feedbackpolicy;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;

import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.output.FeedbackItem;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages accessibility hints. When a node is accessibility-focused or a hint about screen, the
 * hint will be queued after a short delay and this delay must be implemented at inherited class.
 */
public abstract class AbstractAccessibilityHintsManager {

  /** Timeout before reading a hint. */
  public static final long DELAY_HINT = 400; // ms

  protected final HintInfo hintInfo;

  public AbstractAccessibilityHintsManager() {
    hintInfo = new HintInfo();
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Abstract function. Inherited class has to implement these functions to process speaking hints.

  /** Starts the hint timeout. */
  protected abstract void startHintDelay();

  /** Removes the hint timeout and completion action. Call this for every event. */
  protected abstract void cancelHintDelay();

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Virtual function. Inherited class may or may not override these functions.

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

  /**
   * Should be called when the window state changes. This method will cancel the pending hint if
   * deemed appropriate based on the window event.
   */
  public void onScreenStateChanged() {
    cancelA11yHintBasedOnEventType();
  }

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

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Inner class for hint data

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
}
