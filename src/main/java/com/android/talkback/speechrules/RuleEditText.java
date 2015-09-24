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
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.EditText;

import com.android.talkback.R;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.StringBuilderUtils;
import com.android.utils.WindowManager;
import com.android.utils.compat.provider.SettingsCompatUtils;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.List;

/**
 * Processes editable text fields.
 */
class RuleEditText implements NodeSpeechRule, NodeHintRule {
    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, EditText.class);
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final CharSequence text = getText(context, node);
        String formattedText;
        boolean isCurrentlyEditing = node.isFocused();
        if (hasWindowSupport()) {
            isCurrentlyEditing = isCurrentlyEditing && isInputWindowOnScreen();
        }

        if (isCurrentlyEditing) {
            formattedText = context.getString(R.string.template_edit_box_current, text);
        } else {
            formattedText = context.getString(R.string.template_edit_box, text);
        }

        return StringBuilderUtils.createSpannableFromTextWithTemplate(formattedText, text);
    }

    // package visibility for tests
    boolean isInputWindowOnScreen() {
        TalkBackService service = TalkBackService.getInstance();
        if (service == null) {
            return false;
        }

        WindowManager windowManager = new WindowManager();
        List<AccessibilityWindowInfo> windows = service.getWindows();
        windowManager.setWindows(windows);
        return windowManager.isInputWindowOnScreen();
    }

    // package visibility for tests
    boolean hasWindowSupport() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        // Disabled items don't have any hint text.
        if (!node.isEnabled()) {
            return context.getString(R.string.value_disabled);
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();
        StringBuilderUtils.appendWithSeparator(builder,
                NodeHintHelper.getHintString(context, R.string.template_hint_edit_text));
        final CharSequence defaultHint = NodeHintHelper.getDefaultHintString(context, node);

        if(!TextUtils.isEmpty(defaultHint)) {
            StringBuilderUtils.appendWithSeparator(builder, defaultHint);
        }

        return builder;
    }

    /**
     * Inverts the default priorities of text and content description. If the
     * field is a password, only returns the content description or "password".
     *
     * @param context current context
     * @param node to get text from
     * @return A text description of the editable text area.
     */
    private CharSequence getText(Context context, AccessibilityNodeInfoCompat node) {
        final CharSequence text = node.getText();
        final boolean shouldSpeakPasswords = SettingsCompatUtils.SecureCompatUtils.shouldSpeakPasswords(context);

        if (!TextUtils.isEmpty(text) && (!node.isPassword() || shouldSpeakPasswords)) {
            return text;
        }

        final CharSequence contentDescription = node.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            return contentDescription;
        }

        if (node.isPassword() && !shouldSpeakPasswords) {
            return context.getString(R.string.value_password);
        }

        return "";
    }
}
