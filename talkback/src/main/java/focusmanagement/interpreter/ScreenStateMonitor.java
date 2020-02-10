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

import static com.google.android.accessibility.utils.WindowEventInterpreter.WINDOW_CHANGE_DELAY_MS;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WindowEventInterpreter;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * A class works as a detector for {@link ScreenState} changes and notifies {@link
 * ScreenStateChangeListener} of the change. It also provides an API {@link
 * #isWindowStateRecentlyChanged(int)} to check whether a window is under transition.
 *
 * <p><strong>Note: </strong>To make this feature works, the {@link AccessibilityService} has to
 * declare the capability to retrieve window content by setting the {@code
 * android.R.styleable#AccessibilityService_canRetrieveWindowContent} property in its meta-data.
 * Also, the service has to opt-in to retrieve the interactive windows by setting the {@link
 * android.accessibilityservice.AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS} flag.
 */
public class ScreenStateMonitor implements AccessibilityEventListener {

  private static final String TAG = "ScreenStateMonitor";

  /** Listens to {@link ScreenState} changes. */
  public interface ScreenStateChangeListener {
    /**
     * Callback when {@link ScreenState} changes.
     *
     * @return {@code true} if any accessibility action is successfully performed.
     */
    boolean onScreenStateChanged(
        @Nullable ScreenState oldScreenState,
        ScreenState newScreenState,
        long startTime,
        EventId eventId);
  }

  // Mask of accessibility events processed by this class.
  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  // Mask of window change types in TYPE_WINDOWS_CHANGED events processed by this class.
  @TargetApi(Build.VERSION_CODES.P)
  private static final int WINDOW_CHANGES_MASK =
      AccessibilityEvent.WINDOWS_CHANGE_ADDED
          | AccessibilityEvent.WINDOWS_CHANGE_REMOVED
          | AccessibilityEvent.WINDOWS_CHANGE_TITLE
          | AccessibilityEvent.WINDOWS_CHANGE_PARENT
          | AccessibilityEvent.WINDOWS_CHANGE_CHILDREN
          | AccessibilityEvent.WINDOWS_CHANGE_PIP;

  // Timeout that we consider the screen become stable since the last time we captured a screen
  // state change.
  // TODO: . We need longer delay for TV devices.
  // ProcessorFocusAndSingleTap.FirstWindowFocusManager.MISS_FOCUS_DELAY_TV is 1200.
  // TODO: Need longer delay for PicInPic window.
  // PicInPic is a special case where the window transition might last for 4 seconds.
  // TODO: Investigate if we can/need to decrease the delay for better user experience.
  private static final int WINDOW_DELAY_MARGIN_MS = 10;

  /** Timeout to re-verify when we find that active window after transition is invisible. */
  private static final int TIMEOUT_RETRY_MS = 200;

  /**
   * Maximum attempts to check active window visibility and notify listeners.
   *
   * <p>The maximum timeout we think a screen state must be stabilized is 510 *1 + 200*3 = 1110ms.
   */
  private static final int MAXIMUM_ATTEMPTS = 4;

  /* Used to get current window list with AccessibilityService.getWindows() */
  private final AccessibilityService service;

  /** Source of the current window transition delay time. */
  private WindowEventInterpreter windowEventInterpreter;

  /* The handler used to post delay handling screen state changes. */
  private final PostDelayHandler handler;

  /* The last stable screen state. */
  private ScreenState lastStableScreenState;

  /* The latest screen state we captured during window transition. */
  @Nullable private ScreenState latestScreenStateDuringTransition;

  private long screenTransitionStartTime = -1;

  /* Window transition information since the time we capture the last stable screen state. */
  private final WindowTransitionInfo windowTransitionInfo;

  private final List<ScreenStateChangeListener> listeners =
      new ArrayList<ScreenStateChangeListener>();

  public ScreenStateMonitor(AccessibilityService service) {
    this.service = service;
    handler = new PostDelayHandler(this);
    windowTransitionInfo = new WindowTransitionInfo();
  }

  public void setWindowEventInterpreter(WindowEventInterpreter windowEventInterpreter) {
    this.windowEventInterpreter = windowEventInterpreter;
  }

  public void addScreenStateChangeListener(ScreenStateChangeListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Listener must not be null.");
    }
    listeners.add(listener);
  }

  @Override
  public int getEventTypes() {
    return EVENT_MASK;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (shouldIgnoreEvent(event)) {
      return;
    }
    List<AccessibilityWindowInfo> currentWindows =
        AccessibilityServiceCompatUtils.getWindows(service);

    AccessibilityWindowInfo sourceWindow = getWindowInfoById(currentWindows, event.getWindowId());

    // Update window transition info if the windowId is valid.
    // For pre-P devices, it's mostly a TYPE_WINDOW_STATE_CHANGED event.
    // For p-devices, it could be either TYPE_WINDOWS_CHANGED or TYPE_WINDOW_STATE_CHANGED event.
    windowTransitionInfo.updateTransitionInfoFromEvent(sourceWindow, event);

    ScreenState currentScreenState =
        createScreenStateSnapShot(
            currentWindows, AccessibilityServiceCompatUtils.getActiveWidow(service));
    if (currentScreenState.equals(lastStableScreenState)) {
      // If screen state changes several times in a short time, but eventually falls back to the
      // original stable state, clear the window transition and do not notify screen state changes.
      cancelPendingScreenStateChangedAndClearCache();
      return;
    }
    if (latestScreenStateDuringTransition == null) {
      // This is the first screen state captured during the transition.
      screenTransitionStartTime = event.getEventTime();
    }
    // Called when any window ID or window title is added/removed/replaced.
    postDelayNotifyingScreenStateChanged(currentScreenState, eventId);
  }

  @TargetApi(Build.VERSION_CODES.P)
  private boolean shouldIgnoreEvent(AccessibilityEvent event) {
    // Starting from P, we only care about certain types of window changes.
    return BuildVersionUtils.isAtLeastP()
        && (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        && ((event.getWindowChanges() & WINDOW_CHANGES_MASK) == 0);
  }

  @Nullable
  private AccessibilityWindowInfo getWindowInfoById(
      List<AccessibilityWindowInfo> windows, int windowId) {
    if (windowId < 0) {
      return null;
    }
    for (AccessibilityWindowInfo window : windows) {
      if (window.getId() == windowId) {
        return window;
      }
    }
    return null;
  }

  /**
   * Caches the latest screen state and posts delayed to notify screen state changes.
   *
   * <p>We don't notify the change immediately. Instead, we post delay to notify the change in case
   * when the window transition takes a long time with several steps(e.g. Replacing a window might
   * be captured as removing a window and then adding another window), and we might capture an
   * intermediate screen state.
   */
  private void postDelayNotifyingScreenStateChanged(ScreenState newScreenState, EventId eventId) {
    latestScreenStateDuringTransition = newScreenState;
    long transitionMs =
        (windowEventInterpreter == null)
            ? WINDOW_CHANGE_DELAY_MS
            : windowEventInterpreter.getWindowTransitionDelayMs();
    transitionMs += WINDOW_DELAY_MARGIN_MS;
    handler.postHandleScreenStateChanges(eventId, /* attempt= */ 1, transitionMs);
  }

  private void cancelPendingScreenStateChangedAndClearCache() {
    handler.cancelHandlingScreenStateChanges();
    latestScreenStateDuringTransition = null;
    screenTransitionStartTime = -1;
    windowTransitionInfo.clear();
  }

  private void handleScreenStateChangeWhenUIStabilized(EventId eventId, int attempt) {
    AccessibilityWindowInfo activeWindow = latestScreenStateDuringTransition.getActiveWindow();
    if ((activeWindow != null)
        && !AccessibilityWindowInfoUtils.isWindowContentVisible(activeWindow)
        && (attempt < MAXIMUM_ATTEMPTS)) {
      // UI might not be stabilized yet. Try again later.
      LogUtils.w(TAG, "Active window is invisible, try again later.");
      handler.postHandleScreenStateChanges(eventId, attempt + 1, TIMEOUT_RETRY_MS);
      return;
    }
    ScreenState oldScreenState = lastStableScreenState;
    ScreenState newScreenState = latestScreenStateDuringTransition;
    long transitionStartTime = screenTransitionStartTime;
    setStableScreenState(latestScreenStateDuringTransition);
    onScreenStateChanged(oldScreenState, newScreenState, transitionStartTime, eventId);
  }

  /** Sets the last stable {@link ScreenState} and clear the window transition information. */
  private void setStableScreenState(ScreenState stableScreenState) {
    lastStableScreenState = stableScreenState;
    screenTransitionStartTime = -1;
    latestScreenStateDuringTransition = null;
    windowTransitionInfo.clear();
  }

  /**
   * Takes a snapshot of and returns current screen state.
   *
   * <p>The current screen state is composed of the following three components:
   *
   * <ul>
   *   <li>Basic information from {@link AccessibilityService#getWindows()} (e.g. How many windows
   *       are currently on screen, what is the active window.)
   *   <li>Legacy information in last stable screen state (e.g. We need to inherit the legacy window
   *       titles when {@link AccessibilityWindowInfo#getTitle()} is not available for API level <
   *       24.)
   *   <li>Transition information since last stable screen state. (e.g. The window titles can only
   *       be retrieved from AccessibilityEvent when {@link AccessibilityWindowInfo#getTitle()} is
   *       not available for API level < 24, which are cached during transition.)
   * </ul>
   */
  private ScreenState createScreenStateSnapShot(
      List<AccessibilityWindowInfo> currentWindows, AccessibilityWindowInfo activeWindow) {
    ScreenState previousScreenInfo = lastStableScreenState;
    ScreenState newScreenInfo = new ScreenState(currentWindows, activeWindow);

    // AccessibilityWindowInfo.getTitle() is introduced at API level 24. Before that, we can only
    // passively listen to and cache window title notified in window_state_changed events and
    // carefully inherits the information when window transition completes.

    // Inherit screen information from previous screen state.
    if (previousScreenInfo != null) {
      newScreenInfo.inheritOverriddenTitlesFromPreviousScreenState(previousScreenInfo);
    }
    // Update screen information from the ongoing window transition.
    newScreenInfo.updateOverriddenTitlesFromEvents(windowTransitionInfo);
    return newScreenInfo;
  }

  @Nullable
  public ScreenState getCurrentScreenState() {
    return latestScreenStateDuringTransition == null
        ? lastStableScreenState
        : latestScreenStateDuringTransition;
  }

  /** Returns whether the window with given ID is under transition. */
  public boolean isWindowStateRecentlyChanged(int windowId) {
    return windowTransitionInfo.isWindowStateRecentlyChanged(windowId);
  }

  /** Notifies {@link ScreenStateChangeListener} of the {@link ScreenState} changes. */
  private void onScreenStateChanged(
      @Nullable ScreenState previousScreen,
      @NonNull ScreenState currentScreen,
      long startTime,
      EventId eventId) {
    for (ScreenStateChangeListener listener : listeners) {
      listener.onScreenStateChanged(previousScreen, currentScreen, startTime, eventId);
    }
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
      parent.handleScreenStateChangeWhenUIStabilized((EventId) msg.obj, /* attempt= */ msg.arg1);
    }

    private void postHandleScreenStateChanges(EventId eventId, int attempt, long delay) {
      removeMessages(MSG_SCREEN_STATE_CHANGES);
      sendMessageDelayed(
          obtainMessage(
              /* what= */ MSG_SCREEN_STATE_CHANGES,
              /* arg1= */ attempt, // Count of attempts.
              /* arg2= */ 0, // Unused.
              eventId),
          delay);
    }

    private void cancelHandlingScreenStateChanges() {
      removeMessages(MSG_SCREEN_STATE_CHANGES);
    }
  }
}
