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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.FailoverTextToSpeech.SpeechParam;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.TextCursorManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.LinkedList;
import java.util.List;

/** Processes editable text fields. */
class RuleEditText implements NodeMenuRule {
  /** Default pitch adjustment for text copy event feedback. */
  private static final float DEFAULT_COPY_PITCH = 1.2f;

  private final EditTextActionHistory mEditTextActionHistory;
  private final TextCursorManager mTextCursorManager;

  public RuleEditText(
      EditTextActionHistory editTextActionHistory, TextCursorManager textCursorManager) {
    mEditTextActionHistory = editTextActionHistory;
    mTextCursorManager = textCursorManager;
  }

  @Override
  public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
    return Role.getRole(node) == Role.ROLE_EDIT_TEXT;
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node) {
    final AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
    final CursorController cursorController = service.getCursorController();
    final List<ContextMenuItem> items = new LinkedList<>();

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
        moveToBeginning.setSkipRefocusEvents(true);
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
        moveToEnd.setSkipRefocusEvents(true);
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
        cut.setSkipRefocusEvents(true);
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
        copy.setSkipRefocusEvents(true);
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
        paste.setSkipRefocusEvents(true);
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
        select.setSkipRefocusEvents(true);
        items.add(select);
      }

      // TODO Use a checkable menu item once supported.
      final ContextMenuItem selectionMode;
      if (cursorController.isSelectionModeActive()) {
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

      selectionMode.setSkipRefocusEvents(true);
      items.add(selectionMode);
    }

    for (ContextMenuItem item : items) {
      item.setOnMenuItemClickListener(
          new EditTextMenuItemClickListener(service, mEditTextActionHistory, nodeCopy));
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
    private final TalkBackService mService;
    private final FeedbackController mFeedback;
    private final CursorController mCursorController;
    private final ClipboardManager mClipboardManager;
    private final SpeechController mSpeechController;
    private final AccessibilityNodeInfoCompat mNode;
    private final EditTextActionHistory mEditTextActionHistory;

    public EditTextMenuItemClickListener(
        TalkBackService service,
        EditTextActionHistory editTextActionHistory,
        AccessibilityNodeInfoCompat node) {
      mService = service;
      mFeedback = service.getFeedbackController();
      mCursorController = service.getCursorController();
      mClipboardManager = (ClipboardManager) service.getSystemService(Context.CLIPBOARD_SERVICE);
      mSpeechController = service.getSpeechController();
      mEditTextActionHistory = editTextActionHistory;
      mNode = node;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (item == null) {
        mNode.recycle();
        return true;
      }

      final int itemId = item.getItemId();
      final Bundle args = new Bundle();
      final boolean result;
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
      if (itemId == R.id.edittext_breakout_move_to_beginning) {
        mTextCursorManager.forceSetCursorPosition(0, 0);
        if (AccessibilityNodeInfoUtils.supportsAction(
            mNode, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)) {
          args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
          args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, 0);
          result =
              PerformActionUtils.performAction(
                  mNode, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
        } else {
          args.putInt(
              AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
              AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
          result =
              PerformActionUtils.performAction(
                  mNode,
                  AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                  args,
                  eventId);
        }
        mSpeechController.speak(
            mService.getString(R.string.notification_type_beginning_of_field),
            /**
             * It makes sense to interrupt all the previous utterances generated in the local
             * context menu. After the cursor action is performed, it's the most important to notify
             * the user what happens to the edit text.
             */
            SpeechController.QUEUE_MODE_INTERRUPT,
            FeedbackItem.FLAG_FORCED_FEEDBACK,
            null,
            eventId);
      } else if (itemId == R.id.edittext_breakout_move_to_end) {
        int length = 0;
        if (mNode.getText() != null) {
          length = mNode.getText().length();
          mTextCursorManager.forceSetCursorPosition(length, length);
        }
        if (AccessibilityNodeInfoUtils.supportsAction(
                mNode, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)
            && mNode.getText() != null) {
          args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, length);
          args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, length);
          result =
              PerformActionUtils.performAction(
                  mNode, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
        } else {
          args.putInt(
              AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
              AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
          result =
              PerformActionUtils.performAction(
                  mNode,
                  AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
                  args,
                  eventId);
        }
        mSpeechController.speak(
            mService.getString(R.string.notification_type_end_of_field),
            SpeechController.QUEUE_MODE_INTERRUPT,
            FeedbackItem.FLAG_FORCED_FEEDBACK,
            null,
            eventId);
      } else if (itemId == R.id.edittext_breakout_cut) {
        mEditTextActionHistory.beforeCut();
        result =
            PerformActionUtils.performAction(
                mNode, AccessibilityNodeInfoCompat.ACTION_CUT, eventId);
        mEditTextActionHistory.afterCut();
      } else if (itemId == R.id.edittext_breakout_copy) {
        result =
            PerformActionUtils.performAction(
                mNode, AccessibilityNodeInfoCompat.ACTION_COPY, eventId);
        ClipData data = mClipboardManager.getPrimaryClip();
        if (data != null && data.getItemCount() > 0 && data.getItemAt(0).getText() != null) {
          Bundle params = new Bundle();
          params.putFloat(SpeechParam.PITCH, DEFAULT_COPY_PITCH);
          mSpeechController.speak(
              mService.getString(
                  com.google.android.accessibility.utils.R.string.template_text_copied,
                  data.getItemAt(0).getText().toString()),
              SpeechController.QUEUE_MODE_INTERRUPT,
              FeedbackItem.FLAG_FORCED_FEEDBACK,
              params,
              eventId);
        }
      } else if (itemId == R.id.edittext_breakout_paste) {
        mEditTextActionHistory.beforePaste();
        result =
            PerformActionUtils.performAction(
                mNode, AccessibilityNodeInfoCompat.ACTION_PASTE, eventId);
        mEditTextActionHistory.afterPaste();
      } else if (itemId == R.id.edittext_breakout_select_all && mNode.getText() != null) {
        mEditTextActionHistory.beforeSelectAll();
        args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
        args.putInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT,
            mNode.getText().length());
        result =
            PerformActionUtils.performAction(
                mNode, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
        mEditTextActionHistory.afterSelectAll();
        mSpeechController.speak(
            SpeechCleanupUtils.cleanUp(
                mService,
                mService.getString(R.string.template_announce_selected_text, mNode.getText())),
            SpeechController.QUEUE_MODE_INTERRUPT,
            FeedbackItem.FLAG_FORCED_FEEDBACK,
            null,
            eventId);
      } else if (itemId == R.id.edittext_breakout_start_selection_mode) {
        mCursorController.setSelectionModeActive(mNode, true, eventId);
        result = true;
        mSpeechController.speak(
            mService.getString(R.string.notification_type_selection_mode_on),
            SpeechController.QUEUE_MODE_INTERRUPT,
            FeedbackItem.FLAG_FORCED_FEEDBACK,
            null,
            eventId);
      } else if (itemId == R.id.edittext_breakout_end_selection_mode) {
        mCursorController.setSelectionModeActive(mNode, false, eventId);
        result = true;
        mSpeechController.speak(
            mService.getString(R.string.notification_type_selection_mode_off),
            SpeechController.QUEUE_MODE_INTERRUPT,
            FeedbackItem.FLAG_FORCED_FEEDBACK,
            null,
            eventId);
        int start = mNode.getTextSelectionStart();
        int end = mNode.getTextSelectionEnd();
        if (start > end) {
          int tmp = start;
          start = end;
          end = tmp;
        }
        CharSequence text = mNode.getText();
        if (text != null
            && start >= 0
            && start <= text.length()
            && end >= 0
            && end <= text.length()) {
          CharSequence textToSpeak;
          if (start != end) {
            textToSpeak =
                mService.getString(
                    R.string.template_announce_selected_text, text.subSequence(start, end));

          } else {
            textToSpeak = mService.getString(R.string.template_no_text_selected);
          }
          mSpeechController.speak(
              textToSpeak,
              SpeechController.QUEUE_MODE_QUEUE,
              FeedbackItem.FLAG_FORCED_FEEDBACK,
              null,
              eventId);
        }
      } else {
        result = false;
      }

      if (result) {
        TalkBackService service = TalkBackService.getInstance();
        if (service != null) {
          service.getAnalytics().onTextEdited();
        }
      } else {
        mFeedback.playAuditory(R.raw.complete);
      }

      mNode.recycle();
      return true;
    }
  }
}
