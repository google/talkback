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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import com.google.android.accessibility.utils.Performance.EventId;

public class PerformActionUtils {

  public static boolean performAction(
      AccessibilityNodeInfoCompat node, int action, EventId eventId) {
    return performAction(node, action, null /* args */, eventId);
  }

  public static boolean performAction(
      AccessibilityNodeInfoCompat node, int action, Bundle args, EventId eventId) {
    if (node == null) {
      return false;
    }

    LogUtils.log(
        PerformActionUtils.class,
        Log.DEBUG,
        "perform action=%d=%s with args=%s on node=%s for event=%s",
        action,
        AccessibilityNodeInfoUtils.actionToString(action),
        args,
        node,
        eventId);

    return node.performAction(action, args);
  }
}
