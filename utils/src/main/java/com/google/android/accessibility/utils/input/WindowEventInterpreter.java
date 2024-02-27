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

package com.google.android.accessibility.utils.input;

import static androidx.core.view.accessibility.AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_APPEARED;
import static androidx.core.view.accessibility.AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED;
import static androidx.core.view.accessibility.AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_TITLE;
import static com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_ID_NONE;
import static com.google.android.accessibility.utils.Role.ROLE_NONE;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.ReadOnly;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.monitor.DisplayMonitor;
import com.google.android.accessibility.utils.monitor.DisplayMonitor.DisplayStateChangedListener;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Translates Accessibility Window Events into more usable event description.
 *
 * <p>A major difficulty is floods of transitional window events, many of which may be redundant.
 * Also, some window-events originate from WindowManager, others from the Views, and both sources of
 * information must be merged. To solve for this, when a window event is received, interpretation is
 * delayed, and window changes are buffered in pendingWindowRoles until the delayed interpretation
 * is complete.
 *
 * <p>Android P changes:
 *
 * <ul>
 *   <li>No longer get TYPE_WINDOW_STATE_CHANGED announcement when volume controls are hidden.
 *   <li>More windows returned by getWindowsOnAllDisplays(), including multiple system windows.
 * </ul>
 */
public class WindowEventInterpreter implements WindowsDelegate, DisplayStateChangedListener {

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final boolean DISPLAY_PERFORMANCE = false;

  private static final String TAG = "WindowEventInterpreter";

  // Maximum delay times to interpret events and avoid incomplete transitional data.
  private static final int PIC_IN_PIC_DELAY_MS = 300;
  public static final int WINDOW_CHANGE_DELAY_MS = 550;
  public static final int WINDOW_CHANGE_DELAY_NO_ANIMATION_MS = 200;
  private static final int ACCESSIBILITY_OVERLAY_DELAY_MS = 150;

  // Delay between repeated event-interpretation events. 300ms delay is enough to prevent
  // transitional split-screen announcement on android-Q + pixel-2.
  // TODO: Add minimum-delay-time only for split-screen on older android.
  private static final int DELAY_INCREMENT_MS = 10;

  @RequiresApi(api = VERSION_CODES.P)
  private static final int WINDOWS_CHANGE_TYPES_USED =
      AccessibilityEvent.WINDOWS_CHANGE_ADDED
          | AccessibilityEvent.WINDOWS_CHANGE_TITLE
          | AccessibilityEvent.WINDOWS_CHANGE_REMOVED
          | AccessibilityEvent.WINDOWS_CHANGE_PIP;

  private static final int PANE_CONTENT_CHANGE_TYPES =
      CONTENT_CHANGE_TYPE_PANE_TITLE
          | CONTENT_CHANGE_TYPE_PANE_APPEARED
          | CONTENT_CHANGE_TYPE_PANE_DISAPPEARED;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Inner classes

  /** Caches window-data from window-event. */
  public static class Window {
    public Window() {}

    /**
     * The most recent title, from either window events or pane events. Used to provide screen
     * feedback.
     */
    public CharSequence title;

    /** Title from events windows-changed & window-state-changed excluding pane-changed. */
    private @Nullable CharSequence titleFromWindowChange;

    /** Title from TYPE_WINDOW_STATE_CHANGED events that are not announcements. */
    private @Nullable CharSequence titleFromStateChange;

    private boolean hasAccessibilityPane = false;

    public void setTitleFromWindowChange(@Nullable CharSequence newTitle) {
      if (!TextUtils.equals(this.titleFromWindowChange, newTitle)) {
        this.titleFromStateChange = null;
      }
      this.titleFromWindowChange = newTitle;
    }

    public @Nullable CharSequence getTitleFromWindowChange() {
      return titleFromWindowChange;
    }

    public void setTitleFromStateChange(@Nullable CharSequence newTitle) {
      this.titleFromStateChange = newTitle;
    }

    public @Nullable CharSequence getTitleFromStateChange() {
      return titleFromStateChange;
    }

    public void setHasAccessibilityPane(boolean hasAccessibilityPane) {
      this.hasAccessibilityPane = hasAccessibilityPane;
    }

    public boolean hasAccessibilityPane() {
      return hasAccessibilityPane;
    }

    @RoleName public int eventSourceRole = ROLE_NONE;
    public @Nullable CharSequence eventPackageName;

    @Override
    public String toString() {
      return "{ "
          + StringBuilderUtils.joinFields(
              StringBuilderUtils.optionalText("title", title),
              StringBuilderUtils.optionalText("titleFromWindowChange", titleFromWindowChange),
              StringBuilderUtils.optionalText(
                  "eventSourceRole", Role.roleToString(eventSourceRole)),
              StringBuilderUtils.optionalText("eventPackageName", eventPackageName))
          + "}";
    }
  }

  /** Caches data from non-main-window announcements. */
  @AutoValue
  public abstract static class Announcement {
    public static Announcement create(
        CharSequence text,
        @Nullable CharSequence packageName,
        boolean isFromVolumeControlPanel,
        boolean isFromInputMethodEditor) {
      return new AutoValue_WindowEventInterpreter_Announcement(
          text, packageName, isFromVolumeControlPanel, isFromInputMethodEditor);
    }

    public abstract CharSequence text();

    public abstract @Nullable CharSequence packageName();

    public abstract boolean isFromVolumeControlPanel();

    public abstract boolean isFromInputMethodEditor();
  }

  /**
   * Assignment of windows to roles. Encapsulated in a data-struct, to allow temporary assignment of
   * roles.
   */
  public static class WindowRoles {
    // Window A: In split screen mode, left (right in RTL) or top window. In full screen mode, the
    // current window.
    public int windowIdA = WINDOW_ID_NONE;
    public @Nullable CharSequence windowTitleA;

    // Window B: In split screen mode, right (left in RTL) or bottom window. This must be
    // WINDOW_ID_NONE in full screen mode.
    public int windowIdB = WINDOW_ID_NONE;
    public @Nullable CharSequence windowTitleB;

    // Accessibility overlay window
    public int accessibilityOverlayWindowId = WINDOW_ID_NONE;
    public @Nullable CharSequence accessibilityOverlayWindowTitle;

    // Picture-in-picture window history.
    public int picInPicWindowId = WINDOW_ID_NONE;
    public @Nullable CharSequence picInPicWindowTitle;

    // Input method window
    public int inputMethodWindowId = WINDOW_ID_NONE;
    public @Nullable CharSequence inputMethodWindowTitle;

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
      inputMethodWindowId = oldRoles.inputMethodWindowId;
      inputMethodWindowTitle = oldRoles.inputMethodWindowTitle;
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
      inputMethodWindowId = WINDOW_ID_NONE;
      inputMethodWindowTitle = null;
    }

    @Override
    public String toString() {
      return String.format(
          "a:%s:%s b:%s:%s accessOverlay:%s:%s picInPic:%s:%s inputMethod:%s:%s",
          windowIdA,
          windowTitleA,
          windowIdB,
          windowTitleB,
          accessibilityOverlayWindowId,
          accessibilityOverlayWindowTitle,
          picInPicWindowId,
          picInPicWindowTitle,
          inputMethodWindowId,
          inputMethodWindowTitle);
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private final AccessibilityService service;
  private final boolean isSplitScreenModeAvailable;
  private final HashMap<Integer, Window> windowIdToData = new HashMap<>();
  // Caches the window roles from last window transition for comparison.
  private WindowRoles windowRoles = new WindowRoles();
  private @Nullable WindowRoles pendingWindowRoles;
  private int picInPicLastShownId = WINDOW_ID_NONE; // Last pic-in-pic window that was shown.
  private long picInPicDisappearTime = 0; // Last time pic-in-pic was hidden.
  // Announcement from event TYPE_WINDOW_STATE_CHANGED, to be spoken with next event-interpretation.
  private @Nullable Announcement announcement;

  /** Preference to reduce delay before considering windows stable. */
  private boolean reduceDelayPref = false;

  private long screenTransitionStartTime = 0;

  /**
   * Sets to {@code true} if receiving {@link AccessibilityEvent#TYPE_WINDOWS_CHANGED} and resets it
   * after window transitions finished.
   */
  public boolean areWindowsChanging = false;

  /** Flag whether IME transition happened recently. */
  private boolean recentKeyboardWindowChange = false;

  // android_library_with_nullness_check will throws an error of assignment.type.incompatible in
  // this assignment. This assignment is safe here since we don't access it in the construction
  // phrase, so we suppress it.
  @SuppressWarnings({"nullness:assignment"})
  private final WindowEventDelayer windowEventDelayer = new WindowEventDelayer(this);

  private List<WindowEventHandler> listeners = new ArrayList<>();
  private final List<WindowEventHandler> priorityListeners = new ArrayList<>();

  private final Performance.Statistics statisticsAboutDelay = new Performance.Statistics();

  // We use Boolean since we want to initialize it as null before onDisplayStateChanged is invoked.
  private @Nullable Boolean defaultDisplayOn = null;
  private final DisplayMonitor displayMonitor;

  @Override
  public void onDisplayStateChanged(boolean displayOn) {
    defaultDisplayOn = displayOn;
    if (!displayOn) {
      // When the display turns off, we clear screen state.
      clearScreenState();
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public WindowEventInterpreter(AccessibilityService service, DisplayMonitor displayMonitor) {
    this.service = service;
    this.displayMonitor = displayMonitor;
    boolean isArc = FeatureSupport.isArc();
    isSplitScreenModeAvailable =
        BuildVersionUtils.isAtLeastN() && !FeatureSupport.isTv(service) && !isArc;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public void setReduceDelayPref(boolean reduceDelayPref) {
    this.reduceDelayPref = reduceDelayPref;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public void onResumeInfrastructure() {
    displayMonitor.addDisplayStateChangedListener(this);
  }

  public void onSuspendInfrastructure() {
    displayMonitor.removeDisplayStateChangedListener(this);
  }

  private void clearScreenState() {
    clearRoles();
  }

  @Override // WindowsDelegate
  public CharSequence getWindowTitle(int windowId) {
    return getWindowTitleForFeedback(windowId);
  }

  @Override
  public CharSequence getAccessibilityPaneTitle(int windowId) {
    @Nullable Window window = windowIdToData.get(windowId);
    if (window == null || !window.hasAccessibilityPane()) {
      return "";
    }

    return window.title;
  }

  private @Nullable CharSequence getWindowTitleInternal(int windowId) {
    return getWindowTitleInternal(windowId, areWindowsChanging);
  }

  /**
   * Gets window title from window first if {@code windowInfoFirst} is true. Otherwise, gets from
   * the cache which comes from the event or window.
   */
  private @Nullable CharSequence getWindowTitleInternal(int windowId, boolean windowInfoFirst) {
    CharSequence titleFromWindowInfo = getWindowTitleFromWindowInfo(windowId);
    @Nullable Window window = windowIdToData.get(windowId);

    if (windowInfoFirst && !TextUtils.isEmpty(titleFromWindowInfo)) {
      return titleFromWindowInfo;
    }

    if (window != null && !TextUtils.isEmpty(window.title)) {
      return window.title;
    }

    return titleFromWindowInfo;
  }

  private @Nullable CharSequence getWindowTitleFromWindowInfo(int windowId) {
    if (!FeatureSupport.supportGetTitleFromWindows()) {
      return null;
    }

    List<AccessibilityWindowInfo> windows = getAllWindows(service);
    for (AccessibilityWindowInfo window : windows) {
      if (window.getId() == windowId) {
        return AccessibilityWindowInfoUtils.getTitle(window);
      }
    }
    return null;
  }

  public boolean isSplitScreenModeAvailable() {
    return isSplitScreenModeAvailable;
  }

  @Override // WindowsDelegate
  public boolean isSplitScreenMode(int displayId) {
    if (!isSplitScreenModeAvailable) {
      return false;
    }

    List<AccessibilityWindowInfo> windows =
        AccessibilityServiceCompatUtils.getWindowsOnAllDisplays(service).get(displayId);
    if (windows == null) {
      return false;
    }
    for (AccessibilityWindowInfo window : windows) {
      if ((window != null)
          && window.getType() == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasAccessibilityPane(int windowId) {
    @Nullable Window window = windowIdToData.get(windowId);
    return (window != null) && window.hasAccessibilityPane();
  }

  /**
   * Returns {@code true} if it is a supported {@link AccessibilityEvent#TYPE_WINDOWS_CHANGED}
   * event.
   */
  public static boolean isSupportedWindowsChange(AccessibilityEvent event) {
    // On android P, only use window events with change-types that set window title or announce.
    if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      return false;
    }
    if (BuildVersionUtils.isAtLeastP()
        && ((event.getWindowChanges() & WINDOWS_CHANGE_TYPES_USED) == 0)) {
      return false;
    }
    return true;
  }

  /**
   * Returns {@code true} if it is a supported {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED}
   * event.
   */
  public boolean isSupportedWindowStateChange(AccessibilityEvent event) {
    if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      return false;
    }

    // TODO: Exclude more cases that doesn't need to be interpreted because some events should just
    // update windowIdToData rather provide any window announcement.
    if (FeatureSupport.windowStateChangeRequiresPane()) {
      switch (event.getContentChangeTypes()) {
        case AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED:
        case AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED:
        case AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE:
          return true;
        default:
          int windowId = AccessibilityEventUtils.getWindowId(event);
          if (windowIdToData.get(windowId) != null) {
            LogUtils.d(
                TAG,
                "Dropping window state changed event %s if the windowIdToData was handled before.",
                event.toString());
            return false;
          }
          return true;
      }
    }

    if (recentKeyboardWindowChange && AccessibilityEventUtils.isFromOnScreenKeyboard(event)) {
      LogUtils.v(
          TAG,
          "IME transition happened and handled, so ignore the resting announcements from IME.");
      return false;
    }

    return true;
  }

  /** Step 1: Define listener for delayed window events. */
  public interface WindowEventHandler {
    void handle(EventInterpretation interpretation, @Nullable EventId eventId);
  }

  /** Step 2: Add window event listener, that gets window-events before other listeners. */
  public void addPriorityListener(WindowEventHandler listener) {
    priorityListeners.add(listener);
  }

  /** Step 2: Add window event listener. */
  public void addListener(WindowEventHandler listener) {
    listeners.add(listener);
  }

  @VisibleForTesting
  public void setListeners(WindowEventHandler listener) {
    listeners = new ArrayList<>();
    addListener(listener);
  }

  /** Step 3: Extract data from window event and related APIs. */
  @TargetApi(Build.VERSION_CODES.P)
  public void interpret(AccessibilityEvent event, @Nullable EventId eventId) {
    interpret(event, eventId, true);
  }

  @TargetApi(Build.VERSION_CODES.P)
  public void interpret(AccessibilityEvent event, @Nullable EventId eventId, boolean allowEvent) {

    if (Boolean.FALSE.equals(defaultDisplayOn)) {
      final int displayId = AccessibilityEventUtils.getDisplayId(event);
      LogUtils.d(TAG, "interpret() : displayId=%s, default display is off", displayId);
      // When the display is off, we clear screen's state. However, it continually receives a11y
      // events and interprets windowRoles, making clear action ineffective. So, we don't interpret
      // when the display is off.
      if (displayId == Display.DEFAULT_DISPLAY) {
        return;
      }
    }

    if (!isSupportedWindowsChange(event) && !isSupportedWindowStateChange(event)) {
      return;
    }

    LogUtils.v(
        TAG,
        "START interpret() event type=%s time=%s allowEvent=%s",
        AccessibilityEventUtils.typeToString(event.getEventType()),
        event.getEventTime(),
        allowEvent);
    int depth = 0;

    if (screenTransitionStartTime == 0) {
      screenTransitionStartTime = event.getEventTime();
    }
    InterpretationAndRoles interpAndRoles = interpretInternal(event, depth);
    EventInterpretation interpretation = interpAndRoles.interpretation;
    WindowRoles newWindowRoles = interpAndRoles.roles;

    // Check whether windows are stable.
    long delayMs = calculateDelayMs(interpretation);
    interpretation.setWindowsStable(delayMs == 0);
    interpretation.setMaxDelay(delayMs);
    interpretation.setAllowAnnounce(allowEvent);
    LogDepth.log(TAG, depth, "interpret() delayMs=%s, interpretation=%s", delayMs, interpretation);
    interpretation.setEventStartTime(screenTransitionStartTime);

    // Stop delayed interpretation efforts, since new non-empty interpretation is coming.
    windowEventDelayer.removeMessages(WindowEventDelayer.MSG_DELAY_INTERPRET);

    if (delayMs == 0) {
      setRoles(newWindowRoles);
      LogUtils.v(TAG, "END interpret()");
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
      delayInterpret(interpretation, eventId);
    }

    // Send an immediate window event interpretation, possibly with unstable windows.
    notifyInterpretationListeners(interpretation, eventId);
  }

  private InterpretationAndRoles interpretInternal(AccessibilityEvent event, int depth) {
    LogDepth.log(TAG, depth, "interpret() windowRoles=%s", windowRoles);
    // Create event interpretation.
    EventInterpretation interpretation = new EventInterpretation();
    interpretation.setEventType(event.getEventType());
    interpretation.setOriginalEvent(true);
    interpretation.setWindowIdFromEvent(AccessibilityEventUtils.getWindowId(event));
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      if (BuildVersionUtils.isAtLeastP()) {
        interpretation.setChangeTypes(event.getContentChangeTypes());
      }
      // Update anchor node role for feedback announcement.
      updateAnchorNodeRole(event, interpretation);
    } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      areWindowsChanging = true;
      if (BuildVersionUtils.isAtLeastP()) {
        interpretation.setChangeTypes(event.getWindowChanges());
      }
    }

    final int displayId = AccessibilityEventUtils.getDisplayId(event);
    interpretation.setDisplayId(displayId);

    // Sets the bounds of the source node when the source node of the event is available
    AccessibilityNode sourceNode = AccessibilityNode.takeOwnership(event.getSource());
    if (sourceNode != null) {
      Rect rect = new Rect();
      sourceNode.getBoundsInScreen(rect);
      interpretation.setSourceBoundsInScreen(rect);
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
    setOldWindowInterpretation(
        windowRoles.inputMethodWindowId,
        windowRoles.inputMethodWindowTitle,
        interpretation.getInputMethod());
    updateFirstTimeInterpretationWhenWakeUp(interpretation, depth + 1);

    // Update stored windows for titles.
    updateWindowTitles(event, interpretation, depth + 1);
    syncAnnouncement(interpretation);

    // Map windows to roles, detect window role changes.
    WindowRoles latestRoles = (pendingWindowRoles == null) ? windowRoles : pendingWindowRoles;
    WindowRoles newWindowRoles = new WindowRoles(latestRoles);
    updateWindowRoles(interpretation, service, newWindowRoles, depth + 1);
    setWindowTitles(newWindowRoles);
    detectWindowChanges(newWindowRoles, interpretation, depth + 1);
    detectInputMethodChanged(
        newWindowRoles, interpretation, /* checkDuplicate= */ false, depth + 1);

    // Detect picture-in-picture window change, ruling out temporary disappear & reappear.
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

    return new InterpretationAndRoles(interpretation, newWindowRoles);
  }

  private static class InterpretationAndRoles {
    public final EventInterpretation interpretation;
    public final WindowRoles roles;

    public InterpretationAndRoles(EventInterpretation interpretation, WindowRoles roles) {
      this.interpretation = interpretation;
      this.roles = roles;
    }
  }

  /**
   * Delay the event to wait for next window event comes in below situations:
   *
   * <ul>
   *   <li>Delay for main window changed to update window title from the latest event.
   *   <li>Delay for input method changed and announcements to check duplicates between them in
   *       {@code detectInputMethodChanged}.
   * </ul>
   */
  private long calculateDelayMs(EventInterpretation interpretation) {
    if (!interpretation.getMainWindowsChanged()
        && !interpretation.getShouldAnnounceInputMethodChange()
        && (interpretation.getAnnouncement() == null)) {
      return 0;
    }
    return (interpretation.getAccessibilityOverlay().getId() == WINDOW_ID_NONE)
        ? getWindowTransitionDelayMs()
        : ACCESSIBILITY_OVERLAY_DELAY_MS;
  }

  /** Returns the current window-transition delay in milliseconds. */
  public long getWindowTransitionDelayMs() {
    long delayMs = WINDOW_CHANGE_DELAY_MS;
    if (reduceDelayPref && SettingsUtils.isAnimationDisabled(service)) {
      delayMs = WINDOW_CHANGE_DELAY_NO_ANIMATION_MS;
    }
    return delayMs;
  }

  /** Step 4: Delay event interpretation. */
  private static class WindowEventDelayer extends WeakReferenceHandler<WindowEventInterpreter> {
    public static final int MSG_DELAY_INTERPRET = 1;
    public static final int MSG_WAIT_ANNOUNCEMENT = 2;

    // android_library_with_nullness_check will throws an error of argument.type.incompatible in
    // this constructor's parameter. It is safe for us to feed WindowEventInterpreter.this here
    // since we can only access WindowEventInterpreter.this in the next message, so we suppress and
    // annotate this parent as @UnderInitialization.
    @SuppressWarnings({"nullness:argument"})
    WindowEventDelayer(@UnderInitialization WindowEventInterpreter parent) {
      super(parent, Looper.myLooper());
    }

    @Override
    public void handleMessage(Message message, WindowEventInterpreter parent) {
      if (message.what == MSG_DELAY_INTERPRET) {
        @SuppressWarnings("unchecked")
        EventIdAnd<EventInterpretation> eventIdAndInterpretation =
            (EventIdAnd<EventInterpretation>) message.obj;
        parent.delayedInterpret(eventIdAndInterpretation.object, eventIdAndInterpretation.eventId);
      } else if (message.what == MSG_WAIT_ANNOUNCEMENT) {
        parent.recentKeyboardWindowChange = false;
        LogUtils.v(TAG, "IME transition finished & start to support Announcement from IME.");
      }
    }
  }

  public void clearQueue() {
    windowEventDelayer.removeMessages(WindowEventDelayer.MSG_DELAY_INTERPRET);
    clearWindowTransition();
  }

  /**
   * Refresh the global flags.
   *
   * <ul>
   *   <li>Reset {@code windowRoles} if {@code clearRole} is true. Otherwise update to latest.
   *   <li>Reset other global flags which temporarily be used to cache data in delayed-event-queue.
   * </ul>
   */
  private void clearRoles() {
    windowRoles.clear();
    picInPicLastShownId = WINDOW_ID_NONE;
    picInPicDisappearTime = 0;
    clearWindowTransition();
  }

  private void setRoles(@Nullable WindowRoles newWindowRoles) {
    if (newWindowRoles != null) {
      windowRoles = newWindowRoles;
    }
    clearWindowTransition();
  }

  private void clearWindowTransition() {
    announcement = null;
    pendingWindowRoles = null;
    screenTransitionStartTime = 0;
    areWindowsChanging = false;
  }

  /** Step 5: After delay from "unstable" window events, re-run window interpretation. */
  void delayedInterpret(EventInterpretation interpretation, @Nullable EventId eventId) {
    int depth = 0;
    LogDepth.log(TAG, depth, "delayedInterpret()");

    interpretation.setOriginalEvent(false);

    // Map windows to roles, detect window role changes.
    WindowRoles latestRoles = (pendingWindowRoles == null) ? windowRoles : pendingWindowRoles;
    WindowRoles newWindowRoles = new WindowRoles(latestRoles);
    updateWindowRoles(interpretation, service, newWindowRoles, depth + 1);
    setWindowTitles(newWindowRoles);
    detectWindowChanges(newWindowRoles, interpretation, depth + 1);
    detectInputMethodChanged(newWindowRoles, interpretation, /* checkDuplicate= */ true, depth + 1);
    LogUtils.v(TAG, "END delayedInterpret() interpretation=%s", interpretation);

    // Assume windows are stable if they all have titles from state-change-events, or if maximum
    // delay is reached.
    if (interpretation.hasTitlesFromStateChange()
        || (interpretation.getMaxDelayMs() <= interpretation.getTotalDelayMs())) {
      interpretation.setWindowsStable(true);
      setRoles(newWindowRoles);
      notifyInterpretationListeners(interpretation, eventId);
    } else {
      delayInterpret(interpretation, eventId);
    }
  }

  private void delayInterpret(EventInterpretation interpretation, @Nullable EventId eventId) {
    long delay = DELAY_INCREMENT_MS;
    interpretation.incrementTotalDelayMs(delay);

    windowEventDelayer.sendMessageDelayed(
        windowEventDelayer.obtainMessage(
            WindowEventDelayer.MSG_DELAY_INTERPRET, new EventIdAnd<>(interpretation, eventId)),
        delay);
  }

  /** Send event interpretation to each listener. */
  private void notifyInterpretationListeners(
      EventInterpretation interpretation, @Nullable EventId eventId) {

    if (DISPLAY_PERFORMANCE && interpretation.windowsStable) {
      statisticsAboutDelay.increment(interpretation.getTotalDelayMs());
      Performance.displayStatistics(statisticsAboutDelay);
    }

    for (WindowEventHandler listener : priorityListeners) {
      listener.handle(interpretation, eventId);
    }
    for (WindowEventHandler listener : listeners) {
      listener.handle(interpretation, eventId);
    }
  }

  /** Collect data about window into interpretation. */
  private static void setOldWindowInterpretation(
      int oldWindowId, @Nullable CharSequence oldWindowTitle, WindowInterpretation interpretation) {
    interpretation.setOldId(oldWindowId);
    interpretation.setOldTitle(oldWindowTitle);
  }

  private void setNewWindowInterpretation(int windowId, WindowInterpretation interpretation) {
    interpretation.setId(windowId);
    @Nullable CharSequence title = getWindowTitleInternal(windowId);
    interpretation.setTitle(title);

    @Nullable Window window = windowIdToData.get(windowId);
    interpretation.setTitleFromStateChange(
        (window == null) ? null : window.getTitleFromStateChange());

    interpretation.setTitleForFeedback(getWindowTitleForFeedback(windowId, title));
  }

  /** Updates anchor node role for interpretation if the source window has an anchor node. */
  private void updateAnchorNodeRole(AccessibilityEvent event, EventInterpretation interpretation) {
    // incompatible argument for parameter node of getWindow.
    @SuppressWarnings("nullness:argument")
    AccessibilityWindowInfo sourceWindow = AccessibilityNodeInfoUtils.getWindow(event.getSource());
    if (sourceWindow == null) {
      return;
    }
    AccessibilityNodeInfoCompat anchorNode = AccessibilityWindowInfoUtils.getAnchor(sourceWindow);
    if (anchorNode != null) {
      interpretation.setAnchorNodeRole(Role.getRole(anchorNode));
    }
  }

  private void syncAnnouncement(EventInterpretation interpretation) {
    // Sync announcement to the latest interpretation.
    if (interpretation.getAnnouncement() == null) {
      interpretation.setAnnouncement(announcement);
    } else {
      announcement = interpretation.getAnnouncement();
    }
  }

  private void updateWindowTitles(
      AccessibilityEvent event, EventInterpretation interpretation, int depth) {
    updateWindowTitlesImp(event, interpretation, depth + 1);
    LogDepth.log(TAG, depth, "updateWindowTitles() windowIdToData=%s", windowIdToData);
  }

  private void updateFirstTimeInterpretationWhenWakeUp(
      EventInterpretation interpretation, int depth) {
    // We combine these 2 conditions to indicate that it is the first time interpretation from wake
    // up experience.
    // 1. The display turns on so the defaultDisplayOn is true and not null.
    // 2. The windowRoles for the window in the full screen mode or the main window in the split
    //    screen mode is cleared when the display is off earlier.
    interpretation.setInterpretFirstTimeWhenWakeUp(
        windowRoles.windowIdA == WINDOW_ID_NONE && Boolean.TRUE.equals(defaultDisplayOn));
    LogDepth.log(
        TAG, depth, "updateFirstTimeInterpretationWhenWakeUp() windowIdToData=%s", windowIdToData);
  }

  private void updateWindowTitlesImp(
      AccessibilityEvent event, EventInterpretation interpretation, int depth) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        {
          // If split screen mode is NOT available, we only need to care single window.
          if (!isSplitScreenModeAvailable) {
            windowIdToData.clear();
          }

          if (isPaneContentChangeTypes(event.getContentChangeTypes())) {
            updateWindowTitleFromPane(event, depth);
            return;
          }
          int windowId = AccessibilityEventUtils.getWindowId(event);
          boolean isFromVolumeWindow = AccessibilityEventUtils.isFromVolumeControlPanel(event);
          boolean isFromIMEWindow =
              AccessibilityEventUtils.isFromOnScreenKeyboard(event)
                  // Assume window ID of -1 is the keyboard.
                  || AccessibilityEventUtils.getWindowId(event) == WINDOW_ID_NONE;
          boolean shouldAnnounceEvent =
              shouldAnnounceWindowStateChanged(isFromVolumeWindow, isFromIMEWindow);
          CharSequence text =
              getTextFromWindowStateChange(event, /* useContentDescription= */ shouldAnnounceEvent);
          if (!TextUtils.isEmpty(text)) {
            if (shouldAnnounceEvent) {
              // When software keyboard is shown or hidden, TYPE_WINDOW_STATE_CHANGED
              // is dispatched with text describing the visibility of the keyboard.
              // Volume control shade/dialog also files TYPE_WINDOW_STATE_CHANGED event when it's
              // shown.
              interpretation.setAnnouncement(
                  Announcement.create(
                      text, event.getPackageName(), isFromVolumeWindow, isFromIMEWindow));
              LogDepth.log(
                  TAG,
                  depth,
                  "setAnnouncementFromEvent window id=%s announcement=%s",
                  windowId,
                  interpretation.getAnnouncement());
            } else {
              int role = Role.getSourceRole(event);
              Window window = new Window();
              window.title = text;
              window.setTitleFromWindowChange(text);
              window.setTitleFromStateChange(text);
              window.eventSourceRole = role;
              window.eventPackageName = event.getPackageName();
              windowIdToData.put(windowId, window);
              LogDepth.log(TAG, depth, "windowId=%s %s", windowId, window);
            }
          }
        }
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        {
          HashSet<Integer> windowIdsToBeRemoved = new HashSet<>(windowIdToData.keySet());
          int displayId = interpretation.getDisplayId();
          List<AccessibilityWindowInfo> windows =
              AccessibilityServiceCompatUtils.getWindowsOnAllDisplays(service).get(displayId);
          if (windows == null) {
            return;
          }
          for (AccessibilityWindowInfo window : windows) {
            int windowId = window.getId();
            CharSequence title = AccessibilityWindowInfoUtils.getTitle(window);
            if (!TextUtils.isEmpty(title)) {
              Window oldWindow = windowIdToData.get(windowId);
              Window newWindow = (oldWindow == null) ? new Window() : oldWindow;
              newWindow.title = title;
              newWindow.setTitleFromWindowChange(title);
              windowIdToData.put(windowId, newWindow);
              LogDepth.log(TAG, depth, "windowId=%s %s", windowId, newWindow);
            }
            windowIdsToBeRemoved.remove(windowId);
          }
          for (Integer windowId : windowIdsToBeRemoved) {
            windowIdToData.remove(windowId);
          }
        }
        break;
      default: // fall out
    }
  }

  private static @Nullable CharSequence getTextFromWindowStateChange(
      AccessibilityEvent event, boolean useContentDescription) {
    if (useContentDescription && !TextUtils.isEmpty(event.getContentDescription())) {
      return event.getContentDescription();
    }

    List<CharSequence> texts = event.getText();
    if (!texts.isEmpty()) {
      return texts.get(0);
    }

    return null;
  }

  /**
   * Gets {@code accessibilityPaneTitle} from {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED}
   * and it should get from {@link AccessibilityNodeInfoCompat#getPaneTitle()} prior to {@link
   * AccessibilityEvent#getText()}.
   */
  private static @Nullable CharSequence getAccessibilityPaneTitleFromEvent(
      AccessibilityEvent event) {
    if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || !isPaneContentChangeTypes(event.getContentChangeTypes())) {
      return null;
    }
    CharSequence accessibilityPaneTitle = null;
    AccessibilityNodeInfoCompat accessibilityNodeInfoCompat =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (accessibilityNodeInfoCompat != null) {
      accessibilityPaneTitle = accessibilityNodeInfoCompat.getPaneTitle();
    }
    if (TextUtils.isEmpty(accessibilityPaneTitle)) {
      accessibilityPaneTitle =
          getTextFromWindowStateChange(event, /* useContentDescription= */ false);
    }
    return accessibilityPaneTitle;
  }

  private static boolean isPaneContentChangeTypes(int changeTypes) {
    return (changeTypes & PANE_CONTENT_CHANGE_TYPES) != 0;
  }

  private void updateWindowTitleFromPane(AccessibilityEvent event, int depth) {
    CharSequence accessibilityPaneTitle = getAccessibilityPaneTitleFromEvent(event);
    if (TextUtils.isEmpty(accessibilityPaneTitle)) {
      return;
    }

    int windowId = AccessibilityEventUtils.getWindowId(event);
    Window oldWindow = windowIdToData.get(windowId);
    CharSequence title = null;
    boolean isPaneDisappeared = false;
    if ((event.getContentChangeTypes() & CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) != 0) {
      isPaneDisappeared = true;
      // Rollback title to titleFromWindowChange if the title was came from pane and disappeared.
      if (oldWindow != null && TextUtils.equals(accessibilityPaneTitle, oldWindow.title)) {
        title = oldWindow.getTitleFromWindowChange();
      }
    } else {
      title = accessibilityPaneTitle;
    }

    if (TextUtils.isEmpty(title)) {
      return;
    }
    // Only updates title value for pane events.
    Window newWindow = (oldWindow == null) ? new Window() : oldWindow;
    newWindow.title = title;
    newWindow.setHasAccessibilityPane(!isPaneDisappeared);
    if (!sourceAncestorHasPane(event)) {
      // Keep outer-most pane-title for window.
      newWindow.setTitleFromStateChange(title);
    }
    windowIdToData.put(windowId, newWindow);
    LogDepth.log(
        TAG,
        depth,
        "windowId=%s %s accessibilityPaneTitle=%s",
        windowId,
        newWindow,
        accessibilityPaneTitle);
  }

  private static boolean sourceAncestorHasPane(AccessibilityEvent event) {
    @Nullable AccessibilityNodeInfoCompat source = AccessibilityEventUtils.sourceCompat(event);
    return AccessibilityNodeInfoUtils.hasMatchingAncestor(
        source, Filter.node((n) -> (n.getPaneTitle() != null)));
  }

  /**
   * Uses a heuristic to guess whether an event should be announced. Any event that comes from an
   * IME, or an invisible window is considered an announcement because they are inactive windows so
   * they can't be updated to window roles and run standard window-title updated process. This is a
   * work around to make them could be announced.
   *
   * <p>Note: Gboard design change since T. It stops sending window state changed event for
   * accessibility announcement.
   *
   * <p>Note: IME service design change since T. IME navigation bar implementation is in IME service
   * rather than system UI since T. So the window state changed event source of IME navigation bar
   * is from IME service package.
   */
  // TODO  : define the behavior of non-active floating windows in TalkBack
  private boolean shouldAnnounceWindowStateChanged(
      boolean isFromVolumeWindow, boolean isFromIMEWindow) {
    return isFromVolumeWindow || (isFromIMEWindow && !BuildVersionUtils.isAtLeastT());
  }

  private boolean isAlertDialog(int windowId) {
    @Nullable Window window = windowIdToData.get(windowId);
    @RoleName int role = (window == null) ? ROLE_NONE : window.eventSourceRole;
    return role == Role.ROLE_ALERT_DIALOG;
  }

  /**
   * Modifies window IDs in roles. Should run after {@code updateWindowTitles} to get the
   * interpreted {@code windowIdToData}.
   */
  private void updateWindowRoles(
      EventInterpretation interpretation,
      AccessibilityService service,
      WindowRoles roles,
      int depth) {

    LogDepth.log(TAG, depth, "updateWindowRoles() interpretation=%s", interpretation);

    if (interpretation.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      // For simplicity and reliability, update roles for both TYPE_WINDOW_STATE_CHANGED and
      // TYPE_WINDOWS_CHANGED, using AccessibilityService.getWindows()
      // If non-empty unhandled change-type... skip updating window roles.
      int changeTypes = interpretation.getChangeTypes();
      if ((changeTypes != 0) && !isPaneContentChangeTypes(changeTypes)) {
        return;
      }
    }

    ArrayList<AccessibilityWindowInfo> applicationWindows = new ArrayList<>();
    ArrayList<AccessibilityWindowInfo> otherWindows = new ArrayList<>();
    ArrayList<AccessibilityWindowInfo> accessibilityOverlayWindows = new ArrayList<>();
    ArrayList<AccessibilityWindowInfo> picInPicWindows = new ArrayList<>();
    AccessibilityWindowInfo inputMethodWindow = null;

    int displayId = interpretation.getDisplayId();
    List<AccessibilityWindowInfo> windows =
        AccessibilityServiceCompatUtils.getWindowsOnAllDisplays(service).get(displayId);

    // If there are no windows available, clear the cached IDs.
    if (windows == null || windows.isEmpty()) {
      LogDepth.log(TAG, depth, "updateWindowRoles() windows.isEmpty()=true returning");
      roles.clear();
      return;
    }

    for (int i = 0; i < windows.size(); i++) {
      AccessibilityWindowInfo window = windows.get(i);
      if (AccessibilityWindowInfoUtils.isPictureInPicture(window)) {
        picInPicWindows.add(window);
        continue;
      }
      boolean roleAssigned = false;
      switch (window.getType()) {
        case AccessibilityWindowInfo.TYPE_APPLICATION:
          if (window.getParent() == null) {
            applicationWindows.add(window);
            roleAssigned = true;
          }
          break;
        case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY:
          accessibilityOverlayWindows.add(window);
          roleAssigned = true;
          break;
        case AccessibilityWindowInfo.TYPE_INPUT_METHOD:
          if (!isAlertDialog(window.getId())) {
            inputMethodWindow = window;
            roleAssigned = true;
          }
          break;
        default: // fall out
      }
      if (!roleAssigned) {
        otherWindows.add(window);
      }
    }

    LogDepth.log(
        TAG,
        depth,
        "updateWindowRoles() accessibilityOverlayWindows.size()=%d",
        accessibilityOverlayWindows.size());
    LogDepth.log(
        TAG, depth, "updateWindowRoles() applicationWindows.size()=%d", applicationWindows.size());

    roles.accessibilityOverlayWindowId = WINDOW_ID_NONE;
    // Choose the top-most active overlay window because some a11y overlay is non-active and always
    // on screen with full-transparent mask. For this case, we should skip it and update other roles
    // behind the overlay.
    for (AccessibilityWindowInfo windowInfo : accessibilityOverlayWindows) {
      if (windowInfo.isFocused() && windowInfo.isActive()) {
        roles.accessibilityOverlayWindowId = windowInfo.getId();
        LogDepth.log(TAG, depth, "updateWindowRoles() Accessibility overlay case");
        break;
      }
    }

    roles.picInPicWindowId =
        picInPicWindows.isEmpty() ? WINDOW_ID_NONE : picInPicWindows.get(0).getId();

    roles.inputMethodWindowId =
        inputMethodWindow == null ? WINDOW_ID_NONE : inputMethodWindow.getId();

    if (applicationWindows.isEmpty()) {
      LogDepth.log(TAG, depth, "updateWindowRoles() Zero application windows case");
      roles.windowIdA = WINDOW_ID_NONE;
      roles.windowIdB = WINDOW_ID_NONE;

      // If there is no application window but has other window, report the active window as the
      // current window.
      for (AccessibilityWindowInfo otherWindow : otherWindows) {
        if (otherWindow.isActive()) {
          roles.windowIdA = otherWindow.getId();
          return;
        }
      }
    } else if (applicationWindows.size() == 1) {
      LogDepth.log(TAG, depth, "updateWindowRoles() One application window case");
      roles.windowIdA = applicationWindows.get(0).getId();
      roles.windowIdB = WINDOW_ID_NONE;
    } else if (applicationWindows.size() == 2
        && !hasOverlap(applicationWindows.get(0), applicationWindows.get(1), depth + 1)) {
      LogDepth.log(TAG, depth, "updateWindowRoles() Two application windows case");
      Collections.sort(
          applicationWindows,
          new AccessibilityWindowInfoUtils.WindowPositionComparator(
              WindowUtils.isScreenLayoutRTL(service)));

      roles.windowIdA = applicationWindows.get(0).getId();
      roles.windowIdB = applicationWindows.get(1).getId();
      // Determine the 2 application window placement.
      @Nullable Rect rectA = AccessibilityWindowInfoUtils.getBounds(applicationWindows.get(0));
      @Nullable Rect rectB = AccessibilityWindowInfoUtils.getBounds(applicationWindows.get(1));
      boolean horizontalPlacement =
          (rectA != null && rectB != null)
              && (rectA.left >= rectB.right || rectA.right <= rectB.left);
      interpretation.setHorizontalPlacement(horizontalPlacement);
    } else {
      LogDepth.log(TAG, depth, "updateWindowRoles() Default number of application windows case");
      // If there are more than 2 windows, report the active window as the current window.
      for (AccessibilityWindowInfo applicationWindow : applicationWindows) {
        if (applicationWindow.isActive()) {
          roles.windowIdA = applicationWindow.getId();
          roles.windowIdB = WINDOW_ID_NONE;
          return;
        }
      }
    }
  }

  private static boolean hasOverlap(
      AccessibilityWindowInfo windowA, AccessibilityWindowInfo windowB, int depth) {

    @Nullable Rect rectA = AccessibilityWindowInfoUtils.getBounds(windowA);
    LogDepth.log(TAG, depth, "hasOverlap() windowA=%s rectA=%s", windowA, rectA);
    if (rectA == null) {
      return false;
    }

    @Nullable Rect rectB = AccessibilityWindowInfoUtils.getBounds(windowB);
    LogDepth.log(TAG, depth, "hasOverlap() windowB=%s rectB=%s", windowB, rectB);
    if (rectB == null) {
      return false;
    }

    return Rect.intersects(rectA, rectB);
  }

  /** Updates window titles in windowRoles. */
  private void setWindowTitles(WindowRoles windowRoles) {
    windowRoles.windowTitleA = getWindowTitleInternal(windowRoles.windowIdA);
    windowRoles.windowTitleB = getWindowTitleInternal(windowRoles.windowIdB);
    windowRoles.accessibilityOverlayWindowTitle =
        getWindowTitleInternal(windowRoles.accessibilityOverlayWindowId);
    windowRoles.picInPicWindowTitle = getWindowTitleInternal(windowRoles.picInPicWindowId);
    windowRoles.inputMethodWindowTitle = getWindowTitleInternal(windowRoles.inputMethodWindowId);
  }

  /** Detect window role changes, and turn on flags in interpretation. */
  private void detectWindowChanges(
      WindowRoles roles, EventInterpretation interpretation, int depth) {
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
    LogDepth.log(TAG, depth, "detectWindowChanges()=%s roles=%s", mainWindowsChanged, roles);
    interpretation.setMainWindowsChanged(mainWindowsChanged);
  }

  /**
   * Detects whether the input method window changed. Because there is an old design {@code
   * Announcement} to generate input method feedback, we should check whether there is an
   * announcement send to represent the window transition when {@code checkDuplicate} is true to
   * prevent generate duplicate feedback.
   */
  // TODO  : remove code related to Announcement after intergrating to Gboard done and
  // stable.
  private void detectInputMethodChanged(
      WindowRoles roles, EventInterpretation interpretation, boolean checkDuplicate, int depth) {
    setNewWindowInterpretation(roles.inputMethodWindowId, interpretation.getInputMethod());
    boolean inputMethodChanged = interpretation.getInputMethod().idOrTitleChanged();
    interpretation.setInputMethodChanged(inputMethodChanged);
    interpretation.setShouldAnnounceInputMethodChange(inputMethodChanged);

    if (interpretation.getShouldAnnounceInputMethodChange() && checkDuplicate) {
      Announcement announcement = interpretation.getAnnouncement();
      if (announcement != null && announcement.isFromInputMethodEditor()) {
        // It already has an announcement in IME transition, checks whether they come from the same
        // source or not. If so, use the announcement first. If not, they are supposed to be
        // different transitions and should keep both.
        int inputMethodWindowId = interpretation.getInputMethod().id;
        CharSequence inputMethodPackageName = getWindowPackageName(service, inputMethodWindowId);
        CharSequence announcementPackageName = announcement.packageName();
        if (inputMethodWindowId == WINDOW_ID_NONE
            || (inputMethodPackageName != null
                && announcementPackageName != null
                && inputMethodPackageName.toString().contentEquals(announcementPackageName))) {
          interpretation.setShouldAnnounceInputMethodChange(false);
        }
      }
      // No announcement in IME transition yet, wait and drop the delayed announcements send from
      // IME in 1 sec, i.e. delayed "Keyboard hidden".
      recentKeyboardWindowChange = true;
      windowEventDelayer.sendEmptyMessageDelayed(WindowEventDelayer.MSG_WAIT_ANNOUNCEMENT, 1000);
    }
    LogDepth.log(
        TAG,
        depth,
        "detectInputMethodChanged()=%s",
        interpretation.getShouldAnnounceInputMethodChange());
  }

  /** Returns window title for feedback. */
  public CharSequence getWindowTitleForFeedback(int windowId) {
    // When TB receives CONTENT_CHANGE_TYPE_PANE_APPEARED, the window title will be assigned by the
    // current pane title from the node before it disappears. For a watch device, we should use
    // this pane title to visually distinguish distinct portion of window since such pane would
    // occupy the whole screen, making use ignore the context of background window.
    final boolean windowInfoFirst = !FeatureSupport.isWatch(service);

    return getWindowTitleForFeedback(windowId, getWindowTitleInternal(windowId, windowInfoFirst));
  }

  /** Returns window title by priority: window title > application label > untitled. */
  private CharSequence getWindowTitleForFeedback(int windowId, @Nullable CharSequence title) {
    if (TextUtils.isEmpty(title)) {
      // Try to fall back to application label if window title is not available.
      @Nullable Window window = windowIdToData.get(windowId);
      @Nullable CharSequence packageName = (window == null) ? null : window.eventPackageName;
      // Try to get package name from accessibility window info if it's not in the map.
      if (packageName == null) {
        packageName = getWindowPackageName(service, windowId);
      }
      if (packageName != null) {
        title = getApplicationLabel(packageName);
      }
    }

    if (TextUtils.isEmpty(title)) {
      title = service.getString(R.string.untitled_window);
    }
    return title;
  }

  private static @Nullable CharSequence getWindowPackageName(
      AccessibilityService service, int windowId) {
    @Nullable CharSequence packageName = null;

    List<AccessibilityWindowInfo> windows = getAllWindows(service);
    for (AccessibilityWindowInfo accessibilityWindowInfo : windows) {
      if (accessibilityWindowInfo.getId() == windowId) {
        AccessibilityNodeInfo rootNode =
            AccessibilityWindowInfoUtils.getRoot(accessibilityWindowInfo);
        if (rootNode != null) {
          packageName = rootNode.getPackageName();
        }
        break;
      }
    }
    return packageName;
  }

  private @Nullable CharSequence getApplicationLabel(CharSequence packageName) {
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

  private static List<AccessibilityWindowInfo> getAllWindows(AccessibilityService service) {
    List<AccessibilityWindowInfo> windows = new ArrayList<>();
    SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays =
        AccessibilityServiceCompatUtils.getWindowsOnAllDisplays(service);
    final int displaySize = windowsOnAllDisplays.size();
    for (int i = 0; i < displaySize; i++) {
      windows.addAll(windowsOnAllDisplays.valueAt(i));
    }
    return windows;
  }

  // /////////////////////////////////////////////////////////////////////////////////////
  // Inner classes: event interpretation

  /** Fully interpreted and analyzed window-change event description. */
  public static class EventInterpretation extends ReadOnly {
    private int windowIdFromEvent = WINDOW_ID_NONE;
    private @Nullable Announcement announcement;
    private final WindowInterpretation windowA = new WindowInterpretation();
    private final WindowInterpretation windowB = new WindowInterpretation();
    private final WindowInterpretation accessibilityOverlay = new WindowInterpretation();
    private final WindowInterpretation picInPic = new WindowInterpretation();
    private final WindowInterpretation inputMethod = new WindowInterpretation();
    private boolean mainWindowsChanged = false;
    private boolean picInPicChanged = false;
    private boolean windowsStable = false;
    private boolean originalEvent = false;
    private boolean allowAnnounce = true;
    private boolean inputMethodChanged = false;
    private boolean shouldAnnounceInputMethodChange = false;
    // How does the 2 app-window placement; horizontal/vertical.
    private boolean horizontalPlacement;
    private int displayId = Display.DEFAULT_DISPLAY;
    private int eventType = 0;
    private long eventStartTime = 0;
    private long maxDelayMs = WINDOW_CHANGE_DELAY_MS;
    private long totalDelayMs = 0;
    private boolean interpretFirstTimeWhenWakeUp = false;

    /** Bitmask from getContentChangeTypes() or getWindowChanges(), depending on eventType. */
    private int changeTypes = 0;
    /** The bounds of the source node. */
    private @Nullable Rect sourceBoundsInScreen;

    @RoleName private int anchorNodeRole;

    @Override
    public void setReadOnly() {
      super.setReadOnly();
      windowA.setReadOnly();
      windowB.setReadOnly();
      accessibilityOverlay.setReadOnly();
      picInPic.setReadOnly();
      inputMethod.setReadOnly();
    }

    public void setWindowIdFromEvent(int id) {
      checkIsWritable();
      windowIdFromEvent = id;
    }

    public int getWindowIdFromEvent() {
      return windowIdFromEvent;
    }

    public void setAnnouncement(@Nullable Announcement announcement) {
      checkIsWritable();
      this.announcement = announcement;
    }

    public @Nullable Announcement getAnnouncement() {
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

    public WindowInterpretation getInputMethod() {
      return inputMethod;
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

    /**
     * Sets the value for {@link #getInputMethodChanged()}.
     *
     * <p>Must only be called before this instance is locked.
     */
    public void setInputMethodChanged(boolean changed) {
      checkIsWritable();
      inputMethodChanged = changed;
    }

    /**
     * Sets the value for {@link #getShouldAnnounceInputMethodChange()}.
     *
     * <p>Must only be called before this instance is locked.
     */
    public void setShouldAnnounceInputMethodChange(boolean shouldAnnounce) {
      checkIsWritable();
      shouldAnnounceInputMethodChange = shouldAnnounce;
    }

    /** Returns whether the input method has changed. */
    public boolean getInputMethodChanged() {
      return inputMethodChanged;
    }

    /**
     * Returns whether the input method has changed and an according announcement should be made.
     */
    public boolean getShouldAnnounceInputMethodChange() {
      return shouldAnnounceInputMethodChange;
    }

    public void setHorizontalPlacement(boolean horizontalPlacement) {
      checkIsWritable();
      this.horizontalPlacement = horizontalPlacement;
    }

    public boolean getHorizontalPlacement() {
      return horizontalPlacement;
    }

    public void setWindowsStable(boolean stable) {
      checkIsWritable();
      windowsStable = stable;
    }

    public boolean areWindowsStable() {
      return windowsStable;
    }

    public void setMaxDelay(long millisec) {
      checkIsWritable();
      this.maxDelayMs = millisec;
    }

    public long getMaxDelayMs() {
      return maxDelayMs;
    }

    public void incrementTotalDelayMs(long incrementMs) {
      checkIsWritable();
      totalDelayMs += incrementMs;
    }

    public long getTotalDelayMs() {
      return totalDelayMs;
    }

    public boolean hasTitlesFromStateChange() {
      return windowA.hasTitleFromStateChange()
          && windowB.hasTitleFromStateChange()
          && accessibilityOverlay.hasTitleFromStateChange()
          && picInPic.hasTitleFromStateChange()
          && inputMethod.hasTitleFromStateChange();
    }

    public void setOriginalEvent(boolean original) {
      checkIsWritable();
      originalEvent = original;
    }

    public boolean isOriginalEvent() {
      return originalEvent;
    }

    public void setAllowAnnounce(boolean allowAnnounce) {
      checkIsWritable();
      this.allowAnnounce = allowAnnounce;
    }

    public boolean isAllowAnnounce() {
      return allowAnnounce;
    }

    public void setDisplayId(int displayId) {
      checkIsWritable();
      this.displayId = displayId;
    }

    public int getDisplayId() {
      return displayId;
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

    public void setEventStartTime(long time) {
      checkIsWritable();
      this.eventStartTime = time;
    }

    public long getEventStartTime() {
      return eventStartTime;
    }

    public void setSourceBoundsInScreen(@NonNull Rect bounds) {
      checkIsWritable();
      this.sourceBoundsInScreen = new Rect(bounds);
    }

    public @Nullable Rect getSourceBoundsInScreen() {
      return (sourceBoundsInScreen == null) ? null : new Rect(sourceBoundsInScreen);
    }

    public void setAnchorNodeRole(@RoleName int role) {
      this.anchorNodeRole = role;
    }

    @RoleName
    public int getAnchorNodeRole() {
      return anchorNodeRole;
    }

    public void setInterpretFirstTimeWhenWakeUp(boolean interpretFirstTimeWhenWakeUp) {
      this.interpretFirstTimeWhenWakeUp = interpretFirstTimeWhenWakeUp;
    }

    public boolean isInterpretFirstTimeWhenWakeUp() {
      return interpretFirstTimeWhenWakeUp;
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinSubObjects(
          StringBuilderUtils.joinFields(
              StringBuilderUtils.optionalInt(
                  "WindowIdFromEvent", windowIdFromEvent, WINDOW_ID_NONE),
              StringBuilderUtils.optionalTag("MainWindowsChanged", mainWindowsChanged),
              StringBuilderUtils.optionalTag("PicInPicChanged", picInPicChanged),
              StringBuilderUtils.optionalTag("inputMethodChanged", shouldAnnounceInputMethodChange),
              StringBuilderUtils.optionalTag("horizontalPlacement", horizontalPlacement),
              StringBuilderUtils.optionalTag("WindowsStable", windowsStable),
              StringBuilderUtils.optionalTag("OriginalEvent", originalEvent),
              StringBuilderUtils.optionalTag("allowAnnounce", allowAnnounce),
              StringBuilderUtils.optionalTag(
                  "interpretFirstTimeWhenWakeUp", interpretFirstTimeWhenWakeUp),
              StringBuilderUtils.optionalInt("displayId", displayId, Display.DEFAULT_DISPLAY),
              StringBuilderUtils.optionalField(
                  "EventType",
                  eventType == 0 ? null : AccessibilityEventUtils.typeToString(eventType)),
              StringBuilderUtils.optionalField("ChangeTypes", stateChangesToString()),
              StringBuilderUtils.optionalSubObj("Announcement", announcement),
              StringBuilderUtils.optionalInt("eventStartTime", eventStartTime, 0),
              StringBuilderUtils.optionalInt("maxDelayMs", maxDelayMs, 0),
              StringBuilderUtils.optionalInt("totalDelayMs", totalDelayMs, 0),
              StringBuilderUtils.optionalSubObj("sourceBoundsInScreen", sourceBoundsInScreen)),
          StringBuilderUtils.optionalSubObj("WindowA", windowA),
          StringBuilderUtils.optionalSubObj("WindowB", windowB),
          StringBuilderUtils.optionalSubObj("A11yOverlay", accessibilityOverlay),
          StringBuilderUtils.optionalSubObj("PicInPic", picInPic),
          StringBuilderUtils.optionalSubObj("inputMethod", inputMethod),
          StringBuilderUtils.optionalSubObj("AnchorNodeRole", anchorNodeRole));
    }

    private @Nullable String stateChangesToString() {
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
    private @Nullable CharSequence title;
    private @Nullable CharSequence titleForFeedback;
    private @Nullable CharSequence titleFromStateChange;
    private int oldId = WINDOW_ID_NONE;
    private @Nullable CharSequence oldTitle;

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

    public void setTitle(@Nullable CharSequence title) {
      checkIsWritable();
      this.title = title;
    }

    public @Nullable CharSequence getTitle() {
      return title;
    }

    public void setTitleForFeedback(@Nullable CharSequence title) {
      checkIsWritable();
      titleForFeedback = title;
    }

    public @Nullable CharSequence getTitleForFeedback() {
      return titleForFeedback;
    }

    public void setTitleFromStateChange(@Nullable CharSequence title) {
      checkIsWritable();
      this.titleFromStateChange = title;
    }

    public boolean hasTitleFromStateChange() {
      return !idOrTitleChanged() || (titleFromStateChange != null);
    }

    public void setOldId(int oldId) {
      checkIsWritable();
      this.oldId = oldId;
    }

    public int getOldId() {
      return oldId;
    }

    public void setOldTitle(@Nullable CharSequence oldTitle) {
      checkIsWritable();
      this.oldTitle = oldTitle;
    }

    public @Nullable CharSequence getOldTitle() {
      return oldTitle;
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalInt("ID", id, WINDOW_ID_NONE),
          StringBuilderUtils.optionalText("Title", title),
          StringBuilderUtils.optionalText("titleFromStateChange", titleFromStateChange),
          StringBuilderUtils.optionalText("TitleForFeedback", titleForFeedback),
          StringBuilderUtils.optionalInt("OldID", oldId, WINDOW_ID_NONE),
          StringBuilderUtils.optionalText("OldTitle", oldTitle));
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Logging methods

  private static @Nullable String contentChangeTypesToString(int typesBitmask) {
    StringBuilder strings = new StringBuilder();
    if ((typesBitmask & CONTENT_CHANGE_TYPE_PANE_TITLE) != 0) {
      strings.append("CONTENT_CHANGE_TYPE_PANE_TITLE");
    }
    if ((typesBitmask & CONTENT_CHANGE_TYPE_PANE_APPEARED) != 0) {
      strings.append("CONTENT_CHANGE_TYPE_PANE_APPEARED");
    }
    if ((typesBitmask & CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) != 0) {
      strings.append("CONTENT_CHANGE_TYPE_PANE_DISAPPEARED");
    }
    return (strings.length() == 0) ? null : strings.toString();
  }

  @TargetApi(Build.VERSION_CODES.P)
  private static @Nullable String windowChangeTypesToString(int typesBitmask) {
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
