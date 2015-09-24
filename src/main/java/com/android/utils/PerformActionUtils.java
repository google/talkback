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

import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import com.android.utils.LogUtils;

public class PerformActionUtils {

    public static boolean performAction(AccessibilityNodeInfoCompat node, int action) {
        if (node == null) {
            return false;
        }

        logPerformAction(node, action);

        return node.performAction(action);
    }

    public static boolean performAction(AccessibilityNodeInfoCompat node, int action, Bundle args) {
        if (node == null) {
            return false;
        }

        logPerformAction(node, action);

        return node.performAction(action, args);
    }

    private static void logPerformAction(AccessibilityNodeInfoCompat node, int action) {
        LogUtils.log(PerformActionUtils.class, Log.DEBUG, "perform action " + action +
                " for node " + node.toString());
    }
}
