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
import android.util.AttributeSet;

/**
 * Small menu button consisting of an icon and text to the icon's right. Currently used for "cancel"
 * and "more" options at the bottom of Switch Access menus.
 */
public class SmallMenuButton extends MenuButton {

  public SmallMenuButton(Context context) {
    this(context, null);
  }

  public SmallMenuButton(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SmallMenuButton(Context context, AttributeSet attrs, int defStyleRes) {
    this(context, attrs, defStyleRes, 0);
  }

  public SmallMenuButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected int getLayoutResource() {
    return R.layout.switch_access_small_menu_button;
  }
}
