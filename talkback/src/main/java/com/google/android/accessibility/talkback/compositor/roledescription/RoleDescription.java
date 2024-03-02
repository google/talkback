/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.compositor.roledescription;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;

/**
 * Interface that provides the description elements for node role descriptions. And the node role
 * description text is composed of the elements, role, name and state description text.
 */
interface RoleDescription {

  /** Returns {@code true} if the node role description should be ignored. */
  default boolean shouldIgnoreDescription(AccessibilityNodeInfoCompat node) {
    return false;
  }

  /** Returns the node name text. */
  CharSequence nodeName(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables);

  /**
   * Returns the node role description text.
   *
   * <p>Note: The content should be non-copyable text for "copy last spoken phrase"
   */
  CharSequence nodeRole(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables);

  /** Returns the node state description text. */
  CharSequence nodeState(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables);
}
