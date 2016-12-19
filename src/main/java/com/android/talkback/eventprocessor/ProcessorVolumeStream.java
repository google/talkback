/*
 * Copyright (C) 2013 Google Inc.
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.talkback.CursorGranularity;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.utils.Role;
import com.android.utils.WeakReferenceHandler;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.controller.CursorController;
import com.android.talkback.controller.DimScreenController;
import com.android.talkback.controller.FeedbackController;
import com.android.talkback.volumebutton.VolumeButtonPatternDetector;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.PerformActionUtils;
import com.android.utils.SharedPreferencesUtils;

import java.util.List;

/**
 * Locks the volume control stream during a touch interaction event.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ProcessorVolumeStream implements AccessibilityEventListener,
        TalkBackService.KeyEventListener, VolumeButtonPatternDetector.OnPatternMatchListener {
    /** Minimum API version required for this class to function. */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    private static final boolean API_LEVEL_SUPPORTS_WINDOW_NAVIGATION =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;

    /** Default flags for volume adjustment while touching the screen. */
    private static final int DEFAULT_FLAGS_TOUCHING_SCREEN = (AudioManager.FLAG_SHOW_UI
            | AudioManager.FLAG_VIBRATE);

    /** Default flags for volume adjustment while not touching the screen. */
    private static final int DEFAULT_FLAGS_NOT_TOUCHING_SCREEN = (AudioManager.FLAG_SHOW_UI
            | AudioManager.FLAG_VIBRATE | AudioManager.FLAG_PLAY_SOUND);

    /** Stream to control when the user is touching the screen. */
    private static final int STREAM_TOUCHING_SCREEN = SpeechController.DEFAULT_STREAM;

    /** Stream to control when the user is not touching the screen. */
    private static final int STREAM_DEFAULT = AudioManager.USE_DEFAULT_STREAM_TYPE;

    /** Tag used for identification of the wake lock held by this class */
    private static final String WL_TAG = ProcessorVolumeStream.class.getSimpleName();

    /** The audio manager, used to adjust speech volume. */
    private final AudioManager mAudioManager;

    /** WakeLock used to keep the screen active during key events */
    private final WakeLock mWakeLock;

    /** Handler for completing volume key handling outside of the main key-event handler. */
    private final VolumeStreamHandler mHandler = new VolumeStreamHandler(this);

    /**
     * The cursor controller, used for determining the focused node and
     * navigating.
     */
    private final CursorController mCursorController;

    /**
     * Feedback controller for providing feedback on boundaries during volume
     * key navigation.
     */
    private final FeedbackController mFeedbackController;

    /** Whether the user is touching the screen. */
    private boolean mTouchingScreen = false;
    private SharedPreferences mPrefs;
    private TalkBackService mService;
    private DimScreenController mDimScreenController;

    private VolumeButtonPatternDetector mPatternDetector;

    @SuppressWarnings("deprecation")
    public ProcessorVolumeStream(FeedbackController feedbackController,
                                 CursorController cursorController,
                                 DimScreenController dimScreenController,
                                 TalkBackService service) {
        if (feedbackController == null) throw new IllegalStateException(
                "CachedFeedbackController is null");
        if (cursorController == null) throw new IllegalStateException("CursorController is null");
        if (dimScreenController == null) throw new IllegalStateException(
                "DimScreenController is null");

        mAudioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
        mCursorController = cursorController;
        mFeedbackController = feedbackController;

        final PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, WL_TAG);

        mPrefs = SharedPreferencesUtils.getSharedPreferences(service);
        mService = service;
        mDimScreenController = dimScreenController;
        mPatternDetector = new VolumeButtonPatternDetector();
        mPatternDetector.setOnPatternMatchListener(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
                mTouchingScreen = true;
                break;
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
                mTouchingScreen = false;
                break;
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        boolean handled = mPatternDetector.onKeyEvent(event);

        if (handled) {
            // Quickly acquire and release the wake lock so that
            // PowerManager.ON_AFTER_RELEASE takes effect.
            mWakeLock.acquire();
            mWakeLock.release();
        }

        return handled;
    }

    @Override
    public boolean processWhenServiceSuspended() {
        return true;
    }

    private void handleBothVolumeKeysLongPressed() {
        if (TalkBackService.isServiceActive() && switchTalkBackActiveStateEnabled()) {
            mService.requestSuspendTalkBack();
        } else {
            mService.resumeTalkBack();
        }
    }

    private boolean switchTalkBackActiveStateEnabled() {
        return SharedPreferencesUtils.getBooleanPref(mPrefs, mService.getResources(),
                R.string.pref_two_volume_long_press_key,
                R.bool.pref_resume_volume_buttons_long_click_default);
    }

    private void navigateSlider(int button, @NonNull AccessibilityNodeInfoCompat node) {
        int action;
        if (button == VolumeButtonPatternDetector.VOLUME_UP) {
            action = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
        } else if (button == VolumeButtonPatternDetector.VOLUME_DOWN) {
            action = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
        } else {
            return;
        }

        PerformActionUtils.performAction(node, action);
    }

    private void navigateEditText(int button, @NonNull AccessibilityNodeInfoCompat node) {
        boolean result = false;

        Bundle args = new Bundle();
        CursorGranularity currentGranularity = mCursorController.getGranularityAt(node);
        if (currentGranularity != CursorGranularity.DEFAULT) {
            args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    currentGranularity.value);
        } else {
            args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);
        }

        if (mCursorController.isSelectionModeActive()) {
            args.putBoolean(
                    AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true);
        }

        EventState.getInstance().addEvent(
                EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
        EventState.getInstance().addEvent(
                EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE);

        if (button == VolumeButtonPatternDetector.VOLUME_UP) {
            result = PerformActionUtils.performAction(node,
                    AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args);
        } else if (button == VolumeButtonPatternDetector.VOLUME_DOWN) {
            result = PerformActionUtils.performAction(node,
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, args);
        }

        if (!result) {
            mFeedbackController.playAuditory(R.raw.complete);
        }
    }

    private boolean attemptNavigation(int button) {
        AccessibilityNodeInfoCompat node = mCursorController.getCursorOrInputCursor();

        // Clear focus if it is on an IME
        if (node != null) {
            if (API_LEVEL_SUPPORTS_WINDOW_NAVIGATION) {
                for (AccessibilityWindowInfo awi : mService.getWindows()) {
                    if (awi.getId() == node.getWindowId()) {
                        if (awi.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                            node.recycle();
                            node = null;
                        }
                        break;
                    }
                }
            }
        }

        // If we cleared the focus before it is on an IME, try to get the current node again.
        if (node == null) {
            node = mCursorController.getCursorOrInputCursor();
        }

        if (node == null) return false;
        try {
            if (Role.getRole(node) == Role.ROLE_SEEK_CONTROL) {
                navigateSlider(button, node);
                return true;
            }

            // In general, do not allow volume key navigation when the a11y focus is placed but
            // it is not on the edit field that the keyboard is currently editing.
            //
            // Example 1:
            // EditText1 has input focus and EditText2 has accessibility focus.
            // getCursorOrInputCursor() will return EditText2 based on its priority order.
            // EditText2.isFocused() = false, so we should not allow volume keys to control text.
            //
            // Example 2:
            // EditText1 in Window1 has input focus. EditText2 in Window2 has input focus as well.
            // If Window1 is input-focused but Window2 has the accessibility focus, don't allow
            // the volume keys to control the text.
            boolean nodeWindowFocused;
            if (API_LEVEL_SUPPORTS_WINDOW_NAVIGATION) {
                nodeWindowFocused = node.getWindow() != null && node.getWindow().isFocused();
            } else {
                nodeWindowFocused = true;
            }

            if (node.isFocused() && nodeWindowFocused &&
                    AccessibilityNodeInfoUtils.isEditable(node)) {
                navigateEditText(button, node);
                return true;
            }

            return false;
      } finally {
            AccessibilityNodeInfoUtils.recycleNodes(node);
        }
    }

    private void adjustVolumeFromKeyEvent(int button) {
        final int direction = ((button == VolumeButtonPatternDetector.VOLUME_UP) ?
                AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER);

        if (mTouchingScreen) {
            mAudioManager.adjustStreamVolume(
                    STREAM_TOUCHING_SCREEN, direction, DEFAULT_FLAGS_TOUCHING_SCREEN);
        } else {
            // Attempt to adjust the suggested stream, but let the system
            // override in special situations like during voice calls, when an
            // application has locked the volume control stream, or when music
            // is playing.
            mAudioManager.adjustSuggestedStreamVolume(
                    direction, STREAM_DEFAULT, DEFAULT_FLAGS_NOT_TOUCHING_SCREEN);
        }
    }

    @Override
    public void onPatternMatched(int patternCode, int buttonCombination) {
        mHandler.postPatternMatched(patternCode, buttonCombination);
    }

    public void onPatternMatchedInternal(int patternCode, int buttonCombination) {
        switch (patternCode) {
            case VolumeButtonPatternDetector.SHORT_PRESS_PATTERN:
                handleSingleTap(buttonCombination);
                break;
            case VolumeButtonPatternDetector.TWO_BUTTONS_LONG_PRESS_PATTERN:
                handleBothVolumeKeysLongPressed();
                mPatternDetector.clearState();
                break;
            case VolumeButtonPatternDetector.TWO_BUTTONS_THREE_PRESS_PATTERN:
                if (!mService.isInstanceActive()) {
                    // If the service isn't active, the user won't get any feedback that
                    // anything happened, so we shouldn't change the dimming setting.
                    return;
                }

                boolean globalShortcut = isTripleClickEnabledGlobally();
                boolean dimmed = mDimScreenController.isDimmingEnabled();

                if (dimmed && (globalShortcut || mDimScreenController.isInstructionDisplayed())) {
                    mDimScreenController.disableDimming();
                } else if (!dimmed && globalShortcut) {
                    mDimScreenController.showDimScreenDialog();
                }

                break;
        }
    }

    private void handleSingleTap(int button) {
        if (TalkBackService.isServiceActive() && attemptNavigation(button)) {
            return;
        }

        adjustVolumeFromKeyEvent(button);
    }

    private boolean isTripleClickEnabledGlobally() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mService);
        return SharedPreferencesUtils.getBooleanPref(prefs, mService.getResources(),
                R.string.pref_dim_volume_three_clicks_key,
                R.bool.pref_dim_volume_three_clicks_default);
    }

    /**
     * Used to run potentially long methods outside of the key handler so that we don't ever
     * hit the key handler timeout.
     */
    private static final class VolumeStreamHandler
            extends WeakReferenceHandler<ProcessorVolumeStream> {

        public VolumeStreamHandler(ProcessorVolumeStream parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, ProcessorVolumeStream parent) {
            int patternCode = msg.arg1;
            int buttonCombination = msg.arg2;
            parent.onPatternMatchedInternal(patternCode, buttonCombination);
        }

        public void postPatternMatched(int patternCode, int buttonCombination) {
            Message msg = obtainMessage(0 /* what */,
                    patternCode /* arg1 */,
                    buttonCombination /* arg2 */);
            sendMessage(msg);
        }

    }
}
