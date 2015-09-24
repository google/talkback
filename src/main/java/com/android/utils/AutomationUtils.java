/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import com.android.utils.compat.CompatUtils;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Contains static UI automation helper methods.
 */
public class AutomationUtils {
    /**
     * Returns the internal Android string corresponding to a specific resource name.
     *
     * @param context The parent context.
     * @param resName The short name of the resource, e.g. "ok".
     * @return An internal string, or {@code null}.
     */
    public static String getInternalString(Context context, String resName) {
        final Class<?> internalRes = CompatUtils.getClass("com.android.internal.R$string");
        final Field resField = CompatUtils.getField(internalRes, resName);
        final int resId = (Integer) CompatUtils.getFieldValue(null, 0, resField);
        if (resId <= 0) {
            return null;
        }

        return context.getString(resId);
    }

    /**
     * Returns the string corresponding to a resource name in a specific package.
     *
     * @param context The parent context.
     * @param packageName The application's package name, e.g. "com.android.settings".
     * @param resName The short name of the resource, e.g. "ok".
     * @return A package-specific string, or {@code null}.
     */
    public static String getPackageString(Context context, String packageName, String resName) {
        final PackageManager pm = context.getPackageManager();

        final Resources packageRes;
        try {
            packageRes = pm.getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        final int resId = packageRes.getIdentifier(resName, "string", packageName);
        if (resId <= 0) {
            return null;
        }

        final String result;
        try {
            result = packageRes.getString(resId);
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
            return null;
        }

        return result;
    }

    /**
     * Attempts to perform an accessibility action on a view that is an
     * instance of the class {@code className}, the text or content
     * description {@code text}, and exists under a parent view {@code root}.
     *
     * @param root The parent view's node.
     * @param className The class name to match.
     * @param text The text or content description to match.
     * @param action The action to perform.
     * @param arguments The action arguments.
     * @return {@code true} if the action was performed.
     */
    public static boolean performActionOnView(AccessibilityNodeInfoCompat root,
            CharSequence className, String text, int action, Bundle arguments) {
        final List<AccessibilityNodeInfoCompat> matches = findViewsWithText(root, className, text);
        if (matches.size() != 1) {
            return false;
        }

        final AccessibilityNodeInfoCompat node = matches.get(0);
        return PerformActionUtils.performAction(node, action, arguments);
    }

    /**
     * Returns a list of nodes under the {@code root} node that match the
     * class specified by {@code className} and exactly match the text or
     * content description specified by {@code text}.
     */
    private static List<AccessibilityNodeInfoCompat> findViewsWithText(
            AccessibilityNodeInfoCompat root, CharSequence className, String text) {
        final List<AccessibilityNodeInfoCompat> nodes =
                root.findAccessibilityNodeInfosByText(text);
        final List<AccessibilityNodeInfoCompat> results = new LinkedList<>();

        for (AccessibilityNodeInfoCompat node : nodes) {
            if (nodeMatchesFilter(node, className, text)) {
                results.add(node);
            }
        }

        return Collections.unmodifiableList(results);
    }

    /**
     * Returns whether a node matches the class specified by
     * {@code className} and exactly match the text or content description
     * specified by {@code text}.
     */
    private static boolean nodeMatchesFilter(AccessibilityNodeInfoCompat node,
            CharSequence referenceClassName, String findText) {

        return ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClassName)
                && (TextUtils.equals(findText, node.getText())
                        || TextUtils.equals(findText, node.getContentDescription()));

    }
}
