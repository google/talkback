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

package com.google.android.accessibility.utils;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Used to perform an action on a AccessibilityNodeInfoCompat and log relevant information. */
public class PerformActionUtils {
  private static final String TAG = "PerformActionUtils";

  public static boolean performAction(
      @Nullable AccessibilityNodeInfoCompat node, int action, @Nullable EventId eventId) {
    return performAction(node, action, null /* args */, eventId);
  }

  public static boolean performAction(
      @Nullable AccessibilityNodeInfoCompat node,
      int action,
      Bundle args,
      @Nullable EventId eventId) {
    if (node == null) {
      return false;
    }

    boolean result = node.performAction(action, args);
    LogUtils.d(
        TAG,
        "perform action=%d=%s returns %s with args=%s on node=%s for event=%s",
        action,
        AccessibilityNodeInfoUtils.actionToString(action),
        result,
        args,
        node,
        eventId);

    return result;
  }

  public static boolean showOnScreen(
      @Nullable AccessibilityNodeInfoCompat node, @Nullable EventId eventId) {
    return performAction(node, AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId(), eventId);
  }
}
