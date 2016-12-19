/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.controller.FeedbackController;
import com.android.talkback.controller.GestureController;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.picidae.IntegratedTapDetector;
import com.google.android.marvin.talkback.TalkBackService;

/**
 * Manages detection of taps on the side of the device. Wraps IntegratedTapDetector.
 */
public class SideTapManager extends BroadcastReceiver
    implements IntegratedTapDetector.TapListener, FeedbackController.HapticFeedbackListener,
        AccessibilityEventListener {

    /* Ignore taps for this long after the screen is touched */
    private static final long MIN_TIME_BETWEEN_TOUCH_AND_TAP_NANOS = 500 * 1000 * 1000;

    /* Ignore taps for this long after the screen is touched */
    private static final long MIN_TIME_BETWEEN_HAPTIC_AND_TAP_NANOS = 500 * 1000 * 1000;

    /* Time interval for double taps */
    private static final long DOUBLE_TAP_SPACING_NANOS = 500 * 1000 * 1000;

    private static final long MILIS_PER_NANO = 1000 * 1000;

    private Context mContext;

    /* Time of last touch of the screen */
    private long mLastTouchTime = 0;

    /* Time of last haptic feedback */
    private long mLastHapticTime = 0;

    /* Class that deals with the hardware and calls us back with taps */
    private IntegratedTapDetector mIntegratedTapDetector;

    private final GestureController mGestureController;

    /**
     * @param context TalkBackService whose {@code performCustomGesture}
     * will be called when taps are detected
     */
    public SideTapManager(TalkBackService context, GestureController gestureController) {
        if (gestureController == null) throw new IllegalStateException();
        mContext = context;
        mIntegratedTapDetector = new IntegratedTapDetector(
                (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE));
        mIntegratedTapDetector.addListener(this);
        mIntegratedTapDetector
                .setPostDelayTimeMillis(MIN_TIME_BETWEEN_TOUCH_AND_TAP_NANOS / MILIS_PER_NANO);
        mGestureController = gestureController;
    }

    /**
     * Stops tap detection.
     */
    public void onSuspendInfrastructure() {
        mIntegratedTapDetector.stop();
    }

    /**
     * Enables tap detection if appropriate based on preferences.
     */
    public void onReloadPreferences() {
        SharedPreferences settings = SharedPreferencesUtils.getSharedPreferences(mContext);

        boolean enableTapDetection = false;
        if (!settings.getString(mContext.getString(R.string.pref_shortcut_single_tap_key),
                mContext.getString(R.string.pref_shortcut_single_tap_default)).
                equals(mContext.getString(R.string.shortcut_value_unassigned))) {
            /* Single tap is assigned */
            enableTapDetection = true;
        }

        if (!settings.getString(mContext.getString(R.string.pref_shortcut_double_tap_key),
                mContext.getString(R.string.pref_shortcut_double_tap_default)).
                equals(mContext.getString(R.string.shortcut_value_unassigned))) {
            /* Double tap is assigned */
            enableTapDetection = true;
            mIntegratedTapDetector.setMaxDoubleTapSpacingNanos(DOUBLE_TAP_SPACING_NANOS);
        } else {
            mIntegratedTapDetector.setMaxDoubleTapSpacingNanos(0);
        }

        /* The setting is 'sensitivity', which is the opposite of the detector's 'quality' */
        if (settings.getString(mContext.getString(R.string.pref_tap_sensitivity_key),
                mContext.getString(R.string.pref_tap_sensitivity_default)).
                equals(mContext.getString(R.string.tap_sensitivity_value_lowest))) {
            mIntegratedTapDetector.setTapDetectionQuality(
                    IntegratedTapDetector.TAP_QUALITY_HIGHEST);
        }

        if (settings.getString(mContext.getString(R.string.pref_tap_sensitivity_key),
                mContext.getString(R.string.pref_tap_sensitivity_default)).
                equals(mContext.getString(R.string.tap_sensitivity_value_low))) {
            mIntegratedTapDetector.setTapDetectionQuality(IntegratedTapDetector.TAP_QUALITY_HIGH);
        }

        if (settings.getString(mContext.getString(R.string.pref_tap_sensitivity_key),
                mContext.getString(R.string.pref_tap_sensitivity_default)).
                equals(mContext.getString(R.string.tap_sensitivity_value_medium))) {
            mIntegratedTapDetector.setTapDetectionQuality(
                    IntegratedTapDetector.TAP_QUALITY_MEDIUM);
        }

        if (settings.getString(mContext.getString(R.string.pref_tap_sensitivity_key),
                mContext.getString(R.string.pref_tap_sensitivity_default)).
                equals(mContext.getString(R.string.tap_sensitivity_value_high))) {
            mIntegratedTapDetector.setTapDetectionQuality(IntegratedTapDetector.TAP_QUALITY_LOW);
        }

        /* Second tap of double taps can be low quality */
        mIntegratedTapDetector.setDoubleTapDetectionQuality(
                IntegratedTapDetector.TAP_QUALITY_LOW);

        if (enableTapDetection) {
            mIntegratedTapDetector.start();
        } else {
            mIntegratedTapDetector.stop();
        }
    }

    /**
     * Called so we can avoid detecting screen touches as side taps.
     */
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START) {
            mLastTouchTime = System.nanoTime();
        }
    }

    /* Handle a single tap on the side of the device */
    @Override
    public void onSingleTap(long timeStamp) {
        boolean talkBackActive = TalkBackService.isServiceActive();
        boolean tapIsntFromScreenTouch =
                (Math.abs(timeStamp - mLastTouchTime) > MIN_TIME_BETWEEN_TOUCH_AND_TAP_NANOS);
        boolean tapIsntFromHaptic =
                (Math.abs(timeStamp - mLastHapticTime) > MIN_TIME_BETWEEN_HAPTIC_AND_TAP_NANOS);
        if (talkBackActive && tapIsntFromScreenTouch && tapIsntFromHaptic) {
            SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
            mGestureController.performAction(prefs.getString(
                    mContext.getString(R.string.pref_shortcut_single_tap_key),
                    mContext.getString(R.string.pref_shortcut_single_tap_default)));
        }
    }

    /* Handle a double tap on the side of the device */
    @Override
    public void onDoubleTap(long timeStamp) {
        boolean talkBackActive = TalkBackService.isServiceActive();
        boolean tapIsntFromScreenTouch =
                (Math.abs(timeStamp - mLastTouchTime) > MIN_TIME_BETWEEN_TOUCH_AND_TAP_NANOS);
        boolean tapIsntFromHaptic =
                (Math.abs(timeStamp - mLastHapticTime) > MIN_TIME_BETWEEN_HAPTIC_AND_TAP_NANOS);
        if (talkBackActive && tapIsntFromScreenTouch && tapIsntFromHaptic) {
            SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
            mGestureController.performAction(prefs.getString(
                    mContext.getString(R.string.pref_shortcut_double_tap_key),
                    mContext.getString(R.string.pref_shortcut_double_tap_default)));
        }
    }

    /*
     * Haptic feedback can be interpreted as a tap, similarly to a screen tap.
     */
    @Override
    public void onHapticFeedbackStarting(long currentNanoTime) {
        mLastHapticTime = currentNanoTime;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TalkBackService.isServiceActive()) {
            return;
        }

        String action = intent.getAction();
        if (action.equals(Intent.ACTION_SCREEN_ON)) {
            onReloadPreferences();
        }
        if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            mIntegratedTapDetector.stop();
        }
    }

    public static IntentFilter getFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        return filter;
    }

    /**
     * Permit tests to replace the tap detector to verify calls to it
     * @param itd new IntegratedTapDetector
     */
    // Visible for testing
    /* package */ void setIntegratedTapDetector(IntegratedTapDetector itd) {
        mIntegratedTapDetector = itd;
    }

}
