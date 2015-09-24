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

import android.accessibilityservice.AccessibilityService;

import com.android.talkback.BuildConfig;
import com.googlecode.eyesfree.testing.BaseAccessibilityInstrumentationTestCase;

/**
 * Test case for supporting end-to-end tests of SwitchControlService
 */
public class SwitchAccessInstrumentationTestCase extends BaseAccessibilityInstrumentationTestCase {
    private static final String TARGET_PACKAGE = BuildConfig.APPLICATION_ID;
    private static final String TARGET_CLASS = "com.android.switchaccess.SwitchAccessService";

    private SwitchAccessService mService;

    @Override
    protected AccessibilityService getService() {
        mService = SwitchAccessService.getInstance();
        return mService;
    }

    @Override
    protected void enableTargetService() {
        assertServiceIsInstalled(TARGET_PACKAGE, TARGET_CLASS);

        enableService(TARGET_PACKAGE, TARGET_CLASS, false /* usesExploreByTouch */);
    }

    @Override
    protected void connectServiceListener() {
        // Not using listeners
    }

    @Override
    protected void disconnectServiceListener() {
        // Not using listeners
    }

 }
