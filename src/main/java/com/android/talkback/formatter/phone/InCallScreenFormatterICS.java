/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.talkback.formatter.phone;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.formatter.EventSpeechRule;

import java.util.List;

/**
 * Formatter that returns an utterance to announce the incoming call screen.
 *
 * NOTE: This is required for pre-GB compatibility.
 */
public final class InCallScreenFormatterICS implements EventSpeechRule.AccessibilityEventFormatter {
    private static final int MIN_EVENT_TEXT_COUNT = 6;

    private static final int INDEX_UPPER_TITLE = 1;
    private static final int INDEX_PHOTO = 2;
    private static final int INDEX_NAME = 3;
    private static final int INDEX_LABEL = 5;

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final List<CharSequence> eventText = event.getText();
        if (eventText.size() < MIN_EVENT_TEXT_COUNT) {
            return false;
        }

        final CharSequence title = eventText.get(INDEX_UPPER_TITLE);
        if (!TextUtils.isEmpty(title)) {
            utterance.addSpoken(title);
        }

        final CharSequence name = eventText.get(INDEX_NAME);
        if (TextUtils.isEmpty(name)) {
            // Return immediately if there is no contact name.
            return !utterance.getSpoken().isEmpty();
        }

        utterance.addSpoken(name);

        // If the contact name is not a phone number, add the label and photo.
        if (!isPhoneNumber(name.toString())) {
            final CharSequence label = eventText.get(INDEX_LABEL);
            if (label != null) {
                utterance.addSpoken(label);
            }

            final CharSequence photo = eventText.get(INDEX_PHOTO);
            if (photo != null) {
                utterance.addSpoken(photo);
            }
        }

        return !utterance.getSpoken().isEmpty();
    }

    /**
     * Returns if a <code>value</code> is a phone number.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean isPhoneNumber(String value) {
        final String valueNoDashes = value.replaceAll("-", "");

        try {
            Long.parseLong(valueNoDashes);
            return true;
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }
}