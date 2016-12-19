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

package com.android.talkback.contextmenu;

import com.android.talkback.FeedbackItem;
import com.android.talkback.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.android.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.InputModeManager;
import com.android.talkback.controller.CursorController;
import com.android.talkback.controller.FeedbackController;
import com.android.talkback.menurules.NodeMenuRuleProcessor;

/**
 * TalkBack-specific implementation of RadialMenuClient.
 */
public class TalkBackRadialMenuClient implements RadialMenuManager.RadialMenuClient {
    /** The parent service. */
    private final TalkBackService mService;

    /** Menu inflater, used for constructing menus on-demand. */
    private final MenuInflater mMenuInflater;

    /** Menu rule processor, used to generate local context menus. */
    private final NodeMenuRuleProcessor mMenuRuleProcessor;

    /** Global menu processor, used to generate items depending on current TalkBack state. */
    private final GlobalMenuProcessor mGlobalMenuProcessor;

    private ContextMenuItemClickProcessor mMenuClickProcessor;

    public TalkBackRadialMenuClient(TalkBackService service) {
        mService = service;
        mMenuInflater = new MenuInflater(mService);
        mMenuRuleProcessor = new NodeMenuRuleProcessor(mService);
        mGlobalMenuProcessor = new GlobalMenuProcessor(mService);
        mMenuClickProcessor = new ContextMenuItemClickProcessor(mService);
    }

    @Override
    public void onCreateRadialMenu(int menuId, RadialMenu menu) {
        if (menuId == R.menu.global_context_menu) {
            onCreateGlobalContextMenu(menu);
        }
    }

    @Override
    public boolean onPrepareRadialMenu(int menuId, RadialMenu menu) {
        if (menuId == R.menu.global_context_menu) {
            return onPrepareGlobalContextMenu(menu);
        } else if (menuId == R.menu.local_context_menu) {
            return onPrepareLocalContextMenu(menu);
        } else if (menuId == R.id.custom_action_menu) {
            return onPrepareCustomActionMenu(menu);
        } else {
            return false;
        }
    }

    /**
     * Handles clicking on a radial menu item.
     *
     * @param menuItem The radial menu item that was clicked.
     */
    @Override
    public boolean onMenuItemClicked(MenuItem menuItem) {
        return mMenuClickProcessor.onMenuItemClicked(menuItem);
    }

    @Override
    public boolean onMenuItemHovered() {
        // Let the manager handle spoken feedback from hovering.
        return false;
    }

    private void onCreateGlobalContextMenu(RadialMenu menu) {
        mMenuInflater.inflate(R.menu.global_context_menu, menu);
        onCreateQuickNavigationMenuItem(menu.findItem(R.id.quick_navigation));

        // Only show "Repeat last utterance" on useful platforms.
        menu.removeItem(R.id.repeat_last_utterance);
    }

    private void onCreateQuickNavigationMenuItem(RadialMenuItem quickNavigationItem) {
        final RadialSubMenu quickNavigationSubMenu = quickNavigationItem.getSubMenu();
        final QuickNavigationJogDial quickNav = new QuickNavigationJogDial(mService);

        // TODO: This doesn't seem like a very clean OOP implementation.
        quickNav.populateMenu(quickNavigationSubMenu);

        quickNavigationSubMenu.setDefaultSelectionListener(quickNav);
        quickNavigationSubMenu.setOnMenuVisibilityChangedListener(quickNav);
    }

    private boolean onPrepareGlobalContextMenu(RadialMenu menu) {
        return mGlobalMenuProcessor.prepareMenu(menu);
    }

    private boolean onPrepareLocalContextMenu(RadialMenu menu) {
        final AccessibilityNodeInfoCompat currentNode =
                mService.getCursorController().getCursorOrInputCursor();
        if (mMenuRuleProcessor == null || currentNode == null) {
            return false;
        }

        final boolean result = mMenuRuleProcessor.prepareMenuForNode(menu, currentNode);
        if (!result && menu.size() == 0) {
            mService.getSpeechController().speak(
                    mService.getString(R.string.title_local_breakout_no_items),
                    SpeechController.QUEUE_MODE_FLUSH_ALL, FeedbackItem.FLAG_NO_HISTORY, null);
        }
        currentNode.recycle();
        return result;
    }

    private boolean onPrepareCustomActionMenu(RadialMenu menu) {
        final AccessibilityNodeInfoCompat currentNode =
                mService.getCursorController().getCursorOrInputCursor();
        if (mMenuRuleProcessor == null || currentNode == null) {
            return false;
        }

        final boolean result = mMenuRuleProcessor.prepareCustomActionMenuForNode(menu, currentNode);
        if (!result && menu.size() == 0) {
            mService.getSpeechController().speak(
                    mService.getString(R.string.title_local_breakout_no_items),
                    SpeechController.QUEUE_MODE_FLUSH_ALL, FeedbackItem.FLAG_NO_HISTORY, null);
        }
        currentNode.recycle();
        return result;
    }

    private static class QuickNavigationJogDial extends BreakoutMenuUtils.JogDial
            implements RadialMenuItem.OnMenuItemSelectionListener,
            RadialMenu.OnMenuVisibilityChangedListener {

        private static final int SEGMENT_COUNT = 16;

        private final Handler mHandler = new Handler();

        private final Context mContext;
        private final SpeechController mSpeechController;
        private final CursorController mCursorController;
        private final FeedbackController mFeedbackController;

        public QuickNavigationJogDial(TalkBackService service) {
            super(SEGMENT_COUNT);

            mContext = service;
            mSpeechController = service.getSpeechController();
            mCursorController = service.getCursorController();
            mFeedbackController = service.getFeedbackController();
        }

        @Override
        public void onFirstTouch() {
            if (!mCursorController.refocus()) {
                mCursorController.next(false /* shouldWrap */, true /* shouldScroll */,
                        false /*useInputFocusAsPivotIfEmpty*/,
                        InputModeManager.INPUT_MODE_TOUCH);
            }
        }

        @Override
        public void onPrevious() {
            if (!mCursorController.previous(false /* shouldWrap */, true /* shouldScroll */,
                    false /*useInputFocusAsPivotIfEmpty*/,
                    InputModeManager.INPUT_MODE_TOUCH)) {
                mFeedbackController.playAuditory(R.raw.complete);
            }
        }

        @Override
        public void onNext() {
            if (!mCursorController.next(false /* shouldWrap */, true /* shouldScroll */,
                    false /*useInputFocusAsPivotIfEmpty*/,
                    InputModeManager.INPUT_MODE_TOUCH)) {
                mFeedbackController.playAuditory(R.raw.complete);
            }
        }

        @Override
        public boolean onMenuItemSelection(RadialMenuItem item) {
            mHandler.removeCallbacks(mHintRunnable);

            if (item == null) {
                // Let the manager handle cancellations.
                return false;
            }

            // Don't provide feedback for individual segments.
            return true;
        }

        @Override
        public void onMenuShown() {
            mHandler.postDelayed(mHintRunnable, RadialMenuManager.DELAY_RADIAL_MENU_HINT);
        }

        @Override
        public void onMenuDismissed() {
            mHandler.removeCallbacks(mHintRunnable);
            mCursorController.refocus();
        }

        private final Runnable mHintRunnable = new Runnable() {
            @Override
            public void run() {
                final String hintText = mContext.getString(R.string.hint_summary_jog_dial);
                mSpeechController.speak(hintText, SpeechController.QUEUE_MODE_QUEUE,
                        FeedbackItem.FLAG_NO_HISTORY, null);
            }
        };
    }
}
