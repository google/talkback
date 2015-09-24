/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.content.Context;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Leaf node in the Option Scanning tree that perform actions
 */
public abstract class OptionScanActionNode implements ContextMenuItem {
    private OptionScanSelectionNode mParent;

    /**
     * Get the label for the action
     *
     * @param context The current context
     * @return A localized label for the action
     */
    public abstract CharSequence getActionLabel(Context context);

    @Override
    public List<CharSequence> getActionLabels(Context context) {
        CharSequence actionLabel = getActionLabel(context);
        if (TextUtils.isEmpty(actionLabel)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(getActionLabel(context)));
    }

    @Override
    public void recycle() {
    }

    @Override
    public void performAction() {
    }

    @Override
    public OptionScanSelectionNode getParent() {
        return mParent;
    }

    @Override
    public void setParent(OptionScanSelectionNode parent) {
        mParent = parent;
    }
}
