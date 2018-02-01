/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import android.accessibilityservice.AccessibilityService;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;

/**
 * A class works as a detector for {@link ScreenState} changes and notifies {@link
 * com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager} of the
 * change. It also provides an API {@link #isWindowStateRecentlyChanged(int)} to check whether a
 * window is under transition.
 *
 * <p><strong>Note: </strong>To make this feature works, the {@link AccessibilityService} has to
 * declare the capability to retrieve window content by setting the {@code
 * android.R.styleable#AccessibilityService_canRetrieveWindowContent} property in its meta-data.
 * Also, the service has to opt-in to retrieve the interactive windows by setting the {@link
 * android.accessibilityservice.AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS} flag.
 */
public class ScreenStateMonitor implements AccessibilityEventListener {
  // Mask of accessibility events processed by this class.
  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  // Timeout that we consider the screen become stable since the last time we captured a screen
  // state change.
  // TODO:. We need longer delay for TV devices.
  // ProcessorFocusAndSingleTap.FirstWindowFocusManager.MISS_FOCUS_DELAY_TV is 1200.
  // TODO: Need longer delay for PicInPic window.
  // PicInPic is a special case where the window transition might last for 4 seconds.
  private static final int TIMEOUT_UI_STABILIZE_MS = 400;

  /* Used to get current window list with AccessibilityService.getWindows() */
  private final AccessibilityService mService;

  private final AccessibilityFocusManager mA11yFocusManager;

  /* The handler used to post delay handling screen state changes. */
  private final PostDelayHandler mHandler;

  /* The last stable screen state. */
  private ScreenState mLastStableScreenState;

  /* The latest screen state we captured during window transition. */
  private ScreenState mLatestScreenStateDuringTransition;

  /* Window transition information since the time we capture the last stable screen state. */
  private final WindowTransitionInfo mWindowTransitionInfo;

  public ScreenStateMonitor(
      AccessibilityService service, AccessibilityFocusManager a11yFocusManager) {
    mService = service;
    mA11yFocusManager = a11yFocusManager;
    mHandler = new PostDelayHandler(this);
    mWindowTransitionInfo = new WindowTransitionInfo();
  }

  @Override
  public int getEventTypes() {
    return EVENT_MASK;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    updateWindowTransitionInfoFromEvent(event);

    ScreenState currentScreenState = getCurrentScreenState();
    ScreenState lastScreenState =
        mLatestScreenStateDuringTransition == null
            ? mLastStableScreenState
            : mLatestScreenStateDuringTransition;

    if (!currentScreenState.equals(lastScreenState)) {
      // Called when any window ID or window title is added/removed/replaced.
      scheduleNotifyingScreenStateChanged(currentScreenState, eventId);
    }
  }

  /**
   * Caches the latest screen state and posts delayed to notify screen state changes.
   *
   * <p>We don't notify the change immediately. Instead, we post delay to notify the change in case
   * when the window transition takes a long time with several steps(e.g. Replacing a window might
   * be captured as removing a window and then adding another window), and we might capture an
   * intermediate screen state.
   */
  private void scheduleNotifyingScreenStateChanged(ScreenState newScreenState, EventId eventId) {
    mLatestScreenStateDuringTransition = newScreenState;
    mHandler.postHandleScreenStateChanges(eventId, TIMEOUT_UI_STABILIZE_MS);
  }

  private void updateWindowTransitionInfoFromEvent(AccessibilityEvent event) {
    mWindowTransitionInfo.updateTransitionInfoFromEvent(event);
  }

  private void handleScreenStateChangeWhenUIStabilized(EventId eventId) {
    ScreenState oldScreenState = mLastStableScreenState;
    ScreenState newScreenState = mLatestScreenStateDuringTransition;

    setStableScreenState(mLatestScreenStateDuringTransition);
    onScreenStateChanged(oldScreenState, newScreenState, eventId);
  }

  /** Sets the last stable {@link ScreenState} and clear the window transition information. */
  private void setStableScreenState(ScreenState stableScreenState) {
    mLastStableScreenState = stableScreenState;
    mLatestScreenStateDuringTransition = null;
    mWindowTransitionInfo.clear();
  }

  /**
   * Gets the current screen state.
   *
   * <p>The current screen state is composed of the following three components:
   *
   * <ul>
   *   <li>Basic information from {@link AccessibilityService#getWindows()} (e.g. How many windows
   *       are currently on screen.)
   *   <li>Legacy information in last stable screen state (e.g. We need to inherit the legacy window
   *       titles when {@link AccessibilityWindowInfo#getTitle()} is not available for API level <
   *       24.)
   *   <li>Transition information since last stable screen state. (e.g. The window titles can only
   *       be retrieved from AccessibilityEvent when {@link AccessibilityWindowInfo#getTitle()} is
   *       not available for API level < 24, which are cached during transition.)
   * </ul>
   */
  private ScreenState getCurrentScreenState() {
    ScreenState previousScreenInfo = mLastStableScreenState;
    ScreenState newScreenInfo = new ScreenState(mService.getWindows());

    // AccessibilityWindowInfo.getTitle() is introduced at API level 24. Before that, we can only
    // passively listen to and cache window title notified in window_state_changed events and
    // carefully inherits the information when window transition completes.

    // Inherit screen information from previous screen state.
    if (previousScreenInfo != null) {
      newScreenInfo.updateOverriddenWindowTitles(previousScreenInfo);
    }
    // Update screen information from the ongoing window transition.
    newScreenInfo.updateOverriddenWindowTitles(mWindowTransitionInfo);
    return newScreenInfo;
  }

  /** Returns whether the window with given ID is under transition. */
  public boolean isWindowStateRecentlyChanged(int windowId) {
    return mWindowTransitionInfo.isWindowStateRecentlyChanged(windowId);
  }

  /**
   * Notifies {@link
   * com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager} of the
   * {@link ScreenState} changes.
   */
  private void onScreenStateChanged(
      @Nullable ScreenState previousScreen, @NonNull ScreenState currentScreen, EventId eventId) {
    LogUtils.log(
        this,
        Log.DEBUG,
        "onScreenStateChanged.\nPrevious screen: %s\nCurrent screen: %s",
        previousScreen,
        currentScreen);
    mA11yFocusManager.notifyScreenStateChanged(previousScreen, currentScreen, eventId);
  }

  /** A {@link WeakReferenceHandler} used to post delay handing {@link ScreenState} changes. */
  private static final class PostDelayHandler extends WeakReferenceHandler<ScreenStateMonitor> {
    private static final int MSG_SCREEN_STATE_CHANGES = 1;

    private PostDelayHandler(ScreenStateMonitor parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, ScreenStateMonitor parent) {
      if (msg.what != MSG_SCREEN_STATE_CHANGES) {
        return;
      }
      parent.handleScreenStateChangeWhenUIStabilized((EventId) msg.obj);
    }

    private void postHandleScreenStateChanges(EventId eventId, long delay) {
      removeMessages(MSG_SCREEN_STATE_CHANGES);
      sendMessageDelayed(obtainMessage(/* what= */ MSG_SCREEN_STATE_CHANGES, eventId), delay);
    }
  }
}
