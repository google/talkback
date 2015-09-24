/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.utils;

import android.content.Context;
import android.content.res.Resources;

/**
 * Utility methods for retrieving information about the dimensions of the screen
 */
public class ScreenDimensionUtils {
    /**
     * Get the current nav bar height
     * @param context current context
     * @return The current nav bar height in dps
     */
    public static int getNavBarHeight(final Context context) {
        int navBarHeight;
        try {
            /* TODO(PW) Identify or request robust APIs to obtain the nav bar height */
            final Resources resources = context.getResources();
            navBarHeight = resources.getDimensionPixelSize(
                    resources.getIdentifier("navigation_bar_height", "dimen", "android"));
        } catch (Resources.NotFoundException e) {
            navBarHeight = 0;
        }

        return navBarHeight;
    }

    /**
     * Get the current status bar height
     * @param context current context
     * @return The current status bar height in dps
     */
    public static int getStatusBarHeight(final Context context) {
        int statusBarHeight;
        try {
            /* TODO(PW) Identify or request robust APIs to obtain the status bar height */
            final Resources resources = context.getResources();
            statusBarHeight = resources.getDimensionPixelSize(
                    resources.getIdentifier("status_bar_height", "dimen", "android"));
        } catch (Resources.NotFoundException e) {
            statusBarHeight = 0;
        }

        return statusBarHeight;
    }
}

