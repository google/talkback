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
import android.os.Bundle;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.android.talkbacktests.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CollapsibleExpandableViewTest extends BaseTestContent {
    private static final int GROUP_SIZE = 15;
    private static final int ITEM_SIZE = 10;

    private Context mContext;
    private ExpandableListView mExpListView;
    private List<String> mListHeader;
    private HashMap<String, List<String>> mListData;

    public CollapsibleExpandableViewTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_collapsible_expandable_view, container, false);
        mExpListView = (ExpandableListView) view.findViewById(
                R.id.expListView);
        mContext = context;

        prepareData();

        mExpListView.setAdapter(new MyListAdapter());

        return view;
    }

    private void prepareData() {
        mListHeader = new ArrayList<>();
        mListData = new HashMap<>();

        for (int i = 0; i < GROUP_SIZE; i++) {
            mListHeader.add(getString(R.string.list_header_template, (i + 1)));
            List<String> child = new ArrayList<>();
            for (int j = 0; j < ITEM_SIZE; j++) {
                child.add(getString(R.string.list_item_template2, (i + 1), (j + 1)));
            }
            mListData.put(mListHeader.get(i), child);
        }
    }

    private final class MyListAdapter extends BaseExpandableListAdapter {

        @Override
        public int getGroupCount() {
            return mListHeader.size();
        }

        @Override
        public int getChildrenCount(int index) {
            return mListData.get(mListHeader.get(index)).size();
        }

        @Override
        public Object getGroup(int index) {
            return mListHeader.get(index);
        }

        @Override
        public Object getChild(int groupIndex, int childIndex) {
            return mListData.get(mListHeader.get(groupIndex)).get(childIndex);
        }

        @Override
        public long getGroupId(int index) {
            return index;
        }

        @Override
        public long getChildId(int groupIndex, int childIndex) {
            return childIndex;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(final int index, final boolean expanded, View view,
                                 ViewGroup viewGroup) {
            if (view == null) {
                view = LayoutInflater.from(mContext).inflate(
                        R.layout.collapsible_expandable_listgroup, null);
            }

            ViewCompat.setAccessibilityDelegate(view, new MyDelegate(index));
            TextView headerText = (TextView) view.findViewById(R.id.listHeader);
            headerText.setText((String) getGroup(index));

            return view;
        }

        @Override
        public View getChildView(int groupIndex, int childIndex, boolean expanded,
                                 View view, ViewGroup viewGroup) {
            if (view == null) {
                view = LayoutInflater.from(mContext).inflate(
                        R.layout.collapsible_expandable_listitem, null);
            }

            TextView itemText = (TextView) view.findViewById(R.id.listItem);
            itemText.setText((String) getChild(groupIndex, childIndex));

            return view;
        }

        @Override
        public boolean isChildSelectable(int groupIndex, int childIndex) {
            return true;
        }
    }

    private final class MyDelegate extends AccessibilityDelegateCompat {
        final int mIndex;

        MyDelegate(int index) {
            mIndex = index;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host,
                                                      AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            if (mExpListView.isGroupExpanded(mIndex)) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_COLLAPSE);
            } else {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_EXPAND);
            }
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            boolean result;
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
                    result = mExpListView.collapseGroup(mIndex);
                    break;
                case AccessibilityNodeInfoCompat.ACTION_EXPAND:
                    result = mExpListView.expandGroup(mIndex);
                    break;
                default:
                    result = super.performAccessibilityAction(host, action, args);
                    break;
            }
            return result;
        }
    }
}