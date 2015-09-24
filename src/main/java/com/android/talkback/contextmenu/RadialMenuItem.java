/*
 * Copyright (C) 2011 Google Inc.
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

import android.content.Context;
import android.view.MenuItem;

public class RadialMenuItem extends ContextMenuItem {

    private boolean mCorner;
    private RadialSubMenu mSubMenu;
    /* package */ float offset;
    private OnMenuItemSelectionListener mSelectionListener;

    /**
     * Creates a new menu item that represents a sub-menu.
     *
     * @param context The parent context.
     * @param groupId The menu item's group identifier.
     * @param itemId The menu item's identifier (should be unique).
     * @param order The order of this item.
     * @param title The text to be displayed for this menu item.
     * @param subMenu The sub-menu represented by this menu item.
     */
    /* package */ RadialMenuItem(Context context, int groupId, int itemId,
            int order, CharSequence title, RadialSubMenu subMenu) {
        super(context, groupId, itemId, order, title);
        mSubMenu = subMenu;
    }

    public RadialMenuItem(Context context, int groupId, int itemId,
                   int order, CharSequence title) {
        super(context, groupId, itemId, order, title);
    }

    @Override
    public RadialSubMenu getSubMenu() {
        return mSubMenu;
    }

    @Override
    public boolean hasSubMenu() {
        return (mSubMenu != null);
    }

    /**
     * @return {@code true} if this menu is a corner.
     */
    public boolean isCorner() {
        return mCorner;
    }

    /**
     * Attempts to perform this item's selection action.
     *
     * @return {@code true} if this item performs an action
     */
    public boolean onSelectionPerformed() {
        return mSelectionListener != null && mSelectionListener.onMenuItemSelection(this);
    }

    /**
     * Sets the listener that will receive selection callbacks.
     *
     * @param menuItemSelectionListener The listener to set.
     * @return This item so additional setters can be called.
     */
    public MenuItem setOnMenuItemSelectionListener(
            OnMenuItemSelectionListener menuItemSelectionListener) {
        mSelectionListener = menuItemSelectionListener;
        return this;
    }

    /**
     * Sets whether this menu item is a corner.
     *
     * @return This item so additional setters can be called.
     */
    /* package */MenuItem setCorner() {
        mCorner = true;
        return this;
    }

    /**
     * Interface definition for a callback to be invoked when a menu item is
     * selected.
     */
    public interface OnMenuItemSelectionListener {
        /**
         * Called when a menu item has been selected. This is the first code
         * that is executed; if it returns true, no other callbacks will be
         * executed.
         *
         * @param item The menu item that was selected.
         * @return {@code true} to consume this selection event and prevent
         *         other listeners from executing.
         */
        public boolean onMenuItemSelection(RadialMenuItem item);
    }
}
