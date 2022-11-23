/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorFocus;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.utils.TreeDebug;

/**
 * A debugging navigation mode that allows navigating through the currently active window's node
 * tree.
 */
public class TreeDebugNavigationMode implements NavigationMode {

  private static final String TAG = "TreeDebugNavigationMode";

  private static final BrailleCharacter DOTS_LETTER_B = new BrailleCharacter("12");
  private static final BrailleCharacter DOTS_LETTER_R = new BrailleCharacter("235");
  private static final BrailleCharacter DOTS_LETTER_P = new BrailleCharacter("234");

  private final CellsContentConsumer cellsContentConsumer;
  private final FeedbackManager feedbackManager;
  private final BehaviorFocus behaviorFocus;

  // TODO: Could keep current nodes in inactive Windows in an LRU
  // cache to give a better user experience when switching
  // between windows.
  /** The node that is currently shown on the display and which navigation commands start at. */
  private AccessibilityNodeInfo currentNode;
  /**
   * The node of the accessibility event that was last observed. This may become the current node
   * under certain circumstances.
   */
  private AccessibilityNodeInfo pendingNode;

  public TreeDebugNavigationMode(
      CellsContentConsumer cellsContentConsumer,
      FeedbackManager feedbackManager,
      BehaviorFocus behaviorFocus) {
    this.cellsContentConsumer = cellsContentConsumer;
    this.feedbackManager = feedbackManager;
    this.behaviorFocus = behaviorFocus;
  }

  @Override
  public boolean onPanLeftOverflow() {
    return movePrevious();
  }

  @Override
  public boolean onPanRightOverflow() {
    return moveNext();
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    switch (event.getCommand()) {
      case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
        return movePreviousSibling();
      case BrailleInputEvent.CMD_NAV_LINE_NEXT:
        return moveNextSibling();
      case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
        return moveParent();
      case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
        return moveFirstChild();
      case BrailleInputEvent.CMD_ROUTE:
        return activateCurrent();
      case BrailleInputEvent.CMD_BRAILLE_KEY:
        if (event.getArgument() == DOTS_LETTER_B.toInt()) {
          showRect();
          return true;
        }
        if (event.getArgument() == DOTS_LETTER_R.toInt()) {
          setPendingNode(
              behaviorFocus.getAccessibilityFocusNode(/* fallbackOnRoot= */ true).unwrap());
          if (pendingNode == null) {
            return false;
          }
          makePendingNodeCurrent();
          displayCurrentNode();
          return true;
        }
        if (event.getArgument() == DOTS_LETTER_P.toInt()) {
          printNodes();
          return true;
        }
        break;
      default:
        return false;
    }
    return false;
  }

  @Override
  public void onActivate() {
    if (pendingNode != null) {
      makePendingNodeCurrent();
    }
    displayCurrentNode();
  }

  @Override
  public void onDeactivate() {
    setCurrentNode(null);
  }

  @Override
  public boolean onAccessibilityEvent(AccessibilityEvent event) {
    AccessibilityNodeInfo source = event.getSource();
    if (source == null) {
      return false;
    }
    boolean isNewWindow = false;
    if (currentNode == null || currentNode.getWindowId() != source.getWindowId()) {
      isNewWindow = true;
    }
    int t = event.getEventType();
    boolean isInterestingEventType = false;
    if (t == AccessibilityEvent.TYPE_VIEW_SELECTED
        || t == AccessibilityEvent.TYPE_VIEW_FOCUSED
        || t == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        || t != AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
      isInterestingEventType = true;
    }
    if (isNewWindow || isInterestingEventType) {
      setPendingNode(source);
      makePendingNodeCurrent();
      displayCurrentNode();
      return true;
    }
    return false;
  }

  private void setPendingNode(AccessibilityNodeInfo newNode) {
    pendingNode = newNode;
  }

  private void setCurrentNode(AccessibilityNodeInfo newNode) {
    currentNode = newNode;
  }

  private void makePendingNodeCurrent() {
    setCurrentNode(pendingNode);
    pendingNode = null;
  }

  private void displayCurrentNode() {
    if (currentNode == null) {
      cellsContentConsumer.setContent(new CellsContent("No Node"));
    } else {
      cellsContentConsumer.setContent(
          new CellsContent(
              TreeDebug.nodeDebugDescription(new AccessibilityNodeInfoCompat(currentNode))));
    }
  }

  private boolean movePreviousSibling() {
    if (currentNode == null) {
      return false;
    }
    return moveTo(getPreviousSibling(currentNode));
  }

  @Nullable
  private AccessibilityNodeInfo getPreviousSibling(AccessibilityNodeInfo from) {
    AccessibilityNodeInfo ret = null;
    AccessibilityNodeInfo parent = from.getParent();
    if (parent == null) {
      return null;
    }
    AccessibilityNodeInfo prev = null;
    AccessibilityNodeInfo cur = null;
    int childCount = parent.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      cur = parent.getChild(i);
      if (cur == null) {
        return null;
      }
      if (cur.equals(from)) {
        ret = prev;
        prev = null;
        return ret;
      }
      prev = cur;
      cur = null;
    }
    return ret;
  }

  private boolean moveNextSibling() {
    if (currentNode == null) {
      return false;
    }
    return moveTo(getNextSibling(currentNode));
  }

  @Nullable
  private AccessibilityNodeInfo getNextSibling(AccessibilityNodeInfo from) {
    AccessibilityNodeInfo parent = from.getParent();
    if (parent == null) {
      return null;
    }
    AccessibilityNodeInfo cur = null;
    int childCount = parent.getChildCount();
    for (int i = 0; i < childCount - 1; ++i) {
      cur = parent.getChild(i);
      if (cur == null) {
        return null;
      }
      if (cur.equals(from)) {
        return parent.getChild(i + 1);
      }
      cur = null;
    }
    return null;
  }

  private boolean moveParent() {
    if (currentNode == null) {
      return false;
    }
    AccessibilityNodeInfo parent = currentNode.getParent();
    return moveTo(parent, FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY);
  }

  private boolean moveFirstChild() {
    if (currentNode == null) {
      return false;
    }
    return moveTo(getFirstChild(currentNode), FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY);
  }

  @Nullable
  private AccessibilityNodeInfo getFirstChild(AccessibilityNodeInfo from) {
    if (from.getChildCount() < 1) {
      return null;
    }
    return from.getChild(0);
  }

  @Nullable
  private AccessibilityNodeInfo getLastChild(AccessibilityNodeInfo from) {
    if (from.getChildCount() < 1) {
      return null;
    }
    return from.getChild(from.getChildCount() - 1);
  }

  private boolean movePrevious() {
    if (currentNode == null) {
      return false;
    }
    AccessibilityNodeInfo target = null;
    int feedbackType = FeedbackManager.TYPE_NONE;
    AccessibilityNodeInfo prevSibling = getPreviousSibling(currentNode);
    if (prevSibling != null) {
      target = getLastDescendantDfs(prevSibling);
      if (target != null) {
        feedbackType = FeedbackManager.TYPE_NAVIGATE_INTO_HIERARCHY;
        prevSibling = null;
      } else {
        target = prevSibling;
      }
    }
    if (target == null) {
      target = currentNode.getParent();
      if (target != null) {
        feedbackType = FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY;
      }
    }
    return moveTo(target, feedbackType);
  }

  @Nullable
  private AccessibilityNodeInfo getLastDescendantDfs(AccessibilityNodeInfo from) {
    AccessibilityNodeInfo lastChild = getLastChild(from);
    if (lastChild == null) {
      return null;
    }
    while (true) {
      AccessibilityNodeInfo lastGrandChild = getLastChild(lastChild);
      if (lastGrandChild != null) {
        lastChild = lastGrandChild;
      } else {
        break;
      }
    }
    return lastChild;
  }

  private boolean moveNext() {
    if (currentNode == null) {
      return false;
    }
    int feedbackType = FeedbackManager.TYPE_NONE;
    AccessibilityNodeInfo target = getFirstChild(currentNode);
    if (target != null) {
      feedbackType = FeedbackManager.TYPE_NAVIGATE_INTO_HIERARCHY;
    } else {
      target = getNextSibling(currentNode);
    }
    if (target == null) {
      AccessibilityNodeInfo ancestor = currentNode.getParent();
      while (target == null && ancestor != null) {
        target = getNextSibling(ancestor);
        if (target == null) {
          AccessibilityNodeInfo temp = ancestor.getParent();
          ancestor = temp;
        } else {
          ancestor = null;
        }
      }
      if (target != null) {
        feedbackType = FeedbackManager.TYPE_NAVIGATE_OUT_OF_HIERARCHY;
      }
    }
    return moveTo(target, feedbackType);
  }

  private boolean moveTo(AccessibilityNodeInfo node, int feedbackType) {
    if (moveTo(node)) {
      feedbackManager.emitFeedback(feedbackType);
      return true;
    }
    return false;
  }

  private boolean moveTo(AccessibilityNodeInfo node) {
    if (node == null) {
      return false;
    }
    setCurrentNode(node);
    displayCurrentNode();
    return true;
  }

  private boolean activateCurrent() {
    if (currentNode == null) {
      return false;
    }
    boolean ret = currentNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    return ret;
  }

  private void showRect() {
    if (currentNode == null) {
      return;
    }
    Rect rect = new Rect();
    currentNode.getBoundsInScreen(rect);
    cellsContentConsumer.setContent(new CellsContent("b: " + rect));
  }

  /** Outputs the node tree from the current node using dfs preorder traversal. */
  private void printNodes() {
    if (currentNode == null) {
      BrailleDisplayLog.d(TAG, "No current node");
      return;
    }
    TreeDebug.logNodeTree(new AccessibilityNodeInfoCompat(currentNode));
  }
}
