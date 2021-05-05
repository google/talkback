/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.contextmenu;

import static com.google.android.accessibility.talkback.actor.SystemActionPerformer.EXCLUDED_ACTIONS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.view.Menu;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import java.util.ArrayList;
import java.util.List;

/** Configure dynamic menu items in the window navigation context menu. */
public class WindowNavigationMenuProcessor {

  private WindowNavigationMenuProcessor() {}

  private static List<ContextMenuItem> getMenuItems(
      AccessibilityService service,
      Pipeline.FeedbackReturner pipeline) {
    if (service == null) {
      return null;
    }
    List<AccessibilityAction> actionList = service.getSystemActions();
    if (actionList == null) {
      return null;
    }
    WindowMenuItemClickListener clickListener = new WindowMenuItemClickListener(pipeline);
    List<ContextMenuItem> menuItems = new ArrayList<>();
    for (AccessibilityAction action : actionList) {
      if (!EXCLUDED_ACTIONS.contains(action.getId())) {
        ContextMenuItem val =
            ContextMenu.createMenuItem(service, 0, action.getId(), Menu.NONE, action.getLabel());
        val.setOnMenuItemClickListener(clickListener);
        menuItems.add(val);
      }
    }
    return menuItems;
  }

  /**
   * Populates a {@link android.view.SubMenu} with dynamic items relevant to the current global
   * TalkBack state. This is called when the menu is opened via global context menu.
   *
   * @param subMenu The subMenu to populate.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public static boolean prepareWindowSubMenu(
      AccessibilityService service, ListSubMenu subMenu, Pipeline.FeedbackReturner pipeline) {
    List<ContextMenuItem> menuItems = getMenuItems(service, pipeline);
    if (menuItems == null) {
      return false;
    }
    for (ContextMenuItem menuItem : menuItems) {
      subMenu.add(menuItem);
    }
    return true;
  }

  private static class WindowMenuItemClickListener implements OnContextMenuItemClickListener {
    private final Pipeline.FeedbackReturner pipeline;

    public WindowMenuItemClickListener(Pipeline.FeedbackReturner pipeline) {
      this.pipeline = pipeline;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (item == null) {
        return false;
      }
      return pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.systemAction(item.getItemId()));
    }
  }
}
