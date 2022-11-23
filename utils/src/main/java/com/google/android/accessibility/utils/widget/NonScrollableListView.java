/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.utils.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * A {@link ListView} whose height is the sum of all items' height. The ListView can be inside a
 * ScrollView to prevent nested scrolling.
 */
public class NonScrollableListView extends ListView {
  public NonScrollableListView(Context context) {
    super(context);
  }

  public NonScrollableListView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public NonScrollableListView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public NonScrollableListView(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // The size and mode of measure specification are stored in an integer. There are three modes,
    // so 2 bits represents the mode and the rest represents the size.
    int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
    super.onMeasure(widthMeasureSpec, expandSpec);
  }
}
