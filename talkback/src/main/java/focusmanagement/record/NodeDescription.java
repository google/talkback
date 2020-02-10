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

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import android.text.TextUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/** Describes a single {@link AccessibilityNodeInfoCompat} with some immutable information. */
public class NodeDescription {

  static final int UNDEFINED_INDEX = -1;

  /** Indicates the index directly got from parent.getChild(index). */
  static final int INDEX_TYPE_RAW = 0;
  /**
   * Indicates the row/column index got from {@link
   * android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo}.
   */
  static final int INDEX_TYPE_COLLECTION = 1;

  /** Types of children index. */
  @IntDef({INDEX_TYPE_RAW, INDEX_TYPE_COLLECTION})
  @Retention(RetentionPolicy.SOURCE)
  @interface IndexType {}

  // Immutable identity information from View.

  final CharSequence className;
  final String viewIdResourceName;

  // Index information

  @IndexType final int indexType;
  // Index from AccessibilityNodeInfo.getChild(index).
  final int rowIndex;
  // Index from CollectionItemInfo.
  final int columnIndex;
  final int rawIndexInParent;

  // Mutable information from AccessibilityNodeInfoCompat.
  // The information stays immutable as long as the view stays visible on screen.
  // Ideally we want AccessibilityNodeInfo.mSourceNodeId but that is an hidden API. We work around
  // this by using hashCode(), which is generated based on mSourceNodeId.
  final int nodeInfoHashCode;

  @VisibleForTesting
  NodeDescription(
      CharSequence className,
      String viewIdResourceName,
      int indexType,
      int rowIndex,
      int columnIndex,
      int rawIndexInParent,
      int nodeInfoHashCode) {
    this.className = className;
    this.viewIdResourceName = viewIdResourceName;
    this.indexType = indexType;
    this.rowIndex = rowIndex;
    this.columnIndex = columnIndex;
    this.rawIndexInParent = rawIndexInParent;
    this.nodeInfoHashCode = nodeInfoHashCode;
  }

  static NodeDescription obtain(AccessibilityNodeInfoCompat node) {
    CollectionItemInfoCompat itemInfo = node.getCollectionItemInfo();
    return new NodeDescription(
        node.getClassName(),
        node.getViewIdResourceName(),
        itemInfo == null ? INDEX_TYPE_RAW : INDEX_TYPE_COLLECTION,
        itemInfo == null ? UNDEFINED_INDEX : itemInfo.getRowIndex(),
        itemInfo == null ? UNDEFINED_INDEX : itemInfo.getColumnIndex(),
        getRawIndexInParent(node),
        node.hashCode());
  }

  public boolean identityMatches(AccessibilityNodeInfoCompat node) {
    return (node != null)
        && TextUtils.equals(node.getClassName(), className)
        && TextUtils.equals(node.getViewIdResourceName(), viewIdResourceName);
  }

  public boolean indexMatches(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    if (indexType == INDEX_TYPE_COLLECTION) {
      CollectionItemInfoCompat itemInfo = node.getCollectionItemInfo();
      return (itemInfo != null)
          && (itemInfo.getRowIndex() == rowIndex)
          && (itemInfo.getColumnIndex() == columnIndex);
    } else {
      AccessibilityNodeInfoCompat parent = null;
      AccessibilityNodeInfoCompat child = null;
      try {
        parent = node.getParent();
        if (parent == null || parent.getChildCount() <= rawIndexInParent) {
          return false;
        }
        child = parent.getChild(rawIndexInParent);
        return node.equals(child);

      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(parent, child);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NodeDescription)) {
      return false;
    }

    NodeDescription that = (NodeDescription) o;
    // nodeInfoHashCode is mutable as the View goes off/on screen, thus we don't compare it here.
    return (indexType == that.indexType)
        && (rowIndex == that.rowIndex)
        && (columnIndex == that.columnIndex)
        && (rawIndexInParent == that.rawIndexInParent)
        && TextUtils.equals(className, that.className)
        && TextUtils.equals(viewIdResourceName, that.viewIdResourceName);
  }

  @Override
  public int hashCode() {
    // nodeInfoHashCode is mutable as the View goes off/on screen, thus we don't add it here.
    return Objects.hash(
        className, viewIdResourceName, indexType, rowIndex, columnIndex, rawIndexInParent);
  }

  private static int getRawIndexInParent(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return UNDEFINED_INDEX;
    }
    AccessibilityNodeInfoCompat parent = node.getParent();
    if (parent == null) {
      return UNDEFINED_INDEX;
    }

    for (int i = 0; i < parent.getChildCount(); i++) {
      AccessibilityNodeInfoCompat child = parent.getChild(i);
      try {
        if (node.equals(child)) {
          return i;
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(child);
      }
    }
    return UNDEFINED_INDEX;
  }
}
