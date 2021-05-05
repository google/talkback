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
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_VIEW_PAGER;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Rule for generating menu items related to ViewPager layouts. */
public class RuleViewPager extends NodeMenuRule {
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_PAGED =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return Role.getRole(node) == Role.ROLE_PAGER;
        }
      };

  private final Pipeline.FeedbackReturner pipeline;
  private final TalkBackAnalytics analytics;

  public RuleViewPager(Pipeline.FeedbackReturner pipeline, TalkBackAnalytics analytics) {
    super(
        R.string.pref_show_context_menu_page_navigation_setting_key,
        R.bool.pref_show_context_menu_page_navigation_default);
    this.pipeline = pipeline;
    this.analytics = analytics;
  }

  @Override
  public boolean accept(AccessibilityService service, AccessibilityNodeInfoCompat node) {
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
      AccessibilityService service, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    final List<ContextMenuItem> items = new ArrayList<>();
    AccessibilityNodeInfoCompat pagerNode =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_PAGED);

    if (pagerNode == null) {
      return items;
    }
    try {
      if (!includeAncestors && !pagerNode.equals(node)) {
        return items;
      }

      addPageActions(items, service, pagerNode);

      // Check for scroll actions if no page items were added. A node with page actions shouldn't be
      // using scroll actions to navigate pages.
      if (items.isEmpty()) {
        addScrollActions(items, service, pagerNode);
      }

      if (items.isEmpty()) {
        return items;
      }

      final ViewPagerItemClickListener itemClickListener =
          new ViewPagerItemClickListener(pagerNode, pipeline, analytics);
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
      AccessibilityService service,
      AccessibilityNodeInfoCompat pagerNode) {

    addMenuItemIfActionExists(
        items,
        service,
        R.id.viewpager_breakout_page_up,
        R.string.title_viewpager_breakout_page_up,
        ACTION_PAGE_UP.getId(),
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        R.id.viewpager_breakout_page_down,
        R.string.title_viewpager_breakout_page_down,
        ACTION_PAGE_DOWN.getId(),
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        R.id.viewpager_breakout_page_left,
        R.string.title_viewpager_breakout_page_left,
        ACTION_PAGE_LEFT.getId(),
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        R.id.viewpager_breakout_page_right,
        R.string.title_viewpager_breakout_page_right,
        ACTION_PAGE_RIGHT.getId(),
        pagerNode);
  }

  /** Appends to items list. */
  private void addScrollActions(
      List<ContextMenuItem> items,
      AccessibilityService service,
      AccessibilityNodeInfoCompat pagerNode) {

    addMenuItemIfActionExists(
        items,
        service,
        R.id.viewpager_breakout_prev_page,
        R.string.title_viewpager_breakout_prev_page,
        AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
        pagerNode);

    addMenuItemIfActionExists(
        items,
        service,
        R.id.viewpager_breakout_next_page,
        R.string.title_viewpager_breakout_next_page,
        AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
        pagerNode);
  }

  /** Appends to items list. */
  private void addMenuItemIfActionExists(
      List<ContextMenuItem> items,
      AccessibilityService service,
      int valueResourceId,
      int titleResourceId,
      int nodeActionId,
      AccessibilityNodeInfoCompat pagerNode) {
    if (AccessibilityNodeInfoUtils.supportsAction(pagerNode, nodeActionId)) {
      items.add(
          ContextMenu.createMenuItem(
              service, Menu.NONE, valueResourceId, Menu.NONE, service.getString(titleResourceId)));
    }
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_viewpager_controls);
  }

  /** Listener may be shared by multi-contextItems. */
  private static class ViewPagerItemClickListener extends AbstractOnContextMenuItemClickListener {

    public ViewPagerItemClickListener(
        AccessibilityNodeInfoCompat node,
        Pipeline.FeedbackReturner pipeline,
        TalkBackAnalytics analytics) {
      super(node, pipeline, analytics);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      try {
        if (item == null) {
          return true;
        }

        final int itemId = item.getItemId();
        analytics.onLocalContextMenuAction(MENU_TYPE_VIEW_PAGER, itemId);
        EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
        if (itemId == R.id.viewpager_breakout_prev_page) {
          pipeline.returnFeedback(
              eventId,
              Feedback.nodeAction(node, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD));
        } else if (itemId == R.id.viewpager_breakout_next_page) {
          pipeline.returnFeedback(
              eventId,
              Feedback.nodeAction(node, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD));
        } else if (itemId == R.id.viewpager_breakout_page_up) {
          pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_PAGE_UP.getId()));
        } else if (itemId == R.id.viewpager_breakout_page_down) {
          pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_PAGE_DOWN.getId()));
        } else if (itemId == R.id.viewpager_breakout_page_left) {
          pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_PAGE_LEFT.getId()));
        } else if (itemId == R.id.viewpager_breakout_page_right) {
          pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_PAGE_RIGHT.getId()));
        } else {
          return false;
        }
        return true;
      } finally {
        clear();
      }
    }
  }
}
