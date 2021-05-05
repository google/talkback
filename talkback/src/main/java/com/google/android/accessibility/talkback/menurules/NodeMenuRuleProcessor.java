/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.accessibility.talkback.menurules;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.compositor.NodeMenuProvider;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ListSubMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Rule-based processor for adding items to the talkback breakout menu. */
public class NodeMenuRuleProcessor implements NodeMenuProvider {
  private final List<NodeMenuRule> rules = new ArrayList<>();
  private final HashMap<Integer, NodeMenuRule> nodeMenuRuleHashMap;
  private final AccessibilityService service;

  public NodeMenuRuleProcessor(
      AccessibilityService service,
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      TalkBackAnalytics analytics) {
    this.service = service;
    // Rules are matched in the order they are added, but any rule that
    // accepts will be able to modify the menu.
    RuleEditText ruleEditText = new RuleEditText(pipeline, actorState, analytics);
    rules.add(ruleEditText);
    RuleUnlabeledNode ruleUnlabeledNode = new RuleUnlabeledNode(pipeline, actorState, analytics);
    rules.add(ruleUnlabeledNode);
    RuleCustomAction ruleCustomAction = new RuleCustomAction(pipeline, analytics);
    rules.add(ruleCustomAction);

    RuleViewPager ruleViewPager = new RuleViewPager(pipeline, analytics);
    rules.add(ruleViewPager);

    RuleGranularity ruleGranularity = new RuleGranularity(pipeline, actorState, analytics);
    rules.add(ruleGranularity);

    RuleSpannables ruleSpannables = new RuleSpannables(analytics);
    rules.add(ruleSpannables);

    nodeMenuRuleHashMap = new HashMap<>();
    nodeMenuRuleHashMap.put(R.id.custom_action_menu, ruleCustomAction);
    nodeMenuRuleHashMap.put(R.id.editing_menu, ruleEditText);
    nodeMenuRuleHashMap.put(R.id.viewpager_menu, ruleViewPager);
    nodeMenuRuleHashMap.put(R.id.granularity_menu, ruleGranularity);
    nodeMenuRuleHashMap.put(R.id.links_menu, ruleSpannables);
    nodeMenuRuleHashMap.put(R.id.labeling_breakout_add_label, ruleUnlabeledNode);
  }

  /**
   * Populates items for rule menu to the provided node based on {@link NodeMenuRule}s, then it is
   * add to {@link ContextMenu}
   *
   * @param menu The specific menu that has items to add.
   * @param node The node with which to populate the item.
   * @param itemId The itemId to decide which rule for the node with which to populate the items.
   *     and set title of menu.
   */
  public void prepareRuleMenuForNode(
      ContextMenu menu, AccessibilityNodeInfoCompat node, int itemId) {
    if (node == null) {
      return;
    }

    // Always reset the menu since it is based on the current cursor.
    menu.clear();

    NodeMenuRule nodeMenuRule = nodeMenuRuleHashMap.get(itemId);

    if ((nodeMenuRule == null) || !nodeMenuRule.accept(service, node)) {
      return;
    }

    List<ContextMenuItem> menuItems =
        nodeMenuRule.getMenuItemsForNode(service, node, /* includeAncestors= */ true);
    if (menuItems == null || menuItems.isEmpty()) {
      return;
    }

    for (ContextMenuItem menuItem : menuItems) {
      setNodeMenuDefaultCloseRules(menuItem);
      menu.add(menuItem);
    }
  }

  /**
   * Populates a item or {@link ListSubMenu} with items specific to the provided node based on
   * {@link NodeMenuRule}s, then it is add to {@link ContextMenu}
   *
   * @param menu The Talkback menu that sub menu or item to add.
   * @param node The node with which to populate the menu.
   * @param itemId The itemId to decide which rule for the node with which to populate the menu.
   * @param titleId The titleId to decide which rule for the node with which to populate the menu.
   * @param itemOrder The itemOrder to decide which rule for the node with which to populate the
   *     menu.
   */
  public void prepareTalkbackMenuForNode(
      ContextMenu menu, AccessibilityNodeInfoCompat node, int itemId, int titleId, int itemOrder) {
    if (node == null) {
      return;
    }

    NodeMenuRule nodeMenuRule = nodeMenuRuleHashMap.get(itemId);

    if ((nodeMenuRule == null)
        || !nodeMenuRule.isEnabled(service)
        || !nodeMenuRule.accept(service, node)) {
      return;
    }

    List<ContextMenuItem> menuItems =
        nodeMenuRule.getMenuItemsForNode(service, node, /* includeAncestors= */ true);

    if (menuItems == null || menuItems.isEmpty()) {
      return;
    }

    if (nodeMenuRule.isSubMenu()) {
      // Adds sub menu to menu
      ListSubMenu subMenu =
          menu.addSubMenu(
              /* groupId= */ 0, itemId, itemOrder, /* title= */ service.getString(titleId));

      for (ContextMenuItem menuItem : menuItems) {
        setNodeMenuDefaultCloseRules(menuItem);
        subMenu.add(menuItem);
      }
    } else {
      // Adds item to menu
      setNodeMenuDefaultCloseRules(menuItems.get(0));
      menu.add(menuItems.get(0));
    }
  }

  /**
   * Determines whether the {@link NodeMenuRule} with the itemId is enabled.
   *
   * @param itemId The itemId to decide which rule for the node with which to populate the menu.
   * @return {@code true} if the node menu rule is enabled.
   */
  public boolean isEnabled(int itemId) {
    NodeMenuRule nodeMenuRule = nodeMenuRuleHashMap.get(itemId);
    return (nodeMenuRule != null) && nodeMenuRule.isEnabled(service);
  }

  /** Apply rules when the item has been clicked and context menu is about to close. */
  private static void setNodeMenuDefaultCloseRules(ContextMenuItem menuItem) {
    menuItem.setNeedRestoreFocus(true);
  }

  /**
   * Returns action types of menu items supported from the node itself except granularity. It uses
   * {@link NodeMenuRule#getMenuItemsForNode} to find supported menu actions and filter ones may
   * come from its ancestors.
   *
   * @param node The target node to find supported menu action types
   */
  @Override
  public List<String> getSelfNodeMenuActionTypes(AccessibilityNodeInfoCompat node) {
    List<String> menuTypes = new ArrayList<>();
    if (node == null) {
      return menuTypes;
    }

    // Track which rules accept the node.
    for (NodeMenuRule rule : rules) {
      if (rule instanceof RuleGranularity) {
        continue;
      }
      if (!rule.isEnabled(service) || !rule.accept(service, node)) {
        continue;
      }
      List<ContextMenuItem> ruleResults =
          rule.getMenuItemsForNode(service, node, /* includeAncestors= */ false);
      if (ruleResults == null || ruleResults.isEmpty()) {
        continue;
      }
      menuTypes.add(rule.getUserFriendlyMenuName(service).toString());
    }

    return menuTypes;
  }
}
