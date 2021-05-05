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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Class that constructs list-style context menu which could also provide sub menu */
public class ContextMenu implements Menu {

  private static final int ID_CANCEL = -1;

  private final Context context;
  private String title;
  private final List<ContextMenuItem> items;
  private MenuItem.OnMenuItemClickListener listener;

  public ContextMenu(Context context) {
    this.context = context;
    items = new ArrayList<>();
  }

  /**
   * Sets the default click listener for menu items. If the default click listener returns false for
   * an event, it will be passed to the menu's parent menu (if any).
   *
   * @param listener The default click listener for menu items.
   */
  public void setDefaultListener(MenuItem.OnMenuItemClickListener listener) {
    this.listener = listener;
  }

  public MenuItem.OnMenuItemClickListener getDefaultListener() {
    return listener;
  }

  public Context getContext() {
    return context;
  }

  @Override
  public ContextMenuItem add(int titleRes) {
    return add(NONE, NONE, NONE, titleRes);
  }

  @Override
  public ContextMenuItem add(CharSequence title) {
    return add(NONE, NONE, NONE, title);
  }

  @Override
  public ContextMenuItem add(int groupId, int itemId, int order, int titleRes) {
    CharSequence title = context.getText(titleRes);
    return add(groupId, itemId, order, title);
  }

  @Override
  public ContextMenuItem add(int groupId, int itemId, int order, CharSequence title) {
    ContextMenuItem item = new ContextMenuItem(getContext(), groupId, itemId, order, title);
    addItem(item);
    return item;
  }

  public void add(ContextMenuItem item) {
    items.add(item);
  }

  @Override
  public SubMenu addSubMenu(int titleRes) {
    return addSubMenu(NONE, NONE, NONE, titleRes);
  }

  @Override
  public SubMenu addSubMenu(CharSequence title) {
    return addSubMenu(NONE, NONE, NONE, title);
  }

  @Override
  public SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
    final CharSequence title = context.getText(titleRes);
    return addSubMenu(groupId, itemId, order, title);
  }

  @Override
  public ListSubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
    ContextMenuItem item = new ContextMenuItem(getContext(), groupId, itemId, order, title);
    addItem(item);
    return item.getOrCreateSubMenu();
  }

  @Override
  public void setQwertyMode(boolean isQwerty) {
    // NoOp
  }

  @Override
  public int size() {
    return items.size();
  }

  @Override
  public void clear() {
    for (ContextMenuItem item : items) {
      item.clear();
    }
    items.clear();
  }

  @Override
  public void removeItem(int id) {
    final MenuItem item = findItem(id);
    if (item == null) {
      return;
    }

    items.remove(item);
  }

  @Override
  public boolean hasVisibleItems() {
    for (MenuItem item : items) {
      if (item.isVisible()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public ContextMenuItem getItem(int index) {
    return items.get(index);
  }

  protected int indexOf(ContextMenuItem item) {
    return items.indexOf(item);
  }

  @Override
  public void removeGroup(int group) {
    List<MenuItem> removeItems = new ArrayList<>();

    for (MenuItem item : items) {
      if (item.getGroupId() == group) {
        removeItems.add(item);
      }
    }

    items.removeAll(removeItems);
  }

  @Override
  public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
    for (MenuItem item : items) {
      if (item.getGroupId() == group) {
        item.setCheckable(checkable);
      }
    }
  }

  @Override
  public void setGroupEnabled(int group, boolean enabled) {
    for (MenuItem item : items) {
      if (item.getGroupId() == group) {
        item.setEnabled(enabled);
      }
    }
  }

  @Override
  public void setGroupVisible(int group, boolean visible) {
    for (MenuItem item : items) {
      if (item.getGroupId() == group) {
        item.setVisible(visible);
      }
    }
  }

  @Override
  public ContextMenuItem findItem(int id) {
    if (id == ID_CANCEL) {
      return null;
    }

    for (ContextMenuItem item : items) {
      if (item.getItemId() == id) {
        return item;
      }
    }

    return null;
  }

  /** Finds menuitem from menu or submenu by item id. */
  public @Nullable ContextMenuItem findItemInMenuOrSubmenus(int id) {
    if (id == ID_CANCEL) {
      return null;
    }

    for (ContextMenuItem item : items) {
      if (item.getItemId() == id) {
        return item;
      } else if (item.hasSubMenu()) {
        SubMenu subMenu = item.getSubMenu();
        if (subMenu instanceof ListSubMenu) {
          ContextMenuItem subMenuItem = ((ListSubMenu) subMenu).findItem(id);
          if (subMenuItem != null) {
            return subMenuItem;
          }
        }
      }
    }

    return null;
  }

  @Override
  public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
    final ContextMenuItem item = getItemForShortcut(keyCode);
    return performMenuItem(item, flags);
  }

  @Override
  public boolean performIdentifierAction(int id, int flags) {
    final ContextMenuItem item = findItem(id);
    return performMenuItem(item, flags);
  }

  @Override
  public boolean isShortcutKey(int keyCode, KeyEvent event) {
    final MenuItem item = getItemForShortcut(keyCode);
    return (item != null);
  }

  /**
   * Performs the action associated with a particular menu item, or {@code null} if the menu was
   * cancelled.
   *
   * @param item to perform
   * @param flags for action
   * @return {@code true} if the menu item performs an action
   */
  public boolean performMenuItem(ContextMenuItem item, int flags) {
    final boolean performedAction =
        (item == null)
            || item.onClickPerformed()
            || ((listener != null) && listener.onMenuItemClick(item));

    if ((item == null)
        || ((flags & FLAG_PERFORM_NO_CLOSE) == 0)
        || ((flags & FLAG_ALWAYS_PERFORM_CLOSE) != 0)) {
      close();
    }

    return performedAction;
  }

  /**
   * Returns the {@link MenuItem} that responds to a given shortcut key.
   *
   * @param keyCode for the shortcut
   * @return the {@link MenuItem} that responds to a given shortcut key
   */
  protected ContextMenuItem getItemForShortcut(int keyCode) {
    for (ContextMenuItem item : items) {
      if (item.getAlphabeticShortcut() == keyCode || item.getNumericShortcut() == keyCode) {
        return item;
      }
    }

    return null;
  }

  @Override
  public int addIntentOptions(
      int groupId,
      int itemId,
      int order,
      ComponentName caller,
      Intent[] specifics,
      Intent intent,
      int flags,
      MenuItem[] outSpecificItems) {
    final PackageManager manager = getContext().getPackageManager();
    final List<ResolveInfo> infoList =
        manager.queryIntentActivityOptions(caller, specifics, intent, 0);

    if ((flags & FLAG_APPEND_TO_GROUP) == 0) {
      removeGroup(groupId);
    }

    int i = 0;

    for (ResolveInfo info : infoList) {
      final Drawable icon = info.loadIcon(manager);
      final CharSequence title = info.loadLabel(manager);
      final MenuItem item = add(groupId, itemId, order, title);

      item.setIcon(icon);

      if (i < outSpecificItems.length) {
        outSpecificItems[i++] = item;
      } else {
        throw new ArrayIndexOutOfBoundsException();
      }
    }

    return infoList.size();
  }

  private void addItem(ContextMenuItem item) {
    item.setOnMenuItemClickListener(getDefaultListener());
    add(item);
  }

  public static ContextMenuItem createMenuItem(
      Context context, int groupId, int itemId, int order, CharSequence title) {
    ContextMenuItem item = new ContextMenuItem(context, groupId, itemId, order, title);
    item.setEnabled(true);
    return item;
  }

  @Override
  public void close() {
    // NoOp
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }

  /** Sorts menu items by order. */
  public void sortItemsByOrder() {
    Collections.sort(
        items, (item1, item2) -> Integer.valueOf(item1.getOrder()).compareTo(item2.getOrder()));
  }
}
