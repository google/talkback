/*
 * Copyright (C) 2011 The Android Open Source Project
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
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Processes editable text fields. */
public class RuleEditText implements NodeMenuRule {

  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;

  public RuleEditText(Pipeline.FeedbackReturner pipeline, ActorState actorState) {
    this.pipeline = pipeline;
    this.actorState = actorState;
  }

  @Override
  public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
    return Role.getRole(node) == Role.ROLE_EDIT_TEXT;
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node,
      boolean includeAncestors) {
    final AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
    final List<ContextMenuItem> items = new ArrayList<>();

    // This action has inconsistencies with EditText nodes that have
    // contentDescription attributes.
    if (TextUtils.isEmpty(nodeCopy.getContentDescription())) {
      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          nodeCopy,
          AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
          AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToBeginning =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_move_to_beginning,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_move_to_beginning));
        items.add(moveToBeginning);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          nodeCopy,
          AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
          AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToEnd =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_move_to_end,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_move_to_end));
        items.add(moveToEnd);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          nodeCopy, AccessibilityNodeInfoCompat.ACTION_CUT)) {
        ContextMenuItem cut =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_cut,
                Menu.NONE,
                service.getString(android.R.string.cut));
        items.add(cut);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          nodeCopy, AccessibilityNodeInfoCompat.ACTION_COPY)) {
        ContextMenuItem copy =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_copy,
                Menu.NONE,
                service.getString(android.R.string.copy));
        items.add(copy);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          nodeCopy, AccessibilityNodeInfoCompat.ACTION_PASTE)) {
        ContextMenuItem paste =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_paste,
                Menu.NONE,
                service.getString(android.R.string.paste));
        items.add(paste);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
              nodeCopy, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)
          && nodeCopy.getText() != null) {
        ContextMenuItem select =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_select_all,
                Menu.NONE,
                service.getString(android.R.string.selectAll));
        items.add(select);
      }

      // TODO Use a checkable menu item once supported.
      final ContextMenuItem selectionMode;
      if (actorState.getDirectionNavigation().isSelectionModeActive()) {
        selectionMode =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_end_selection_mode,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_end_selection_mode));
      } else {
        selectionMode =
            menuItemBuilder.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_start_selection_mode,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_start_selection_mode));
      }
      items.add(selectionMode);
    }

    for (ContextMenuItem item : items) {
      item.setOnMenuItemClickListener(new EditTextMenuItemClickListener(nodeCopy));
      // Skip window and focued event for edit options, see .
      item.setSkipRefocusEvents(true);
      item.setSkipWindowEvents(true);
    }

    return items;
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_edittext_controls);
  }

  @Override
  public boolean canCollapseMenu() {
    return true;
  }

  private class EditTextMenuItemClickListener implements MenuItem.OnMenuItemClickListener {
    private final AccessibilityNodeInfoCompat node;

    public EditTextMenuItemClickListener(
        AccessibilityNodeInfoCompat node) {
      this.node = node;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (item == null) {
        node.recycle();
        return true;
      }

      final int itemId = item.getItemId();
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
      } else if (itemId == R.id.edittext_breakout_select_all && node.getText() != null) {
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

      node.recycle();
      return true;
    }
  }

}
