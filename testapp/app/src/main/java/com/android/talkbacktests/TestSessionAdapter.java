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

package com.android.talkbacktests;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.talkbacktests.testsession.TestSession;

/**
 * A {@link android.widget.ListAdapter} to display a list of test sessions.
 */
public class TestSessionAdapter extends BaseAdapter {

    private final Context mContext;
    private final TestController mController;
    private final NavigationCallback mCallback;

    public TestSessionAdapter(Context context, TestController controller,
                              NavigationCallback callback) {
        mContext = context;
        mController = controller;
        mCallback = callback;
    }

    @Override
    public int getCount() {
        return mController.getSessionCount();
    }

    @Override
    public Object getItem(int position) {
        return mController.getSessionByIndex(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.test_session_item, parent, false);
            final ViewHolder holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.test_case_title);
            holder.description = (TextView) convertView.findViewById(R.id.test_case_description);
            holder.panel = convertView.findViewById(R.id.test_case_panel);
            convertView.setTag(holder);
        }

        final TestSession session = mController.getSessionByIndex(position);
        final ViewHolder holder = (ViewHolder) convertView.getTag();
        holder.title.setText(mContext.getString(
                R.string.test_session_title_template, (position + 1), session.getTitle()));
        holder.description.setText(session.getDescription());
        holder.panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallback.onTestSessionSelected(session.getId());
            }
        });
        return convertView;
    }

    /**
     * A view holder describes an item view's content within the ListView.
     */
    private static final class ViewHolder {
        TextView title;
        TextView description;
        View panel;
    }
}