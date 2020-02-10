/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.switchaccess.feedback;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache.SwitchAccessPreferenceChangedListener;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.UiChangeStabilizer;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSystemProvidedNode;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.feedback.AccessibilityHintsManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.libraries.accessibility.utils.eventfilter.AccessibilityEventFilter;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The central class used to provide feedback in Switch Access. This class is the point of contact
 * for all feedback requests, and it forwards the feedback requests to individual feedback
 * controllers based on the type of the request.
 */
public class SwitchAccessFeedbackController
    implements SpeechController.Observer,
        AccessibilityEventFilter.AccessibilityEventProcessor,
        SwitchAccessPreferenceChangedListener,
        UiChangeStabilizer.WindowChangedListener {

  private final Runnable notifyFeedbackCompleteRunnable =
      () -> {
        // Stop feedback before calling onSpeechComplete, so that onSpeechCompleted is not called
        // again after the current speech finishes. Note: pending hints should not be canceled here.
        // If the description for the currently highlighted item takes longer than the "maximum time
        // per item" to complete, the speech will be stopped here. If we cancel hints, the usage
        // hints for the current item will never be spoken. Auto-scan will be stopped at the current
        // item because we are still waiting for the hints to be spoken.
        stopAllFeedback(
            false /* stopTtsCompletely */,
            true /* interruptItemsThatCanIgnoreInterrupts */,
            false /* cancelHints */);
        // If the current speech is the content description of an actionable item, and the "Speak
        // usage hints" option is enabled, then isLastSpeech is false. Calling
        // onSpeechCompleted() will not cause the highlight to move to the next item.
        onSpeechCompleted();
      };

  private final Context context;
  private final Compositor compositor;
  private final GlobalVariables globalVariables;
  private final SpeechControllerImpl speechController;
  private final FeedbackController feedbackController;
  private final AccessibilityHintsManager hintsManager;
  private final SwitchAccessHighlightFeedbackController highlightFeedbackController;
  private final SwitchAccessActionFeedbackController actionFeedbackController;
  private final SwitchAccessAccessibilityEventFeedbackController
      accessibilityEventFeedbackController;
  private final Handler handler;

  private boolean isSpokenFeedbackEnabled;

  public SwitchAccessFeedbackController(
      AccessibilityService service,
      Compositor compositor,
      SpeechControllerImpl speechController,
      FeedbackController feedbackController,
      GlobalVariables globalVariables,
      AccessibilityHintsManager hintsManager,
      SwitchAccessHighlightFeedbackController highlightFeedbackController,
      SwitchAccessActionFeedbackController actionFeedbackController,
      SwitchAccessAccessibilityEventFeedbackController accessibilityEventFeedbackController,
      Handler handler) {
    context = service;
    this.compositor = compositor;
    this.globalVariables = globalVariables;
    this.speechController = speechController;
    this.feedbackController = feedbackController;
    this.handler = handler;
    this.hintsManager = hintsManager;
    this.highlightFeedbackController = highlightFeedbackController;
    this.actionFeedbackController = actionFeedbackController;
    this.accessibilityEventFeedbackController = accessibilityEventFeedbackController;

    this.hintsManager.setHintEventListener(this.highlightFeedbackController);
    this.speechController.addObserver(this);

    SwitchAccessPreferenceUtils.registerSwitchAccessPreferenceChangedListener(service, this);
  }

  /**
   * Set a listener to be notified when speech ends, regardless of the reason (error, completion, or
   * interruption). The listener is guaranteed to be called at least once after each call to {@link
   * SwitchAccessFeedbackController#speakFeedback}. Note: Only the last listener to be set will be
   * notified.
   */
  public void setOnUtteranceCompleteListener(OnUtteranceCompleteListener listener) {
    highlightFeedbackController.setOnUtteranceCompleteListener(listener);
  }

  /**
   * Provides the spoken feedback for the nodes rooted at the given {@link TreeScanNode}.
   *
   * @param root The root of all nodes that correspond to the currently highlighted items
   * @param isFocusingFirstNodeInTree Whether the given {@link TreeScanNode} is the first node to be
   *     highlighted; if true, this means that we have also just drawn the Menu button
   * @param isSwitchAccessMenuVisible Whether the currently highlighted items are on the Switch
   *     Access menu; if true, this means that we didn't draw the Menu button
   */
  public void speakFeedback(
      TreeScanNode root, boolean isFocusingFirstNodeInTree, boolean isSwitchAccessMenuVisible) {
    if (!isSpokenFeedbackEnabled) {
      return;
    }

    highlightFeedbackController.speakFeedback(
        root, isFocusingFirstNodeInTree, isSwitchAccessMenuVisible);
  }

  /**
   * Stops Switch Access speech and feedback. If this method is called with the parameter
   * interruptItemsThatCanIgnoreInterrupts set to false, items that are in the
   * QUEUE_MODE_CAN_IGNORE_INTERRUPTS mode will not be interrupted or removed from the speech queue.
   * The interrupt will never be ignored if the parameter interruptItemsThatCanIgnoreInterrupts is
   * true.
   *
   * @param stopTtsCompletely Whether to also stop speech from other apps
   * @param interruptItemsThatCanIgnoreInterrupts Whether to interrupt and remove FeedbackItems that
   *     are in the QUEUE_MODE_CAN_IGNORE_INTERRUPTS mode when this method is called
   * @param cancelHints Whether to cancel the pending accessibility hints
   */
  public void stopAllFeedback(
      boolean stopTtsCompletely,
      boolean interruptItemsThatCanIgnoreInterrupts,
      boolean cancelHints) {
    handler.removeCallbacks(notifyFeedbackCompleteRunnable);

    if (speechController != null) {
      speechController.interrupt(
          stopTtsCompletely,
          false /* notifyObserver */,
          interruptItemsThatCanIgnoreInterrupts /* interruptItemsThatCanIgnoreInterrupts */);
    }

    if (cancelHints && hintsManager != null) {
      // Cancel pending accessibility hints.
      hintsManager.onScreenStateChanged();
    }

    if (feedbackController != null) {
      feedbackController.interrupt();
    }
  }

  /** Called after focus is cleared. */
  public void onFocusCleared() {
    highlightFeedbackController.onFocusCleared();
  }

  /**
   * Speaks the text of a key typed on the on-screen keyboard. This method should be called whenever
   * a key from the on-screen keyboard is typed.
   *
   * @param node The {@link TreeScanSystemProvidedNode} that corresponds to the key that was typed
   *     from the on-screen keyboard
   */
  public void onKeyTyped(TreeScanSystemProvidedNode node) {
    if (SwitchAccessPreferenceUtils.shouldSpeakTypedKey(context)) {
      actionFeedbackController.onKeyTyped(node);
    }
  }

  /**
   * Called whenever we are about to rebuild the tree while the user is in the middle of scanning.
   * If this method is called with spoken feedback enabled, spoken feedback indicates that the
   * screen has changed.
   */
  public void onTreeRebuiltDuringScanning() {
    if (isSpokenFeedbackEnabled) {
      // Provide spoken feedback to indicate that the screen has changed if the tree was rebuilt
      // while the user was in the middle of scanning.
      actionFeedbackController.onTreeRebuiltDuringScanning();
    }
  }

  /**
   * Speaks a message to acknowledge the selection of a group. This method should be called whenever
   * a group is selected.
   *
   * @param optionIndex The index of the selected group
   */
  public void onGroupSelected(int optionIndex) {
    if (SwitchAccessPreferenceUtils.shouldSpeakSelectedItemOrGroup(context)) {
      actionFeedbackController.onGroupSelected(optionIndex);
    }
  }

  /**
   * Speaks a message to acknowledge the selection of an actionable item or a row. This method
   * should be called whenever the user selects an actionable item or row.
   *
   * @param currentNode The {@link TreeScanNode} which corresponds to the actionable item or the row
   *     that was selected
   */
  public void onNodeSelected(TreeScanNode currentNode) {
    if (!SwitchAccessPreferenceUtils.shouldSpeakSelectedItemOrGroup(context)) {
      return;
    }

    actionFeedbackController.onNodeSelected(currentNode);
  }

  // {@link SpeechController.Observer} overrides

  @Override
  public void onSpeechStarting() {
    handler.removeCallbacks(notifyFeedbackCompleteRunnable);
    if (SwitchAccessPreferenceUtils.isAutoScanEnabled(context)) {
      handler.postDelayed(
          notifyFeedbackCompleteRunnable,
          SwitchAccessPreferenceUtils.getMaximumTimePerItem(context));
    }
  }

  @Override
  public void onSpeechCompleted() {
    handler.removeCallbacks(notifyFeedbackCompleteRunnable);
    if (highlightFeedbackController != null) {
      highlightFeedbackController.onSpeechCompleted();
    }
  }

  @Override
  public void onSpeechPaused() {
    // Do nothings since its a callback method for pause API. Pause API is not used in our feature.
  }

  /** Notify that preferences have changed. */
  @Override
  public void onPreferenceChanged(SharedPreferences sharedPreferences, String preferenceKey) {
    boolean isSpokenFeedbackEnabled = SwitchAccessPreferenceUtils.isSpokenFeedbackEnabled(context);
    if (isSpokenFeedbackEnabled != this.isSpokenFeedbackEnabled) {
      if (isSpokenFeedbackEnabled) {
        // Announce that spoken feedback is turned on.
        compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_ON, EVENT_ID_UNTRACKED);
      } else {
        // Announce that spoken feedback is turned off.
        compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_DISABLED, EVENT_ID_UNTRACKED);
      }
      this.isSpokenFeedbackEnabled = isSpokenFeedbackEnabled;
    }
    globalVariables.setUsageHintEnabled(SwitchAccessPreferenceUtils.shouldSpeakHints(context));

    globalVariables.setUseAutoSelect(SwitchAccessPreferenceUtils.isAutoselectEnabled(context));

    // Update feedback preferences.
    feedbackController.setVolumeAdjustment(
        SwitchAccessPreferenceUtils.getSoundVolumePercentage(context) / 100.0f);
    feedbackController.setHapticEnabled(
        SwitchAccessPreferenceUtils.shouldPlayVibrationFeedback(context));
    feedbackController.setAuditoryEnabled(
        SwitchAccessPreferenceUtils.shouldPlaySoundFeedback(context));

    // Update speech preferences.
    speechController.setUseIntonation(SwitchAccessPreferenceUtils.shouldChangePitchForIme(context));
    speechController.setOverlayEnabled(SwitchAccessPreferenceUtils.isSpeechOutputVisible(context));

    boolean useAudioFocus = SwitchAccessPreferenceUtils.shouldDuckAudio(context);
    speechController.setUseAudioFocus(useAudioFocus);
    globalVariables.setUseAudioFocus(useAudioFocus);

    // Update compositor preferences.
    // Update preference: speak list and grid information.
    compositor.setSpeakCollectionInfo(
        SwitchAccessPreferenceUtils.shouldSpeakElementPosition(context));
    // Update preference: speak element type.
    compositor.setSpeakRoles(SwitchAccessPreferenceUtils.shouldSpeakElementType(context));
    // Update preference: element description order.
    compositor.setDescriptionOrder(SwitchAccessPreferenceUtils.getElementDescriptionOrder(context));
    // Update preference: speak element IDs.
    compositor.setSpeakElementIds(SwitchAccessPreferenceUtils.shouldSpeakElementIds(context));
    // Reload compositor configuration.
    compositor.refreshParseTreeIfNeeded();
  }

  // {@link SwitchAccessAccessibilityEventDetector.AccessibilityEventProcessor} overrides

  @Override
  public void processAccessibilityEvent(AccessibilityEvent event) {
    if (isSpokenFeedbackEnabled) {
      accessibilityEventFeedbackController.processAccessibilityEvent(event);
    }
  }

  // {@link UiChangeStabilizer.OnWindowChangedListener} overrides

  @Override
  public void onWindowChangedAndIsNowStable(AccessibilityEvent event, @Nullable EventId eventId) {
    if (isSpokenFeedbackEnabled) {
      accessibilityEventFeedbackController.onWindowChangedAndIsNowStable(event, eventId);
    }
  }

  @Override
  public void onWindowChangeStarted() {
    if (!isSpokenFeedbackEnabled) {
      return;
    }

    stopAllFeedback(
        false /* stopTtsCompletely */,
        false /* interruptItemsThatCanIgnoreInterrupts */,
        true /* cancelHints */);

    highlightFeedbackController.speakPendingFeedbackAfterWindowChangeStarted();
  }

  @Override
  public void onSwitchAccessMenuShown(MenuType menuType) {
    if (!isSpokenFeedbackEnabled) {
      return;
    }

    // Notify AccessibilityHintsManager that the screen changed. This will cancel pending
    // hints that correspond to AccessibilityEvent types other than TYPE_VIEW_FOCUSED.
    if (hintsManager != null) {
      hintsManager.onScreenStateChanged();
    }

    actionFeedbackController.onSwitchAccessMenuShown(menuType);
  }

  /** Clean up when this object is no longer needed */
  public void shutdown() {
    SwitchAccessPreferenceUtils.unregisterSwitchAccessPreferenceChangedListener(this);
    if (feedbackController != null) {
      feedbackController.shutdown();
    }

    if (speechController != null) {
      handler.removeCallbacks(notifyFeedbackCompleteRunnable);
      speechController.removeObserver(this);
      speechController.shutdown();
    }
  }

  /** A listener that is notified when feedback is completed. */
  public interface OnUtteranceCompleteListener {
    /**
     * Called when feedback is completed, regardless of reason (error, completion, interruption).
     */
    void onUtteranceComplete();
  }
}
