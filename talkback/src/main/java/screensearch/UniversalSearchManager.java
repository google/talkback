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

package com.google.android.accessibility.talkback.screensearch;

import androidx.annotation.VisibleForTesting;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.RingerModeAndScreenMonitor;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorAccessibilityHints;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WindowEventInterpreter;
import com.google.android.accessibility.utils.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Handles keyword search of the nodes on the screen. See  */
public class UniversalSearchManager
    implements ServiceKeyEventListener,
        KeyComboManager.KeyComboListener,
        WindowEventInterpreter.WindowEventHandler {
  /** The parent context. */
  private final TalkBackService talkbackService;

  /** SearchScreenOverlay object for control UI display. */
  private final SearchScreenOverlay searchScreenOverlay;

  /** ProcessorAccessibilityHints object, which is used to speak hint. */
  private ProcessorAccessibilityHints processorAccessibilityHints;

  public UniversalSearchManager(
      TalkBackService talkbackservice,
      CustomLabelManager labelManager,
      Pipeline.FeedbackReturner pipeline,
      RingerModeAndScreenMonitor ringerModeAndScreenMonitor,
      WindowEventInterpreter windowInterpreter) {
    this.talkbackService = talkbackservice;

    searchScreenOverlay = new SearchScreenOverlay(this.talkbackService, labelManager, pipeline);

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
    if (!isUiVisible()) {
      return;
    }

    // We need to cancel search when focused window changed and new overlay appeared so that we
    // won't perform search on wrong screen content.
    if (interpretation.getMainWindowsChanged()
        && !isOverlayRemoved(interpretation)
        // Need to exclude change triggered by screen search to prevent close automatically upon
        // launching.
        && !isScreenSearchChange(interpretation)) {
      cancelSearch(eventId);
    }
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
  public boolean onComboPerformed(int id, EventId eventId) {
    if (id == KeyComboManager.ACTION_TOGGLE_SEARCH) {
      toggleSearch(eventId);
      return true;
    }
    return false;
  }

  /** Start search mode. */
  private void startSearch(EventId eventId) {
    searchScreenOverlay.show();

    /** Speak start search hint. */
    SpannableStringBuilder builder = new SpannableStringBuilder();
    StringBuilderUtils.appendWithSeparator(
        builder, talkbackService.getString(R.string.search_mode_open));
    StringBuilderUtils.appendWithSeparator(
        builder, talkbackService.getString(R.string.search_mode_hint_start));

    /** Use the ProcessorAccessibilityHints object to speak hint. */
    if (processorAccessibilityHints != null) {
      processorAccessibilityHints.onScreenHint(builder.toString());
    }
  }

  /** Cancel the current search. Return accessibility focus to the initial node. */
  private void cancelSearch(@Nullable EventId eventId) {
    searchScreenOverlay.stopSearch();

    /** Speak stop search hint. */
    CharSequence hint = talkbackService.getString(R.string.search_mode_cancel);
    if (processorAccessibilityHints != null) {
      processorAccessibilityHints.onScreenHint(hint.toString());
    }
  }

  /** Set the ProcessorAccessibilityHints object. */
  public void setProcessorAccessibilityHints(ProcessorAccessibilityHints processor) {
    processorAccessibilityHints = processor;
  }

  /** Returns if current search UI is visible. */
  public boolean isUiVisible() {
    return searchScreenOverlay.isVisible();
  }

  int getOverlayId() {
    return searchScreenOverlay.getOverlayId();
  }

  private boolean isOverlayRemoved(EventInterpretation interpretation) {
    if (!interpretation.getAccessibilityOverlay().idOrTitleChanged()) {
      return false;
    }

    // Change triggered by overlay but ID is -1 means that change was resulted from overlay removed.
    // We would only like to cancel search when new overlay is shown not hidden.
    // Refer to .
    return interpretation.getAccessibilityOverlay().getId()
        == AccessibilityEventUtils.WINDOW_ID_NONE;
  }

  private boolean isScreenSearchChange(EventInterpretation interpretation) {
    if (!interpretation.getAccessibilityOverlay().idOrTitleChanged()) {
      return false;
    }

    return interpretation.getAccessibilityOverlay().getId() == getOverlayId();
  }

  public void onAutoScrolled(AccessibilityNode scrolledNode, EventId eventId) {
    searchScreenOverlay.onAutoScrolled(scrolledNode, eventId);
  }

  public void onAutoScrollFailed(AccessibilityNode scrolledNode) {
    searchScreenOverlay.onAutoScrollFailed(scrolledNode);
  }
}
