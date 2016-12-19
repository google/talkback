/*
 * Copyright (C) 2012 The Android Open Source Project
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
 */
public final class InCallScreenFormatterJB implements EventSpeechRule.AccessibilityEventFormatter {
    private static final int MIN_EVENT_TEXT_COUNT = 3;

    private static final int INDEX_UPPER_TITLE = 1;
    private static final int INDEX_SUBTITILE = 2;

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

        final CharSequence subtitle = eventText.get(INDEX_SUBTITILE);
        if (!TextUtils.isEmpty(subtitle)) {
            utterance.addSpoken(subtitle);
        }

        return !utterance.getSpoken().isEmpty();
    }
}
