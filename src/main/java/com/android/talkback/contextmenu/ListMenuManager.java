/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback.contextmenu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.talkback.tutorial.AccessibilityTutorialActivity;
import com.android.utils.AccessibilityEventListener;

import java.util.ArrayList;
import java.util.List;

public class ListMenuManager implements MenuManager, AccessibilityEventListener {

    private static final long RESET_FOCUS_DELAY = 1000;

    private TalkBackService mService;
    private int mMenuShown;
    private ContextMenuItemClickProcessor mMenuClickProcessor;
    private DeferredAction mDeferredAction;
    private Dialog mCurrentDialog;
    private FeedbackItem mLastUtterance;

    public ListMenuManager(TalkBackService service) {
        mService = service;
        mMenuClickProcessor = new ContextMenuItemClickProcessor(service);
    }

    @Override
    public boolean showMenu(int menuId) {
        mLastUtterance = mService.getSpeechController().getLastUtterance();
        dismissAll();

        // Some TalkBack tutorial modules don't allow context menus.
        if (AccessibilityTutorialActivity.isTutorialActive()
                && !AccessibilityTutorialActivity.shouldAllowContextMenus()) {
            return false;
        }

        mService.saveFocusedNode();
        final ListMenu menu = new ListMenu(mService);
        menu.setDefaultListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.hasSubMenu()) {
                    CharSequence[] items = getItemsFromMenu(item.getSubMenu());
                    ListMenu menu = (ListMenu) item.getSubMenu();
                    showDialogMenu(menu.getTitle(), items, menu);
                } else if (item.getItemId() == R.id.spell_last_utterance) {
                    AccessibilityNodeInfo node = mService.findFocus(
                            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                    if (node != null) {
                        AccessibilityNodeInfoCompat compat = new AccessibilityNodeInfoCompat(node);
                        CharSequence text = NodeSpeechRuleProcessor.getInstance()
                                .getDescriptionForNode(compat, null);
                        compat.recycle();
                        mService.getSpeechController().spellUtterance(text);
                    }
                } else if(item.getItemId() == R.id.repeat_last_utterance) {
                    mService.getSpeechController().interrupt();
                    mService.getSpeechController().repeatUtterance(mLastUtterance);
                } else {
                    mMenuClickProcessor.onMenuItemClicked(item);
                }

                return true;
            }
        });

        ListMenuPreparer menuPreparer = new ListMenuPreparer(mService);
        menuPreparer.prepareMenu(menu, menuId);
        showDialogMenu(menu.getTitle(), getItemsFromMenu(menu), menu);
        return false;
    }

    private void showDialogMenu(String title, CharSequence[] items, final ContextMenu menu) {
        if (items == null || items.length == 0) {
            return;
        }

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                final ContextMenuItem menuItem = menu.getItem(item);
                if (menuItem.hasSubMenu()) {
                    menuItem.onClickPerformed();
                } else {
                    mDeferredAction = getDeferredAction(menuItem);
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(mService);
        builder.setTitle(title);
        builder.setItems(items, listener);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mMenuShown--;
                if (mMenuShown == 0) {
                    long delay = 0;
                    if (mDeferredAction != null) {
                        mService.addEventListener(ListMenuManager.this);

                        if (needFocusDelay(mDeferredAction.actionId)) {
                            delay = RESET_FOCUS_DELAY;
                        }
                    }

                    mService.resetFocusedNode(delay);
                    mCurrentDialog = null;
                }
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            alert.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        } else {
            alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        }

        alert.show();
        mCurrentDialog = alert;
        mMenuShown++;
    }

    // on pre L_MR1 version focus events could be swallowed on platform after window state change
    // so for actions that are rely on accessibility focus we need to delay focus request
    private boolean needFocusDelay(int actionId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false;
        }

        switch (actionId) {
            case R.id.pause_feedback:
            case R.id.talkback_settings:
            case R.id.tts_settings:
                return false;
        }

        return true;
    }

    private DeferredAction getDeferredAction(final ContextMenuItem menuItem) {
        DeferredAction action = new DeferredAction() {
            @Override
            public void run() {
                menuItem.onClickPerformed();
            }
        };

        action.actionId = menuItem.getItemId();
        return action;
    }

    private CharSequence[] getItemsFromMenu(Menu menu) {
        int size = menu.size();
        List<CharSequence> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            MenuItem item = menu.getItem(i);
            if (item != null && item.isEnabled() && item.isVisible()) {
                items.add(item.getTitle());
            }
        }

        return items.toArray(new CharSequence[items.size()]);
    }

    @Override
    public boolean isMenuShowing() {
        return false;
    }

    @Override
    public void dismissAll() {
        if (mCurrentDialog != null && mCurrentDialog.isShowing()) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
    }

    @Override
    public void clearCache() {
        // NoOp
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED &&
                mDeferredAction != null) {
            Handler handler = new Handler();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (mDeferredAction != null) {
                        mDeferredAction.run();
                        mDeferredAction = null;
                    }
                }
            });
            mService.postRemoveEventListener(this);
        }
    }

    @Override
    public void onGesture(int gesture) {}

    private static abstract class DeferredAction implements Runnable {
        public int actionId;
    }
}
