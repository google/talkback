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
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import com.android.talkback.R;
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
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ImageView.class);
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
                               AccessibilityEvent event) {
        final CharSequence text = super.format(context, node, event);
        final boolean isClickable = AccessibilityNodeInfoUtils.isClickable(node);
        final boolean hasLabel = !TextUtils.isEmpty(text);

        if (hasLabel) {
            // We speak labeled images as buttons if acitonable, or simply speak
            // their text if non-actionable.
            if (isClickable) {
                String resultText = context.getString(R.string.template_button, text);
                return StringBuilderUtils.createSpannableFromTextWithTemplate(resultText, text);
            } else {
                return text;
            }
        } else {
            if (mLabelManager != null) {
                // Check to see if a custom label exists for the unlabeled control.
                Label label = mLabelManager.getLabelForViewIdFromCache(
                        node.getViewIdResourceName());
                if (label != null) {
                    final CharSequence labelText = label.getText();
                    if (isClickable) {
                        return context.getString(R.string.template_button, labelText);
                    } else {
                        return labelText;
                    }
                }
            }

            // We provide as much information about the control as possible in
            // the case it is unlabeled.
            final int nodeInt = Math.abs(node.hashCode() % 100);
            if (isClickable) {
                return context.getString(R.string.template_unlabeled_button, nodeInt);
            } else {
                return context.getString(R.string.template_unlabeled_image_view, nodeInt);
            }
        }
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
