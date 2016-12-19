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

package com.android.switchaccess;

import android.os.Build;
import android.support.annotation.NonNull;
import android.test.suitebuilder.annotation.MediumTest;
import com.android.talkback.R;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.widget.Button;
import android.widget.ScrollView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

/**
 * End-to-end tests for SwitchAccessService
 */
public class SwitchAccessEndToEndTest extends SwitchAccessInstrumentationTestCase {

    private final KeyEvent mMoveFocusEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A);
    private final KeyEvent mMoveFocusKeyUpEvent =
            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A);

    private final KeyEvent mClickEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B);

    private final KeyEvent mScrollForwardEvent =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C);

    private SwitchAccessService mService;

    @Override
    public void setUp() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        super.setUp();
        setContentView(R.layout.scroll_two_buttons);
        mService = (SwitchAccessService) getService();
        Context context = mService;

        /* Set up preferences for key events */
        String preferenceKey = context.getString(R.string.pref_key_mapped_to_next_key);
        long extendedKeyCode =  KeyComboPreference.keyEventToExtendedKeyCode(mMoveFocusEvent);
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().
                putLong(preferenceKey, extendedKeyCode).commit();

        preferenceKey = context.getString(R.string.pref_key_mapped_to_click_key);
        extendedKeyCode =  KeyComboPreference.keyEventToExtendedKeyCode(mClickEvent);
        PreferenceManager.getDefaultSharedPreferences(context).edit().
                putLong(preferenceKey, extendedKeyCode).commit();

        preferenceKey = context.getString(R.string.pref_key_mapped_to_scroll_forward_key);
        extendedKeyCode =  KeyComboPreference.keyEventToExtendedKeyCode(mScrollForwardEvent);
        PreferenceManager.getDefaultSharedPreferences(context).edit().
                putLong(preferenceKey, extendedKeyCode).commit();
    }

    @Override
    protected void tearDown() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        super.tearDown();
    }

    @MediumTest
    public void testButton1_click() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        final Button button = (Button) getActivity().findViewById(R.id.button_1);
        final CountDownLatch clicksMissed = new CountDownLatch(1);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clicksMissed.countDown();
            }
        });
        sendKeyEventSync(mMoveFocusEvent);
        sendKeyEventSync(mMoveFocusKeyUpEvent);
        sendKeyEventSync(mMoveFocusEvent);
        sendKeyEventSync(mClickEvent);
        assertEquals(0, clicksMissed.getCount());
    }

    @MediumTest
    public void testScrolling_scroll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        final ScrollView scrollView = (ScrollView) getActivity().findViewById(R.id.scroll_id);
        final CountDownLatch scrollsMissed = new CountDownLatch(1);
        scrollView.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, Bundle args) {
                if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
                    scrollsMissed.countDown();
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        sendKeyEventSync(mMoveFocusEvent);
        sendKeyEventSync(mScrollForwardEvent);
        assertEquals(0, scrollsMissed.getCount());
    }

    private void sendKeyEventSync(KeyEvent event) {
        /* Send the keystroke using reflection */
        try {
            Method onKeyEvent = mService.getClass().getDeclaredMethod("onKeyEvent", KeyEvent.class);
            onKeyEvent.setAccessible(true);
            onKeyEvent.invoke(mService, event);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                | IllegalArgumentException e) {
            e.printStackTrace();
        }

        getInstrumentation().waitForIdleSync();
    }
}
