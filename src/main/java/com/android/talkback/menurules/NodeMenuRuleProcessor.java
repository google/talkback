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

package com.android.talkback.menurules;

import android.view.Menu;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import com.android.talkback.contextmenu.ContextMenu;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ContextSubMenu;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Rule-based processor for adding items to the local breakout menu.
 */
public class NodeMenuRuleProcessor {
    private static final LinkedList<NodeMenuRule> mRules = new LinkedList<>();
    private static final NodeMenuRule mRuleCustomAction;

    static {
        // Rules are matched in the order they are added, but any rule that
        // accepts will be able to modify the menu.
        mRules.add(new RuleSpannables());
        mRules.add(new RuleEditText());
        mRules.add(new RuleViewPager());
        mRules.add(new RuleGranularity());
        mRules.add(new RuleUnlabeledImage());
        mRules.add(new RuleSuggestions());
        mRules.add(new RuleSeekBar());
        mRuleCustomAction = new RuleCustomAction();
        mRules.add(mRuleCustomAction);
    }

    private final TalkBackService mService;

    public NodeMenuRuleProcessor(TalkBackService service) {
        mService = service;
    }

    /**
     * Populates a {@link Menu} with items specific to the provided node
     * based on {@link NodeMenuRule}s.
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
        final LinkedList<NodeMenuRule> matchingRules = new LinkedList<>();
        for (NodeMenuRule rule : mRules) {
            if (rule.accept(mService, node)) {
                matchingRules.add(rule);
            }
        }

        List<List<ContextMenuItem>> menuItems = new ArrayList<>();
        List<CharSequence> subMenuTitles = new ArrayList<>();
        boolean canCollapseMenu = false;
        for (NodeMenuRule rule : matchingRules) {
            List<ContextMenuItem> ruleResults = rule.getMenuItemsForNode(mService,
                    menu.getMenuItemBuilder(), node);
            if (ruleResults != null && ruleResults.size() > 0) {
                menuItems.add(ruleResults);
                subMenuTitles.add(rule.getUserFriendlyMenuName(mService));
            }
            canCollapseMenu |= rule.canCollapseMenu();
        }

        boolean needCollapse = canCollapseMenu && menuItems.size() == 1;
        if (needCollapse) {
            for (ContextMenuItem menuItem : menuItems.get(0)) {
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
                    subMenu.add(menuItem);
                }
            }
        }

        return menu.size() != 0;
    }

    public boolean prepareCustomActionMenuForNode(ContextMenu menu, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        // Always reset the menu since it is based on the current cursor.
        menu.clear();

        if (!mRuleCustomAction.accept(mService, node)) {
            return false;
        }

        List<ContextMenuItem> menuItems = mRuleCustomAction.getMenuItemsForNode(mService,
                menu.getMenuItemBuilder(), node);
        if (menuItems == null || menuItems.size() == 0) {
            return false;
        }

        for (ContextMenuItem menuItem : menuItems) {
            menu.add(menuItem);
        }

        return menu.size() != 0;
    }
}
