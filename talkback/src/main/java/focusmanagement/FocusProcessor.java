/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.action.TouchExplorationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;

/** Interface of a module to put accessibility focus or clear accessibility focus. */
// TODO: Remove AccessibilityEventListener when FocusProcessor implementations are
// refactored.
public abstract class FocusProcessor implements AccessibilityEventListener {

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Dispatches user actions

  /** Called when the user performs a {@link NavigationAction}. */
  public void onNavigationAction(NavigationAction navigationAction, EventId eventId) {}

  /** Called when the user performs a {@link TouchExplorationAction}. */
  public void onTouchExplorationAction(
      TouchExplorationAction touchExplorationAction, EventId eventId) {}

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Notifies changes from framework

  /**
   * Called when {@link ScreenState} changes.
   *
   * @param oldScreenState The last screen state.
   * @param newScreenState Current screen state.
   * @param eventId EventId for performance tracking.
   */
  public void onScreenStateChanged(
      @Nullable ScreenState oldScreenState, @NonNull ScreenState newScreenState, EventId eventId) {}

  /**
   * Called when input focus changes.
   *
   * @param inputFocus Input focused node.
   * @param eventId EventId for performance tracking.
   */
  public void onViewInputFocused(AccessibilityNodeInfoCompat inputFocus, EventId eventId) {}

  /**
   * Called when window content changes.
   *
   * @param windowId Id of the window whose content is changed.
   * @param eventId EventId for performance tracking.
   */
  public void onWindowContentChanged(int windowId, EventId eventId) {}

  /**
   * Called when a node is manually scrolled by dragging with two fingers on screen.
   *
   * @param scrolledNode Node being scrolled.
   * @param direction The scroll direction.
   * @param eventId EventId for performance tracking.
   */
  public void onNodeManuallyScrolled(
      AccessibilityNodeInfoCompat scrolledNode,
      @TraversalStrategy.SearchDirection int direction,
      EventId eventId) {}
}
