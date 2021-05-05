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

import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY;
import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionRecord;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.ClassLoadingCache;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Role;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Monitors the current accessibility-focus location, for both event-interpreters and actors. */
public class AccessibilityFocusMonitor {
  public static final Filter<AccessibilityNodeInfoCompat> NUMBER_PICKER_FILTER_FOR_ADJUST =
      new Filter.NodeCompat(
          (node) ->
              (node != null)
                  && ClassLoadingCache.checkInstanceOf(
                      node.getClassName(), android.widget.NumberPicker.class));
  private final AccessibilityService service;
  private final FocusFinder focusFinder;
  private final AccessibilityFocusActionHistory.Reader history;

  public AccessibilityFocusMonitor(
      AccessibilityService service,
      FocusFinder focusFinder,
      AccessibilityFocusActionHistory.Reader history) {
    this.service = service;
    this.focusFinder = focusFinder;
    this.history = history;
  }

  /**
   * Returns accessibility focused node if it's visible on screen. Otherwise returns input focused
   * edit field if{@code returnInputFocusedEditFieldIfNullOrInvisible} is set to {@code true}.
   * Called by DirectionNavigationInterpreter.
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
    return getAccessibilityFocus(useInputFocusIfEmpty, /* requireEditable= */ true);
  }

  /** Return true if currently there's a focused node on screen. */
  public boolean hasAccessibilityFocus(boolean useInputFocusIfEmpty) {
    @Nullable AccessibilityNodeInfoCompat node = null;
    try {
      node = getAccessibilityFocus(useInputFocusIfEmpty);
      return (node != null);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  /**
   * Returns accessibility focused node if it's visible on screen. Otherwise returns current focused
   * field if {@code requireEditable} is set as {@code false} and {@code
   * returnInputFocusedEditFieldIfNullOrInvisible} is set to {@code true}. Called by
   * FocusProcessorForLogicalNavigation.
   *
   * <p>More info see javadoc on {@code getAccessibilityFocus(boolean)}
   */
  public @Nullable AccessibilityNodeInfoCompat getAccessibilityFocus(
      boolean useInputFocusIfEmpty, boolean requireEditable) {
    // Nodes to be recycled.
    AccessibilityNodeInfoCompat a11yFocusedNode = null;
    AccessibilityNodeInfoCompat inputFocusedNode = null;
    AccessibilityNodeInfoCompat lastFocusedEditFieldInHistory = null;

    try {
      // First, see if we've already placed accessibility focus.
      a11yFocusedNode = focusFinder.findFocusCompat(FOCUS_ACCESSIBILITY);

      if ((a11yFocusedNode != null) && AccessibilityNodeInfoUtils.isVisible(a11yFocusedNode)) {
        return AccessibilityNodeInfoUtils.obtain(a11yFocusedNode);
      }

      if (!useInputFocusIfEmpty) {
        return null;
      }

      // TODO: If there's no focused node, we should either mimic following
      // focus from new window or try to be smart for things like list views.
      inputFocusedNode = focusFinder.findFocusCompat(FOCUS_INPUT);
      if (inputFocusedNode != null) {
        boolean isEditable =
            inputFocusedNode.isEditable() || Role.getRole(inputFocusedNode) == Role.ROLE_EDIT_TEXT;
        if (inputFocusedNode.isFocused() && (!requireEditable || isEditable)) {
          return AccessibilityNodeInfoUtils.obtain(inputFocusedNode);
        }
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
        if (AccessibilityServiceCompatUtils.isInputWindowOnScreen(service)) {
          return AccessibilityNodeInfoCompat.obtain(lastFocusedEditFieldInHistory);
        }
      }
      return null;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(
          a11yFocusedNode, inputFocusedNode, lastFocusedEditFieldInHistory);
    }
  }

  /**
   * get node with class type is Role.ROLE_SEEK_CONTROL or Role.ROLE_NUMBER_PICKER.
   *
   * <p><strong>Note:</strong> It is a client responsibility to recycle the received info by calling
   * {@link AccessibilityNodeInfoCompat#recycle()} .
   *
   * @return Either the SeekBar or NumberPicker node info, null otherwise.
   */
  @Nullable
  public AccessibilityNodeInfoCompat getSupportedAdjustableNode() {
    // get current focus node
    AccessibilityNodeInfoCompat focusNode =
        getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);

    if (Role.getRole(focusNode) == Role.ROLE_SEEK_CONTROL) {
      return focusNode;
    }

    try {
      // The focused node for the NumberPicker is the child node(Button or EditText), so it has to
      // customize the node matching rule for NumberPicker here.
      return AccessibilityNodeInfoUtils.getMatchingAncestor(
          focusNode, NUMBER_PICKER_FILTER_FOR_ADJUST);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(focusNode);
    }
  }
}
