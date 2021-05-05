/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.actor.search;

import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_FONT_SCALE;
import static android.content.pm.ActivityInfo.CONFIG_LAYOUT_DIRECTION;
import static com.google.android.accessibility.talkback.Feedback.HINT;

import android.content.res.Configuration;
import androidx.annotation.VisibleForTesting;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.RingerModeAndScreenMonitor;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.WindowEventInterpreter;
import com.google.android.accessibility.utils.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Handles keyword search of the nodes on the screen. REFERTO */
public class UniversalSearchManager
    implements ServiceKeyEventListener,
        KeyComboManager.KeyComboListener,
        WindowEventInterpreter.WindowEventHandler {
  private static String TAG = "ScreenSearch";
  /**
   * Monitor the components of configuration. When any of the components changed, the screen overlay
   * has to be refreshed
   */
  private static final int CONFIG_CHANGE_TRACKING_MASK =
      CONFIG_FONT_SCALE | CONFIG_DENSITY | CONFIG_LAYOUT_DIRECTION;

  /** The parent context. */
  private final TalkBackService talkbackService;

  /** SearchScreenOverlay object for control UI display. */
  private SearchScreenOverlay searchScreenOverlay;

  private Pipeline.FeedbackReturner pipeline;

  /**
   * Record current configuration so that when configuration changed, we can tell whether the
   * changed parts affect the overlay
   */
  private Configuration currentConfig;

  public UniversalSearchManager(
      TalkBackService talkbackservice,
      SearchScreenOverlay searchScreenOverlay,
      Pipeline.FeedbackReturner pipeline,
      RingerModeAndScreenMonitor ringerModeAndScreenMonitor,
      WindowEventInterpreter windowInterpreter) {
    this.talkbackService = talkbackservice;
    this.pipeline = pipeline;
    this.searchScreenOverlay = searchScreenOverlay;
    currentConfig = new Configuration(talkbackservice.getResources().getConfiguration());

    // Registers screen state changed listener.
    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.addScreenChangedListener(
          (isScreenOn, eventId) -> {
            cancelSearchWhenScreenOff(isScreenOn, eventId);
          });
    }

    if (windowInterpreter != null) {
      windowInterpreter.addListener(this);
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

  // Cancels search when screen off.
  @VisibleForTesting
  protected void cancelSearchWhenScreenOff(boolean isScreenOn, EventId eventId) {
    if (!isUiVisible()) {
      return;
    }
    if (!isScreenOn) {
      cancelSearch(eventId);
    }
  }

  /** Toggle search mode. */
  public void toggleSearch(EventId eventId) {
    if (isUiVisible()) {
      cancelSearch(eventId);
    } else {
      startSearch(eventId);
    }
  }

  @Override
  public void handle(EventInterpretation interpretation, EventId eventId) {
    if (!interpretation.areWindowsStable()) {
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

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    return false;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }

  @Override
  public boolean onComboPerformed(int id, String name, EventId eventId) {
    if (id == KeyComboManager.ACTION_TOGGLE_SEARCH) {
      toggleSearch(eventId);
      return true;
    }
    return false;
  }

  /** Start search mode. */
  private void startSearch(EventId eventId) {
    searchScreenOverlay.show();
  }

  /** Cancel the current search. Return accessibility focus to the initial node. */
  private void cancelSearch(@Nullable EventId eventId) {
    searchScreenOverlay.hide();

    // Speak stop search hint.
    CharSequence hint = talkbackService.getString(R.string.search_mode_cancel);
    // Use {@link FeedbackItem#FLAG_NO_HISTORY} not to save this utterance to history.
    // Use {@link FeedbackItem#FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE} force to speak while
    // playing audio.
    // Use {@link FeedbackItem#FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE} force to speak while using
    // microphone.
    // Use {@link FeedbackItem#FLAG_FORCED_FEEDBACK_SSB_ACTIVE} force to speak while speech
    // recognition.
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setFlags(
                FeedbackItem.FLAG_NO_HISTORY
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(hint, speakOptions)
            .setInterruptGroup(HINT)
            .setInterruptLevel(1)
            .setSenderName(TAG));
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
        AccessibilityServiceCompatUtils.getWindows(talkbackService);
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
        AccessibilityServiceCompatUtils.getWindows(talkbackService);
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

  public void onAutoScrolled(AccessibilityNode scrolledNode, EventId eventId) {
    searchScreenOverlay.onAutoScrolled(scrolledNode, eventId);
  }

  public void onAutoScrollFailed(AccessibilityNode scrolledNode) {
    searchScreenOverlay.onAutoScrollFailed(scrolledNode);
  }
}
