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

import static com.google.android.accessibility.talkback.Feedback.EditText.Action.COPY;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CURSOR_TO_BEGINNING;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CURSOR_TO_END;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.CUT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.END_SELECT;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.PASTE;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.SELECT_ALL;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.START_SELECT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_EDIT_OPTIONS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Adds edit options to the talkback context menu. */
public class EditingAndSelectingMenu implements NodeMenu {
  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  TalkBackAnalytics analytics;
  private final boolean isAndroidWear;

  public EditingAndSelectingMenu(
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      TalkBackAnalytics analytics) {
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.analytics = analytics;
    isAndroidWear = FormFactorUtils.getInstance().isAndroidWear();
  }

  @Override
  public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat editingNode =
        accessibilityFocusMonitor.getEditingNodeFromFocusedKeyboard(node);
    return acceptEditingAndSelectingMenu(node, editingNode);
  }

  private static boolean acceptEditingAndSelectingMenu(
      AccessibilityNodeInfoCompat node, AccessibilityNodeInfoCompat editingNode) {
    return (node.isFocused() && Role.getRole(node) == Role.ROLE_EDIT_TEXT)
        || AccessibilityNodeInfoUtils.isNonEditableSelectableText(node)
        || (editingNode != null);
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    List<ContextMenuItem> editingItems = new ArrayList<>();
    AccessibilityNodeInfoCompat editingNode =
        accessibilityFocusMonitor.getEditingNodeFromFocusedKeyboard(node);
    if (acceptEditingAndSelectingMenu(node, editingNode)) {
      if (editingNode != null) {
        // Apply edit options on the editing node, instead of the focused node.
        populateEditingAndSelectingMenuItemsForNode(context, editingNode, editingItems);
      } else {
        populateEditingAndSelectingMenuItemsForNode(context, node, editingItems);
      }
    }
    return editingItems;
  }

  /**
   * Populates a menu with the context menu items for a node, searching up its ancestor hierarchy if
   * the current node has no editing actions.
   *
   * @param context The parent context.
   * @param node The node to process
   */
  private void populateEditingAndSelectingMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, List<ContextMenuItem> items) {
    // This action has inconsistencies with EditText nodes that have
    // contentDescription attributes.
    if (TextUtils.isEmpty(node.getContentDescription())) {
      if (Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node,
              AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
              AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToBeginning =
            ContextMenu.createMenuItem(
                context,
                Menu.NONE,
                R.id.edittext_breakout_move_to_beginning,
                Menu.NONE,
                context.getString(R.string.title_edittext_breakout_move_to_beginning));
        items.add(moveToBeginning);
      }

      if (Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node,
              AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
              AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToEnd =
            ContextMenu.createMenuItem(
                context,
                Menu.NONE,
                R.id.edittext_breakout_move_to_end,
                Menu.NONE,
                context.getString(R.string.title_edittext_breakout_move_to_end));
        items.add(moveToEnd);
      }

      // TODO: We can remove the condition of form factor if action supported logic is completed at
      //  the wear framework side.
      if (!isAndroidWear
          && Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_CUT)) {
        ContextMenuItem cut =
            ContextMenu.createMenuItem(
                context,
                Menu.NONE,
                R.id.edittext_breakout_cut,
                Menu.NONE,
                context.getString(android.R.string.cut));
        items.add(cut);
      }

      if (!isAndroidWear
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_COPY)) {
        ContextMenuItem copy =
            ContextMenu.createMenuItem(
                context,
                Menu.NONE,
                R.id.edittext_breakout_copy,
                Menu.NONE,
                context.getString(android.R.string.copy));
        items.add(copy);
      }

      if (!isAndroidWear
          && Role.getRole(node) == Role.ROLE_EDIT_TEXT
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_PASTE)) {
        ContextMenuItem paste =
            ContextMenu.createMenuItem(
                context,
                Menu.NONE,
                R.id.edittext_breakout_paste,
                Menu.NONE,
                context.getString(android.R.string.paste));
        items.add(paste);
      }

      if (!isAndroidWear
          && AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)
          && AccessibilityNodeInfoUtils.getText(node) != null) {
        ContextMenuItem select =
            ContextMenu.createMenuItem(
                context,
                Menu.NONE,
                R.id.edittext_breakout_select_all,
                Menu.NONE,
                context.getString(android.R.string.selectAll));
        items.add(select);
      }

      // TODO Use a checkable menu item once supported.
      if (!isAndroidWear) {
        final ContextMenuItem selectionMode;
        if (actorState.getDirectionNavigation().isSelectionModeActive()) {
          selectionMode =
              ContextMenu.createMenuItem(
                  context,
                  Menu.NONE,
                  R.id.edittext_breakout_end_selection_mode,
                  Menu.NONE,
                  context.getString(R.string.title_edittext_breakout_end_selection_mode));
        } else {
          selectionMode =
              ContextMenu.createMenuItem(
                  context,
                  Menu.NONE,
                  R.id.edittext_breakout_start_selection_mode,
                  Menu.NONE,
                  context.getString(R.string.title_edittext_breakout_start_selection_mode));
        }
        items.add(selectionMode);
      }
    }

    EditingMenuItemClickListener listener =
        new EditingMenuItemClickListener(node, pipeline, analytics);
    for (ContextMenuItem item : items) {
      item.setOnMenuItemClickListener(listener);
      // Skip window and focued event for edit options, REFERTO.
      item.setSkipRefocusEvents(true);
      item.setSkipWindowEvents(true);
      item.setDeferredType(DeferredType.ACCESSIBILITY_FOCUS_RECEIVED);
    }
  }

  /** Listener may be shared by multi-contextItems. */
  private static class EditingMenuItemClickListener extends AbstractOnContextMenuItemClickListener {

    public EditingMenuItemClickListener(
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
        analytics.onLocalContextMenuAction(MENU_TYPE_EDIT_OPTIONS, itemId);
        final boolean result;
        EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
        if (itemId == R.id.edittext_breakout_move_to_beginning) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, CURSOR_TO_BEGINNING));
        } else if (itemId == R.id.edittext_breakout_move_to_end) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, CURSOR_TO_END));
        } else if (itemId == R.id.edittext_breakout_cut) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, CUT));
        } else if (itemId == R.id.edittext_breakout_copy) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, COPY));
        } else if (itemId == R.id.edittext_breakout_paste) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, PASTE));
        } else if (itemId == R.id.edittext_breakout_select_all
            && !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, SELECT_ALL));
        } else if (itemId == R.id.edittext_breakout_start_selection_mode) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, START_SELECT));
        } else if (itemId == R.id.edittext_breakout_end_selection_mode) {
          result = pipeline.returnFeedback(eventId, Feedback.edit(node, END_SELECT));
        } else {
          result = false;
        }

        if (result) {
          TalkBackService service = TalkBackService.getInstance();
          if (service != null) {
            service.getAnalytics().onTextEdited();
          }
        } else {
          pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
        }
        return true;
      } finally {
        clear();
      }
    }
  }
}
