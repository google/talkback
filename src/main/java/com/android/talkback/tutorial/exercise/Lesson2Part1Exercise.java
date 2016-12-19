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

package com.android.talkback.tutorial.exercise;


import android.content.Context;
import android.widget.AbsListView;
import com.android.talkback.R;

public class Lesson2Part1Exercise extends ListItemExercise {

    private static final int COMPLETE_ON_SCROLL_TO_ITEM = 40;
    private boolean mIsCompleted;

    @Override
    public void onInitialized(Context context) {
        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) { }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
                if (!mIsCompleted &&
                        firstVisibleItem + visibleItemCount > COMPLETE_ON_SCROLL_TO_ITEM) {
                    mIsCompleted = true;
                }
            }
        });
    }
}
