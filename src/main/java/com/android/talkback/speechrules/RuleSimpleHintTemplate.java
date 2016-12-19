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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.InputModeManager;
import com.android.talkback.KeyComboManager;
import com.android.talkback.R;

import com.android.utils.Role;

import com.google.android.marvin.talkback.TalkBackService;

public class RuleSimpleHintTemplate extends RuleDefault implements NodeHintRule {
    private final int mTargetRole;
    private final int mHintTouchResId;
    private final int mHintKeyboardResId;

    public RuleSimpleHintTemplate(@Role.RoleName int targetRole, int hintTouchResId,
            int hintKeyboardResId) {
        super();
        mTargetRole = targetRole;
        mHintTouchResId = hintTouchResId;
        mHintKeyboardResId = hintKeyboardResId;
    }

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        return mTargetRole != Role.ROLE_NONE && Role.getRole(node) == mTargetRole;
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        KeyComboManager keyComboManager = null;
        int inputMode = InputModeManager.INPUT_MODE_TOUCH;

        TalkBackService talkBackService = TalkBackService.getInstance();
        if (talkBackService != null) {
            keyComboManager = talkBackService.getKeyComboManager();
            inputMode = talkBackService.getInputModeManager().getInputMode();
        }

        return NodeHintHelper.getHintForInputMode(context, inputMode, keyComboManager,
                context.getString(R.string.keycombo_shortcut_perform_click), mHintTouchResId,
                mHintKeyboardResId, null /* label */);
    }
}
