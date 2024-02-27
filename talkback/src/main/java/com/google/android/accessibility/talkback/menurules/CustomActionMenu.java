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

import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_ITEM_UNKNOWN;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_CUSTOM_ACTION;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adds custom actions to the talkback context menu. */
public class CustomActionMenu implements NodeMenu {
  private static final String TAG = "CustomActionMenu";
  private final Pipeline.FeedbackReturner pipeline;
  TalkBackAnalytics analytics;

  public CustomActionMenu(Pipeline.FeedbackReturner pipeline, TalkBackAnalytics analytics) {
    this.pipeline = pipeline;
    this.analytics = analytics;
  }

  @Override
  public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
    return acceptCustomActionMenu(node);
  }

  private static boolean acceptCustomActionMenu(AccessibilityNodeInfoCompat node) {
    List<AccessibilityActionCompat> actions = node.getActionList();
    return (actions != null && !actions.isEmpty());
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    List<ContextMenuItem> customItems = new ArrayList<>();
    HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    if (acceptCustomActionMenu(node)) {
      populateCustomMenuItemsForNode(context, node, customItems, includeAncestors, visitedNodes);
    }
    return customItems;
  }

  /**
   * Populates a menu with the context menu items for a node, searching up its ancestor hierarchy if
   * the current node has no custom actions.
   *
   * @param context The parent context.
   * @param node The node to process
   * @param includeAncestors sets to {@code false} not to search its ancestor
   * @param visitedNodes keeps all traversed node
   */
  private void populateCustomMenuItemsForNode(
      Context context,
      AccessibilityNodeInfoCompat node,
      List<ContextMenuItem> menu,
      boolean includeAncestors,
      Set<AccessibilityNodeInfoCompat> visitedNodes) {
    if (node == null) {
      return;
    }
    if (visitedNodes.contains(node)) {
      LogUtils.w(TAG, "View node tree contains a loop.");
      return;
    }
    visitedNodes.add(node);

    for (AccessibilityActionCompat action : node.getActionList()) {
      CharSequence label = "";
      int id = action.getId();
      // On Android O, sometime TalkBack get fails on performing actions (mostly on notification
      // shelf). And deferring the action make the but unreproducible. REFERTO.
      boolean deferToWindowsSrable = false;

      if (AccessibilityNodeInfoUtils.isCustomAction(action)) {
        label = action.getLabel();
      } else if (id == AccessibilityNodeInfoCompat.ACTION_DISMISS) {
        label = context.getString(R.string.title_action_dismiss);
        deferToWindowsSrable = true;
      } else if (id == AccessibilityNodeInfoCompat.ACTION_EXPAND) {
        label = context.getString(R.string.title_action_expand);
        deferToWindowsSrable = true;
      } else if (id == AccessibilityNodeInfoCompat.ACTION_COLLAPSE) {
        label = context.getString(R.string.title_action_collapse);
        deferToWindowsSrable = true;
      } else if (FeatureSupport.supportDragAndDrop()) {
        if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START.getId()) {
          // TODO: Replace with AndroidX constants
          label =
              action.getLabel() == null
                  ? context.getString(R.string.title_action_drag_start)
                  : action.getLabel();
        } else if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP.getId()) {
          label =
              action.getLabel() == null
                  ? context.getString(R.string.title_action_drag_drop)
                  : action.getLabel();
        } else if (id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL.getId()) {
          label =
              action.getLabel() == null
                  ? context.getString(R.string.title_action_drag_cancel)
                  : action.getLabel();
        }
      }

      if (TextUtils.isEmpty(label)) {
        continue;
      }

      ContextMenuItem item = ContextMenu.createMenuItem(context, Menu.NONE, id, Menu.NONE, label);
      item.setOnMenuItemClickListener(
          new CustomMenuItemClickListener(id, node, pipeline, analytics));
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
      populateCustomMenuItemsForNode(
          context, node.getParent(), menu, /* includeAncestors= */ true, visitedNodes);
    }
  }

  /** Listener may be shared by multi-contextItems. */
  private static class CustomMenuItemClickListener extends AbstractOnContextMenuItemClickListener {
    private final int id;

    CustomMenuItemClickListener(
        int id,
        AccessibilityNodeInfoCompat node,
        Pipeline.FeedbackReturner pipeline,
        TalkBackAnalytics analytics) {
      super(node, pipeline, analytics);
      this.id = id;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      try {
        EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
        boolean ret = pipeline.returnFeedback(eventId, Feedback.nodeAction(node, id));
        switch (id) {
          case AccessibilityNodeInfoCompat.ACTION_DISMISS:
          case AccessibilityNodeInfoCompat.ACTION_EXPAND:
          case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
            analytics.onLocalContextMenuAction(MENU_TYPE_CUSTOM_ACTION, id);
            break;
          default:
            analytics.onLocalContextMenuAction(MENU_TYPE_CUSTOM_ACTION, MENU_ITEM_UNKNOWN);
            break;
        }

        return ret;
      } finally {
        clear();
      }
    }
  }
}
