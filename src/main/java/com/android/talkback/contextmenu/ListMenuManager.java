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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.eventprocessor.EventState;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.utils.AccessibilityEventListener;

import java.util.ArrayList;
import java.util.List;

public class ListMenuManager implements MenuManager, AccessibilityEventListener {

    /** Minimum reset-focus delay to prevent flakiness. Should be hardly perceptible. */
    private static final long RESET_FOCUS_DELAY_SHORT = 50;
    /** Longer reset-focus delay for actions that explicitly request a delay. */
    private static final long RESET_FOCUS_DELAY_LONG = 1000;

    private TalkBackService mService;
    private SpeechController mSpeechController;
    private int mMenuShown;
    private ContextMenuItemClickProcessor mMenuClickProcessor;
    private DeferredAction mDeferredAction;
    private Dialog mCurrentDialog;
    private FeedbackItem mLastUtterance;
    private MenuTransformer mMenuTransformer;
    private MenuActionInterceptor mMenuActionInterceptor;

    public ListMenuManager(TalkBackService service) {
        mService = service;
        mSpeechController = service.getSpeechController();
        mMenuClickProcessor = new ContextMenuItemClickProcessor(service);
    }

    @Override
    public boolean showMenu(int menuId) {
        mLastUtterance = mSpeechController.getLastUtterance();
        dismissAll();

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
                    mService.getSpeechController().interrupt();
                    mService.getSpeechController().spellUtterance(
                            mLastUtterance.getAggregateText());
                } else if (item.getItemId() == R.id.repeat_last_utterance) {
                    mService.getSpeechController().interrupt();
                    mService.getSpeechController().repeatUtterance(mLastUtterance);
                } else if (item.getItemId() == R.id.copy_last_utterance_to_clipboard) {
                    mService.getSpeechController().interrupt();
                    mService.getSpeechController().copyLastUtteranceToClipboard(mLastUtterance);
                } else {
                    mMenuClickProcessor.onMenuItemClicked(item);
                }

                return true;
            }
        });

        ListMenuPreparer menuPreparer = new ListMenuPreparer(mService);
        menuPreparer.prepareMenu(menu, menuId);
        if (mMenuTransformer != null) {
            mMenuTransformer.transformMenu(menu, menuId);
        }

        if (menu.size() == 0) {
            mSpeechController.speak(mService.getString(R.string.title_local_breakout_no_items),
                    SpeechController.QUEUE_MODE_FLUSH_ALL, FeedbackItem.FLAG_NO_HISTORY, null);
            return false;
        }
        showDialogMenu(menu.getTitle(), getItemsFromMenu(menu), menu);
        return true;
    }

    private void showDialogMenu(String title, CharSequence[] items, final ContextMenu menu) {
        if (items == null || items.length == 0) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mService);
        builder.setTitle(title);
        View view = prepareCustomView(items, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Dialog currentDialog = mCurrentDialog;
                final ContextMenuItem menuItem = menu.getItem(position);
                if (mMenuActionInterceptor != null) {
                    if (mMenuActionInterceptor.onInterceptMenuClick(menuItem)) {
                        // If the click was intercepted, stop processing the
                        // event.
                        return;
                    }
                }

                if (menuItem.isEnabled()) {
                    // Defer the action only if we are about to close the menu and there's a saved
                    // node. In that case, we have to wait for it to regain accessibility focus
                    // before acting.
                    if (menuItem.hasSubMenu() || !mService.hasSavedNode()) {
                        menuItem.onClickPerformed();
                    } else {
                        mDeferredAction = getDeferredAction(menuItem);
                    }
                }

                if (currentDialog != null && currentDialog.isShowing() && menuItem.isEnabled()) {
                    if (menuItem.getSkipRefocusEvents()) {
                        EventState.getInstance().addEvent(EventState
                                .EVENT_SKIP_FOCUS_PROCESSING_AFTER_CURSOR_CONTROL);
                        EventState.getInstance().addEvent(EventState
                                .EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
                        EventState.getInstance().addEvent(EventState
                                .EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL);
                        EventState.getInstance().addEvent(EventState
                                .EVENT_SKIP_HINT_AFTER_CURSOR_CONTROL);
                    }
                    currentDialog.dismiss();
                }
            }
        });
        builder.setView(view);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mMenuActionInterceptor != null) {
                    mMenuActionInterceptor.onCancelButtonClicked();
                }

                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mMenuShown--;
                if (mMenuShown == 0) {
                    // Sometimes, the node we want to refocus erroneously reports that it is
                    // already accessibility focused right after windows change; to mitigate this,
                    // we should wait a very short delay.
                    long delay = RESET_FOCUS_DELAY_SHORT;
                    if (mDeferredAction != null) {
                        mService.addEventListener(ListMenuManager.this);

                        // Actions that explicitly need a focus delay should get a much longer
                        // focus delay.
                        if (needFocusDelay(mDeferredAction.actionId)) {
                            delay = RESET_FOCUS_DELAY_LONG;
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

    private View prepareCustomView(CharSequence[] items, AdapterView.OnItemClickListener listener) {
        ListView view = new ListView(mService);
        view.setBackground(null);
        view.setDivider(null);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(mService,
                android.R.layout.simple_list_item_1, android.R.id.text1, items);
        view.setAdapter(adapter);
        view.setOnItemClickListener(listener);
        return view;
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
            case R.id.enable_dimming:
            case R.id.disable_dimming:
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
            if (item != null && item.isVisible()) {
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

    @Override
    public void setMenuTransformer(MenuTransformer transformer) {
        mMenuTransformer = transformer;
    }

    @Override
    public void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor) {
        mMenuActionInterceptor = actionInterceptor;
    }

    private static abstract class DeferredAction implements Runnable {
        public int actionId;
    }
}
