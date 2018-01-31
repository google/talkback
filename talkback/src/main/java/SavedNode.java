/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback;

import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.input.CursorGranularity;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SavedNode implements AccessibilityEventListener {
  private static final int NODE_REGULAR = 0;
  private static final int NODE_ANCHOR = 1;
  private static final int NODE_ANCHORED = 2;

  /** Event types that are handled by SavedNode. */
  private static final int MASK_EVENTS_HANDLED_BY_SAVED_NODE =
      AccessibilityEvent.TYPE_WINDOWS_CHANGED | AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED;

  @IntDef({NODE_REGULAR, NODE_ANCHOR, NODE_ANCHORED})
  @Retention(RetentionPolicy.SOURCE)
  private @interface SavedNodeType {}

  /** The node that previously had accessibility focus. */
  private AccessibilityNodeInfoCompat mNode;
  /** The anchor node of the window of mNode. */
  private AccessibilityNodeInfoCompat mAnchor;

  private Selection mSelection;
  private CursorGranularity mGranularity;
  private @SavedNodeType int mSavedNodeType;
  private Map<AccessibilityNodeInfoCompat, Selection> mSelectionCache = new HashMap<>();

  public void saveNodeState(
      AccessibilityNodeInfoCompat node, CursorGranularity granularity, TalkBackService service) {
    if (node == null) {
      return;
    }

    // Anchors and anchored nodes can only exist on >= N.
    if (BuildVersionUtils.isAtLeastN()) {
      // Does the current node have a window anchored to it?
      WindowManager windowManager = new WindowManager(service);
      if (windowManager.getAnchoredWindow(node) != null) {
        mSavedNodeType = NODE_ANCHOR;
        mNode = AccessibilityNodeInfoCompat.obtain(node);
        mAnchor = null;
        mGranularity = granularity;
        mSelection = findSelectionForNode(node);
        return;
      }

      // Is the current node in a window anchored to another node?
      AccessibilityNodeInfoCompat anchor = AccessibilityNodeInfoUtils.getAnchor(node);
      if (anchor != null) {
        mSavedNodeType = NODE_ANCHORED;
        mNode = AccessibilityNodeInfoCompat.obtain(node);
        mAnchor = anchor;
        mGranularity = granularity;
        mSelection = findSelectionForNode(anchor);
        return;
      }
    }

    // The node is neither anchored nor an anchor.
    mSavedNodeType = NODE_REGULAR;
    mNode = AccessibilityNodeInfoCompat.obtain(node);
    mAnchor = null;
    mGranularity = granularity;
    mSelection = findSelectionForNode(node);
  }

  public AccessibilityNodeInfoCompat getNode() {
    return mNode;
  }

  public AccessibilityNodeInfoCompat getAnchor() {
    return mAnchor;
  }

  public CursorGranularity getGranularity() {
    return mGranularity;
  }

  public Selection getSelection() {
    return mSelection;
  }

  private void clear() {
    mNode = null;
    mSelection = null;
    mGranularity = null;
  }

  private void clearCache() {
    List<AccessibilityNodeInfoCompat> toRemove = new ArrayList<>();
    for (AccessibilityNodeInfoCompat node : mSelectionCache.keySet()) {
      boolean refreshed = refreshNode(node);
      if (!refreshed || !node.isVisibleToUser()) {
        toRemove.add(node);
      }
    }

    for (AccessibilityNodeInfoCompat node : toRemove) {
      mSelectionCache.remove(node);
      node.recycle();
    }
  }

  private boolean refreshNode(AccessibilityNodeInfoCompat node) {
    return ((AccessibilityNodeInfo) node.getInfo()).refresh();
  }

  private Selection findSelectionForNode(AccessibilityNodeInfoCompat targetNode) {
    if (targetNode == null) {
      return null;
    }

    return mSelectionCache.get(targetNode);
  }

  public void restoreTextAndSelection(EventId eventId) {
    switch (mSavedNodeType) {
      case NODE_REGULAR:
        {
          if (mNode != null) {
            restoreSelection(mNode, eventId);
          }
        }
        break;
      case NODE_ANCHOR:
        {
          if (mNode != null) {
            // Restore text on the current node so that its popup window appears again.
            restoreText(mNode, eventId);
            restoreSelection(mNode, eventId);
          }
        }
        break;
      case NODE_ANCHORED:
        {
          if (mAnchor != null) {
            // Restore text on anchor so that its popup (containing current node) appears.
            restoreText(mAnchor, eventId);
            restoreSelection(mAnchor, eventId);
          }
        }
        break;
      default: // fall out
    }
  }

  private void restoreText(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (node.getText() != null) {
      Bundle args = new Bundle();
      args.putCharSequence(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, node.getText());
      PerformActionUtils.performAction(
          node, AccessibilityNodeInfoCompat.ACTION_SET_TEXT, args, eventId);
    }
  }

  private void restoreSelection(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (mSelection != null) {
      Bundle args = new Bundle();
      args.putInt(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, mSelection.start);
      args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, mSelection.end);
      PerformActionUtils.performAction(
          node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
    }
  }

  public void recycle() {
    if (mNode != null) {
      mNode.recycle();
      mNode = null;
    }
    if (mAnchor != null) {
      mAnchor.recycle();
      mAnchor = null;
    }
    clear();
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_SAVED_NODE;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        clearCache();
        break;
      case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
          AccessibilityNodeInfoCompat copyNode =
              AccessibilityNodeInfoUtils.toCompat(AccessibilityNodeInfo.obtain(source));
          Selection selection = new Selection(event.getFromIndex(), event.getToIndex());
          mSelectionCache.put(copyNode, selection);
          source.recycle();
        }
        break;
      default: // fall out
    }
  }

  public static class Selection {
    public final int start;
    public final int end;

    public Selection(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }
}
