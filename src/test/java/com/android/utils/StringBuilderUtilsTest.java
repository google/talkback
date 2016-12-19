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

package com.android.utils;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.SpannableStringBuilder;
import com.android.talkback.BuildConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertTrue;

@Config(
        constants = BuildConfig.class,
        sdk = 21)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class StringBuilderUtilsTest {

    @Test
    public void testAppendWithSeparator() {
        final String a = "Phone number:";
        final String b = "555-444-3333";
        final String c = "This is a sentence.";

        final SpannableStringBuilder builder = new SpannableStringBuilder();
        StringBuilderUtils.appendWithSeparator(builder, a, b, c);
        final String result = builder.toString();

        assertTrue("Added non-breaking separator after colon",
                result.contains(":" + StringBuilderUtils.DEFAULT_SEPARATOR + "5"));
        assertTrue("Added breaking separator after phone number",
                result.contains("3" + StringBuilderUtils.DEFAULT_BREAKING_SEPARATOR));
        assertTrue("Did not add separator at end of text", result.endsWith(c));
    }
}
