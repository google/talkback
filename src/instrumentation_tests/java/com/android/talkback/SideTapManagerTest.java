/*
 * Copyright (C) 2015 Google Inc.
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

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.controller.GestureController;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.picidae.IntegratedTapDetector;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

/**
 * Tests for SideTapManager
 */
public class SideTapManagerTest extends TalkBackInstrumentationTestCase {
    private TalkBackService mTalkBack;

    private SideTapManager mSideTapManager;
    private MockGestureController mGestureController;
    private Instrumentation mInstrumentation;

    private MockIntegratedTapDetector mMockIntegratedDetector;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTalkBack = getService();
        waitForAccessibilityIdleSync();
        mGestureController = new MockGestureController();
        mSideTapManager = new SideTapManager(mTalkBack, mGestureController);
        mMockIntegratedDetector = new MockIntegratedTapDetector(
                (SensorManager) mTalkBack.getSystemService(Context.SENSOR_SERVICE));
        mSideTapManager.setIntegratedTapDetector(mMockIntegratedDetector);
        mMockIntegratedDetector.singleTapQuality = -1.0;
        mMockIntegratedDetector.doubleTapQuality = -1.0;
        mInstrumentation = getInstrumentation();
    }

    @Override
    protected void tearDown() throws Exception {
        // Must set prefs before teardown because TalkBack service is required.
        setSingleTapPreference(R.string.shortcut_value_unassigned);
        setDoubleTapPreference(R.string.shortcut_value_unassigned);

        super.tearDown();
    }

    private void setSingleTapPreference(int stringId) {
        SharedPreferences settings = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        settings.edit().putString(mTalkBack.getString(R.string.pref_shortcut_single_tap_key),
                mTalkBack.getString(stringId)).commit();
    }

    private void setDoubleTapPreference(int stringId) {
        SharedPreferences settings = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        settings.edit().putString(mTalkBack.getString(R.string.pref_shortcut_double_tap_key),
                mTalkBack.getString(stringId)).commit();
    }

    private void setTapSensitivityPreference(int stringId) {
        SharedPreferences settings = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        settings.edit().putString(mTalkBack.getString(R.string.pref_tap_sensitivity_key),
                mTalkBack.getString(stringId)).commit();
    }

    @MediumTest
    public void testReloadPreferences_singleTap() {
        setSingleTapPreference(R.string.shortcut_value_home);
        setDoubleTapPreference(R.string.shortcut_value_unassigned);
        mMockIntegratedDetector.doubleTapSpacing = -1;

        mSideTapManager.onReloadPreferences();
        assertEquals(1, mMockIntegratedDetector.startCount);
        assertEquals(0, mMockIntegratedDetector.stopCount);
        assertEquals(0, mMockIntegratedDetector.doubleTapSpacing);
    }

    @MediumTest
    public void testReloadPreferences_doubleTap() {
        setDoubleTapPreference(R.string.shortcut_value_home);
        setSingleTapPreference(R.string.shortcut_value_unassigned);
        mMockIntegratedDetector.doubleTapSpacing = -1;

        mSideTapManager.onReloadPreferences();
        assertEquals(1, mMockIntegratedDetector.startCount);
        assertEquals(0, mMockIntegratedDetector.stopCount);
        assertTrue(mMockIntegratedDetector.doubleTapSpacing > 0);
    }

    @MediumTest
    public void testReloadPreferences_tapsUnassigned() {
        setDoubleTapPreference(R.string.shortcut_value_unassigned);
        setSingleTapPreference(R.string.shortcut_value_unassigned);
        mMockIntegratedDetector.doubleTapSpacing = -1;

        mSideTapManager.onReloadPreferences();
        assertEquals(0, mMockIntegratedDetector.startCount);
        assertEquals(1, mMockIntegratedDetector.stopCount);
        assertEquals(0, mMockIntegratedDetector.doubleTapSpacing);
    }

    @MediumTest
    public void testReloadPreferences_sensitivityLowest() {
        setTapSensitivityPreference(R.string.tap_sensitivity_value_lowest);
        mSideTapManager.onReloadPreferences();

        assertEquals(IntegratedTapDetector.TAP_QUALITY_HIGHEST,
                mMockIntegratedDetector.singleTapQuality, 0.001);
        assertEquals(IntegratedTapDetector.TAP_QUALITY_LOW,
                mMockIntegratedDetector.doubleTapQuality, 0.001);
    }

    @MediumTest
    public void testReloadPreferences_sensitivityLow() {
        setTapSensitivityPreference(R.string.tap_sensitivity_value_low);
        mSideTapManager.onReloadPreferences();

        assertEquals(IntegratedTapDetector.TAP_QUALITY_HIGH,
                mMockIntegratedDetector.singleTapQuality, 0.001);
        assertEquals(IntegratedTapDetector.TAP_QUALITY_LOW,
                mMockIntegratedDetector.doubleTapQuality, 0.001);
    }

    @MediumTest
    public void testReloadPreferences_sensitivityMedium() {
        setTapSensitivityPreference(R.string.tap_sensitivity_value_medium);
        mSideTapManager.onReloadPreferences();

        assertEquals(IntegratedTapDetector.TAP_QUALITY_MEDIUM,
                mMockIntegratedDetector.singleTapQuality, 0.001);
        assertEquals(IntegratedTapDetector.TAP_QUALITY_LOW,
                mMockIntegratedDetector.doubleTapQuality, 0.001);
    }

    @MediumTest
    public void testReloadPreferences_sensitivityHigh() {
        setTapSensitivityPreference(R.string.tap_sensitivity_value_high);
        mSideTapManager.onReloadPreferences();

        assertEquals(IntegratedTapDetector.TAP_QUALITY_LOW,
                mMockIntegratedDetector.singleTapQuality, 0.001);
        assertEquals(IntegratedTapDetector.TAP_QUALITY_LOW,
                mMockIntegratedDetector.doubleTapQuality, 0.001);
    }

    @MediumTest
    public void testOnSuspendInfrastructure() {
        mSideTapManager.onSuspendInfrastructure();
        assertEquals(1, mMockIntegratedDetector.stopCount);
    }

    /* Simulate a screen touch by sending an accessibilityEvent to the sideTapManager */
    private void touchScreen() {
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START);
        mSideTapManager.onAccessibilityEvent(event);
    }

    @MediumTest
    public void testSingleTaps_tapSentToTalkBack() {
        /* Single Tap at a legal time */
        touchScreen();
        long startTime = System.nanoTime();
        mSideTapManager.onHapticFeedbackStarting(startTime);
        mSideTapManager.onSingleTap(startTime + 2000 * 1000 * 1000L);
        assertEquals(1, mGestureController.mNumPerformCustomGestureCalls);

        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        String expectedAction = prefs.getString(
                mTalkBack.getString(R.string.pref_shortcut_single_tap_key),
                mTalkBack.getString(R.string.pref_shortcut_single_tap_default));
        assertEquals(expectedAction, mGestureController.mLastAction);

        /* Tap should not be sent along if service is not active */
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mTalkBack.suspendTalkBack();
            }
        });
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();
        mSideTapManager.onSingleTap(startTime + 4000 * 1000 * 1000L);
        assertEquals(1, mGestureController.mNumPerformCustomGestureCalls);

        /* Make sure it works again after resume */
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mTalkBack.resumeTalkBack();
            }
        });
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();
        mSideTapManager.onSingleTap(startTime + 6000 * 1000 * 1000L);
        assertEquals(2, mGestureController.mNumPerformCustomGestureCalls);
    }

    @MediumTest
    public void testSingleTaps_ignoreTapsAfterScreenTouch() {
        /* Single Tap 10 ms after a screen touch */
        touchScreen();
        long startTime = System.nanoTime();
        mSideTapManager.onHapticFeedbackStarting(startTime - 2000 * 1000 * 1000L);
        mSideTapManager.onSingleTap(startTime + 10 * 1000 * 1000L);
        assertEquals(0, mGestureController.mNumPerformCustomGestureCalls);
    }

    @MediumTest
    public void testSingleTaps_ignoreTapsAfterHaptic() {
        /* Single Tap 10 ms after haptic */
        touchScreen();
        long startTime = System.nanoTime();
        mSideTapManager.onHapticFeedbackStarting(startTime + 1990 * 1000 * 1000L);
        mSideTapManager.onSingleTap(startTime + 2000 * 1000 * 1000L);
        assertEquals(0, mGestureController.mNumPerformCustomGestureCalls);
    }

    @MediumTest
    public void testDoubleTaps_tapSentToTalkBack() {
        /* Double Tap at a legal time */
        touchScreen();
        long startTime = System.nanoTime();
        mSideTapManager.onHapticFeedbackStarting(startTime);
        mSideTapManager.onDoubleTap(startTime + 2000 * 1000 * 1000L);
        assertEquals(1, mGestureController.mNumPerformCustomGestureCalls);

        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        String expectedAction = prefs.getString(
                mTalkBack.getString(R.string.pref_shortcut_double_tap_key),
                mTalkBack.getString(R.string.pref_shortcut_double_tap_default));
        assertEquals(expectedAction, mGestureController.mLastAction);

        /* Tap should not be sent along if service is not active */
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mTalkBack.suspendTalkBack();
            }
        });
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();
        mSideTapManager.onDoubleTap(startTime + 4000 * 1000 * 1000L);
        assertEquals(1, mGestureController.mNumPerformCustomGestureCalls);

        /* Make sure it works again after resume */
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mTalkBack.resumeTalkBack();
            }
        });
        mInstrumentation.waitForIdleSync();
        waitForAccessibilityIdleSync();
        mSideTapManager.onDoubleTap(startTime + 6000 * 1000 * 1000L);
        assertEquals(2, mGestureController.mNumPerformCustomGestureCalls);
    }

    @MediumTest
    public void testDoubleTaps_ignoreTapsAfterScreenTouch() {
        /* Single Tap 10 ms before a screen touch */
        touchScreen();
        long startTime = System.nanoTime();
        mSideTapManager.onHapticFeedbackStarting(startTime - 2000 * 1000 * 1000L);
        mSideTapManager.onDoubleTap(startTime - 10 * 1000 * 1000L);
        assertEquals(0, mGestureController.mNumPerformCustomGestureCalls);
    }

    @MediumTest
    public void testDoubleTaps_ignoreTapsAfterHaptic() {
        /* Double Tap 10 ms after haptic */
        touchScreen();
        long startTime = System.nanoTime();
        mSideTapManager.onHapticFeedbackStarting(startTime + 1990 * 1000 * 1000L);
        mSideTapManager.onDoubleTap(startTime + 2000 * 1000 * 1000L);
        assertEquals(0, mGestureController.mNumPerformCustomGestureCalls);
    }

    @MediumTest
    public void testScreenTurnedOffAndOn_tapDetectorShouldStopAndStart() {
        setSingleTapPreference(R.string.shortcut_value_home);
        int startCountBefore = mMockIntegratedDetector.startCount;
        int stopCountBefore = mMockIntegratedDetector.stopCount;
        Intent screenOnIntent = new Intent(Intent.ACTION_SCREEN_ON);
        Intent screenOffIntent = new Intent(Intent.ACTION_SCREEN_OFF);
        mSideTapManager.onReceive(mTalkBack, screenOffIntent);
        assertEquals(1, mMockIntegratedDetector.stopCount - stopCountBefore);
        assertEquals(0, mMockIntegratedDetector.startCount - startCountBefore);

        mSideTapManager.onReceive(mTalkBack, screenOnIntent);
        assertEquals(1, mMockIntegratedDetector.stopCount - stopCountBefore);
        assertEquals(1, mMockIntegratedDetector.startCount - startCountBefore);
    }
}

/*
 * TODO I'd prefer to use mockito here, but I get confusing
 * error messages that the class can't be constructed, so I'm creating these
 * mocks myself.
 */
final class MockIntegratedTapDetector extends IntegratedTapDetector {

    public int startCount = 0, stopCount = 0;

    public long messageDelay;

    public long doubleTapSpacing;

    public double singleTapQuality;

    public double doubleTapQuality;

    public MockIntegratedTapDetector(SensorManager sensorManager) {
        super(sensorManager);
    }

    @Override
    public void start() {
        startCount++;
    }

    @Override
    public void stop() {
        stopCount++;
    }

    @Override
    public void setMaxDoubleTapSpacingNanos(long maxDTapSpacing) {
        doubleTapSpacing = maxDTapSpacing;
    }

    @Override
    public void setTapDetectionQuality(double tq) {
        singleTapQuality = tq;
    }

    @Override
    public void setDoubleTapDetectionQuality(double tq) {
        doubleTapQuality = tq;
    }

}

final class MockGestureController implements GestureController {

    public int mNumPerformCustomGestureCalls = 0;
    public String mLastAction;

    @Override
    public void onGesture(int gestureId) {
        mNumPerformCustomGestureCalls++;
    }

    @Override
    public void performAction(String action) {
        mNumPerformCustomGestureCalls++;
        mLastAction = action;
    }

    @Override
    public String gestureFromAction(String action) {
        return "";
    }

    @Override
    public String gestureDescriptionFromAction(String action) {
        return "";
    }
}
