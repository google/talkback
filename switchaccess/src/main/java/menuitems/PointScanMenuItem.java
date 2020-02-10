/*
 * Copyright (C) 2016 Google Inc.
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

import android.content.Context;
import com.google.android.accessibility.switchaccess.PointScanManager;
import com.google.android.accessibility.switchaccess.PointScanManager.PointScanAction;
import com.google.android.accessibility.switchaccess.R;
import java.util.ArrayList;
import java.util.List;

/** Holds data required to construct menu items in the point scan menu. */
public class PointScanMenuItem extends MenuItem {

  private final int iconResource;
  private final String text;
  private final MenuItemOnClickListener onClickListener;

  public static List<MenuItem> getPointScanMenuItemList(
      Context context, final PointScanManager manager) {
    List<MenuItem> menu = new ArrayList<>();
    // Select.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_select,
            context.getString(R.string.action_name_click),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_CLICK)));
    // Long press.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_long_press,
            context.getString(R.string.action_name_long_click),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_LONG_CLICK)));
    // Swipe left.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_scroll_left,
            context.getString(R.string.action_name_swipe_left),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_SWIPE_LEFT)));
    // Swipe right.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_scroll_right,
            context.getString(R.string.action_name_swipe_right),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_SWIPE_RIGHT)));
    // Swipe up.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_scroll_up,
            context.getString(R.string.action_name_swipe_up),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_SWIPE_UP)));
    // Swipe down.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_scroll_down,
            context.getString(R.string.action_name_swipe_down),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_SWIPE_DOWN)));
    // Custom swipe.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_scroll_right,
            context.getString(R.string.action_name_swipe_custom),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_SWIPE_CUSTOM)));
    // Zoom in.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_zoom_in,
            context.getString(R.string.action_name_zoom_in),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_ZOOM_IN)));
    // Zoom out.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_zoom_out,
            context.getString(R.string.action_name_zoom_out),
            manager.createOnClickListenerForAction(PointScanAction.ACTION_ZOOM_OUT)));

    return menu;
  }

  private PointScanMenuItem(
      int iconResource, String text, MenuItemOnClickListener onClickListener) {
    this.onClickListener = onClickListener;
    this.iconResource = iconResource;
    this.text = text;
  }

  @Override
  public int getIconResource() {
    return iconResource;
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public MenuItemOnClickListener getOnClickListener() {
    return onClickListener;
  }
}
