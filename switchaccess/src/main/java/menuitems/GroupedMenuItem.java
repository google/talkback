/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess.menuitems;

import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Holds data required to create a menu item that, when clicked, creates new menu items. */
public abstract class GroupedMenuItem extends MenuItem {

  private final MenuItemOnClickListener onClickListener;

  /**
   * @param overlayController The overlayController associated with this menu
   * @param menuItemEnum The menu item enum associated with opening this menu item
   * @param selectMenuItemListener The {@link SelectMenuItemListener} to be called when this menu
   *     item is clicked
   */
  GroupedMenuItem(
      final OverlayController overlayController,
      SwitchAccessMenuItemEnum.MenuItem menuItemEnum,
      @Nullable final SelectMenuItemListener selectMenuItemListener) {
    onClickListener =
        new MenuItemOnClickListener() {
          @Override
          // The uninitialized GroupedMenuItem needs to be referenced in the on-click listener in
          // the constructor, but fully initialized by the time this is called.
          @SuppressWarnings("initialization:argument.type.incompatible")
          public void onClick() {
            overlayController.showSubMenu(GroupedMenuItem.this);

            if (selectMenuItemListener != null) {
              selectMenuItemListener.onMenuItemSelected(menuItemEnum);
            }
          }
        };
  }

  @Override
  public MenuItemOnClickListener getOnClickListener() {
    return onClickListener;
  }

  public abstract List<MenuItem> getSubMenuItems();

  // TODO: Override getHeader in a new subclass that uses GroupedMenuItemHeader with a
  // menu item for the volume mute button.
  public abstract GroupedMenuItemHeader getHeader();

  /**
   * Returns {@code true} if this menu item should allow the layout to be populated dynamically,
   * i.e. use a FlexboxLayout. This should be true unless specifically overridden in a subclass.
   */
  public boolean shouldPopulateLayoutDynamically() {
    return true;
  }

  /** Contains data such as text and {@link MenuItem}s belonging in a grouped menu item header. */
  public static class GroupedMenuItemHeader {

    @Nullable private MenuItem headerMenuItem;

    private String headerText;

    /**
     * Constructs a submenu header that only contains text.
     *
     * @param text Header text to be displayed in a submenu
     */
    public GroupedMenuItemHeader(String text) {
      this(text, null);
    }

    /**
     * Constructs a submenu header that contains text and an optional {@link MenuItem} to be
     * displayed near the text header.
     *
     * @param text Header text to be displayed in a submenu
     * @param menuItem A menu item that is displayed within a submenu header. Used for MenuItems,
     *     such as the volume mute button, that should have more visual prominence
     */
    public GroupedMenuItemHeader(String text, @Nullable MenuItem menuItem) {
      this.headerText = text;
      this.headerMenuItem = menuItem;
    }

    public String getHeaderText() {
      return headerText;
    }

    /** Returns a menu item that should be shown in a submenu header. */
    @Nullable
    public MenuItem getHeaderMenuItem() {
      return headerMenuItem;
    }
  }
}
