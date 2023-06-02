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

import android.graphics.Region;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Node representation in a tree {@link Snapshot} that represents windows {@link
 * AccessibilityWindowInfo}.
 */
public interface SnapshotWindow extends Node {

  public static final int INVALID_WINDOW_ID = -1;
  public static final int INVALID_DISPLAY_ID = -1;
  public static final int INVALID_LAYER = -1;
  public static final int INVALID_TYPE = -1;

  /**
   * Mutable implementation of {@link SnapshotWindow} that is used for building and refining.
   * Immutable object {@link SnapshotWindow} is immutable and will not become mutable by simply
   * casting to this class. Rather, only way to create this mutable instance is by copying existing
   * immutable tree.
   */
  interface Mutable extends SnapshotWindow {

    /** Set roots that affects {@link #getRoot()} */
    void setRoot(@Nullable SnapshotView.Mutable root);

    /** Set metrics that affects {@link #getMetric()} */
    void setMetric(@NonNull SnapshotWindow.WindowMetric metric);

    /** Set a parent to the node. */
    void setParent(@Nullable SnapshotWindow.Mutable parent);

    /** Set children to the node. */
    void setChildren(@NonNull List<SnapshotWindow.Mutable> children);

    /** Add a child to the node. */
    void addChild(@NonNull SnapshotWindow.Mutable toAdd);

    /** Replace a child in the node. */
    void replaceChild(
        @NonNull SnapshotWindow.Mutable toRemove, @NonNull SnapshotWindow.Mutable toAdd);
  }

  /** Metric regarding the window. */
  class WindowMetric {
    /** Creation time of the root node and all of its children. */
    public final long creationTime;
    /** True if the tree contains loop. */
    public final boolean hasLoop;
    /** Window from which the snapshot is taken. */
    public final int windowId;
    /** Number of [SnapshotView] in the window. */
    public final int size;

    public WindowMetric(long creationTime, boolean hasLoop, int windowId, int size) {
      this.creationTime = creationTime;
      this.hasLoop = hasLoop;
      this.windowId = windowId;
      this.size = size;
    }
  }

  /** Returns metric related to this root node. */
  @NonNull
  default SnapshotWindow.WindowMetric getMetric() {
    // TODO: (b/229649593) remove metrics as it does not support all implementation.
    return new SnapshotWindow.WindowMetric(-1, false, -1, 0);
  }

  /** Returns the tree. */
  @NonNull
  Snapshot getSnapshot();

  /** Returns the title of this window. */
  @Nullable
  default CharSequence getTitle() {
    return null;
  }

  /** Returns the root View contained in this window. */
  @Nullable
  default SnapshotView getRoot(int prefetchingStrategy) {
    return getRoot();
  }

  /**
   * Gets the root node in the window's hierarchy.
   *
   * @return The root node.
   */
  @Nullable
  default SnapshotView getRoot() {
    return null;
  }

  /**
   * Gets the node that anchors this window to another.
   *
   * @return The anchor node, or {@code null} if none exists.
   */
  @Nullable
  default SnapshotView getAnchor() {
    return null;
  }

  /**
   * Gets the parent window.
   *
   * @return The parent window, or {@code null} if none exists.
   */
  @Nullable
  default SnapshotWindow getParent() {
    return null;
  }

  default @NonNull ImmutableList<SnapshotWindow> getChildren() {
    ImmutableList.Builder<SnapshotWindow> children = ImmutableList.builder();
    for (int c = 0; c < getChildCount(); ++c) {
      SnapshotWindow child = getChild(c);
      if (child != null) {
        children.add(child);
      }
    }
    return children.build();
  }

  /** Returns a child window. */
  @Nullable
  default SnapshotWindow getChild(int index) {
    return null;
  }

  /** Returns the count of child windows. */
  default int getChildCount() {
    return 0;
  }

  /** Returns a child-node that may represent either a View or a Window. */
  @Override
  @Nullable
  default Node getChildNode(int index) {
    // Order root-view before child-windows.
    return (index < 1) ? getRoot() : getChild(index - 1);
  }

  @Override
  default int getChildNodeCount() {
    // Include root-view and child-windows.
    return 1 + getChildCount();
  }

  @Override
  @Nullable
  default Node getParentNode() {
    // Prefer anchor-view over parent-window, because anchor-view is lower in the node-tree.
    @Nullable SnapshotView parentView = getAnchor();
    return (parentView == null) ? getParent() : parentView;
  }

  /**
   * Gets the unique window id.
   *
   * @return windowId The window id.
   */
  default int getId() {
    return SnapshotWindow.INVALID_WINDOW_ID;
  }

  /** Returns the ID of the display-screen that this window is on. */
  default int getDisplayId() {
    return INVALID_DISPLAY_ID;
  }

  /** Returns the Z-order of the window, where higher numbers are in front of lower. */
  default int getLayer() {
    return INVALID_LAYER;
  }

  /** Returns the touchable region of this window in the screen. */
  default void getRegionInScreen(Region outRegion) {}

  /** Returns a window-type constant, like TYPE_APPLICATION, TYPE_INPUT_METHOD... */
  default int getType() {
    return INVALID_TYPE;
  }

  /** Returns whether this window has accessibility-focus. */
  default boolean isAccessibilityFocused() {
    return false;
  }

  /** Returns whether this window is active: currently touched or has input-focus. */
  default boolean isActive() {
    return false;
  }

  /** Returns whether this window has input-focus. */
  default boolean isFocused() {
    return false;
  }

  /** Returns whether the window is a miniature floating picture-in-picture window. */
  default boolean isInPictureInPictureMode() {
    return false;
  }
}
