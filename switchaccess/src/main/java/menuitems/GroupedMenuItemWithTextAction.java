/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess.menuitems;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT;
import static com.google.android.accessibility.switchaccess.utils.TextEditingUtils.ACTION_GRANULARITY_ALL;
import static com.google.android.accessibility.switchaccess.utils.TextEditingUtils.ACTION_GRANULARITY_SENTENCE;
import static com.google.android.accessibility.switchaccess.utils.TextEditingUtils.MOVEMENT_GRANULARITIES_MULTILINE;
import static com.google.android.accessibility.switchaccess.utils.TextEditingUtils.MOVEMENT_GRANULARITIES_ONE_LINE;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessAction;
import com.google.android.accessibility.switchaccess.SwitchAccessActionGroup;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import com.google.android.accessibility.switchaccess.utils.TextEditingUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.libraries.accessibility.utils.undo.ActionTimeline;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Holds data required to create a menu item that corresponds to a group of closely related actions
 * for a specific {@link AccessibilityNodeInfoCompat}. When clicked, this GroupedMenuItem creates
 * new menu items for each of these related actions.
 */
public class GroupedMenuItemWithTextAction extends GroupedMenuItem {

  private static final String TAG = "GroupedMenuItemWithTextAction";
  private final SwitchAccessActionGroup action;
  private final AccessibilityService service;
  private final ActionTimeline actionTimeline;
  private final SwitchAccessNodeCompat nodeCompat;
  @Nullable private final SelectMenuItemListener selectMenuItemListener;

  /**
   * @param service AccessibilityService used to retrieve strings from resources
   * @param nodeCompat Node to create {@link SwitchAccessAction}s and corresponding {@link
   *     NodeActionMenuItem}s for
   * @param action Generic action group used to create {@link SwitchAccessAction}s which a user can
   *     choose to perform on the given {@link SwitchAccessNodeCompat}
   * @param overlayController The overlayController associated with this menu
   * @param actionTimeline The {@link ActionTimeline} associated with this menu, to be used for undo
   *     and redo actions
   * @param selectMenuItemListener The {@link SelectMenuItemListener} to be called when this menu
   *     item is clicked
   */
  public GroupedMenuItemWithTextAction(
      AccessibilityService service,
      final SwitchAccessNodeCompat nodeCompat,
      final SwitchAccessActionGroup action,
      final OverlayController overlayController,
      final ActionTimeline actionTimeline,
      @Nullable final SelectMenuItemListener selectMenuItemListener) {
    super(overlayController, getMenuItemEnumFromAction(action), selectMenuItemListener);
    this.action = action;
    this.service = service;
    this.nodeCompat = nodeCompat;
    this.selectMenuItemListener = selectMenuItemListener;
    this.actionTimeline = actionTimeline;
  }

  @Override
  public int getIconResource() {
    int actionId = action.getId();
    switch (actionId) {
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        return R.drawable.ic_text;
      case TextEditingUtils.ACTION_DELETE_TEXT:
        return R.drawable.ic_delete_text;
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return R.drawable.ic_highlight_text;
      default:
        // We don't know what the action is. Don't show an icon.
        return 0;
    }
  }

  @Override
  public String getText() {
    CharSequence label = action.getLabel();
    if (label != null) {
      return label.toString();
    }

    int actionId = action.getId();
    switch (actionId) {
      case TextEditingUtils.ACTION_DELETE_TEXT:
        return service.getString(R.string.action_group_name_delete);
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        return service.getString(R.string.switch_access_move_prev);
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        return service.getString(R.string.switch_access_move_next);
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return service.getString(R.string.action_group_name_highlight);
      default:
        // This should never happen.
        return "";
    }
  }

  /*
   * Create the {@link SwitchAccessAction}s corresponding to the given {@link
   * SwitchAccessActionGroup}, and get a list of {@link NodeActionMenuItem}s made from those
   * actions.
   */
  @Override
  public List<MenuItem> getSubMenuItems() {
    return getMenuItemsForNodeAndAction(
        service, nodeCompat, action, actionTimeline, selectMenuItemListener);
  }

  @Override
  public GroupedMenuItemHeader getHeader() {
    return new GroupedMenuItemHeader(getText());
  }

  @VisibleForTesting
  static List<MenuItem> getMenuItemsForNodeAndAction(
      AccessibilityService service,
      SwitchAccessNodeCompat nodeCompat,
      SwitchAccessActionGroup action,
      ActionTimeline actionTimeline,
      @Nullable SelectMenuItemListener selectMenuItemListener) {

    List<Integer> relevantGranularities = new ArrayList<>();
    int movementGranularities = nodeCompat.getMovementGranularities();
    int[] supportedGranularities =
        (nodeCompat.isMultiLine()
            ? MOVEMENT_GRANULARITIES_MULTILINE
            : MOVEMENT_GRANULARITIES_ONE_LINE);
    for (int supportedGranularity : supportedGranularities) {
      if ((movementGranularities & supportedGranularity) != 0) {
        relevantGranularities.add(supportedGranularity);
      }
    }

    // Add sentence (custom) granularity for this action.
    if (TextEditingUtils.containsSentence(nodeCompat.getText())) {
      relevantGranularities.add(ACTION_GRANULARITY_SENTENCE);
    }

    // Allow all text to be highlighted. If all text is already highlighted,
    // ActionBuildingUtils#getActionsForNode will not add an ACTION_SET_SELECTION, so no MenuItem
    // would be added, and this method would never be called with this id.
    if (action.getId() == AccessibilityNodeInfo.ACTION_SET_SELECTION) {
      relevantGranularities.add(ACTION_GRANULARITY_ALL);
    }

    ArrayList<MenuItem> newMenuItems = new ArrayList<>();
    for (int granularity : relevantGranularities) {
      Bundle args = new Bundle();
      args.putInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity);
      SwitchAccessAction node =
          new SwitchAccessAction(nodeCompat, action.getId(), action.getLabel(), args);
      newMenuItems.add(
          new NodeActionMenuItem(
              service, nodeCompat, node, actionTimeline, selectMenuItemListener));
    }

    return newMenuItems;
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemEnumFromAction(
      SwitchAccessActionGroup action) {
    switch (action.getId()) {
      case TextEditingUtils.ACTION_DELETE_TEXT:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE;
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_PREVIOUS;
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_NEXT;
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_TEXT;
      default:
        // This should never happen.
        LogUtils.e(TAG, "Action is not supported by the GroupMenuItem");
        return SwitchAccessMenuItemEnum.MenuItem.ITEM_UNSPECIFIED;
    }
  }
}
