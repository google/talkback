/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import android.view.accessibility.AccessibilityEvent;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.ui.OverlayController.GlobalMenuButtonListener;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Delays notifying the rest of Switch Access of changes to the UI. As we aren't synchronized with
 * the UI, this gives the UI time to settle.
 */
public class UiChangeStabilizer
    implements UiChangeDetector.PossibleUiChangeListener, GlobalMenuButtonListener {
  /* Empirically determined magic time that we delay after possible changes to the UI */
  private static final long FRAMEWORK_SETTLING_TIME_MILLIS = 500;

  // The amount we should delay after showing the global menu button until it's safe to clear
  // focus again.
  @VisibleForTesting
  public static final int TIME_UNTIL_SAFE_TO_CLEAR_FOCUS_AFTER_SHOWING_GLOBAL_MENU_MS = 750;

  // We should force a call to #onUiChangedAndIsNowStable if more than 3 events in the last second
  // have not been processed. This allows the tree to rebuild correctly when loading a screen with
  // a constantly refreshing view that refreshes more quickly than the rate we check for UI
  // stabilization.
  private static final int MAX_WAIT_TIME_FOR_MULTIPLE_UI_CHANGES_BEFORE_FORCED_REFRESH_MS = 1000;
  private static final int UI_CHANGES_PER_SECOND_BEFORE_FORCED_REFRESH = 3;

  // The time that the Switch Access global menu button was last shown.
  private long lastGlobalMenuButtonShownTimeMs = -1;

  // The time that UiChangedListener#onUiChangedAndIsNowStable was last called.
  private long lastCallToUiChangedListenerTimeMs;

  // The number of possible UI changes that have been detected since the time that
  // UiChangeListener#onUiChangedAndIsNowStable was last called.
  private int numUiChangesSinceLastCallToUiChangedListener = 0;

  /** Event types that are sent to WindowChangedListener. */
  private static final int MASK_EVENTS_SENT_TO_WINDOW_CHANGED_LISTENER =
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_WINDOWS_CHANGED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED;

  private final UiChangedListener uiChangedListener;
  private final WindowChangedListener windowChangedListener;

  private final Runnable runnableToInformOfUiChange =
      new Runnable() {
        @Override
        public void run() {
          long timeToWait = getMillisUntilSafeToInformOfUiChange();

          if (timeToWait > 0 && safeToDelayCallToUiChangedListener()) {
            /* Not safe to process now; reschedule for later */
            ThreadUtils.removeCallbacks(runnableToInformOfUiChange);
            ThreadUtils.runOnMainThreadDelayed(
                SwitchAccessService::isActive, runnableToInformOfUiChange, timeToWait);
            return;
          }

          if (isWaitingForSettleAfterMenuButtonShown()) {
            // Prevent focus from clearing because the global menu was just shown. Otherwise, the
            // AccessibilityEvents received from showing the global menu button can incorrectly
            // cause us to clear focus.
            return;
          }

          lastCallToUiChangedListenerTimeMs = SystemClock.elapsedRealtime();
          numUiChangesSinceLastCallToUiChangedListener = 0;
          uiChangedListener.onUiChangedAndIsNowStable(windowChangedListener, windowChangeEventList);
        }
      };

  private long lastWindowChangeTime = 0;

  // The AccessibilityEvents that will be sent to the WindowChangedListener after the UI
  // stabilizes. These events will be further sent to the SwitchAccessScreenFeedbackManager to
  // provide screen hints. When the user opens a new window, multiple AccessibilityEvents will be
  // triggered. SwitchAccessScreenFeedbackManager needs to interpret these AccessibilityEvents to
  // generate screen hints.
  private final List<AccessibilityEvent> windowChangeEventList = new ArrayList<>();

  /**
   * @param uiChangedListener A listener to be notified when the UI updates (typically an
   *     OptionManager)
   * @param windowChangedListener A listener to which accessibility window events triggered by the
   *     UI updates will be sent (typically a SwitchAccessFeedbackController)
   */
  public UiChangeStabilizer(
      UiChangedListener uiChangedListener, WindowChangedListener windowChangedListener) {
    this.uiChangedListener = uiChangedListener;
    this.windowChangedListener = windowChangedListener;
  }

  /**
   * If the UI may have changed, this method should be called so we know to wait for it to settle.
   *
   * @param event The AccessibilityEvent that caused a possible change to the UI. A null value
   *     indicates a possible change to the UI based on a user action.
   */
  @Override
  public void onPossibleChangeToUi(@Nullable AccessibilityEvent event) {
    lastWindowChangeTime = SystemClock.elapsedRealtime();
    numUiChangesSinceLastCallToUiChangedListener++;

    if (event != null) {
      int eventType = event.getEventType();
      if ((eventType & MASK_EVENTS_SENT_TO_WINDOW_CHANGED_LISTENER) != 0) {
        if (windowChangeEventList.isEmpty()) {
          // When the windowChangeEventList queue is empty, the current event is the first of a
          // sequence of events that are triggered by the UI change. Call windowChangedListener to
          // stop all previous speech immediately before
          // providing spoken feedback for the new UI.
          windowChangedListener.onWindowChangeStarted();
        }

        // Obtain a new AccessibilityEvent because {@link AccessibilityEvent#getSource} can only be
        // called on a sealed instance.
        windowChangeEventList.add(AccessibilityEvent.obtain(event));
      }
    }

    if (safeToDelayCallToUiChangedListener()) {
      ThreadUtils.removeCallbacks(runnableToInformOfUiChange);
    }
    ThreadUtils.runOnMainThreadDelayed(
        SwitchAccessService::isActive,
        runnableToInformOfUiChange,
        getMillisUntilSafeToInformOfUiChange());
  }

  @Override
  public void onGlobalMenuButtonShown() {
    // If the Switch Access global menu has just been shown, signal that we should delay clearing
    // focus to prevent excessive tree rebuilding (e.g. on pages with constantly changing content,
    // such as YouTube). However, if other accessibility events are pending when the global menu
    // button is shown, do not prevent focus from being cleared as it possible that focus should be
    // cleared from the pending events. Otherwise, Switch Access can become "stuck" on a stale tree
    // that won't rebuild. For example- beginning a scan immediately after closing the SA global
    // menu would cause the menu button to show and prevent the tree from being rebuilt with the
    // content behind the menu.
    if (windowChangeEventList.isEmpty()) {
      lastGlobalMenuButtonShownTimeMs = SystemClock.elapsedRealtime();
    }
  }

  /** Listener that is notified of UI changes once the UI stabilizes. */
  public interface UiChangedListener {
    /**
     * Notifies the listener that the UI has stabilized.
     *
     * @param windowChangedListener The listener to which window change events generated by the UI
     *     change will be sent to
     * @param windowChangeEventList A list of AccessibilityEvents generated by the UI change
     */
    void onUiChangedAndIsNowStable(
        WindowChangedListener windowChangedListener,
        List<AccessibilityEvent> windowChangeEventList);
  }

  /**
   * Listener to which window change events are sent to once the UI stabilizes. Events are queued
   * while,the UI is not stable, then all queued events are sent once the UI stabilizes.
   */
  public interface WindowChangedListener {

    /** Called when a window change is first detected. */
    void onWindowChangeStarted();

    /**
     * Called when the UI stabilizes after a window change.
     *
     * @param event An {@link AccessibilityEvent} triggered by the window change
     * @param eventId Id of the event
     */
    void onWindowChangedAndIsNowStable(AccessibilityEvent event, @Nullable EventId eventId);

    /**
     * Called when a window change triggered by opening the Switch Access menu is detected.
     *
     * @param menuType Type of the Switch Access menu
     */
    void onSwitchAccessMenuShown(MenuType menuType);
  }

  public void shutdown() {
    ThreadUtils.removeCallbacks(runnableToInformOfUiChange);
  }

  private long getMillisUntilSafeToInformOfUiChange() {
    long timeToWait =
        lastWindowChangeTime + FRAMEWORK_SETTLING_TIME_MILLIS - SystemClock.elapsedRealtime();
    timeToWait = Math.max(timeToWait, 0);
    return timeToWait;
  }

  private boolean isWaitingForSettleAfterMenuButtonShown() {
    long elapsedTime = SystemClock.elapsedRealtime() - lastGlobalMenuButtonShownTimeMs;
    return (elapsedTime < TIME_UNTIL_SAFE_TO_CLEAR_FOCUS_AFTER_SHOWING_GLOBAL_MENU_MS);
  }

  // If UI events are occurring too quickly to be processed, we shouldn't continue to delay
  // calls to UiChangedListener#onUiChangedAndIsNowStable, as this is indicates that the view
  // may never stabilize (i.e. there is a constantly refreshing view that prevents stabilization).
  // Forcing a call to #onUiChangedAndIsNowStable may cause duplicate information to be sent to the
  // UiChangedListener, but this is preferable to information never getting sent.
  private boolean safeToDelayCallToUiChangedListener() {
    long currentTime = SystemClock.elapsedRealtime();
    long timeSinceLastCallToUiChangeListener = currentTime - lastCallToUiChangedListenerTimeMs;

    // Less than 3 calls to possibleChangeToUi &
    return (timeSinceLastCallToUiChangeListener
            < MAX_WAIT_TIME_FOR_MULTIPLE_UI_CHANGES_BEFORE_FORCED_REFRESH_MS)
        || (numUiChangesSinceLastCallToUiChangedListener
            < UI_CHANGES_PER_SECOND_BEFORE_FORCED_REFRESH);
  }
}
