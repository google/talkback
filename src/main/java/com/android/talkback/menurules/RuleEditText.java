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

package com.android.talkback.menurules;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.android.talkback.EditTextActionHistory;
import com.android.talkback.R;
import com.android.talkback.SpeechCleanupUtils;
import com.android.talkback.SpeechController;
import com.android.talkback.controller.TextCursorController;
import com.android.utils.Role;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ContextMenuItemBuilder;
import com.android.talkback.controller.CursorController;
import com.android.talkback.controller.FeedbackController;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.PerformActionUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Processes editable text fields.
 */
class RuleEditText implements NodeMenuRule {
    /**
     * Default pitch adjustment for text copy event feedback.
     */
    private static final float DEFAULT_COPY_PITCH = 1.2f;

    @Override
    public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
        return Role.getRole(node) == Role.ROLE_EDIT_TEXT;
    }

    @Override
    public List<ContextMenuItem> getMenuItemsForNode(
            TalkBackService service, ContextMenuItemBuilder menuItemBuilder,
            AccessibilityNodeInfoCompat node) {
        final AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
        final CursorController cursorController = service.getCursorController();
        final List<ContextMenuItem> items = new LinkedList<>();

        // This action has inconsistencies with EditText nodes that have
        // contentDescription attributes.
        if (TextUtils.isEmpty(nodeCopy.getContentDescription())) {
            if (AccessibilityNodeInfoUtils.supportsAnyAction(nodeCopy,
                    AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
                ContextMenuItem moveToBeginning = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_move_to_beginning,
                        Menu.NONE,
                        service.getString(R.string.title_edittext_breakout_move_to_beginning));
                moveToBeginning.setSkipRefocusEvents(true);
                items.add(moveToBeginning);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(nodeCopy,
                    AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
                    AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)) {
                ContextMenuItem moveToEnd = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_move_to_end,
                        Menu.NONE,
                        service.getString(R.string.title_edittext_breakout_move_to_end));
                moveToEnd.setSkipRefocusEvents(true);
                items.add(moveToEnd);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_CUT)) {
                ContextMenuItem cut = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_cut,
                        Menu.NONE,
                        service.getString(android.R.string.cut));
                cut.setSkipRefocusEvents(true);
                items.add(cut);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_COPY)) {
                ContextMenuItem copy = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_copy,
                        Menu.NONE,
                        service.getString(android.R.string.copy));
                copy.setSkipRefocusEvents(true);
                items.add(copy);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_PASTE)) {
                ContextMenuItem paste = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_paste,
                        Menu.NONE,
                        service.getString(android.R.string.paste));
                paste.setSkipRefocusEvents(true);
                items.add(paste);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION) &&
                    nodeCopy.getText() != null) {
                ContextMenuItem select = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_select_all,
                        Menu.NONE,
                        service.getString(android.R.string.selectAll));
                select.setSkipRefocusEvents(true);
                items.add(select);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // Text selection APIs are available in API 18+
                // TODO Use a checkable menu item once supported.
                final ContextMenuItem selectionMode;
                if (cursorController.isSelectionModeActive()) {
                    selectionMode = menuItemBuilder.createMenuItem(service,
                            Menu.NONE, R.id.edittext_breakout_end_selection_mode,
                            Menu.NONE,
                            service.getString(R.string.title_edittext_breakout_end_selection_mode));
                } else {
                    selectionMode = menuItemBuilder.createMenuItem(service,
                            Menu.NONE, R.id.edittext_breakout_start_selection_mode,
                            Menu.NONE,
                            service.getString(R.string.title_edittext_breakout_start_selection_mode));
                }

                selectionMode.setSkipRefocusEvents(true);
                items.add(selectionMode);
            }
        }

        for (ContextMenuItem item : items) {
            item.setOnMenuItemClickListener(new EditTextMenuItemClickListener(service, nodeCopy));
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

    private static class EditTextMenuItemClickListener implements MenuItem.OnMenuItemClickListener {
        private final TalkBackService mService;
        private final FeedbackController mFeedback;
        private final CursorController mCursorController;
        private final TextCursorController mTextCursorController;
        private final ClipboardManager mClipboardManager;
        private final SpeechController mSpeechController;
        private final AccessibilityNodeInfoCompat mNode;

        public EditTextMenuItemClickListener(
                TalkBackService service, AccessibilityNodeInfoCompat node) {
            mService = service;
            mFeedback = service.getFeedbackController();
            mCursorController = service.getCursorController();
            mTextCursorController = service.getTextCursorController();
            mClipboardManager = (ClipboardManager) service.getSystemService(
                    Context.CLIPBOARD_SERVICE);
            mSpeechController = service.getSpeechController();
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

            if (itemId == R.id.edittext_breakout_move_to_beginning) {
                mTextCursorController.forceSetCursorPosition(0, 0);
                if (AccessibilityNodeInfoUtils.supportsAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)) {
                    args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                    args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, 0);
                    result = PerformActionUtils.performAction(mNode,
                            AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args);
                } else {
                    args.putInt(
                            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
                    result = PerformActionUtils.performAction(mNode,
                            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
                            args);
                }
                mSpeechController.speak(
                        mService.getString(R.string.notification_type_beginning_of_field),
                        /** It makes sense to interrupt all the previous utterances generated in
                         * the local context menu. After the cursor action is performed, it's
                         * the most important to notify the user what happens to the edit text. */
                        SpeechController.QUEUE_MODE_INTERRUPT,
                        0,
                        null);
            } else if (itemId == R.id.edittext_breakout_move_to_end) {
                int length = 0;
                if (mNode.getText() != null) {
                    length = mNode.getText().length();
                    mTextCursorController.forceSetCursorPosition(length, length);
                }
                if (AccessibilityNodeInfoUtils.supportsAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_SET_SELECTION) &&
                        mNode.getText() != null) {
                    args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT,
                            length);
                    args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT,
                            length);
                    result = PerformActionUtils.performAction(mNode,
                            AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args);
                } else {
                    args.putInt(
                            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
                    result = PerformActionUtils.performAction(mNode,
                            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args);
                }
                mSpeechController.speak(
                        mService.getString(R.string.notification_type_end_of_field),
                        SpeechController.QUEUE_MODE_INTERRUPT,
                        0,
                        null);
            } else if (itemId == R.id.edittext_breakout_cut) {
                EditTextActionHistory.getInstance().beforeCut();
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_CUT);
                EditTextActionHistory.getInstance().afterCut();
            } else if (itemId == R.id.edittext_breakout_copy) {
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_COPY);
                ClipData data = mClipboardManager.getPrimaryClip();
                if (data != null && data.getItemCount() > 0
                        && data.getItemAt(0).getText() != null) {
                    Bundle params = new Bundle();
                    params.putFloat(SpeechController.SpeechParam.PITCH, DEFAULT_COPY_PITCH);
                    mSpeechController.speak(
                            mService.getString(R.string.template_text_copied,
                                    data.getItemAt(0).getText().toString()),
                            SpeechController.QUEUE_MODE_INTERRUPT,
                            0,
                            params);
                }
            } else if (itemId == R.id.edittext_breakout_paste) {
                EditTextActionHistory.getInstance().beforePaste();
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_PASTE);
                EditTextActionHistory.getInstance().afterPaste();
            } else if (itemId == R.id.edittext_breakout_select_all && mNode.getText() != null) {
                EditTextActionHistory.getInstance().beforeSelectAll();
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT,
                        mNode.getText().length());
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args);
                EditTextActionHistory.getInstance().afterSelectAll();
                mSpeechController.speak(
                        SpeechCleanupUtils.cleanUp(mService,
                                mService.getString(R.string.template_announce_selected_text,
                                        mNode.getText())),
                        SpeechController.QUEUE_MODE_INTERRUPT,
                        0,
                        null);
            } else if (itemId == R.id.edittext_breakout_start_selection_mode) {
                mCursorController.setSelectionModeActive(mNode, true);
                result = true;
                mSpeechController.speak(
                        mService.getString(R.string.notification_type_selection_mode_on),
                        SpeechController.QUEUE_MODE_INTERRUPT,
                        0,
                        null);
            } else if (itemId == R.id.edittext_breakout_end_selection_mode) {
                mCursorController.setSelectionModeActive(mNode, false);
                result = true;
                mSpeechController.speak(
                        mService.getString(R.string.notification_type_selection_mode_off),
                        SpeechController.QUEUE_MODE_INTERRUPT,
                        0,
                        null);
                int start = mNode.getTextSelectionStart();
                int end = mNode.getTextSelectionEnd();
                if (start > end) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                CharSequence text = mNode.getText();
                if (text != null && start >= 0 && start <= text.length()
                        && end >= 0 && end <= text.length()) {
                    CharSequence textToSpeak;
                    if (start != end) {
                        textToSpeak = mService.getString(R.string.template_announce_selected_text,
                                text.subSequence(start, end));

                    } else {
                        textToSpeak = mService.getString(R.string.template_no_text_selected);
                    }
                    mSpeechController.speak(
                            textToSpeak,
                            SpeechController.QUEUE_MODE_QUEUE,
                            0,
                            null);
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
