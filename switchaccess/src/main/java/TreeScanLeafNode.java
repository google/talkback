/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.graphics.Rect;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/** Leaf node in the scanning tree that performs actions. */
public abstract class TreeScanLeafNode implements TreeScanNode {
  private TreeScanSelectionNode mParent;

  @Override
  public void recycle() {}

  /**
   * Gets the available actions when focus reaches this node.
   *
   * @return the menu items amongst which we should disambiguate
   */
  public List<MenuItem> performActionOrGetMenuItems() {
    return Collections.emptyList();
  }

  /** Returns the speakable text for this node. */
  public List<CharSequence> getSpeakableText() {
    return Collections.emptyList();
  }

  /**
   * Gets the rect that will be used when determining how to highlight this node during scanning.
   *
   * @return The rectangle that should be used when highlighting this item. Coordinates are absolute
   *     (bounds in screen).
   */
  public abstract Rect getRectForNodeHighlight();

  /** Returns {@code true} if this node has scroll actions. */
  public boolean isScrollable() {
    return false;
  }

  @Override
  public List<TreeScanLeafNode> getNodesList() {
    List<TreeScanLeafNode> nodes = new LinkedList<>();
    nodes.add(this);
    return nodes;
  }

  @Override
  public TreeScanSelectionNode getParent() {
    return mParent;
  }

  @Override
  public void setParent(TreeScanSelectionNode parent) {
    mParent = parent;
  }
}
