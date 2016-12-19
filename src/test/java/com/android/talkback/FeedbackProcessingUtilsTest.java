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
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import com.android.talkback.FeedbackFragment;
import com.android.talkback.FeedbackItem;
import com.android.talkback.FeedbackProcessingUtils;


/**
 * Tests for FeedbackProcessingUtils
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
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

    @Test
    public void addFormattingCharacteristics_noSpans() {
        final String testString = "The quick brown fox jumps over the lazy dog!";

        FeedbackFragment fragment = new FeedbackFragment(testString, null);
        fragment.addEarcon(R.raw.bold);

        FeedbackItem feedback = new FeedbackItem();
        feedback.addFragment(fragment);

        FeedbackProcessingUtils.addFormattingCharacteristics(feedback);
        assertEquals(1, feedback.getFragments().size());
        assertFragment(feedback, 0, testString, true);
    }

    @Test
    public void addFormattingCharacteristics_preserveExistingFeedback() {
        // Indices:                   4----9
        final String testSring = "The QUICK brown fox jumps over the lazy dog!";

        SpannableString spannableString = new SpannableString(testSring);
        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 4, 9,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        FeedbackFragment fragment = new FeedbackFragment(spannableString, null);
        fragment.addEarcon(R.raw.bold);

        FeedbackItem feedback = new FeedbackItem();
        feedback.addFragment(fragment);

        FeedbackProcessingUtils.addFormattingCharacteristics(feedback);
        assertEquals(3, feedback.getFragments().size());
        assertFragment(feedback, 0, "The ", true /* hasEarcon */); // Existing earcon!
        assertFragment(feedback, 1, "QUICK", true); // New earcon.
        assertFragment(feedback, 2, " brown fox jumps over the lazy dog!", false);
    }

    @Test
    public void addFormattingCharacteristics_styleSpans() {
        // Indices:                   4----9          20--------30
        final String testSring = "The QUICK brown fox JUMPS OVER the lazy dog!";

        SpannableString spannableString = new SpannableString(testSring);
        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 4, 9,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 20, 30,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        FeedbackItem feedback = new FeedbackItem();
        feedback.addFragment(new FeedbackFragment(spannableString, null));

        FeedbackProcessingUtils.addFormattingCharacteristics(feedback);
        assertEquals(5, feedback.getFragments().size());
        assertFragment(feedback, 0, "The ", false /* hasEarcon */);
        assertFragment(feedback, 1, "QUICK", true);
        assertFragment(feedback, 2, " brown fox ", false);
        assertFragment(feedback, 3, "JUMPS OVER", true);
        assertFragment(feedback, 4, " the lazy dog!", false);
    }

    @Test
    public void addFormattingCharacteristics_nonOverlappingSpans() {
        // Indices:                   4----9          20--------30
        final String testSring = "The QUICK brown fox JUMPS OVER the lazy dog!";

        SpannableString spannableString = new SpannableString(testSring);
        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 4, 9,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new URLSpan("https://www.google.com"), 20, 30,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        FeedbackItem feedback = new FeedbackItem();
        feedback.addFragment(new FeedbackFragment(spannableString, null));

        FeedbackProcessingUtils.addFormattingCharacteristics(feedback);
        assertEquals(5, feedback.getFragments().size());
        assertFragment(feedback, 0, "The ", false /* hasEarcon */);
        assertFragment(feedback, 1, "QUICK", true);
        assertFragment(feedback, 2, " brown fox ", false);
        assertFragment(feedback, 3, "JUMPS OVER", true);
        assertFragment(feedback, 4, " the lazy dog!", false);
    }

    @Test
    public void addFormattingCharacteristics_overlappingSpans() {
        // StyleSpan:                 4--------------19
        // URLSpan:                         10------------------30
        final String testSring = "The QUICK BROWN FOX JUMPS OVER the lazy dog!";

        SpannableString spannableString = new SpannableString(testSring);
        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 4, 19,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new URLSpan("https://www.google.com"), 10, 30,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        FeedbackItem feedback = new FeedbackItem();
        feedback.addFragment(new FeedbackFragment(spannableString, null));

        FeedbackProcessingUtils.addFormattingCharacteristics(feedback);
        assertEquals(5, feedback.getFragments().size());
        assertFragment(feedback, 0, "The ", false /* hasEarcon */);
        assertFragment(feedback, 1, "QUICK ", true);
        assertFragment(feedback, 2, "BROWN FOX", true);
        assertFragment(feedback, 3, " JUMPS OVER", true);
        assertFragment(feedback, 4, " the lazy dog!", false);
    }

    @Test
    public void addFormattingCharacteristics_manySpans() {
        // Span 1:                0---4
        // Span 2:                   3---------------19
        // Span 3:                                          26--30
        // Span 4:                                          26------34
        // Span 5:                                              30-------39
        // BLOCKS:                |--||--------------|......|---|---|----|....
        final String testSring = "The QUICK BROWN FOX JUMPS OVER the lazy dog!";

        SpannableString spannableString = new SpannableString(testSring);
        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, 4,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.BOLD), 3, 19,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new URLSpan("https://www.google.com"), 26, 30,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 26, 34,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 30, 39,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        FeedbackItem feedback = new FeedbackItem();
        feedback.addFragment(new FeedbackFragment(spannableString, null));

        FeedbackProcessingUtils.addFormattingCharacteristics(feedback);
        assertEquals(8, feedback.getFragments().size());
        assertFragment(feedback, 0, "The", true /* hasEarcon */);
        assertFragment(feedback, 1, " ", true);
        assertFragment(feedback, 2, "QUICK BROWN FOX", true);
        assertFragment(feedback, 3, " JUMPS ", false);
        assertFragment(feedback, 4, "OVER", true);
        assertFragment(feedback, 5, " the", true);
        assertFragment(feedback, 6, " lazy", true);
        assertFragment(feedback, 7, " dog!", false);
    }

    @Test
    public void addFormattingCharacteristics_extraneousSpans() {
        // Span 1:                0---4
        // Span 2 (extra):           3---------------19
        // Span 3 (extra):                                  26--30
        // Span 4 (extra):                                  26------34
        // Span 5:                                              30-------39
        // BLOCKS:                |---|.........................|--------|....
        final String testSring = "The QUICK BROWN FOX JUMPS OVER the lazy dog!";

        SpannableString spannableString = new SpannableString(testSring);
        spannableString.setSpan(new StyleSpan(Typeface.ITALIC), 0, 4,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new FakeSpan(), 3, 19,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new FakeSpan(), 26, 30,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new FakeSpan(), 26, 34,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 30, 39,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        FeedbackItem feedback = new FeedbackItem();
        feedback.addFragment(new FeedbackFragment(spannableString, null));

        FeedbackProcessingUtils.addFormattingCharacteristics(feedback);
        assertEquals(4, feedback.getFragments().size());
        assertFragment(feedback, 0, "The ", true /* hasEarcon */);
        assertFragment(feedback, 1, "QUICK BROWN FOX JUMPS OVER", false);
        assertFragment(feedback, 2, " the lazy", true);
        assertFragment(feedback, 3, " dog!", false);
    }

    private void assertFragment(FeedbackItem feedback, int fragmentIndex, String text,
            boolean hasEarcon) {
        FeedbackFragment fragment = feedback.getFragments().get(fragmentIndex);
        assertEquals(text, fragment.getText().toString());
        if (hasEarcon) {
            assertTrue(0 != fragment.getEarcons().size());
        } else {
            assertTrue(0 == fragment.getEarcons().size());
        }
    }

    private static class FakeSpan extends CharacterStyle {
        public FakeSpan() {}

        @Override
        public void updateDrawState(TextPaint tp) {}
    }

}

