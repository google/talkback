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

import android.os.SystemClock;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import java.util.Collection;
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
  private final NodePathDescription nodePathDescription;
  /** Extra information about source focus action. */
  private final FocusActionInfo extraInfo;

  /**
   * Constructs a FocusActionRecord.
   *
   * <p><strong>Note:</strong>Caller is responsible for recycling the {@code focusedNode}.
   *
   * @param focusedNode Node being accessibility focused.
   * @param extraInfo Extra information defined by action performer.
   * @param actionTime Time when focus action happens. Got by {@link SystemClock#uptimeMillis()}.
   */
  public FocusActionRecord(
      AccessibilityNodeInfoCompat focusedNode, FocusActionInfo extraInfo, long actionTime) {
    this.focusedNode = AccessibilityNodeInfoUtils.obtain(focusedNode);
    nodePathDescription = NodePathDescription.obtain(focusedNode);
    this.extraInfo = extraInfo;
    this.actionTime = actionTime;
  }

  /**
   * Constructs FocusActionRecord. Used internally by {@link #copy(FocusActionRecord)}. Avoid
   * generating {@link NodePathDescription} from {@code focusedNode}.
   */
  private FocusActionRecord(
      AccessibilityNodeInfoCompat focusedNode,
      NodePathDescription nodePathDescription,
      FocusActionInfo extraInfo,
      long actionTime) {
    this.focusedNode = AccessibilityNodeInfoUtils.obtain(focusedNode);
    this.nodePathDescription = nodePathDescription;
    this.extraInfo = extraInfo;
    this.actionTime = actionTime;
  }

  /**
   * Returns an instance of the focused node.
   *
   * <p><strong>Caller is responsible for recycling the node after use.</strong>
   */
  public AccessibilityNodeInfoCompat getFocusedNode() {
    return AccessibilityNodeInfoUtils.obtain(focusedNode);
  }

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

  /** Returns the instance of focused node back into reuse. */
  public void recycle() {
    AccessibilityNodeInfoUtils.recycleNodes(focusedNode);
  }

  /** Returns a copied instance of another FocusActionRecord. */
  public static FocusActionRecord copy(FocusActionRecord record) {
    if (record == null) {
      return null;
    }
    return new FocusActionRecord(
        AccessibilityNodeInfoUtils.obtain(record.focusedNode),
        record.nodePathDescription,
        record.extraInfo,
        record.actionTime);
  }

  /** Recycles a collection of record instances. */
  public static void recycle(Collection<FocusActionRecord> records) {
    if (records == null) {
      return;
    }
    for (FocusActionRecord record : records) {
      if (record != null) {
        record.recycle();
      }
    }
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
        && (actionTime == otherRecord.actionTime);
  }

  public boolean focusedNodeEquals(AccessibilityNodeInfoCompat targetNode) {
    if (focusedNode == null || targetNode == null) {
      return false;
    }
    return (focusedNode == targetNode) || focusedNode.equals(targetNode);
  }

  @Override
  public String toString() {
    return "FocusActionRecord: \n"
        + "node="
        + focusedNode.toString()
        + "\n"
        + "time="
        + actionTime
        + "\n"
        + "extraInfo="
        + extraInfo.toString();
  }
}
