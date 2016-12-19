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

import static org.junit.Assert.assertEquals;

import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

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
public class TalkBackUpdateHelperTest {

    private TalkBackService mService;
    private Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private TalkBackUpdateHelper mUpdateHelper;

    private class MockTalkBackService extends TalkBackService {

        private KeyComboManager mKeyComboManager;

        public MockTalkBackService() {
            attachBaseContext(RuntimeEnvironment.application.getApplicationContext());
            mKeyComboManager = KeyComboManager.create(
                    RuntimeEnvironment.application.getApplicationContext());
        }

        @Override
        public KeyComboManager getKeyComboManager() {
            return mKeyComboManager;
        }
    }

    @Before
    public void setUp() {
        mService = new MockTalkBackService();
        mUpdateHelper = new TalkBackUpdateHelper(mService);
    }

    @Test
    public void testUpdateKeyCombos_shouldNotOverrideUserCombo() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.edit().putInt(TalkBackUpdateHelper.PREF_APP_VERSION, 1).apply();

        KeyEvent altSix = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_6, 0,
                KeyEvent.META_ALT_ON);
        long altSixCode = KeyComboManager.getKeyComboCode(altSix);

        String increaseGranularity =
                mContext.getString(R.string.keycombo_shortcut_granularity_increase);
        mService.getKeyComboManager().getKeyComboModel()
                .saveKeyComboCode(increaseGranularity, altSixCode);

        mUpdateHelper.checkUpdate();

        long newIncreaseGranularityCode = mService.getKeyComboManager().getKeyComboModel()
                .getKeyComboCodeForKey(increaseGranularity);

        assertEquals(altSixCode, newIncreaseGranularityCode);
    }

    @Test
    public void testUpdateKeyCombos_shouldOverrideDefaultCombo() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.edit().putInt(TalkBackUpdateHelper.PREF_APP_VERSION, 1).apply();

        KeyEvent altPlus = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PLUS, 0,
                KeyEvent.META_ALT_ON);
        long altPlusCode = KeyComboManager.getKeyComboCode(altPlus);

        String increaseGranularity =
                mContext.getString(R.string.keycombo_shortcut_granularity_increase);
        mService.getKeyComboManager().getKeyComboModel()
                .saveKeyComboCode(increaseGranularity, altPlusCode);

        mUpdateHelper.checkUpdate();

        long newIncreaseGranularityCode = mService.getKeyComboManager().getKeyComboModel()
                .getKeyComboCodeForKey(increaseGranularity);
        long defaultIncreaseGranularityCode = mService.getKeyComboManager().getKeyComboModel()
                .getDefaultKeyComboCode(increaseGranularity);

        assertEquals(defaultIncreaseGranularityCode, newIncreaseGranularityCode);
    }

    @Test
    public void testUpdateKeyCombos_shouldNotOverrideOnVersion40400000() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.edit().putInt(TalkBackUpdateHelper.PREF_APP_VERSION, 40400000).apply();

        KeyEvent altPlus = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PLUS, 0,
                KeyEvent.META_ALT_ON);
        long altPlusCode = KeyComboManager.getKeyComboCode(altPlus);

        String increaseGranularity =
                mContext.getString(R.string.keycombo_shortcut_granularity_increase);
        mService.getKeyComboManager().getKeyComboModel()
                .saveKeyComboCode(increaseGranularity, altPlusCode);

        mUpdateHelper.checkUpdate();

        long newIncreaseGranularityCode = mService.getKeyComboManager().getKeyComboModel()
                .getKeyComboCodeForKey(increaseGranularity);
        long defaultIncreaseGranularityCode = mService.getKeyComboManager().getKeyComboModel()
                .getDefaultKeyComboCode(increaseGranularity);

        assertEquals(altPlusCode, newIncreaseGranularityCode);
    }

    @Test
    public void testUpdateGestures45_shouldNotOverrideUserGestures_userChangedBoth() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.edit().putInt(TalkBackUpdateHelper.PREF_APP_VERSION, 1).apply();

        setUpDownGestureActions(R.string.shortcut_value_scroll_back,
                R.string.shortcut_value_overview);
        mUpdateHelper.checkUpdate();
        assertUpDownGestureActions(R.string.shortcut_value_scroll_back,
                R.string.shortcut_value_overview);
    }

    @Test
    public void testUpdateGestures45_shouldNotOverrideUserGestures_userChangedOne() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.edit().putInt(TalkBackUpdateHelper.PREF_APP_VERSION, 1).apply();

        setUpDownGestureActions(0, R.string.shortcut_value_overview);
        mUpdateHelper.checkUpdate();
        assertUpDownGestureActions(R.string.pref_deprecated_shortcut_up,
                R.string.shortcut_value_overview);
    }

    @Test
    public void testUpdateGestures45_shouldOverrideDefaultGestures() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.edit().putInt(TalkBackUpdateHelper.PREF_APP_VERSION, 1).apply();

        // Let's mix this up by explicitly setting one of the default gestures but leaving the
        // other one blank. We expect the gestures to still be over-written in this case.
        setUpDownGestureActions(0, R.string.pref_deprecated_shortcut_down);
        mUpdateHelper.checkUpdate();
        assertUpDownGestureActions(R.string.pref_shortcut_up_default,
                R.string.pref_shortcut_down_default);
    }

    @Test
    public void testUpdateGestures45_shouldNotOverrideOnVersion40500000() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.edit().putInt(TalkBackUpdateHelper.PREF_APP_VERSION, 40500000).apply();

        setUpDownGestureActions(R.string.pref_deprecated_shortcut_up,
                R.string.pref_deprecated_shortcut_down);
        mUpdateHelper.checkUpdate();
        assertUpDownGestureActions(R.string.pref_deprecated_shortcut_up,
                R.string.pref_deprecated_shortcut_down);
    }

    /**
     * Sets the up gesture and down gesture to the actions given by the specified resource IDs.
     * Pass a resource ID of 0 to remove the preference, simulating the state of the preference had
     * the user never modified it.
     */
    private void setUpDownGestureActions(int upAction, int downAction) {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        final SharedPreferences.Editor editor = prefs.edit();

        final String upKey = mService.getString(R.string.pref_shortcut_up_key);
        final String downKey = mService.getString(R.string.pref_shortcut_down_key);

        if (upAction == 0) {
            editor.remove(upKey);
        } else {
            editor.putString(upKey, mService.getString(upAction));
        }

        if (downAction == 0) {
            editor.remove(downKey);
        } else {
            editor.putString(downKey, mService.getString(downAction));
        }

        editor.apply();
    }

    private void assertUpDownGestureActions(int upAction, int downAction) {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);

        final String upKey = mService.getString(R.string.pref_shortcut_up_key);
        final String downKey = mService.getString(R.string.pref_shortcut_down_key);
        final String upDefault = mService.getString(R.string.pref_shortcut_up_default);
        final String downDefault = mService.getString(R.string.pref_shortcut_down_default);

        String upPref = prefs.getString(upKey, upDefault);
        assertEquals(mService.getString(upAction), upPref);

        String downPref = prefs.getString(downKey, downDefault);
        assertEquals(mService.getString(downAction), downPref);
    }

}
