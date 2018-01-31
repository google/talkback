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

import android.view.View;

/** Holds all data required to construct an item in a Switch Access menu. */
public class MenuItem {
  protected int mIconResource;
  protected CharSequence mText;
  protected View.OnClickListener mOnClickListener;

  public MenuItem(int iconResource, CharSequence text, View.OnClickListener onClickListener) {
    mIconResource = iconResource;
    mText = text;
    mOnClickListener = onClickListener;
  }

  public int getIconResource() {
    return mIconResource;
  }

  public CharSequence getText() {
    return mText;
  }

  public View.OnClickListener getOnClickListener() {
    return mOnClickListener;
  }
}
