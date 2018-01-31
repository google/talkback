/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/** Class to manage the navigation granularity for a given {@link AccessibilityNodeInfoCompat}. */
public class CursorGranularityManager {
  /** Unsupported movement within a granularity */
  private static final int NOT_SUPPORTED = -1;

  /** Movement within a granularity reached the edge of possible movement */
  public static final int HIT_EDGE = 0;

  /** Movement within a granularity was successful */
  public static final int SUCCESS = 1;

  /** Represents an increase in granularity */
  public static final int CHANGE_GRANULARITY_HIGHER = 1;

  /** Represents a decrease in granularity */
  public static final int CHANGE_GRANULARITY_LOWER = -1;

  /** The list of navigable nodes. Computed by {@link #extractNavigableNodes}. */
  private final List<AccessibilityNodeInfoCompat> mNavigableNodes = new ArrayList<>();

  /**
   * The list of granularities supported by the navigable nodes. Computed by {@link
   * #extractNavigableNodes}.
   */
  private final ArrayList<CursorGranularity> mSupportedGranularities = new ArrayList<>();

  /**
   * The top-level node within which the user is navigating. This node's navigable children are
   * represented in {@link #mNavigableNodes}.
   */
  private AccessibilityNodeInfoCompat mLockedNode;

  /** The index of the current node within {@link #mNavigableNodes}. */
  private int mCurrentNodeIndex;

  // granularity that was chosen by user
  private CursorGranularity mSavedGranularity = CursorGranularity.DEFAULT;
  // it usually equals mSavedGranularity. But sometimes when we move focus between nodes some
  // nodes does not contain previously chosen granularity. In that case default granularity
  // is used for that node. but when we move to next node that support previously chosen
  // granularity (stored in mSavedGranularity) it switch back to that.
  private CursorGranularity mCurrentGranularity = CursorGranularity.DEFAULT;

  /** Used on API 18+ to track when text selection mode is active. */
  private boolean mSelectionModeActive;

  private final GlobalVariables mGlobalVariables;

  public CursorGranularityManager(GlobalVariables globalVariables) {
    mGlobalVariables = globalVariables;
  }

  // TODO: Remove this constructor.
  public CursorGranularityManager() {
    mGlobalVariables = null;
  }

  /** Releases resources associated with this object. */
  public void shutdown() {
    clear();
  }

  /**
   * Whether granular navigation is locked to {@code node}. If the currently requested granularity
   * is {@link CursorGranularity#DEFAULT} or is native macro granularity this will always return
   * {@code false}.
   *
   * @param node The node to check.
   * @return Whether navigation is locked to {@code node}.
   */
  public boolean isLockedTo(AccessibilityNodeInfoCompat node) {
    // If the requested granularity is default or native macro, don't report as locked.
    return mCurrentGranularity != CursorGranularity.DEFAULT
        && ((mLockedNode != null) && mLockedNode.equals(node))
        && !mCurrentGranularity.isNativeMacroGranularity();
  }

  /**
   * @return The current navigation granularity, or {@link CursorGranularity#DEFAULT} if the
   *     currently requested granularity is invalid.
   */
  public CursorGranularity getCurrentGranularity() {
    return mCurrentGranularity;
  }

  public CursorGranularity getSavedGranularity() {
    return mSavedGranularity;
  }

  /**
   * Locks navigation within the specified node, if not already locked, and sets the current
   * granularity.
   *
   * @param granularity The requested granularity.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public boolean setGranularityAt(
      AccessibilityNodeInfoCompat node, CursorGranularity granularity, EventId eventId) {
    setLockedNode(node, eventId);

    // If node is null, setLocked node does not populate mSupportedGranularities,
    // so we do it here.
    if (node == null) {
      populateMacroGranularities();
    }

    if (!mSupportedGranularities.contains(granularity)) {
      mCurrentGranularity = CursorGranularity.DEFAULT;
      return false;
    }

    mSavedGranularity = granularity;
    mCurrentGranularity = granularity;
    return true;
  }

  /** Sets granularity to default, clears locked node. */
  public void setGranularityToDefault() {
    mCurrentGranularity = CursorGranularity.DEFAULT;
    mSavedGranularity = CursorGranularity.DEFAULT;
    clear();
  }

  /** Populates macro granularities in {@link #mSupportedGranularities}. */
  private void populateMacroGranularities() {
    mSupportedGranularities.clear();
    final List<CursorGranularity> supportedGranularities = mSupportedGranularities;
    CursorGranularity.extractFromMask(0, false, null, supportedGranularities);
  }

  /**
   * Sets the current state of selection mode for navigation within text content. When enabled, the
   * manager will attempt to extend selection during navigation within a locked node.
   *
   * @param active {@code true} to activate selection mode, {@code false} to deactivate.
   */
  public void setSelectionModeActive(boolean active) {
    mSelectionModeActive = active;
    if (mGlobalVariables != null) {
      mGlobalVariables.setSelectionModeActive(active);
    }
  }

  /** @return {@code true} if selection mode is active, {@code false} otherwise. */
  public boolean isSelectionModeActive() {
    return mSelectionModeActive;
  }

  /**
   * Locks navigation within the specified node, if not already locked, and adjusts the current
   * granularity in the specified direction.
   *
   * @param direction The direction to adjust granularity. One of {@link
   *     CursorGranularityManager#CHANGE_GRANULARITY_HIGHER} or {@link
   *     CursorGranularityManager#CHANGE_GRANULARITY_LOWER}
   * @return {@code true} if the granularity changed.
   */
  public boolean adjustGranularityAt(
      AccessibilityNodeInfoCompat node, int direction, EventId eventId) {
    setLockedNode(node, eventId);

    // If node is null, setLocked node does not populate mSupportedGranularities,
    // so we do it here.
    if (node == null) {
      populateMacroGranularities();
    }

    final int count = mSupportedGranularities.size();
    int currentIndex = mSupportedGranularities.indexOf(mCurrentGranularity);
    int nextIndex;

    // Granularity adjustments always wrap around.
    nextIndex = (currentIndex + direction) % count;
    if (nextIndex < 0) {
      nextIndex = count - 1;
    }

    mCurrentGranularity = mSupportedGranularities.get(nextIndex);
    mSavedGranularity = mCurrentGranularity;
    return nextIndex != currentIndex;
  }

  /**
   * Clears the currently locked node and associated state variables. Recycles all currently held
   * nodes. Resets the requested granularity.
   */
  private void clear() {
    mCurrentNodeIndex = 0;
    mSupportedGranularities.clear();

    AccessibilityNodeInfoUtils.recycleNodes(mNavigableNodes);
    mNavigableNodes.clear();

    AccessibilityNodeInfoUtils.recycleNodes(mLockedNode);
    mLockedNode = null;

    setSelectionModeActive(false);
  }

  /**
   * As {@link #clear), but in addition keeps the granularity. Tries to restore the saved
   * granularity, after falling back to navigating with default granularity.
   *
   * @param focusedNode The new node to be focused, {@code null} if there is no new node.
   */
  private void clearAndRetainGranularity(
      @NonNull AccessibilityNodeInfoCompat focusedNode, EventId eventId) {
    CursorGranularity currentGranularity = mSavedGranularity;
    clear();
    setGranularityAt(focusedNode, currentGranularity, eventId);
  }

  /**
   * Processes TYPE_VIEW_ACCESSIBILITY_FOCUSED events by clearing the currently locked node and
   * associated state variables if the provided node is different from the locked node.
   *
   * @param node The node to compare against the locked node.
   */
  public void onNodeFocused(
      AccessibilityEvent event, AccessibilityNodeInfoCompat node, EventId eventId) {
    if (node == null) {
      return;
    }
    // If not using granular navigation... do nothing.
    if (mLockedNode == null) {
      return;
    }
    // If focused-node is different from locked-node, lock the newly focused-node (updating the
    // available granularities and resetting the granular position to the start of the node).
    if (!mLockedNode.equals(node) && !AccessibilityNodeInfoUtils.isKeyboard(event, node)) {
      clearAndRetainGranularity(node, eventId);
    }
  }

  public void startFromLastNode() {
    mCurrentNodeIndex = mNavigableNodes.size() - 1;
  }

  /**
   * Attempt to navigate within the currently locked node at the current granularity. You should
   * call either {@link #setGranularityAt} or {@link #adjustGranularityAt} before calling this
   * method.
   *
   * @return The result of navigation, which is always {@link #NOT_SUPPORTED} if there is no locked
   *     node or if the requested granularity is {@link CursorGranularity#DEFAULT}.
   */
  public int navigate(int action, EventId eventId) {
    if (mLockedNode == null) {
      return NOT_SUPPORTED;
    }

    final CursorGranularity requestedGranularity = mCurrentGranularity;
    if ((requestedGranularity == null) || (requestedGranularity == CursorGranularity.DEFAULT)) {
      return NOT_SUPPORTED;
    }

    // Handle web granularity separately.
    if (requestedGranularity.isWebGranularity()) {
      return navigateWeb(action, requestedGranularity, eventId);
    }

    final Bundle arguments = new Bundle();
    final int count = mNavigableNodes.size();
    final int increment;

    switch (action) {
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        increment = 1;
        if (mCurrentNodeIndex < 0) {
          mCurrentNodeIndex++;
        }
        break;
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        increment = -1;
        if (mCurrentNodeIndex >= count) {
          mCurrentNodeIndex--;
        }
        break;
      default:
        return NOT_SUPPORTED;
    }

    while ((mCurrentNodeIndex >= 0) && (mCurrentNodeIndex < count)) {
      if (isSelectionModeActive()) {
        arguments.putBoolean(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true);
      }

      arguments.putInt(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
          requestedGranularity.value);

      final AccessibilityNodeInfoCompat currentNode = mNavigableNodes.get(mCurrentNodeIndex);
      if (PerformActionUtils.performAction(currentNode, action, arguments, eventId)) {
        return SUCCESS;
      }

      LogUtils.log(
          this,
          Log.VERBOSE,
          "Failed to move with granularity %s, trying next node",
          requestedGranularity.name());

      // If movement failed, advance to the next node and try again.
      mCurrentNodeIndex += increment;
    }

    return HIT_EDGE;
  }

  /**
   * Attempts to navigate web content at the specified granularity.
   *
   * @param action The accessibility action to perform, one of:
   *     <ul>
   *       <li>{@link AccessibilityNodeInfoCompat#ACTION_NEXT_AT_MOVEMENT_GRANULARITY}
   *       <li>{@link AccessibilityNodeInfoCompat#ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY}
   *     </ul>
   *
   * @param granularity The granularity at which to navigate.
   * @return The result of navigation, which is always {@link #NOT_SUPPORTED} if there is no locked
   *     node or if the requested granularity is {@link CursorGranularity#DEFAULT}.
   */
  private int navigateWeb(int action, CursorGranularity granularity, EventId eventId) {
    final int movementType;
    final String htmlElementType;

    switch (action) {
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        movementType = WebInterfaceUtils.DIRECTION_FORWARD;
        break;
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        movementType = WebInterfaceUtils.DIRECTION_BACKWARD;
        break;
      default:
        return NOT_SUPPORTED;
    }

    switch (granularity) {
      case WEB_SECTION:
        htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_SECTION;
        break;
      case WEB_LINK:
        htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_LINK;
        break;
      case WEB_LIST:
        htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_LIST;
        break;
      case WEB_CONTROL:
        htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_CONTROL;
        break;
      default:
        return NOT_SUPPORTED;
    }

    if (!WebInterfaceUtils.performNavigationToHtmlElementAction(
        mLockedNode, movementType, htmlElementType, eventId)) {
      return HIT_EDGE;
    }

    return SUCCESS;
  }

  /**
   * Manages the currently locked node, clearing properties and loading navigable children if
   * necessary.
   *
   * @param node The node the user wishes to navigate within.
   */
  private void setLockedNode(AccessibilityNodeInfoCompat node, EventId eventId) {
    // Clear current state if text has changed even with the same node. Supported granularities
    // can be changed.
    if ((mLockedNode != null)
        && (!mLockedNode.equals(node)
            || !TextUtils.equals(mLockedNode.getText(), node.getText()))) {
      clear();
    }

    if (mLockedNode == null && node != null) {
      mLockedNode = AccessibilityNodeInfoCompat.obtain(node);

      if (shouldClearSelection(mLockedNode)) {
        PerformActionUtils.performAction(
            mLockedNode, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, eventId);
      }

      // Extract the navigable nodes and supported granularities.
      final List<CursorGranularity> supported = mSupportedGranularities;
      Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
      final int supportedMask =
          extractNavigableNodes(mLockedNode, mNavigableNodes, visitedNodes, eventId);
      AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
      final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(mLockedNode);

      String[] supportedHtmlElements = WebInterfaceUtils.getSupportedHtmlElements(mLockedNode);

      CursorGranularity.extractFromMask(
          supportedMask, hasWebContent, supportedHtmlElements, supported);
    }
  }

  /**
   * Return whether selection should be cleared from the specified node when locking navigation to
   * it.
   *
   * @param node The node to check.
   * @return {@code true} if selection should be cleared.
   */
  private boolean shouldClearSelection(AccessibilityNodeInfoCompat node) {
    // EditText has has a stable cursor position, so don't clear selection.
    return (Role.getRole(node) != Role.ROLE_EDIT_TEXT || !node.isFocused());
  }

  /**
   * Populates a list with the set of {@link CursorGranularity}s supported by the specified root
   * node and its navigable children.
   *
   * @param context The parent context.
   * @param root The root node from which to extract granularities.
   * @return A list of supported granularities.
   */
  public static List<CursorGranularity> getSupportedGranularities(
      Context context, AccessibilityNodeInfoCompat root, EventId eventId) {
    final LinkedList<CursorGranularity> supported = new LinkedList<>();
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    final int supportedMask = extractNavigableNodes(root, null, visitedNodes, eventId);
    AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
    final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(root);

    String[] supportedHtmlElements = WebInterfaceUtils.getSupportedHtmlElements(root);

    CursorGranularity.extractFromMask(
        supportedMask, hasWebContent, supportedHtmlElements, supported);

    return supported;
  }

  /**
   * Search for granularity-traversable nodes under <strong>node description tree</strong> of {@code
   * root}. <strong>Note:</strong> Caller should recycle {@code root}, {@code nodes} and {@code
   * visitedNodes} after use.
   *
   * @param root The root node of the node description tree.
   * @param nodes The list of granularity-traversable nodes collected.
   * @param visitedNodes The list of visited nodes during the node search, used to avoid infinite
   *     loop in the node tree.
   * @return The mask of supported all granularities supported by the root and child nodes.
   */
  private static int extractNavigableNodes(
      AccessibilityNodeInfoCompat root,
      @Nullable List<AccessibilityNodeInfoCompat> nodes,
      Set<AccessibilityNodeInfoCompat> visitedNodes,
      EventId eventId) {
    int supportedGranularities = 0;
    if (root == null) {
      return supportedGranularities;
    }

    // "root" will be recycled, make a node copy when adding it to {@code visitedNodes}.
    AccessibilityNodeInfoCompat currentNode = AccessibilityNodeInfoUtils.obtain(root);
    if (!visitedNodes.add(currentNode)) {
      // Root already visited. Recycle root node and stop searching.
      currentNode.recycle();
      return supportedGranularities;
    }

    if (root.getMovementGranularities() != 0) {
      if (nodes != null) {
        // "root" will be recycled, make a node copy when adding it to collection.
        nodes.add(AccessibilityNodeInfoUtils.obtain(root));
      }
      supportedGranularities |= root.getMovementGranularities();
    }

    if (!TextUtils.isEmpty(root.getContentDescription())) {
      // Don't pull children from nodes with content descriptions.
      return supportedGranularities;
    }
    ReorderedChildrenIterator iterator = ReorderedChildrenIterator.createAscendingIterator(root);
    while (iterator.hasNext()) {
      AccessibilityNodeInfoCompat child = iterator.next();
      try {
        // Reset selection for each child.
        PerformActionUtils.performAction(
            child, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, null /* args */, eventId);
        // Use the same traversal filter logic as what we use to compose node tree description for
        // TYPE_VIEW_ACCESSIBILITY_FOCUSED events. See named node "description_for_tree_nodes" in
        // compositor.json.
        if (AccessibilityNodeInfoUtils.isVisible(child)
            && !AccessibilityNodeInfoUtils.isAccessibilityFocusable(child)) {
          supportedGranularities |= extractNavigableNodes(child, nodes, visitedNodes, eventId);
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(child);
      }
    }

    return supportedGranularities;
  }
}
