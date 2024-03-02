/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.accessibility.utils.widget;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import androidx.annotation.StyleRes;

/**
 * A {@link RecyclerView} whose height is the sum of all items' height. The RecyclerView can be in a
 * ScrollView to prevent nested scrolling.
 */
public final class NonScrollableRecyclerView extends RecyclerView {
  public NonScrollableRecyclerView(Context context) {
    super(context);
  }

  public NonScrollableRecyclerView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public NonScrollableRecyclerView(Context context, AttributeSet attrs, @StyleRes int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
    super.onMeasure(widthMeasureSpec, expandSpec);
  }
}
