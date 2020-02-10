/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.accessibility.talkback.controller;

import com.google.android.accessibility.utils.Performance.EventId;

// TODO: Handle changing window content.  Look at hierarchy cache invalidation.
/** Component used to control reading of the entire display. */
public interface FullScreenReadController {
  /** Releases all resources held by this controller and save any persistent preferences. */
  void shutdown();

  /** Starts linearly reading from the node with accessibility focus. */
  void startReadingFromNextNode(EventId eventId);

  /**
   * Starts linearly reading from the node with accessibility focus.
   *
   * @param eventId EventId for tracking performance.
   * @param fromContextMenu Flag to check if Reading is triggered from Context menu.
   */
  void startReadingFromNextNode(EventId eventId, boolean fromContextMenu);

  /** Starts linearly reading from the top of the view hierarchy. */
  void startReadingFromBeginning(EventId eventId);

  /**
   * Starts linearly reading from the top of the view hierarchy.
   *
   * @param eventId EventId for tracking performance.
   * @param fromContextMenu Flag to check if Reading is triggered from Context menu.
   */
  void startReadingFromBeginning(EventId eventId, boolean fromContextMenu);

  /** Starts linearly reading without showing dialog */
  void startReadingWithoutDialog(EventId eventId, int state);

  /** Stops speech output and view traversal at the current position. */
  void interrupt();

  /**
   * Returns whether full-screen reading is currently active. Equivalent to calling {@code
   * mCurrentState != STATE_STOPPED}.
   *
   * @return Whether full-screen reading is currently active.
   */
  boolean isActive();

  /**
   * Gets dialog for continuous reading mode.
   *
   * @return FullScreenReadDialog for other class to access {@link
   *     FullScreenReadDialog#getShouldShowDialogPref()}
   */
  FullScreenReadDialog getFullScreenReadDialog();
}
