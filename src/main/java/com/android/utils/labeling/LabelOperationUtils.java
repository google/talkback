/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.utils.labeling;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Convenience utilities and constants for invoking operations related to label
 * management.
 */
public class LabelOperationUtils {

    // Intent values for launching LabelDialogActivity
    public static final String
            ACTION_ADD_LABEL = "com.google.android.marvin.talkback.labeling.ADD_LABEL";
    public static final String
            ACTION_EDIT_LABEL = "com.google.android.marvin.talkback.labeling.EDIT_LABEL";
    public static final String
            ACTION_REMOVE_LABEL = "com.google.android.marvin.talkback.labeling.REMOVE_LABEL";
    public static final String EXTRA_STRING_RESOURCE_NAME = "EXTRA_STRING_RESOURCE_NAME";
    public static final String EXTRA_RECT_VIEW_BOUNDS = "EXTRA_RECT_VIEW_BOUNDS";
    public static final String EXTRA_LONG_LABEL_ID = "EXTRA_LONG_LABEL_ID";

    // Intent values for broadcasts to CustomLabelManager
    public static final
            String ACTION_REFRESH_LABEL_CACHE = "com.google.android.marvin.talkback.labeling.REFRESH_LABEL_CACHE";
    public static final String EXTRA_STRING_ARRAY_PACKAGES = "EXTRA_STRING_ARRAY_PACKAGES";

    private LabelOperationUtils() {
        // Static utility class only
    }

    public static boolean startActivityAddLabelForNode(
            Context context, AccessibilityNodeInfoCompat node) {
        if (context == null || node == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            final Intent addIntent = new Intent(ACTION_ADD_LABEL);
            final Bundle extras = new Bundle();
            extras.putString(EXTRA_STRING_RESOURCE_NAME, node.getViewIdResourceName());
            addIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            addIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            addIntent.putExtras(extras);

            try {
                context.startActivity(addIntent);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean startActivityEditLabel(Context context, Label label) {
        if (context == null || label == null) {
            return false;
        }

        final Intent editIntent = new Intent(ACTION_EDIT_LABEL);
        final Bundle extras = new Bundle();
        extras.putLong(EXTRA_LONG_LABEL_ID, label.getId());
        editIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        editIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        editIntent.putExtras(extras);

        try {
            context.startActivity(editIntent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean startActivityRemoveLabel(Context context, Label label) {
        if (context == null || label == null) {
            return false;
        }

        final Intent removeIntent = new Intent(ACTION_REMOVE_LABEL);
        final Bundle extras = new Bundle();
        extras.putLong(EXTRA_LONG_LABEL_ID, label.getId());
        removeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        removeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        removeIntent.putExtras(extras);

        try {
            context.startActivity(removeIntent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
