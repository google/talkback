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

package com.google.android.accessibility.utils;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.Role.RoleName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Translates Accessibility Window Events into more usable event description.
 *
 * <p>The main difficulties are floods of transitional window state events, many of which have to be
 * composed before we know the net window-state change. To solve for this, when a transitional
 * window event is received, interpretation is delayed, and window changes are buffered in
 * mPendingWindowRoles until the delayed interpretation is complete.
 */
public class WindowEventInterpreter {

  public static final int WINDOW_ID_NONE = -1;
  private static final int WINDOW_TYPE_NONE = -1;
  private static final int PIC_IN_PIC_DELAY_MS = 300;
  private static final int WINDOW_CHANGE_DELAY_MS = 500;
  private static final int ACCESSIBILITY_OVERLAY_DELAY_MS = 150;

  private final AccessibilityService mService;
  private final boolean mIsSplitScreenModeAvailable;

  // TODO: Extract this to another class, and merge with the same logic in
  // TouchExplorationFormatter.
  private HashMap<Integer, CharSequence> mWindowTitlesMap = new HashMap<>();
  private HashMap<Integer, CharSequence> mWindowToClassName = new HashMap<>();
  private HashMap<Integer, CharSequence> mWindowToPackageName = new HashMap<>();

  private HashSet<Integer> mSystemWindowIdsSet = new HashSet<>();

  /**
   * Assignment of windows to roles. Encapsulated in a data-struct, to allow temporary assignment of
   * roles.
   */
  public static class WindowRoles {
    // Window A: In split screen mode, left (right in RTL) or top window. In full screen mode, the
    // current window.
    public int mWindowIdA = WINDOW_ID_NONE;
    public CharSequence mWindowTitleA;

    // Window B: In split screen mode, right (left in RTL) or bottom window. This must be
    // WINDOW_ID_NONE in full screen mode.
    public int mWindowIdB = WINDOW_ID_NONE;
    public CharSequence mWindowTitleB;

    // Accessibility overlay window
    public int mAccessibilityOverlayWindowId = WINDOW_ID_NONE;
    public CharSequence mAccessibilityOverlayWindowTitle;

    // Picture-in-picture window history.
    public int mPicInPicWindowId = WINDOW_ID_NONE;
    public CharSequence mPicInPicWindowTitle;

    public WindowRoles() {}

    public WindowRoles(WindowRoles oldRoles) {
      mWindowIdA = oldRoles.mWindowIdA;
      mWindowTitleA = oldRoles.mWindowTitleA;
      mWindowIdB = oldRoles.mWindowIdB;
      mWindowTitleB = oldRoles.mWindowTitleB;
      mAccessibilityOverlayWindowId = oldRoles.mAccessibilityOverlayWindowId;
      mAccessibilityOverlayWindowTitle = oldRoles.mAccessibilityOverlayWindowTitle;
      mPicInPicWindowId = oldRoles.mPicInPicWindowId;
      mPicInPicWindowTitle = oldRoles.mPicInPicWindowTitle;
    }

    public void clear() {
      mWindowIdA = WINDOW_ID_NONE;
      mWindowTitleA = null;
      mWindowIdB = WINDOW_ID_NONE;
      mWindowTitleB = null;
      mAccessibilityOverlayWindowId = WINDOW_ID_NONE;
      mAccessibilityOverlayWindowTitle = null;
      mPicInPicWindowId = WINDOW_ID_NONE;
      mPicInPicWindowTitle = null;
    }

    @Override
    public String toString() {
      return String.format(
          "a:%s:%s b:%s:%s accesOverlay:%s:%s picInPic:%s:%s",
          mWindowIdA,
          mWindowTitleA,
          mWindowIdB,
          mWindowTitleB,
          mAccessibilityOverlayWindowId,
          mAccessibilityOverlayWindowTitle,
          mPicInPicWindowId,
          mPicInPicWindowTitle);
    }
  }

  private WindowRoles mWindowRoles = new WindowRoles();
  private WindowRoles mPendingWindowRoles;
  private int mPicInPicLastShownId = WINDOW_ID_NONE; // Last pic-in-pic window that was shown.
  private long mPicInPicDisappearTime = 0; // Last time pic-in-pic was hidden.

  public WindowEventInterpreter(AccessibilityService service) {
    mService = service;
    boolean isArc = FormFactorUtils.getInstance(service).isArc();
    mIsSplitScreenModeAvailable =
        BuildVersionUtils.isAtLeastN() && !FormFactorUtils.getInstance(service).isTv() && !isArc;
  }

  public void clearScreenState() {
    mWindowRoles.clear();
    mPicInPicLastShownId = WINDOW_ID_NONE;
    mPicInPicDisappearTime = 0;
  }

  public CharSequence getWindowTitle(int windowId) {
    // Try to get window title from the map.
    CharSequence windowTitle = mWindowTitlesMap.get(windowId);
    if (windowTitle != null) {
      return windowTitle;
    }

    if (!BuildVersionUtils.isAtLeastN()) {
      return null;
    }

    // Do not try to get system window title from AccessibilityWindowInfo.getTitle, it can
    // return non-translated value.
    if (isSystemWindow(windowId)) {
      return null;
    }

    // Try to get window title from AccessibilityWindowInfo.
    for (AccessibilityWindowInfo window : mService.getWindows()) {
      if (window.getId() == windowId) {
        return window.getTitle();
      }
    }

    return null;
  }

  public boolean isSplitScreenModeAvailable() {
    return mIsSplitScreenModeAvailable;
  }

  public boolean isSplitScreenMode() {
    if (!mIsSplitScreenModeAvailable) {
      return false;
    }

    // TODO: Update this state when receiving a TYPE_WINDOWS_CHANGED event if possible.
    List<AccessibilityWindowInfo> windows = mService.getWindows();
    List<AccessibilityWindowInfo> applicationWindows = new ArrayList<>();
    for (AccessibilityWindowInfo window : windows) {
      if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
        if (window.getParent() == null
            && !AccessibilityWindowInfoUtils.isPictureInPicture(window)) {
          applicationWindows.add(window);
        }
      }
    }

    // We consider user to be in split screen mode if there are two non-parented
    // application windows.
    return applicationWindows.size() == 2;
  }

  /** Step 1: Define listener for delayed window events. */
  public interface WindowEventHandler {
    void handle(EventInterpretation interpretation, EventId eventId);
  }

  /** Step 2: Add window event listener. */
  public void addListener(WindowEventHandler listener) {
    mListeners.add(listener);
  }

  private List<WindowEventHandler> mListeners = new ArrayList<>();

  /** Step 3: Extract data from window event and related APIs. */
  public void interpret(AccessibilityEvent event, EventId eventId) {
    int eventType = event.getEventType();
    if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      return;
    }

    log(
        "interpret() event type=%s time=%s",
        AccessibilityEventUtils.typeToString(event.getEventType()), event.getEventTime());
    log("interpret() mWindowRoles=%s", mWindowRoles);

    // Create event interpretation.
    EventInterpretation interpretation = new EventInterpretation();
    interpretation.setEventType(event.getEventType());
    interpretation.setOriginalEvent(true);
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      interpretation.setWindowIdFromEvent(getWindowId(event));
    }

    // Collect old window information into interpretation.
    setOldWindowInterpretation(
        mWindowRoles.mWindowIdA, mWindowRoles.mWindowTitleA, interpretation.getWindowA());
    setOldWindowInterpretation(
        mWindowRoles.mWindowIdB, mWindowRoles.mWindowTitleB, interpretation.getWindowB());
    setOldWindowInterpretation(
        mWindowRoles.mAccessibilityOverlayWindowId,
        mWindowRoles.mAccessibilityOverlayWindowTitle,
        interpretation.getAccessibilityOverlay());
    setOldWindowInterpretation(
        mWindowRoles.mPicInPicWindowId,
        mWindowRoles.mPicInPicWindowTitle,
        interpretation.getPicInPic());

    // Update stored windows.
    updateWindowTitlesMap(event, interpretation);

    // Map windows to roles, detect window role changes.
    WindowRoles latestRoles = (mPendingWindowRoles == null) ? mWindowRoles : mPendingWindowRoles;
    WindowRoles newWindowRoles = new WindowRoles(latestRoles);
    updateScreenState(interpretation, mService, mIsSplitScreenModeAvailable, newWindowRoles);
    setWindowTitles(newWindowRoles);
    detectWindowChanges(newWindowRoles, interpretation);

    // Detect picture-in-picture window change, ruling out temporary disappear & reappear.
    // TODO: If picture-in-picture does even more transitional behavior than temporarily
    // disappearing, then delay pic-in-pic change detection.
    boolean picInPicDisappearedRecently =
        (event.getEventTime() < (mPicInPicDisappearTime + PIC_IN_PIC_DELAY_MS));
    boolean picInPicTemporarilyHidden =
        (mPicInPicLastShownId == newWindowRoles.mPicInPicWindowId && picInPicDisappearedRecently);
    boolean picInPicChanged =
        (!picInPicTemporarilyHidden && interpretation.getPicInPic().idOrTitleChanged());
    // Update picture-in-picture history.
    if (newWindowRoles.mPicInPicWindowId == WINDOW_ID_NONE) {
      if (interpretation.getPicInPic().getOldId() != WINDOW_ID_NONE) {
        mPicInPicDisappearTime = event.getEventTime();
      }
    } else {
      mPicInPicLastShownId = newWindowRoles.mPicInPicWindowId;
    }
    interpretation.setPicInPicChanged(picInPicChanged);

    // Check whether windows are stable.
    long delayMs = 0;
    if (interpretation.getMainWindowsChanged()) {
      if (interpretation.getAccessibilityOverlay().getId() != WINDOW_ID_NONE) {
        delayMs = ACCESSIBILITY_OVERLAY_DELAY_MS;
      } else if (interpretation.getWindowB().getId() == WINDOW_ID_NONE) {
        // Single window.  Stable if null title, or alert dialog, or split-screen unavailable.
        if (interpretation.getWindowA().getTitle() != null) {
          if (isSplitScreenModeAvailable() && !interpretation.getWindowA().isAlertDialog()) {
            delayMs = WINDOW_CHANGE_DELAY_MS;
          }
        }
      } else {
        // 2 windows showing.  Stable if not split-screen.
        if (isSplitScreenModeAvailable()
            && !interpretation.getWindowA().isAlertDialog()
            && !interpretation.getWindowB().isAlertDialog()) {
          delayMs = WINDOW_CHANGE_DELAY_MS;
        }
      }
    }
    interpretation.setWindowsStable(delayMs == 0);
    log("interpret() delayMs=%s interpretation=%s", delayMs, interpretation);

    // Stop delayed interpretation efforts, since new non-empty interpretation is coming.
    mWindowEventDelayer.removeMessages();
    // Send an immediate window event interpretation, possibly with unstable windows.
    notifyInterpretationListeners(interpretation, eventId);
    if (delayMs == 0) {
      // If no delay needed to stablize windows... keep stable window role assignments.
      mWindowRoles = newWindowRoles;
      mPendingWindowRoles = null;
    } else {
      // Delay updating window roles, to find non-transitional role changes. But accumulate delayed
      // role information in mPendingWindowRoles, to allow delayed interpretation to have up-to-date
      // window info.
      //
      // Saving all role updates breaks announcing "settings" and "home", because 2nd
      // WINDOWS_CHANGED not different roles than 1st. Discarding role updates is preventing
      // announcing home screen, because WINDOWS_CHANGED then WINDOW_STATE_CHANGED never updates
      // roles. Base new role updates on pending roles, to allow WINDOW_STATE_CHANGED to know that
      // window id changed in preceding WINDOWS_CHANGED.
      mPendingWindowRoles = newWindowRoles;

      // Delay and re-interpret window event later.
      EventIdAnd<EventInterpretation> interpretationAndEventId =
          new EventIdAnd<>(interpretation, eventId);
      mWindowEventDelayer.delay(delayMs, interpretationAndEventId);
    }
  }

  /** Step 4: Delay event interpretation. */
  private DelayHandler<EventIdAnd<EventInterpretation>> mWindowEventDelayer =
      new DelayHandler<EventIdAnd<EventInterpretation>>() {
        @Override
        public void handle(EventIdAnd<EventInterpretation> eventIdAndInterpretation) {
          delayedInterpret(eventIdAndInterpretation.object, eventIdAndInterpretation.eventId);
        }
      };

  /** Step 5: After delay from "unstable" window events, re-run window interpretation. */
  public void delayedInterpret(EventInterpretation interpretation, EventId eventId) {
    interpretation.setOriginalEvent(false);
    interpretation.setWindowsStable(true);

    // Map windows to roles, detect window role changes.
    WindowRoles latestRoles = (mPendingWindowRoles == null) ? mWindowRoles : mPendingWindowRoles;
    WindowRoles newWindowRoles = new WindowRoles(latestRoles);
    updateScreenState(interpretation, mService, mIsSplitScreenModeAvailable, newWindowRoles);
    setWindowTitles(newWindowRoles);
    detectWindowChanges(newWindowRoles, interpretation);
    log("delayedInterpret() interpretation=" + interpretation);

    // Keep stable window role assignments.
    mWindowRoles = newWindowRoles;
    mPendingWindowRoles = null;

    notifyInterpretationListeners(interpretation, eventId);
  }

  /** Send event interpretation to each listener. */
  private void notifyInterpretationListeners(EventInterpretation interpretation, EventId eventId) {
    for (WindowEventHandler listener : mListeners) {
      listener.handle(interpretation, eventId);
    }
  }

  /** Collect data about window into interpretation. */
  private void setOldWindowInterpretation(
      int oldWindowId, CharSequence oldWindowTitle, WindowInterpretation interpretation) {
    interpretation.setOldId(oldWindowId);
    interpretation.setOldTitle(oldWindowTitle);
  }

  private void setNewWindowInterpretation(int windowId, WindowInterpretation interpretation) {
    interpretation.setId(windowId);
    interpretation.setTitle(getWindowTitle(windowId));
    interpretation.setTitleForFeedback(getWindowTitleForFeedback(windowId));
    interpretation.setAlertDialog(isAlertDialog(windowId));
  }

  private boolean isAlertDialog(int windowId) {
    CharSequence className = mWindowToClassName.get(windowId);
    return className != null && className.equals("android.app.AlertDialog");
  }

  private void updateWindowTitlesMap(AccessibilityEvent event, EventInterpretation interpretation) {

    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        {
          // If split screen mode is NOT available, we only need to care single window.
          if (!mIsSplitScreenModeAvailable) {
            mWindowTitlesMap.clear();
          }

          int windowId = getWindowId(event);
          boolean shouldAnnounceEvent = shouldAnnounceEvent(event, windowId);
          CharSequence title =
              getWindowTitleFromEvent(event, shouldAnnounceEvent /* useContentDescription */);
          if (title != null) {
            if (shouldAnnounceEvent) {
              // When software keyboard is shown or hidden, TYPE_WINDOW_STATE_CHANGED
              // is dispatched with text describing the visibility of the keyboard.
              interpretation.setAnnouncement(title);
            } else {
              mWindowTitlesMap.put(windowId, title);

              if (getWindowType(event) == AccessibilityWindowInfo.TYPE_SYSTEM) {
                mSystemWindowIdsSet.add(windowId);
              }

              CharSequence eventWindowClassName = event.getClassName();
              mWindowToClassName.put(windowId, eventWindowClassName);
              mWindowToPackageName.put(windowId, event.getPackageName());
            }
          }
        }
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        {
          HashSet<Integer> windowIdsToBeRemoved = new HashSet<Integer>(mWindowTitlesMap.keySet());
          List<AccessibilityWindowInfo> windows = mService.getWindows();
          for (AccessibilityWindowInfo window : windows) {
            int windowId = window.getId();
            if (BuildVersionUtils.isAtLeastN()) {
              CharSequence title = window.getTitle();
              if (title != null) {
                mWindowTitlesMap.put(windowId, title);
              }
            }
            windowIdsToBeRemoved.remove(windowId);
          }
          for (Integer windowId : windowIdsToBeRemoved) {
            mWindowTitlesMap.remove(windowId);
            mSystemWindowIdsSet.remove(windowId);
            mWindowToClassName.remove(windowId);
            mWindowToPackageName.remove(windowId);
          }
        }
        break;
      default: // fall out
    }

    log("updateWindowTitlesMap() mWindowTitlesMap=" + mWindowTitlesMap);
  }

  private CharSequence getWindowTitleFromEvent(
      AccessibilityEvent event, boolean useContentDescription) {
    if (useContentDescription && !TextUtils.isEmpty(event.getContentDescription())) {
      return event.getContentDescription();
    }

    List<CharSequence> titles = event.getText();
    if (titles.size() > 0) {
      return titles.get(0);
    }

    return null;
  }

  /**
   * Uses a heuristic to guess whether an event should be announced. Any event that comes from an
   * IME, or an invisible window is considered an announcement.
   */
  private boolean shouldAnnounceEvent(AccessibilityEvent event, int windowId) {
    // Assume window ID of -1 is the keyboard.
    if (windowId == WINDOW_ID_NONE) {
      return true;
    }
    // TODO This needs to be revisited and should be handled in a better way.
    /**
     * Currently time picker and date picker are specifically used to change the flow of execution
     * for O devices and announce the title. The execution flow in processorScreen needs to be
     * revisited so that it works in the similar fashion for pre-O and O devices. Explicit checks
     * have been added for Volume slider, popup windows in O to get the intended behaviour.
     */
    if (BuildVersionUtils.isAtLeastO()) {
      @RoleName int eventSourceRole = Role.getSourceRole(event);
      if (eventSourceRole == Role.ROLE_DATE_PICKER_DIALOG
          || eventSourceRole == Role.ROLE_TIME_PICKER_DIALOG) {
        return true;
      }
    }
    return AccessibilityEventUtils.isNonMainWindowEvent(event);
  }

  /** Modifies window IDs in windowRoles. */
  private static void updateScreenState(
      EventInterpretation interpretation,
      AccessibilityService service,
      boolean splitScreenModeAvailable,
      WindowRoles windowRoles) {

    log("updateScreenState() interpretation=" + interpretation);

    switch (interpretation.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        // Do nothing if split screen mode is available since it can be covered by
        // TYPE_WINDOWS_CHANGED events.
        // TODO: Stop treating TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOWS_CHANGED
        // differently, for simplicity and reliability.  Incrementally update all roles for all
        // events, based on AccessibilityService.getWindows()
        if (splitScreenModeAvailable) {
          return;
        }
        windowRoles.mWindowIdA = interpretation.getWindowIdFromEvent();
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        // Do nothing if split screen mode is NOT available since it can be covered by
        // TYPE_WINDOW_STATE_CHANGED events.
        if (!splitScreenModeAvailable) {
          return;
        }

        ArrayList<AccessibilityWindowInfo> applicationWindows = new ArrayList<>();
        ArrayList<AccessibilityWindowInfo> systemWindows = new ArrayList<>();
        ArrayList<AccessibilityWindowInfo> accessibilityOverlayWindows = new ArrayList<>();
        ArrayList<AccessibilityWindowInfo> picInPicWindows = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = service.getWindows();

        // If there are no windows available, clear the cached IDs.
        if (windows.isEmpty()) {
          windowRoles.clear();
          return;
        }

        for (int i = 0; i < windows.size(); i++) {
          AccessibilityWindowInfo window = windows.get(i);
          if (AccessibilityWindowInfoUtils.isPictureInPicture(window)) {
            picInPicWindows.add(window);
            continue;
          }
          switch (window.getType()) {
            case AccessibilityWindowInfo.TYPE_APPLICATION:
              if (window.getParent() == null) {
                applicationWindows.add(window);
              }
              break;
            case AccessibilityWindowInfo.TYPE_SYSTEM:
              systemWindows.add(window);
              break;
            case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY:
              accessibilityOverlayWindows.add(window);
              break;
            default: // fall out
          }
        }

        if (accessibilityOverlayWindows.size() == windows.size()) {
          // TODO: investigate whether there is a case where we have more than one
          // accessibility overlay, and add a logic for it if there is.
          windowRoles.mAccessibilityOverlayWindowId = accessibilityOverlayWindows.get(0).getId();
          return;
        }

        windowRoles.mPicInPicWindowId =
            (picInPicWindows.size() == 0) ? WINDOW_ID_NONE : picInPicWindows.get(0).getId();

        windowRoles.mAccessibilityOverlayWindowId = WINDOW_ID_NONE;

        if (applicationWindows.size() == 0) {
          windowRoles.mWindowIdA = WINDOW_ID_NONE;
          windowRoles.mWindowIdB = WINDOW_ID_NONE;

          // If there is no application window but a system window, consider it as a
          // current window. This logic handles notification shade and lock screen.
          if (systemWindows.size() > 0) {
            Collections.sort(
                systemWindows,
                new WindowManager.WindowPositionComparator(
                    WindowManager.isScreenLayoutRTL(service)));

            windowRoles.mWindowIdA = systemWindows.get(0).getId();
          }
        } else if (applicationWindows.size() == 1) {
          windowRoles.mWindowIdA = applicationWindows.get(0).getId();
          windowRoles.mWindowIdB = WINDOW_ID_NONE;
        } else if (applicationWindows.size() == 2
            && !hasOverlap(applicationWindows.get(0), applicationWindows.get(1))) {
          Collections.sort(
              applicationWindows,
              new WindowManager.WindowPositionComparator(WindowManager.isScreenLayoutRTL(service)));

          windowRoles.mWindowIdA = applicationWindows.get(0).getId();
          windowRoles.mWindowIdB = applicationWindows.get(1).getId();
        } else {
          // If there are more than 2 windows, report the active window as the current
          // window.
          for (AccessibilityWindowInfo applicationWindow : applicationWindows) {
            if (applicationWindow.isActive()) {
              windowRoles.mWindowIdA = applicationWindow.getId();
              windowRoles.mWindowIdB = WINDOW_ID_NONE;
              return;
            }
          }
        }
        break;
      default: // fall out
    }
  }

  private static boolean hasOverlap(
      AccessibilityWindowInfo windowA, AccessibilityWindowInfo windowB) {
    AccessibilityNodeInfo rootA = windowA.getRoot();
    AccessibilityNodeInfo rootB = windowB.getRoot();

    if (rootA == null || rootB == null) {
      AccessibilityNodeInfoUtils.recycleNodes(rootA, rootB);
      return false;
    }

    // Use root nodes to get bounds of windows. AccessibilityWindowInfo.getBoundsInScreen sometimes
    // returns incorrect bounds.
    Rect rectA = new Rect();
    Rect rectB = new Rect();
    rootA.getBoundsInScreen(rectA);
    rootB.getBoundsInScreen(rectB);

    AccessibilityNodeInfoUtils.recycleNodes(rootA, rootB);

    return Rect.intersects(rectA, rectB);
  }

  private boolean isSystemWindow(int windowId) {
    if (mSystemWindowIdsSet.contains(windowId)) {
      return true;
    }

    if (!mIsSplitScreenModeAvailable) {
      return false;
    }

    for (AccessibilityWindowInfo window : mService.getWindows()) {
      if (window.getId() == windowId && window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
        return true;
      }
    }

    return false;
  }

  /** Updates window titles in windowRoles. */
  private void setWindowTitles(WindowRoles windowRoles) {
    windowRoles.mWindowTitleA = getWindowTitle(windowRoles.mWindowIdA);
    windowRoles.mWindowTitleB = getWindowTitle(windowRoles.mWindowIdB);
    windowRoles.mAccessibilityOverlayWindowTitle =
        getWindowTitle(windowRoles.mAccessibilityOverlayWindowId);
    windowRoles.mPicInPicWindowTitle = getWindowTitle(windowRoles.mPicInPicWindowId);
  }

  /** Detect window role changes, and turn on flags in interpretation. */
  private void detectWindowChanges(WindowRoles roles, EventInterpretation interpretation) {
    log("detectWindowChanges() roles=" + roles);

    // Collect new window information into interpretation.
    setNewWindowInterpretation(roles.mWindowIdA, interpretation.getWindowA());
    setNewWindowInterpretation(roles.mWindowIdB, interpretation.getWindowB());
    setNewWindowInterpretation(
        roles.mAccessibilityOverlayWindowId, interpretation.getAccessibilityOverlay());
    setNewWindowInterpretation(roles.mPicInPicWindowId, interpretation.getPicInPic());

    // If there is no screen update, do not provide spoken feedback.
    boolean mainWindowsChanged =
        (interpretation.getWindowA().idOrTitleChanged()
            || interpretation.getWindowB().idOrTitleChanged()
            || interpretation.getAccessibilityOverlay().idOrTitleChanged());
    interpretation.setMainWindowsChanged(mainWindowsChanged);
  }

  private CharSequence getWindowTitleForFeedback(int windowId) {
    CharSequence title = getWindowTitle(windowId);

    // Try to fall back to application label if window title is not available.
    if (title == null) {
      CharSequence packageName = mWindowToPackageName.get(windowId);

      // Try to get package name from accessibility window info if it's not in the map.
      if (packageName == null) {
        for (AccessibilityWindowInfo window : mService.getWindows()) {
          if (window.getId() == windowId) {
            AccessibilityNodeInfo rootNode = window.getRoot();
            if (rootNode != null) {
              packageName = rootNode.getPackageName();
              rootNode.recycle();
            }
          }
        }
      }

      if (packageName != null) {
        getApplicationLabel(packageName);
      }
    }

    // TODO: Do not announce "untitled" for any types of window in single window mode.
    if (title == null) {
      for (AccessibilityWindowInfo window : mService.getWindows()) {
        if ((window.getId() == windowId)
            && (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)) {
          // Return empty title directly without attaching the "Alert" prefix.
          return "";
        }
      }
    }

    if (title == null) {
      title = mService.getString(R.string.untitled_window);
    }

    if (isAlertDialog(windowId)) {
      title = mService.getString(R.string.template_alert_dialog_template, title);
    }

    return title;
  }

  private CharSequence getApplicationLabel(CharSequence packageName) {
    PackageManager packageManager = mService.getPackageManager();
    if (packageManager == null) {
      return null;
    }

    ApplicationInfo applicationInfo;
    try {
      applicationInfo = packageManager.getApplicationInfo(packageName.toString(), 0 /* no flag */);
    } catch (PackageManager.NameNotFoundException exception) {
      return null;
    }

    return packageManager.getApplicationLabel(applicationInfo);
  }

  private static int getWindowId(AccessibilityEvent event) {
    AccessibilityNodeInfo node = event.getSource();
    if (node == null) {
      return WINDOW_ID_NONE;
    }

    int windowId = node.getWindowId();
    node.recycle();
    return windowId;
  }

  private static int getWindowType(AccessibilityEvent event) {
    if (event == null) {
      return WINDOW_TYPE_NONE;
    }

    AccessibilityNodeInfo nodeInfo = event.getSource();
    if (nodeInfo == null) {
      return WINDOW_TYPE_NONE;
    }

    AccessibilityNodeInfoCompat nodeInfoCompat = AccessibilityNodeInfoUtils.toCompat(nodeInfo);
    AccessibilityWindowInfoCompat windowInfoCompat = nodeInfoCompat.getWindow();
    if (windowInfoCompat == null) {
      nodeInfoCompat.recycle();
      return WINDOW_TYPE_NONE;
    }

    int windowType = windowInfoCompat.getType();
    windowInfoCompat.recycle();
    nodeInfoCompat.recycle();

    return windowType;
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Inner classes: event interpretation

  /** Fully interpreted and analyzed window-change event description. */
  public static class EventInterpretation extends ReadOnly {
    private int mWindowIdFromEvent = WINDOW_ID_NONE;
    private CharSequence mAnnouncement;
    private WindowInterpretation mWindowA = new WindowInterpretation();
    private WindowInterpretation mWindowB = new WindowInterpretation();
    private WindowInterpretation mAccessibilityOverlay = new WindowInterpretation();
    private WindowInterpretation mPicInPic = new WindowInterpretation();
    private boolean mMainWindowsChanged = false;
    private boolean mPicInPicChanged = false;
    private boolean mWindowsStable = false;
    private boolean mOriginalEvent = false;
    private int mEventType = 0;

    @Override
    public void setReadOnly() {
      super.setReadOnly();
      mWindowA.setReadOnly();
      mWindowB.setReadOnly();
      mAccessibilityOverlay.setReadOnly();
      mPicInPic.setReadOnly();
    }

    public void setWindowIdFromEvent(int id) {
      checkIsWritable();
      mWindowIdFromEvent = id;
    }

    public int getWindowIdFromEvent() {
      return mWindowIdFromEvent;
    }

    public void setAnnouncement(CharSequence announcement) {
      checkIsWritable();
      mAnnouncement = announcement;
    }

    public CharSequence getAnnouncement() {
      return mAnnouncement;
    }

    public WindowInterpretation getWindowA() {
      return mWindowA;
    }

    public WindowInterpretation getWindowB() {
      return mWindowB;
    }

    public WindowInterpretation getAccessibilityOverlay() {
      return mAccessibilityOverlay;
    }

    public WindowInterpretation getPicInPic() {
      return mPicInPic;
    }

    public void setMainWindowsChanged(boolean changed) {
      checkIsWritable();
      mMainWindowsChanged = changed;
    }

    public boolean getMainWindowsChanged() {
      return mMainWindowsChanged;
    }

    public void setPicInPicChanged(boolean changed) {
      checkIsWritable();
      mPicInPicChanged = changed;
    }

    public boolean getPicInPicChanged() {
      return mPicInPicChanged;
    }

    public void setWindowsStable(boolean stable) {
      checkIsWritable();
      mWindowsStable = stable;
    }

    public boolean areWindowsStable() {
      return mWindowsStable;
    }

    public void setOriginalEvent(boolean original) {
      checkIsWritable();
      mOriginalEvent = original;
    }

    public boolean isOriginalEvent() {
      return mOriginalEvent;
    }

    public void setEventType(int eventType) {
      checkIsWritable();
      mEventType = eventType;
    }

    public int getEventType() {
      return mEventType;
    }

    // TODO: Use standard field logging functions in StringBuilderUtils.
    @Override
    public String toString() {
      return String.format(
              "window A:%s, window B:%s, a11y overlay:%s, pic-in-pic:%s",
              mWindowA, mWindowB, mAccessibilityOverlay, mPicInPic)
          + (mWindowIdFromEvent == WINDOW_ID_NONE
              ? ""
              : ", WindowIdFromEvent " + mWindowIdFromEvent)
          + (mMainWindowsChanged ? ", MainWindowsChanged" : "")
          + (mPicInPicChanged ? ", PicInPicChanged" : "")
          + (mWindowsStable ? ", WindowsStable" : "")
          + (mOriginalEvent ? ", OriginalEvent" : "")
          + (mEventType == 0 ? "" : ", " + AccessibilityEventUtils.typeToString(mEventType))
          + (mAnnouncement == null ? "" : ", Annoucement " + formatString(mAnnouncement));
    }
  }

  /** Fully interpreted and analyzed window-change event description about one window. */
  public static class WindowInterpretation extends ReadOnly {
    private int mId = WINDOW_ID_NONE;
    private CharSequence mTitle;
    private CharSequence mTitleForFeedback;
    private boolean mIsAlertDialog = false;
    private int mOldId = WINDOW_ID_NONE;
    private CharSequence mOldTitle;

    public void setId(int id) {
      checkIsWritable();
      mId = id;
    }

    public boolean idOrTitleChanged() {
      return (mOldId != mId) || !TextUtils.equals(mOldTitle, mTitle);
    }

    public int getId() {
      return mId;
    }

    public void setTitle(CharSequence title) {
      checkIsWritable();
      mTitle = title;
    }

    public CharSequence getTitle() {
      return mTitle;
    }

    public void setTitleForFeedback(CharSequence title) {
      checkIsWritable();
      mTitleForFeedback = title;
    }

    public CharSequence getTitleForFeedback() {
      return mTitleForFeedback;
    }

    public void setAlertDialog(boolean isAlert) {
      checkIsWritable();
      mIsAlertDialog = isAlert;
    }

    public boolean isAlertDialog() {
      return mIsAlertDialog;
    }

    public void setOldId(int oldId) {
      checkIsWritable();
      mOldId = oldId;
    }

    public int getOldId() {
      return mOldId;
    }

    public void setOldTitle(CharSequence oldTitle) {
      checkIsWritable();
      mOldTitle = oldTitle;
    }

    public CharSequence getOldTitle() {
      return mOldTitle;
    }

    @Override
    public String toString() {
      return (mId == WINDOW_ID_NONE ? "" : "" + mId)
          + (mTitle == null ? "" : " " + formatString(mTitle))
          + (mTitleForFeedback == null ? "" : " " + formatString(mTitleForFeedback))
          + (mIsAlertDialog ? " alert" : "")
          + (mOldId == WINDOW_ID_NONE && mOldTitle == null ? "" : " old")
          + (mOldId == WINDOW_ID_NONE ? "" : " " + mOldId)
          + (mOldTitle == null ? "" : " " + formatString(mOldTitle));
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Logging functions

  private static CharSequence formatString(CharSequence text) {
    return (text == null) ? "null" : String.format("\"%s\"", text);
  }

  private static void log(String format, Object... args) {
    LogUtils.log(WindowEventInterpreter.class, Log.VERBOSE, format, args);
  }
}
