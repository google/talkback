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

/**
 * A {@link GroupedMenuItem} that specifies its menu items at the time of creation. For example,
 * when the group contains menu items of other grouped menu items. This should be used when the
 * structure of the menu items configuration is unique.
 */
public class SimpleGroupedMenuItem extends GroupedMenuItem {

  private int iconResource;
  private String text;
  private List<MenuItem> menuItems;

  /**
   * @param overlayController The overlayController associated with this menu
   * @param menuItems The menu items that will be created when this grouped menu item is clicked
   */
  public SimpleGroupedMenuItem(
      final OverlayController overlayController, List<MenuItem> menuItems) {
    this(
        overlayController,
        0,
        "",
        menuItems,
        SwitchAccessMenuItemEnum.MenuItem.ITEM_UNSPECIFIED /* menuItemEnum */,
        null /* selectMenuItemListener */);
  }
  /**
   * @param overlayController The overlayController associated with this menu
   * @param iconResource The resource id for the icon to be displayed for this menu item
   * @param headerText The text used as the label for this menu item and as the header when the menu
   *     item is opened
   * @param menuItems The menu items that will be created when this grouped menu item is clicked
   * @param menuItemEnum The menu item enum associated with opening this menu item
   * @param selectMenuItemListener The {@link SelectMenuItemListener} to be called when this menu
   *     item is clicked
   */
  public SimpleGroupedMenuItem(
      final OverlayController overlayController,
      int iconResource,
      String headerText,
      List<MenuItem> menuItems,
      SwitchAccessMenuItemEnum.MenuItem menuItemEnum,
      @Nullable final SelectMenuItemListener selectMenuItemListener) {
    super(overlayController, menuItemEnum, selectMenuItemListener);

    this.iconResource = iconResource;
    this.text = headerText;
    this.menuItems = menuItems;
  }

  @Override
  public int getIconResource() {
    return iconResource;
  }

  @Override
  public String getText() {
    return text;
  }

  public List<MenuItem> getSubMenuItems() {
    return menuItems;
  }

  @Override
  public GroupedMenuItemHeader getHeader() {
    return new GroupedMenuItemHeader(getText());
  }
}
