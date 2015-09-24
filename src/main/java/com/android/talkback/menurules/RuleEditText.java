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

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import com.android.talkback.PasteHistory;
import com.android.talkback.R;
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
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, EditText.class);
    }

    @Override
    public List<ContextMenuItem> getMenuItemsForNode(
            TalkBackService service, ContextMenuItemBuilder menuItemBuilder ,
            AccessibilityNodeInfoCompat node) {
        final AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
        final CursorController cursorController = service.getCursorController();
        final List<ContextMenuItem> items = new LinkedList<>();

        // This action has inconsistencies with EditText nodes that have
        // contentDescription attributes.
        if (TextUtils.isEmpty(nodeCopy.getContentDescription())) {
            if (AccessibilityNodeInfoUtils.supportsAnyAction(nodeCopy,
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
                ContextMenuItem moveToBeginning = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_move_to_beginning,
                        Menu.NONE,
                        service.getString(R.string.title_edittext_breakout_move_to_beginning));
                items.add(moveToBeginning);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)) {
                ContextMenuItem moveToEnd = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_move_to_end,
                        Menu.NONE,
                        service.getString(R.string.title_edittext_breakout_move_to_end));
                items.add(moveToEnd);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_CUT)) {
                ContextMenuItem cut = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_cut,
                        Menu.NONE,
                        service.getString(android.R.string.cut));
                items.add(cut);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_COPY)) {
                ContextMenuItem copy = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_copy,
                        Menu.NONE,
                        service.getString(android.R.string.copy));
                items.add(copy);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_PASTE)) {
                ContextMenuItem paste = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_paste,
                        Menu.NONE,
                        service.getString(android.R.string.paste));
                items.add(paste);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)) {
                ContextMenuItem select = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.edittext_breakout_select_all,
                        Menu.NONE,
                        service.getString(android.R.string.selectAll));
                items.add(select);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // Text selection APIs are available in API 18+
                // TODO(CB) Use a checkable menu item once supported.
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
        private final FeedbackController mFeedback;
        private final CursorController mCursorController;
        private final AccessibilityNodeInfoCompat mNode;

        public EditTextMenuItemClickListener(
                TalkBackService service, AccessibilityNodeInfoCompat node) {
            mFeedback = service.getFeedbackController();
            mCursorController = service.getCursorController();
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
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, args);
            } else if (itemId == R.id.edittext_breakout_move_to_end) {
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args);
            } else if (itemId == R.id.edittext_breakout_cut) {
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_CUT);
            } else if (itemId == R.id.edittext_breakout_copy) {
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_COPY);
            } else if (itemId == R.id.edittext_breakout_paste) {
                PasteHistory.getInstance().before();
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_PASTE);
                PasteHistory.getInstance().after();
            } else if (itemId == R.id.edittext_breakout_select_all) {
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT,
                        mNode.getText().length());
                result = PerformActionUtils.performAction(mNode,
                        AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args);
            } else if (itemId == R.id.edittext_breakout_start_selection_mode) {
                mCursorController.setSelectionModeActive(mNode, true);
                result = true;
            } else if (itemId == R.id.edittext_breakout_end_selection_mode) {
                mCursorController.setSelectionModeActive(mNode, false);
                result = true;
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
