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
import com.android.talkback.R;
import com.android.utils.Role;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.StringBuilderUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.Label;

/**
 * Processes image widgets.
 */
public class RuleNonTextViews extends RuleDefault {

    /**
     * Lazily initialized manager for handling custom label substitutions in the
     * rule processor
     */
    private CustomLabelManager mLabelManager = null;

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        initLabelManagerIfNeeded();
        int role = Role.getRole(node);
        return role == Role.ROLE_IMAGE || role == Role.ROLE_IMAGE_BUTTON;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
                               AccessibilityEvent event) {
        final CharSequence text = AccessibilityNodeInfoUtils.getNodeText(node);
        CharSequence labelText = null;
        if (!TextUtils.isEmpty(text)) {
            labelText = text;
        } else {
            if (mLabelManager != null) {
                // Check to see if a custom label exists for the unlabeled control.
                Label label = mLabelManager.getLabelForViewIdFromCache(
                        node.getViewIdResourceName());
                if (label != null) {
                    labelText = label.getText();
                }
            }
        }

        // If the node's role is ROLE_IMAGE and the node is selectable, there's a non-trivial chance
        // that the node acts like a button (but we can't be sure). In this case, it is safest to
        // not append any role text to avoid confusing the user.
        CharSequence roleText;
        if (Role.getRole(node) == Role.ROLE_IMAGE && AccessibilityNodeInfoUtils.supportsAction(
                node, AccessibilityNodeInfoCompat.ACTION_SELECT)) {
            roleText = node.getRoleDescription(); // But don't fall back to default text.
        } else {
            roleText = Role.getRoleDescriptionOrDefault(context, node);
        }

        CharSequence unlabelledState;
        if (labelText == null) {
            unlabelledState = context.getString(R.string.value_unlabelled);
        } else {
            unlabelledState = null;
        }

        SpannableStringBuilder output = new SpannableStringBuilder();
        StringBuilderUtils.append(output, labelText, roleText);
        StringBuilderUtils.append(output, unlabelledState);

        return output;
    }

    /**
     * Retrieves a handle to the correct {@link CustomLabelManager} if the rule
     * requires it.
     */
    private void initLabelManagerIfNeeded() {
        if (Build.VERSION.SDK_INT >= CustomLabelManager.MIN_API_LEVEL) {
            final TalkBackService service = TalkBackService.getInstance();
            if (service != null) {
                mLabelManager = service.getLabelManager();
            }
        }
    }
}
