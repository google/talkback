/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.toStringShort;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.compat.CompatUtils;
import java.util.Objects;

/**
 * A record of TalkBack performing {@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS}
 * action.
 */
public class FocusActionRecord {
  /**
   * Time when the accessibility focus action is performed. Initialized with
   * SystemClock.uptimeMillis().
   */
  private final long actionTime;
  /** Node being accessibility focused. */
  private final AccessibilityNodeInfoCompat focusedNode;
  /** Describes how to find focused node from root node. */
  @NonNull private final NodePathDescription nodePathDescription;
  /** Extra information about source focus action. */
  private final FocusActionInfo extraInfo;
  /** Unique id for the node */
  private final String uniqueId;

  // This is temporarily used until the androidx api is ready; then we can call getUniqueId
  // directly. TODO: call AccessibilityNodeInfoCompat.getUniqueId() instead.
  private static String getUniqueIdViaReflection(AccessibilityNodeInfoCompat nodeInfo) {
    if (nodeInfo == null) {
      return null;
    }
    String val =
        (String)
            CompatUtils.invoke(
                nodeInfo.unwrap(),
                /* defaultValue= */ null,
                CompatUtils.getMethod(AccessibilityNodeInfo.class, "getUniqueId"));
    return val;
  }

  private static String compoundPackageNameAndUniqueId(AccessibilityNodeInfoCompat nodeInfo) {
    if (nodeInfo == null) {
      return null;
    }
    String compoundId = getUniqueIdViaReflection(nodeInfo);
    if (compoundId != null) {
      compoundId = String.valueOf(nodeInfo.getPackageName()) + ':' + compoundId;
    }
    return compoundId;
  }

  /**
   * Constructs a FocusActionRecord.
   *
   * @param focusedNode Node being accessibility focused.
   * @param extraInfo Extra information defined by action performer.
   * @param actionTime Time when focus action happens. Got by {@link SystemClock#uptimeMillis()}.
   */
  public FocusActionRecord(
      @NonNull AccessibilityNodeInfoCompat focusedNode,
      FocusActionInfo extraInfo,
      long actionTime) {
    this.focusedNode = focusedNode;
    nodePathDescription = NodePathDescription.obtain(focusedNode);
    this.extraInfo = extraInfo;
    this.actionTime = actionTime;
    this.uniqueId = (focusedNode == null) ? null : compoundPackageNameAndUniqueId(focusedNode);
  }

  /** Constructs FocusActionRecord. Used internally by {@link #copy(FocusActionRecord)}. */
  private FocusActionRecord(
      AccessibilityNodeInfoCompat focusedNode,
      @NonNull NodePathDescription nodePathDescription,
      FocusActionInfo extraInfo,
      long actionTime,
      String uniqueId) {
    this.focusedNode = focusedNode;
    this.nodePathDescription = new NodePathDescription(nodePathDescription);
    this.extraInfo = extraInfo;
    this.actionTime = actionTime;
    this.uniqueId = uniqueId;
  }

  /** Returns an instance of the focused node. */
  public AccessibilityNodeInfoCompat getFocusedNode() {
    return focusedNode;
  }

  /** Returns reference to node-path. */
  public NodePathDescription getNodePathDescription() {
    return nodePathDescription;
  }

  /** Returns extra information of the focus action. */
  public FocusActionInfo getExtraInfo() {
    return extraInfo;
  }

  /**
   * Returns the time when the accessibility focus action happens, which is initialized with {@link
   * SystemClock#uptimeMillis()}.
   */
  public long getActionTime() {
    return actionTime;
  }

  /** Returns the stored unique id which is created in the FocusActionRecord constructor. */
  @Nullable
  public String getUniqueId() {
    return uniqueId;
  }

  /** Returns a copied instance of another FocusActionRecord. */
  @Nullable
  public static FocusActionRecord copy(FocusActionRecord record) {
    if (record == null) {
      return null;
    }
    return new FocusActionRecord(
        record.focusedNode,
        record.nodePathDescription,
        record.extraInfo,
        record.actionTime,
        record.uniqueId);
  }

  /**
   * Returns true when the unique id are identical or both null.
   *
   * @param uniqueId existing uniqueId.
   * @param node Accessibility node to check its own uniqueId
   */
  private static boolean checkUniqueIdIdentical(
      @Nullable String uniqueId, AccessibilityNodeInfoCompat node) {
    if (uniqueId == null) {
      return compoundPackageNameAndUniqueId(node) == null;
    } else {
      return uniqueId.equals(compoundPackageNameAndUniqueId(node));
    }
  }

  /**
   * Returns the last focused node in {@code window} if it's still valid on screen, otherwise
   * returns focusable node with the same position.
   */
  @Nullable
  public static AccessibilityNodeInfoCompat getFocusableNodeFromFocusRecord(
      @Nullable AccessibilityNodeInfoCompat root,
      @NonNull FocusFinder focusFinder,
      @NonNull FocusActionRecord focusActionRecord) {
    AccessibilityNodeInfoCompat lastFocusedNode = focusActionRecord.getFocusedNode();

    // When looking up the focusable node by focus record, the unique id is the highest priority to
    // match.
    @Nullable String uniqueId = focusActionRecord.getUniqueId();
    if (lastFocusedNode.refresh()
        && checkUniqueIdIdentical(uniqueId, lastFocusedNode)
        && AccessibilityNodeInfoUtils.shouldFocusNode(lastFocusedNode)) {
      return lastFocusedNode;
    }

    if (uniqueId != null) {
      lastFocusedNode =
          AccessibilityNodeInfoUtils.searchFromBfs(
              root,
              new Filter<AccessibilityNodeInfoCompat>() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                  return uniqueId.equals(compoundPackageNameAndUniqueId(node));
                }
              });
      if (lastFocusedNode != null && AccessibilityNodeInfoUtils.shouldFocusNode(lastFocusedNode)) {
        return lastFocusedNode;
      }
    }

    if (root == null) {
      return null;
    }

    @Nullable
    NodePathDescription nodePath = focusActionRecord.getNodePathDescription(); // Not owner
    @Nullable
    AccessibilityNodeInfoCompat nodeAtSamePosition =
        (nodePath == null) ? null : nodePath.findNodeToRefocus(root, focusFinder);
    if ((nodeAtSamePosition != null)
        && AccessibilityNodeInfoUtils.shouldFocusNode(nodeAtSamePosition)) {
      AccessibilityNodeInfoCompat returnNode = nodeAtSamePosition;
      nodeAtSamePosition = null;
      return returnNode;
    }

    return null;
  }

  @Override
  public int hashCode() {
    return Objects.hash(actionTime, focusedNode, nodePathDescription, extraInfo);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof FocusActionRecord)) {
      return false;
    }
    FocusActionRecord otherRecord = (FocusActionRecord) other;
    return (focusedNode.equals(otherRecord.focusedNode))
        && (nodePathDescription.equals(otherRecord.nodePathDescription))
        && (extraInfo.equals(otherRecord.extraInfo))
        && (actionTime == otherRecord.actionTime)
        && Objects.equals(uniqueId, ((FocusActionRecord) other).uniqueId);
  }

  public boolean focusedNodeEquals(AccessibilityNodeInfoCompat targetNode) {
    if (focusedNode == null || targetNode == null) {
      return false;
    }

    // Unique id (if it exists) dominates the focus record comparison.
    String targetUniqueId = compoundPackageNameAndUniqueId(targetNode);
    if (!Objects.equals(uniqueId, targetUniqueId)) {
      return false;
    } else if (uniqueId != null) {
      return true;
    }

    return (focusedNode == targetNode) || focusedNode.equals(targetNode);
  }

  @Override
  public String toString() {
    return "FocusActionRecord: \n    "
        + "node="
        + toStringShort(focusedNode)
        + "\n    "
        + "time="
        + actionTime
        + "\n    "
        + "extraInfo="
        + extraInfo.toString()
        + "\n    "
        + "uniqueId="
        + uniqueId;
  }
}
