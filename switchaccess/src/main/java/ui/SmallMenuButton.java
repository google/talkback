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

package com.google.android.accessibility.switchaccess.ui;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.accessibility.switchaccess.R;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Small menu button consisting of an icon and text to the icon's right. Currently used for "close"
 * and "back" at the bottom of Switch Access menus.
 */
public class SmallMenuButton extends MenuButton {

  public SmallMenuButton(Context context) {
    this(context, null);
  }

  public SmallMenuButton(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SmallMenuButton(Context context, @Nullable AttributeSet attrs, int defStyleRes) {
    this(context, attrs, defStyleRes, 0);
  }

  // This constructor needs to be public because it is for an inflated view.
  @SuppressWarnings("WeakerAccess")
  public SmallMenuButton(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes, R.layout.switch_access_small_menu_button);
  }
}
