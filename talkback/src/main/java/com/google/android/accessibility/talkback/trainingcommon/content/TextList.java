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

package com.google.android.accessibility.talkback.trainingcommon.content;

import static android.view.View.VISIBLE;
import static androidx.core.content.res.ResourcesCompat.ID_NULL;

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.ArrayRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
import com.google.android.accessibility.utils.widget.NonScrollableListView;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A list view. */
public class TextList extends PageContentConfig {

  /** A string array of list item titles. */
  @ArrayRes private final int titlesResId;

  /** A string array of list item summaries. */
  @ArrayRes private final int summariesResId;

  public TextList(@ArrayRes int titlesResId) {
    this(titlesResId, ID_NULL);
  }

  public TextList(@ArrayRes int titlesResId, @ArrayRes int summariesResId) {
    this.titlesResId = titlesResId;
    this.summariesResId = summariesResId;
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view = inflater.inflate(R.layout.training_text_list, container, false);
    final NonScrollableListView listView = view.findViewById(R.id.training_text_list);
    final String[] titles = context.getResources().getStringArray(titlesResId);
    final String[] summaries;
    if (summariesResId == ID_NULL) {
      summaries = new String[titles.length];
      Arrays.fill(summaries, "");
    } else {
      summaries = context.getResources().getStringArray(summariesResId);
    }
    listView.setAdapter(new TextListAdapter(context, titles, summaries));
    listView.setDividerHeight(
        context.getResources().getDimensionPixelSize(R.dimen.training_list_item_padding));
    return view;
  }

  private static class TextListAdapter extends BaseAdapter {
    private final Context context;
    private final List<Pair<String, String>> titleSummaryPairList = new ArrayList<>();

    private TextListAdapter(Context context, String[] titles, String[] summaries) {
      this.context = context;
      Preconditions.checkArgument(titles.length == summaries.length);
      for (int i = 0; i < titles.length; i++) {
        titleSummaryPairList.add(new Pair<>(titles[i], summaries[i]));
      }
    }

    @Override
    public int getCount() {
      return titleSummaryPairList.size();
    }

    @Override
    public Pair<String, String> getItem(int position) {
      return titleSummaryPairList.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater layoutInflater =
          (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      final View view = layoutInflater.inflate(R.layout.training_list_item, parent, false);
      TextView titleView = view.findViewById(R.id.training_list_item_title);
      titleView.setText(getItem(position).first);
      TextView summaryView = view.findViewById(R.id.training_list_item_summary);
      String summary = getItem(position).second;
      if (summaryView != null && !TextUtils.isEmpty(summary)) {
        summaryView.setText(getItem(position).second);
        summaryView.setVisibility(VISIBLE);
      }
      return view;
    }
  }
}
