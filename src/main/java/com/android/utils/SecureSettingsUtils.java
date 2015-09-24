/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

/**
 * Utility class to simplify access to {@link android.provider.Settings.Secure}.
 */
public class SecureSettingsUtils {
    /**
     * Returns whether a specific accessibility service is enabled.
     *
     * @param context The parent context.
     * @param packageName The package name of the accessibility service.
     * @return {@code true} of the service is enabled.
     */
    public static boolean isAccessibilityServiceEnabled(Context context, String packageName) {
        final ContentResolver resolver = context.getContentResolver();
        final String enabledServices = Settings.Secure.getString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        return enabledServices.contains(packageName);
    }
}
