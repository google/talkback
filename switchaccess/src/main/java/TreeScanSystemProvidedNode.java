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

package com.google.android.accessibility.switchaccess;

import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Leaf node in the scanning tree which corresponds to a scannable item on the screen and is
 * generated from a system-provided {@link AccessibilityNodeInfoCompat}. This leaf node can
 * correspond to either an actionable item ({@link ShowActionsMenuNode}) or a non-actionable item
 * ({@link NonActionableItemNode}).
 */
public abstract class TreeScanSystemProvidedNode extends TreeScanLeafNode {

  /** Returns a copy of the {@link SwitchAccessNodeCompat} used to create this node. */
  public abstract SwitchAccessNodeCompat getNodeInfoCompat();

  @Override
  public Rect getRectForNodeHighlight() {
    Rect bounds = new Rect();
    getVisibleBoundsInScreen(bounds);
    return bounds;
  }

  /**
   * Gets the visible bounds of the {@link SwitchAccessNodeCompat} held by this node.
   *
   * @param bounds The rect in which the visible bounds will be stored
   */
  protected abstract void getVisibleBoundsInScreen(Rect bounds);
}
