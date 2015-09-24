/*
 * Copyright (C) 2013 Google Inc.
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

package com.android.talkback.tutorial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.security.InvalidParameterException;

/**
 * A receiver that listens for and responds to actions performed on TalkBack
 * context menus.
 */
public class ContextMenuMonitor extends BroadcastReceiver {
    public static final String ACTION_CONTEXT_MENU_SHOWN =
            "com.android.talkback.tutorial.ContextMenuShownAction";
    public static final String ACTION_CONTEXT_MENU_HIDDEN =
            "com.android.talkback.tutorial.ContextMenuHiddenAction";
    public static final String ACTION_CONTEXT_MENU_ITEM_CLICKED =
            "com.android.talkback.tutorial.ContextMenuItemClickedAction";
    public static final String EXTRA_MENU_ID =
            "com.android.talkback.tutorial.MenuIdExtra";
    public static final String EXTRA_ITEM_ID =
            "com.android.talkback.tutorial.ItemIdExtra";

    /** The filter for which broadcast events this receiver should monitor. */
    public static final IntentFilter FILTER = new IntentFilter();
    static {
        FILTER.addAction(ACTION_CONTEXT_MENU_SHOWN);
        FILTER.addAction(ACTION_CONTEXT_MENU_HIDDEN);
        FILTER.addAction(ACTION_CONTEXT_MENU_ITEM_CLICKED);
    }

    private ContextMenuListener mListener;

    /**
     * Sets the {@link ContextMenuListener} that handles context menu events.
     *
     * @param listener The listener that should handle menu events, or
     *            {@code null} if such events should not be handled.
     */
    public void setListener(ContextMenuListener listener) {
        mListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mListener == null) {
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            throw new IllegalStateException("Unknown action passed the BroadcastReceiver filter.");
        }

        switch (action) {
            case ACTION_CONTEXT_MENU_SHOWN:
                if (intent.hasExtra(EXTRA_MENU_ID)) {
                    mListener.onShow();
                } else {
                    throw new InvalidParameterException("Intent missing Menu ID extra.");
                }
                break;
            case ACTION_CONTEXT_MENU_HIDDEN:
                if (intent.hasExtra(EXTRA_MENU_ID)) {
                    final int menuId = intent.getIntExtra(EXTRA_MENU_ID, Integer.MIN_VALUE);
                    mListener.onHide(menuId);
                } else {
                    throw new InvalidParameterException("Intent missing Menu ID extra.");
                }
                break;
            case ACTION_CONTEXT_MENU_ITEM_CLICKED:
                if (intent.hasExtra(EXTRA_ITEM_ID)) {
                    final int itemId = intent.getIntExtra(EXTRA_ITEM_ID, Integer.MIN_VALUE);
                    mListener.onItemClick(itemId);
                } else {
                    throw new InvalidParameterException("Intent missing Item ID extra.");
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown action passed the BroadcastReceiver filter.");
        }
    }

    /** A delegate for handling actions performed on TalkBack context menus. */
    public interface ContextMenuListener {
        /**
         * Handles a context menu that is shown.
         */
        void onShow();

        /**
         * Handles a context menu that is hidden.
         *
         * @param menuId The resource identifier for the menu that was hidden.
         */
        void onHide(int menuId);

        /**
         * Handles an item clicked on the TalkBack Global Context Menu.
         * <p>
         * Note: Buttons clicked on Local Context Menus are not handled here
         * because they are constructed dynamically depending on the context and
         * each have their own item click listeners.
         *
         * @param itemId The identifier for the menu item that was clicked.
         */
        void onItemClick(int itemId);
    }
}
