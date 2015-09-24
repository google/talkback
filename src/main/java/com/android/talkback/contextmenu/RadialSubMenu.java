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
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.SubMenu;
import android.view.View;

/**
 * Implements a radial sub-menu.
 */
public class RadialSubMenu extends RadialMenu implements ContextSubMenu {
    private final RadialMenu mParentMenu;
    private final RadialMenuItem mMenuItem;

    /**
     * Creates a new radial sub-menu and associated radial menu item.
     *
     * @param context The parent context.
     * @param groupId The menu item's group identifier.
     * @param itemId The menu item's identifier (should be unique).
     * @param order The order of this item.
     * @param title The text to be displayed for this menu item.
     */
    /* package */ RadialSubMenu(Context context, DialogInterface parent, RadialMenu parentMenu,
                                int groupId, int itemId, int order, CharSequence title) {
        super(context, parent);

        mParentMenu = parentMenu;
        mMenuItem = new RadialMenuItem(context, groupId, itemId, order, title, this);
    }

    @Override
    public boolean selectMenuItem(RadialMenuItem item, int flags) {
        return super.selectMenuItem(item, flags)
                || ((mParentMenu != null) && mParentMenu.selectMenuItem(item, flags));
    }

    @Override
    public void clearHeader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RadialMenuItem getItem() {
        return mMenuItem;
    }

    @Override
    public @NonNull SubMenu setHeaderIcon(int iconRes) {
        mMenuItem.setIcon(iconRes);

        return this;
    }

    @Override
    public @NonNull SubMenu setHeaderIcon(Drawable icon) {
        mMenuItem.setIcon(icon);

        return this;
    }

    @Override
    public @NonNull SubMenu setHeaderTitle(int titleRes) {
        mMenuItem.setTitle(titleRes);

        return this;
    }

    @Override
    public @NonNull SubMenu setHeaderTitle(CharSequence title) {
        mMenuItem.setTitle(title);

        return this;
    }

    @Override
    public @NonNull SubMenu setHeaderView(View view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull SubMenu setIcon(int iconRes) {
        mMenuItem.setIcon(iconRes);

        return this;
    }

    @Override
    public @NonNull SubMenu setIcon(Drawable icon) {
        mMenuItem.setIcon(icon);

        return this;
    }

}
