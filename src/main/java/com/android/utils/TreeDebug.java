/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashSet;

/**
 * Util class to help debug Node trees.
 */
public class TreeDebug {

    /**
     * Logs the tree using the input node as the root.
     */
    public static void logNodeTree(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return;
        }

        HashSet<AccessibilityNodeInfoCompat> seen = new HashSet<>();
        logNodeTree(AccessibilityNodeInfoCompat.obtain(node), "", seen);
        for (AccessibilityNodeInfoCompat n : seen) {
            n.recycle();
        }
    }

    private static void logNodeTree(AccessibilityNodeInfoCompat node, String indent,
                HashSet<AccessibilityNodeInfoCompat> seen) {
        if (!seen.add(node)) {
            LogUtils.log(TreeDebug.class, Log.VERBOSE, "Cycle: %d", node.hashCode());
            return;
        }

        // Include the hash code as a "poor man's" id, knowing that it
        // might not always be unique.
        LogUtils.log(TreeDebug.class, Log.VERBOSE, "%s(%d)%s", indent, node.hashCode(),
                nodeDebugDescription(node));

        indent += "  ";
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            AccessibilityNodeInfoCompat child = node.getChild(i);
            if (child == null) {
                LogUtils.log(TreeDebug.class, Log.VERBOSE, "%sCouldn't get child %d", indent, i);
                continue;
            }

            logNodeTree(child, indent, seen);
        }
    }

    private static void appendSimpleName(StringBuilder sb, CharSequence fullName) {
        int dotIndex = TextUtils.lastIndexOf(fullName, '.');
        if (dotIndex < 0) {
            dotIndex = 0;
        }

        sb.append(fullName, dotIndex, fullName.length());
    }

    /**
     * Gets a description of the properties of a node.
     */
    private static CharSequence nodeDebugDescription(AccessibilityNodeInfoCompat node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getWindowId());

        if (node.getClassName() != null) {
            appendSimpleName(sb, node.getClassName());
        } else {
            sb.append("??");
        }

        if (!node.isVisibleToUser()) {
            sb.append(":invisible");
        }

        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        sb.append(":");
        sb.append("(").append(rect.left).append(", ").append(rect.top).append(" - ")
                .append(rect.right).append(", ").append(rect.bottom).append(")");

        if (node.getText() != null) {
            sb.append(":");
            sb.append(node.getText().toString().trim());
        }

        if (node.getContentDescription() != null) {
            sb.append(":");
            sb.append(node.getContentDescription().toString().trim());
        }

        int actions = node.getActions();
        if (actions != 0) {
            sb.append(":");
            if ((actions & AccessibilityNodeInfoCompat.ACTION_FOCUS) != 0) {
                sb.append("F");
            }
            if ((actions & AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) != 0) {
                sb.append("A");
            }
            if ((actions & AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) != 0) {
                sb.append("a");
            }
            if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) != 0) {
                sb.append("-");
            }
            if ((actions & AccessibilityNodeInfoCompat.ACTION_CLICK) != 0) {
                sb.append("C");
            }
            if ((actions & AccessibilityNodeInfoCompat.ACTION_LONG_CLICK) != 0) {
                sb.append("L");
            }
            if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) != 0) {
                sb.append("+");
            }
        }

        if (node.isCheckable()) {
            sb.append(":");
            if (node.isChecked()) {
                sb.append("(X)");
            } else {
                sb.append("( )");
            }
        }

        if (node.isFocusable()) {
            sb.append(":focusable");
        }

        if (node.isFocused()) {
            sb.append(":focused");
        }

        if (node.isSelected()) {
            sb.append(":selected");
        }

        if (node.isClickable()) {
            sb.append(":clickable");
        }

        if (node.isLongClickable()) {
            sb.append(":longClickable");
        }

        if (node.isAccessibilityFocused()) {
            sb.append(":accessibilityFocused");
        }

        if (!node.isEnabled()) {
            sb.append(":disabled");
        }

        return sb.toString();
    }
}

