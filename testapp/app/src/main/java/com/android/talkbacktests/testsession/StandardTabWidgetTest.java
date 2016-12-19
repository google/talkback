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

import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.talkbacktests.R;

public class StandardTabWidgetTest extends BaseTestContent {

    public StandardTabWidgetTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_standard_tab_widget, container, false);

        TabLayout tabLayout = (TabLayout) view.findViewById(R.id.test_standard_tab_widget_tabs);
        ViewPager viewPager = (ViewPager) view.findViewById(
                R.id.test_standard_tab_widget_viewpager);
        viewPager.setAdapter(new SimplePageAdapter(context, 8));
        tabLayout.setupWithViewPager(viewPager);

        return view;
    }
}