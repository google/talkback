/*
 * Copyright (C) 2014 Google Inc.
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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.LinkedList;
import java.util.List;

/** Adds custom actions to the local context menu. */
public class RuleCustomAction implements NodeMenuRule {
  @Override
  public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
    List<AccessibilityActionCompat> actions = node.getActionList();
    return actions != null && !actions.isEmpty();
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node) {
    List<ContextMenuItem> menu = new LinkedList<>();
    recursivelyPopulateMenuItemsForNode(service, menuItemBuilder, node, menu);

    return menu;
  }

  /**
   * Populates a menu with the context menu items for a node, searching up its ancestor hierarchy if
   * the current node has no custom actions.
   */
  private void recursivelyPopulateMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node,
      List<ContextMenuItem> menu) {
    if (node == null) {
      return;
    }

    for (AccessibilityActionCompat action : node.getActionList()) {
      CharSequence label = "";
      int id = action.getId();

      if (AccessibilityNodeInfoUtils.isCustomAction(action)) {
        label = action.getLabel();
      } else if (id == AccessibilityNodeInfoCompat.ACTION_DISMISS) {
        label = service.getString(R.string.title_action_dismiss);
      } else if (id == AccessibilityNodeInfoCompat.ACTION_EXPAND) {
        label = service.getString(R.string.title_action_expand);
      } else if (id == AccessibilityNodeInfoCompat.ACTION_COLLAPSE) {
        label = service.getString(R.string.title_action_collapse);
      }

      if (TextUtils.isEmpty(label)) {
        continue;
      }

      ContextMenuItem item =
          menuItemBuilder.createMenuItem(service, Menu.NONE, id, Menu.NONE, label);
      item.setOnMenuItemClickListener(
          new CustomMenuItem(id, AccessibilityNodeInfoCompat.obtain(node)));
      item.setCheckable(false);
      menu.add(item);
    }

    if (menu.isEmpty()) {
      recursivelyPopulateMenuItemsForNode(service, menuItemBuilder, node.getParent(), menu);
    }
  }

  @Override
  public boolean canCollapseMenu() {
    return true;
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_custom_action);
  }

  private static class CustomMenuItem implements MenuItem.OnMenuItemClickListener {
    final int mId;
    final AccessibilityNodeInfoCompat mNode;

    CustomMenuItem(int id, AccessibilityNodeInfoCompat node) {
      mId = id;
      mNode = node;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
      boolean ret = PerformActionUtils.performAction(mNode, mId, eventId);
      mNode.recycle();
      return ret;
    }
  }
}
