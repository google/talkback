/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.screensummary.SummaryOutput.LocationData;
import com.google.android.accessibility.utils.Performance;
import java.util.List;

/**
 * Creates an expandable list. Will close and redirect accessibility focus to corresponding items on
 * screen when summary details are selected.
 */
public class ExpandableListAdapter extends BaseExpandableListAdapter {

  private Context context;
  private List<LocationData> locationDataList;
  private Dialog dialog;

  /**
   * NodeList is used to redirect the accessibility focus when an item is clicked. Dialog is a
   * reference to the dialog containing the summary, so that the summary dialog can be closed before
   * accessibility focus is moved.
   */
  public ExpandableListAdapter(
      Context startContext, List<LocationData> startLocationData, Dialog startDialog) {
    this.context = startContext;
    this.locationDataList = startLocationData;
    this.dialog = startDialog;
  }

  @Override
  public Object getChild(int groupPosition, int childPosition) {
    return this.locationDataList
        .get(groupPosition)
        .getNodeDataList()
        .get(childPosition)
        .getDescription();
  }

  @Override
  public long getChildId(int groupPosition, int childPosition) {
    return childPosition;
  }

  @Override
  public View getChildView(
      int groupPosition,
      final int childPosition,
      boolean isLastChild,
      View convertView,
      ViewGroup parent) {

    final String childText = (String) getChild(groupPosition, childPosition);

    if (convertView == null) {
      LayoutInflater layoutInflater =
          (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      convertView = layoutInflater.inflate(R.layout.summary_list_item, /* root= */ null);
    }

    TextView txtListChild = (TextView) convertView.findViewById(R.id.summaryListItem);

    txtListChild.setText(childText);

    /** When a list item is clicked, close the alert dialog and redirect the accessibility focus. */
    Context context = this.context;
    Dialog dialog = this.dialog;
    List<LocationData> locationList = this.locationDataList;
    convertView.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            dialog.cancel();
            locationList
                .get(groupPosition)
                .getNodeDataList()
                .get(childPosition)
                .getNode()
                .performAction(
                    AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                    Performance.EVENT_ID_UNTRACKED);
          }
        });

    return convertView;
  }

  @Override
  public int getChildrenCount(int groupPosition) {
    return this.locationDataList.get(groupPosition).getNodeDataList().size();
  }

  @Override
  public Object getGroup(int groupPosition) {
    return this.locationDataList.get(groupPosition).getHeading();
  }

  @Override
  public int getGroupCount() {
    return this.locationDataList.size();
  }

  @Override
  public long getGroupId(int groupPosition) {
    return groupPosition;
  }

  @Override
  public View getGroupView(
      int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
    String headerTitle = (String) getGroup(groupPosition);
    if (convertView == null) {
      LayoutInflater layoutInflater =
          (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      convertView = layoutInflater.inflate(R.layout.summary_list_group, /* root= */ null);
    }

    TextView summaryListHeader = (TextView) convertView.findViewById(R.id.summaryListHeader);
    summaryListHeader.setTypeface(null, Typeface.BOLD);
    summaryListHeader.setText(headerTitle);
    convertView.setClickable(false);
    return convertView;
  }

  @Override
  public boolean hasStableIds() {
    return false;
  }

  @Override
  public boolean isChildSelectable(int groupPosition, int childPosition) {
    return true;
  }
}
