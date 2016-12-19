/*
 * Copyright (C) 2016 Google Inc.
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

import android.content.Context;
import android.text.TextUtils;

import com.android.talkback.SpeechCleanupUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricGradleTestRunner.class)
public class SpeechCleanupUtilsTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application.getApplicationContext();
    }

    @Test
    public void testCleanUpSpace() {
        assertEquals("Space", SpeechCleanupUtils.cleanUp(mContext, " "));
    }

    @Test
    public void testCleanUpSpace_withWhitespace() {
        assertEquals(0, TextUtils.getTrimmedLength("      "));
        assertEquals("Space", SpeechCleanupUtils.cleanUp(mContext, "      "));
    }

    @Test
    public void testCleanUpSquare() {
        assertEquals("Black square", SpeechCleanupUtils.cleanUp(mContext, "\u25a0"));
    }

    @Test
    public void testCleanUpSquare_withWhitespace() {
        assertEquals("Black square", SpeechCleanupUtils.cleanUp(mContext, "  \u25a0 "));
    }

    @Test
    public void testCleanUpEmpty() {
        assertEquals("", SpeechCleanupUtils.cleanUp(mContext, ""));
    }

}