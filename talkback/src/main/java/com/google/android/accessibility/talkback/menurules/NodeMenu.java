/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.talkback.menurules;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import java.util.List;

/** Interface to provide menus for the {@link AccessibilityNodeInfoCompat}. */
public interface NodeMenu {
  /**
   * Determines whether this rule should process the specified node.
   *
   * @param context The parent context.
   * @param node The node to filter.
   * @return {@code true} if this rule should process the node.
   */
  boolean accept(Context context, AccessibilityNodeInfoCompat node);

  /**
   * Processes the specified node and returns a {@link List} of relevant local {@link
   * ContextMenuItem}s for that node.
   *
   * <p>Note: The validity of the node is guaranteed only within the scope of this method.
   *
   * <p>
   *
   * @param context The parent context.
   * @param node The node to process
   * @param includeAncestors sets to {@code false} to find menu items from the node itself only.
   *     Sets to {@code true} may find menu items from its ancestors.
   */
  List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors);
}
