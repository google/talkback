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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.ActionProvider;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

public abstract class ContextMenuItem implements MenuItem {

    private Context mContext;
    private int mItemId;
    private int mGroupId;
    private char mAlphaShortcut;
    private char mNumericShortcut;
    private Drawable mIcon;
    private int mOrder;
    private Intent mIntent;
    private boolean mEnabled = true;
    private CharSequence mTitle;
    private CharSequence mTitleCondensed;
    private boolean mVisible = true;
    private boolean mCheckable = false;
    private boolean mChecked = false;
    private OnMenuItemClickListener mListener;
    private ContextMenuInfo mMenuInfo;

    protected ContextMenuItem(Context context, int groupId, int itemId, int order,
                              CharSequence title) {
        mItemId = itemId;
        mGroupId = groupId;
        mContext = context;
        mOrder = order;
        mTitle = title;
        mMenuInfo = new ContextMenuInfo();
    }

    @Override
    public char getAlphabeticShortcut() {
        return mAlphaShortcut;
    }

    @Override
    public char getNumericShortcut() {
        return mNumericShortcut;
    }

    @Override
    public ContextMenuItem setAlphabeticShortcut(char alphaChar) {
        mAlphaShortcut = alphaChar;
        return this;
    }

    @Override
    public ContextMenuItem setNumericShortcut(char numericChar) {
        mNumericShortcut = numericChar;
        return this;
    }

    @Override
    public ContextMenuItem setShortcut(char numericChar, char alphaChar) {
        mNumericShortcut = numericChar;
        mAlphaShortcut = alphaChar;
        return this;
    }

    @Override
    public int getGroupId() {
        return mGroupId;
    }

    @Override
    public Drawable getIcon() {
        return mIcon;
    }

    @Override
    public MenuItem setIcon(Drawable icon) {
        mIcon = icon;
        return this;
    }

    @Override
    public MenuItem setIcon(int iconRes) {
        if (iconRes == 0) {
            mIcon = null;
        } else {
            mIcon = mContext.getResources().getDrawable(iconRes);
        }

        return this;
    }

    @Override
    public int getOrder() {
        return mOrder;
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public MenuItem setIntent(Intent intent) {
        mIntent = intent;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public MenuItem setEnabled(boolean enabled) {
        mEnabled = enabled;
        return this;
    }

    @Override
    public CharSequence getTitle() {
        return mTitle;
    }

    @Override
    public MenuItem setTitle(CharSequence title) {
        mTitle = title;
        return this;
    }

    @Override
    public CharSequence getTitleCondensed() {
        return mTitleCondensed;
    }

    @Override
    public MenuItem setTitleCondensed(CharSequence titleCondensed) {
        mTitleCondensed = titleCondensed;
        return this;
    }


    @Override
    public MenuItem setTitle(int titleRes) {
        mTitle = mContext.getText(titleRes);
        return this;
    }

    @Override
    public int getItemId() {
        return mItemId;
    }

    @Override
    public boolean isCheckable() {
        return mCheckable;
    }

    @Override
    public boolean isChecked() {
        return mCheckable && mChecked;
    }

    @Override
    public boolean isVisible() {
        return mVisible;
    }

    @Override
    public MenuItem setCheckable(boolean checkable) {
        mCheckable = checkable;
        return this;
    }

    @Override
    public MenuItem setChecked(boolean checked) {
        mChecked = checked;
        return this;
    }

    @Override
    public MenuItem setVisible(boolean visible) {
        mVisible = visible;
        return this;
    }

    @Override
    public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) {
        mListener = menuItemClickListener;
        return this;
    }

    @Override
    public boolean collapseActionView() {
        return false;
    }

    @Override
    public boolean expandActionView() {
        return false;
    }

    @TargetApi(14)
    @Override
    public ActionProvider getActionProvider() {
        return null;
    }

    @Override
    public View getActionView() {
        return null;
    }

    @Override
    public boolean isActionViewExpanded() {
        return false;
    }

    @TargetApi(14)
    @Override
    public MenuItem setActionProvider(ActionProvider actionProvider) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setActionView(View view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setActionView(int resId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setOnActionExpandListener(OnActionExpandListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setShowAsAction(int actionEnum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setShowAsActionFlags(int actionEnum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContextMenu.ContextMenuInfo getMenuInfo() {
        return mMenuInfo;
    }

    //TODO internal sdk has no setIconTintList abstract method but public sdk does.
    // Check it after M release
    public MenuItem setIconTintList(ColorStateList tint) {
        return this;
    }

    //TODO internal sdk has no setIconTintMode abstract method but public sdk does.
    // Check it after M release
    public MenuItem setIconTintMode(PorterDuff.Mode tintMode) {
        return this;
    }

    /**
     * Attempts to perform this item's click action.
     *
     * @return {@code true} if this item performs an action
     */
    public boolean onClickPerformed() {
        if (mCheckable) {
            mChecked = !mChecked;
        }

        return mListener != null && mListener.onMenuItemClick(this);
    }

    /**
     * This class doesn't actually do anything.
     */
    private static class ContextMenuInfo implements ContextMenu.ContextMenuInfo {
        // Empty class.
    }
}
