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

package com.android.talkback.tutorial;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.android.talkback.R;

public class GridView extends ViewGroup {

    public interface ItemProvider {
        public View getView(ViewGroup parent, int index);
    }

    private final static int DEFAULT_COLUMNS = 4;
    private final static int DEFAULT_ROWS = 4;

    private ItemProvider mItemProvider;
    private int mCellWidth;
    private int mCellHeight;
    private int mColumns;
    private int mRows;
    private int mHorizontalOffset;
    private int mVerticalOffset;

    public GridView(Context context, ItemProvider provider) {
        super(context);
        mItemProvider = provider;
        mCellWidth = context.getResources().getDimensionPixelSize(R.dimen.tutorial_grid_item_width);
        mCellHeight = context.getResources().getDimensionPixelSize(
                R.dimen.tutorial_grid_item_height);
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
            width = DEFAULT_COLUMNS * mCellWidth + getPaddingLeft() + getPaddingRight();
        } else {
            width = widthSize;
        }

        if (heightMode == MeasureSpec.UNSPECIFIED) {
            height = DEFAULT_ROWS * mCellHeight + getPaddingTop() + getPaddingBottom();
        } else {
            height = heightSize;
        }

        int contentWidth = width - getPaddingLeft() - getPaddingRight();
        int contentHeight = height - getPaddingTop() - getPaddingBottom();
        mColumns = contentWidth / mCellWidth;
        mRows = contentHeight / mCellHeight;
        mHorizontalOffset = (contentWidth - (mCellWidth * mColumns)) / 2;
        mVerticalOffset = (contentHeight - (mCellHeight * mRows)) / 2;

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childToLayout = mColumns * mRows;
        removeAllViews();
        inflateViews(childToLayout);

        int rowIndex = 0;
        int colIndex = 0;
        int childIndex = 0;
        while (childIndex < childToLayout) {
            int cellLeft = getPaddingLeft() + mHorizontalOffset + colIndex * mCellWidth;
            int cellTop = getPaddingTop() + mVerticalOffset + rowIndex * mCellHeight;
            View view = getChildAt(childIndex);
            int viewWidth = view.getMeasuredWidth();
            int viewHeight = view.getMeasuredHeight();
            int left = cellLeft + (mCellWidth - viewWidth) / 2;
            int top = cellTop + (mCellHeight - viewHeight) / 2;
            view.layout(left, top, left + viewWidth, top + viewHeight);
            childIndex++;
            colIndex++;
            if (colIndex >= mColumns) {
                colIndex = 0;
                rowIndex++;
            }
        }
    }

    private void inflateViews(int count) {
        if (mItemProvider == null) {
            return;
        }

        int cellWidthMeasureSpec = MeasureSpec.makeMeasureSpec(mCellWidth, MeasureSpec.EXACTLY);
        int cellHeightMeasureSpec = MeasureSpec.makeMeasureSpec(mCellHeight, MeasureSpec.EXACTLY);

        for (int i = 0; i < count; i++) {
            View view = mItemProvider.getView(this, i);
            addView(view);
            view.measure(cellWidthMeasureSpec, cellHeightMeasureSpec);
        }
    }
}
