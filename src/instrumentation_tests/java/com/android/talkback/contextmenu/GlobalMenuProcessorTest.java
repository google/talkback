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

package com.android.talkback.contextmenu;

import android.content.SharedPreferences;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.talkback.R;
import com.android.talkback.contextmenu.GlobalMenuProcessor;
import com.android.talkback.contextmenu.ListMenu;
import com.android.talkback.contextmenu.ListMenuItem;
import com.android.utils.SharedPreferencesUtils;

import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.testing.TalkBackInstrumentationTestCase;

public class GlobalMenuProcessorTest extends TalkBackInstrumentationTestCase {
    private TalkBackService mTalkBack;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTalkBack = getService();
        assertNotNull("Obtained TalkBack instance", mTalkBack);
    }

    @Override
    protected void tearDown() throws Exception {
        // Disable dimming in case it was turned on.
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        SharedPreferencesUtils.putBooleanPref(prefs, mTalkBack.getResources(),
                R.string.pref_dim_when_talkback_enabled_key, false);

        super.tearDown();
    }

    @MediumTest
    public void testScreenDimmingIsOff_shouldShowEnableDimmingOption() {
        ListMenu menu = new ListMenu(mTalkBack);
        new MenuInflater(mTalkBack).inflate(R.menu.global_context_menu, menu);

        GlobalMenuProcessor globalProcessor = new GlobalMenuProcessor(mTalkBack);
        globalProcessor.prepareMenu(menu);

        assertMenuItemHasTitle(menu, R.id.enable_dimming, R.string.shortcut_enable_dimming);
    }

    @MediumTest
    public void testScreenDimmingIsOn_shouldShowDisableDimmingOption() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mTalkBack);
        SharedPreferencesUtils.putBooleanPref(prefs, mTalkBack.getResources(),
                R.string.pref_dim_when_talkback_enabled_key, true);

        ListMenu menu = new ListMenu(mTalkBack);
        new MenuInflater(mTalkBack).inflate(R.menu.global_context_menu, menu);

        GlobalMenuProcessor globalProcessor = new GlobalMenuProcessor(mTalkBack);
        globalProcessor.prepareMenu(menu);

        assertMenuItemHasTitle(menu, R.id.disable_dimming, R.string.shortcut_disable_dimming);
    }

    private void assertMenuItemHasTitle(Menu menu, int menuItemId, int titleStringResource) {
        MenuItem item = menu.findItem(menuItemId);
        assertNotNull(item);
        assertEquals(mTalkBack.getString(titleStringResource), item.getTitle());
    }

}