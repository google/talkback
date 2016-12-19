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
package com.android.talkback.eventprocessor;

import android.content.Intent;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import com.android.talkback.R;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

public class AccessibilityEventProcessorTest extends TalkBackInstrumentationTestCase {

    private int mMatchEventType = 0;
    private AccessibilityNodeInfoCompat mMatchNode = null;
    private boolean mMatched = false;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setContentView(R.layout.view_selected_events);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (mMatchNode != null) {
            mMatchNode.recycle();
            mMatchNode = null;
        }
        mMatchEventType = 0;
        mMatched = false;
    }

    @MediumTest
    public void testViewFocused_overridesViewSelected() {
        final View outerView = getViewForId(R.id.outer_view);
        final View middleView = getViewForId(R.id.middle_view);
        final View innerView = getViewForId(R.id.inner_view);

        mMatchEventType = AccessibilityEvent.TYPE_VIEW_SELECTED;

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                middleView.requestFocus();
                outerView.setSelected(true);
                innerView.setSelected(true);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertFalse(mMatched);
    }

    @MediumTest
    public void testViewFocused_doesntOverrideUnrelatedViewSelected() {
        final View outerView = getViewForId(R.id.outer_view);
        final View middleView = getViewForId(R.id.middle_view);
        final View seekBar = getViewForId(R.id.seek_bar);

        mMatchEventType = AccessibilityEvent.TYPE_VIEW_SELECTED;
        mMatchNode = AccessibilityNodeInfoCompat.obtain(seekBar);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                middleView.requestFocus();
                outerView.setSelected(true);
                seekBar.setSelected(true);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mMatched);
    }

    /**
     * Make sure that selection-event elimination does not get rid of progress bar VIEW_SELECTED
     * events.
     */
    @MediumTest
    public void testProgressBarSelectedEvents() {
        final ProgressBar progressBar = (ProgressBar) getViewForId(R.id.progress_bar);
        final AccessibilityNodeInfoCompat progressBarNode = getNodeForView(progressBar);

        mMatchEventType = AccessibilityEvent.TYPE_VIEW_SELECTED;
        mMatchNode = AccessibilityNodeInfoCompat.obtain(progressBarNode);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                progressBar.requestFocus();
                progressBar.setProgress(25);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mMatched);
    }

    /**
     * Make sure that selection-event elimination does not get rid of selection bar VIEW_SELECTED
     * events.
     */
    @MediumTest
    public void testSeekBarSelectedEvents() {
        final SeekBar seekBar = (SeekBar) getViewForId(R.id.seek_bar);
        final AccessibilityNodeInfoCompat seekBarNode = getNodeForView(seekBar);

        mMatchEventType = AccessibilityEvent.TYPE_VIEW_SELECTED;
        mMatchNode = AccessibilityNodeInfoCompat.obtain(seekBarNode);

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                seekBar.requestFocus();
                seekBar.setProgress(25);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();

        assertTrue(mMatched);
    }

    /**
     * This is a special case: the screen is off and telephony reports that the phone isn't
     * ringing. However, telephony might be a split-second too late; the dialer might appear and
     * send the accessibility event BEFORE telephony switches to ringing state. In this case,
     * we should still process the event!
     */
    @MediumTest
    public void testWindowStateChanged_fromDialer_screenOff_notRinging() {
        mMatchEventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        mMatchNode = null;

        if (!simulateTelephonyState(TelephonyManager.EXTRA_STATE_IDLE)) {
            return;
        }
        simulateScreenState(false);
        sendEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, getDialerClassName());

        assertTrue(mMatched);
    }

    @MediumTest
    public void testWindowStateChanged_fromDialer_screenOn_notRinging() {
        mMatchEventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        mMatchNode = null;

        if (!simulateTelephonyState(TelephonyManager.EXTRA_STATE_IDLE)) {
            return;
        }
        simulateScreenState(true);
        sendEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, getDialerClassName());

        assertTrue(mMatched);
    }

    @MediumTest
    public void testWindowStateChanged_fromDialer_screenOff_ringing() {
        mMatchEventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        mMatchNode = null;

        if (!simulateTelephonyState(TelephonyManager.EXTRA_STATE_RINGING)) {
            return;
        }
        simulateScreenState(false);
        sendEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, getDialerClassName());

        assertTrue(mMatched);
    }

    @MediumTest
    public void testWindowStateChanged_notFromDialer_screenOff() {
        mMatchEventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        mMatchNode = null;

        simulateTelephonyState(TelephonyManager.EXTRA_STATE_IDLE);
        simulateScreenState(false);
        sendEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.example.app.ExampleClass");

        assertFalse(mMatched);
    }

    @MediumTest
    public void testWindowStateChanged_notFromDialer_screenOn() {
        mMatchEventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        mMatchNode = null;

        simulateTelephonyState(TelephonyManager.EXTRA_STATE_IDLE);
        simulateScreenState(true);
        sendEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.example.app.ExampleClass");

        assertTrue(mMatched);
    }

    @MediumTest
    public void testQuickRefocusSameNode_eventDropped() {
        final SeekBar seekBar = (SeekBar) getViewForId(R.id.seek_bar);
        final AccessibilityNodeInfoCompat seekBarNode = getNodeForView(seekBar);

        getService().getCursorController().setCursor(seekBarNode);
        waitForAccessibilityIdleSync();

        mMatchEventType = AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
        mMatchNode = AccessibilityNodeInfoCompat.obtain(seekBarNode);

        seekBarNode.performAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        seekBarNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        waitForAccessibilityIdleSync();

        assertFalse(mMatched);
    }

    @MediumTest
    public void testQuickRefocusSameNode_fromTalkBack_eventNotDropped() {
        final SeekBar seekBar = (SeekBar) getViewForId(R.id.seek_bar);
        final AccessibilityNodeInfoCompat seekBarNode = getNodeForView(seekBar);

        getService().getCursorController().setCursor(seekBarNode);
        waitForAccessibilityIdleSync();

        mMatchEventType = AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
        mMatchNode = AccessibilityNodeInfoCompat.obtain(seekBarNode);

        getService().getCursorController().refocus();
        waitForAccessibilityIdleSync();

        assertTrue(mMatched);
    }

    private String getDialerClassName() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return AccessibilityEventProcessor.CLASS_DIALER_JELLY_BEAN;
        } else {
            return AccessibilityEventProcessor.CLASS_DIALER_KITKAT;
        }
    }

    /**
     * Tells TalkBack's CallStateMonitor that the current call state has changed, but otherwise
     * changes nothing on the system.
     * @param state {@link TelephonyManager#EXTRA_STATE_IDLE},
     *         {@link TelephonyManager#EXTRA_STATE_RINGING}, or
     *         {@link TelephonyManager#EXTRA_STATE_OFFHOOK}
     * @return {@code true} if the CallStateMonitor exists, {@code false} otherwise (e.g. running on
     *         a tablet and not a phone)
     */
    private boolean simulateTelephonyState(String state) {
        Intent intent = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                .putExtra(TelephonyManager.EXTRA_STATE, state);
        try {
            getService().getCallStateMonitor().onReceive(getService(), intent);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void simulateScreenState(boolean screenOn) {
        Intent intent = new Intent(screenOn ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF);
        getService().getRingerModeAndScreenMonitor().onReceive(getService(), intent);
    }

    private void sendEvent(final int event, final CharSequence className) {
        final View outerView = getViewForId(R.id.outer_view);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                outerView.setAccessibilityDelegate(new AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityEvent(View host,
                            AccessibilityEvent event) {
                        super.onInitializeAccessibilityEvent(host, event);
                        event.setClassName(className);
                    }
                });
                outerView.sendAccessibilityEvent(event);
            }
        });
        getInstrumentation().waitForIdleSync();
        waitForAccessibilityIdleSync();
    }

    @Override
    public void afterEventReceived(AccessibilityEvent event) {
        if (mMatched) {
            return;
        }

        if (event.getEventType() == mMatchEventType) {
            if (mMatchNode == null) {
                mMatched = true;
            } else {
                AccessibilityNodeInfoCompat eventNode =
                        new AccessibilityNodeInfoCompat(event.getSource());
                mMatched = mMatchNode.equals(eventNode);
            }
        }
    }

}
