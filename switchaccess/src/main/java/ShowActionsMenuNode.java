/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.TextUtils;
import com.google.android.accessibility.switchaccess.utils.FeedbackUtils;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Leaf node of scanning tree used for performing actions on nodes and showing an actions menu for
 * nodes that support multiple actions.
 */
public class ShowActionsMenuNode extends TreeScanSystemProvidedNode {
  // TODO Support all actions, perhaps conditioned on user preferences.
  protected static final Set<Integer> FRAMEWORK_ACTIONS =
      new HashSet<>(
          Arrays.asList(
              AccessibilityNodeInfoCompat.ACTION_CLICK,
              AccessibilityNodeInfoCompat.ACTION_COLLAPSE,
              AccessibilityNodeInfoCompat.ACTION_COPY,
              AccessibilityNodeInfoCompat.ACTION_CUT,
              AccessibilityNodeInfoCompat.ACTION_DISMISS,
              AccessibilityNodeInfoCompat.ACTION_EXPAND,
              AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
              AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
              AccessibilityNodeInfoCompat.ACTION_PASTE,
              AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
              AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
              AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
              AccessibilityNodeInfoCompat.ACTION_SET_SELECTION));
  protected static final int[] MOVEMENT_GRANULARITIES_ONE_LINE = {
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD
  };
  protected static final int[] MOVEMENT_GRANULARITIES_MULTILINE = {
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD
  };
  protected static final int SYSTEM_ACTION_MAX = 0x01FFFFFF;

  // A wrapper for the SwitchAccessNodeCompat used to create the ShowActionsMenuNode (using
  // createNodeIfHasActions()) as well as any children with duplicate bounds and the actions
  // supported by this node or nodes.
  protected NodesAndActionsWrapper mNodesAndActions;

  private final Context mContext;

  /**
   * Returns a new ShowActionsMenuNode if the provided {@link SwitchAccessNodeCompat} has actions we
   * support and is visible to the user.
   *
   * @param context Context used to determine whether autoselect is enabled
   * @param nodeCompat The {@link SwitchAccessNodeCompat} based on which this node will be created
   * @return A ShowActionsMenuMode if the provided {@link SwitchAccessNodeCompat} has supported
   *     actions. Returns {@code null} otherwise}.
   */
  public static ShowActionsMenuNode createNodeIfHasActions(
      Context context, @NonNull SwitchAccessNodeCompat nodeCompat) {
    NodesAndActionsWrapper nodesAndActions = getNodesAndActions(context, nodeCompat);
    // Create a node if valid actions exist.
    if (nodesAndActions != null) {
      return new ShowActionsMenuNode(nodesAndActions, context);
    } else {
      return null;
    }
  }

  /**
   * @param nodesAndActions The {@link NodesAndActionsWrapper} that describes this node and the
   *     actions that it can perform.
   */
  public ShowActionsMenuNode(NodesAndActionsWrapper nodesAndActions, Context context) {
    mNodesAndActions = nodesAndActions;
    mContext = context;
  }

  @Override
  public Rect getRectForNodeHighlight() {
    Rect bounds = new Rect();
    mNodesAndActions.getVisibleBoundsInScreen(bounds);
    return bounds;
  }

  @Override
  public boolean isScrollable() {
    return mNodesAndActions.isScrollable(mContext);
  }

  private Rect getBoundsInScreen() {
    Rect bounds = new Rect();
    mNodesAndActions.getBoundsInScreen(bounds);
    return bounds;
  }

  @Override
  protected void getVisibleBoundsInScreen(Rect bounds) {
    mNodesAndActions.getVisibleBoundsInScreen(bounds);
  }

  @Override
  public List<MenuItem> performActionOrGetMenuItems() {
    List<SwitchAccessActionCompat> actions = mNodesAndActions.getActionCompats();
    // Return the list of available actions in the form of menu items.
    List<MenuItem> menuList = new ArrayList<>();
    for (SwitchAccessActionCompat action : actions) {
      SwitchAccessNodeCompat nodeCompat = mNodesAndActions.getNodeForAction(action);
      menuList.add(new NodeActionMenuItem(mContext, nodeCompat, action));
    }
    return menuList;
  }

  @Override
  public void recycle() {
    mNodesAndActions.recycle();
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ShowActionsMenuNode)) {
      return false;
    }

    ShowActionsMenuNode otherNode = (ShowActionsMenuNode) other;
    return otherNode.getBoundsInScreen().equals(getBoundsInScreen())
        && otherNode.mNodesAndActions.equals(mNodesAndActions);
  }

  @Override
  public int hashCode() {
    /*
     * Hashing function taken from an example in "Effective Java" page 38/39. The number 13 is
     * arbitrary, but choosing non-zero number to start decreases the number of collisions. 37
     * is used as it's an odd prime. If multiplication overflowed and the 37 was an even number,
     * it would be equivalent to bit shifting. The fact that 37 is prime is standard practice.
     */
    int hashCode = 13;
    hashCode = 37 * hashCode + getBoundsInScreen().hashCode();
    hashCode = 37 * hashCode + getClass().hashCode();
    return hashCode;
  }

  @Override
  public List<CharSequence> getSpeakableText() {
    List<CharSequence> speakableText = new LinkedList<>();
    speakableText.add(
        FeedbackUtils.getSpeakableTextForNode(mContext, mNodesAndActions.getFirstNodeCompat()));
    return speakableText;
  }

  @Override
  public SwitchAccessNodeCompat getNodeInfoCompat() {
    return mNodesAndActions.getFirstNodeCompat().obtainCopy();
  }

  protected static boolean isActionSupported(
      AccessibilityNodeInfoCompat.AccessibilityActionCompat action) {
    // White-listed framework actions
    if (action.getId() <= SYSTEM_ACTION_MAX) {
      return FRAMEWORK_ACTIONS.contains(action.getId());
    }
    // Support custom actions with proper labels.
    return !TextUtils.isEmpty(action.getLabel());
  }

  /**
   * Get the actions associated with the given compat node.
   *
   * @param context The current context
   * @param nodeCompat The node whose actions should be obtained.
   * @return A {@link NodesAndActionsWrapper} that represents this node (including any chilren with
   *     duplicate bounds) and any supported actions that can be performed on the corresponding
   *     view.
   */
  public static NodesAndActionsWrapper getNodesAndActions(
      Context context, SwitchAccessNodeCompat nodeCompat) {
    if (!nodeCompat.isVisibleToUser()) {
      return null;
    }

    // Ignore invisible nodes and those which have the same bounds as their actionable parents.
    if (nodeCompat.getHasSameBoundsAsAncestor()) {
      SwitchAccessNodeCompat parent = nodeCompat.getParent();
      // If the parent is null, ignore it. Likely, the tree will be updated soon.
      if (parent != null) {
        List<SwitchAccessActionCompat> parentActions = getActionCompatsInternal(context, parent);
        parent.recycle();
        if (!parentActions.isEmpty()) {
          // The parent is actionable, so it will hold this node's actions as well
          return null;
        }
      }
    }

    // We only care about nodes that have supported actions.
    List<SwitchAccessActionCompat> actions = getActionCompatsInternal(context, nodeCompat);
    if (actions.isEmpty()) {
      return null;
    }

    List<SwitchAccessNodeCompat> nodes = new ArrayList<>();
    nodes.add(nodeCompat.obtainCopy());

    // If child nodes have the same bounds, add their actions to this node's list of actions.
    List<SwitchAccessNodeCompat> descendantsWithSameBounds =
        nodeCompat.getDescendantsWithDuplicateBounds();
    List<SwitchAccessActionCompat> actionsFromDescendants = new ArrayList<>();
    int duplicateBoundsDisambiguationNumber = 2;
    for (int i = 0; i < descendantsWithSameBounds.size(); i++) {
      SwitchAccessNodeCompat descendantWithSameBounds = descendantsWithSameBounds.get(i);
      List<SwitchAccessActionCompat> descendantActions =
          getActionCompatsInternal(context, descendantWithSameBounds);
      // Append the child actions to the parent's list.
      if (!descendantActions.isEmpty()) {
        for (int j = 0; j < descendantActions.size(); j++) {
          descendantActions
              .get(j)
              .setNumberToAppendToDuplicateAction(duplicateBoundsDisambiguationNumber);
        }
        actionsFromDescendants.addAll(descendantActions);
        duplicateBoundsDisambiguationNumber++;
        nodes.add(descendantWithSameBounds);
      } else {
        descendantWithSameBounds.recycle();
      }
    }

    if (!actionsFromDescendants.isEmpty()) {
      // Add a disambiguation number to this node's actions.
      for (int i = 0; i < actions.size(); i++) {
        actions.get(i).setNumberToAppendToDuplicateAction(1);
      }
      actions.addAll(actionsFromDescendants);
    }

    return new NodesAndActionsWrapper(nodes, actions);
  }

  protected static List<SwitchAccessActionCompat> getActionCompatsInternal(
      Context context, SwitchAccessNodeCompat nodeCompat) {
    List<SwitchAccessActionCompat> actions = new ArrayList<>();
    List<AccessibilityActionCompat> originalActions = nodeCompat.getActionList();
    boolean autoselectEnabled = SwitchAccessPreferenceActivity.isAutoselectEnabled(context);
    for (AccessibilityActionCompat action : originalActions) {
      if (autoselectEnabled && (action.getId() == AccessibilityNodeInfoCompat.ACTION_CLICK)) {
        actions.clear();
        actions.add(new SwitchAccessActionCompat(action));
        return actions;
      }
      if (isActionSupported(action)) {
        if ((action.getId() == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
            || (action.getId()
                == AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
          /*
           * These actions can populate a long context menu, and all Views with
           * content descriptions support them. We therefore try to filter out what
           * we should surface to provide the user with exactly the set of actions that
           * are relevant to the view.
           */
          boolean canMoveInDirection = !TextUtils.isEmpty(nodeCompat.getText());
          if (canMoveInDirection
              && (nodeCompat.getTextSelectionStart() == nodeCompat.getTextSelectionEnd())) {
            // Nothing is selected.
            int cursorPosition = nodeCompat.getTextSelectionStart();
            boolean forward =
                (action.getId() == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
            canMoveInDirection &= !(forward && cursorPosition == nodeCompat.getText().length());
            canMoveInDirection &= !(!forward && cursorPosition == 0);
            canMoveInDirection &= cursorPosition >= 0;
          }
          if (nodeCompat.isEditable() && canMoveInDirection) {
            int movementGranularities = nodeCompat.getMovementGranularities();
            int[] supportedGranularities =
                (nodeCompat.isMultiLine()
                    ? MOVEMENT_GRANULARITIES_MULTILINE
                    : MOVEMENT_GRANULARITIES_ONE_LINE);
            for (int granularity : supportedGranularities) {
              if ((movementGranularities & granularity) != 0) {
                Bundle args = new Bundle();
                args.putInt(
                    AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    granularity);
                SwitchAccessActionCompat node = new SwitchAccessActionCompat(action, args);
                actions.add(node);
              }
            }
          }
        } else if (action.getId() == AccessibilityNodeInfoCompat.ACTION_SET_SELECTION) {
          // Ignore nodes that support ACTION_SET_SELECTION but are not EditTexts
          // because many nodes with visible text claim to support the action without
          // doing so, causing a lot of clutter without this restriction. The expected
          // primary use case for this action is copying or cutting paste from an editable
          // text view, which should not be affected by this pruning.
          if ((Role.getRole(nodeCompat) == Role.ROLE_EDIT_TEXT)
              && !TextUtils.isEmpty(nodeCompat.getText())) {
            Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
            args.putInt(
                AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT,
                nodeCompat.getText().length());
            SwitchAccessActionCompat node = new SwitchAccessActionCompat(action, args);
            actions.add(node);
          }
        } else {
          actions.add(new SwitchAccessActionCompat(action));
        }
      }
    }
    return actions;
  }

  private static class NodesAndActionsWrapper {
    private final List<SwitchAccessNodeCompat> mNodeCompats;
    private final List<SwitchAccessActionCompat> mActionCompats;

    /**
     * @param nodeCompats Nodes consisting of the current one as well as any children with the same
     *     bounds. If more than one node is present in this list, the number returned by {@link
     *     SwitchAccessActionCompat#getNumberToAppendToDuplicateAction} - 1 should be the index of
     *     the relevant {@link SwitchAccessNodeCompat}. The size of this list should be greater than
     *     or equal to 1.
     * @param actionCompats Actions supported by any of the {@link SwitchAccessNodeCompat} passed to
     *     the constructor. The size of this list should be greater than or equal to 1.
     */
    public NodesAndActionsWrapper(
        List<SwitchAccessNodeCompat> nodeCompats, List<SwitchAccessActionCompat> actionCompats) {
      mNodeCompats = nodeCompats;
      mActionCompats = actionCompats;
    }

    /** Returns a list of all {SwitchAccessActionCompat}s held by this object. */
    public List<SwitchAccessActionCompat> getActionCompats() {
      return mActionCompats;
    }

    /**
     * Returns the first node in the list of {@link SwitchAccessNodeCompat} used to create this
     * object. This node should correspond to the node used to create the corresponding
     * ShowActionsMenuNode.
     */
    public SwitchAccessNodeCompat getFirstNodeCompat() {
      return mNodeCompats.get(0);
    }

    /**
     * Returns the node on which the provided {SwitchAccessActionCompat}'s action should be
     * performed.
     */
    public SwitchAccessNodeCompat getNodeForAction(SwitchAccessActionCompat action) {
      int numberToAppendToDuplicateAction = action.getNumberToAppendToDuplicateAction();
      if (numberToAppendToDuplicateAction > 0) {
        return mNodeCompats.get(numberToAppendToDuplicateAction - 1);
      } else {
        return mNodeCompats.get(0);
      }
    }

    /** Returns the visible bounds of the {@link SwitchAccessNodeCompat}s held by this object. */
    public void getVisibleBoundsInScreen(Rect bounds) {
      mNodeCompats.get(0).getVisibleBoundsInScreen(bounds);
    }

    /** Returns the bounds of the {@link SwitchAccessNodeCompat}s held by this object. */
    public void getBoundsInScreen(Rect bounds) {
      mNodeCompats.get(0).getBoundsInScreen(bounds);
    }

    /**
     * Returns {@code true} if a scroll action is available to the user, {@code false} otherwise.
     * That is, if auto-select is enabled and both clickable and scrollable nodes exist, then this
     * method will return {@code false}. If auto-select is disabled and scrollable nodes exist or if
     * no clickable nodes exist and scrollable nodes exist, then this method will return {@code
     * true}
     */
    public boolean isScrollable(Context context) {
      // If auto-select is enabled, then clickable nodes will automatically be clicked.
      if (SwitchAccessPreferenceActivity.isAutoselectEnabled(context)) {
        for (SwitchAccessNodeCompat node : mNodeCompats) {
          if (node.isClickable()) {
            return false;
          }
        }
      }

      // If auto-select is disabled or there are no clickable nodes, scroll actions will be
      // available to the user.
      for (SwitchAccessNodeCompat node : mNodeCompats) {
        if (node.isScrollable()) {
          return true;
        }
      }
      return false;
    }

    /** Recycles all {@link SwitchAccessNodeCompat}s held by this object. */
    public void recycle() {
      for (SwitchAccessNodeCompat nodeCompat : mNodeCompats) {
        nodeCompat.recycle();
      }
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof NodesAndActionsWrapper)) {
        return false;
      }

      NodesAndActionsWrapper otherNodesAndActions = (NodesAndActionsWrapper) other;
      if (mNodeCompats.size() != otherNodesAndActions.mNodeCompats.size()
          || mActionCompats.size() != otherNodesAndActions.mActionCompats.size()) {
        return false;
      }

      for (int i = 0; i < mActionCompats.size(); i++) {
        if (otherNodesAndActions.mActionCompats.get(i).getId() != mActionCompats.get(i).getId()) {
          return false;
        }
      }

      return true;
    }

    @Override
    public int hashCode() {
      /*
       * Hashing function taken from an example in "Effective Java" page 38/39. The number
       * is arbitrary, but choosing non-zero number to start decreases the number of
       * collisions. 37 is used as it's an odd prime. If multiplication overflowed and the 37
       * was an even number, it would be equivalent to bit shifting. The fact that 37 is prime
       * is standard practice.
       */
      int hashCode = 13;
      hashCode = 37 * hashCode + mNodeCompats.size();
      for (SwitchAccessActionCompat actionCompat : mActionCompats) {
        hashCode = 37 * hashCode + actionCompat.getId();
      }
      hashCode = 37 * hashCode + getClass().hashCode();
      return hashCode;
    }
  }
}
