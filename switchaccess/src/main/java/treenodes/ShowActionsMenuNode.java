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

package com.google.android.accessibility.switchaccess.treenodes;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Trace;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.view.accessibility.AccessibilityWindowInfo;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.SwitchAccessAction;
import com.google.android.accessibility.switchaccess.SwitchAccessActionBase;
import com.google.android.accessibility.switchaccess.SwitchAccessActionGroup;
import com.google.android.accessibility.switchaccess.SwitchAccessActionTimeline;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.menuitems.GroupedMenuItemWithTextAction;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import com.google.android.accessibility.switchaccess.menuitems.NodeActionMenuItem;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import com.google.android.accessibility.switchaccess.utils.ActionBuildingUtils;
import com.google.android.accessibility.switchaccess.utils.FeedbackUtils;
import com.google.android.accessibility.switchaccess.utils.TextEditingUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Leaf node of scanning tree used for performing actions on nodes and showing an actions menu for
 * nodes that support multiple actions.
 */
public class ShowActionsMenuNode extends TreeScanSystemProvidedNode {

  // The SwitchAccessNodeCompat used to create the ShowActionsMenuNode (using
  // createNodeIfHasActions()) as well as any children with duplicate bounds that support actions
  private final List<SwitchAccessNodeCompat> nodeCompats;

  // The ActionTimeline used to track the actions that are performed on this node.
  final SwitchAccessActionTimeline actionTimeline;

  private final AccessibilityService service;

  /**
   * Returns a new ShowActionsMenuNode if the provided {@link SwitchAccessNodeCompat} has actions we
   * support and is visible to the user.
   *
   * @param service AccessibilityService used to determine whether autoselect is enabled
   * @param nodeCompat The {@link SwitchAccessNodeCompat} based on which this node will be created
   * @return A ShowActionsMenuMode if the provided {@link SwitchAccessNodeCompat} has supported
   *     actions. Returns {@code null} otherwise}
   */
  @Nullable
  public static ShowActionsMenuNode createNodeIfHasActions(
      AccessibilityService service, SwitchAccessNodeCompat nodeCompat) {
    List<SwitchAccessNodeCompat> nodeCompats = getNodesIfHasActions(nodeCompat);
    // Create a node if valid actions exist.
    if (nodeCompats != null) {
      return new ShowActionsMenuNode(
          nodeCompats, service, new SwitchAccessActionTimeline(nodeCompat));
    } else {
      return null;
    }
  }

  /**
   * @param nodeCompats A list containing a {@link SwitchAccessNodeCompat} and any of its children
   *     with duplicate bounds that support actions
   * @param service AccessibilityService used to determine whether autoselect is enabled
   * @param actionTimeline The {@link SwitchAccessActionTimeline} used to track to actions performed
   *     on this node
   */
  public ShowActionsMenuNode(
      List<SwitchAccessNodeCompat> nodeCompats,
      AccessibilityService service,
      SwitchAccessActionTimeline actionTimeline) {
    this.nodeCompats = nodeCompats;
    this.service = service;
    this.actionTimeline = actionTimeline;
  }

  @Override
  public Rect getRectForNodeHighlight() {
    Rect bounds = new Rect();
    getVisibleBoundsInScreen(bounds);
    if (Role.getRole(nodeCompats.get(0)) == Role.ROLE_EDIT_TEXT) {
      bounds.left -= TextEditingUtils.EDIT_TEXT_HORIZONTAL_PADDING_PX;
      bounds.right += TextEditingUtils.EDIT_TEXT_HORIZONTAL_PADDING_PX;
    }
    return bounds;
  }

  /**
   * Returns {@code true} if a scroll action is available to the user, {@code false} otherwise. That
   * is, if auto-select is enabled and both clickable and scrollable nodes exist, then this method
   * will return {@code false}. If auto-select is disabled and scrollable nodes exist or if no
   * clickable nodes exist and scrollable nodes exist, then this method will return {@code true}
   */
  @Override
  public boolean isScrollable() {
    // If auto-select is enabled, then clickable nodes will automatically be clicked.
    if (SwitchAccessPreferenceUtils.isAutoselectEnabled(service)) {
      for (SwitchAccessNodeCompat node : nodeCompats) {
        if (node.isClickable()) {
          return false;
        }
      }
    }

    // If auto-select is disabled or there are no clickable nodes, scroll actions will be
    // available to the user.
    for (SwitchAccessNodeCompat node : nodeCompats) {
      if (node.isScrollable()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<AccessibilityActionCompat> getActionList() {
    List<AccessibilityActionCompat> actions = new ArrayList<>();
    for (SwitchAccessNodeCompat node : nodeCompats) {
      actions.addAll(node.getActionList());
    }
    return actions;
  }

  /** Returns whether this node corresponds to an item on the IME. */
  public boolean isImeWindowType() {
    return AccessibilityNodeInfoUtils.getWindowType(nodeCompats.get(0))
        == AccessibilityWindowInfo.TYPE_INPUT_METHOD;
  }

  /** Returns whether this node support multiple Switch Access actions. */
  public boolean hasMultipleSwitchAccessActions() {
    return ActionBuildingUtils.hasMultipleActions(service, nodeCompats);
  }

  @Override
  protected boolean hasSimilarText(TreeScanSystemProvidedNode other) {
    // If the nodes are editable or toggleable, the text can change.
    SwitchAccessNodeCompat firstNode = nodeCompats.get(0);
    SwitchAccessNodeCompat otherFirstNode = ((ShowActionsMenuNode) other).nodeCompats.get(0);
    return ((firstNode.isEditable() && otherFirstNode.isEditable())
        || (firstNode.isCheckable() && otherFirstNode.isCheckable())
        || firstNode.getNodeText().toString().contentEquals(otherFirstNode.getNodeText()));
  }

  @Override
  protected SwitchAccessNodeCompat getNodeInfoCompatDirectly() {
    return nodeCompats.get(0);
  }

  @Override
  protected void getVisibleBoundsInScreen(Rect bounds) {
    nodeCompats.get(0).getVisibleBoundsInScreen(bounds);
  }

  @Override
  public List<MenuItem> performActionOrGetMenuItems(
      @Nullable SelectMenuItemListener selectMenuItemListener) {
    List<SwitchAccessActionBase> actions = getActions(service);
    // Return the list of available actions in the form of menu items.
    List<MenuItem> menuList = new ArrayList<>(actions.size());
    for (SwitchAccessActionBase action : actions) {
      SwitchAccessNodeCompat nodeCompat = getNodeForAction(action);
      if (action instanceof SwitchAccessAction) {
        SwitchAccessAction switchAccessAction = (SwitchAccessAction) action;
        menuList.add(
            new NodeActionMenuItem(
                service, nodeCompat, switchAccessAction, actionTimeline, selectMenuItemListener));
      } else { // action instanceof SwitchAccessActionGroup
        SwitchAccessActionGroup switchAccessActionGroup = (SwitchAccessActionGroup) action;
        OverlayController overlayController =
            ((SwitchAccessService) service).getOverlayController();
        menuList.add(
            new GroupedMenuItemWithTextAction(
                service,
                nodeCompat,
                switchAccessActionGroup,
                overlayController,
                actionTimeline,
                selectMenuItemListener));
      }
    }
    return menuList;
  }

  @Override
  public void recycle() {
    for (SwitchAccessNodeCompat nodeCompat : nodeCompats) {
      nodeCompat.recycle();
    }
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (!(other instanceof ShowActionsMenuNode)) {
      return false;
    }

    ShowActionsMenuNode otherNode = (ShowActionsMenuNode) other;
    if ((nodeCompats.size() != otherNode.nodeCompats.size())
        || !otherNode.getBoundsInScreen().equals(getBoundsInScreen())) {
      return false;
    }

    SwitchAccessNodeCompat firstNode = nodeCompats.get(0);
    SwitchAccessNodeCompat otherFirstNode = otherNode.nodeCompats.get(0);

    // If the nodes are editable, they can still be equal without having the same text because the
    // text can change. If they're not editable, they must have the same text.
    return (firstNode.isChecked() == otherFirstNode.isChecked())
        && ((firstNode.isEditable() && otherFirstNode.isEditable())
            || firstNode.getNodeText().toString().contentEquals(otherFirstNode.getNodeText()));
  }

  @Override
  public boolean isProbablyTheSameAs(Object other) {
    if (!(other instanceof ShowActionsMenuNode)) {
      return false;
    }

    return super.isProbablyTheSameAs(other);
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
    speakableText.add(FeedbackUtils.getSpeakableTextForActionableNode(service, nodeCompats.get(0)));
    return speakableText;
  }

  /**
   * Get the nodes associated with the given compat node. These include the given {@link
   * SwitchAccessNodeCompat} and any children with the same bounds that support actions. If the
   * given node does not have any actions, this returns null.
   *
   * @param nodeCompat The node whose associated nodes should be obtained
   * @return If this node has actions, a list of {@link SwitchAccessNodeCompat} that represents this
   *     node and any children with duplicate bounds that also support actions. Returns {@code null}
   *     otherwise
   */
  @Nullable
  private static List<SwitchAccessNodeCompat> getNodesIfHasActions(
      SwitchAccessNodeCompat nodeCompat) {
    Trace.beginSection("ShowActionsMenuNode#createNodeIfHasActions");
    // Ignore invisible nodes, disabled nodes, and nodes without actions. This is checked first as
    // these are the most common reasons to ignore the node.
    if (!nodeCompat.isEnabled() || !nodeCompat.hasActions() || !nodeCompat.isVisibleToUser()) {
      Trace.endSection();
      return null;
    }

    // Ignore nodes which have the same bounds as their actionable parents.
    if (nodeCompat.getHasSameBoundsAsAncestor()) {
      SwitchAccessNodeCompat parent = nodeCompat.getParent();
      // If the parent is null, ignore it. Likely, the tree will be updated soon.
      if (parent != null) {
        boolean parentHasActions = parent.hasActions();
        parent.recycle();
        if (parentHasActions) {
          // The parent is actionable, so it will hold this node's actions as well
          Trace.endSection();
          return null;
        }
      }
    }

    List<SwitchAccessNodeCompat> nodes = new ArrayList<>();
    nodes.add(nodeCompat.obtainCopy());

    // If child nodes have the same bounds, add to list of nodes.
    List<SwitchAccessNodeCompat> descendantsWithSameBounds =
        nodeCompat.getDescendantsWithDuplicateBounds();
    for (int i = 0; i < descendantsWithSameBounds.size(); i++) {
      SwitchAccessNodeCompat descendantWithSameBounds = descendantsWithSameBounds.get(i);
      if (descendantWithSameBounds.hasActions()) {
        nodes.add(descendantWithSameBounds);
      } else {
        descendantWithSameBounds.recycle();
      }
    }

    Trace.endSection();
    return nodes;
  }

  /*
   * Get the actions associated with the given compat node and any children with the same bounds.
   *
   * @param service The current {@link AccessibilityService}
   * @return A list of {@link SwitchAccessActionBase}s that represents any supported actions that
   *     can be performed on the corresponding view by the given {@link SwitchAccessNodeCompat} and
   *     any children with duplicate bounds
   */
  private List<SwitchAccessActionBase> getActions(AccessibilityService service) {
    List<SwitchAccessActionBase> actions =
        ActionBuildingUtils.getActionsForNode(service, nodeCompats.get(0), actionTimeline);

    // If child nodes have the same bounds, add their actions to this node's list of actions.
    List<SwitchAccessActionBase> actionsFromDescendants = new ArrayList<>();
    int duplicateBoundsDisambiguationNumber = 2;
    for (int i = 1; i < nodeCompats.size(); i++) {
      List<SwitchAccessActionBase> descendantActions =
          ActionBuildingUtils.getActionsForNode(service, nodeCompats.get(i), actionTimeline);
      // Append the child actions to the parent's list.
      if (!descendantActions.isEmpty()) {
        for (int j = 0; j < descendantActions.size(); j++) {
          descendantActions
              .get(j)
              .setNumberToAppendToDuplicateAction(duplicateBoundsDisambiguationNumber);
        }
        actionsFromDescendants.addAll(descendantActions);
        duplicateBoundsDisambiguationNumber++;
      }
    }

    if (!actionsFromDescendants.isEmpty()) {
      // Add a disambiguation number to this node's actions.
      for (int i = 0; i < actions.size(); i++) {
        actions.get(i).setNumberToAppendToDuplicateAction(1);
      }
      actions.addAll(actionsFromDescendants);
    }

    return actions;
  }

  /*
   * Returns the node on which the provided {SwitchAccessActionBase}'s action should be performed.
   */
  private SwitchAccessNodeCompat getNodeForAction(SwitchAccessActionBase action) {
    int numberToAppendToDuplicateAction = action.getNumberToAppendToDuplicateAction();
    if (numberToAppendToDuplicateAction > 0) {
      return nodeCompats.get(numberToAppendToDuplicateAction - 1);
    } else {
      return nodeCompats.get(0);
    }
  }
}
