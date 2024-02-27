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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

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
  private final @NonNull AccessibilityNodeInfoCompat focusedNode;
  /** Describes how to find focused node from root node. */
  private final @NonNull NodePathDescription nodePathDescription;
  /** Extra information about source focus action. */
  private final @NonNull FocusActionInfo extraInfo;
  /** Unique id for the node */
  private final String uniqueId;

  private static @Nullable String compoundPackageNameAndUniqueId(
      @Nullable AccessibilityNodeInfoCompat nodeInfo) {
    if (nodeInfo == null) {
      return null;
    }
    String compoundId = nodeInfo.getUniqueId();
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
      @NonNull FocusActionInfo extraInfo,
      long actionTime) {
    this.focusedNode = focusedNode;
    nodePathDescription = NodePathDescription.obtain(focusedNode);
    this.extraInfo = extraInfo;
    this.actionTime = actionTime;
    this.uniqueId = compoundPackageNameAndUniqueId(focusedNode);
  }

  /** Constructs FocusActionRecord. Used internally by {@link #copy(FocusActionRecord)}. */
  private FocusActionRecord(
      @NonNull AccessibilityNodeInfoCompat focusedNode,
      @NonNull NodePathDescription nodePathDescription,
      @NonNull FocusActionInfo extraInfo,
      long actionTime,
      String uniqueId) {
    this.focusedNode = focusedNode;
    this.nodePathDescription = new NodePathDescription(nodePathDescription);
    this.extraInfo = extraInfo;
    this.actionTime = actionTime;
    this.uniqueId = uniqueId;
  }

  /** Returns an instance of the focused node. */
  public @NonNull AccessibilityNodeInfoCompat getFocusedNode() {
    return focusedNode;
  }

  /** Returns reference to node-path. */
  public @NonNull NodePathDescription getNodePathDescription() {
    return nodePathDescription;
  }

  /** Returns extra information of the focus action. */
  public @NonNull FocusActionInfo getExtraInfo() {
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
   * Returns true when the unique id are identical and not both null.
   *
   * @param uniqueId existing uniqueId.
   * @param node Accessibility node to check its own uniqueId
   */
  private static boolean checkUniqueIdIdentical(
      @NonNull String uniqueId, AccessibilityNodeInfoCompat node) {
    return uniqueId.equals(compoundPackageNameAndUniqueId(node));
  }

  /**
   * Returns the last focused node in {@code window} if it's still valid on screen with same unique
   * identifier, otherwise returns focusable node with the same position.
   */
  @Nullable
  public static AccessibilityNodeInfoCompat getFocusableNodeFromFocusRecord(
      @Nullable AccessibilityNodeInfoCompat root,
      @NonNull FocusFinder focusFinder,
      @NonNull FocusActionRecord focusActionRecord) {
    AccessibilityNodeInfoCompat lastFocusedNode = focusActionRecord.getFocusedNode();

    // When looking up the focusable node by focus record, the refocus candidate(last focused node)
    // should
    // 1. Keep valid (after refresh) and
    // 2. Has identical unique id
    // 3. The refreshed node is focusable.
    @Nullable String uniqueId = focusActionRecord.getUniqueId();
    if (lastFocusedNode.refresh() && AccessibilityNodeInfoUtils.shouldFocusNode(lastFocusedNode)) {
      if ((uniqueId == null && lastFocusedNode.getUniqueId() == null)
          || (uniqueId != null && checkUniqueIdIdentical(uniqueId, lastFocusedNode))) {
        return lastFocusedNode;
      }
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

    @Nullable NodePathDescription nodePath =
        focusActionRecord.getNodePathDescription(); // Not owner
    @Nullable AccessibilityNodeInfoCompat nodeAtSamePosition =
        (nodePath == null) ? null : nodePath.findNodeToRefocus(root, focusFinder);
    if ((nodeAtSamePosition != null)
        && AccessibilityNodeInfoUtils.shouldFocusNode(nodeAtSamePosition)) {
      return nodeAtSamePosition;
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
