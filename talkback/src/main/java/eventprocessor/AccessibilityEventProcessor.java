/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.accessibility.talkback.eventprocessor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityManagerCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.RingerModeAndScreenMonitor;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.VoiceActionMonitor;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.Utterance;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Runs a collection of AccessibilityEventListeners on each event. */
public class AccessibilityEventProcessor {
  private static final String TAG = "A11yEventProcessor";
  private static final String DUMP_EVENT_LOG_FORMAT = "A11yEventDumper: %s";
  private TalkBackListener testingListener;

  /**
   * Used to not drop TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED for the below android versions to fix
   * permissions dialog overlay
   */
  private static final boolean API_REQUIRES_PERMISSION_OVERLAY = !BuildVersionUtils.isAtLeastNMR1();

  /** Event types that are allowed to interrupt radial menus. */
  // TODO: What's the rationale for HOVER_ENTER? Navigation bar?
  private static final int MASK_EVENT_TYPES_INTERRUPT_RADIAL_MENU =
      AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEventCompat.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY;

  /** Event types to drop after receiving a window state change. */
  public static final int AUTOMATIC_AFTER_STATE_CHANGE =
      AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEventCompat.TYPE_VIEW_SCROLLED;

  /**
   * Event types that signal a change in touch interaction state and should be dropped on {@link
   * Configuration#TOUCHSCREEN_NOTOUCH} devices
   */
  private static final int MASK_EVENT_TYPES_TOUCH_STATE_CHANGES =
      AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START
          | AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END
          | AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START
          | AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END
          | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END
          | AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT;

  /**
   * Event types that should be processed with a very minor delay in order to wait for state to
   * catch up. The delay time is specified by {@link #EVENT_PROCESSING_DELAY}.
   */
  private static final int MASK_DELAYED_EVENT_TYPES = AccessibilityEvent.TYPE_VIEW_CLICKED;

  /**
   * The minimum delay between window state change and automatic events. Note that this delay
   * doesn't affect response to user actions, so it is OK if it is a tad long.
   */
  public static final long DELAY_AUTO_AFTER_STATE = 200;

  /**
   * The minimum delay after a focus event that a selected event can be processed on the same branch
   * of the accessibility node tree. Note that this delay doesn't affect response to user actions,
   * so it is OK if it is a tad long.
   */
  public static final long DELAY_SELECTED_AFTER_FOCUS = 200;

  /**
   * Delay (ms) to wait for the state to catch up before processing events that match the mask
   * {@link #MASK_DELAYED_EVENT_TYPES}. This delay should be nearly imperceptible; practical testing
   * has determined that the minimum delay is ~150ms, but a 150ms delay should be barely
   * perceptible. The 150ms delay has been tested on a variety of Nexus/non-Nexus devices.
   */
  public static final long EVENT_PROCESSING_DELAY = 150;

  static final String CLASS_DIALER = "com.android.incallui.InCallActivity";

  private final TalkBackService service;
  private AccessibilityManager accessibilityManager;
  private VoiceActionMonitor voiceActionMonitor;
  private RingerModeAndScreenMonitor ringerModeAndScreenMonitor;
  private DelayedEventHandler handler = new DelayedEventHandler();

  private static Method getSourceNodeIdMethod;

  private long lastClearedSourceId = -1;
  private int lastClearedWindowId = -1;
  private long lastClearA11yFocus = System.currentTimeMillis();
  private long lastPronouncedSourceId = -1;
  private int lastPronouncedWindowId = -1;

  // If the same node is cleared and set inside this time we ignore the events
  private static final long CLEAR_SET_A11Y_FOCUS_WINDOW = 1000;

  static {
    try {
      getSourceNodeIdMethod = AccessibilityRecord.class.getDeclaredMethod("getSourceNodeId");
      getSourceNodeIdMethod.setAccessible(true);
    } catch (NoSuchMethodException e) {
      LogUtils.d(TAG, "Error setting up fields: " + e.toString());
      e.printStackTrace();
    }
  }

  /**
   * List of passive event processors. All processors in the list are sent the event in the order
   * they were added.
   */
  private List<AccessibilityEventListener> accessibilityEventListeners = new ArrayList<>();

  private long lastWindowStateChanged;
  private AccessibilityEvent lastFocusedEvent;

  private boolean speakWhenScreenOff = false;

  // Use bit mask to note what types of accessibility events should dump.
  private int dumpEventMask = 0;

  public AccessibilityEventProcessor(TalkBackService service) {
    accessibilityManager =
        (AccessibilityManager) service.getSystemService(Context.ACCESSIBILITY_SERVICE);

    this.service = service;
    initDumpEventMask();
  }

  /** Read dump event configuration from preferences. */
  private void initDumpEventMask() {
    int[] eventTypes = AccessibilityEventUtils.getAllEventTypes();
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(service);
    for (int type : eventTypes) {
      String prefKey = service.getString(R.string.pref_dump_event_key_prefix, type);
      if (sharedPreferences.getBoolean(prefKey, false)) {
        dumpEventMask |= type;
      }
    }
  }

  public void setSpeakWhenScreenOff(boolean speak) {
    speakWhenScreenOff = speak;
  }

  /* Used to cache instance of VoiceActionMonitor. */
  public void setVoiceActionMonitor(VoiceActionMonitor voiceActionMonitor) {
    this.voiceActionMonitor = voiceActionMonitor;
  }

  public void setRingerModeAndScreenMonitor(RingerModeAndScreenMonitor ringerModeAndScreenMonitor) {
    this.ringerModeAndScreenMonitor = ringerModeAndScreenMonitor;
  }

  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {

    if (testingListener != null) {
      testingListener.onAccessibilityEvent(event);
    }

    if ((dumpEventMask & event.getEventType()) != 0) {
      LogUtils.v(TAG, DUMP_EVENT_LOG_FORMAT, event);
    }

    if (shouldDropRefocusEvent(event)) {
      return;
    }

    if (shouldDropEvent(event)) {
      return;
    }

    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      lastWindowStateChanged = SystemClock.uptimeMillis();
    }

    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      service.setRootDirty(true);
    }

    // We need to save the last focused event so that we can filter out related selected events.
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
      if (lastFocusedEvent != null) {
        lastFocusedEvent.recycle();
      }

      lastFocusedEvent = AccessibilityEvent.obtain(event);
    }

    if (AccessibilityEventUtils.eventMatchesAnyType(event, MASK_DELAYED_EVENT_TYPES)) {
      handler.postProcessEvent(event, eventId);
    } else {
      processEvent(event, eventId);
    }

    if (testingListener != null) {
      testingListener.afterAccessibilityEvent(event);
    }
  }

  // TODO: When FocusManagement is implemented, we should revisit the way how we drop
  // accessibility focus events.
  private boolean isFromFocusManagement(AccessibilityEvent event) {
    return service.getAccessibilityFocusManager().isEventFromFocusManagement(event);
  }

  /**
   * Returns whether the device should drop this event due to refocus issue. Sometimes TalkBack will
   * receive four consecutive events from one single node:. 1. Accessibility_Focus_Cleared 2.
   * Accessibility_Focused 3. Accessibility_Focus_Cleared 4. Accessibility_Focused
   *
   * <p>The cause of this issue could be: i. Chrome clears and set a11y focus for each scroll event.
   * If it is an action to navigate to previous/next element and causes view scrolling. The first
   * two events are caused by navigation, and the last two events are caused by chrome refocus
   * issue. The last two events are not intended to be spoken. If it is a scroll action. It might
   * cause a lot of a11y_focus_cleared and a11y_focused events. In this case all the events are not
   * intended to be spoken.
   *
   * <p>ii. User taps on screen to refocus on the a11y focused node. In this case event 2 and 4
   * should be spoken to the user.
   *
   * <p>If the event is received from the ALLOW_BUTTON of ProcessorPermissionDialogs, the event need
   * not be dropped and needs to be sent to ProcessorPermissionDialogs to clear the permissions
   * overlay. This is required only for API level M to N.
   *
   * @param event The current event.
   * @return {@code true} if the event should be dropped.
   */
  private boolean shouldDropRefocusEvent(AccessibilityEvent event) {
    int eventType = event.getEventType();
    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
      if (getSourceNodeIdMethod != null) {
        AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        AccessibilityNodeInfoCompat source = record.getSource();
        try {
          lastClearedSourceId = (long) getSourceNodeIdMethod.invoke(event);
          lastClearedWindowId = event.getWindowId();
          lastClearA11yFocus = System.currentTimeMillis();
          if (lastClearedSourceId != lastPronouncedSourceId
              || lastClearedWindowId != lastPronouncedWindowId) {
            // something strange. not accessibility focused node sends clear focus event
            // 
            lastClearedSourceId = -1;
            lastClearedWindowId = -1;
            lastClearA11yFocus = 0;
          }
          // The event needs to be sent to ProcessorPermissionDialogs
          // to clear the overlay
          if (source != null
              && API_REQUIRES_PERMISSION_OVERLAY
              && TextUtils.equals(
                  ProcessorPermissionDialogs.ALLOW_BUTTON, source.getViewIdResourceName())) {
            return false;
          }
        } catch (Exception e) {
          LogUtils.d(TAG, "Exception accessing field: " + e.toString());
        } finally {
          AccessibilityNodeInfoUtils.recycleNodes(source);
        }
      }
      return true;
    }

    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      if (getSourceNodeIdMethod != null
          && !EventState.getInstance().checkAndClearRecentFlag(EventState.EVENT_NODE_REFOCUSED)) {
        try {
          long sourceId = (long) getSourceNodeIdMethod.invoke(event);
          int windowId = event.getWindowId();
          // If this event is fired by the "clear and set a11y focus" issue of Chrome,
          // ignore and don't speak to the user, otherwise update the node and window IDs
          // and then process the event.
          if (System.currentTimeMillis() - lastClearA11yFocus < CLEAR_SET_A11Y_FOCUS_WINDOW
              && sourceId == lastClearedSourceId
              && windowId == lastClearedWindowId
              && !isFromFocusManagement(event)) {
            return true;
          } else {
            lastPronouncedSourceId = sourceId;
            lastPronouncedWindowId = windowId;
          }
        } catch (Exception e) {
          LogUtils.d(TAG, "Exception accessing field: " + e.toString());
        }
      }
    }
    return false;
  }

  /**
   * Returns whether the device should drop this event. Caches notifications if necessary.
   *
   * @param event The current event.
   * @return {@code true} if the event should be dropped.
   */
  private boolean shouldDropEvent(AccessibilityEvent event) {
    // Always drop null events.
    if (event == null) {
      return true;
    }

    // Always drop events if the service is suspended.
    if (!TalkBackService.isServiceActive()) {
      return true;
    }

    // If touch exploration is enabled, drop automatically generated events
    // that are sent immediately after a window state change... unless we
    // decide to keep the event.
    if (AccessibilityManagerCompat.isTouchExplorationEnabled(accessibilityManager)
        && ((event.getEventType() & AUTOMATIC_AFTER_STATE_CHANGE) != 0)
        && ((event.getEventTime() - lastWindowStateChanged) < DELAY_AUTO_AFTER_STATE)
        && !shouldKeepAutomaticEvent(event)) {
      LogUtils.v(TAG, "Drop event after window state change");
      return true;
    }

    // Some view-selected events are spurious if sent immediately after a focused event.
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED
        && !shouldKeepViewSelectedEvent(event)) {
      LogUtils.v(TAG, "Drop selected event after focused event");
      return true;
    }

    boolean isPhoneRinging =
        voiceActionMonitor != null
            && voiceActionMonitor.getCurrentCallState() == TelephonyManager.CALL_STATE_RINGING;

    // Sometimes the dialer's window-state-changed event gets sent right before the
    // TelephonyManager transitions to CALL_STATE_RINGING, so we need to check isDialerEvent().
    isPhoneRinging |= isDialerEvent(event);

    if (ringerModeAndScreenMonitor != null
        && !ringerModeAndScreenMonitor.isScreenOn()
        && !isPhoneRinging) {
      boolean isNotification = AccessibilityEventUtils.isNotificationEvent(event);
      if (!speakWhenScreenOff) {
        // If the user doesn't allow speech when the screen is
        // off, drop the event immediately.
        LogUtils.v(TAG, "Drop event due to screen state and user pref");
        return true;
      } else if (!isNotification) {
        // If the user allows speech when the screen is off, drop
        // all non-notification events.
        LogUtils.v(TAG, "Drop non-notification event due to screen state");
        return true;
      }
    }

    final boolean canInterruptRadialMenu =
        AccessibilityEventUtils.eventMatchesAnyType(event, MASK_EVENT_TYPES_INTERRUPT_RADIAL_MENU);
    final boolean silencedByRadialMenu =
        (service.getMenuManager().isMenuShowing() && !canInterruptRadialMenu);

    // Don't speak events that cannot interrupt the radial menu, if showing
    if (silencedByRadialMenu) {
      LogUtils.v(TAG, "Drop event due to radial menu state");
      return true;
    }

    final int touchscreenState = service.getResources().getConfiguration().touchscreen;
    final boolean isTouchInteractionStateChange =
        AccessibilityEventUtils.eventMatchesAnyType(event, MASK_EVENT_TYPES_TOUCH_STATE_CHANGES);

    // Drop all events related to touch interaction state on devices that don't support touch.
    return (touchscreenState == Configuration.TOUCHSCREEN_NOTOUCH) && isTouchInteractionStateChange;
  }

  /**
   * Helper method for {@link #shouldDropEvent} that handles events that automatically occur
   * immediately after a window state change.
   *
   * @param event The automatically generated event to consider retaining.
   * @return Whether to retain the event.
   */
  private boolean shouldKeepAutomaticEvent(AccessibilityEvent event) {
    final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);

    // Don't drop focus events from EditTexts.
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
      AccessibilityNodeInfoCompat node = null;

      try {
        node = record.getSource();
        if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
          return true;
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }
    }

    return false;
  }

  /**
   * Helper method for {@link #shouldDropEvent} that filters out selected events that occur in close
   * proximity to focused events.
   *
   * <p>A selected event should be kept if: - The most recent focused event occurred over {@link
   * #DELAY_SELECTED_AFTER_FOCUS} ms ago. - The most recent focused event occurred on a different
   * branch of the accessibility node tree, i.e., not in an ancestor or descendant of the selected
   * event.
   *
   * @param event The view-selected event to consider retaining.
   * @return Whether to retain the event.
   */
  private boolean shouldKeepViewSelectedEvent(final AccessibilityEvent event) {
    if (lastFocusedEvent == null) {
      return true;
    }

    if (event.getEventTime() - lastFocusedEvent.getEventTime() > DELAY_SELECTED_AFTER_FOCUS) {
      return true;
    }

    // AccessibilityEvent.getSource will obtain() an AccessibilityNodeInfo, so it is our
    // responsibility to recycle() it.
    AccessibilityNodeInfo selectedSource = event.getSource();
    AccessibilityNodeInfo focusedSource = lastFocusedEvent.getSource();

    try {
      // Note: AccessibilityNodeInfoCompat constructor will silently succeed when wrapping
      // a null object.
      if (selectedSource != null && focusedSource != null) {
        AccessibilityNodeInfoCompat selectedSourceCompat =
            AccessibilityNodeInfoUtils.toCompat(selectedSource);
        AccessibilityNodeInfoCompat focusedSourceCompat =
            AccessibilityNodeInfoUtils.toCompat(focusedSource);

        if (AccessibilityNodeInfoUtils.areInSameBranch(selectedSourceCompat, focusedSourceCompat)) {
          return false;
        }
      }

      // In different branch (or we could not check branches of accessibility node tree).
      return true;
    } finally {
      if (selectedSource != null) {
        selectedSource.recycle();
      }
      if (focusedSource != null) {
        focusedSource.recycle();
      }
    }
  }

  /**
   * Helper method for {@link #shouldDropEvent} to determine whether an event is the phone dialer
   * appearing for an incoming call.
   *
   * @param event The event to check.
   * @return Whether the event represents an incoming call on the phone dialer.
   */
  private boolean isDialerEvent(final AccessibilityEvent event) {
    return event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        && TextUtils.equals(CLASS_DIALER, event.getClassName());
  }

  /**
   * Passes the event to all registered {@link AccessibilityEventListener}s in the order they were
   * added.
   *
   * @param event The current event.
   */
  private void processEvent(AccessibilityEvent event, EventId eventId) {
    // Log the listeners for this event type.
    if (LogUtils.getLogLevel() <= Log.VERBOSE) {
      StringBuilder handlerNames = new StringBuilder();
      for (AccessibilityEventListener eventListener : accessibilityEventListeners) {
        int eventTypesHandled = eventListener.getEventTypes();
        if (AccessibilityEventUtils.eventMatchesAnyType(event, eventTypesHandled)) {
          handlerNames.append((handlerNames.length() == 0) ? "" : ","); // Delimiter
          handlerNames.append(eventListener.getClass().getSimpleName());
        }
      }
      LogUtils.v(TAG, "Event listeners: %s", handlerNames);
    }

    // Send events to the only those processors which handle them.
    for (AccessibilityEventListener eventProcessor : accessibilityEventListeners) {
      int eventTypesHandled = eventProcessor.getEventTypes();
      if (AccessibilityEventUtils.eventMatchesAnyType(event, eventTypesHandled)) {
        eventProcessor.onAccessibilityEvent(event, eventId);
      }
    }
  }

  public void addAccessibilityEventListener(AccessibilityEventListener listener) {
    accessibilityEventListeners.add(listener);
  }

  public void postRemoveAccessibilityEventListener(final AccessibilityEventListener listener) {
    new Handler()
        .post(
            new Runnable() {
              @Override
              public void run() {
                accessibilityEventListeners.remove(listener);
              }
            });
  }

  public void setDumpEventMask(int dumpEventMask) {
    this.dumpEventMask = dumpEventMask;
  }

  public void setTestingListener(TalkBackListener testingListener) {
    this.testingListener = testingListener;
  }

  public interface TalkBackListener {
    void onAccessibilityEvent(AccessibilityEvent event);

    void afterAccessibilityEvent(AccessibilityEvent event);
    // TODO: Solve this by making a fake tts and look for calls into it instead
    void onUtteranceQueued(Utterance utterance);
  }

  private class DelayedEventHandler extends Handler {

    public static final int MESSAGE_WHAT_PROCESS_EVENT = 1;

    @Override
    public void handleMessage(Message message) {
      if (message.what != MESSAGE_WHAT_PROCESS_EVENT || message.obj == null) {
        return;
      }

      @SuppressWarnings("unchecked")
      EventIdAnd<AccessibilityEvent> eventAndId = (EventIdAnd<AccessibilityEvent>) message.obj;
      AccessibilityEvent event = eventAndId.object;
      processEvent(event, eventAndId.eventId);
      event.recycle();
    }

    public void postProcessEvent(AccessibilityEvent event, EventId eventId) {
      AccessibilityEvent eventCopy = AccessibilityEvent.obtain(event);
      EventIdAnd<AccessibilityEvent> eventAndId =
          new EventIdAnd<AccessibilityEvent>(eventCopy, eventId);
      Message msg = obtainMessage(MESSAGE_WHAT_PROCESS_EVENT, eventAndId);
      sendMessageDelayed(msg, EVENT_PROCESSING_DELAY);
    }
  }
}
