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

package com.android.talkback.eventprocessor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import com.android.talkback.CallStateMonitor;
import com.android.talkback.R;
import com.android.talkback.RingerModeAndScreenMonitor;
import com.android.talkback.formatter.ClickFormatter;
import com.android.utils.Role;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

public class AccessibilityEventProcessor {
    private static final String LOGTAG = "A11yEventProcessor";
    private static final String DUMP_EVNET_LOG_TAG = "EventDumper";
    private TalkBackListener mTestingListener;

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
     * Event types that signal a change in touch interaction state and should be
     * dropped on {@link Configuration#TOUCHSCREEN_NOTOUCH} devices
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
     *
     * Note: On Lollipop, the {@link ClickFormatter} is used and needs a short processing delay.
     * On KitKat, the legacy {@link com.android.talkback.formatter.CheckableClickedFormatter} is
     * used and needs no delay.
     */
    private static final int MASK_DELAYED_EVENT_TYPES =
            Build.VERSION.SDK_INT >= ClickFormatter.MIN_API_LEVEL ?
                    AccessibilityEvent.TYPE_VIEW_CLICKED : 0;

    /**
     * The minimum delay between window state change and automatic events. Note that this delay
     * doesn't affect response to user actions, so it is OK if it is a tad long.
     */
    public static final long DELAY_AUTO_AFTER_STATE = 200;

    /**
     * The minimum delay after a focus event that a selected event can be processed on the same
     * branch of the accessibility node tree. Note that this delay doesn't affect response to user
     * actions, so it is OK if it is a tad long.
     */
    public static final long DELAY_SELECTED_AFTER_FOCUS = 200;

    /**
     * Delay (ms) to wait for the state to catch up before processing events that match the mask
     * {@link #MASK_DELAYED_EVENT_TYPES}. This delay should be nearly imperceptible; practical
     * testing has determined that the minimum delay is ~150ms, but a 150ms delay should be barely
     * perceptible. The 150ms delay has been tested on a variety of Nexus/non-Nexus devices.
     */
    public static final long EVENT_PROCESSING_DELAY = 150;

    static final String CLASS_DIALER_JELLY_BEAN = "com.android.phone.InCallScreen";
    static final String CLASS_DIALER_KITKAT = "com.android.incallui.InCallActivity";

    private final TalkBackService mService;
    private AccessibilityManager mAccessibilityManager;
    private CallStateMonitor mCallStateMonitor;
    private ProcessorEventQueue mProcessorEventQueue;
    private ProcessorFocusAndSingleTap mProcessorFocusAndSingleTap;
    private RingerModeAndScreenMonitor mRingerModeAndScreenMonitor;
    private DelayedEventHandler mHandler = new DelayedEventHandler();

    private static Method sGetSourceNodeIdMethod;

    private long mLastClearedSourceId = -1;
    private int mLastClearedWindowId = -1;
    private long mLastClearA11yFocus = System.currentTimeMillis();
    private long mLastPronouncedSourceId = -1;
    private int mLastPronouncedWindowId = -1;

    // If the same node is cleared and set inside this time we ignore the events
    private static final long CLEAR_SET_A11Y_FOCUS_WINDOW = 1000;

    static {
        try {
            sGetSourceNodeIdMethod = AccessibilityRecord.class.getDeclaredMethod("getSourceNodeId");
            sGetSourceNodeIdMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            Log.d(LOGTAG, "Error setting up fields: " + e.toString());
            e.printStackTrace();
        }
    }

    /**
     * List of passive event processors. All processors in the list are sent the
     * event in the order they were added.
     */
    private List<AccessibilityEventListener> mAccessibilityEventListeners = new LinkedList<>();

    private boolean mIsUserTouchExploring;
    private long mLastWindowStateChanged;
    private AccessibilityEvent mLastFocusedEvent;

    private boolean mSpeakWhenScreenOff = false;

    // Use bit mask to note what types of accessibility events should dump.
    private int mDumpEventMask = 0;

    public AccessibilityEventProcessor(TalkBackService service) {
        mAccessibilityManager =
                (AccessibilityManager) service.getSystemService(Context.ACCESSIBILITY_SERVICE);

        mService = service;
        initDumpEventMask();
    }

    /**
     * Read dump event configuration from preferences.
     */
    private void initDumpEventMask() {
        int[] eventTypes = AccessibilityEventUtils.getAllEventTypes();
        SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(mService);
        for (int type : eventTypes) {
            String prefKey = mService.getString(R.string.pref_dump_event_key_prefix, type);
            if (sharedPreferences.getBoolean(prefKey, false)) {
                mDumpEventMask |= type;
            }
        }
    }

    public void setSpeakWhenScreenOff(boolean speak) {
        mSpeakWhenScreenOff = speak;
    }

    public void setCallStateMonitor(CallStateMonitor callStateMonitor) {
        mCallStateMonitor = callStateMonitor;
    }

    public void setRingerModeAndScreenMonitor(
            RingerModeAndScreenMonitor ringerModeAndScreenMonitor) {
        mRingerModeAndScreenMonitor = ringerModeAndScreenMonitor;
    }

    public void setProcessorEventQueue(ProcessorEventQueue processorEventQueue) {
        mProcessorEventQueue = processorEventQueue;
    }

    public void setProcessorFocusAndSingleTap(ProcessorFocusAndSingleTap processor) {
        mProcessorFocusAndSingleTap = processor;
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mTestingListener != null) {
            mTestingListener.onAccessibilityEvent(event);
        }

        if ((mDumpEventMask & event.getEventType()) != 0) {
            Log.v(DUMP_EVNET_LOG_TAG, event.toString());
        }

        if (shouldDropRefocusEvent(event)) {
            return;
        }

        if (shouldDropEvent(event)) {
            return;
        }

        maintainExplorationState(event);

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            mService.setRootDirty(true);
        }

        // We need to save the last focused event so that we can filter out related selected events.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            if (mLastFocusedEvent != null) {
                mLastFocusedEvent.recycle();
            }

            mLastFocusedEvent = AccessibilityEvent.obtain(event);
        }

        if (AccessibilityEventUtils.eventMatchesAnyType(event, MASK_DELAYED_EVENT_TYPES)) {
            mHandler.postProcessEvent(event);
        } else {
            processEvent(event);
        }

        if (mTestingListener != null) {
            mTestingListener.afterAccessibilityEvent(event);
        }
    }

    /**
     * Returns whether the device should drop this event due to refocus issue.
     * Sometimes TalkBack will receive four consecutive events from one single node:.
     * 1. Accessibility_Focus_Cleared
     * 2. Accessibility_Focused
     * 3. Accessibility_Focus_Cleared
     * 4. Accessibility_Focused
     * <p/>
     * The cause of this issue could be:
     * i. Chrome clears and set a11y focus for each scroll event.
     * If it is an action to navigate to previous/next element and causes view scrolling. The
     * first two events are caused by navigation, and the last two events are caused by chrome
     * refocus issue. The last two events are not intended to be spoken.
     * If it is a scroll action. It might cause a lot of a11y_focus_cleared and a11y_focused
     * events. In this case all the events are not intended to be spoken.
     * <p/>
     * ii. User taps on screen to refocus on the a11y focused node. In this case event 2 and 4
     * should be spoken to the user.
     *
     * @param event The current event.
     * @return {@code true} if the event should be dropped.
     */
    private boolean shouldDropRefocusEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
            if (sGetSourceNodeIdMethod != null) {
                try {
                    mLastClearedSourceId = (long) sGetSourceNodeIdMethod.invoke(event);
                    mLastClearedWindowId = event.getWindowId();
                    mLastClearA11yFocus = System.currentTimeMillis();
                    if (mLastClearedSourceId != mLastPronouncedSourceId ||
                            mLastClearedWindowId != mLastPronouncedWindowId ||
                            mProcessorFocusAndSingleTap.isFromRefocusAction(event)) {
                        // something strange. not accessibility focused node sends clear focus event
                        // BUG
                        mLastClearedSourceId = -1;
                        mLastClearedWindowId = -1;
                        mLastClearA11yFocus = 0;
                    }
                } catch (Exception e) {
                    Log.d(LOGTAG, "Exception accessing field: " + e.toString());
                }
            }
            return true;
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            if (sGetSourceNodeIdMethod != null && !EventState.getInstance()
                    .checkAndClearRecentEvent(EventState.EVENT_NODE_REFOCUSED)) {
                try {
                    long sourceId = (long) sGetSourceNodeIdMethod.invoke(event);
                    int windowId = event.getWindowId();
                    // If this event is fired by the "clear and set a11y focus" issue of Chrome,
                    // ignore and don't speak to the user, otherwise update the node and window IDs
                    // and then process the event.
                    if (System.currentTimeMillis() - mLastClearA11yFocus
                            < CLEAR_SET_A11Y_FOCUS_WINDOW
                            && sourceId == mLastClearedSourceId
                            && windowId == mLastClearedWindowId) {
                        return true;
                    } else {
                        mLastPronouncedSourceId = sourceId;
                        mLastPronouncedWindowId = windowId;
                    }
                } catch (Exception e) {
                    Log.d(LOGTAG, "Exception accessing field: " + e.toString());
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the device should drop this event. Caches notifications
     * if necessary.
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
        if (AccessibilityManagerCompat.isTouchExplorationEnabled(mAccessibilityManager)
                && ((event.getEventType() & AUTOMATIC_AFTER_STATE_CHANGE) != 0)
                && ((event.getEventTime() - mLastWindowStateChanged) < DELAY_AUTO_AFTER_STATE)
                && !shouldKeepAutomaticEvent(event)) {
            if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
                Log.v(LOGTAG, "Drop event after window state change");
            }
            return true;
        }

        // Some view-selected events are spurious if sent immediately after a focused event.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED
                && !shouldKeepViewSelectedEvent(event)) {
            if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
                Log.v(LOGTAG, "Drop selected event after focused event");
            }
            return true;
        }

        // Real notification events always have parcelable data.
        final boolean isNotification =
                (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
                        && (event.getParcelableData() != null);

        final boolean isPhoneActive = (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState() != TelephonyManager.CALL_STATE_IDLE);
        final boolean isPhoneRinging = (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState() == TelephonyManager.CALL_STATE_RINGING);

        // Sometimes the dialer's window-state-changed event gets sent right before the
        // TelephonyManager transitions to CALL_STATE_RINGING, so we need to check isDialerEvent().
        final boolean shouldSpeakCallerId = isPhoneRinging || isDialerEvent(event);

        if (mRingerModeAndScreenMonitor != null &&
                !mRingerModeAndScreenMonitor.isScreenOn() &&
                !shouldSpeakCallerId) {
            if (!mSpeakWhenScreenOff) {
                // If the user doesn't allow speech when the screen is
                // off, drop the event immediately.
                if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
                    Log.v(LOGTAG, "Drop event due to screen state and user pref");
                }
                return true;
            } else if (!isNotification) {
                // If the user allows speech when the screen is off, drop
                // all non-notification events.
                if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
                    Log.v(LOGTAG, "Drop non-notification event due to screen state");
                }
                return true;
            }
        }

        final boolean canInterruptRadialMenu = AccessibilityEventUtils.eventMatchesAnyType(
                event, MASK_EVENT_TYPES_INTERRUPT_RADIAL_MENU);
        final boolean silencedByRadialMenu = (mService.getMenuManager().isMenuShowing()
                && !canInterruptRadialMenu);

        // Don't speak events that cannot interrupt the radial menu, if showing
        if (silencedByRadialMenu) {
            if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
                Log.v(LOGTAG, "Drop event due to radial menu state");
            }
            return true;
        }

        // Don't speak notification events if the user is touch exploring or a phone call is active.
        if (isNotification && (mIsUserTouchExploring || isPhoneActive)) {
            if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
                Log.v(LOGTAG, "Drop notification due to touch or phone state");
            }
            return true;
        }

        final int touchscreenState = mService.getResources().getConfiguration().touchscreen;
        final boolean isTouchInteractionStateChange = AccessibilityEventUtils.eventMatchesAnyType(
                event, MASK_EVENT_TYPES_TOUCH_STATE_CHANGES);

        // Drop all events related to touch interaction state on devices that don't support touch.
        return (touchscreenState == Configuration.TOUCHSCREEN_NOTOUCH)
                && isTouchInteractionStateChange;
    }

    /**
     * Helper method for {@link #shouldDropEvent} that handles events that
     * automatically occur immediately after a window state change.
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
     * Helper method for {@link #shouldDropEvent} that filters out selected events that occur
     * in close proximity to focused events.
     *
     * A selected event should be kept if:
     * - The most recent focused event occurred over {@link #DELAY_SELECTED_AFTER_FOCUS} ms ago.
     * - The most recent focused event occurred on a different branch of the accessibility node
     *   tree, i.e., not in an ancestor or descendant of the selected event.
     *
     * @param event The view-selected event to consider retaining.
     * @return Whether to retain the event.
     */
    private boolean shouldKeepViewSelectedEvent(final AccessibilityEvent event) {
        if (mLastFocusedEvent == null) {
            return true;
        }

        if (event.getEventTime() - mLastFocusedEvent.getEventTime() > DELAY_SELECTED_AFTER_FOCUS) {
            return true;
        }

        // AccessibilityEvent.getSource will obtain() an AccessibilityNodeInfo, so it is our
        // responsibility to recycle() it.
        AccessibilityNodeInfo selectedSource = event.getSource();
        AccessibilityNodeInfo focusedSource = mLastFocusedEvent.getSource();

        try {
            // Note: AccessibilityNodeInfoCompat constructor will silently succeed when wrapping
            // a null object.
            if (selectedSource != null && focusedSource != null) {
                AccessibilityNodeInfoCompat selectedSourceCompat =
                        new AccessibilityNodeInfoCompat(selectedSource);
                AccessibilityNodeInfoCompat focusedSourceCompat =
                        new AccessibilityNodeInfoCompat(focusedSource);

                if (AccessibilityNodeInfoUtils.areInSameBranch(selectedSourceCompat,
                        focusedSourceCompat)) {
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
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                    && CLASS_DIALER_JELLY_BEAN.equals(event.getClassName())) {
                return true;
            } else if (CLASS_DIALER_KITKAT.equals(event.getClassName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Manages touch exploration state.
     *
     * @param event The current event.
     */
    private void maintainExplorationState(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            mIsUserTouchExploring = true;
        } else if (eventType == AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
            mIsUserTouchExploring = false;
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            mLastWindowStateChanged = SystemClock.uptimeMillis();
        }
    }

    /**
     * Passes the event to all registered {@link AccessibilityEventListener}s in the order
     * they were added.
     *
     * @param event The current event.
     */
    private void processEvent(AccessibilityEvent event) {
        for (AccessibilityEventListener eventProcessor : mAccessibilityEventListeners) {
            eventProcessor.onAccessibilityEvent(event);
        }
    }

    public void addAccessibilityEventListener(AccessibilityEventListener listener) {
        mAccessibilityEventListeners.add(listener);
    }

    public void postRemoveAccessibilityEventListener(final AccessibilityEventListener listener) {
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                mAccessibilityEventListeners.remove(listener);
            }
        });
    }

    public void setTestingListener(TalkBackListener testingListener) {
        mTestingListener = testingListener;

        if (mProcessorEventQueue != null) {
            mProcessorEventQueue.setTestingListener(testingListener);
        }
    }

    public TalkBackListener getTestingListener() {
        return mTestingListener;
    }

    /**
     * Update the dump event mask when relavant preferences are changed.
     */
    public void onDumpEventPreferenceChanged(int eventType, boolean shouldDump) {
        if (((mDumpEventMask & eventType) != 0) != shouldDump) {
            mDumpEventMask ^= eventType;
        }
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

            AccessibilityEvent event = (AccessibilityEvent) message.obj;
            processEvent(event);
            event.recycle();
        }

        public void postProcessEvent(AccessibilityEvent event) {
            AccessibilityEvent eventCopy = AccessibilityEvent.obtain(event);
            Message msg = obtainMessage(MESSAGE_WHAT_PROCESS_EVENT, eventCopy);
            sendMessageDelayed(msg, EVENT_PROCESSING_DELAY);
        }

    }
}
