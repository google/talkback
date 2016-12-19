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
import android.support.v4.widget.PopupWindowCompat;
import android.support.v7.widget.ListPopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.talkbacktests.R;

public class PopupViewTest extends BaseTestContent implements View.OnClickListener {

    private static final Integer[] sWindowHeight = new Integer[]{
            ViewGroup.LayoutParams.WRAP_CONTENT, 200, 400, 600, 800};

    private static final Integer[] sListSize = new Integer[]{
            1, 2, 3, 5, 10, 20};

    private final String[] mWindowTypes;

    private Spinner mWindowTypeSpinner;
    private Spinner mWindowHeightSpinner;
    private Spinner mListSizeSpinner;
    private CheckBox mModalCheckbox;
    private CheckBox mDefaultInflaterCheckbox;

    public PopupViewTest(Context context, String subtitle, String description) {
        super(context, subtitle, description);
        mWindowTypes = context.getResources().getStringArray(R.array.window_type_array);
    }

    @Override
    public View getView(final LayoutInflater inflater, ViewGroup container, Context context) {
        View view = inflater.inflate(R.layout.test_popup_view, container, false);

        final ArrayAdapter<String> windowTypeAdapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, mWindowTypes);
        windowTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final ArrayAdapter<Integer> windowHeightAdapter = new ArrayAdapter<Integer>(
                context, android.R.layout.simple_spinner_item, sWindowHeight) {

        };
        windowHeightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final ArrayAdapter<Integer> listSizeAdapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, sListSize);
        listSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);


        mWindowTypeSpinner = (Spinner) view.findViewById(R.id.window_type_spinner);
        mWindowTypeSpinner.setAdapter(windowTypeAdapter);
        mWindowTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                if (pos == 0) {
                    mModalCheckbox.setEnabled(false);
                } else {
                    mModalCheckbox.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mModalCheckbox.setEnabled(false);
            }
        });
        mWindowHeightSpinner = (Spinner) view.findViewById(R.id.window_height_spinner);
        mWindowHeightSpinner.setAdapter(windowHeightAdapter);
        mListSizeSpinner = (Spinner) view.findViewById(R.id.list_size_spinner);
        mListSizeSpinner.setAdapter(listSizeAdapter);
        mListSizeSpinner.setSelection(4); // Set initial size to 10. The index is 4.
        mModalCheckbox = (CheckBox) view.findViewById(R.id.modal_checkbox);
        mModalCheckbox.setChecked(true);
        mDefaultInflaterCheckbox = (CheckBox) view.findViewById(R.id.inflater_checkbox);
        mDefaultInflaterCheckbox.setChecked(true);
        view.findViewById(R.id.button).setOnClickListener(this);
        return view;
    }


    @Override
    public void onClick(View v) {
        final String windowType = (String) mWindowTypeSpinner.getSelectedItem();
        final int windowHeight = (int) mWindowHeightSpinner.getSelectedItem();
        final int listSize = (int) mListSizeSpinner.getSelectedItem();
        final boolean useDefaultInfalter = mDefaultInflaterCheckbox.isChecked();
        if (mWindowTypes[0].equals(windowType)) {
            showPopupWindow(v, windowHeight, listSize, useDefaultInfalter);
        } else {
            final boolean isModal = mModalCheckbox.isChecked();
            showListPopupWindow(v, windowHeight, listSize, isModal, useDefaultInfalter);
        }
    }

    /**
     * Shows a PopupWindow at the anchor view with given window height and list size.
     */
    private void showPopupWindow(View button, int height, int listSize,
                                 boolean useDefaultInflater) {
        final Context context = button.getContext();
        final ListView listView = new ListView(context);
        final BaseAdapter adapter;
        if (useDefaultInflater) {
            adapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_list_item_1, android.R.id.text1,
                    createSampleArray(listSize));
        } else {
            adapter = new MyAdapter(button.getContext(), createSampleArray(listSize));
        }
        listView.setAdapter(adapter);


        listView.setVerticalScrollBarEnabled(true);
        listView.setBackgroundColor(0xFFFFFF);
        final PopupWindow window = new PopupWindow(listView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                height,
                true);
        window.setBackgroundDrawable(context.getResources().getDrawable(
                android.R.drawable.editbox_background));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (window.isShowing()) {
                    window.dismiss();
                }
            }
        });
        PopupWindowCompat.showAsDropDown(window, button, 0, 0, Gravity.NO_GRAVITY);
    }

    /**
     * Shows a ListPopupWindow at the anchor view with given window height, list size and isModal.
     */
    private void showListPopupWindow(View button, int height, int listSize, boolean isModal,
                                     boolean useDefaultInflater) {
        final Context context = button.getContext();

        final BaseAdapter adapter;
        if (useDefaultInflater) {
            adapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_list_item_1, android.R.id.text1,
                    createSampleArray(listSize));
        } else {
            adapter = new MyAdapter(button.getContext(), createSampleArray(listSize));
        }
        final ListPopupWindow window = new ListPopupWindow(context, null);
        window.setAnchorView(button);
        window.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setHeight(height);
        window.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (window.isShowing()) {
                    window.dismiss();
                }
            }
        });
        window.setModal(isModal);
        window.setAdapter(adapter);
        window.show();
    }

    private String[] createSampleArray(int size) {
        String[] result = new String[size];
        for (int i = 0; i < size; i++) {
            result[i] = getContext().getString(R.string.list_item_template1, (i + 1));
        }
        return result;
    }

    private static final class MyAdapter extends BaseAdapter {
        private String[] mItems;
        private Context mContext;

        public MyAdapter(Context context, String[] items) {
            mItems = items;
            mContext = context;
        }

        @Override
        public int getCount() {
            return mItems.length;
        }

        @Override
        public Object getItem(int i) {
            return mItems[i];
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup viewGroup) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(
                        R.layout.test_popupview_list_item, viewGroup, false);
                holder = new ViewHolder();
                holder.text = (TextView) convertView.findViewById(R.id.text);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.text.setText(mItems[i]);
            return convertView;
        }
    }

    private static final class ViewHolder {
        public TextView text;

    }
}