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

package com.google.android.accessibility.talkback.contextmenu;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PointF;
import android.view.Menu;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SparseIterableArray;

/**
 * Implements a radial menu with up to four corners.
 *
 * @see Menu
 */
public class RadialMenu extends ContextMenu {
  private static final int ORDER_NW = 0;
  private static final int ORDER_NE = 1;
  private static final int ORDER_SW = 2;
  private static final int ORDER_SE = 3;

  private final DialogInterface parent;
  private final SparseIterableArray<RadialMenuItem> corners;

  private RadialMenuItem.OnMenuItemSelectionListener selectionListener;
  private OnMenuVisibilityChangedListener visibilityListener;
  private MenuLayoutListener layoutListener;

  /**
   * Creates a new radial menu with the specified parent dialog interface.
   *
   * @param context Current context
   * @param parent to the menu
   */
  public RadialMenu(Context context, DialogInterface parent) {
    super(context);
    this.parent = parent;
    corners = new SparseIterableArray<>();
  }

  public void setLayoutListener(MenuLayoutListener layoutListener) {
    this.layoutListener = layoutListener;
  }

  /**
   * Sets the default selection listener for menu items. If the default selection listener returns
   * false for an event, it will be passed to the menu's parent menu (if any).
   *
   * @param selectionListener The default selection listener for menu items.
   */
  public void setDefaultSelectionListener(
      RadialMenuItem.OnMenuItemSelectionListener selectionListener) {
    this.selectionListener = selectionListener;
  }

  public void setOnMenuVisibilityChangedListener(OnMenuVisibilityChangedListener listener) {
    visibilityListener = listener;
  }

  @Override
  public RadialMenuItem add(int groupId, int itemId, int order, CharSequence title) {
    final RadialMenuItem item = new RadialMenuItem(getContext(), groupId, itemId, order, title);
    return addItem(item);
  }

  /**
   * Add a pre-constructed {@link RadialMenuItem} to the menu.
   *
   * @param item The item to add.
   */
  private RadialMenuItem addItem(RadialMenuItem item) {
    // If this is a sub-menu, add the default selection and click listeners.
    if (item.hasSubMenu()) {
      item.setOnMenuItemSelectionListener(selectionListener);
      item.setOnMenuItemClickListener(getDefaultListener());
    }

    if (item.getGroupId() == R.id.group_corners) {
      item.setCorner();
      corners.put(item.getOrder(), item);
    } else {
      add(item);
    }

    onLayoutChanged();

    return item;
  }

  @Override
  public RadialSubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
    final RadialSubMenu submenu =
        new RadialSubMenu(getContext(), parent, this, groupId, itemId, order, title);

    addItem(submenu.getItem());

    return submenu;
  }

  /*package*/ void onShow() {
    if (visibilityListener != null) {
      visibilityListener.onMenuShown();
    }
  }

  /*package*/ void onDismiss() {
    if (visibilityListener != null) {
      visibilityListener.onMenuDismissed();
    }
  }

  @Override
  public void close() {
    onDismiss();
    parent.dismiss();
  }

  @Override
  public ContextMenuItemBuilder getMenuItemBuilder() {
    return new ContextMenuItemBuilder() {
      @Override
      public ContextMenuItem createMenuItem(
          Context context, int groupId, int itemId, int order, CharSequence title) {
        return new RadialMenuItem(context, groupId, itemId, order, title);
      }
    };
  }

  @Override
  public RadialMenuItem getItem(int index) {
    return (RadialMenuItem) super.getItem(index);
  }

  /**
   * Gets the corner menu item with the given group ID.
   *
   * @param groupId The corner group ID of the item to be returned. One of:
   *     <ul>
   *       <li>{@link RadialMenu#ORDER_NW}
   *       <li>{@link RadialMenu#ORDER_NE}
   *       <li>{@link RadialMenu#ORDER_SE}
   *       <li>{@link RadialMenu#ORDER_SW}
   *     </ul>
   *
   * @return The corner menu item.
   */
  public RadialMenuItem getCorner(int groupId) {
    return corners.get(groupId);
  }

  /**
   * Returns the rotation of a corner in degrees.
   *
   * @param groupId The corner group ID of the item to be returned. One of:
   *     <ul>
   *       <li>{@link RadialMenu#ORDER_NW}
   *       <li>{@link RadialMenu#ORDER_NE}
   *       <li>{@link RadialMenu#ORDER_SE}
   *       <li>{@link RadialMenu#ORDER_SW}
   *     </ul>
   *
   * @return The rotation of a corner in degrees.
   */
  /* package */ static float getCornerRotation(int groupId) {
    final float rotation;

    switch (groupId) {
      case RadialMenu.ORDER_NW:
        rotation = 135;
        break;
      case RadialMenu.ORDER_NE:
        rotation = -135;
        break;
      case RadialMenu.ORDER_SE:
        rotation = -45;
        break;
      case RadialMenu.ORDER_SW:
        rotation = 45;
        break;
      default:
        rotation = 0;
    }

    return rotation;
  }

  /**
   * Returns the on-screen location of a corner as percentages of the screen size. The resulting
   * point coordinates should be multiplied by screen width and height.
   *
   * @param groupId The corner group ID of the item to be returned. One of:
   *     <ul>
   *       <li>{@link RadialMenu#ORDER_NW}
   *       <li>{@link RadialMenu#ORDER_NE}
   *       <li>{@link RadialMenu#ORDER_SE}
   *       <li>{@link RadialMenu#ORDER_SW}
   *     </ul>
   *
   * @return The on-screen location of a corner as percentages of the screen size.
   */
  static PointF getCornerLocation(int groupId) {
    final float x;
    final float y;

    switch (groupId) {
      case RadialMenu.ORDER_NW:
        x = 0;
        y = 0;
        break;
      case RadialMenu.ORDER_NE:
        x = 1;
        y = 0;
        break;
      case RadialMenu.ORDER_SE:
        x = 1;
        y = 1;
        break;
      case RadialMenu.ORDER_SW:
        x = 0;
        y = 1;
        break;
      default:
        return null;
    }

    return new PointF(x, y);
  }

  @Override
  public void removeItem(int id) {
    super.removeItem(id);
    onLayoutChanged();
  }

  /**
   * Performs the selection action associated with a particular {@link RadialMenuItem}.
   *
   * @param item to select
   * @param flags unused
   * @return {@code true} if the menu item performs an action
   */
  public boolean selectMenuItem(RadialMenuItem item, int flags) {
    return ((item != null) && item.onSelectionPerformed())
        || ((selectionListener != null) && selectionListener.onMenuItemSelection(item));
  }

  public boolean clearSelection(int flags) {
    return selectMenuItem(null, flags);
  }

  @Override
  public void removeGroup(int group) {
    super.removeGroup(group);
    onLayoutChanged();
  }

  @Override
  public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
    super.setGroupCheckable(group, checkable, exclusive);
    onLayoutChanged();
  }

  @Override
  public void setGroupEnabled(int group, boolean enabled) {
    super.setGroupEnabled(group, enabled);
    onLayoutChanged();
  }

  @Override
  public void setGroupVisible(int group, boolean visible) {
    super.setGroupVisible(group, visible);
    onLayoutChanged();
  }

  @Override
  public RadialMenuItem findItem(int id) {
    RadialMenuItem menuItem = (RadialMenuItem) super.findItem(id);
    if (menuItem == null) {
      for (RadialMenuItem item : corners) {
        if (item.getItemId() == id) {
          return item;
        }
      }
    }

    return menuItem;
  }

  /**
   * {@inheritDoc}
   *
   * <p>For radial menu, dismissing the menu just hides it and does not clear it. Hence we need to
   * toggle visibility each time we reopen radial menu.
   */
  @Override
  public void updateItemAvailability(boolean shouldBeAvailable, int itemId) {
    findItem(itemId).setVisible(shouldBeAvailable);
  }

  void onLayoutChanged() {
    if (layoutListener != null) {
      layoutListener.onLayoutChanged();
    }
  }

  public interface MenuLayoutListener {
    void onLayoutChanged();
  }

  /** Interface definition for a callback to be invoked when a menu is shown or dismissed. */
  public interface OnMenuVisibilityChangedListener {
    /** Called when a menu has been displayed. */
    void onMenuShown();

    /** Called when a menu has been dismissed. */
    void onMenuDismissed();
  }
}
