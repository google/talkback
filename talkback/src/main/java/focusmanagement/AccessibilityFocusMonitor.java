/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WindowManager;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Monitors the current accessibility-focus location, for both event-interpreters and actors. */
public class AccessibilityFocusMonitor {
  private final AccessibilityService service;
  private final AccessibilityFocusActionHistory.Reader history;

  public AccessibilityFocusMonitor(
      AccessibilityService service, AccessibilityFocusActionHistory.Reader history) {
    this.service = service;
    this.history = history;
  }

  /**
   * Returns accessibility focused node if it's visible on screen. Otherwise returns input focused
   * edit field if{@code returnInputFocusedEditFieldIfNullOrInvisible} is set to {@code true}.
   * Called by DirectionNavigationController.
   *
   * <p><strong>Note:</strong>
   *
   * <ul>
   *   <li>The client is responsible for recycling the returned node.
   *   <li>The returned node might not pass {@link
   *       AccessibilityNodeInfoUtils#shouldFocusNode(AccessibilityNodeInfoCompat)}, the caller
   *       should validate the result if needed.
   * </ul>
   */
  public @Nullable AccessibilityNodeInfoCompat getAccessibilityFocus(boolean useInputFocusIfEmpty) {
    // Nodes to be recycled.
    AccessibilityNodeInfoCompat root = null;
    AccessibilityNodeInfoCompat a11yFocusedNode = null;
    AccessibilityNodeInfoCompat inputFocusedNode = null;
    AccessibilityNodeInfoCompat lastFocusedEditFieldInHistory = null;

    try {
      // First, see if we've already placed accessibility focus.
      root = AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(service);
      if (root == null) {
        return null;
      }
      a11yFocusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

      if ((a11yFocusedNode != null) && AccessibilityNodeInfoUtils.isVisible(a11yFocusedNode)) {
        return AccessibilityNodeInfoUtils.obtain(a11yFocusedNode);
      }

      if (!useInputFocusIfEmpty) {
        return null;
      }

      // TODO: If there's no focused node, we should either mimic following
      // focus from new window or try to be smart for things like list views.
      inputFocusedNode = AccessibilityServiceCompatUtils.getInputFocusedNode(service);
      if ((inputFocusedNode != null)
          && inputFocusedNode.isFocused()
          && (inputFocusedNode.isEditable()
              || (Role.getRole(inputFocusedNode) == Role.ROLE_EDIT_TEXT))) {
        return AccessibilityNodeInfoUtils.obtain(inputFocusedNode);
      }

      // If we can't find the focused node but the keyboard is showing, return the last editable.
      // This will occur if the input-focused view is actually a virtual view (e.g. in WebViews).
      // Note: need to refresh() in order to verify that the node is still available on-screen.
      FocusActionRecord record = history.getLastEditableFocusActionRecord();

      lastFocusedEditFieldInHistory = (record == null) ? null : record.getFocusedNode();

      if ((lastFocusedEditFieldInHistory != null) && lastFocusedEditFieldInHistory.refresh()) {
        // TODO: Shall we check the presence of IME window?
        // IME window check below is copied from legacy CursorController. What if the device is
        // connected to bluetooth keyboard?
        WindowManager windowManager = new WindowManager(false); // RTL state doesn't matter.
        windowManager.setWindows(AccessibilityServiceCompatUtils.getWindows(service));
        if (windowManager.isInputWindowOnScreen()) {
          return AccessibilityNodeInfoCompat.obtain(lastFocusedEditFieldInHistory);
        }
      }
      return null;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(
          root, a11yFocusedNode, inputFocusedNode, lastFocusedEditFieldInHistory);
    }
  }
}
