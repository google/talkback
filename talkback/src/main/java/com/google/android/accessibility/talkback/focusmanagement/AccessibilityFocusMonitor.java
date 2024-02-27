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
      Filter.node(
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
    return getAccessibilityFocus(useInputFocusIfEmpty) != null;
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
    // First, see if we've already placed accessibility focus.
    AccessibilityNodeInfoCompat a11yFocusedNode = focusFinder.findFocusCompat(FOCUS_ACCESSIBILITY);

    if ((a11yFocusedNode != null) && AccessibilityNodeInfoUtils.isVisible(a11yFocusedNode)) {
      return a11yFocusedNode;
    }

    if (!useInputFocusIfEmpty) {
      return null;
    }

    // TODO: If there's no focused node, we should either mimic following
    // focus from new window or try to be smart for things like list views.
    AccessibilityNodeInfoCompat inputFocusedNode = getInputFocus();
    if (inputFocusedNode != null) {
      boolean isEditable =
          inputFocusedNode.isEditable() || Role.getRole(inputFocusedNode) == Role.ROLE_EDIT_TEXT;
      if (inputFocusedNode.isFocused() && (!requireEditable || isEditable)) {
        return inputFocusedNode;
      }
    }

    // If we can't find the focused node but the keyboard is showing, return the last editable.
    // This will occur if the input-focused view is actually a virtual view (e.g. in WebViews).
    // Note: need to refresh() in order to verify that the node is still available on-screen.
    FocusActionRecord record = history.getLastEditableFocusActionRecord();

    AccessibilityNodeInfoCompat lastFocusedEditFieldInHistory =
        (record == null) ? null : record.getFocusedNode();

    if ((lastFocusedEditFieldInHistory != null) && lastFocusedEditFieldInHistory.refresh()) {
      // TODO: Shall we check the presence of IME window?
      // IME window check below is copied from legacy CursorController. What if the device is
      // connected to bluetooth keyboard?
      if (AccessibilityServiceCompatUtils.isInputWindowOnScreen(service)) {
        return lastFocusedEditFieldInHistory;
      }
    }
    return null;
  }

  /**
   * get node with class type is Role.ROLE_SEEK_CONTROL or Role.ROLE_NUMBER_PICKER.
   *
   * @return Either the SeekBar or NumberPicker node info, null otherwise.
   */
  public @Nullable AccessibilityNodeInfoCompat getSupportedAdjustableNode() {
    // get current focus node
    AccessibilityNodeInfoCompat focusNode =
        getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);

    if (Role.getRole(focusNode) == Role.ROLE_SEEK_CONTROL) {
      return focusNode;
    }

    // The focused node for the NumberPicker is the child node(Button or EditText), so it has to
    // customize the node matching rule for NumberPicker here.
    return AccessibilityNodeInfoUtils.getMatchingAncestor(
        focusNode, NUMBER_PICKER_FILTER_FOR_ADJUST);
  }

  /**
   * Returns the visible editing node when the given {@link AccessibilityNodeInfoCompat} is focused
   * and on an IME window.
   */
  public @Nullable AccessibilityNodeInfoCompat getEditingNodeFromFocusedKeyboard(
      AccessibilityNodeInfoCompat accessibilityFocusNode) {
    if (AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(accessibilityFocusNode)
        && AccessibilityNodeInfoUtils.isKeyboard(accessibilityFocusNode)) {
      AccessibilityNodeInfoCompat inputFocus = getInputFocus();
      if (inputFocus != null
          && inputFocus.isVisibleToUser()
          && Role.getRole(inputFocus) == Role.ROLE_EDIT_TEXT) {
        return inputFocus;
      }
    }
    return null;
  }

  /**
   * Returns the node that currently has input focus.
   *
   * <p>Uses the {@link FocusFinder}.
   */
  public @Nullable AccessibilityNodeInfoCompat getInputFocus() {
    return focusFinder.findFocusCompat(FOCUS_INPUT);
  }

  /**
   * For some actions which are not directly operated on the Accessibility focused node, especially
   * for EditText with IME popped. This method
   *
   * @param node Node of the focused node.
   * @return the actionable node related to the focused node which can be either EditText or view
   *     node inside keyboard.
   */
  public @Nullable AccessibilityNodeInfoCompat getNodeForEditingActions(
      AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return node;
    }
    return getEditingNodeFromFocusedKeyboard(node);
  }
}
