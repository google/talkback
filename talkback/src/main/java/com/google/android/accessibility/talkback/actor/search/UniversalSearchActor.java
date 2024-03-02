/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.android.accessibility.talkback.actor.search;

import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_FONT_SCALE;
import static android.content.pm.ActivityInfo.CONFIG_LAYOUT_DIRECTION;
import static com.google.android.accessibility.talkback.Feedback.HINT;

import android.content.Context;
import android.content.res.Configuration;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.labeling.TalkBackLabelManager;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Toggle search actions performer */
public class UniversalSearchActor {
  private Context context;
  private TalkBackService talkBackService;
  private SearchScreenOverlay searchScreenOverlay;
  private ScreenStateMonitor.State screenState;
  private Pipeline.FeedbackReturner pipeline;
  private static final String TAG = "UniversalSearchActor";

  /**
   * Monitor the components of configuration. When any of the components changed, the screen overlay
   * has to be refreshed
   */
  private static final int CONFIG_CHANGE_TRACKING_MASK =
      CONFIG_FONT_SCALE | CONFIG_DENSITY | CONFIG_LAYOUT_DIRECTION;

  /**
   * Record current configuration so that when configuration changed, we can tell whether the
   * changed parts affect the overlay
   */
  private Configuration currentConfig;

  /** Limited read only interface returning search state. */
  public class State {
    public boolean isUiVisible() {
      return UniversalSearchActor.this.isUiVisible();
    }
  }

  public State state = new State();

  public UniversalSearchActor(
      TalkBackService talkBackService,
      ScreenStateMonitor.State screenState,
      FocusFinder focusFinder,
      TalkBackLabelManager labelManager) {
    this.context = talkBackService;
    this.talkBackService = talkBackService;
    this.screenState = screenState;

    // Search mode should receive key combos immediately after the TalkBackService.
    searchScreenOverlay = new SearchScreenOverlay(talkBackService, focusFinder, labelManager);

    currentConfig = new Configuration(context.getResources().getConfiguration());
  }

  // For testing
  public void setSearchScreenOverlay(SearchScreenOverlay searchScreenOverlay) {
    this.searchScreenOverlay = searchScreenOverlay;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
    searchScreenOverlay.setPipeline(pipeline);
  }

  /** Toggles search mode. */
  public void toggleSearch(EventId eventId) {
    if (isUiVisible()) {
      cancelSearch(eventId);
    } else {
      startSearch();
    }
  }

  // Called when configuration change. Then determine whether densityDPI, fontScale or layout
  // direction are changed which causes the overlay invalid.
  public void renewOverlay(Configuration newConfig) {
    if ((currentConfig.diff(newConfig) & CONFIG_CHANGE_TRACKING_MASK) != 0) {
      searchScreenOverlay.invalidateUIElements();
      currentConfig = new Configuration(newConfig);
    }
  }

  /**
   * Called when {@link com.google.android.accessibility.utils.input.WindowEventInterpreter} has
   * interpreted a window event.
   */
  public void handleScreenState(EventId eventId) {
    if (!screenState.areMainWindowsStable()) {
      return;
    }

    if (!isUiVisible()) {
      return;
    }

    if (!isScreenSearchPresent()) {
      // This might happened in old platform under API28 because of design changed in framework.
      LogUtils.v(
          TAG,
          "Search overlay exist but framework didn't aware yet, we can ignore the false alarm and"
              + " wait for next window event.");
      return;
    }

    if (isTargetWindowAccessibleBelowTalkbackOverlays()) {
      return;
    }

    // We need to cancel search when focused window gone or above the overlay so that we
    // won't perform search on wrong screen content.
    LogUtils.v(
        TAG,
        "Search canceled due to can't find initialFocusedWindow below screen search after window"
            + " changed.");
    cancelSearch(eventId);
  }

  /** Starts search mode. */
  private void startSearch() {
    searchScreenOverlay.show();
  }

  /** Cancel the current search. Return accessibility focus to the initial node. */
  public void cancelSearch(@Nullable EventId eventId) {
    if (!isUiVisible()) {
      return;
    }
    searchScreenOverlay.hide();

    // Speak stop search hint.
    CharSequence hint = context.getString(R.string.search_mode_cancel);
    // Use {@link FeedbackItem#FLAG_NO_HISTORY} not to save this utterance to history.
    // Use {@link FeedbackItem#FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE} force to speak
    // while playing audio.
    // Use {@link FeedbackItem#FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE} force to speak while
    // using microphone.
    // Use {@link FeedbackItem#FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE} force to speak while speech
    // recognition.
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setFlags(
                FeedbackItem.FLAG_NO_HISTORY
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE);

    if (pipeline != null) {
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(hint, speakOptions)
              .setInterruptGroup(HINT)
              .setInterruptLevel(1)
              .setSenderName(TAG));
    }
  }

  /** Returns if current search UI is visible. */
  public boolean isUiVisible() {
    return searchScreenOverlay.isVisible();
  }

  private int getOverlayId() {
    return searchScreenOverlay.getOverlayId();
  }

  private int getInitialFocusedWindowId() {
    return searchScreenOverlay.getInitialFocusedWindowId();
  }

  private boolean isScreenSearchPresent() {
    if (getOverlayId() < 0) {
      return false;
    }
    List<AccessibilityWindowInfo> windows =
        AccessibilityServiceCompatUtils.getWindows(talkBackService);
    return windows.stream().anyMatch(w -> w != null && w.getId() == getOverlayId());
  }

  /**
   * In normal case the target window should be accessible under screen search overlay, otherwise we
   * should close screen search overlay because it can't find anything.
   * REFERTO
   */
  private boolean isTargetWindowAccessibleBelowTalkbackOverlays() {
    if (getOverlayId() < 0 || getInitialFocusedWindowId() < 0) {
      return false;
    }
    List<AccessibilityWindowInfo> windows =
        AccessibilityServiceCompatUtils.getWindows(talkBackService);
    int targetWindowLayer = -1;
    int overlayWindowLayer = -1;
    for (AccessibilityWindowInfo window : windows) {
      if (getInitialFocusedWindowId() == window.getId()) {
        targetWindowLayer = window.getLayer();
      } else if (getOverlayId() == window.getId()) {
        overlayWindowLayer = window.getLayer();
      }
    }
    return ((targetWindowLayer > -1) && (overlayWindowLayer > targetWindowLayer));
  }

  public void onAutoScrolled(@NonNull AccessibilityNode scrolledNode, EventId eventId) {
    searchScreenOverlay.onAutoScrolled(scrolledNode, eventId);
  }

  public void onAutoScrollFailed(@NonNull AccessibilityNode scrolledNode) {
    searchScreenOverlay.onAutoScrollFailed(scrolledNode);
  }
}
