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
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class SimplePageAdapter extends PagerAdapter {
    private Context mContext;
    private int mPageCount = 0;

    public SimplePageAdapter(Context context, int pageCount) {
        mContext = context;
        mPageCount = pageCount;
    }

    @Override
    public int getCount() {
        return mPageCount;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup collection, int position) {
        TextView textView = (TextView) LayoutInflater.from(mContext)
                .inflate(R.layout.fragment_simple_text, collection, false);
        textView.setText(mContext.getString(R.string.simple_tab_content_template, (position + 1)));
        collection.addView(textView);
        return textView;
    }

    @Override
    public void destroyItem(ViewGroup collection, int position, Object view) {
        collection.removeView((View) view);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mContext.getString(R.string.tab_title_template, (position + 1));
    }
}
