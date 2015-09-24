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
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.StringBuilderUtils;


class RuleSimpleTemplate extends RuleDefault {
    private final String mTargetClassName;
    private final Class<?> mTargetClass;
    private final int mResId;

    public RuleSimpleTemplate(Class<?> targetClass, int resId) {
        mTargetClassName = null;
        mTargetClass = targetClass;
        mResId = resId;
    }

    RuleSimpleTemplate(String targetClassName, int resId) {
        mTargetClassName = targetClassName;
        mTargetClass = null;
        mResId = resId;
    }

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        if (mTargetClass != null) {
            return AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, mTargetClass);
        }

        return mTargetClassName != null
                && AccessibilityNodeInfoUtils.nodeMatchesClassByName(node, mTargetClassName);

    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node,
                               AccessibilityEvent event) {
        final CharSequence text = super.format(context, node, event);
        String formattedText = context.getString(mResId, text);
        return StringBuilderUtils.createSpannableFromTextWithTemplate(formattedText, text);
    }
}
