/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback.formatter;

import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.utils.StringBuilderUtils;

import java.util.Collections;
import java.util.List;

/**
 * Formatter for touch exploration events from the System UI.
 */
public class TouchExplorationSystemUiFormatter
        implements EventSpeechRule.AccessibilityEventFormatter {
    /** The most recently spoken utterance. Used to eliminate duplicates. */
    private final SpannableStringBuilder mLastUtteranceText = new SpannableStringBuilder();

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final SpannableStringBuilder recordText = new SpannableStringBuilder();
        final List<CharSequence> entries = AccessibilityEventCompat.getRecord(event, 0).getText();

        // Reverse the entries so that time is read aloud first.
        Collections.reverse(entries);

        for (final CharSequence entry : entries) {
            StringBuilderUtils.appendWithSeparator(recordText, entry);
        }

        // Don't populate with empty text. This should never happen!
        if (TextUtils.isEmpty(recordText)) return false;

        // Don't speak the same utterance twice.
        if (TextUtils.equals(mLastUtteranceText, recordText)) return false;

        utterance.addSpoken(recordText);
        utterance.addHaptic(R.array.view_hovered_pattern);
        utterance.addAuditory(R.raw.focus);

        mLastUtteranceText.clear();
        mLastUtteranceText.append(recordText);

        return true;
    }
}
