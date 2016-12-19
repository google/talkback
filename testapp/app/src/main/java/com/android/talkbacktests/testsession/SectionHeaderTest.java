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
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class SectionHeaderTest extends BaseTestContent {

    public SectionHeaderTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_section_header, container, false);
        ListView listView = (ListView) view.findViewById(R.id.test_section_header_listview);
        MyAdapter adapter =
                new MyAdapter(context.getResources().getStringArray(
                        R.array.test_section_header_string_array),
                        context.getResources().getIntArray(R.array.test_section_header_item_type));
        listView.setAdapter(adapter);
        return view;
    }

    private static final class MyAdapter extends BaseAdapter {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private String[] mData;
        private int[] mIsHead;

        public MyAdapter(String[] data, int[] isHead) {
            mData = data;
            mIsHead = isHead;
        }

        @Override
        public int getCount() {
            return mData.length;
        }

        @Override
        public Object getItem(int position) {
            return mData[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return mIsHead[position] == 1 ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            int type = getItemViewType(position);

            if (convertView == null) {
                holder = new ViewHolder();
                switch (type) {
                    case TYPE_HEADER:
                        convertView = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.test_section_header_header, null);
                        convertView.setAccessibilityDelegate(
                                new ListItemAccessibilityDelegate(true, position));
                        holder.textView = (TextView) convertView.findViewById(R.id.textview_header);
                        break;
                    case TYPE_ITEM:
                        convertView = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.test_section_header_item, null);
                        convertView.setAccessibilityDelegate(
                                new ListItemAccessibilityDelegate(false, position));
                        holder.textView = (TextView) convertView.findViewById(R.id.textview_item);
                        break;
                }
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.textView.setText(mData[position]);

            return convertView;
        }
    }

    private static final class ViewHolder {
        public TextView textView;
    }

    private static final class ListItemAccessibilityDelegate extends View.AccessibilityDelegate {
        private final boolean mIsHeading;
        private final int mRowIndex;

        public ListItemAccessibilityDelegate(boolean isHeading, int rowIndex) {
            mIsHeading = isHeading;
            mRowIndex = rowIndex;
        }

        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo
                        .obtain(mRowIndex, 0, 0, 0, mIsHeading));
            }
        }
    }
}