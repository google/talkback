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

import static com.google.android.accessibility.utils.AccessibilityEventUtils.WINDOW_ID_NONE;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Translates Accessibility Window Events into more usable event description.
 *
 * <p>The main difficulties are floods of transitional window state events, many of which have to be
 * composed before we know the net window-state change. To solve for this, when a transitional
 * window event is received, interpretation is delayed, and window changes are buffered in
 * pendingWindowRoles until the delayed interpretation is complete.
 *
 * <p>Android P changes:
 *
 * <ul>
 *   <li>No longer get TYPE_WINDOW_STATE_CHANGED announcement when volume controls are hidden.
 *   <li>More windows returned by getWindows(), including multiple system windows.
 * </ul>
 *
 * TODO: Starting from Android P, collect all windows into WindowRoles, regardless of
 * window type, and announce all new windows?
 */
public class WindowEventInterpreter {

  private static final String TAG = "WindowEventInterpreter";

  private static final int WINDOW_TYPE_NONE = -1;
  private static final int PIC_IN_PIC_DELAY_MS = 300;
  public static final int WINDOW_CHANGE_DELAY_MS = 550;
  public static final int WINDOW_CHANGE_DELAY_NO_ANIMATION_MS = 200;
  private static final int ACCESSIBILITY_OVERLAY_DELAY_MS = 150;
  // TODO: Shorten delay if animations are off.

  private static final int WINDOWS_CHANGE_TYPES_USED =
      AccessibilityEvent.WINDOWS_CHANGE_ADDED
          | AccessibilityEvent.WINDOWS_CHANGE_TITLE
          | AccessibilityEvent.WINDOWS_CHANGE_REMOVED
          | AccessibilityEvent.WINDOWS_CHANGE_PIP;

  private final AccessibilityService service;
  private final boolean isSplitScreenModeAvailable;

  // TODO: Extract this to another class, and merge with the same logic in
  // TouchExplorationFormatter.
  private final HashMap<Integer, CharSequence> windowTitlesMap = new HashMap<>();
  private final HashMap<Integer, Integer> windowToRole = new HashMap<>();
  private final HashMap<Integer, CharSequence> windowToPackageName = new HashMap<>();

  private final HashSet<Integer> systemWindowIdsSet = new HashSet<>();

  /**
   * Assignment of windows to roles. Encapsulated in a data-struct, to allow temporary assignment of
   * roles.
   */
  public static class WindowRoles {
    // Window A: In split screen mode, left (right in RTL) or top window. In full screen mode, the
    // current window.
    public int windowIdA = WINDOW_ID_NONE;
    public CharSequence windowTitleA;

    // Window B: In split screen mode, right (left in RTL) or bottom window. This must be
    // WINDOW_ID_NONE in full screen mode.
    public int windowIdB = WINDOW_ID_NONE;
    public CharSequence windowTitleB;

    // Accessibility overlay window
    public int accessibilityOverlayWindowId = WINDOW_ID_NONE;
    public CharSequence accessibilityOverlayWindowTitle;

    // Picture-in-picture window history.
    public int picInPicWindowId = WINDOW_ID_NONE;
    public CharSequence picInPicWindowTitle;

    public WindowRoles() {}

    public WindowRoles(WindowRoles oldRoles) {
      windowIdA = oldRoles.windowIdA;
      windowTitleA = oldRoles.windowTitleA;
      windowIdB = oldRoles.windowIdB;
      windowTitleB = oldRoles.windowTitleB;
      accessibilityOverlayWindowId = oldRoles.accessibilityOverlayWindowId;
      accessibilityOverlayWindowTitle = oldRoles.accessibilityOverlayWindowTitle;
      picInPicWindowId = oldRoles.picInPicWindowId;
      picInPicWindowTitle = oldRoles.picInPicWindowTitle;
    }

    public void clear() {
      windowIdA = WINDOW_ID_NONE;
      windowTitleA = null;
      windowIdB = WINDOW_ID_NONE;
      windowTitleB = null;
      accessibilityOverlayWindowId = WINDOW_ID_NONE;
      accessibilityOverlayWindowTitle = null;
      picInPicWindowId = WINDOW_ID_NONE;
      picInPicWindowTitle = null;
    }

    @Override
    public String toString() {
      return String.format(
          "a:%s:%s b:%s:%s accessOverlay:%s:%s picInPic:%s:%s",
          windowIdA,
          windowTitleA,
          windowIdB,
          windowTitleB,
          accessibilityOverlayWindowId,
          accessibilityOverlayWindowTitle,
          picInPicWindowId,
          picInPicWindowTitle);
    }
  }

  private WindowRoles windowRoles = new WindowRoles();
  private WindowRoles pendingWindowRoles;
  private int picInPicLastShownId = WINDOW_ID_NONE; // Last pic-in-pic window that was shown.
  private long picInPicDisappearTime = 0; // Last time pic-in-pic was hidden.

  /** Preference to reduce delay before considering windows stable. */
  private boolean reduceDelayPref = false;

  public WindowEventInterpreter(AccessibilityService service) {
    this.service = service;
    boolean isArc = FeatureSupport.isArc();
    isSplitScreenModeAvailable =
        BuildVersionUtils.isAtLeastN() && !FeatureSupport.isTv(service) && !isArc;
  }

  public void setReduceDelayPref(boolean reduceDelayPref) {
    this.reduceDelayPref = reduceDelayPref;
  }

  public void clearScreenState() {
    windowRoles.clear();
    picInPicLastShownId = WINDOW_ID_NONE;
    picInPicDisappearTime = 0;
  }

  public CharSequence getWindowTitle(int windowId) {
    // Try to get window title from the map.
    CharSequence windowTitle = windowTitlesMap.get(windowId);
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
    for (AccessibilityWindowInfo window : AccessibilityServiceCompatUtils.getWindows(service)) {
      if (window.getId() == windowId) {
        return window.getTitle();
      }
    }

    return null;
  }

  public boolean isSplitScreenModeAvailable() {
    return isSplitScreenModeAvailable;
  }

  public boolean isSplitScreenMode() {
    if (!isSplitScreenModeAvailable) {
      return false;
    }

    // TODO: Update this state when receiving a TYPE_WINDOWS_CHANGED event if possible.
    List<AccessibilityWindowInfo> windows = AccessibilityServiceCompatUtils.getWindows(service);
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
    void handle(EventInterpretation interpretation, @Nullable EventId eventId);
  }

  /** Step 2: Add window event listener. */
  public void addListener(WindowEventHandler listener) {
    listeners.add(listener);
  }

  private final List<WindowEventHandler> listeners = new ArrayList<>();

  /** Step 3: Extract data from window event and related APIs. */
  @TargetApi(Build.VERSION_CODES.P)
  public void interpret(AccessibilityEvent event, @Nullable EventId eventId) {
    interpret(event, eventId, true);
  }

  @TargetApi(Build.VERSION_CODES.P)
  public void interpret(AccessibilityEvent event, @Nullable EventId eventId, boolean allowEvent) {
    int eventType = event.getEventType();
    if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      return;
    }
    // On android P, only use window events with change-types that set window title or announce.
    if (BuildVersionUtils.isAtLeastP()) {
      if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
          && (event.getWindowChanges() & WINDOWS_CHANGE_TYPES_USED) == 0) {
        return;
      }
      if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
        int changeTypes = event.getContentChangeTypes();
        if ((changeTypes & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) != 0) {
          return;
        }
      }
    }

    log(
        "interpret() event type=%s time=%s allowEvent=%s",
        AccessibilityEventUtils.typeToString(event.getEventType()),
        event.getEventTime(),
        allowEvent);
    log("interpret() windowRoles=%s", windowRoles);

    // Create event interpretation.
    EventInterpretation interpretation = new EventInterpretation();
    interpretation.setEventType(event.getEventType());
    interpretation.setOriginalEvent(true);
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      interpretation.setWindowIdFromEvent(AccessibilityEventUtils.getWindowId(event));
      if (BuildVersionUtils.isAtLeastP()) {
        interpretation.setChangeTypes(event.getContentChangeTypes());
      }
    } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      if (BuildVersionUtils.isAtLeastP()) {
        interpretation.setChangeTypes(event.getWindowChanges());
      }
    }

    // Collect old window information into interpretation.
    setOldWindowInterpretation(
        windowRoles.windowIdA, windowRoles.windowTitleA, interpretation.getWindowA());
    setOldWindowInterpretation(
        windowRoles.windowIdB, windowRoles.windowTitleB, interpretation.getWindowB());
    setOldWindowInterpretation(
        windowRoles.accessibilityOverlayWindowId,
        windowRoles.accessibilityOverlayWindowTitle,
        interpretation.getAccessibilityOverlay());
    setOldWindowInterpretation(
        windowRoles.picInPicWindowId,
        windowRoles.picInPicWindowTitle,
        interpretation.getPicInPic());

    // Update stored windows.
    updateWindowTitlesMap(event, interpretation);

    // Map windows to roles, detect window role changes.
    WindowRoles latestRoles = (pendingWindowRoles == null) ? windowRoles : pendingWindowRoles;
    WindowRoles newWindowRoles = new WindowRoles(latestRoles);
    updateWindowRoles(interpretation, service, newWindowRoles);
    setWindowTitles(newWindowRoles);
    detectWindowChanges(newWindowRoles, interpretation);

    // Detect picture-in-picture window change, ruling out temporary disappear & reappear.
    // TODO: If picture-in-picture does even more transitional behavior than temporarily
    // disappearing, then delay pic-in-pic change detection.
    boolean picInPicDisappearedRecently =
        (event.getEventTime() < (picInPicDisappearTime + PIC_IN_PIC_DELAY_MS));
    boolean picInPicTemporarilyHidden =
        (picInPicLastShownId == newWindowRoles.picInPicWindowId && picInPicDisappearedRecently);
    boolean picInPicChanged =
        (!picInPicTemporarilyHidden && interpretation.getPicInPic().idOrTitleChanged());
    // Update picture-in-picture history.
    if (newWindowRoles.picInPicWindowId == WINDOW_ID_NONE) {
      if (interpretation.getPicInPic().getOldId() != WINDOW_ID_NONE) {
        picInPicDisappearTime = event.getEventTime();
      }
    } else {
      picInPicLastShownId = newWindowRoles.picInPicWindowId;
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
            delayMs = getWindowTransitionDelayMs();
          }
        }
      } else {
        // 2 windows showing.  Stable if not split-screen.
        if (isSplitScreenModeAvailable()
            && !interpretation.getWindowA().isAlertDialog()
            && !interpretation.getWindowB().isAlertDialog()) {
          delayMs = getWindowTransitionDelayMs();
        }
      }
    }
    interpretation.setWindowsStable(delayMs == 0);
    log("interpret() delayMs=%s interpretation=%s", delayMs, interpretation);
    interpretation.setAllowAnnounce(allowEvent);

    // Stop delayed interpretation efforts, since new non-empty interpretation is coming.
    windowEventDelayer.removeMessages();
    // Send an immediate window event interpretation, possibly with unstable windows.
    notifyInterpretationListeners(interpretation, eventId);
    if (delayMs == 0) {
      // If no delay needed to stablize windows... keep stable window role assignments.
      windowRoles = newWindowRoles;
      pendingWindowRoles = null;
    } else {
      // Delay updating window roles, to find non-transitional role changes. But accumulate delayed
      // role information in pendingWindowRoles, to allow delayed interpretation to have up-to-date
      // window info.
      //
      // Saving all role updates breaks announcing "settings" and "home", because 2nd
      // WINDOWS_CHANGED not different roles than 1st. Discarding role updates is preventing
      // announcing home screen, because WINDOWS_CHANGED then WINDOW_STATE_CHANGED never updates
      // roles. Base new role updates on pending roles, to allow WINDOW_STATE_CHANGED to know that
      // window id changed in preceding WINDOWS_CHANGED.
      pendingWindowRoles = newWindowRoles;

      EventIdAnd<EventInterpretation> interpretationAndEventId =
          new EventIdAnd<>(interpretation, eventId);
      windowEventDelayer.delay(delayMs, interpretationAndEventId);
    }
  }

  /** Returns the current window-transition delay in milliseconds. */
  public long getWindowTransitionDelayMs() {
    long delayMs = WINDOW_CHANGE_DELAY_MS;
    if (reduceDelayPref && SettingsUtils.isAnimationDisabled(service)) {
      delayMs = WINDOW_CHANGE_DELAY_NO_ANIMATION_MS;
    }
    log("getWindowTransitionDelayMs() delayMs=%d", delayMs);
    return delayMs;
  }

  /** Step 4: Delay event interpretation. */
  private final DelayHandler<EventIdAnd<EventInterpretation>> windowEventDelayer =
      new DelayHandler<EventIdAnd<EventInterpretation>>() {
        @Override
        public void handle(EventIdAnd<EventInterpretation> eventIdAndInterpretation) {
          delayedInterpret(eventIdAndInterpretation.object, eventIdAndInterpretation.eventId);
        }
      };

  public void clearQueue() {
    windowEventDelayer.removeMessages();
  }

  /** Step 5: After delay from "unstable" window events, re-run window interpretation. */
  public void delayedInterpret(EventInterpretation interpretation, @Nullable EventId eventId) {

    log("delayedInterpret()");

    interpretation.setOriginalEvent(false);
    interpretation.setWindowsStable(true);

    // Map windows to roles, detect window role changes.
    WindowRoles latestRoles = (pendingWindowRoles == null) ? windowRoles : pendingWindowRoles;
    WindowRoles newWindowRoles = new WindowRoles(latestRoles);
    updateWindowRoles(interpretation, service, newWindowRoles);
    setWindowTitles(newWindowRoles);
    detectWindowChanges(newWindowRoles, interpretation);
    log("delayedInterpret() interpretation=%s", interpretation);

    // Keep stable window role assignments.
    windowRoles = newWindowRoles;
    pendingWindowRoles = null;

    notifyInterpretationListeners(interpretation, eventId);
  }

  /** Send event interpretation to each listener. */
  private void notifyInterpretationListeners(
      EventInterpretation interpretation, @Nullable EventId eventId) {
    for (WindowEventHandler listener : listeners) {
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
    Integer role = windowToRole.get(windowId);
    return (role != null) && (role.intValue() == Role.ROLE_ALERT_DIALOG);
  }

  private void updateWindowTitlesMap(AccessibilityEvent event, EventInterpretation interpretation) {

    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        {
          // If split screen mode is NOT available, we only need to care single window.
          if (!isSplitScreenModeAvailable) {
            windowTitlesMap.clear();
          }

          int windowId = AccessibilityEventUtils.getWindowId(event);
          boolean shouldAnnounceEvent = shouldAnnounceWindowStateChange(event, windowId);
          CharSequence title =
              getWindowTitleFromWindowStateChange(
                  event, /* useContentDescription= */ shouldAnnounceEvent);
          log(
              "updateWindowTitlesMap() window id=%s title=%s shouldAnnounceEvent=%s",
              windowId, title, shouldAnnounceEvent);
          if (!TextUtils.isEmpty(title)) {
            if (shouldAnnounceEvent) {
              // When software keyboard is shown or hidden, TYPE_WINDOW_STATE_CHANGED
              // is dispatched with text describing the visibility of the keyboard.
              // Volume control shade/dialog also files TYPE_WINDOW_STATE_CHANGED event when it's
              // shown.
              interpretation.setAnnouncement(title);
              interpretation.setIsFromVolumeControlPanel(
                  AccessibilityEventUtils.isFromVolumeControlPanel(event));
              interpretation.setIsFromInputMethodEditor(
                  getWindowType(event) == AccessibilityWindowInfo.TYPE_INPUT_METHOD);
            } else {
              windowTitlesMap.put(windowId, title);

              if (getWindowType(event) == AccessibilityWindowInfo.TYPE_SYSTEM) {
                systemWindowIdsSet.add(windowId);
              }

              int role = Role.getSourceRole(event);
              windowToRole.put(windowId, role);
              windowToPackageName.put(windowId, event.getPackageName());
            }
          }
        }
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        {
          HashSet<Integer> windowIdsToBeRemoved = new HashSet<>(windowTitlesMap.keySet());
          List<AccessibilityWindowInfo> windows =
              AccessibilityServiceCompatUtils.getWindows(service);
          for (AccessibilityWindowInfo window : windows) {
            int windowId = window.getId();
            if (BuildVersionUtils.isAtLeastN()) {
              CharSequence title = window.getTitle();
              log(
                  "updateWindowTitlesMap() window id=%s title=%s type=%s",
                  windowId, title, windowTypeToString(window.getType()));
              if (!TextUtils.isEmpty(title)) {
                windowTitlesMap.put(windowId, title);
              }
            }
            windowIdsToBeRemoved.remove(windowId);
          }
          for (Integer windowId : windowIdsToBeRemoved) {
            windowTitlesMap.remove(windowId);
            systemWindowIdsSet.remove(windowId);
            windowToRole.remove(windowId);
            windowToPackageName.remove(windowId);
          }
        }
        break;
      default: // fall out
    }

    log("updateWindowTitlesMap() windowTitlesMap=%s", windowTitlesMap);
  }

  private CharSequence getWindowTitleFromWindowStateChange(
      AccessibilityEvent event, boolean useContentDescription) {
    if (useContentDescription && !TextUtils.isEmpty(event.getContentDescription())) {
      return event.getContentDescription();
    }

    List<CharSequence> titles = event.getText();
    if (!titles.isEmpty()) {
      return titles.get(0);
    }

    return null;
  }

  /**
   * Uses a heuristic to guess whether an event should be announced. Any event that comes from an
   * IME, or an invisible window is considered an announcement.
   */
  @TargetApi(Build.VERSION_CODES.O)
  private boolean shouldAnnounceWindowStateChange(AccessibilityEvent event, int windowId) {
    if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      throw new IllegalStateException();
    }

    // Assume window ID of -1 is the keyboard.
    if (windowId == WINDOW_ID_NONE) {
      log("shouldAnnounceWindowStateChange() windowId=WINDOW_ID_NONE");
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

    boolean isNonMainWindow = AccessibilityEventUtils.isNonMainWindowEvent(event);
    log("shouldAnnounceWindowStateChange() isNonMainWindow=%s", isNonMainWindow);
    return isNonMainWindow;
  }

  /** Modifies window IDs in windowRoles. */
  @TargetApi(Build.VERSION_CODES.P)
  private static void updateWindowRoles(
      EventInterpretation interpretation, AccessibilityService service, WindowRoles windowRoles) {

    log("updateWindowRoles() interpretation=%s", interpretation);

    if (interpretation.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      if (BuildVersionUtils.isAtLeastP()) {
        // For simplicity and reliability, update roles for both TYPE_WINDOW_STATE_CHANGED and
        // TYPE_WINDOWS_CHANGED, using AccessibilityService.getWindows()
        // TODO: Do the same for older android versions.

        // If non-empty unhandled change-type... skip updating window roles.
        int changeTypesUsed =
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED;
        int changeTypes = interpretation.getChangeTypes();
        if ((changeTypes != 0) && (changeTypes & changeTypesUsed) == 0) {
          return;
        }
      }
    }

    ArrayList<AccessibilityWindowInfo> applicationWindows = new ArrayList<>();
    ArrayList<AccessibilityWindowInfo> systemWindows = new ArrayList<>();
    ArrayList<AccessibilityWindowInfo> accessibilityOverlayWindows = new ArrayList<>();
    ArrayList<AccessibilityWindowInfo> picInPicWindows = new ArrayList<>();
    List<AccessibilityWindowInfo> windows = AccessibilityServiceCompatUtils.getWindows(service);

    // If there are no windows available, clear the cached IDs.
    if (windows.isEmpty()) {
      log("updateWindowRoles() windows.isEmpty()=true returning");
      windowRoles.clear();
      return;
    }

    for (int i = 0; i < windows.size(); i++) {
      AccessibilityWindowInfo window = windows.get(i);
      log("updateWindowRoles() window id=%d", window.getId());
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
          // From LMR1 to N, for Talkback we create a transparent a11y overlay on edit text when
          // double click is performed. That is done so that we can adjust the cursor position to
          // the end of the edit text instead of the center, which is the default behavior. This
          // overlay should be ignored while detecting window changes.
          boolean isOverlayOnEditTextSupported = !BuildVersionUtils.isAtLeastO();
          AccessibilityNodeInfo root = AccessibilityWindowInfoUtils.getRoot(window);
          boolean isTalkbackOverlay = (Role.getRole(root) == Role.ROLE_TALKBACK_EDIT_TEXT_OVERLAY);
          AccessibilityNodeInfoUtils.recycleNodes(root);
          // Only add overlay window not shown by talkback.
          if (!isOverlayOnEditTextSupported || !isTalkbackOverlay) {
            accessibilityOverlayWindows.add(window);
          }
          break;
        default: // fall out
      }
    }

    log(
        "updateWindowRoles() accessibilityOverlayWindows.size()=%d",
        accessibilityOverlayWindows.size());
    log("updateWindowRoles() applicationWindows.size()=%d", applicationWindows.size());

    // If an accessibility-overlay exists, only update overlay role.
    if (accessibilityOverlayWindows.size() >= 1) {
      // TODO: Figure out how to choose between multiple overlays.
      // Use title from TYPE_WINDOW_STATE_CHANGED via windowTitlesMap to check overlay titles and
      // decide which of many accessibility-overlays should take the role here.
      log("updateWindowRoles() Updating overlay role only");
      // Take the last overlay to check, so we could know if there is any new added overlay.
      windowRoles.accessibilityOverlayWindowId =
          accessibilityOverlayWindows.get(accessibilityOverlayWindows.size() - 1).getId();

      // Prefer to choose an overlay window with the focused and active state.()
      for (AccessibilityWindowInfo windowInfo : accessibilityOverlayWindows) {
        if (windowInfo.isFocused() && windowInfo.isActive()) {
          windowRoles.accessibilityOverlayWindowId = windowInfo.getId();
          break;
        }
      }
    } else {
      windowRoles.accessibilityOverlayWindowId = WINDOW_ID_NONE;
    }

    windowRoles.picInPicWindowId =
        picInPicWindows.isEmpty() ? WINDOW_ID_NONE : picInPicWindows.get(0).getId();

    if (applicationWindows.isEmpty()) {
      log("updateWindowRoles() Zero application windows case");
      windowRoles.windowIdA = WINDOW_ID_NONE;
      windowRoles.windowIdB = WINDOW_ID_NONE;

      // If there is no application window but a system window, consider it as a current window.
      // This logic handles notification shade and lock screen.
      if (!systemWindows.isEmpty()) {
        Collections.sort(
            systemWindows,
            new WindowManager.WindowPositionComparator(WindowManager.isScreenLayoutRTL(service)));

        windowRoles.windowIdA = systemWindows.get(0).getId();
      }
    } else if (applicationWindows.size() == 1) {
      log("updateWindowRoles() One application window case");
      windowRoles.windowIdA = applicationWindows.get(0).getId();
      windowRoles.windowIdB = WINDOW_ID_NONE;
    } else if (applicationWindows.size() == 2
        && !hasOverlap(applicationWindows.get(0), applicationWindows.get(1))) {
      log("updateWindowRoles() Two application windows case");
      Collections.sort(
          applicationWindows,
          new WindowManager.WindowPositionComparator(WindowManager.isScreenLayoutRTL(service)));

      windowRoles.windowIdA = applicationWindows.get(0).getId();
      windowRoles.windowIdB = applicationWindows.get(1).getId();
    } else {
      log("updateWindowRoles() Default number of application windows case");
      // If there are more than 2 windows, report the active window as the current window.
      for (AccessibilityWindowInfo applicationWindow : applicationWindows) {
        if (applicationWindow.isActive()) {
          windowRoles.windowIdA = applicationWindow.getId();
          windowRoles.windowIdB = WINDOW_ID_NONE;
          return;
        }
      }
    }
  }

  private static boolean hasOverlap(
      AccessibilityWindowInfo windowA, AccessibilityWindowInfo windowB) {

    Rect rectA = AccessibilityWindowInfoUtils.getBounds(windowA);
    log("hasOverlap() windowA=%s rectA=%s", windowA, rectA);
    if (rectA == null) {
      return false;
    }

    Rect rectB = AccessibilityWindowInfoUtils.getBounds(windowB);
    log("hasOverlap() windowB=%s rectB=%s", windowB, rectB);
    if (rectB == null) {
      return false;
    }

    return Rect.intersects(rectA, rectB);
  }

  private boolean isSystemWindow(int windowId) {
    if (systemWindowIdsSet.contains(windowId)) {
      return true;
    }

    if (!isSplitScreenModeAvailable) {
      return false;
    }

    for (AccessibilityWindowInfo window : AccessibilityServiceCompatUtils.getWindows(service)) {
      if (window.getId() == windowId && window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
        return true;
      }
    }

    return false;
  }

  /** Updates window titles in windowRoles. */
  private void setWindowTitles(WindowRoles windowRoles) {
    windowRoles.windowTitleA = getWindowTitle(windowRoles.windowIdA);
    windowRoles.windowTitleB = getWindowTitle(windowRoles.windowIdB);
    windowRoles.accessibilityOverlayWindowTitle =
        getWindowTitle(windowRoles.accessibilityOverlayWindowId);
    windowRoles.picInPicWindowTitle = getWindowTitle(windowRoles.picInPicWindowId);
  }

  /** Detect window role changes, and turn on flags in interpretation. */
  private void detectWindowChanges(WindowRoles roles, EventInterpretation interpretation) {
    log("detectWindowChanges() roles=%s", roles);

    // Collect new window information into interpretation.
    setNewWindowInterpretation(roles.windowIdA, interpretation.getWindowA());
    setNewWindowInterpretation(roles.windowIdB, interpretation.getWindowB());
    setNewWindowInterpretation(
        roles.accessibilityOverlayWindowId, interpretation.getAccessibilityOverlay());
    setNewWindowInterpretation(roles.picInPicWindowId, interpretation.getPicInPic());

    // If there is no screen update, do not provide spoken feedback.
    boolean mainWindowsChanged =
        (interpretation.getWindowA().idOrTitleChanged()
            || interpretation.getWindowB().idOrTitleChanged()
            || interpretation.getAccessibilityOverlay().idOrTitleChanged());
    interpretation.setMainWindowsChanged(mainWindowsChanged);
  }

  private CharSequence getWindowTitleForFeedback(int windowId) {
    CharSequence title = getWindowTitle(windowId);

    if (title == null) {
      // , Do not announce application label for accessibility overlay.
      // TODO: Do not announce application label for any types of window in single
      // window mode.
      for (AccessibilityWindowInfo window : AccessibilityServiceCompatUtils.getWindows(service)) {
        if ((window.getId() == windowId)
            && (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)) {
          // Return empty title directly without attaching the "Alert" prefix.
          return "";
        }
      }

      // Try to fall back to application label if window title is not available.
      CharSequence packageName = windowToPackageName.get(windowId);
      // Try to get package name from accessibility window info if it's not in the map.
      if (packageName == null) {
        for (AccessibilityWindowInfo window : AccessibilityServiceCompatUtils.getWindows(service)) {
          if (window.getId() == windowId) {
            AccessibilityNodeInfo rootNode = AccessibilityWindowInfoUtils.getRoot(window);
            if (rootNode != null) {
              packageName = rootNode.getPackageName();
              rootNode.recycle();
            }
          }
        }
      }

      if (packageName != null) {
        title = getApplicationLabel(packageName);
      }

      if (title == null) {
        title = service.getString(R.string.untitled_window);
      }
    }

    if (isAlertDialog(windowId)) {
      title = service.getString(R.string.template_alert_dialog_template, title);
    }

    return title;
  }

  private CharSequence getApplicationLabel(CharSequence packageName) {
    PackageManager packageManager = service.getPackageManager();
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

  private static int getWindowType(AccessibilityEvent event) {
    if (event == null) {
      return WINDOW_TYPE_NONE;
    }

    AccessibilityNodeInfo nodeInfo = event.getSource();
    if (nodeInfo == null) {
      return WINDOW_TYPE_NONE;
    }

    AccessibilityNodeInfoCompat nodeInfoCompat = AccessibilityNodeInfoUtils.toCompat(nodeInfo);
    AccessibilityWindowInfoCompat windowInfoCompat =
        AccessibilityNodeInfoUtils.getWindow(nodeInfoCompat);
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
    private int windowIdFromEvent = WINDOW_ID_NONE;
    private CharSequence announcement;
    private final WindowInterpretation windowA = new WindowInterpretation();
    private final WindowInterpretation windowB = new WindowInterpretation();
    private final WindowInterpretation accessibilityOverlay = new WindowInterpretation();
    private final WindowInterpretation picInPic = new WindowInterpretation();
    private boolean mainWindowsChanged = false;
    private boolean picInPicChanged = false;
    private boolean windowsStable = false;
    private boolean originalEvent = false;
    private boolean isFromVolumeControlPanel = false;
    private boolean isFromInputMethodEditor = false;
    private boolean allowAnnounce = true;
    private int eventType = 0;

    /** Bitmask from getContentChangeTypes() or getWindowChanges(), depending on eventType. */
    private int changeTypes = 0;

    @Override
    public void setReadOnly() {
      super.setReadOnly();
      windowA.setReadOnly();
      windowB.setReadOnly();
      accessibilityOverlay.setReadOnly();
      picInPic.setReadOnly();
    }

    public void setWindowIdFromEvent(int id) {
      checkIsWritable();
      windowIdFromEvent = id;
    }

    public int getWindowIdFromEvent() {
      return windowIdFromEvent;
    }

    public void setAnnouncement(CharSequence announcement) {
      checkIsWritable();
      this.announcement = announcement;
    }

    public CharSequence getAnnouncement() {
      return announcement;
    }

    public WindowInterpretation getWindowA() {
      return windowA;
    }

    public WindowInterpretation getWindowB() {
      return windowB;
    }

    public WindowInterpretation getAccessibilityOverlay() {
      return accessibilityOverlay;
    }

    public WindowInterpretation getPicInPic() {
      return picInPic;
    }

    public void setMainWindowsChanged(boolean changed) {
      checkIsWritable();
      mainWindowsChanged = changed;
    }

    public boolean getMainWindowsChanged() {
      return mainWindowsChanged;
    }

    public void setPicInPicChanged(boolean changed) {
      checkIsWritable();
      picInPicChanged = changed;
    }

    public boolean getPicInPicChanged() {
      return picInPicChanged;
    }

    public void setWindowsStable(boolean stable) {
      checkIsWritable();
      windowsStable = stable;
    }

    public boolean areWindowsStable() {
      return windowsStable;
    }

    public void setOriginalEvent(boolean original) {
      checkIsWritable();
      originalEvent = original;
    }

    public boolean isOriginalEvent() {
      return originalEvent;
    }

    public void setIsFromVolumeControlPanel(boolean isFromVolumeControlPanel) {
      checkIsWritable();
      this.isFromVolumeControlPanel = isFromVolumeControlPanel;
    }

    public boolean isFromVolumeControlPanel() {
      return isFromVolumeControlPanel;
    }

    public void setIsFromInputMethodEditor(boolean isFromInputMethodEditor) {
      checkIsWritable();
      this.isFromInputMethodEditor = isFromInputMethodEditor;
    }

    public boolean isFromInputMethodEditor() {
      return isFromInputMethodEditor;
    }

    public void setAllowAnnounce(boolean allowAnnounce) {
      checkIsWritable();
      this.allowAnnounce = allowAnnounce;
    }

    public boolean isAllowAnnounce() {
      return allowAnnounce;
    }

    public void setEventType(int eventType) {
      checkIsWritable();
      this.eventType = eventType;
    }

    public int getEventType() {
      return eventType;
    }

    public void setChangeTypes(int changeTypes) {
      checkIsWritable();
      this.changeTypes = changeTypes;
    }

    public int getChangeTypes() {
      return changeTypes;
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalSubObj("WindowA", windowA),
          StringBuilderUtils.optionalSubObj("WindowB", windowB),
          StringBuilderUtils.optionalSubObj("A11yOverlay", accessibilityOverlay),
          StringBuilderUtils.optionalSubObj("PicInPic", picInPic),
          StringBuilderUtils.optionalInt("WindowIdFromEvent", windowIdFromEvent, WINDOW_ID_NONE),
          StringBuilderUtils.optionalTag("MainWindowsChanged", mainWindowsChanged),
          StringBuilderUtils.optionalTag("PicInPicChanged", picInPicChanged),
          StringBuilderUtils.optionalTag("WindowsStable", windowsStable),
          StringBuilderUtils.optionalTag("OriginalEvent", originalEvent),
          StringBuilderUtils.optionalTag("IsFromVolumeControlPanel", isFromVolumeControlPanel),
          StringBuilderUtils.optionalTag("isFromInputMethodEditor", isFromInputMethodEditor),
          StringBuilderUtils.optionalTag("allowAnnounce", allowAnnounce),
          StringBuilderUtils.optionalField(
              "EventType", eventType == 0 ? null : AccessibilityEventUtils.typeToString(eventType)),
          StringBuilderUtils.optionalField("ChangeTypes", stateChangesToString()),
          StringBuilderUtils.optionalText("Announcement", announcement));
    }

    private String stateChangesToString() {
      switch (eventType) {
        case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
          return contentChangeTypesToString(changeTypes);
        case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
          return windowChangeTypesToString(changeTypes);
        default:
          return null;
      }
    }
  }

  /** Fully interpreted and analyzed window-change event description about one window. */
  public static class WindowInterpretation extends ReadOnly {
    private int id = WINDOW_ID_NONE;
    private CharSequence title;
    private CharSequence titleForFeedback;
    private boolean isAlertDialog = false;
    private int oldId = WINDOW_ID_NONE;
    private CharSequence oldTitle;

    public void setId(int id) {
      checkIsWritable();
      this.id = id;
    }

    public boolean idOrTitleChanged() {
      return (oldId != id) || !TextUtils.equals(oldTitle, title);
    }

    public int getId() {
      return id;
    }

    public void setTitle(CharSequence title) {
      checkIsWritable();
      this.title = title;
    }

    public CharSequence getTitle() {
      return title;
    }

    public void setTitleForFeedback(CharSequence title) {
      checkIsWritable();
      titleForFeedback = title;
    }

    public CharSequence getTitleForFeedback() {
      return titleForFeedback;
    }

    public void setAlertDialog(boolean isAlert) {
      checkIsWritable();
      isAlertDialog = isAlert;
    }

    public boolean isAlertDialog() {
      return isAlertDialog;
    }

    public void setOldId(int oldId) {
      checkIsWritable();
      this.oldId = oldId;
    }

    public int getOldId() {
      return oldId;
    }

    public void setOldTitle(CharSequence oldTitle) {
      checkIsWritable();
      this.oldTitle = oldTitle;
    }

    public CharSequence getOldTitle() {
      return oldTitle;
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalInt("ID", id, WINDOW_ID_NONE),
          StringBuilderUtils.optionalText("Title", title),
          StringBuilderUtils.optionalText("TitleForFeedback", titleForFeedback),
          StringBuilderUtils.optionalTag("Alert", isAlertDialog),
          StringBuilderUtils.optionalInt("OldID", oldId, WINDOW_ID_NONE),
          StringBuilderUtils.optionalText("OldTitle", oldTitle));
    }
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Logging functions

  private static void log(String format, Object... args) {
    LogUtils.v(TAG, format, args);
  }

  private static String windowTypeToString(int type) {
    switch (type) {
      case AccessibilityWindowInfo.TYPE_APPLICATION:
        return "TYPE_APPLICATION";
      case AccessibilityWindowInfo.TYPE_INPUT_METHOD:
        return "TYPE_INPUT_METHOD";
      case AccessibilityWindowInfo.TYPE_SYSTEM:
        return "TYPE_SYSTEM";
      case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY:
        return "TYPE_ACCESSIBILITY_OVERLAY";
      case AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER:
        return "TYPE_SPLIT_SCREEN_DIVIDER";
      default:
        return "UNKNOWN";
    }
  }

  @TargetApi(Build.VERSION_CODES.P)
  private static String contentChangeTypesToString(int typesBitmask) {
    StringBuilder strings = new StringBuilder();
    if ((typesBitmask & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE) != 0) {
      strings.append("CONTENT_CHANGE_TYPE_PANE_TITLE");
    }
    if ((typesBitmask & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED) != 0) {
      strings.append("CONTENT_CHANGE_TYPE_PANE_APPEARED");
    }
    if ((typesBitmask & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) != 0) {
      strings.append("CONTENT_CHANGE_TYPE_PANE_DISAPPEARED");
    }
    return (strings.length() == 0) ? null : strings.toString();
  }

  @TargetApi(Build.VERSION_CODES.P)
  private static String windowChangeTypesToString(int typesBitmask) {
    StringBuilder strings = new StringBuilder();
    if ((typesBitmask & AccessibilityEvent.WINDOWS_CHANGE_ADDED) != 0) {
      strings.append("WINDOWS_CHANGE_ADDED");
    }
    if ((typesBitmask & AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0) {
      strings.append("WINDOWS_CHANGE_REMOVED");
    }
    if ((typesBitmask & AccessibilityEvent.WINDOWS_CHANGE_TITLE) != 0) {
      strings.append("WINDOWS_CHANGE_TITLE");
    }
    return (strings.length() == 0) ? null : strings.toString();
  }
}
