/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.text.Spannable;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.R;
import com.android.talkback.Utterance;
import com.android.utils.StringBuilderUtils;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.List;

/**
 * Formatter that will speak the dialog title or a message if the dialog does not have a title
 */
public final class DialogFormatter implements EventSpeechRule.AccessibilityEventFormatter {

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        List<CharSequence> texts = event.getText();
        if (texts != null && texts.size() > 0) {
            CharSequence text = texts.get(0);
            String template = context.getString(R.string.template_alert_dialog_template, text);
            Spannable message = StringBuilderUtils.createSpannableFromTextWithTemplate(template,
                    text);
            utterance.addSpoken(message);
            return true;
        }

        return false;
    }
}
