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

public class ListMenu extends ContextMenu {

  private String title;

  public ListMenu(Context context) {
    super(context);
  }

  @Override
  public ContextMenuItem add(int groupId, int itemId, int order, CharSequence title) {
    ListMenuItem item = new ListMenuItem(getContext(), groupId, itemId, order, title);
    addItem(item);
    return item;
  }

  private void addItem(ContextMenuItem item) {
    item.setOnMenuItemClickListener(getDefaultListener());
    add(item);
  }

  @Override
  public ListSubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
    ListSubMenu subMenu = new ListSubMenu(getContext(), groupId, itemId, order, title);
    addItem(subMenu.getItem());
    return subMenu;
  }

  @Override
  public ContextMenuItemBuilder getMenuItemBuilder() {
    return new ContextMenuItemBuilder() {
      @Override
      public ContextMenuItem createMenuItem(
          Context context, int groupId, int itemId, int order, CharSequence title) {
        ListMenuItem item = new ListMenuItem(context, groupId, itemId, order, title);
        item.setEnabled(true);
        return item;
      }
    };
  }

  /**
   * {@inheritDoc}
   *
   * <p>For list views, dismissing the list menu, clears the menu. Hence we don't need to add items
   * if we removed them once. Also just setting the item visibility to false does not work for list
   * menu. Remove the item if not required every time list menu is opened.
   */
  @Override
  public void updateItemAvailability(boolean shouldBeAvailable, int itemId) {
    if (!shouldBeAvailable) {
      removeItem(itemId);
    }
  }

  @Override
  public void close() {
    //NoOp
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }
}
