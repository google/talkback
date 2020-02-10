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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import com.google.android.accessibility.compositor.NodeMenuProvider;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextSubMenu;
import com.google.android.accessibility.talkback.contextmenu.ListMenu;
import java.util.ArrayList;
import java.util.List;

/** Rule-based processor for adding items to the local breakout menu. */
public class NodeMenuRuleProcessor implements NodeMenuProvider {
  private final List<NodeMenuRule> rules = new ArrayList<>();
  private NodeMenuRule ruleCustomAction;
  private NodeMenuRule ruleEditText;

  private final TalkBackService service;

  public NodeMenuRuleProcessor(
      TalkBackService service, Pipeline.FeedbackReturner pipeline, ActorState actorState) {
    this.service = service;
    // Rules are matched in the order they are added, but any rule that
    // accepts will be able to modify the menu.
    ruleEditText = new RuleEditText(pipeline, actorState);
    rules.add(ruleEditText);
    rules.add(new RuleSpannables());
    rules.add(new RuleUnlabeledImage(pipeline));
    rules.add(new RuleSeekBar(pipeline));
    ruleCustomAction = new RuleCustomAction();
    rules.add(ruleCustomAction);
    rules.add(new RuleViewPager());
    rules.add(new RuleGranularity(pipeline, actorState));
  }

  /**
   * Populates a {@link Menu} with items specific to the provided node based on {@link
   * NodeMenuRule}s.
   *
   * @param menu The menu to populate.
   * @param node The node with which to populate the menu.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public boolean prepareMenuForNode(ContextMenu menu, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    // Always reset the menu since it is based on the current cursor.
    menu.clear();

    // Track which rules accept the node.
    final List<NodeMenuRule> matchingRules = new ArrayList<>();
    for (NodeMenuRule rule : rules) {
      if (rule.accept(service, node)) {
        matchingRules.add(rule);
      }
    }

    List<List<ContextMenuItem>> menuItems = new ArrayList<>();
    List<CharSequence> subMenuTitles = new ArrayList<>();
    boolean canCollapseMenu = false;
    for (NodeMenuRule rule : matchingRules) {
      List<ContextMenuItem> ruleResults =
          rule.getMenuItemsForNode(
              service, menu.getMenuItemBuilder(), node, /* includeAncestors= */ true);
      if (ruleResults != null && ruleResults.size() > 0) {
        menuItems.add(ruleResults);
        subMenuTitles.add(rule.getUserFriendlyMenuName(service));
      }
      canCollapseMenu |= rule.canCollapseMenu();
    }

    boolean needCollapse = canCollapseMenu && menuItems.size() == 1;
    if (needCollapse) {
      for (ContextMenuItem menuItem : menuItems.get(0)) {
        setNodeMenuDefaultCloseRules(menuItem);
        menu.add(menuItem);
      }
    } else {
      int size = menuItems.size();
      for (int i = 0; i < size; i++) {
        List<ContextMenuItem> items = menuItems.get(i);
        CharSequence subMenuName = subMenuTitles.get(i);
        ContextSubMenu subMenu = menu.addSubMenu(0, 0, 0, subMenuName);
        subMenu.getItem().setEnabled(true);
        for (ContextMenuItem menuItem : items) {
          setNodeMenuDefaultCloseRules(menuItem);
          subMenu.add(menuItem);
        }
      }
    }

    return menu.size() != 0;
  }

  public boolean prepareCustomActionMenuForNode(
      ContextMenu menu, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    // Always reset the menu since it is based on the current cursor.
    menu.clear();

    if (!ruleCustomAction.accept(service, node)) {
      return false;
    }

    List<ContextMenuItem> menuItems =
        ruleCustomAction.getMenuItemsForNode(
            service, menu.getMenuItemBuilder(), node, /* includeAncestors= */ true);
    if (menuItems == null || menuItems.size() == 0) {
      return false;
    }

    for (ContextMenuItem menuItem : menuItems) {
      setNodeMenuDefaultCloseRules(menuItem);
      menu.add(menuItem);
    }

    return menu.size() != 0;
  }

  public boolean prepareEditingMenuForNode(ContextMenu menu, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    // Always reset the menu since it is based on the current cursor.
    menu.clear();

    if (!ruleEditText.accept(service, node)) {
      return false;
    }

    List<ContextMenuItem> menuItems =
        ruleEditText.getMenuItemsForNode(
            service, menu.getMenuItemBuilder(), node, /* includeAncestors= */ true);
    if (menuItems == null || menuItems.size() == 0) {
      return false;
    }

    for (ContextMenuItem menuItem : menuItems) {
      setNodeMenuDefaultCloseRules(menuItem);
      menu.add(menuItem);
    }

    return menu.size() != 0;
  }

  /** Apply rules when the item has been clicked and context menu is about to close. */
  private void setNodeMenuDefaultCloseRules(ContextMenuItem menuItem) {
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
      if (!rule.accept(service, node)) {
        continue;
      }
      List<ContextMenuItem> ruleResults =
          rule.getMenuItemsForNode(
              service,
              new ListMenu(service).getMenuItemBuilder(),
              node,
              /* includeAncestors= */ false);
      if (ruleResults == null || ruleResults.size() == 0) {
        continue;
      }
      menuTypes.add(rule.getUserFriendlyMenuName(service).toString());
    }

    return menuTypes;
  }
}
