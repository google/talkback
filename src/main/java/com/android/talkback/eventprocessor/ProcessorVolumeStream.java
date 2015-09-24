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
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.EditText;

import android.widget.SeekBar;
import com.android.talkback.CursorGranularity;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
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

        mPrefs = PreferenceManager.getDefaultSharedPreferences(service);
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

    private void navigateSlider(int button) {
        if (button == VolumeButtonPatternDetector.VOLUME_UP) {
            mCursorController.more();
        }
        else if (button == VolumeButtonPatternDetector.VOLUME_DOWN) {
            mCursorController.less();
        }
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

    private AccessibilityNodeInfoCompat findInputFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            List<AccessibilityWindowInfo> awis = mService.getWindows();
            for (AccessibilityWindowInfo awi : awis) {
                if (awi.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
                AccessibilityNodeInfo info = awi.getRoot();
                if (info == null) continue;
                AccessibilityNodeInfoCompat root = new AccessibilityNodeInfoCompat(awi.getRoot());
                AccessibilityNodeInfoCompat focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focus != null) {
                    if (!root.equals(focus)) root.recycle();
                    return focus;
                } else {
                    root.recycle();
                }
            }
            return null;
        } else {
            AccessibilityNodeInfo info = mService.getRootInActiveWindow();
            if (info == null) return null;
            AccessibilityNodeInfoCompat root =
                    new AccessibilityNodeInfoCompat(info);
            AccessibilityNodeInfoCompat focus =
                    root.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);
            if (focus != null && !focus.equals(root)) root.recycle();
            return focus;
        }
    }

    private boolean attemptNavigation(int button) {
        AccessibilityNodeInfoCompat node = mCursorController.getCursor();

        // Clear focus if it is on an IME
        if (node != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
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

        if (node == null) {
            node = findInputFocus();
        }

        if (node == null) return false;
        try {
            if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, SeekBar.class)) {
                navigateSlider(button);
                return true;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if (((AccessibilityNodeInfo) node.getInfo()).isEditable()) {
                    navigateEditText(button, node);
                    return true;
                }
            } else {
                if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, EditText.class) ||
                    AccessibilityNodeInfoUtils.nodeMatchesClassByName(node,
                            "com.google.android.search.searchplate.SimpleSearchText")) {
                    navigateEditText(button, node);
                    return true;
                }
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
        switch (patternCode) {
            case VolumeButtonPatternDetector.SHORT_PRESS_PATTERN:
                handleSingleTap(buttonCombination);
                break;
            case VolumeButtonPatternDetector.TWO_BUTTONS_LONG_PRESS_PATTERN:
                handleBothVolumeKeysLongPressed();
                mPatternDetector.clearState();
                break;
            case VolumeButtonPatternDetector.TWO_BUTTONS_THREE_PRESS_PATTERN:
                if (mDimScreenController.isInstructionDisplayed()) {
                    mDimScreenController.makeScreenBright();
                    resetDimScreenSettings();
                }
                break;
        }
    }

    private void resetDimScreenSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mService);
        SharedPreferencesUtils.putBooleanPref(prefs, mService.getResources(),
                R.string.pref_dim_when_talkback_enabled_key, false);
    }

    private void handleSingleTap(int button) {
        if (TalkBackService.isServiceActive() && attemptNavigation(button)) {
            return;
        }

        adjustVolumeFromKeyEvent(button);
    }
}
