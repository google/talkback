/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorFocus;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorLabel;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNodeText;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay.CustomLabelAction;
import com.google.android.accessibility.utils.AccessibilityNodeInfoRef;

/**
 * Navigation mode for going through menu items shown specially on the Braille display. Not to be
 * confused with navigation through normal on screen options menu items. This is a menu that is
 * shown only on the Braille display for unlabeled items for adding, editing, or removing labels.
 */
public class BrailleMenuNavigationMode implements NavigationMode {

  /** Callback. */
  public interface Callback {
    void onMenuClosed();
  }

  private final FeedbackManager feedbackManager;
  private final Callback callback;
  private final BehaviorFocus behaviorFocus;
  private final BehaviorLabel behaviorLabel;
  private final BehaviorNodeText behaviorNodeText;
  private final AccessibilityNodeInfoRef initialNode = new AccessibilityNodeInfoRef();

  private boolean active;

  public BrailleMenuNavigationMode(
      FeedbackManager feedbackManager,
      Callback callback,
      BehaviorFocus behaviorFocus,
      BehaviorLabel behaviorLabel,
      BehaviorNodeText behaviorNodeText) {
    this.feedbackManager = feedbackManager;
    this.behaviorFocus = behaviorFocus;
    this.behaviorLabel = behaviorLabel;
    this.behaviorNodeText = behaviorNodeText;
    this.callback = callback;
  }

  @Override
  public void onActivate() {
    active = true;

    // If the currently focused node doesn't warrant a menu, deactivate.
    AccessibilityNodeInfoCompat focused = behaviorFocus.getAccessibilityFocusNode(false);
    if (!showMenuForNode(focused)) {
      feedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
      closeMenu();
      return;
    }

    // Save the node.
    initialNode.reset(focused);

    brailleDisplayWithCurrentItem();
  }

  @Override
  public void onDeactivate() {
    active = false;
    initialNode.clear();
  }

  @Override
  public boolean onAccessibilityEvent(AccessibilityEvent event) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        closeMenu();
        return true;
      default: // Don't let fall through.
        return true;
    }
  }

  @Override
  public boolean onPanLeftOverflow() {
    return false;
  }

  @Override
  public boolean onPanRightOverflow() {
    return false;
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    return false;
  }

  /** Return whether navigation mode is active. */
  public boolean isActive() {
    return active;
  }

  /**
   * Displays a menu for the node and populates {@code mMenuItems}. If the node doesn't warrant a
   * menu does nothing. Returns whether a menu was shown.
   */
  private boolean showMenuForNode(AccessibilityNodeInfoCompat node) {
    return node != null && behaviorLabel.needsLabel(node);
  }

  /**
   * Calls the listener signaling that the menu has been closed and the navigation mode should be
   * exited.
   */
  private void closeMenu() {
    callback.onMenuClosed();
  }

  /** Updates the text on the braille display to match the currently selected menu item. */
  private void brailleDisplayWithCurrentItem() {
    AccessibilityNodeInfoCompat node = initialNode.release();
    CharSequence viewLabel = behaviorNodeText.getCustomLabelText(node);
    // If no custom label, only have "add" option. If there is already a
    // label we have the "edit" and "remove" options.
    if (TextUtils.isEmpty(viewLabel)) {
      behaviorLabel.showLabelDialog(CustomLabelAction.ADD_LABEL, node);
    } else {
      behaviorLabel.showLabelDialog(CustomLabelAction.EDIT_LABEL, node);
    }
  }
}
