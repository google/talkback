/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.accessibility.talkback.screensummary;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.screensummary.SummaryOutput.SummaryViewInfo;
import com.google.android.accessibility.utils.Performance;
import java.util.ArrayList;

/** An adapter to place summary output into a ListView. */
public class SummaryListAdapter extends BaseAdapter {
  private final ArrayList<SummaryViewInfo> viewInfoList;
  private final Context context;
  private Dialog dialog;

  public SummaryListAdapter(
      Context startContext, ArrayList<SummaryViewInfo> startViewInfoList, Dialog dialog) {
    this.viewInfoList = startViewInfoList;
    this.context = startContext;
    this.dialog = dialog;
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public Object getItem(int position) {
    return this.viewInfoList.get(position);
  }

  @Override
  public int getCount() {
    return this.viewInfoList.size();
  }

  @Override
  public int getViewTypeCount() {
    return SummaryOutput.NUM_OF_TYPES;
  }

  @Override
  public int getItemViewType(int position) {
    return viewInfoList.get(position).viewType;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {

    // Inflate a new header or item view
    if (convertView == null) {
      LayoutInflater layoutInflater =
          (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      switch (getItemViewType(position)) {
        case SummaryOutput.TYPE_ITEM:
          convertView = layoutInflater.inflate(R.layout.summary_list_item, /* root= */ null);
          break;
        case SummaryOutput.TYPE_HEADING:
          convertView = layoutInflater.inflate(R.layout.summary_list_group, /* root= */ null);
          TextView summaryListHeader = (TextView) convertView.findViewById(R.id.summaryListHeader);
          summaryListHeader.setTypeface(null, Typeface.BOLD);
          break;
        default:
          return convertView;
      }
    }

    SummaryViewInfo currentViewInfo = (SummaryViewInfo) this.viewInfoList.get(position);

    // Update an existing view
    switch (getItemViewType(position)) {
      case SummaryOutput.TYPE_ITEM:
        TextView txtListChild = (TextView) convertView.findViewById(R.id.summaryListItem);
        txtListChild.setText(currentViewInfo.text);
        convertView.setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                dialog.cancel();
                currentViewInfo.node.performAction(
                    AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                    Performance.EVENT_ID_UNTRACKED);
                // TODO:Use focus management to perform accessibility focus action
              }
            });
        break;
      case SummaryOutput.TYPE_HEADING:
        TextView summaryListHeader = (TextView) convertView.findViewById(R.id.summaryListHeader);
        summaryListHeader.setText(currentViewInfo.text);
        break;
      default:
        break;
    }
    return convertView;
  }
}
