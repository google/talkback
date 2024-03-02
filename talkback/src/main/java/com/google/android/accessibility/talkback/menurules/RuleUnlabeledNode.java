/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_LABELING;

import android.content.Context;
import android.view.Menu;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.labeling.LabelDialogManager;
import com.google.android.accessibility.utils.labeling.Label;
import java.util.ArrayList;
import java.util.List;

/** Rule for generating menu item related to label. */
class RuleUnlabeledNode extends NodeMenuRule {

  private final Pipeline.FeedbackReturner pipeline;
  private final TalkBackAnalytics analytics;
  private final ActorState actorState;

  public RuleUnlabeledNode(
      Pipeline.FeedbackReturner pipeline, ActorState actorState, TalkBackAnalytics analytics) {
    super(
        R.string.pref_show_context_menu_labeling_setting_key,
        R.bool.pref_show_context_menu_labeling_default);
    this.pipeline = pipeline;
    this.analytics = analytics;
    this.actorState = actorState;
  }

  @Override
  public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
    return actorState.getLabelManagerState().supportsLabel(node);
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_labeling_controls);
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    final long viewLabelId = actorState.getLabelManagerState().getLabelIdForNode(node);
    List<ContextMenuItem> menuList = new ArrayList<>();
    String resourceName = node.getViewIdResourceName();
    ContextMenuItem item;

    if (viewLabelId == Label.NO_ID) {
      item =
          ContextMenu.createMenuItem(
              context,
              Menu.NONE,
              R.id.labeling_breakout_add_label,
              Menu.NONE,
              context.getString(R.string.label_dialog_title_add));
      item.setOnMenuItemClickListener(
          (menuItem) -> {
            LabelDialogManager.addLabel(
                context, resourceName, /* needToRestoreFocus= */ true, pipeline);
            analytics.onLocalContextMenuAction(MENU_TYPE_LABELING, item.getItemId());
            return true;
          });

    } else {
      item =
          ContextMenu.createMenuItem(
              context,
              Menu.NONE,
              R.id.labeling_breakout_edit_label,
              Menu.NONE,
              context.getString(R.string.label_dialog_title_edit));
      item.setOnMenuItemClickListener(
          (menuItem) -> {
            LabelDialogManager.editLabel(
                context, viewLabelId, /* needToRestoreFocus= */ true, pipeline);
            analytics.onLocalContextMenuAction(MENU_TYPE_LABELING, item.getItemId());
            return true;
          });
    }
    menuList.add(item);

    return menuList;
  }

  @Override
  boolean isSubMenu() {
    return false;
  }
}
