/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import com.android.talkback.KeyComboManager;
import com.android.talkback.keyboard.DefaultKeyComboModel;
import com.android.talkback.keyboard.KeyComboModel;
import com.android.talkback.keyboard.KeyComboPersister;
import com.android.utils.SharedPreferencesUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.Long;
import java.util.Map;

@Config(constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricGradleTestRunner.class)
public class DefaultKeyComboModelTest {
    private static final String PREF_KEY_PREFIX = "default_key_combo_model|";

    private DefaultKeyComboModel mDefaultKeyComboModel;
    private Context mContext;
    private SharedPreferences mPref;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application.getApplicationContext();
        mDefaultKeyComboModel = new DefaultKeyComboModel(mContext);
        mPref = SharedPreferencesUtils.getSharedPreferences(mContext);
    }

    @After
    public void tearDown() {
        mPref.edit().clear().commit();
    }

    @Test
    public void persistDefaultKeyComboCodeAtInitialization() {
        String keyNavigateNext = mContext.getString(R.string.keycombo_shortcut_navigate_next);
        KeyComboPersister keyComboPersister =
                new KeyComboPersister(mContext, DefaultKeyComboModel.PREF_KEY_PREFIX);

        // Confirm that default key combo code for navigating to next item is persisted.
        assertTrue(keyComboPersister.contains(keyNavigateNext));
        assertEquals(mDefaultKeyComboModel.getDefaultKeyComboCode(keyNavigateNext),
                keyComboPersister.getKeyComboCode(keyNavigateNext).longValue());
    }

    @Test
    public void persistCustomKeyComboCode() {
        String keyNavigateNext = mContext.getString(R.string.keycombo_shortcut_navigate_next);
        long customKeyComboCode =
                KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X);
        assertFalse(customKeyComboCode ==
                mDefaultKeyComboModel.getDefaultKeyComboCode(keyNavigateNext));
        KeyComboPersister keyComboPersister =
                new KeyComboPersister(mContext, DefaultKeyComboModel.PREF_KEY_PREFIX);

        // Change key combo code for navigating to next item to custom one.
        keyComboPersister.saveKeyCombo(keyNavigateNext, customKeyComboCode);

        // Create another DefaultKeyComboModel to confirm that the custom one is persisted. We
        // cannot simply read the value from mDefaultKeyComboModel here since it's instantiated
        // before the key combo code is changed in the above.
        DefaultKeyComboModel defaultKeyComboModel = new DefaultKeyComboModel(mContext);
        assertEquals(customKeyComboCode,
                defaultKeyComboModel.getKeyComboCodeForKey(keyNavigateNext));
    }

    @Test
    public void changeKeyComboCode() {
        String keyNavigateNext = mContext.getString(R.string.keycombo_shortcut_navigate_next);

        // Check default key combo code.
        long defaultKeyComboCode = mDefaultKeyComboModel.getDefaultKeyComboCode(keyNavigateNext);
        assertEquals(defaultKeyComboCode,
                mDefaultKeyComboModel.getKeyComboCodeForKey(keyNavigateNext));

        // Change key combo code.
        long newKeyComboCode = KeyComboManager.getKeyComboCode(
                KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X);
        mDefaultKeyComboModel.saveKeyComboCode(keyNavigateNext, newKeyComboCode);

        Map<String, Long> keyComboCodeMap = mDefaultKeyComboModel.getKeyComboCodeMap();
        assertEquals(newKeyComboCode, keyComboCodeMap.get(keyNavigateNext).longValue());
        assertEquals(newKeyComboCode, mDefaultKeyComboModel.getKeyComboCodeForKey(keyNavigateNext));

        assertEquals(newKeyComboCode, mPref.getLong(PREF_KEY_PREFIX + keyNavigateNext, -1));

        // Create new DefaultKeyComboModel model and confirm it's loaded.
        DefaultKeyComboModel newDefaultKeyComboModel = new DefaultKeyComboModel(mContext);
        assertEquals(newKeyComboCode,
                newDefaultKeyComboModel.getKeyComboCodeForKey(keyNavigateNext));
    }

    @Test
    public void clearKeyComboCode() {
        String keyNavigateNext = mContext.getString(R.string.keycombo_shortcut_navigate_next);

        // Check default key combo code.
        long defaultKeyComboCode = mDefaultKeyComboModel.getDefaultKeyComboCode(keyNavigateNext);
        assertEquals(defaultKeyComboCode,
                mDefaultKeyComboModel.getKeyComboCodeForKey(keyNavigateNext));

        // Clear key combo code.
        mDefaultKeyComboModel.clearKeyComboCode(keyNavigateNext);

        Map<String, Long> keyComboCodeMap = mDefaultKeyComboModel.getKeyComboCodeMap();
        assertEquals(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED,
                keyComboCodeMap.get(keyNavigateNext).longValue());
        assertEquals(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED,
                mDefaultKeyComboModel.getKeyComboCodeForKey(keyNavigateNext));

        assertEquals(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED,
                mPref.getLong(PREF_KEY_PREFIX + keyNavigateNext, -1));

        // Create new DefaultKeyComboModel and confirm it's loaded.
        DefaultKeyComboModel newDefaultKeyComboModel = new DefaultKeyComboModel(mContext);
        assertEquals(KeyComboModel.KEY_COMBO_CODE_UNASSIGNED,
                newDefaultKeyComboModel.getKeyComboCodeForKey(keyNavigateNext));
    }

    @Test
    public void isEligibleKeyComboCode() {
        assertEquals(KeyEvent.META_ALT_ON, mDefaultKeyComboModel.getTriggerModifier());

        assertTrue(mDefaultKeyComboModel.isEligibleKeyComboCode(
                KeyComboModel.KEY_COMBO_CODE_UNASSIGNED));
        assertTrue(mDefaultKeyComboModel.isEligibleKeyComboCode(
                KeyComboManager.getKeyComboCode(KeyComboModel.NO_MODIFIER, KeyEvent.KEYCODE_X)));
        assertTrue(mDefaultKeyComboModel.isEligibleKeyComboCode(
                KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_X)));
        assertFalse(mDefaultKeyComboModel.isEligibleKeyComboCode(
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_X)));
        assertFalse(mDefaultKeyComboModel.isEligibleKeyComboCode(
                KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_SHIFT_LEFT)));
        assertFalse(mDefaultKeyComboModel.isEligibleKeyComboCode(
                KeyComboManager.getKeyComboCode(KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON,
                    KeyEvent.KEYCODE_CTRL_LEFT)));
        assertFalse(mDefaultKeyComboModel.isEligibleKeyComboCode(
                KeyComboManager.getKeyComboCode(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON,
                    KeyEvent.KEYCODE_UNKNOWN)));
    }
}
