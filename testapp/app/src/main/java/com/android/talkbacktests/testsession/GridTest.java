/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkbacktests.testsession;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class GridTest extends BaseTestContent {

    private static final int GRID_SIZE = 30;

    public GridTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_grid, container, false);

        GridView gridView = (GridView) view.findViewById(R.id.test_grid_gridView);
        gridView.setAdapter(new GridViewAdapter(context, GRID_SIZE));

        return view;
    }

    private final class GridViewAdapter extends BaseAdapter {

        private final Context mContext;
        private final int mCount;

        public GridViewAdapter(Context context, int count) {
            mContext = context;
            mCount = count;
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            TextView textView;
            if (view == null) {
                textView = new TextView(mContext);
                textView.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 300));
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(8, 8, 8, 8);
            } else {
                textView = (TextView) view;
            }

            textView.setText(getString(R.string.list_item_template1, i));
            return textView;
        }
    }
}
