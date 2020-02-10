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

package com.google.android.accessibility.switchaccess.treenodes;

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;

/**
 * Leaf node in the scanning tree which corresponds to a scannable item on the screen and is
 * generated from a system-provided {@link AccessibilityNodeInfoCompat}. This leaf node can
 * correspond to either an actionable item ({@link ShowActionsMenuNode}) or a non-actionable item
 * ({@link NonActionableItemNode}).
 */
public abstract class TreeScanSystemProvidedNode extends TreeScanLeafNode {

  /** Returns a copy of the {@link SwitchAccessNodeCompat} used to create this node. */
  public SwitchAccessNodeCompat getNodeInfoCompat() {
    return getNodeInfoCompatDirectly().obtainCopy();
  }

  @Override
  public Rect getRectForNodeHighlight() {
    Rect bounds = new Rect();
    getVisibleBoundsInScreen(bounds);
    return bounds;
  }

  @Override
  public boolean isProbablyTheSameAs(Object other) {
    // Consider Views as likely the same if they have the same
    // 1) View ids
    // 2) Either their top or bottom bounds
    // 3) Either their right or left bounds
    // This is important to be able to keep focus on a text box whose size increases as text is
    // added.
    TreeScanSystemProvidedNode otherNode = (TreeScanSystemProvidedNode) other;
    Rect bounds = getBoundsInScreen();
    Rect otherBounds = otherNode.getBoundsInScreen();
    bounds.sort();
    otherBounds.sort();
    if (((bounds.top != otherBounds.top) && (bounds.bottom != otherBounds.bottom))
        || ((bounds.right != otherBounds.right) && (bounds.left != otherBounds.left))) {
      return false;
    }
    SwitchAccessNodeCompat firstNode = getNodeInfoCompatDirectly();
    SwitchAccessNodeCompat otherFirstNode = otherNode.getNodeInfoCompatDirectly();
    String viewIdResourceName = firstNode.getViewIdResourceName();
    boolean sameResourceId =
        (viewIdResourceName != null)
            && viewIdResourceName.equals(otherFirstNode.getViewIdResourceName());
    if (sameResourceId) {
      return true;
    }

    return hasSimilarText(otherNode);
  }

  /* Returns the bounds of the {@link SwitchAccessNodeCompat}s held by this object. */
  protected Rect getBoundsInScreen() {
    Rect bounds = new Rect();
    getNodeInfoCompatDirectly().getBoundsInScreen(bounds);
    return bounds;
  }

  /**
   * Gets the visible bounds of the {@link SwitchAccessNodeCompat} held by this node.
   *
   * @param bounds The rect in which the visible bounds will be stored
   */
  protected abstract void getVisibleBoundsInScreen(Rect bounds);

  /**
   * Returns {@code true} f the text between two nodes should be considered similar when evaluating
   * #isProbablyTheSameAs
   */
  protected abstract boolean hasSimilarText(TreeScanSystemProvidedNode other);

  /**
   * Returns the {@link SwitchAccessNodeCompat} used to create this node. Note that this doesn't
   * return a copy, so it should only be used internally.
   */
  protected abstract SwitchAccessNodeCompat getNodeInfoCompatDirectly();
}
