/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.utils;

import static com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_TYPE_NONE;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.ViewResourceName;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WindowType;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A wrapper around AccessibilityNodeInfo/Compat, to help with:
 *
 * <ul>
 *   <li>handling null nodes
 *   <li>refreshing
 *   <li>using compat vs bare methods
 *   <li>using correct methods for various android versions
 * </ul>
 *
 * <p>Also wraps a single instance of AccessibilityWindowInfo/Compat, to help with:
 *
 * <ul>
 *   <li>reducing duplication of window info
 *   <li>handling null window info
 * </ul>
 *
 * <p>Does not wrap null node-info to safely chain calls. Does contain intermediate objects, like
 * window info, for pass-through functions instead of chaining.
 */
public class AccessibilityNode {

  private static final String TAG = "AccessibilityNode";

  /**
   * AccessibilityNodeInfoCompat should only be wrapper on AccessibilityNodeInfo. One may be null,
   * created on demand from the other. Never expose these nodes.
   */
  private @Nullable AccessibilityNodeInfo nodeBare;

  private AccessibilityNodeInfoCompat nodeCompat;

  /** Window data, created on demand. */
  private @Nullable AccessibilityWindow window;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  /** Caller keeps ownership of nodeArg. */
  @Deprecated
  public static @Nullable AccessibilityNode obtainCopy(@Nullable AccessibilityNodeInfo nodeArg) {
    return construct(nodeArg, /* copy= */ true, FACTORY);
  }

  /** Caller keeps ownership of nodeArg. */
  @Deprecated
  public static @Nullable AccessibilityNode obtainCopy(
      @Nullable AccessibilityNodeInfoCompat nodeArg) {
    return construct(nodeArg, /* copy= */ true, FACTORY);
  }

  /** Gets a copy of this node. */
  @Deprecated
  public @Nullable AccessibilityNode obtainCopy() {
    return obtainCopy(getCompat());
  }

  /** Gets a copy of this node's inner compat-node. */
  public @Nullable AccessibilityNodeInfoCompat obtainCopyCompat() {
    return AccessibilityNodeInfoCompat.obtain(getCompat());
  }

  /** Wrapgs nodeArg, but does not recycle it. */
  public static @Nullable AccessibilityNode takeOwnership(@Nullable AccessibilityNodeInfo nodeArg) {
    return construct(nodeArg, /* copy= */ false, FACTORY);
  }

  /** Wraps nodeArg, but does not recycle it. */
  public static @Nullable AccessibilityNode takeOwnership(
      @Nullable AccessibilityNodeInfoCompat nodeArg) {
    return construct(nodeArg, /* copy= */ false, FACTORY);
  }

  /**
   * Returns a node instance, or null. Applies null-checking and copying. Should only be called by
   * this class and sub-classes. Uses factory argument to create sub-class instances, without
   * creating unnecessary instances when result should be null. Method is protected so that it can
   * be called by sub-classes without duplicating null-checking logic.
   *
   * @param nodeArg The wrapped node info.
   * @param copy If true, a copy is wrapped.
   * @param factory Creates instances of AccessibilityNode or sub-classes.
   * @return AccessibilityNode instance.
   */
  protected static <T extends AccessibilityNode> @Nullable T construct(
      @Nullable AccessibilityNodeInfo nodeArg, boolean copy, Factory<T> factory) {
    if (nodeArg == null) {
      return null;
    }
    T instance = factory.create();
    AccessibilityNode instanceBase = instance;
    instanceBase.nodeBare = copy ? AccessibilityNodeInfo.obtain(nodeArg) : nodeArg;
    return instance;
  }

  /** Returns a node instance, or null. Should only be called by this class and sub-classes. */
  protected static <T extends AccessibilityNode> @Nullable T construct(
      @Nullable AccessibilityNodeInfoCompat nodeArg, boolean copy, Factory<T> factory) {
    // See implementation notes in overloaded construct() method, above.
    if (nodeArg == null) {
      return null;
    }
    T instance = factory.create();
    AccessibilityNode instanceBase = instance;
    instanceBase.nodeCompat = copy ? AccessibilityNodeInfoCompat.obtain(nodeArg) : nodeArg;
    return instance;
  }

  protected AccessibilityNode() {}

  /** A factory that can create instances of AccessibilityNode or sub-classes. */
  protected interface Factory<T extends AccessibilityNode> {
    T create();
  }

  private static final Factory<AccessibilityNode> FACTORY =
      new Factory<AccessibilityNode>() {
        @Override
        public AccessibilityNode create() {
          return new AccessibilityNode();
        }
      };

  ///////////////////////////////////////////////////////////////////////////////////////
  // Refreshing

  /** Returns flag for success. */
  public final synchronized boolean refresh() {

    // Remove stale window info, so that refreshed node can re-generate window info.
    window = null;

    // Try to refresh node.
    try {
      if (nodeCompat == null) {
        return nodeBare.refresh();
      } else {
        nodeBare = null; // Remove stale inner node reference.
        return nodeCompat.refresh();
      }
    } catch (IllegalStateException e) {
      logOrThrow(
          e, "Caught IllegalStateException from accessibility framework trying to refresh node");
      return false;
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Recycling

  /**
   * Returns whether the wrapped event is already recycled.
   *
   * <p>TODO: Remove once all dependencies have been removed.
   *
   * @deprecated Accessibility is discontinuing recycling. Function will return false.
   */
  @Deprecated
  public final synchronized boolean isRecycled() {
    return false;
  }

  /**
   * Recycles non-null nodes and empties collection.
   *
   * @deprecated Accessibility is discontinuing recycling. Function will still clear nodes.
   */
  @Deprecated
  public static void recycle(String caller, Collection<AccessibilityNode> nodes) {
    if (nodes != null) {
      nodes.clear();
    }
  }

  /**
   * Recycles non-null nodes.
   *
   * @deprecated Accessibility is discontinuing recycling.
   */
  @Deprecated
  public static void recycle(@Nullable AccessibilityNode... nodes) {}

  /**
   * Recycles non-null nodes.
   *
   * @deprecated Accessibility is discontinuing recycling.
   */
  @Deprecated
  public static void recycle(String caller, @Nullable AccessibilityNode... nodes) {}

  /**
   * Recycles the wrapped node & window. Errors if called more than once.
   *
   * @deprecated Accessibility is discontinuing recycling.
   */
  @Deprecated
  public final void recycle() {}

  /**
   * Recycles the wrapped node & window. Errors if called more than once.
   *
   * @deprecated Accessibility is discontinuing recycling.
   */
  @Deprecated
  public final synchronized void recycle(String caller) {}

  /** Overridable for testing. */
  protected boolean isDebug() {
    return BuildConfig.DEBUG;
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityNodeInfo methods. If compat method available, ensure node is converted to compat.
  // TODO: For thorough thread-safety, all data accessor methods should be synchronized,
  // so they do not read node info that is being recycled. Also see:
  // https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo

  /** Access bare node, which always exists. Extracts reference to bare node on demand. */
  private AccessibilityNodeInfo getBare() {
    if (nodeBare == null) {
      nodeBare = nodeCompat.unwrap(); // Available since compat library 26.1.0
    }
    return nodeBare;
  }

  /** Create and use compat wrapper on demand. */
  public AccessibilityNodeInfoCompat getCompat() {
    if (nodeCompat == null) {
      nodeCompat = AccessibilityNodeInfoCompat.wrap(nodeBare); // Available since compat 26.1.0
    }
    return nodeCompat;
  }

  /** Returns hash-code for use as a HashMap key. */
  @Override
  public final int hashCode() {
    return getBare().hashCode();
  }

  /** Returns equality check, for use as a HashMap key. */
  @Override
  public final boolean equals(Object otherObj) {
    if (this == otherObj) {
      return true;
    }
    if (!(otherObj instanceof AccessibilityNode)) {
      return false;
    }
    AccessibilityNode other = (AccessibilityNode) otherObj;
    return getCompat().equals(other.getCompat());
  }

  /** Performs equality checking between this node's info compat and the given one. */
  public final boolean equalTo(AccessibilityNodeInfoCompat node) {
    return getCompat().equals(node);
  }

  /** Performs equality checking between this node's info and the given one. */
  public final boolean equalTo(AccessibilityNodeInfo node) {
    return getBare().equals(node);
  }

  public final List<AccessibilityNodeInfo.AccessibilityAction> getActionList() {
    return getBare().getActionList();
  }

  /** Gets the node bounds in parent coordinates. {@code rect} will be written to. */
  public final void getBoundsInScreen(Rect rect) {
    getCompat().getBoundsInScreen(rect);
  }

  public String getUniqueId() {
    return getCompat().getUniqueId();
  }

  /** Gets the child at the given index. Caller must recycle the returned node. */
  public final AccessibilityNode getChild(int index) {
    return AccessibilityNode.takeOwnership(getCompat().getChild(index));
  }

  public final int getChildCount() {
    return getCompat().getChildCount();
  }

  public final CharSequence getClassName() {
    return getCompat().getClassName();
  }

  public CollectionInfoCompat getCollectionInfo() {
    return getCompat().getCollectionInfo();
  }

  public CollectionItemInfoCompat getCollectionItemInfo() {
    return getCompat().getCollectionItemInfo();
  }

  /** Gets the parent. */
  public AccessibilityNode getParent() {
    return takeOwnership(getCompat().getParent());
  }

  @RoleName
  public int getRole() {
    return Role.getRole(getCompat());
  }

  public final @Nullable CharSequence getContentDescription() {
    return getCompat().getContentDescription();
  }

  public final @Nullable CharSequence getText() {
    return AccessibilityNodeInfoUtils.getText(getCompat()); // Use compat to get clickable-spans.
  }

  public boolean isHeading() {
    return AccessibilityNodeInfoUtils.isHeading(getCompat());
  }

  public final boolean isVisibleToUser() {
    return getCompat().isVisibleToUser();
  }

  public final boolean performAction(int action, @Nullable EventId eventId) {
    return PerformActionUtils.performAction(
        getCompat(), action, eventId); // Use compat to perform action.
  }

  public final boolean performAction(int action, @Nullable Bundle args, @Nullable EventId eventId) {
    return PerformActionUtils.performAction(
        getCompat(), action, args, eventId); // Use compat to perform action.
  }

  public final boolean showOnScreen(@Nullable EventId eventId) {
    return PerformActionUtils.showOnScreen(getCompat(), eventId);
  }

  // TODO: Add more methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // Utility methods.  Call AccessibilityNodeInfoUtils methods, do not duplicate them.

  public int findDepth() {
    return AccessibilityNodeInfoUtils.findDepth(getCompat());
  }

  public boolean hasMatchingDescendantOrRoot(Filter<AccessibilityNodeInfoCompat> filter) {
    if (filter.accept(getCompat())) {
      return true;
    }
    return AccessibilityNodeInfoUtils.hasMatchingDescendant(getCompat(), filter);
  }

  /** Returns all descendants that match filter. */
  public List<AccessibilityNode> getMatchingDescendantsOrRoot(
      Filter<AccessibilityNodeInfoCompat> filter) {
    List<AccessibilityNodeInfoCompat> matchesCompat =
        AccessibilityNodeInfoUtils.getMatchingDescendantsOrRoot(getCompat(), filter);
    List<AccessibilityNode> matches = new ArrayList<>(matchesCompat.size());
    for (AccessibilityNodeInfoCompat matchCompat : matchesCompat) {
      matches.add(AccessibilityNode.takeOwnership(matchCompat));
    }
    return matches;
  }

  /**
   * Returns duplicated current AccessibilityNode if it matches the {@code filter}, or the first
   * matching ancestor. Returns {@code null} if no nodes match.
   */
  public AccessibilityNode getSelfOrMatchingAncestor(Filter<AccessibilityNodeInfoCompat> filter) {
    AccessibilityNodeInfoCompat matchCompat =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(getCompat(), filter);
    return AccessibilityNode.takeOwnership(matchCompat);
  }

  public final CharSequence getNodeText() {
    return AccessibilityNodeInfoUtils.getNodeText(getCompat());
  }

  public final @Nullable String getViewIdText() {
    return AccessibilityNodeInfoUtils.getViewIdText(getCompat());
  }

  public boolean hasAncestor(final AccessibilityNode targetAncestor) {
    if (targetAncestor == null) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return targetAncestor.equalTo(node);
          }
        };

    return AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(getCompat(), filter);
  }

  public final boolean isAccessibilityFocusable() {
    return AccessibilityNodeInfoUtils.isAccessibilityFocusable(getCompat());
  }

  /**
   * Returns the result of applying a filter using breadth-first traversal from current node.
   *
   * @param filter The filter to satisfy.
   * @return The first node reached via BFS traversal that satisfies the filter.
   */
  public @Nullable AccessibilityNode searchFromBfs(Filter<AccessibilityNodeInfoCompat> filter) {
    AccessibilityNodeInfoCompat matchCompat =
        AccessibilityNodeInfoUtils.searchFromBfs(getCompat(), filter);
    return AccessibilityNode.takeOwnership(matchCompat);
  }

  /**
   * Returns true if two nodes share the same parent.
   *
   * @param node1 the node to check.
   * @param node2 the node to check.
   * @return {@code true} if node and comparedNode share the same parent.
   */
  public static boolean shareParent(
      @Nullable AccessibilityNode node1, @Nullable AccessibilityNode node2) {
    if (node1 == null || node2 == null) {
      return false;
    }
    AccessibilityNode node1Parent = node1.getParent();
    AccessibilityNode node2Parent = node2.getParent();
    return (node1Parent != null && node1Parent.equals(node2Parent));
  }

  // TODO: Add methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityWindowInfo methods

  /** Gets or creates window info, owned by AccessibilityNode. */
  private @Nullable AccessibilityWindow getWindow() {
    if (window == null) {
      window =
          AccessibilityWindow.takeOwnership(
              AccessibilityNodeInfoUtils.getWindow(getBare()),
              AccessibilityNodeInfoUtils.getWindow(getCompat()));
    }
    return window;
  }

  public final boolean windowIsInPictureInPictureMode() {
    AccessibilityWindow window = getWindow();
    return (window != null) && window.isInPictureInPictureMode();
  }

  public final boolean windowIsActive() {
    AccessibilityWindow window = getWindow();
    return (window != null) && window.isActive();
  }

  public final boolean windowIsFocused() {
    AccessibilityWindow window = getWindow();
    return (window != null) && window.isFocused();
  }

  @WindowType
  public final int windowGetType() {
    AccessibilityWindow window = getWindow();
    return (window == null) ? WINDOW_TYPE_NONE : window.getType();
  }

  // TODO: Add methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // WebInterfaceUtils methods

  /** Check if this node is web container */
  public boolean isWebContainer() {
    return WebInterfaceUtils.isWebContainer(getCompat());
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // TraversalStrategyUtils methods

  public TraversalStrategy getTraversalStrategy(
      FocusFinder focusFinder, @TraversalStrategy.SearchDirection int direction) {
    return TraversalStrategyUtils.getTraversalStrategy(getCompat(), focusFinder, direction);
  }

  public @Nullable AccessibilityNode findFirstFocusInNodeTree(
      TraversalStrategy traversalStrategy,
      @TraversalStrategy.SearchDirection int searchDirection,
      Filter<AccessibilityNodeInfoCompat> nodeFilter) {

    return AccessibilityNode.takeOwnership(
        TraversalStrategyUtils.findFirstFocusInNodeTree(
            traversalStrategy, getCompat(), searchDirection, nodeFilter));
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // ImageNode methods

  public @Nullable ViewResourceName getPackageNameAndViewId() {
    return ViewResourceName.create(getCompat());
  }

  public boolean isInCollection() {
    return AccessibilityNodeInfoUtils.isInCollection(getCompat());
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Error methods

  @FormatMethod
  private void logOrThrow(
      IllegalStateException exception, @FormatString String format, Object... parameters) {
    if (isDebug()) {
      throw exception;
    } else {
      logError(format, parameters);
      logError("%s", exception);
    }
  }

  protected void logError(String format, Object... parameters) {
    LogUtils.e(TAG, format, parameters);
  }

  @FormatMethod
  protected void throwError(@FormatString String format, Object... parameters) {
    throw new IllegalStateException(String.format(format, parameters));
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Display methods

  @Override
  public String toString() {
    return AccessibilityNodeInfoUtils.toStringShort(getCompat());
  }
}
