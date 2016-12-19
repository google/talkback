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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;

import android.view.KeyEvent;
import com.android.talkback.BuildConfig;
import com.android.talkback.R;
import com.android.talkback.controller.CursorController;
import com.android.talkback.controller.DimScreenController;
import com.android.talkback.controller.DimScreenControllerApp;
import com.android.talkback.controller.FeedbackController;
import com.android.utils.SharedPreferencesUtils;

import com.google.android.marvin.talkback.TalkBackService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricGradleTestRunner.class)
public class ProcessorVolumeStreamTest {

    private Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private ProcessorVolumeStream mProcessorVolumeStream;
    private DimScreenController mDimScreenController;

    private class MockTalkBackService extends TalkBackService {

        public MockTalkBackService() {
            attachBaseContext(mContext);
        }

        @Override
        public Object getSystemService(String name) {
            if (name.equals(Context.AUDIO_SERVICE)) {
                return mock(AudioManager.class);
            }

            return super.getSystemService(name);
        }

        @Override
        public boolean isInstanceActive() {
            return true;
        }

    }

    @Before
    public void setUp() {
        TalkBackService talkBack = new MockTalkBackService();
        FeedbackController feedbackController = mock(FeedbackController.class);
        CursorController cursorController = mock(CursorController.class);

        mDimScreenController = new DimScreenControllerApp(mContext);

        mProcessorVolumeStream = new ProcessorVolumeStream(feedbackController, cursorController,
                mDimScreenController, talkBack);

        // Disable dim confirmation so it doesn't interfere with testing.
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_show_dim_screen_confirmation_dialog, false);
    }

    @After
    public void tearDown() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);

        // Disable dimming in case it was turned on.
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_when_talkback_enabled_key, false);

        // Disable three-volume shortcut in case it was turned on.
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_volume_three_clicks_key, false);

        // Re-enable the dim confirmation.
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_show_dim_screen_confirmation_dialog, true);
    }

    @Test
    public void threeTapsSettingOn_shouldEnableDimmingWhenUndimmed() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_volume_three_clicks_key, true);

        assertFalse(mDimScreenController.isDimmingEnabled());
        simulateThreeVolumeTaps();
        assertTrue(mDimScreenController.isDimmingEnabled());
    }

    @Test
    public void threeTapsSettingOn_shouldDisableDimmingWhenDimmed() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_when_talkback_enabled_key, true);
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_volume_three_clicks_key, true);

        assertTrue(mDimScreenController.isDimmingEnabled());
        simulateThreeVolumeTaps();
        assertFalse(mDimScreenController.isDimmingEnabled());
    }

    @Test
    public void threeTapsSettingOff_shouldNotEnableDimmingWhenUndimmed() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_volume_three_clicks_key, false);

        assertFalse(mDimScreenController.isDimmingEnabled());
        simulateThreeVolumeTaps();
        assertFalse(mDimScreenController.isDimmingEnabled());
    }

    @Test
    public void threeTapsSettingOff_shouldNotDisableDimmingWhenDimmed() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_when_talkback_enabled_key, true);
        SharedPreferencesUtils.putBooleanPref(prefs, mContext.getResources(),
                R.string.pref_dim_volume_three_clicks_key, false);

        assertTrue(mDimScreenController.isDimmingEnabled());
        simulateThreeVolumeTaps();
        assertTrue(mDimScreenController.isDimmingEnabled());
    }

    private void simulateThreeVolumeTaps() {
        long first = SystemClock.uptimeMillis();
        simulateTapBothVolumeKeys(first);
        simulateTapBothVolumeKeys(first + 500);
        simulateTapBothVolumeKeys(first + 1000);
    }

    private void simulateTapBothVolumeKeys(long down) {
        long up = down + 200;

        KeyEvent volUpKeyDown = new KeyEvent(down, down, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP, 0);
        mProcessorVolumeStream.onKeyEvent(volUpKeyDown);

        KeyEvent volDownKeyDown = new KeyEvent(down, down, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_VOLUME_DOWN, 0);
        mProcessorVolumeStream.onKeyEvent(volDownKeyDown);

        KeyEvent volUpKeyUp = new KeyEvent(down, up, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_VOLUME_UP, 0);
        mProcessorVolumeStream.onKeyEvent(volUpKeyUp);

        KeyEvent volDownKeyUp = new KeyEvent(down, up, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN, 0);
        mProcessorVolumeStream.onKeyEvent(volDownKeyUp);
    }

}
