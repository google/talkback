/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.record;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import java.util.Iterator;
import java.util.LinkedList;

/** Describes the path from root node to a given node. */
public final class NodePathDescription {

  /**
   * TODO: Replace LinkedList with appropriate Deque. Simply changing the types to Deque
   * and ArrayDeque causes the tests to fail because some objects are no longer considered equal in
   * spite of containing the same values. This may be an issue with the tests, not with using
   * ArrayDeque.
   */
  private final LinkedList<NodeDescription> nodeDescriptions;

  private NodePathDescription() {
    nodeDescriptions = new LinkedList<>();
  }

  public static NodePathDescription obtain(AccessibilityNodeInfoCompat node) {
    final NodePathDescription nodePath = new NodePathDescription();

    AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
        node,
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            nodePath.nodeDescriptions.addFirst(NodeDescription.obtain(node));
            // Always return false to iterate until the root node.
            return false;
          }
        });
    return nodePath;
  }

  /**
   * Finds the target node from {@code root} node along the {@code path}.
   *
   * <p><strong>Note: </strong> Caller should recycle {@code root} and the returned node.
   */
  @Nullable
  public static AccessibilityNodeInfoCompat findNode(
      AccessibilityNodeInfoCompat root, NodePathDescription path) {
    AccessibilityNodeInfoCompat lastMatchedNodeInPath = null;
    Iterator<NodeDescription> nodeDescriptionIterator = path.nodeDescriptions.iterator();

    NodeDescription rootDescription = nodeDescriptionIterator.next();
    if ((rootDescription == null) || !rootDescription.identityMatches(root)) {
      // If root node is not accepted, return null;
      return null;
    }

    lastMatchedNodeInPath = AccessibilityNodeInfoUtils.obtain(root);

    while (nodeDescriptionIterator.hasNext() && (lastMatchedNodeInPath != null)) {
      NodeDescription childDescription = nodeDescriptionIterator.next();
      final AccessibilityNodeInfoCompat matchedChild =
          findChildWithDescription(/* parent= */ lastMatchedNodeInPath, childDescription);

      AccessibilityNodeInfoUtils.recycleNodes(lastMatchedNodeInPath);
      lastMatchedNodeInPath = matchedChild;
    }
    return lastMatchedNodeInPath;
  }

  /**
   * Returns true if we can find a node in path with the same hash code and identity information of
   * {@code target}.
   */
  public boolean containsNodeByHashAndIdentity(AccessibilityNodeInfoCompat target) {
    if (target == null) {
      return false;
    }
    int hashCode = target.hashCode();
    // Compare from root to leaf, because root node is more immutable.
    for (NodeDescription description : nodeDescriptions) {
      if ((description.nodeInfoHashCode == hashCode) && description.identityMatches(target)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodePathDescription that = (NodePathDescription) o;
    return nodeDescriptions.equals(that.nodeDescriptions);
  }

  @Override
  public int hashCode() {
    return nodeDescriptions.hashCode();
  }

  /** <strong>Note:</strong> Caller is responsible to recycle the returned node. */
  private static AccessibilityNodeInfoCompat findChildWithDescription(
      AccessibilityNodeInfoCompat parent, NodeDescription childDescription) {
    AccessibilityNodeInfoCompat child = findChildMatchesIndex(parent, childDescription);
    if ((child != null) && childDescription.identityMatches(child)) {
      return child;
    } else {
      AccessibilityNodeInfoUtils.recycleNodes(child);
      return null;
    }
  }

  /** <strong>Note:</strong> Caller is responsible to recycle the returned node. */
  private static AccessibilityNodeInfoCompat findChildMatchesIndex(
      AccessibilityNodeInfoCompat parent, NodeDescription childDescription) {
    if (childDescription.indexType == NodeDescription.INDEX_TYPE_RAW) {
      if ((childDescription.rawIndexInParent == NodeDescription.UNDEFINED_INDEX)
          || (parent.getChildCount() <= childDescription.rawIndexInParent)) {
        return null;
      } else {
        return parent.getChild(childDescription.rawIndexInParent);
      }
    } else {
      // INDEX_TYPE_COLLECTION
      for (int i = 0; i < parent.getChildCount(); i++) {
        AccessibilityNodeInfoCompat child = parent.getChild(i);
        // Validate CollectionItemInfo.
        if (childDescription.indexMatches(child)) {
          return child;
        }
        AccessibilityNodeInfoUtils.recycleNodes(child);
      }
      return null;
    }
  }
}
