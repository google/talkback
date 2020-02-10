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
import android.os.Build;
import android.os.Build.VERSION_CODES;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.ArrayList;
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
      AccessibilityNodeInfoCompat node,
      boolean includeAncestors) {
    List<ContextMenuItem> menu = new ArrayList<>();
    populateMenuItemsForNode(service, menuItemBuilder, node, menu, includeAncestors);
    return menu;
  }

  /**
   * Populates a menu with the context menu items for a node, searching up its ancestor hierarchy if
   * the current node has no custom actions.
   *
   * @param service The parent service.
   * @param menuItemBuilder builder to create menu items
   * @param node The node to process
   * @param includeAncestors sets to {@code false} not to search its ancestor
   */
  private void populateMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node,
      List<ContextMenuItem> menu,
      boolean includeAncestors) {
    if (node == null) {
      return;
    }

    for (AccessibilityActionCompat action : node.getActionList()) {
      CharSequence label = "";
      int id = action.getId();
      // On Android O, sometime TalkBack get fails on performing actions (mostly on notification
      // shelf). And deferring the action make the but unreproducible. See  for details.
      boolean deferToWindowsSrable = false;

      if (AccessibilityNodeInfoUtils.isCustomAction(action)) {
        label = action.getLabel();
      } else if (id == AccessibilityNodeInfoCompat.ACTION_DISMISS) {
        label = service.getString(R.string.title_action_dismiss);
        deferToWindowsSrable = true;
      } else if (id == AccessibilityNodeInfoCompat.ACTION_EXPAND) {
        label = service.getString(R.string.title_action_expand);
        deferToWindowsSrable = true;
      } else if (id == AccessibilityNodeInfoCompat.ACTION_COLLAPSE) {
        label = service.getString(R.string.title_action_collapse);
        deferToWindowsSrable = true;
      }

      if (TextUtils.isEmpty(label)) {
        continue;
      }

      ContextMenuItem item =
          menuItemBuilder.createMenuItem(service, Menu.NONE, id, Menu.NONE, label);
      item.setOnMenuItemClickListener(
          new CustomMenuItem(id, AccessibilityNodeInfoCompat.obtain(node)));
      if ((Build.VERSION.SDK_INT == VERSION_CODES.O || Build.VERSION.SDK_INT == VERSION_CODES.O_MR1)
          && deferToWindowsSrable) {
        item.setDeferredType(DeferredType.WINDOWS_STABLE);
      }
      item.setCheckable(false);
      menu.add(item);
    }

    if (!includeAncestors) {
      return;
    }

    if (menu.isEmpty()) {
      populateMenuItemsForNode(
          service, menuItemBuilder, node.getParent(), menu, /* includeAncestors= */ true);
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
    final int id;
    final AccessibilityNodeInfoCompat node;

    CustomMenuItem(int id, AccessibilityNodeInfoCompat node) {
      this.id = id;
      this.node = node;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
      boolean ret = PerformActionUtils.performAction(node, id, eventId);
      node.recycle();
      return ret;
    }
  }
}
