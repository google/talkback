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

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_NON_FOCUSABLE_VISIBLE_NODE;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Class to manage the navigation granularity for a given {@link AccessibilityNodeInfoCompat}. */
public class CursorGranularityManager {

  private static final String TAG = "CursorGranularityManager";

  /** Unsupported movement within a granularity */
  private static final int NOT_SUPPORTED = -1;

  /** Movement within a granularity reached the edge of possible movement */
  public static final int HIT_EDGE = 0;

  /** Movement within a granularity was successful */
  public static final int SUCCESS = 1;

  /** Movement within a granularity reached the edge of possible movement for earcon */
  public static final int PRE_HIT_EDGE = 2;

  /** Represents an increase in granularity */
  public static final int CHANGE_GRANULARITY_HIGHER = 1;

  /** Represents a decrease in granularity */
  public static final int CHANGE_GRANULARITY_LOWER = -1;

  /** The list of navigable nodes. Computed by {@link #extractNavigableNodes}. */
  private final List<AccessibilityNodeInfoCompat> navigableNodes = new ArrayList<>();

  /**
   * The list of granularities supported by the navigable nodes. Computed by {@link
   * #extractNavigableNodes}.
   */
  private final ArrayList<CursorGranularity> supportedGranularities = new ArrayList<>();

  /**
   * The top-level node within which the user is navigating. This node's navigable children are
   * represented in {@link #navigableNodes}.
   */
  private @Nullable AccessibilityNodeInfoCompat lockedNode;

  /** The index of the current node within {@link #navigableNodes}. */
  private int currentNodeIndex;

  /** The granularity that was chosen by user. */
  private CursorGranularity savedGranularity = CursorGranularity.DEFAULT;
  // it usually equals savedGranularity. But sometimes when we move focus between nodes some
  // nodes does not contain previously chosen granularity. In that case default granularity
  // is used for that node. but when we move to next node that support previously chosen
  // granularity (stored in savedGranularity) it switch back to that.
  private CursorGranularity currentGranularity = CursorGranularity.DEFAULT;

  /** Used on API 18+ to track when text selection mode is active. */
  private boolean selectionModeActive;

  private final GlobalVariables globalVariables;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  // Cursor cache associated with GranularityTraversal object must be cleared regularly using
  // GranularityTraversal#clearAllCursors().
  private final GranularityTraversal granularityTraversal;

  private Pipeline.FeedbackReturner pipeline;
  private UserInterface userInterface;

  public CursorGranularityManager(
      GlobalVariables globalVariables,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      ProcessorPhoneticLetters processorPhoneticLetters) {
    this.globalVariables = globalVariables;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    granularityTraversal = new GranularityTraversal(processorPhoneticLetters);
  }

  public void setPipelineFeedbackReturner(Pipeline.FeedbackReturner pipeline) {
    granularityTraversal.setPipelineFeedbackReturner(pipeline);
    this.pipeline = pipeline;
  }

  public void setPipelineEventReceiver(Pipeline.EventReceiver pipeline) {
    granularityTraversal.setPipelineEventReceiver(pipeline);
  }

  public void setUserInterface(UserInterface userInterface) {
    this.userInterface = userInterface;
  }

  /** Releases resources associated with this object. */
  public void shutdown() {
    clear();
  }

  /**
   * Whether granular navigation is locked to {@code node}, or is locked to the editing node if
   * {@code node} is on IME. If the currently requested granularity is {@link
   * CursorGranularity#DEFAULT} or is native macro granularity this will always return {@code
   * false}.
   *
   * @param node The node to check.
   * @return Whether navigation is locked to {@code node}.
   */
  public boolean isLockedToNodeOrEditingNode(AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat editingNode =
        accessibilityFocusMonitor.getEditingNodeFromFocusedKeyboard(node);
    if (editingNode != null) {
      node = editingNode;
    }
    // If the requested granularity is default or native macro, don't report as locked.
    return currentGranularity != CursorGranularity.DEFAULT
        && ((lockedNode != null) && lockedNode.equals(node))
        && !currentGranularity.isNativeMacroGranularity();
  }

  /**
   * @return The current navigation granularity, or {@link CursorGranularity#DEFAULT} if the
   *     currently requested granularity is invalid.
   */
  public CursorGranularity getCurrentGranularity() {
    return currentGranularity;
  }

  public CursorGranularity getSavedGranularity() {
    return savedGranularity;
  }

  /** Returns true, if the focused node supports the given granularity. */
  public boolean supportedGranularity(
      @Nullable AccessibilityNodeInfoCompat node, CursorGranularity granularity, EventId eventId) {
    // TODO Sometimes the lockedNode is null even if there is a focus. So pass a node
    // as an argument to handle this problem.

    // No supported granularity if no locked node.
    if (node == null) {
      return false;
    }
    if (supportedGranularities.isEmpty()) {
      return getSupportedGranularities(node).contains(granularity);
    }

    return (granularity == CursorGranularity.WEB_LANDMARK
            && WebInterfaceUtils.hasNavigableWebContent(node))
        || supportedGranularities.contains(granularity);
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
    setLockedNode(node, granularity.isMicroGranularity(), eventId);

    // If node is null, setLocked node does not populate supportedGranularities,
    // so we do it here.
    if (node == null) {
      populateMacroGranularities();
    }

    // Talkback does not support landmark granularity anymore except via talkback context menu while
    // navigating webviews and hence that won't appear in supported granularities. However users
    // should be allowed to set granularity as landmark via talkback context menu.
    boolean supportsWebLandmark =
        WebInterfaceUtils.hasNavigableWebContent(node)
            && (granularity == CursorGranularity.WEB_LANDMARK);
    if (!supportedGranularities.contains(granularity) && !supportsWebLandmark) {
      currentGranularity = CursorGranularity.DEFAULT;
      return false;
    }

    savedGranularity = granularity;
    currentGranularity = granularity;
    return true;
  }

  /** Sets granularity to default, clears locked node. */
  public void setGranularityToDefault() {
    currentGranularity = CursorGranularity.DEFAULT;
    savedGranularity = CursorGranularity.DEFAULT;
    clear();
  }

  /** Populates macro granularities in {@link #supportedGranularities}. */
  private void populateMacroGranularities() {
    this.supportedGranularities.clear();
    final List<CursorGranularity> supportedGranularities = this.supportedGranularities;
    CursorGranularity.extractFromMask(0, false, null, supportedGranularities);
  }

  /**
   * Sets the current state of selection mode for navigation within text content. When enabled, the
   * manager will attempt to extend selection during navigation within a locked node.
   *
   * @param active {@code true} to activate selection mode, {@code false} to deactivate.
   */
  public void setSelectionModeActive(boolean active) {
    if (isSelectionModeActive() != active) {
      selectionModeActive = active;
      globalVariables.setSelectionModeActive(active);
      userInterface.setSelectionMode(active);
    }
  }

  /**
   * @return {@code true} if selection mode is active.
   */
  public boolean isSelectionModeActive() {
    return selectionModeActive;
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
    // The Next/Previous granularity is unclear before populating the granularity support list, so
    // consider it a micro granularity at first to avoid skipping the necessary step(clear the
    // selection).
    setLockedNode(node, true, eventId);

    // If node is null, setLocked node does not populate supportedGranularities,
    // so we do it here.
    if (node == null) {
      populateMacroGranularities();
    }

    final int count = supportedGranularities.size();
    int currentIndex = supportedGranularities.indexOf(currentGranularity);
    int nextIndex;

    // Granularity adjustments always wrap around.
    nextIndex = (currentIndex + direction) % count;
    if (nextIndex < 0) {
      nextIndex = count - 1;
    }

    currentGranularity = supportedGranularities.get(nextIndex);
    savedGranularity = currentGranularity;
    return nextIndex != currentIndex;
  }

  /**
   * Clears the currently locked node and associated state variables. Resets the requested
   * granularity. Most often called by setLockedNode().
   */
  private void clear() {
    LogUtils.v(TAG, "Clearing the currently locked node and associated state variables");
    currentNodeIndex = 0;
    supportedGranularities.clear();
    navigableNodes.clear();
    granularityTraversal.clearAllCursors();
    lockedNode = null;

    setSelectionModeActive(false);
  }

  /** Restarts intra-node-navigation on a new node. */
  public void followTo(
      @Nullable AccessibilityNodeInfoCompat node, @SearchDirection int direction, EventId eventId) {
    // If focused-node is different from locked-node, lock the newly focused-node (updating the
    // available granularities and resetting the granular position to the start of the node).
    if ((node != null) && (lockedNode != null) && !lockedNode.equals(node)) {
      CursorGranularity currentGranularity = savedGranularity;
      clear();
      setGranularityAt(node, currentGranularity, eventId);
    }

    int unusedResult;
    // Try to automatically perform micro-granularity movement.
    if (direction == SEARCH_FOCUS_FORWARD) {
      unusedResult =
          navigateInternal(
              AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
              eventId,
              /* isEditing= */ false);
    } else if (direction == SEARCH_FOCUS_BACKWARD) {
      startFromLastNode();
      unusedResult =
          navigateInternal(
              AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
              eventId,
              /* isEditing= */ false);
    }
  }

  public void startFromLastNode() {
    currentNodeIndex = navigableNodes.size() - 1;
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
    boolean isEditing =
        (lockedNode != null)
            && (lockedNode.isEditable() || (Role.getRole(lockedNode) == Role.ROLE_EDIT_TEXT))
            && lockedNode.isFocused();
    return navigateInternal(action, eventId, isEditing);
  }

  private int navigateInternal(int action, EventId eventId, boolean isEditing) {
    LogUtils.d(TAG, "Navigate with action: " + AccessibilityNodeInfoUtils.actionToString(action));
    if (lockedNode == null) {
      return NOT_SUPPORTED;
    }

    final CursorGranularity requestedGranularity = currentGranularity;
    if ((requestedGranularity == null) || (requestedGranularity == CursorGranularity.DEFAULT)) {
      return NOT_SUPPORTED;
    }

    // Handle web granularity separately.
    if (requestedGranularity.isWebGranularity()) {
      LogUtils.d(TAG, "Granularity navigation handled by web view");
      return navigateWeb(action, requestedGranularity, eventId);
    }

    final Bundle arguments = new Bundle();
    final int count = navigableNodes.size();
    final int increment;
    boolean forward = false;

    switch (action) {
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        increment = 1;
        forward = true;
        if (currentNodeIndex < 0) {
          currentNodeIndex++;
        }
        break;
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        increment = -1;
        if (currentNodeIndex >= count) {
          currentNodeIndex--;
        }
        break;
      default:
        return NOT_SUPPORTED;
    }

    // increase coverage to check edge and play earcon.
    while ((currentNodeIndex >= -1) && (currentNodeIndex <= count)) {
      if (currentNodeIndex == -1 || currentNodeIndex == count) {
        if (isEditing) {
          // REFERTO: For editbox, to reach this point, it could be the
          // granularity movement which is not supported by the framework. We need to restore the
          // node index here, so that when user change granularity, it can operate properly.
          currentNodeIndex -= increment;
          return HIT_EDGE;
        } else {
          currentNodeIndex += increment;
          return PRE_HIT_EDGE;
        }
      }

      if (isSelectionModeActive()) {
        arguments.putBoolean(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true);
      }

      arguments.putInt(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
          requestedGranularity.value);

      final AccessibilityNodeInfoCompat currentNode = navigableNodes.get(currentNodeIndex);

      if (GranularityTraversal.shouldHandleGranularityTraversalInTalkback(currentNode)) {
        if (granularityTraversal.traverseAtGranularity(
            currentNode, requestedGranularity.value, forward, eventId)) {
          return SUCCESS;
        }
      } else if (requestedGranularity.value == AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE
          && GranularityTraversal.shouldHandleLineGranularityTraversalInTalkback(
              currentNode, forward)) {
        if (granularityTraversal.traverseAtLineGranularity(currentNode, forward, eventId)) {
          return SUCCESS;
        }
      } else if (pipeline.returnFeedback(
          eventId, Feedback.nodeAction(currentNode, action, arguments))) {
        LogUtils.d(TAG, "Granularity traversal handled by framework");
        return SUCCESS;
      }

      LogUtils.v(
          TAG, "Failed to move with granularity %s, trying next node", requestedGranularity.name());

      // If movement failed, advance to the next node and try again.
      currentNodeIndex += increment;
    }

    // Microgranularity traversal can't navigate out of a focused editable node. For non-editable
    // nodes, allow traversal out of the focused node.
    return isEditing ? HIT_EDGE : NOT_SUPPORTED;
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
    String htmlElementType;

    if ((action != AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
        && (action != AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
      return NOT_SUPPORTED;
    }

    switch (granularity) {
      case WEB_HEADING:
        htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_HEADING;
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
      case WEB_LANDMARK:
        htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_LANDMARK;
        break;
      default:
        return NOT_SUPPORTED;
    }

    if (pipeline.returnFeedback(
        eventId,
        Feedback.navigateWebByAction(
            lockedNode, action, htmlElementType, /* updateFocusHistory= */ false))) {
      return SUCCESS;
    }
    return HIT_EDGE;
  }

  /**
   * Manages the currently locked node, clearing properties and loading navigable children if
   * necessary.
   *
   * @param node The node the user wishes to navigate within.
   * @param isMicroGranularity The target granularity is micro or not.
   */
  private void setLockedNode(
      AccessibilityNodeInfoCompat node, boolean isMicroGranularity, EventId eventId) {
    // If the node is on IME and there is an editing node on the active window. Instead of locking
    // the node, TalkBack will lock the editing node to ensure cursor granularities work on the
    // cursor position.
    AccessibilityNodeInfoCompat editingNode =
        accessibilityFocusMonitor.getEditingNodeFromFocusedKeyboard(node);
    if (editingNode != null) {
      node = editingNode;
    }
    // Clear current state if text has changed even with the same node. Supported granularities
    // can be changed.
    if ((lockedNode != null)
        && (!lockedNode.equals(node)
            || !TextUtils.equals(
                AccessibilityNodeInfoUtils.getText(lockedNode),
                AccessibilityNodeInfoUtils.getText(node)))) {
      clear();
    }

    if (lockedNode == null && node != null) {
      lockedNode = node;

      if (shouldClearSelection(lockedNode, isMicroGranularity)) {
        // Always reset selection for the locked node, and reset selection for its non-focusable
        // child nodes before locking navigation to the locked node.
        resetNavigableNodesSelection(lockedNode, eventId, pipeline);
      }

      // Extract the navigable nodes and supported granularities.
      final int supportedMask = extractNavigableNodes(lockedNode, navigableNodes, new HashSet<>());
      final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(lockedNode);

      String[] supportedHtmlElements = WebInterfaceUtils.getSupportedHtmlElements(lockedNode);

      CursorGranularity.extractFromMask(
          supportedMask, hasWebContent, supportedHtmlElements, supportedGranularities);
    }
  }

  /**
   * Return whether selection should be cleared from the specified node when locking navigation to
   * it.
   *
   * @param node The node to check.
   * @param isMicroGranularity The target granularity is micro or not.
   * @return {@code true} if selection should be cleared.
   */
  private boolean shouldClearSelection(
      AccessibilityNodeInfoCompat node, boolean isMicroGranularity) {
    // Only micro granularity needs to clear the selection.
    if (!isMicroGranularity) {
      return false;
    }

    // EditText has has a stable cursor position, so don't clear selection.
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return false;
    }

    return !node.isFocused();
  }

  /**
   * Populates a list with the set of {@link CursorGranularity}s supported by the specified root
   * node and its navigable children.
   *
   * @param root The root node from which to extract granularities.
   * @return A list of supported granularities.
   */
  public @NonNull static List<CursorGranularity> getSupportedGranularities(
      @Nullable AccessibilityNodeInfoCompat root) {
    final List<CursorGranularity> supported = new ArrayList<>();
    final int supportedMask = extractNavigableNodes(root, null, new HashSet<>());
    final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(root);

    String[] supportedHtmlElements = WebInterfaceUtils.getSupportedHtmlElements(root);

    CursorGranularity.extractFromMask(
        supportedMask, hasWebContent, supportedHtmlElements, supported);

    return supported;
  }

  /**
   * Search for granularity-traversable nodes under <strong>node description tree</strong> of {@code
   * root}.
   *
   * @param root The root node of the node description tree.
   * @param nodes The list of granularity-traversable nodes collected.
   * @param visitedNodes The list of visited nodes during the node search, used to avoid infinite
   *     loop in the node tree.
   * @return The mask of supported all granularities supported by the root and child nodes.
   */
  private static int extractNavigableNodes(
      @Nullable AccessibilityNodeInfoCompat root,
      @Nullable List<AccessibilityNodeInfoCompat> nodes,
      @NonNull Set<AccessibilityNodeInfoCompat> visitedNodes) {
    int supportedGranularities = 0;
    if (root == null) {
      return supportedGranularities;
    }

    if (!visitedNodes.add(root)) {
      // Root already visited. Stop searching.
      return supportedGranularities;
    }
    if (GranularityTraversal.shouldHandleGranularityTraversalInTalkback(root)) {
      LogUtils.d(TAG, "Adding granularities supported by Talkback managed granularity navigation");
      supportedGranularities |= GranularityTraversal.TALKBACK_SUPPORTED_GRANULARITIES;
      if (nodes != null) {
        nodes.add(root);
      }

    } else if (AccessibilityNodeInfoUtils.getMovementGranularity(root) != 0) {
      if (nodes != null) {
        nodes.add(root);
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
      // Use the same traversal filter logic as what we use to compose node tree description for
      // TYPE_VIEW_ACCESSIBILITY_FOCUSED events. See named node "description_for_tree_nodes" in
      // compositor.json.
      if (FILTER_NON_FOCUSABLE_VISIBLE_NODE.accept(child)) {
        supportedGranularities |= extractNavigableNodes(child, nodes, visitedNodes);
      }
    }

    return supportedGranularities;
  }

  /**
   * Reset selection for root and granularity-traversable nodes under <strong>node description
   * tree</strong> of {@code root}.
   *
   * @param root The root node of the node description tree. loop in the node tree.
   */
  private static void resetNavigableNodesSelection(
      AccessibilityNodeInfoCompat root, EventId eventId, Pipeline.FeedbackReturner pipeline) {
    if (root == null) {
      return;
    }
    pipeline.returnFeedback(eventId, Feedback.nodeAction(root, ACTION_SET_SELECTION));

    // Traverse whole subtree, resetting selection.
    AccessibilityNodeInfoUtils.processSubtree(
        root,
        Filter.node(
            (node) ->
                FILTER_NON_FOCUSABLE_VISIBLE_NODE.accept(node)
                    && pipeline.returnFeedback(
                        eventId, Feedback.nodeAction(node, ACTION_SET_SELECTION))));
  }
}
