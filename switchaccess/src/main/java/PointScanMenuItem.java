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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Holds data required to construct menu items in the point scan menu. */
public class PointScanMenuItem extends MenuItem {

  public static List<MenuItem> getPointScanMenuItemList(
      Context context, final PointScanManager manager) {
    List<MenuItem> menu = new ArrayList<>();
    // Select.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_select,
            context.getString(R.string.action_name_click),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_CLICK)));
    // Long press.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_long_press,
            context.getString(R.string.action_name_long_click),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_LONG_CLICK)));
    // Swipe left.
    menu.add(
        new PointScanMenuItem(
            R.drawable.quantum_ic_keyboard_arrow_left_white_24,
            context.getString(R.string.action_name_swipe_left),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_SWIPE_LEFT)));
    // Swipe right.
    menu.add(
        new PointScanMenuItem(
            R.drawable.quantum_ic_keyboard_arrow_right_white_24,
            context.getString(R.string.action_name_swipe_right),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_SWIPE_RIGHT)));
    // Swipe up.
    menu.add(
        new PointScanMenuItem(
            R.drawable.quantum_ic_keyboard_arrow_up_white_24,
            context.getString(R.string.action_name_swipe_up),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_SWIPE_UP)));
    // Swipe down.
    menu.add(
        new PointScanMenuItem(
            R.drawable.quantum_ic_keyboard_arrow_down_white_24,
            context.getString(R.string.action_name_swipe_down),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_SWIPE_DOWN)));
    // Custom swipe.
    menu.add(
        new PointScanMenuItem(
            R.drawable.quantum_ic_keyboard_arrow_right_white_24,
            context.getString(R.string.action_name_swipe_custom),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_SWIPE_CUSTOM)));
    // Zoom in.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_zoom_in,
            context.getString(R.string.action_name_zoom_in),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_ZOOM_IN)));
    // Zoom out.
    menu.add(
        new PointScanMenuItem(
            R.drawable.ic_zoom_out,
            context.getString(R.string.action_name_zoom_out),
            manager.createOnClickListenerForAction(PointScanManager.ACTION_ZOOM_OUT)));

    return menu;
  }

  public PointScanMenuItem(
      int iconResource, CharSequence text, View.OnClickListener onClickListener) {
    super(iconResource, text, onClickListener);
  }
}
