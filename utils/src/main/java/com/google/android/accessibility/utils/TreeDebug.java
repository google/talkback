/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Util class to help debug Node trees. */
public class TreeDebug {

  public static final String TAG = "TreeDebug";

  /** Logs the layout hierarchy of node trees for given list of windows. */
  public static void logNodeTrees(List<AccessibilityWindowInfo> windows) {
    if (windows == null) {
      return;
    }
    LogUtils.v(TAG, "------------Node tree------------");
    for (AccessibilityWindowInfo window : windows) {
      if (window == null) {
        continue;
      }
      // TODO: Filter and print useful window information.
      LogUtils.v(TAG, "Window: %s", window);
      AccessibilityNodeInfoCompat root =
          AccessibilityNodeInfoUtils.toCompat(AccessibilityWindowInfoUtils.getRoot(window));
      logNodeTree(root);
      AccessibilityNodeInfoUtils.recycleNodes(root);
    }
  }

  /** Logs the layout hierarchy of node tree for using the input node as the root. */
  public static void logNodeTree(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return;
    }

    HashSet<AccessibilityNodeInfoCompat> seen = new HashSet<>();
    logNodeTree(AccessibilityNodeInfoCompat.obtain(node), "", seen);
    for (AccessibilityNodeInfoCompat n : seen) {
      n.recycle();
    }
  }

  private static void logNodeTree(
      AccessibilityNodeInfoCompat node, String indent, HashSet<AccessibilityNodeInfoCompat> seen) {
    if (!seen.add(node)) {
      LogUtils.v(TAG, "Cycle: %d", node.hashCode());
      return;
    }

    // Include the hash code as a "poor man's" id, knowing that it
    // might not always be unique.
    LogUtils.v(TAG, "%s(%d)%s", indent, node.hashCode(), nodeDebugDescription(node));

    indent += "  ";
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child == null) {
        LogUtils.v(TAG, "%sCouldn't get child %d", indent, i);
        continue;
      }

      logNodeTree(child, indent, seen);
    }
  }

  private static void appendSimpleName(StringBuilder sb, CharSequence fullName) {
    int dotIndex = TextUtils.lastIndexOf(fullName, '.');
    if (dotIndex < 0) {
      dotIndex = 0;
    }

    sb.append(fullName, dotIndex, fullName.length());
  }

  /** Gets a description of the properties of a node. */
  public static CharSequence nodeDebugDescription(AccessibilityNodeInfoCompat node) {
    StringBuilder sb = new StringBuilder();
    sb.append(node.getWindowId());

    if (node.getClassName() != null) {
      appendSimpleName(sb, node.getClassName());
    } else {
      sb.append("??");
    }

    if (!node.isVisibleToUser()) {
      sb.append(":invisible");
    }

    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    sb.append(":");
    sb.append("(")
        .append(rect.left)
        .append(", ")
        .append(rect.top)
        .append(" - ")
        .append(rect.right)
        .append(", ")
        .append(rect.bottom)
        .append(")");

    if (!TextUtils.isEmpty(node.getPaneTitle())) {
      sb.append(":PANE{");
      sb.append(node.getPaneTitle());
      sb.append("}");
    }

    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
    if (nodeText != null) {
      sb.append(":TEXT{");
      sb.append(nodeText.toString().trim());
      sb.append("}");
    }

    if (node.getContentDescription() != null) {
      sb.append(":CONTENT{");
      sb.append(node.getContentDescription().toString().trim());
      sb.append("}");
    }

    if (AccessibilityNodeInfoUtils.getState(node) != null) {
      sb.append(":STATE{");
      sb.append(AccessibilityNodeInfoUtils.getState(node).toString().trim());
      sb.append("}");
    }
    // Views that inherit Checkable can have its own state description and the log already covered
    // by above SD, but for some views that are not Checkable but have checked status, like
    // overriding by AccessibilityDelegate, we should also log it.
    if (node.isCheckable()) {
      sb.append(":");
      if (node.isChecked()) {
        sb.append("checked");
      } else {
        sb.append("not checked");
      }
    }

    int actions = node.getActions();
    if (actions != 0) {
      sb.append("(action:");
      if ((actions & AccessibilityNodeInfoCompat.ACTION_FOCUS) != 0) {
        sb.append("FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) != 0) {
        sb.append("A11Y_FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) != 0) {
        sb.append("CLEAR_A11Y_FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) != 0) {
        sb.append("SCROLL_BACKWARD/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) != 0) {
        sb.append("SCROLL_FORWARD/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_CLICK) != 0) {
        sb.append("CLICK/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_LONG_CLICK) != 0) {
        sb.append("LONG_CLICK/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_EXPAND) != 0) {
        sb.append("EXPAND/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_COLLAPSE) != 0) {
        sb.append("COLLAPSE/");
      }
      sb.setLength(sb.length() - 1);
      sb.append(")");
    }

    if (node.isFocusable()) {
      sb.append(":focusable");
    }
    if (node.isScreenReaderFocusable()) {
      sb.append(":screenReaderfocusable");
    }

    if (node.isFocused()) {
      sb.append(":focused");
    }

    if (node.isSelected()) {
      sb.append(":selected");
    }

    if (node.isScrollable()) {
      sb.append(":scrollable");
    }

    if (node.isClickable()) {
      sb.append(":clickable");
    }

    if (node.isLongClickable()) {
      sb.append(":longClickable");
    }

    if (node.isAccessibilityFocused()) {
      sb.append(":accessibilityFocused");
    }
    if (AccessibilityNodeInfoUtils.supportsTextLocation(node)) {
      sb.append(":supportsTextLocation");
    }
    if (!node.isEnabled()) {
      sb.append(":disabled");
    }

    if (node.getCollectionInfo() != null) {
      sb.append(":collection");
      sb.append("#R");
      sb.append(node.getCollectionInfo().getRowCount());
      sb.append("C");
      sb.append(node.getCollectionInfo().getColumnCount());
    }

    if (AccessibilityNodeInfoUtils.isHeading(node)) {
      sb.append(":heading");
    } else if (node.getCollectionItemInfo() != null) {
      sb.append(":item");
    }
    if (node.getCollectionItemInfo() != null) {
      sb.append("#r");
      sb.append(node.getCollectionItemInfo().getRowIndex());
      sb.append("c");
      sb.append(node.getCollectionItemInfo().getColumnIndex());
    }

    return sb.toString();
  }

  /** Logs the traversal order of node trees for given list of windows. */
  public static void logOrderedTraversalTree(List<AccessibilityWindowInfo> windows) {
    if (windows == null) {
      return;
    }
    LogUtils.v(TAG, "------------Node tree traversal order------------");
    for (AccessibilityWindowInfo window : windows) {
      if (window == null) {
        continue;
      }
      LogUtils.v(TreeDebug.TAG, "Window: %s", window);
      AccessibilityNodeInfoCompat root =
          AccessibilityNodeInfoUtils.toCompat(AccessibilityWindowInfoUtils.getRoot(window));
      logOrderedTraversalTree(root);
      AccessibilityNodeInfoUtils.recycleNodes(root);
    }
  }

  /** Logs the traversal order of node tree for using the input node as the root. */
  private static void logOrderedTraversalTree(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return;
    }
    OrderedTraversalStrategy orderTraversalStrategy = new OrderedTraversalStrategy(node);
    orderTraversalStrategy.dumpTree();
    orderTraversalStrategy.recycle();
  }
}
