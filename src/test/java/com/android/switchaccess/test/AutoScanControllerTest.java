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

package com.android.switchaccess.test;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.android.talkback.BuildConfig;
import com.android.talkback.R;

import com.android.switchaccess.AutoScanController;
import com.android.switchaccess.OptionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowHandler;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.Scheduler;

/**
 * Tests for AutoScanController
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowHandler.class
        })
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class AutoScanControllerTest {
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final Handler mHandler = new Handler();
    private final OptionManager mOptionManager = mock(OptionManager.class);
    private final AutoScanController mAutoScanControllerForOptionScanning
            = new AutoScanController(mOptionManager, mHandler, mContext);
    private Scheduler mRobolectricScheduler;

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        clearPreferences();
        enableAutoScanPreference();
        ShadowLooper shadowLooper = (ShadowLooper) ShadowExtractor.extract(mHandler.getLooper());
        mRobolectricScheduler = shadowLooper.getScheduler();
    }

    @After
    public void tearDown() {
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Test
    public void testStartScanning_makesSelectionOnOptionManager() {
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        verify(mOptionManager, times(1)).selectOption(1);
        verify(mOptionManager, times(1))
                .addOptionManagerListener((OptionManager.OptionManagerListener) anyObject());
        verifyNoMoreInteractions(mOptionManager);
    }

    @Test
    public void testAutoStartScanning_scanningContinues() {
        mAutoScanControllerForOptionScanning.onOptionManagerStartedAutoScan();
        assertEquals(1, mRobolectricScheduler.size());
        // Make sure we don't scan a second time, since the option manager just set focus
        verify(mOptionManager, times(1))
                .addOptionManagerListener((OptionManager.OptionManagerListener) anyObject());
        verifyNoMoreInteractions(mOptionManager);
    }

    @Test
    public void testSelecting_selectsOnOptionManager() {
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        verify(mOptionManager, times(1)).selectOption(1);
        verify(mOptionManager, times(1)).selectOption(0);
        verify(mOptionManager, times(1))
                .addOptionManagerListener((OptionManager.OptionManagerListener) anyObject());
        verifyNoMoreInteractions(mOptionManager);
    }

    @Test
    public void testSelecting_scanningContinuesIfFocusNotCleared() {
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        verify(mOptionManager, times(1)).selectOption(1);
        verify(mOptionManager, times(1)).selectOption(0);
        assertEquals(1, mRobolectricScheduler.size());
    }

    @Test
    public void testSelecting_scanningStopsIfFocusCleared() {
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                mAutoScanControllerForOptionScanning.onOptionManagerClearedFocus();
                return null;
            }
        }).when(mOptionManager).selectOption(0);
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        verify(mOptionManager, times(1)).selectOption(1);
        verify(mOptionManager, times(1)).selectOption(0);
        assertEquals(0, mRobolectricScheduler.size());
    }

    @Test
    public void onClearFocus_stopsScanning() {
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        mAutoScanControllerForOptionScanning.onOptionManagerClearedFocus();
        ShadowHandler.runMainLooperOneTask();
        verify(mOptionManager, times(1)).selectOption(1);
        verify(mOptionManager, times(1))
                .addOptionManagerListener((OptionManager.OptionManagerListener) anyObject());
        verifyNoMoreInteractions(mOptionManager);
    }

    @Test
    public void testAutoReverseReverse_switchesDirectionAndSelects() {
        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        ShadowHandler.runMainLooperOneTask();
        verify(mOptionManager, times(2)).selectOption(1);

        mAutoScanControllerForOptionScanning.autoScanActivated(true);
        ShadowHandler.runMainLooperOneTask();
        verify(mOptionManager, times(2)).selectOption(1);

        mAutoScanControllerForOptionScanning.autoScanActivated(true);
        verify(mOptionManager, times(2)).selectOption(1);
        verify(mOptionManager, times(1)).moveToParent(true);
        verify(mOptionManager, times(1))
                .addOptionManagerListener((OptionManager.OptionManagerListener) anyObject());
    }
    @Test
    public void testReverseAutoAuto_switchesDirectionAndSelects() {
        mAutoScanControllerForOptionScanning.autoScanActivated(true);
        ShadowHandler.runMainLooperOneTask();
        verify(mOptionManager, times(2)).moveToParent(true);

        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        ShadowHandler.runMainLooperOneTask();
        verify(mOptionManager, times(2)).moveToParent(true);

        mAutoScanControllerForOptionScanning.autoScanActivated(false);
        verify(mOptionManager, times(2)).moveToParent(true);
        verify(mOptionManager, times(1)).selectOption(1);
        verify(mOptionManager, times(1))
                .addOptionManagerListener((OptionManager.OptionManagerListener) anyObject());
    }

    private void enableAutoScanPreference() {
        final String preferenceKey = mContext.getString(R.string.pref_key_auto_scan_enabled);
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putBoolean(preferenceKey, true).apply();
    }

    private void clearPreferences() {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().clear().apply();
    }
}
