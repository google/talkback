/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.tutorial;

import android.content.Context;
import androidx.core.view.ViewCompat;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.accessibility.talkback.R;

public class GridView extends ViewGroup {

  public interface ItemProvider {
    public View getView(ViewGroup parent, int index);
  }

  private static final int DEFAULT_COLUMNS = 4;
  private static final int DEFAULT_ROWS = 3;

  private ItemProvider itemProvider;
  private int cellWidth;
  private int cellHeight;
  private int columns;
  private int rows;
  private int horizontalOffset;
  private int verticalOffset;

  public GridView(Context context, ItemProvider provider) {
    super(context);
    itemProvider = provider;
    cellWidth = context.getResources().getDimensionPixelSize(R.dimen.tutorial_grid_item_width);
    cellHeight = context.getResources().getDimensionPixelSize(R.dimen.tutorial_grid_item_height);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);

    int width;
    int height;

    if (widthMode == MeasureSpec.UNSPECIFIED) {
      width = DEFAULT_COLUMNS * cellWidth + getPaddingLeft() + getPaddingRight();
    } else {
      width = widthSize;
    }

    if (heightMode == MeasureSpec.UNSPECIFIED) {
      height = DEFAULT_ROWS * cellHeight + getPaddingTop() + getPaddingBottom();
    } else {
      height = heightSize;
    }

    int contentWidth = width - getPaddingLeft() - getPaddingRight();
    int contentHeight = height - getPaddingTop() - getPaddingBottom();
    columns = contentWidth / cellWidth;
    rows = contentHeight / cellHeight;
    horizontalOffset = (contentWidth - (cellWidth * columns)) / 2;
    verticalOffset = (contentHeight - (cellHeight * rows)) / 2;

    setMeasuredDimension(width, height);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int childToLayout = columns * rows;
    removeAllViews();
    inflateViews(childToLayout);

    int rowIndex = 0;
    int colIndex = 0;
    int childIndex = 0;
    boolean isRTL = ViewCompat.getLayoutDirection(this) == LAYOUT_DIRECTION_RTL;
    while (childIndex < childToLayout) {
      int cellLeft = getPaddingLeft() + horizontalOffset + colIndex * cellWidth;
      int cellTop = getPaddingTop() + verticalOffset + rowIndex * cellHeight;
      // Assume the columns is 4, when layout direction is LTR
      // the layout position is calculated according to child index 0, 1, 2, 3 in 1st row,
      // 4, 5, 6, 7 in the 2nd row, and so forth.
      // On the other hand, when the layout direction is RTL, (views are flipped horizontally)
      // the layout position is calculated according to child index 3, 2, 1, 0 in 1st row,
      // 7, 6, 5, 4 in the 2nd row, and so forth
      int cellIndex = isRTL ? ((rowIndex + 1) * columns) - colIndex - 1 : childIndex;

      // If layout direction is RTL, it's possible the grid view is not fully deployed.
      // In this case, when we count the view layout position backward, we have to confirm the view
      // exists.
      if (cellIndex < childToLayout) {
        View view = getChildAt(cellIndex);
        int viewWidth = view.getMeasuredWidth();
        int viewHeight = view.getMeasuredHeight();
        int left = cellLeft + (cellWidth - viewWidth) / 2;
        int top = cellTop + (cellHeight - viewHeight) / 2;
        view.layout(left, top, left + viewWidth, top + viewHeight);
      }
      childIndex++;
      colIndex++;
      if (colIndex >= columns) {
        colIndex = 0;
        rowIndex++;
      }
    }
  }

  private void inflateViews(int count) {
    if (itemProvider == null) {
      return;
    }

    int cellWidthMeasureSpec = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY);
    int cellHeightMeasureSpec = MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY);

    for (int i = 0; i < count; i++) {
      View view = itemProvider.getView(this, i);
      addView(view);
      view.measure(cellWidthMeasureSpec, cellHeightMeasureSpec);
    }
  }
}
