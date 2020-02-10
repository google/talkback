/*
 * Copyright (C) 2014 Google Inc.
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

package com.googlecode.eyesfree.utils;

import android.content.Context;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;

/**
 * Provides a series of utilities for interacting with AccessibilityNodeInfo
 * objects. NOTE: This class only recycles unused nodes that were collected
 * internally. Any node passed into or returned from a public method is retained
 * and TalkBack should recycle it when appropriate.
 */
public class AccessibilityNodeInfoUtils {
    /** Whether isVisibleToUser() is supported by the current SDK. */
    private static final boolean SUPPORTS_VISIBILITY = (Build.VERSION.SDK_INT >= 16);

    /**
     * Determines if the generating class of an
     * {@link AccessibilityNodeInfoCompat} matches a given {@link Class} by
     * type.
     *
     * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by
     *            the accessibility framework.
     * @param referenceClass A {@link Class} to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object
     *         matches the {@link Class} by type or inherited type,
     *         {@code false} otherwise.
     */
    public static boolean nodeMatchesClassByType(
            Context context, AccessibilityNodeInfoCompat node, Class<?> referenceClass) {
        if ((node == null) || (referenceClass == null)) {
            return false;
        }

        // Attempt to take a shortcut.
        final CharSequence nodeClassName = node.getClassName();
        if (TextUtils.equals(nodeClassName, referenceClass.getName())) {
            return true;
        }

        final ClassLoadingManager loader = ClassLoadingManager.getInstance();
        final CharSequence appPackage = node.getPackageName();
        return loader.checkInstanceOf(context, nodeClassName, appPackage, referenceClass);
    }

    /**
     * Helper method that returns {@code true} if the specified node is visible
     * to the user or if the current SDK doesn't support checking visibility.
     */
    public static  boolean isVisibleOrLegacy(AccessibilityNodeInfoCompat node) {
        return (!AccessibilityNodeInfoUtils.SUPPORTS_VISIBILITY || node.isVisibleToUser());
    }
}
