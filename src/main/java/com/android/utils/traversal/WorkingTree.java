/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.utils.traversal;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import com.android.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree that represents Accessibility node hierarchy. It lets reorder the structure of the tree.
 */
public class WorkingTree {

    private AccessibilityNodeInfoCompat mNode;
    private WorkingTree mParent;
    private List<WorkingTree> mChildren;

    public WorkingTree(AccessibilityNodeInfoCompat node, WorkingTree parent) {
        mNode = node;
        mParent = parent;
        mChildren = new ArrayList<>();
    }

    public AccessibilityNodeInfoCompat getNode() {
        return mNode;
    }

    public WorkingTree getParent() {
        return mParent;
    }

    public void setParent(WorkingTree parent) {
        mParent = parent;
    }

    public void addChild(WorkingTree node) {
        mChildren.add(node);
    }

    public boolean removeChild(WorkingTree child) {
        return mChildren.remove(child);
    }

    public boolean hasNoChild(WorkingTree subTree) {
        while (subTree != null) {
            AccessibilityNodeInfoCompat node = subTree.getNode();
            if (mNode.equals(node)) {
                return false;
            }

            subTree = subTree.getParent();
        }

        return true;
    }

    public void swapChild(WorkingTree swappedChild, WorkingTree newChild) {
        int position = mChildren.indexOf(swappedChild);
        if(position < 0) {
            LogUtils.log(Log.ERROR, "WorkingTree IllegalStateException: swap child not found");
            return;
        }

        mChildren.set(position, newChild);
    }

    public WorkingTree getNext() {
        if (!mChildren.isEmpty()) {
            return mChildren.get(0);
        }

        WorkingTree startNode = this;
        while (startNode != null) {
            WorkingTree nextSibling = startNode.getNextSibling();
            if (nextSibling != null) {
                return nextSibling;
            }

            startNode = startNode.getParent();
        }

        return null;
    }

    public WorkingTree getNextSibling() {
        WorkingTree parent = getParent();
        if (parent == null) {
            return null;
        }

        int currentIndex = parent.mChildren.indexOf(this);
        if (currentIndex < 0) {
            LogUtils.log(Log.ERROR, "WorkingTree IllegalStateException: swap child not found");
            return null;
        }

        currentIndex++;

        if (currentIndex >= parent.mChildren.size()) {
            // it was last child
            return null;
        }

        return parent.mChildren.get(currentIndex);
    }

    public WorkingTree getPrevious() {
        WorkingTree previousSibling = getPreviousSibling();
        if (previousSibling != null) {
            return previousSibling.getLastNode();
        }

        return getParent();
    }

    public WorkingTree getPreviousSibling() {
        WorkingTree parent = getParent();
        if (parent == null) {
            return null;
        }

        int currentIndex = parent.mChildren.indexOf(this);
        if (currentIndex < 0) {
            LogUtils.log(Log.ERROR, "WorkingTree IllegalStateException: swap child not found");
            return null;
        }

        currentIndex--;

        if (currentIndex < 0) {
            // it was first child
            return null;
        }

        return parent.mChildren.get(currentIndex);
    }

    public WorkingTree getLastNode() {
        WorkingTree node = this;
        while (!node.mChildren.isEmpty()) {
            node = node.mChildren.get(node.mChildren.size() - 1);
        }

        return node;
    }

    public WorkingTree getRoot() {
        WorkingTree root = this;
        while (root.getParent() != null) {
            root = root.getParent();
        }

        return root;
    }
}
