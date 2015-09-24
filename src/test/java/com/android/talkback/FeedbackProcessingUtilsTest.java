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

import android.annotation.TargetApi;
import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;


/**
 * Tests for FeedbackProcessingUtils
 */
@Config(emulateSdk = 18)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricTestRunner.class)
public class FeedbackProcessingUtilsTest {

    @Test
    public void splitText_shouldNotAlterOrigin() {
        int numberOfFragment = 2;
        int size = numberOfFragment * FeedbackProcessingUtils.maxUtteranceLength;
        String originText = "";
        for (int i = 0; i < size/2; i++) {
            originText += "i ";
        }
        FeedbackFragment fragment = new FeedbackFragment(originText, null);
        FeedbackItem item = new FeedbackItem();
        item.addFragment(fragment);
        FeedbackProcessingUtils.splitLongText(item);
        StringBuilder processedBuffer = new StringBuilder();
        assertTrue("Expended fragment size should larger than original ",
                 item.getFragments().size() > numberOfFragment);
        for (FeedbackFragment processedFragment : item.getFragments()) {
            assertTrue("Length is smaller than max length", processedFragment.getText().length()
                    < FeedbackProcessingUtils.maxUtteranceLength);
            processedBuffer.append(processedFragment.getText());

        }

        assertEquals(processedBuffer.toString().length(), originText.length());
        assertEquals("Processed content should be the same as original one",
                processedBuffer.toString(), originText);
    }
}

