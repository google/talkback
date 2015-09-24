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
import android.graphics.Rect;

import java.util.Collections;
import java.util.Set;

/**
 * Leaf node of Option Scanning tree that does nothing and just allows focus to be cleared.
 */
public class ClearFocusNode extends OptionScanActionNode {
    @Override
    public CharSequence getActionLabel(Context context) {
        return null;
    }

    @Override
    public Set<Rect> getRectsForNodeHighlight() {
        return Collections.emptySet();
    }

    @Override
    public boolean equals(Object other) {

        return other instanceof ClearFocusNode;
    }
}
