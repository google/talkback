/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback.speechrules;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.Role;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.formatter.EventSpeechRule;
import com.android.utils.StringBuilderUtils;

/**
 * Formats speech for SeekBar widgets.
 */
public class RuleSeekBar extends RuleDefault
        implements EventSpeechRule.AccessibilityEventFormatter {
    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        return Role.getRole(node) == Role.ROLE_SEEK_CONTROL;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
                               AccessibilityEvent event) {
        if (node == null || !node.isAccessibilityFocused()) {
            return "";
        }

        final SpannableStringBuilder output = new SpannableStringBuilder();

        final CharSequence text = AccessibilityNodeInfoUtils.getNodeText(node);
        final CharSequence roleText = Role.getRoleDescriptionOrDefault(context, node);

        StringBuilderUtils.append(output, text, roleText);

        // TODO: We need to be getting this information from the node.
        if ((event != null) && (event.getItemCount() > 0)) {
            final int percent = (100 * event.getCurrentItemIndex()) / event.getItemCount();
            final CharSequence formattedPercent =
                    context.getString(R.string.template_percent, percent);

            StringBuilderUtils.appendWithSeparator(output, formattedPercent);
        }

        return output;
    }

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        final AccessibilityNodeInfoCompat source = record.getSource();
        if (source == null) return false;

        CharSequence text = format(context, source, event);
        if (TextUtils.isEmpty(text)) return false;

        utterance.addSpoken(text);
        utterance.getMetadata().putInt(Utterance.KEY_UTTERANCE_GROUP,
                SpeechController.UTTERANCE_GROUP_SEEK_PROGRESS);
        utterance.addSpokenFlag(
                FeedbackItem.FLAG_CLEAR_QUEUED_UTTERANCES_WITH_SAME_UTTERANCE_GROUP);
        utterance.getMetadata().putInt(Utterance.KEY_METADATA_QUEUING,
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE);
        return true;
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        TalkBackService talkBack = TalkBackService.getInstance();
        if (talkBack != null && talkBack.isDeviceTelevision()) {
            // We want to compose the string so that we ensure the wording is consistent.
            return context.getString(R.string.template_hint_seek_control_tv,
                    context.getString(R.string.value_press_select));
        } else {
            return super.getHintText(context, node);
        }
    }
}
