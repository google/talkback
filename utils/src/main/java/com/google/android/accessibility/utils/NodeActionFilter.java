/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Convenience class for a {@link Filter<AccessibilityNodeInfoCompat>} that checks whether nodes
 * support a specific action.
 */
public class NodeActionFilter extends Filter<AccessibilityNodeInfoCompat> {
  private final int action;

  /**
   * Creates a new action filter with the specified action mask.
   *
   * @param action The ID of the action to accept.
   */
  public NodeActionFilter(int action) {
    this.action = action;
  }

  @Override
  public boolean accept(AccessibilityNodeInfoCompat node) {
    return AccessibilityNodeInfoUtils.supportsAction(node, action);
  }
}
