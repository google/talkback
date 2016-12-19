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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.talkbacktests.R;

public class AutoScrollTest extends BaseTestContent {
    private static final int LIST_SIZE = 30;

    public AutoScrollTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_auto_scroll, container, false);
        ListView list = (ListView) view.findViewById(R.id.test_auto_scroll_listview);
        String[] content = new String[LIST_SIZE];
        for (int i = 0; i < LIST_SIZE; i++) {
            content[i] = getString(R.string.list_item_template1, i);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_list_item_1, content);
        list.setAdapter(adapter);

        return view;
    }
}
