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

import android.graphics.Paint;
import android.graphics.Rect;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Option Scanning node that holds other nodes for scanning.
 */
public class OptionScanSelectionNode implements OptionScanNode {

    protected OptionScanNode[] mChildren;
    private OptionScanSelectionNode mParent;

    /**
     * Selection nodes must be constructed with at least two things to select between
     * @param child0 The first item to select
     * @param child1 The second item to select
     * @param otherChildren Any other items to select
     */
    public OptionScanSelectionNode(
            OptionScanNode child0, OptionScanNode child1, OptionScanNode... otherChildren) {
        mChildren = new OptionScanNode[otherChildren.length + 2];
        mChildren[0] = child0;
        mChildren[1] = child1;
        System.arraycopy(otherChildren, 0, mChildren, 2, otherChildren.length);
        for (OptionScanNode child : mChildren) {
            child.setParent(this);
        }
    }

    @Override
    public Set<Rect> getRectsForNodeHighlight() {
        Set<Rect> rects = new HashSet<>();
        for (OptionScanNode child : mChildren) {
            rects.addAll(child.getRectsForNodeHighlight());
        }
        return Collections.unmodifiableSet(rects);
    }

    @Override
    public void recycle() {
        for (OptionScanNode child : mChildren) {
            child.recycle();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof OptionScanSelectionNode)) {
            return false;
        }
        OptionScanSelectionNode otherNode = (OptionScanSelectionNode) other;
        if (otherNode.getChildCount() != getChildCount()) {
            return false;
        }
        for (int i = 0; i < mChildren.length; i++) {
            OptionScanNode child = mChildren[i];
            if (!child.equals(otherNode.getChild(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void performAction() {}

    @Override
    public OptionScanSelectionNode getParent() {
        return mParent;
    }

    @Override
    public void setParent(OptionScanSelectionNode parent) {
        mParent = parent;
    }

    /**
     * Get the number of child nodes
     * @return The number of child nodes.
     */
    public int getChildCount() {
        return mChildren.length;
    }

    /**
     * Get a specified child node.
     * @param index The index of the desired child.
     * @return The child requested, or {@code null} if
     * {@code ((index < 0) || (index >= getChildCount))}
     */
    @SuppressWarnings("JavaDoc")
    public OptionScanNode getChild(int index) {
        if ((index < 0) || (index >= getChildCount())) {
            return null;
        }
        return mChildren[index];
    }

    public void showSelections(OverlayController overlayController, Paint[] paints) {
        /* Display the options for the children. In addition if there are global views in the
         * overlay, such as the menu button when option scanning in enabled, highlight them as
         * well */
        Rect globalMenuButton = overlayController.getMenuButtonLocation();
        for (int childIndex = 0; childIndex < getChildCount(); ++childIndex) {
            Set<Rect> rectsForHighlight = new HashSet<>();
            if ((paints.length > childIndex) && paints[childIndex] != null) {
                rectsForHighlight.addAll(getChild(childIndex).getRectsForNodeHighlight());
                if (childIndex == getChildCount() - 1 && globalMenuButton != null) {
                    rectsForHighlight.add(globalMenuButton);
                }
                overlayController.highlightPerimeterOfRects(rectsForHighlight, paints[childIndex]);
            }
        }
    }
}
