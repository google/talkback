/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.training.content;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.ArrayRes;
import com.google.android.accessibility.talkback.R;

/** A list view. */
public class TextList extends PageContentConfig {

  /** A string array of list texts. */
  private final @ArrayRes int textsResId;

  public TextList(@ArrayRes int textsResId) {
    this.textsResId = textsResId;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view = inflater.inflate(R.layout.training_text_list, container, false);
    final ListView listView = view.findViewById(R.id.training_text_list);
    final String[] texts = context.getResources().getStringArray(textsResId);
    listView.setAdapter(
        new BaseAdapter() {
          @Override
          public int getCount() {
            return texts.length;
          }

          @Override
          public String getItem(int position) {
            return texts[position];
          }

          @Override
          public long getItemId(int position) {
            return position;
          }

          @Override
          public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
              LayoutInflater layoutInflater =
                  (LayoutInflater)
                      context.getSystemService(android.content.Context.LAYOUT_INFLATER_SERVICE);
              convertView = layoutInflater.inflate(R.layout.training_list_item, parent, false);
            }
            TextView textview = convertView.findViewById(R.id.training_list_item_text);
            textview.setText(getItem(position));
            return convertView;
          }
        });
    LayoutParams layoutParams = listView.getLayoutParams();
    layoutParams.height =
        texts.length
            * (int)
                (context.getResources().getDimension(R.dimen.training_list_item_height)
                    + context.getResources().getDimension(R.dimen.training_list_item_margin_top)
                    + context
                        .getResources()
                        .getDimension(R.dimen.training_list_item_margin_bottom));
    listView.setLayoutParams(layoutParams);
    return view;
  }
}
