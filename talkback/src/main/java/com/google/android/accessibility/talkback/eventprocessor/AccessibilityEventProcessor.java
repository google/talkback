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

import static com.google.android.accessibility.talkback.eventprocessor.EventState.EVENT_SKIP_FOCUS_SYNC_FROM_VIEW_FOCUSED;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.RingerModeAndScreenMonitor;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.VoiceActionMonitor;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.monitor.DisplayMonitor;
import com.google.android.accessibility.utils.monitor.DisplayMonitor.DisplayStateChangedListener;
import com.google.android.accessibility.utils.output.Utterance;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.initialization.qual.UnderInitialization;

/** Runs a collection of AccessibilityEventListeners on each event. */
public class AccessibilityEventProcessor implements DisplayStateChangedListener {
  private static final String TAG = "A11yEventProcessor";
  private static final String DUMP_EVENT_LOG_FORMAT = "A11yEventDumper: %s";
  private TalkBackListener testingListener;

  /** Event types to drop after receiving a window state change. */
  public static final int AUTOMATIC_AFTER_STATE_CHANGE =
      AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED;

  /**
   * Event types that signal a change in touch interaction state and should be dropped on {@link
   * Configuration#TOUCHSCREEN_NOTOUCH} devices
   */
  private static final int MASK_EVENT_TYPES_TOUCH_STATE_CHANGES =
      AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START
          | AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END
          | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START
          | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END
          | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEvent.TYPE_VIEW_HOVER_EXIT;

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
  private ActorState actorState;
  private final AccessibilityManager accessibilityManager;
  private VoiceActionMonitor voiceActionMonitor;
  private RingerModeAndScreenMonitor ringerModeAndScreenMonitor;
  private final DelayedEventHandler handler;

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
  private final List<AccessibilityEventListener> accessibilityEventListeners = new ArrayList<>();

  private long lastWindowStateChanged;
  private AccessibilityEvent lastFocusedEvent;

  private boolean speakWhenScreenOff = false;

  // Use bit mask to note what types of accessibility events should dump.
  private int dumpEventMask = 0;

  // Default is true since we assume when it receive a11y event without callback invocation, the
  // default display should be on.
  private boolean defaultDisplayOn = true;
  private final DisplayMonitor displayMonitor;

  /**
   * Callback interface for the idle state when {@link AccessibilityEventProcessor} doesn't receive
   * {@link AccessibilityEvent} for a while.
   */
  public interface AccessibilityEventIdleListener {
    /** The time threshold of idle state for not receiving any {@link AccessibilityEvent}. */
    int ACCESSIBILITY_EVENT_IDLE_STATE_MS = 3000;

    /** Callback method for the idle state. */
    void onIdle();
  }

  public AccessibilityEventProcessor(TalkBackService service, DisplayMonitor displayMonitor) {
    accessibilityManager =
        (AccessibilityManager) service.getSystemService(Context.ACCESSIBILITY_SERVICE);

    this.service = service;
    this.displayMonitor = displayMonitor;
    initDumpEventMask();
    handler = new DelayedEventHandler(this);
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

  public void onResumeInfrastructure() {
    displayMonitor.addDisplayStateChangedListener(this);
  }

  public void onSuspendInfrastructure() {
    displayMonitor.removeDisplayStateChangedListener(this);
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
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

  public void setAccessibilityEventIdleListener(AccessibilityEventIdleListener listener) {
    handler.setAccessibilityEventIdleListener(listener);
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

    if (defaultDisplayOn) {
      handler.refreshIdleMessage();
    }
  }

  @Override
  public void onDisplayStateChanged(boolean displayOn) {
    defaultDisplayOn = displayOn;
    if (!defaultDisplayOn) {
      // If the display is off, we don't need to do it anymore.
      handler.removeIdleMessage();
    }
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
   * @param event The current event.
   * @return {@code true} if the event should be dropped.
   */
  private boolean shouldDropRefocusEvent(AccessibilityEvent event) {
    int eventType = event.getEventType();
    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
      if (getSourceNodeIdMethod != null) {
        try {
          lastClearedSourceId = (long) getSourceNodeIdMethod.invoke(event);
          lastClearedWindowId = event.getWindowId();
          lastClearA11yFocus = System.currentTimeMillis();
          if (lastClearedSourceId != lastPronouncedSourceId
              || lastClearedWindowId != lastPronouncedWindowId) {
            // something strange. not accessibility focused node sends clear focus event
            // REFERTO
            lastClearedSourceId = -1;
            lastClearedWindowId = -1;
            lastClearA11yFocus = 0;
          }
        } catch (Exception e) {
          LogUtils.d(TAG, "Exception accessing field: " + e.toString());
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
              && !actorState.getFocusHistory().isEventFromFocusManagement(event)) {
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

    // Do not drop TYPE_SPEECH_STATE_CHANGE event to avoid SpeechStateMonitor has any missing.
    if (event.getEventType() == AccessibilityEvent.TYPE_SPEECH_STATE_CHANGE) {
      return false;
    }

    // If touch exploration is enabled, drop automatically generated events
    // that are sent immediately after a window state change... unless we
    // decide to keep the event.
    if (accessibilityManager.isTouchExplorationEnabled()
        && ((event.getEventType() & AUTOMATIC_AFTER_STATE_CHANGE) != 0)
        && ((event.getEventTime() - lastWindowStateChanged) < DELAY_AUTO_AFTER_STATE)
        && !shouldKeepAutomaticEvent(event)) {
      LogUtils.v(
          TAG,
          "Drop event after window state change (event=%s, lastWindowStateChanged=%d)",
          event,
          lastWindowStateChanged);
      return true;
    }

    // Some view-selected events are spurious if sent immediately after a focused event.
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED
        && !shouldKeepViewSelectedEvent(event)) {
      LogUtils.v(TAG, "Drop selected event after focused event");
      return true;
    }

    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
        && EventState.getInstance()
            .checkAndClearRecentFlag(EVENT_SKIP_FOCUS_SYNC_FROM_VIEW_FOCUSED)) {
      LogUtils.v(TAG, "Drop view focused event due to EventState");
      return true;
    }

    // TYPE_VIEW_FOCUSED is received even if the input focus is on a non-focused window.
    // On TV, we want to ignore such events. Since the Accessibility API does not correctly report
    // the window focus, we have to work around that. One of the rare cases where we have multiple
    // windows open, is when the picture-in-picture mode is active.
    if (FormFactorUtils.getInstance().isAndroidTv()
        && event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
        && isPipFocused()
        && event.getSource() != null
        && event.getSource().getWindowId()
            != AccessibilityServiceCompatUtils.getPipWindow(service).getId()) {
      LogUtils.v(
          TAG, "Drop view focused event due to focus being on PIP and event not coming from PIP");
      return true;
    }

    boolean isPhoneRinging =
        voiceActionMonitor != null
            && voiceActionMonitor.getCurrentCallState() == TelephonyManager.CALL_STATE_RINGING;

    // Sometimes the dialer's window-state-changed event gets sent right before the
    // TelephonyManager transitions to CALL_STATE_RINGING, so we need to check isDialerEvent().
    isPhoneRinging |= isDialerEvent(event);

    // In most of the cases, Android system will drop all touch event when the screen is off (not
    // interactive). So, TalkBack also drops accessibility events because the phone is unusable to
    // users in this case.
    // One exception is AoD (always on display) mode, the screen state is off (not interactive) but
    // users may still be able to interact with the screen. For example, charging on a Pixel Stand.
    // In that kind of cases we use the state of Display to check the screen is interactive or not.
    // REFERTO for details.
    // System UI placed live regions in keyguard screen. When device put in pixel stand, it will
    // announce the charging status for any changes which is regarded as annoying.
    // Has communicated with owner who's going to fix in R. So the AOD support should be put off
    // until R.
    // REFERTO for details
    boolean isScreenInteractive =
        ringerModeAndScreenMonitor == null
            || ringerModeAndScreenMonitor.isInteractive()
            || (BuildVersionUtils.isAtLeastR() && ringerModeAndScreenMonitor.isDefaultDisplayOn());
    // AoD mode: isInteractive() is false, but isDefaultDisplayOn() is true

    if (!isScreenInteractive && !isPhoneRinging) {
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

    final int touchscreenState = service.getResources().getConfiguration().touchscreen;
    final boolean isTouchInteractionStateChange =
        AccessibilityEventUtils.eventMatchesAnyType(event, MASK_EVENT_TYPES_TOUCH_STATE_CHANGES);

    // Drop all events related to touch interaction state on devices that don't support touch.
    return (touchscreenState == Configuration.TOUCHSCREEN_NOTOUCH) && isTouchInteractionStateChange;
  }

  /** Helper method for checking if the pip window is both open and focused. */
  @VisibleForTesting
  boolean isPipFocused() {
    AccessibilityWindowInfo window = AccessibilityServiceCompatUtils.getPipWindow(service);
    return (window != null)
        && (window.getRoot() != null)
        && (window.getRoot().findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) != null);
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

      AccessibilityNodeInfoCompat node = record.getSource();
      if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
        return true;
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
    AccessibilityNodeInfo selectedSource = event.getSource();
    AccessibilityNodeInfo focusedSource = lastFocusedEvent.getSource();

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
    if (!accessibilityEventListeners.contains(listener)) {
      return;
    }
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

  /** Interface that provides talkback callback */
  public interface TalkBackListener {
    void onAccessibilityEvent(AccessibilityEvent event);

    void afterAccessibilityEvent(AccessibilityEvent event);

    // TODO: Solve this by making a fake tts and look for calls into it instead
    void onUtteranceQueued(Utterance utterance);
  }

  private static class DelayedEventHandler
      extends WeakReferenceHandler<AccessibilityEventProcessor> {

    public static final int MESSAGE_WHAT_PROCESS_EVENT = 1;
    public static final int MESSAGE_WHAT_PROCESSOR_IDLE = 2;

    private AccessibilityEventIdleListener accessibilityEventIdleListener;

    DelayedEventHandler(@UnderInitialization AccessibilityEventProcessor parent) {
      super(parent, Looper.myLooper());
    }

    @Override
    public void handleMessage(Message message, AccessibilityEventProcessor parent) {
      switch (message.what) {
        case MESSAGE_WHAT_PROCESS_EVENT:
          if (message.obj == null) {
            return;
          }
          @SuppressWarnings("unchecked")
          EventIdAnd<AccessibilityEvent> eventAndId = (EventIdAnd<AccessibilityEvent>) message.obj;
          AccessibilityEvent event = eventAndId.object;
          parent.processEvent(event, eventAndId.eventId);
          break;

        case MESSAGE_WHAT_PROCESSOR_IDLE:
          if (accessibilityEventIdleListener != null) {
            LogUtils.d(TAG, "Processor idle state.");
            accessibilityEventIdleListener.onIdle();
          }
          break;
      }
    }

    public void postProcessEvent(AccessibilityEvent event, EventId eventId) {
      AccessibilityEvent eventCopy = AccessibilityEvent.obtain(event);
      EventIdAnd<AccessibilityEvent> eventAndId =
          new EventIdAnd<AccessibilityEvent>(eventCopy, eventId);
      Message msg = obtainMessage(MESSAGE_WHAT_PROCESS_EVENT, eventAndId);
      sendMessageDelayed(msg, EVENT_PROCESSING_DELAY);
    }

    public void refreshIdleMessage() {
      removeMessages(MESSAGE_WHAT_PROCESSOR_IDLE);
      sendEmptyMessageDelayed(
          MESSAGE_WHAT_PROCESSOR_IDLE,
          AccessibilityEventIdleListener.ACCESSIBILITY_EVENT_IDLE_STATE_MS);
    }

    public void removeIdleMessage() {
      removeMessages(MESSAGE_WHAT_PROCESSOR_IDLE);
    }

    public void setAccessibilityEventIdleListener(AccessibilityEventIdleListener listener) {
      accessibilityEventIdleListener = listener;
    }
  }
}
