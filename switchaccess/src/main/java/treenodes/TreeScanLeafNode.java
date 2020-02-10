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

package com.google.android.accessibility.switchaccess.treenodes;

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Leaf node in the scanning tree that performs actions. */
public abstract class TreeScanLeafNode extends TreeScanNode {

  private TreeScanSelectionNode parent;

  @Override
  public void recycle() {}

  /**
   * Gets the available actions when focus reaches this node.
   *
   * @param selectMenuItemListener The listener that is notified when a menu item is selected
   * @return the menu items amongst which we should disambiguate
   */
  public List<MenuItem> performActionOrGetMenuItems(
      @Nullable SelectMenuItemListener selectMenuItemListener) {
    return Collections.emptyList();
  }

  /** Returns the speakable text for this node. */
  public List<CharSequence> getSpeakableText() {
    return Collections.emptyList();
  }

  /**
   * Gets the rect that will be used when determining how to highlight this node during scanning.
   *
   * @return The rectangle that should be used when highlighting this item or {@code null} if this
   *     item doesn't need to be highlighted on screen. Coordinates are absolute (bounds in screen).
   */
  @Nullable
  public abstract Rect getRectForNodeHighlight();

  /** Returns {@code true} if this node has scroll actions. */
  public boolean isScrollable() {
    return false;
  }

  /** Returns the list of actions that can be performed on this node. */
  public List<AccessibilityActionCompat> getActionList() {
    return Collections.emptyList();
  }

  @Override
  public List<TreeScanLeafNode> getNodesList() {
    List<TreeScanLeafNode> nodes = new ArrayList<>();
    nodes.add(this);
    return nodes;
  }

  @Override
  public TreeScanLeafNode getFirstLeafNode() {
    return this;
  }

  @Override
  public TreeScanSelectionNode getParent() {
    return parent;
  }

  @Override
  public void setParent(TreeScanSelectionNode parent) {
    this.parent = parent;
  }

  /**
   * Checks if this node is probably the same node as the given node. This is different from
   * checking for equality as it is less strict. This is used to determine whether to place focus on
   * an element after an element was just actioned upon (e.g. scrolled).
   *
   * @param other The other node to check
   * @return {@code true} if this node probably corresponds to the same view as the other node
   */
  public abstract boolean isProbablyTheSameAs(Object other);
}
