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

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.utils.LogUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class OrderedTraversalController {

    private WorkingTree mTree;
    private Map<AccessibilityNodeInfoCompat, WorkingTree> mNodeTreeMap;
    private Map<AccessibilityNodeInfoCompat, Boolean> mSpeakNodesCache;

    public OrderedTraversalController() {
        mNodeTreeMap = new LinkedHashMap<>();
    }

    public void setSpeakNodesCache(Map<AccessibilityNodeInfoCompat, Boolean> speakNodeCache) {
        mSpeakNodesCache = speakNodeCache;
    }

    /**
     * before start next traversal node search the controller must be initialized.
     * The initialisation step includes traversal through all accessibility nodes hierarchy
     * to collect information about traversal order of separate subtrees and moving subtries that
     * has custom befor/after traverse view order
     *
     * @param compatRoot - accessibility node that serves as root node for tree hierarchy
     *                   the controller works with
     */
    public void initOrder(AccessibilityNodeInfoCompat compatRoot) {
        if (compatRoot == null) {
            return;
        }

        NodeCachedBoundsCalculator boundsCalculator = new NodeCachedBoundsCalculator();
        boundsCalculator.setSpeakNodesCache(mSpeakNodesCache);
        mTree = createWorkingTree(AccessibilityNodeInfoCompat.obtain(compatRoot), null, boundsCalculator);
        reorderTree();
    }

    /**
     * Creates tree that reproduces AccessibilityNodeInfoCompat tree hierarchy
     * @param rootNode root node that is starting point for tree reproduction
     * @param parent parent WorkingTree node for subtree that would be returned in this method
     * @return subtree that reproduces accessibility node hierarchy
     */
    private WorkingTree createWorkingTree(AccessibilityNodeInfoCompat rootNode,
                          WorkingTree parent, NodeCachedBoundsCalculator boundsCalculator) {
        if (mNodeTreeMap.containsKey(rootNode)) {
            LogUtils.log(OrderedTraversalController.class,
                    Log.WARN, "creating node tree with looped nodes - break the loop edge");
            return null;
        }

        WorkingTree tree = new WorkingTree(rootNode, parent);
        mNodeTreeMap.put(rootNode, tree);
        ReorderedChildrenIterator iterator =
                ReorderedChildrenIterator.createAscendingIterator(rootNode, boundsCalculator);
        while (iterator != null && iterator.hasNext()) {
            AccessibilityNodeInfoCompat child = iterator.next();
            WorkingTree childSubTree = createWorkingTree(child, tree, boundsCalculator);
            if (childSubTree != null) {
                tree.addChild(childSubTree);
            }
        }

        if (iterator != null) {
            iterator.recycle();
        }
        return tree;
    }

    /**
     * reorder previously created tree according to after/before view traversal order on
     * separate nodes
     */
    private void reorderTree() {
        for(WorkingTree subtree : mNodeTreeMap.values()) {
            AccessibilityNodeInfoCompat node = subtree.getNode();
            AccessibilityNodeInfoCompat beforeNode = getTraversalBefore(node);
            if (beforeNode != null) {
                WorkingTree targetTree = mNodeTreeMap.get(beforeNode);
                moveNodeBefore(subtree, targetTree);
            } else {
                AccessibilityNodeInfoCompat afterNode = getTraversalAfter(node);
                if(afterNode != null) {
                    WorkingTree targetTree = mNodeTreeMap.get(afterNode);
                    moveNodeAfter(subtree, targetTree);
                }
            }
        }
    }

    private AccessibilityNodeInfoCompat getTraversalBefore(AccessibilityNodeInfoCompat node) {
        //TODO (MB) remove the check after AccessibilityNodeInfoCompat.getTraversalBefore() would be committed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null;
        }

        AccessibilityNodeInfo info = ((AccessibilityNodeInfo)node.getInfo()).getTraversalBefore();
        if (info == null) {
            return null;
        }

        return new AccessibilityNodeInfoCompat(info);
    }

    private AccessibilityNodeInfoCompat getTraversalAfter(AccessibilityNodeInfoCompat node) {
        //TODO (MB) remove the check after AccessibilityNodeInfoCompat.getTraversalAfter() would be committed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null;
        }

        AccessibilityNodeInfo info = ((AccessibilityNodeInfo)node.getInfo()).getTraversalAfter();
        if (info == null) {
            return null;
        }

        return new AccessibilityNodeInfoCompat(info);
    }

    private void moveNodeBefore(WorkingTree movingTree,
                                WorkingTree targetTree) {
        if (movingTree == null || targetTree == null) {
            return;
        }

        //noinspection StatementWithEmptyBody
        if(movingTree.hasNoChild(targetTree)) {
            moveNodeBeforeNonChild(movingTree, targetTree);
        } else {
            // no operation if move child before parent
        }
    }

    private void moveNodeBeforeNonChild(WorkingTree movingTree,
                                        WorkingTree targetTree) {
        WorkingTree movingTreeRoot = getParentsThatAreMovedBeforeOrSameNode(movingTree);
        detachSubtreeFromItsParent(movingTreeRoot);

        //swap target node with moving node on targets node parent children list
        WorkingTree parent = targetTree.getParent();
        if (parent != null) {
            parent.swapChild(targetTree, movingTreeRoot);
        }

        movingTreeRoot.setParent(parent);

        //add target node as last child of moving node
        movingTree.addChild(targetTree);
        targetTree.setParent(movingTree);
    }

    /**
     * This method is called before moving subtree. It checks if parent of that node was moved
     * on its place because it has before property to that node. In that case parent node should
     * be moved with movingTree node.
     * @return top node that should be moved with movingTree node.
     */
    private WorkingTree getParentsThatAreMovedBeforeOrSameNode(WorkingTree movingTree) {
        WorkingTree parent = movingTree.getParent();
        if (parent == null) {
            return movingTree;
        }

        AccessibilityNodeInfoCompat parentNode = parent.getNode();
        AccessibilityNodeInfoCompat parentNodeBefore = getTraversalBefore(parentNode);
        if (parentNodeBefore == null) {
            return movingTree;
        }

        if (parentNodeBefore.equals(movingTree.getNode())) {
            return getParentsThatAreMovedBeforeOrSameNode(parent);
        }

        return movingTree;
    }

    private void detachSubtreeFromItsParent(WorkingTree subtree) {
        WorkingTree movingTreeParent = subtree.getParent();
        if (movingTreeParent != null) {
            movingTreeParent.removeChild(subtree);
        }
        subtree.setParent(null);
    }

    private void moveNodeAfter(WorkingTree movingTree,
                               WorkingTree targetTree) {
        if (movingTree == null || targetTree == null) {
            return;
        }

        //noinspection StatementWithEmptyBody
        if(movingTree.hasNoChild(targetTree)) {
            moveNodeAfterNonChild(movingTree, targetTree);
        } else {
            // no operation if move parent after child
        }
    }

    private void moveNodeAfterNonChild(WorkingTree movingTree,
                                        WorkingTree targetTree) {
        movingTree = getParentsThatAreMovedBeforeOrSameNode(movingTree);
        detachSubtreeFromItsParent(movingTree);
        targetTree.addChild(movingTree);
        movingTree.setParent(targetTree);
    }

    public AccessibilityNodeInfoCompat findNext(AccessibilityNodeInfoCompat node) {
        WorkingTree tree = mNodeTreeMap.get(node);
        if (tree == null) {
            LogUtils.log(Log.WARN, "findNext(), can't find WorkingTree for AccessibilityNodeInfo");
            return null;
        }

        WorkingTree nextTree = tree.getNext();
        if (nextTree != null) {
            return AccessibilityNodeInfoCompat.obtain(nextTree.getNode());
        }

        return null;
    }


    public AccessibilityNodeInfoCompat findPrevious(AccessibilityNodeInfoCompat node) {
        WorkingTree tree = mNodeTreeMap.get(node);
        if (tree == null) {
            LogUtils.log(Log.WARN, "findPrevious(), can't find WorkingTree for AccessibilityNodeInfo");
            return null;
        }

        WorkingTree prevTree = tree.getPrevious();
        if (prevTree != null) {
            return AccessibilityNodeInfoCompat.obtain(prevTree.getNode());
        }

        return null;
    }


    /**
     * Searches first node to be focused
     */
    public AccessibilityNodeInfoCompat findFirst() {
        if (mTree == null) {
            return null;
        }

        return AccessibilityNodeInfoCompat.obtain(mTree.getRoot().getNode());
    }

    public AccessibilityNodeInfoCompat findFirst(AccessibilityNodeInfoCompat rootNode) {
        if (rootNode == null) {
            return null;
        }

        WorkingTree tree = mNodeTreeMap.get(rootNode);
        if (tree == null) {
            return null;
        }

        return AccessibilityNodeInfoCompat.obtain(tree.getNode());
    }

    /**
     * Searches last node to be focused
     */
    public AccessibilityNodeInfoCompat findLast() {
        if (mTree == null) {
            return null;
        }

        return AccessibilityNodeInfoCompat.obtain(mTree.getRoot().getLastNode().getNode());
    }

    public AccessibilityNodeInfoCompat findLast(AccessibilityNodeInfoCompat rootNode) {
        if (rootNode == null) {
            return null;
        }

        WorkingTree tree = mNodeTreeMap.get(rootNode);
        if (tree == null) {
            return null;
        }

        return AccessibilityNodeInfoCompat.obtain(tree.getLastNode().getNode());
    }

    /**
     * when controller finishes its search it should be recycled
     */
    public void recycle() {
        for (AccessibilityNodeInfoCompat subtree : mNodeTreeMap.keySet()) {
            subtree.recycle();
        }

        mNodeTreeMap.clear();
    }
}
