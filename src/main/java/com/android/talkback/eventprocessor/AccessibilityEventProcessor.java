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
import android.content.res.Configuration;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityRecord;
import android.widget.EditText;
import com.android.talkback.CallStateMonitor;
import com.android.talkback.RingerModeAndScreenMonitor;
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
    private TalkBackListener mTestingListener;

    /** Event types that are allowed to interrupt radial menus. */
    // TODO(KM): What's the rationale for HOVER_ENTER? Navigation bar?
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

    /** The minimum delay between window state change and automatic events. */
    public static final long DELAY_AUTO_AFTER_STATE = 100;

    private final TalkBackService mService;
    private AccessibilityManager mAccessibilityManager;
    private CallStateMonitor mCallStateMonitor;
    private ProcessorEventQueue mProcessorEventQueue;
    private RingerModeAndScreenMonitor mRingerModeAndScreenMonitor;

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

    private boolean mSpeakWhenScreenOff = false;
    private boolean mSpeakCallerId = false;

    public AccessibilityEventProcessor(TalkBackService service) {
        mAccessibilityManager =
                (AccessibilityManager) service.getSystemService(Context.ACCESSIBILITY_SERVICE);

        mService = service;
    }

    public void setSpeakCallerId(boolean speak) {
        mSpeakCallerId = speak;
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

    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mTestingListener != null) {
            mTestingListener.onAccessibilityEvent(event);
        }

        // Chrome clears and set a11y focus for each scroll event, it is not intended to be spoken
        // to the user. Remove this when chromium is fixed.
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
            if (sGetSourceNodeIdMethod != null) {
                try {
                    mLastClearedSourceId = (long)sGetSourceNodeIdMethod.invoke(event);
                    mLastClearedWindowId = event.getWindowId();
                    mLastClearA11yFocus = System.currentTimeMillis();
                    if (mLastClearedSourceId != mLastPronouncedSourceId ||
                            mLastClearedWindowId != mLastPronouncedWindowId) {
                        // something strange. not accessibility focused node sends clear focus event
                        // b/22108305
                        mLastClearedSourceId = -1;
                        mLastClearedWindowId = - 1;
                        mLastClearA11yFocus = 0;
                    }
                } catch (Exception e) {
                    Log.d(LOGTAG, "Exception accessing field: " + e.toString());
                }
            }

            return;
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            if (System.currentTimeMillis() - mLastClearA11yFocus < CLEAR_SET_A11Y_FOCUS_WINDOW) {
                if (sGetSourceNodeIdMethod != null) {
                    try {
                        long sourceId = (long)sGetSourceNodeIdMethod.invoke(event);
                        int windowId = event.getWindowId();
                        if (sourceId == mLastClearedSourceId && windowId == mLastClearedWindowId) {
                            return;
                        }
                        mLastPronouncedSourceId = sourceId;
                        mLastPronouncedWindowId = windowId;
                    } catch (Exception e) {
                        Log.d(LOGTAG, "Exception accessing field: " + e.toString());
                    }
                }
            }
        }

        if (shouldDropEvent(event)) {
            return;
        }

        maintainExplorationState(event);

        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            mService.setRootDirty(true);
        }

        processEvent(event);
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

        // Real notification events always have parcelable data.
        final boolean isNotification =
                (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
                        && (event.getParcelableData() != null);

        final boolean isPhoneActive = (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState() != TelephonyManager.CALL_STATE_IDLE);
        final boolean shouldSpeakCallerId = (mSpeakCallerId && (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState()
                == TelephonyManager.CALL_STATE_RINGING));

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
                if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, EditText.class)) {
                    return true;
                }
            } finally {
                AccessibilityNodeInfoUtils.recycleNodes(node);
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

    public interface TalkBackListener {
        void onAccessibilityEvent(AccessibilityEvent event);
        // TODO: Solve this by making a fake tts and look for calls into it instead
        void onUtteranceQueued(Utterance utterance);
    }
}
