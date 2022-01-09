/*
 * Copyright (C) 2014 Google Inc.
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

import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY;
import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.IntDef;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Functions to find focus.
 *
 * <p>NOTE: To give a consistent behaviour, this code should be kept in sync with the relevant
 * subset of code in the {@code CursorController} class in TalkBack.
 */
public class FocusFinder {

  private static final String TAG = "FocusFinder";
  private final AccessibilityService service;

  /** Screen focus types in accessibility. */
  @IntDef({FOCUS_INPUT, FOCUS_ACCESSIBILITY})
  @Retention(RetentionPolicy.SOURCE)
  public @interface FocusType {}

  public FocusFinder(AccessibilityService service) {
    this.service = service;
  }

  /** Finds the view that has the specified focus type. The type is defined in {@link FocusType}. */
  public @Nullable AccessibilityNodeInfoCompat findFocusCompat(@FocusType int focusType) {
    switch (focusType) {
      case FOCUS_ACCESSIBILITY:
        return FocusFinder.getAccessibilityFocusNode(service, false);
      case FOCUS_INPUT:
        return AccessibilityNodeInfoUtils.toCompat(
            service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT));
      default: // fall out
    }

    return null;
  }

  /**
   * Returns the accessibility focus by calling {@link AccessibilityService#findFocus(int)}. If no
   * focus is found, it allows to return the root node of the active window.
   *
   * @param fallbackOnRoot true for returning the root node if no focus is found.
   *     <p><strong>Note:</strong> Caller is responsible for recycling the returned node.
   */
  public static @Nullable AccessibilityNodeInfoCompat getAccessibilityFocusNode(
      AccessibilityService service, boolean fallbackOnRoot) {
    AccessibilityNodeInfo focused = null;
    AccessibilityNodeInfo root = null;

    try {
      AccessibilityNodeInfo ret = null;
      focused = service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      if (focused == null) {
        // Find the focused node from the root of the active window, as a alternative method if
        // couldn't find the focused node by AccessibilityService.
        root = service.getRootInActiveWindow();
        if (root != null) {
          focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
      }

      if (focused != null) {
        // If the focused node is from WebView, we still need to return it even though it's not
        // visible to user. REFERTO for details.
        if (focused.isVisibleToUser()
            || WebInterfaceUtils.isWebContainer(AccessibilityNodeInfoUtils.toCompat(focused))) {
          ret = focused;
          focused = null;
        }
      }

      if (ret == null && fallbackOnRoot) {
        ret = service.getRootInActiveWindow();
        if (ret == null) {
          LogUtils.e(TAG, "No current window root");
        }
      }

      if (ret != null) {
        // When AccessibilityNodeProvider is used, the returned node may be stale.
        boolean exist = ret.refresh();
        if (!exist) {
          AccessibilityNodeInfoUtils.recycleNodes(ret);
          return null;
        }
        return AccessibilityNodeInfoUtils.toCompat(ret);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(focused, root);
    }

    return null;
  }
}
