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

package com.android.talkback.controller;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.CursorGranularity;
import com.android.talkback.InputModeManager;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;

import com.android.talkback.eventprocessor.EventState;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.WebInterfaceUtils;
import com.android.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.android.utils.traversal.OrderedTraversalStrategy;
import com.android.utils.traversal.TraversalStrategy;

public class FullScreenReadControllerApp implements
        FullScreenReadController, AccessibilityEventListener {
    /** Tag used for log output and wake lock */
    private static final String TAG = "FullScreenReadController";

    /** The possible states of the controller. */
    private static final int STATE_STOPPED = 0;
    private static final int STATE_READING_FROM_BEGINNING = 1;
    private static final int STATE_READING_FROM_NEXT = 2;
    private static final int STATE_ENTERING_LEGACY_WEB_CONTENT = 3;

    /** Event types that should interrupt continuous reading, if active. */
    private static final int MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS =
            AccessibilityEvent.TYPE_VIEW_CLICKED |
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED |
            AccessibilityEvent.TYPE_VIEW_SELECTED |
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
            AccessibilityEventCompat.TYPE_ANNOUNCEMENT |
            AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START |
            AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START |
            AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START |
            AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER |
            AccessibilityEventCompat.TYPE_VIEW_TEXT_SELECTION_CHANGED;

    /**
     * The current state of the controller. Should only be updated through
     * {@link FullScreenReadControllerApp#setReadingState(int)}
     */
    private int mCurrentState = STATE_STOPPED;

    /** The parent service */
    private final TalkBackService mService;

    /** Controller for linearly navigating the view hierarchy tree */
    private CursorController mCursorController;

    /** Feedback controller for audio feedback */
    private final FeedbackController mFeedbackController;

    /** Wake lock for keeping the device unlocked while reading */
    private PowerManager.WakeLock mWakeLock;

    @SuppressWarnings("deprecation")
    public FullScreenReadControllerApp(FeedbackController feedbackController,
                                       CursorController cursorController,
                                       TalkBackService service) {
        if (cursorController == null) throw new IllegalStateException();
        if (feedbackController == null) throw new IllegalStateException();

        mCursorController = cursorController;
        mFeedbackController = feedbackController;
        mService = service;
        mWakeLock = ((PowerManager) service.getSystemService(Context.POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
    }

    /**
     * Releases all resources held by this controller and save any persistent
     * preferences.
     */
    public void shutdown() {
        interrupt();
    }

    /**
     * Starts linearly reading from the node with accessibility focus.
     */
    public void startReadingFromNextNode() {
        if (isActive()) {
            return;
        }

        final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
        if (currentNode == null) {
            return;
        }

        setReadingState(STATE_READING_FROM_NEXT);

        mCursorController.setGranularity(CursorGranularity.DEFAULT, false /* fromUser */);

        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }

        // Avoid reading the elements in web content twice by calling directly
        // into ChromeVox rather than advancing CursorController first.
        if (WebInterfaceUtils.hasLegacyWebContent(currentNode)) {
            moveIntoWebContent();
        } else {
            moveForward();
        }

        currentNode.recycle();
    }

    /**
     * Starts linearly reading from the top of the view hierarchy.
     */
    public void startReadingFromBeginning() {
        AccessibilityNodeInfoCompat rootNode = null;
        AccessibilityNodeInfoCompat currentNode = null;

        if (isActive()) {
            return;
        }

        try {
            rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(mService);
            if (rootNode == null) {
                return;
            }

            TraversalStrategy traversal = new OrderedTraversalStrategy(rootNode);
            try {
                currentNode = AccessibilityNodeInfoUtils.searchFocus(traversal, rootNode,
                        TraversalStrategy.SEARCH_FOCUS_FORWARD,
                        AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
            } finally {
                traversal.recycle();
            }

            if (currentNode == null) {
                return;
            }

            setReadingState(STATE_READING_FROM_BEGINNING);

            mCursorController.setGranularity(CursorGranularity.DEFAULT, false /* fromUser */);

            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }

            // This is potentially a refocus, so we should set the refocus flag just in case.
            EventState.getInstance().addEvent(EventState.EVENT_NODE_REFOCUSED);
            mCursorController.clearCursor();
            mCursorController.setCursor(currentNode); // Will automatically move forward.

            if (WebInterfaceUtils.hasLegacyWebContent(currentNode)) {
                moveIntoWebContent();
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(rootNode, currentNode);
        }
    }

    /**
     * Stops speech output and view traversal at the current position.
     */
    public void interrupt() {
        setReadingState(STATE_STOPPED);

        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    private void moveForward() {
        if (!mCursorController.next(false /* shouldWrap */, false /* shouldScroll */,
                false /*useInputFocusAsPivotIfEmpty*/, InputModeManager.INPUT_MODE_UNKNOWN)) {
            mFeedbackController.playAuditory(R.raw.complete, 1.3f, 1);
            interrupt();
        }

        if (currentNodeHasWebContent()) {
            moveIntoWebContent();
        }
    }

    private void moveIntoWebContent() {
        final AccessibilityNodeInfoCompat webNode = mCursorController.getCursor();
        if (webNode == null) {
            // Reset state.
            interrupt();
            return;
        }

        if (mCurrentState == STATE_READING_FROM_BEGINNING) {
            // Reset ChromeVox's active indicator to the start to the page.
            WebInterfaceUtils.performNavigationAtGranularityAction(webNode,
                    WebInterfaceUtils.DIRECTION_BACKWARD,
                    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
        }

        WebInterfaceUtils.performNavigationAtGranularityAction(webNode,
                WebInterfaceUtils.DIRECTION_FORWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);

        setReadingState(STATE_ENTERING_LEGACY_WEB_CONTENT);

        webNode.recycle();
    }

    private void setReadingState(int newState) {
        LogUtils.log(TAG, Log.VERBOSE, "Continuous reading switching to mode: %s", newState);

        mCurrentState = newState;

        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            service.getSpeechController().setShouldInjectAutoReadingCallbacks(isActive(),
                    mNodeSpokenRunnable);
        }
    }

    public boolean isReadingLegacyWebContent() {
        return mCurrentState == STATE_ENTERING_LEGACY_WEB_CONTENT;
    }

    /**
     * Returns whether full-screen reading is currently active. Equivalent to
     * calling {@code mCurrentState != STATE_STOPPED}.
     *
     * @return Whether full-screen reading is currently active.
     */
    public boolean isActive() {
        return mCurrentState != STATE_STOPPED;
    }

    private boolean currentNodeHasWebContent() {
        final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
        if (currentNode == null) {
            return false;
        }

        final boolean isWebContent = WebInterfaceUtils.hasLegacyWebContent(currentNode);
        currentNode.recycle();
        return isWebContent;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isActive()) {
            return;
        }

        // Only interrupt full screen reading on events that can't be generated
        // by automated cursor movement or from delayed user interaction.
        if (AccessibilityEventUtils.eventMatchesAnyType(
                event, MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS)) {
            interrupt();
        }
    }

    /** Runnable executed when a node has finished being spoken */
    private final SpeechController.UtteranceCompleteRunnable mNodeSpokenRunnable = new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
            if (isActive() && !isReadingLegacyWebContent()
                    && status != SpeechController.STATUS_INTERRUPTED) {
                moveForward();
            }
        }
    };
}
