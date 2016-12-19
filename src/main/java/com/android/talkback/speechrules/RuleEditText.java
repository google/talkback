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

import com.android.talkback.InputModeManager;
import com.android.talkback.KeyComboManager;
import com.android.talkback.R;
import com.android.talkback.SpeechCleanupUtils;
import com.android.talkback.controller.CursorController;
import com.android.utils.Role;
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
        return Role.getRole(node) == Role.ROLE_EDIT_TEXT;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
                               AccessibilityEvent event) {
        final CharSequence text = getText(context, node);
        boolean isCurrentlyEditing = node.isFocused();
        if (hasWindowSupport()) {
            isCurrentlyEditing = isCurrentlyEditing && isInputWindowOnScreen();
        }

        SpannableStringBuilder output = new SpannableStringBuilder();

        CharSequence roleText = Role.getRoleDescriptionOrDefault(context, node);
        StringBuilderUtils.append(output, roleText);

        if (isCurrentlyEditing) {
            CharSequence editing = context.getString(R.string.value_edit_box_editing);
            StringBuilderUtils.append(output, editing);
        }
        if (TalkBackService.getInstance() != null
                && TalkBackService.getInstance().getCursorController().isSelectionModeActive()) {
            StringBuilderUtils.append(output,
                    context.getString(R.string.notification_type_selection_mode_on));
        }

        StringBuilderUtils.append(output, text);

        return output;
    }

    // package visibility for tests
    boolean isInputWindowOnScreen() {
        TalkBackService service = TalkBackService.getInstance();
        if (service == null) {
            return false;
        }

        WindowManager windowManager = new WindowManager(service.isScreenLayoutRTL());
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
        int inputMode = InputModeManager.INPUT_MODE_TOUCH;
        KeyComboManager keyComboManager = null;

        // If the EditText already has the input focus, then we should not tell the user "double-tap
        // to activate", nor "double-tap and hold to long press".
        boolean skipClickHints = false;
        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
            CursorController cursorController = service.getCursorController();
            AccessibilityNodeInfoCompat cursor = cursorController.getCursorOrInputCursor();
            if (cursor != null && cursor.isFocused() && node.equals(cursor)) {
                skipClickHints = true;
            }

            inputMode = service.getInputModeManager().getInputMode();
            keyComboManager = service.getKeyComboManager();
        }

        final CharSequence customClickHint = NodeHintHelper.getHintForInputMode(context, inputMode,
                keyComboManager, context.getString(R.string.keycombo_shortcut_perform_click),
                R.string.template_hint_edit_text, R.string.template_hint_edit_text_keyboard,
                null /* label */);
        final CharSequence customHint = NodeHintHelper.getCustomHintString(context, node,
                customClickHint, null /* customLongClickHint */, skipClickHints, inputMode,
                keyComboManager);

        return customHint;
    }

    /**
     * Inverts the default priorities of text and content description.
     * If the field is a password, returns the content description or "password",
     * as well as the length of the password if it's not empty.
     *
     * @param context current context
     * @param node    to get text from
     * @return A text description of the editable text area.
     */
    private CharSequence getText(Context context, AccessibilityNodeInfoCompat node) {
        final CharSequence text = node.getText();
        final boolean shouldSpeakPasswords = SettingsCompatUtils.SecureCompatUtils.shouldSpeakPasswords(context);

        if (!TextUtils.isEmpty(text) && (!node.isPassword() || shouldSpeakPasswords)) {
            // Text is potentially user input, so we need to make sure we pronounce input that has
            // only symbols.
            return SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(context, text);
        }

        SpannableStringBuilder output = new SpannableStringBuilder();

        final CharSequence contentDescription = node.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            // Less likely, but contentDescription is potentially user input, so we need to make
            // sure we pronounce input that has only symbols.
            StringBuilderUtils.append(output,
                    SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(
                            context,
                            contentDescription));
        } else if (node.isPassword() && !shouldSpeakPasswords) {
            StringBuilderUtils.append(output, context.getString(R.string.value_password));
        }

        if (node.isPassword() && !shouldSpeakPasswords && !TextUtils.isEmpty(text)) {
            // Note: never cleanup password speech because that will mess up the text length.
            StringBuilderUtils.append(output, context.getResources().getQuantityString(
                    R.plurals.template_password_character_count,
                    text.length(),
                    text.length()));
        }

        return output;
    }
}
