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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
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
import java.util.LinkedList;
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
      AccessibilityNodeInfoCompat node) {
    final LinkedList<ContextMenuItem> items = new LinkedList<>();

    AccessibilityNodeInfoCompat pagerNode = null;

    try {
      pagerNode = AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_PAGED);
      if (pagerNode == null) {
        return items;
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          pagerNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)) {
        final ContextMenuItem prevPage =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.viewpager_breakout_prev_page,
                Menu.NONE,
                service.getString(R.string.title_viewpager_breakout_prev_page));
        items.add(prevPage);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          pagerNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)) {
        final ContextMenuItem nextPage =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.viewpager_breakout_next_page,
                Menu.NONE,
                service.getString(R.string.title_viewpager_breakout_next_page));
        items.add(nextPage);
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

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_viewpager_controls);
  }

  @Override
  public boolean canCollapseMenu() {
    return true;
  }

  private static class ViewPagerItemClickListener implements MenuItem.OnMenuItemClickListener {
    private final AccessibilityNodeInfoCompat mNode;

    public ViewPagerItemClickListener(AccessibilityNodeInfoCompat node) {
      mNode = node;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (item == null) {
        mNode.recycle();
        return true;
      }

      final int itemId = item.getItemId();
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
      if (itemId == R.id.viewpager_breakout_prev_page) {
        PerformActionUtils.performAction(
            mNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, eventId);
      } else if (itemId == R.id.viewpager_breakout_next_page) {
        PerformActionUtils.performAction(
            mNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, eventId);
      } else {
        return false;
      }

      mNode.recycle();
      return true;
    }
  }
}
