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

package com.google.android.accessibility.talkback.contextmenu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.SubMenu;
import android.view.View;
import androidx.annotation.NonNull;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Class that provides sub menu function */
public class ListSubMenu extends ContextMenu implements SubMenu {

  /** ContextMenuItem controls access to ListSubMenu. ContextMenuItem cleans up ListSubMenu. */
  private final ContextMenuItem menuItem;

  ListSubMenu(Context context, ContextMenuItem menuItem) {
    super(context);
    this.menuItem = menuItem;
  }

  @Override
  public void setTitle(String title) {
    menuItem.setTitle(title);
  }

  @Override
  public String getTitle() {
    CharSequence title = menuItem.getTitle();
    return title == null ? null : title.toString();
  }

  @CanIgnoreReturnValue
  @NonNull
  @Override
  public SubMenu setHeaderTitle(int titleRes) {
    menuItem.setTitle(titleRes);
    return this;
  }

  @CanIgnoreReturnValue
  @NonNull
  @Override
  public SubMenu setHeaderTitle(CharSequence title) {
    menuItem.setTitle(title);
    return this;
  }

  @CanIgnoreReturnValue
  @NonNull
  @Override
  public SubMenu setHeaderIcon(int iconRes) {
    menuItem.setIcon(iconRes);
    return this;
  }

  @CanIgnoreReturnValue
  @NonNull
  @Override
  public SubMenu setHeaderIcon(Drawable icon) {
    menuItem.setIcon(icon);
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

  @CanIgnoreReturnValue
  @NonNull
  @Override
  public SubMenu setIcon(int iconRes) {
    menuItem.setIcon(iconRes);
    return this;
  }

  @CanIgnoreReturnValue
  @NonNull
  @Override
  public SubMenu setIcon(Drawable icon) {
    menuItem.setIcon(icon);
    return this;
  }

  @Override
  public ContextMenuItem getItem() {
    return menuItem;
  }

  @Override
  public void clear() {
    super.clear();
  }
}
