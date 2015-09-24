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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.SubMenu;
import android.view.View;

public class ListSubMenu extends ListMenu implements ContextSubMenu {

    private final ContextMenuItem mMenuItem;

    public ListSubMenu(Context context, int groupId, int itemId, int order, CharSequence title) {
        super(context);
        mMenuItem = new ListMenuItem(context, groupId, itemId, order, title, this);
    }

    @NonNull
    @Override
    public SubMenu setHeaderTitle(int titleRes) {
        mMenuItem.setTitle(titleRes);
        return this;
    }

    @NonNull
    @Override
    public SubMenu setHeaderTitle(CharSequence title) {
        mMenuItem.setTitle(title);
        return this;
    }

    @NonNull
    @Override
    public SubMenu setHeaderIcon(int iconRes) {
        mMenuItem.setIcon(iconRes);
        return this;
    }

    @NonNull
    @Override
    public SubMenu setHeaderIcon(Drawable icon) {
        mMenuItem.setIcon(icon);
        return this;
    }

    @NonNull
    @Override
    public SubMenu setHeaderView(View view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearHeader() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public SubMenu setIcon(int iconRes) {
        mMenuItem.setIcon(iconRes);
        return this;
    }

    @NonNull
    @Override
    public SubMenu setIcon(Drawable icon) {
        mMenuItem.setIcon(icon);
        return this;
    }

    @Override
    public ContextMenuItem getItem() {
        return mMenuItem;
    }
}
