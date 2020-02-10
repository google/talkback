/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_DOWN;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_LEFT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_RIGHT;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_UP;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Rule for generating menu items related to ViewPager layouts. */
public class RuleViewPager implements NodeMenuRule {
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_PAGED =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return Role.getRole(node) == Role.ROLE_PAGER;
        }
      };

  @Override
  public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat pagerNode = null;

    try {
      rootNode = AccessibilityNodeInfoUtils.getRoot(node);
      if (rootNode == null) {
        return false;
      }

      pagerNode = AccessibilityNodeInfoUtils.searchFromBfs(rootNode, FILTER_PAGED);
      return pagerNode != null;

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(rootNode, pagerNode);
    }
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node,
      boolean includeAncestors) {
    final List<ContextMenuItem> items = new ArrayList<>();

    AccessibilityNodeInfoCompat pagerNode = null;

    try {
      pagerNode = AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_PAGED);
      if (pagerNode == null) {
        return items;
      }

      if (!includeAncestors && !pagerNode.equals(node)) {
        return items;
      }

      addPageActions(items, service, menuItemBuilder, pagerNode);

      // Check for scroll actions if no page items were added. A node with page actions shouldn't be
      // using scroll actions to navigate pages.
      if (items.isEmpty()) {
        addScrollActions(items, service, menuItemBuilder, pagerNode);
      }

      if (items.isEmpty()) {
        return items;
      }

      final AccessibilityNodeInfoCompat pagerNodeClone =
          AccessibilityNodeInfoCompat.obtain(pagerNode);
      final ViewPagerItemClickListener itemClickListener =
          new ViewPagerItemClickListener(pagerNodeClone);
      for (ContextMenuItem item : items) {
        item.setOnMenuItemClickListener(itemClickListener);
      }

      return items;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(pagerNode);
    }
  }

  /** Appends to items list. */
  private void addPageActions(
      List<ContextMenuItem> items,
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat pagerNode) {

    addMenuItemIfActionExists(
        items,
        service,
        menuItemBuilder,
        R.id.viewpager_breakout_page_up,
        R.string.title_viewpager_breakout_page_up,
        ACTION_PAGE_UP.getId(),
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        menuItemBuilder,
        R.id.viewpager_breakout_page_down,
        R.string.title_viewpager_breakout_page_down,
        ACTION_PAGE_DOWN.getId(),
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        menuItemBuilder,
        R.id.viewpager_breakout_page_left,
        R.string.title_viewpager_breakout_page_left,
        ACTION_PAGE_LEFT.getId(),
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        menuItemBuilder,
        R.id.viewpager_breakout_page_right,
        R.string.title_viewpager_breakout_page_right,
        ACTION_PAGE_RIGHT.getId(),
        pagerNode);
  }

  /** Appends to items list. */
  private void addScrollActions(
      List<ContextMenuItem> items,
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat pagerNode) {

    addMenuItemIfActionExists(
        items,
        service,
        menuItemBuilder,
        R.id.viewpager_breakout_prev_page,
        R.string.title_viewpager_breakout_prev_page,
        AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        menuItemBuilder,
        R.id.viewpager_breakout_next_page,
        R.string.title_viewpager_breakout_next_page,
        AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
        pagerNode);
  }

  /** Appends to items list. */
  private void addMenuItemIfActionExists(
      List<ContextMenuItem> items,
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      int valueResourceId,
      int titleResourceId,
      int nodeActionId,
      AccessibilityNodeInfoCompat pagerNode) {
    if (AccessibilityNodeInfoUtils.supportsAction(pagerNode, nodeActionId)) {
      items.add(
          menuItemBuilder.createMenuItem(
              service, Menu.NONE, valueResourceId, Menu.NONE, service.getString(titleResourceId)));
    }
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_viewpager_controls);
  }

  @Override
  public boolean canCollapseMenu() {
    return true;
  }

  private static class ViewPagerItemClickListener implements MenuItem.OnMenuItemClickListener {
    private final AccessibilityNodeInfoCompat node;

    public ViewPagerItemClickListener(AccessibilityNodeInfoCompat node) {
      this.node = node;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (item == null) {
        node.recycle();
        return true;
      }

      final int itemId = item.getItemId();
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
      if (itemId == R.id.viewpager_breakout_prev_page) {
        PerformActionUtils.performAction(
            node, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, eventId);
      } else if (itemId == R.id.viewpager_breakout_next_page) {
        PerformActionUtils.performAction(
            node, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, eventId);
      } else if (itemId == R.id.viewpager_breakout_page_up) {
        PerformActionUtils.performAction(node, ACTION_PAGE_UP.getId(), eventId);
      } else if (itemId == R.id.viewpager_breakout_page_down) {
        PerformActionUtils.performAction(node, ACTION_PAGE_DOWN.getId(), eventId);
      } else if (itemId == R.id.viewpager_breakout_page_left) {
        PerformActionUtils.performAction(node, ACTION_PAGE_LEFT.getId(), eventId);
      } else if (itemId == R.id.viewpager_breakout_page_right) {
        PerformActionUtils.performAction(node, ACTION_PAGE_RIGHT.getId(), eventId);
      } else {
        return false;
      }

      node.recycle();
      return true;
    }
  }
}
