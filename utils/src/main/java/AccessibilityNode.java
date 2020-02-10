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

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
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
 *   <li>recycling
 *   <li>using compat vs bare methods
 *   <li>using correct methods for various android versions
 * </ul>
 *
 * <p>Also wraps a single instance of AccessibilityWindowInfo/Compat, to help with:
 *
 * <ul>
 *   <li>reducing duplication of window info
 *   <li>handling null window info
 *   <li>recycling window info
 * </ul>
 *
 * <p>Does not wrap null node-info to safely chain calls, because intermediate objects cannot be
 * chained, because intermediate objects must be recycled. Does contain intermediate objects, like
 * window info, for pass-through functions instead of chaining.
 */
public class AccessibilityNode {

  private static final String TAG = "AccessibilityNode";

  /**
   * AccessibilityNodeInfoCompat should only be wrapper on AccessibilityNodeInfo. One may be null,
   * created on demand from the other. Never expose these nodes.
   */
  @Nullable private AccessibilityNodeInfo nodeBare;

  private AccessibilityNodeInfoCompat nodeCompat;

  /** Window data, created on demand. */
  @Nullable private AccessibilityWindow window;

  /** Name of calling method that recycled this node. */
  private String recycledBy;

  ///////////////////////////////////////////////////////////////////////////////////////
  // Construction

  /** Caller keeps ownership of nodeArg. Caller must also recycle returned AccessibilityNode. */
  @Nullable
  public static AccessibilityNode obtainCopy(@Nullable AccessibilityNodeInfo nodeArg) {
    return construct(nodeArg, /* copy= */ true, FACTORY);
  }

  /** Caller keeps ownership of nodeArg. Caller must also recycle returned AccessibilityNode. */
  @Nullable
  public static AccessibilityNode obtainCopy(@Nullable AccessibilityNodeInfoCompat nodeArg) {
    return construct(nodeArg, /* copy= */ true, FACTORY);
  }

  /** Gets a copy of this node. Caller must recycle the returned AccessibilityNode. */
  @Nullable
  public AccessibilityNode obtainCopy() {
    return obtainCopy(getCompat());
  }

  /** Takes ownership of nodeArg. Caller must recycle returned AccessibilityNode. */
  @Nullable
  public static AccessibilityNode takeOwnership(@Nullable AccessibilityNodeInfo nodeArg) {
    return construct(nodeArg, /* copy= */ false, FACTORY);
  }

  /** Takes ownership of nodeArg. Caller must recycle returned AccessibilityNode. */
  @Nullable
  public static AccessibilityNode takeOwnership(@Nullable AccessibilityNodeInfoCompat nodeArg) {
    return construct(nodeArg, /* copy= */ false, FACTORY);
  }

  /**
   * Returns a node instance, or null. Applies null-checking and copying. Should only be called by
   * this class and sub-classes. Uses factory argument to create sub-class instances, without
   * creating unnecessary instances when result should be null. Method is protected so that it can
   * be called by sub-classes without duplicating null-checking logic.
   *
   * @param nodeArg The wrapped node info. Caller may retain responsibility to recycle.
   * @param copy If true, a copy is wrapped, and caller must recycle nodeArg.
   * @param factory Creates instances of AccessibilityNode or sub-classes.
   * @return AccessibilityNode instance, that caller must recycle.
   */
  @Nullable
  protected static <T extends AccessibilityNode> T construct(
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
  @Nullable
  protected static <T extends AccessibilityNode> T construct(
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

  /** Returns flag for success versus node already recycled. */
  public final synchronized boolean refresh() {

    // Remove stale window info, so that refreshed node can re-generate window info.
    if (window != null && !window.isRecycled()) {
      window.recycle("AccessibilityNode.refresh()");
      window = null;
    }

    // Error if already recycled.
    if (recycledBy != null) {
      throwError("Trying to refresh node already recycled by %s", recycledBy);
      return false;
    }

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

  /** Returns whether the wrapped event is already recycled. */
  public final synchronized boolean isRecycled() {
    return (recycledBy != null);
  }

  /** Recycles non-null nodes and empties collection. */
  public static void recycle(String caller, Collection<AccessibilityNode> nodes) {
    if (nodes == null) {
      return;
    }

    for (AccessibilityNode node : nodes) {
      if (node != null) {
        node.recycle(caller);
      }
    }

    nodes.clear();
  }

  /** Recycles non-null nodes. */
  public static void recycle(String caller, AccessibilityNode... nodes) {
    if (nodes == null) {
      return;
    }

    for (@Nullable AccessibilityNode node : nodes) {
      if (node != null) {
        node.recycle(caller);
      }
    }
  }

  /** Recycles the wrapped node & window. Errors if called more than once. */
  public final synchronized void recycle(String caller) {

    // Check for double-recycling.
    if (recycledBy == null) {
      recycledBy = caller;
    } else {
      logOrThrow("AccessibilityNode already recycled by %s then by %s", recycledBy, caller);
      return;
    }

    // Recycle compat or bare node -- not both.
    if (nodeCompat != null) {
      // Recycling nodeCompat will also recycle nodeBare, because nodeCompat contains nodeBare.
      recycle(nodeCompat, caller);
    } else if (nodeBare != null) {
      recycle(nodeBare, caller);
    }

    if (window != null && !window.isRecycled()) {
      window.recycle(caller);
    }

    recycledBy = caller;
  }

  private final void recycle(AccessibilityNodeInfo node, String caller) {
    try {
      node.recycle();
    } catch (IllegalStateException e) {
      logOrThrow(
          e,
          "Caught IllegalStateException from accessibility framework with %s trying to recycle"
              + " node %s",
          caller,
          node);
    }
  }

  private final void recycle(AccessibilityNodeInfoCompat node, String caller) {
    try {
      node.recycle();
    } catch (IllegalStateException e) {
      logOrThrow(
          e,
          "Caught IllegalStateException from accessibility framework with %s trying to recycle"
              + " node %s",
          caller,
          node);
    }
  }

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
    if (isRecycled()) {
      throwError("getBare() called on node already recycled by %s", recycledBy);
    }
    if (nodeBare == null) {
      nodeBare = nodeCompat.unwrap(); // Available since compat library 26.1.0
    }
    return nodeBare;
  }

  /** Create and use compat wrapper on demand. */
  private AccessibilityNodeInfoCompat getCompat() {
    if (isRecycled()) {
      throwError("getCompat() called on node already recycled by %s", recycledBy);
    }
    if (nodeCompat == null) {
      nodeCompat = AccessibilityNodeInfoCompat.wrap(nodeBare); // Available since compat 26.1.0
    }
    return nodeCompat;
  }

  /**
   * Returns hash-code for use as a HashMap key.
   *
   * <p><b>Warning:</b> Hash-code will change if node is recycled. Remove from hash keys before
   * recycling.
   */
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

  public final List<AccessibilityNodeInfo.AccessibilityAction> getActionList() {
    return getBare().getActionList();
  }

  /** Gets the node bounds in parent coordinates. {@code rect} will be written to. */
  public final void getBoundsInScreen(Rect rect) {
    getCompat().getBoundsInScreen(rect);
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

  public CollectionItemInfoCompat getCollectionItemInfo() {
    return getCompat().getCollectionItemInfo();
  }

  @RoleName
  public int getRole() {
    return Role.getRole(getCompat());
  }

  public final CharSequence getText() {
    return getCompat().getText(); // Use compat to get clickable-spans.
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

  public final boolean showOnScreen(@Nullable EventId eventId) {
    return PerformActionUtils.showOnScreen(getCompat(), eventId);
  }

  // TODO: Add more methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // Utility methods.  Call AccessibilityNodeInfoUtils methods, do not duplicate them.

  /** Returns all descendants that match filter. Caller must recycle returned nodes. */
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
   * matching ancestor. Returns {@code null} if no nodes match. Caller must recycle returned node.
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
   * @return The first node reached via BFS traversal that satisfies the filter. Must be recycled by
   *     caller.
   */
  @Nullable
  public AccessibilityNode searchFromBfs(Filter<AccessibilityNodeInfoCompat> filter) {
    AccessibilityNodeInfoCompat matchCompat =
        AccessibilityNodeInfoUtils.searchFromBfs(getCompat(), filter);
    return AccessibilityNode.takeOwnership(matchCompat);
  }

  // TODO: Add methods on demand. Keep alphabetic order.

  ///////////////////////////////////////////////////////////////////////////////////////
  // AccessibilityWindowInfo methods

  /** Gets or creates window info, owned by AccessibilityNode. */
  private @Nullable AccessibilityWindow getWindow() {
    if (isRecycled()) {
      throwError("getWindow() called on node already recycled by %s", recycledBy);
    }
    if (window == null) {
      // Window will be recycled by AccessibilityNode.recycle()
      window = AccessibilityWindow.takeOwnership(getBare().getWindow(), getCompat().getWindow());
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

  public final @AccessibilityWindow.WindowType int windowGetType() {
    AccessibilityWindow window = getWindow();
    return (window == null) ? AccessibilityWindow.TYPE_UNKNOWN : window.getType();
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

  public TraversalStrategy getTraversalStrategy(@TraversalStrategy.SearchDirection int direction) {
    return TraversalStrategyUtils.getTraversalStrategy(getCompat(), direction);
  }

  @Nullable
  public AccessibilityNode findInitialFocusInNodeTree(
      TraversalStrategy traversalStrategy,
      @TraversalStrategy.SearchDirection int searchDirection,
      Filter<AccessibilityNodeInfoCompat> nodeFilter) {

    return AccessibilityNode.takeOwnership(
        TraversalStrategyUtils.findInitialFocusInNodeTree(
            traversalStrategy, getCompat(), searchDirection, nodeFilter));
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Error methods

  @FormatMethod
  private void logOrThrow(@FormatString String format, Object... parameters) {
    if (isDebug()) {
      throwError(format, parameters);
    } else {
      logError(format, parameters);
    }
  }

  private void logOrThrow(IllegalStateException exception, String format, Object... parameters) {
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
}

