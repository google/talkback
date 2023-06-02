/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.uiunderstanding;

import android.os.Bundle;
import android.text.InputType;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.TouchDelegateInfoCompat;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Node representation in a tree {@link Snapshot}. Node reprents roughly a view within a window. The
 * abstracted interface allows implementation freedom.
 *
 * <p>Interface is written in java to make integration easier.
 */
public interface SnapshotView extends Node {
  /**
   * Mutable implementation of {@link SnapshotView}. This is useful for building the tree. Immutable
   * object {@link SnapshotView} is immutable and will not become mutable by simply casting to this
   * class. Rather, only way to create this mutable instance is by copying existing immutable tree.
   */
  interface Mutable extends SnapshotView {

    /** Adds a new child. */
    void addChild(@NonNull SnapshotView toAdd);

    /** Replace a child of the current node. */
    void replaceChild(@NonNull SnapshotView toRemove, @NonNull SnapshotView toAdd);

    /** Set a children to the node. Affects {@link #getChildren()} */
    void setChildren(@NonNull List<SnapshotView> children);

    /** Set a parent to the node. Affects {@link #getParent()} */
    void setParent(@Nullable SnapshotView parent);

    /** Set a parent to the node. Affects {@link #getLabelFor()} */
    void setLabelFor(@Nullable SnapshotView labelFor);

    /** Set a parent to the node. Affects {@link #getLabeledBy()} */
    void setLabeledBy(@Nullable SnapshotView labeledBy);

    /** Set a parent to the node. Affects {@link #getAfter()} */
    void setAfter(@Nullable SnapshotView after);

    /** Set a parent to the node. Affects {@link #getBefore()} */
    void setBefore(@Nullable SnapshotView before);

    /**
     * Set a parent to the node. The call is noop if the view is backed by implementation other than
     * AccessibilityNodeInfo. (e.g. ui-element protobuf)
     */
    void setRaw(@NonNull AccessibilityNodeInfoCompat nodeInfo);

    /** Set a parent to the node. Affects {@link #getAnchoredWindows()} */
    void setAnchoredWindows(@Nullable List<SnapshotWindow> windows);

    /** Set a parent to the node. Affects {@link #getRootWindow()} */
    void setRootWindow(@NonNull SnapshotWindow rootWindow);
  }

  /** Returns snapshot of all windows and views. */
  @NonNull
  default Snapshot getSnapshot() {
    return getRootWindow().getSnapshot();
  }

  /**
   * Returns a copy of a list of children the view contains. For iterating without creating a new
   * list, try {@link #getChild(int)} and {@link #getChildCount()}.
   */
  default @NonNull ImmutableList<SnapshotView> getChildren() {
    ImmutableList.Builder<SnapshotView> children = ImmutableList.builder();
    for (int c = 0; c < getChildCount(); ++c) {
      children.add(getChild(c));
    }
    return children.build();
  }

  /** Returns the child at index. Returns null if index is out of bounds. */
  @Nullable
  default SnapshotView getChild(int index) {
    return null;
  }

  /** Returns number of children in the list. */
  default int getChildCount() {
    return 0;
  }

  /** Returns the parent. */
  @Nullable
  default SnapshotView getParent() {
    return null;
  }

  /** Returns a child-node that may represent either a View or a Window. */
  @Override
  @Nullable
  default Node getChildNode(int index) {
    // Order child-views before anchored-windows.
    return (index < getChildCount()) ? getChild(index) : getAnchoredWindow(index - getChildCount());
  }

  @Override
  default int getChildNodeCount() {
    // Include child-views and anchored-windows.
    return getChildCount() + getAnchoredWindowCount();
  }

  @Override
  @Nullable
  default Node getParentNode() {
    // Prefer parent-view over root-window, because ancestor-views are lower in the node-tree.
    @Nullable SnapshotView parentView = getParent();
    return (parentView == null) ? getRootWindow() : parentView;
  }

  /** Returns the node labeled by this node. */
  @Nullable
  default SnapshotView getLabelFor() {
    return null;
  }

  /** Returns the node that labels this node. */
  @Nullable
  default SnapshotView getLabeledBy() {
    return null;
  }

  /**
   * This node comes after the returned node, similar to {@link
   * AccessibilityNodeInfo#getTraversalAfter()}
   */
  @Nullable
  default SnapshotView getAfter() {
    return null;
  }

  /**
   * This node comes before the returned node, similar to {@link
   * AccessibilityNodeInfo#getTraversalBefore()}
   */
  @Nullable
  default SnapshotView getBefore() {
    return null;
  }

  /** Performs AccessibilityNodeInfoCompat.ACTION_* on node, and returns success. */
  default boolean performAction(int action) {
    return performAction(action, /* bundle= */ null, /* event= */ null);
  }

  /** Performs AccessibilityNodeInfoCompat.ACTION_* on node, and returns success. */
  default boolean performAction(int action, @NonNull Bundle bundle) {
    return performAction(action, bundle, /* event= */ null);
  }

  /** Performs AccessibilityNodeInfoCompat.ACTION_* on node, and returns success. */
  default boolean performAction(int action, @NonNull Event event) {
    return performAction(action, /* bundle= */ null, event);
  }

  /** Performs AccessibilityNodeInfoCompat.ACTION_* on node, and returns success. */
  default boolean performAction(int action, @Nullable Bundle bundle, @Nullable Event event) {
    @Nullable EventSource eventSource = getRootWindow().getSnapshot().getEventSource();
    return (eventSource != null) && eventSource.performAction(this, action, bundle, event);
  }

  /** Returns target-view refreshed from app, in new snapshot refreshed from AccessibilityCache. */
  @Nullable
  default SnapshotView refresh() {
    return null;
  }

  /** Returns whether this-node & other-node represent the same View. */
  default boolean equalsRaw(@NonNull AccessibilityNodeInfoCompat other) {
    return false;
  }

  /** Returns the root node of the window. */
  @Nullable
  default SnapshotView getRoot() {
    return getRootWindow().getRoot();
  }

  /** Returns the anchored window at index. Returns null of index is out of bounds. */
  @Nullable
  default SnapshotWindow getAnchoredWindow(int index) {
    return null;
  }

  /** Returns the number of anchored windows the view holds. */
  default int getAnchoredWindowCount() {
    return 0;
  }

  /**
   * Returns a copy of a list of windows the view is anchored.
   *
   * <p>To iterate without creating a new list, try {@link #getAnchoredWindow(int)} and {@link
   * #getAnchoredWindowCount()}.
   */
  @NonNull
  default ImmutableList<SnapshotWindow> getAnchoredWindows() {
    ImmutableList.Builder<SnapshotWindow> windows = ImmutableList.builder();
    for (int c = 0; c < getAnchoredWindowCount(); ++c) {
      windows.add(getAnchoredWindow(c));
    }
    return windows.build();
  }

  /** See {@link SnapshotWindow} that the view is from. */
  @NonNull
  SnapshotWindow getRootWindow();

  /** See {@link AccessibilityNodeInfo#canOpenPopup()} */
  default boolean canOpenPopup() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#getActionList()} */
  default List<AccessibilityActionCompat> getActionList() {
    return ImmutableList.of();
  }

  /** See {@link AccessibilityNodeInfoCompat#getClassName()} */
  @Nullable
  default CharSequence getClassName() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getCollectionInfo()} */
  @Nullable
  default CollectionInfoCompat getCollectionInfo() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getCollectionItemInfo()} */
  @Nullable
  default CollectionItemInfoCompat getCollectionItemInfo() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getContentDescription()} */
  @Nullable
  default CharSequence getContentDescription() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getDrawingOrder()} */
  default int getDrawingOrder() {
    return 0;
  }

  /** See {@link AccessibilityNodeInfoCompat#getError()} */
  @Nullable
  default CharSequence getError() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getHintText()} */
  @Nullable
  default CharSequence getHintText() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getInputType()} */
  default int getInputType() {
    return InputType.TYPE_NULL;
  }

  /** See {@link AccessibilityNodeInfoCompat#getLiveRegion()} */
  default int getLiveRegion() {
    return ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE;
  }

  /** See {@link AccessibilityNodeInfoCompat#getMaxTextLength()} */
  default int getMaxTextLength() {
    return -1;
  }

  /** See {@link AccessibilityNodeInfoCompat#getMovementGranularities()} */
  default int getMovementGranularities() {
    return 0;
  }

  /** See {@link AccessibilityNodeInfoCompat#getPackageName()} */
  @Nullable
  default CharSequence getPackageName() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getPaneTitle()} */
  @Nullable
  default CharSequence getPaneTitle() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getRangeInfo()} */
  @Nullable
  default RangeInfoCompat getRangeInfo() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getRoleDescription()} */
  // Not yet in base
  @Nullable
  default CharSequence getRoleDescription() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getStateDescription()} */
  @Nullable
  default CharSequence getStateDescription() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getText()} */
  @Nullable
  default CharSequence getText() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getTextSelectionEnd()} */
  default int getTextSelectionEnd() {
    return -1;
  }

  /** See {@link AccessibilityNodeInfoCompat#getTextSelectionStart()} */
  default int getTextSelectionStart() {
    return -1;
  }

  /** See {@link AccessibilityNodeInfoCompat#getTooltipText()} */
  @Nullable
  default CharSequence getTooltipText() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getTouchDelegateInfo()} */
  @Nullable
  default TouchDelegateInfoCompat getTouchDelegateInfo() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getViewIdResourceName()} */
  @Nullable
  default String getViewIdResourceName() {
    return null;
  }

  /** See {@link AccessibilityNodeInfoCompat#getWindowId()} */
  default int getWindowId() {
    return SnapshotWindow.INVALID_WINDOW_ID;
  }

  /** See {@link AccessibilityNodeInfoCompat#isAccessibilityFocused()} */
  default boolean isAccessibilityFocused() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isCheckable()} */
  default boolean isCheckable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isChecked()} */
  default boolean isChecked() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isClickable()} */
  default boolean isClickable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isContentInvalid()} */
  default boolean isContentInvalid() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isContextClickable()} */
  default boolean isContextClickable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isDismissable()} */
  default boolean isDismissable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isEditable()} */
  default boolean isEditable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isEnabled()} */
  default boolean isEnabled() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isFocusable()} */
  default boolean isFocusable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isFocused()} */
  default boolean isFocused() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isHeading()} */
  default boolean isHeading() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isImportantForAccessibility()} */
  default boolean isImportantForAccessibility() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isLongClickable()} */
  default boolean isLongClickable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isMultiLine()} */
  default boolean isMultiLine() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isPassword()} */
  default boolean isPassword() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isScreenReaderFocusable()} */
  default boolean isScreenReaderFocusable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isScrollable()} */
  default boolean isScrollable() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isSelected()} */
  default boolean isSelected() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isShowingHintText()} */
  default boolean isShowingHintText() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isTextEntryKey()} */
  default boolean isTextEntryKey() {
    return false;
  }

  /** See {@link AccessibilityNodeInfoCompat#isVisibleToUser()} */
  default boolean isVisibleToUser() {
    return false;
  }
}
