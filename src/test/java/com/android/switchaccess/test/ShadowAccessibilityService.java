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

package com.android.switchaccess.test;

import android.accessibilityservice.AccessibilityService;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowService;

import java.util.ArrayList;
import java.util.List;

/**
 * Shadow of AccessibilityService that saves global actions to a list.
 */
@Implements(AccessibilityService.class)
public class ShadowAccessibilityService extends ShadowService {

    private final List<Integer> mGlobalActionsPerformed = new ArrayList<>();

    @Implementation
    public final boolean performGlobalAction(int action) {
        mGlobalActionsPerformed.add(action);
        return true;
    }

    public List<Integer> getGlobalActionsPerformed() {
        return mGlobalActionsPerformed;
    }
}
