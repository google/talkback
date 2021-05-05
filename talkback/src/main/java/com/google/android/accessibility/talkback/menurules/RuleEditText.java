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
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_EDIT_OPTIONS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
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
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Processes editable text fields. */
public class RuleEditText extends NodeMenuRule {

  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final TalkBackAnalytics analytics;

  public RuleEditText(
      Pipeline.FeedbackReturner pipeline, ActorState actorState, TalkBackAnalytics analytics) {
    super(
        R.string.pref_show_context_menu_editing_setting_key,
        R.bool.pref_show_context_menu_editing_default);
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.analytics = analytics;
  }

  @Override
  public boolean accept(AccessibilityService service, AccessibilityNodeInfoCompat node) {
    return Role.getRole(node) == Role.ROLE_EDIT_TEXT;
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      AccessibilityService service, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    final List<ContextMenuItem> items = new ArrayList<>();

    // This action has inconsistencies with EditText nodes that have
    // contentDescription attributes.
    if (TextUtils.isEmpty(node.getContentDescription())) {
      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          node,
          AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
          AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToBeginning =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_move_to_beginning,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_move_to_beginning));
        items.add(moveToBeginning);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          node,
          AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
          AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)) {
        ContextMenuItem moveToEnd =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_move_to_end,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_move_to_end));
        items.add(moveToEnd);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          node, AccessibilityNodeInfoCompat.ACTION_CUT)) {
        ContextMenuItem cut =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_cut,
                Menu.NONE,
                service.getString(android.R.string.cut));
        items.add(cut);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          node, AccessibilityNodeInfoCompat.ACTION_COPY)) {
        ContextMenuItem copy =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_copy,
                Menu.NONE,
                service.getString(android.R.string.copy));
        items.add(copy);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
          node, AccessibilityNodeInfoCompat.ACTION_PASTE)) {
        ContextMenuItem paste =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_paste,
                Menu.NONE,
                service.getString(android.R.string.paste));
        items.add(paste);
      }

      if (AccessibilityNodeInfoUtils.supportsAnyAction(
              node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)
          && AccessibilityNodeInfoUtils.getText(node) != null) {
        ContextMenuItem select =
            ContextMenu.createMenuItem(
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
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_end_selection_mode,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_end_selection_mode));
      } else {
        selectionMode =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.edittext_breakout_start_selection_mode,
                Menu.NONE,
                service.getString(R.string.title_edittext_breakout_start_selection_mode));
      }
      items.add(selectionMode);
    }

    EditTextMenuItemClickListener listener =
        new EditTextMenuItemClickListener(node, pipeline, analytics);
    for (ContextMenuItem item : items) {
      item.setOnMenuItemClickListener(listener);
      // Skip window and focued event for edit options, REFERTO.
      item.setSkipRefocusEvents(true);
      item.setSkipWindowEvents(true);
    }

    return items;
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_edittext_controls);
  }

  /** Listener may be shared by multi-contextItems. */
  private static class EditTextMenuItemClickListener
      extends AbstractOnContextMenuItemClickListener {

    public EditTextMenuItemClickListener(
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
