/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.switchaccess.ui;

import android.graphics.Paint;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanLeafNode;

/**
 * Interface for classes responsible for highlighting, animating, or drawing additional elements on
 * views to indicate their selectability for different scanning methods.
 */
public interface HighlightStrategy {

  /**
   * Method called by {@code TreeScanNode}s to highlight their children.
   *
   * @param nodes The list of nodes that should be highlighted using the provided {@code
   *     highlightPaint}
   * @param highlightPaint The Paint specifying style and color to be used on this group of {@code
   *     rects}
   * @param groupIndex The index of the parent node whose children will be highlighted, relative to
   *     the grandparent
   * @param totalChildren The total number of child nodes to be highlighted
   */
  void highlight(
      final Iterable<TreeScanLeafNode> nodes,
      final Paint highlightPaint,
      int groupIndex,
      int totalChildren);

  /** Cancels the highlight any stops any animations. */
  void shutdown();
}
