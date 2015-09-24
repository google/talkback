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

package com.android.switchaccess;

import android.graphics.Rect;

import java.util.Set;

/**
 * Abstract class for nodes used to traverse all user options
 */
public interface OptionScanNode {

    /**
     * When offering this node as an option, we will highlight options using rectangles. Each node
     * must specify what rectangles should be highlighted
     * @return An immutable set of rectangles that should be highlighted to indicate this option.
     * Coordinates are absolute (bounds in screen).
     */
    public abstract Set<Rect> getRectsForNodeHighlight();

    /**
     * Recycle the tree based at this node. Some nodes may hold resources such as
     * {@code AccessibilityNodeInfo}s that require explicit recycling.
     */
    public void recycle();

    /**
     * Perform any action needed when focus reaches the node
     */
    public void performAction();

    /**
     * Get parent node
     * @return The parent node
     */
    public OptionScanSelectionNode getParent();

    /**
     * Set the parent node
     * @param parent The parent of this node in the tree
     */
    public void setParent(OptionScanSelectionNode parent);
}
