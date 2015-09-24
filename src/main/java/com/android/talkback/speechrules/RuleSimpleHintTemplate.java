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

public class RuleSimpleHintTemplate extends RuleSimpleTemplate implements NodeHintRule {
    private final int mHintResId;

    public RuleSimpleHintTemplate(Class<?> targetClass, int resId, int hintResId) {
        super(targetClass, resId);

        mHintResId = hintResId;
    }

    public RuleSimpleHintTemplate(String targetClass, int resId, int hintResId) {
        super(targetClass, resId);

        mHintResId = hintResId;
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        return NodeHintHelper.getHintString(context, mHintResId);
    }
}
