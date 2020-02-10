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

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.IntDef;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ListView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.screensummary.TreeTraversal.NodeData;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.widget.DialogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Will be called from GestureController and used to start the activity that will display summary
 * output on screen.
 */
public class SummaryOutput {
  public static final int NUM_OF_TYPES = 2;

  /** Summary row view types */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TYPE_HEADING,
    TYPE_ITEM,
  })
  public @interface SummaryLineType {}

  public static final int TYPE_HEADING = 0;
  public static final int TYPE_ITEM = 1;

  /**
   * Starts the SummaryActivity, passing it the application window which will be used to generate
   * the screen summary.
   */
  public static void showOutput(AccessibilityService service) {
    List<AccessibilityWindowInfo> windows = AccessibilityServiceCompatUtils.getWindows(service);
    ArrayList<ArrayList<NodeData>> nodeDataList = new ArrayList<ArrayList<NodeData>>();
    for (int i = 0; i < TreeTraversal.ZONE_COUNT; i++) {
      nodeDataList.add(new ArrayList<NodeData>());
    }
    // Collect summary info from all windows of type application to account for split screen mode.
    // Also collect info if the window is an active system window, such as notification shade.
    for (AccessibilityWindowInfo window : windows) {
      int windowType = window.getType();
      if (windowType == AccessibilityWindowInfo.TYPE_APPLICATION
          || (window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM && window.isActive())) {
        ArrayList<ArrayList<NodeData>> windowSummary = TreeTraversal.checkRootNode(window, service);
        for (int i = 0; i < TreeTraversal.ZONE_COUNT; i++) {
          nodeDataList.get(i).addAll(windowSummary.get(i));
        }
      }
    }
    // Attach summary elements to their location names in LocationData. Only add to the LocationData
    // list if the list of summary items is non empty.
    ArrayList<LocationData> locationDataList = new ArrayList<LocationData>();
    String[] locationNames = getLocationNames(service);
    for (int i = 0; i < TreeTraversal.ZONE_COUNT; i++) {
      ArrayList<NodeData> nodeDataSubList = nodeDataList.get(i);
      if (!nodeDataSubList.isEmpty()) {
        locationDataList.add(new LocationData(locationNames[i], nodeDataList.get(i)));
      }
    }

    showDialog(service, locationDataList);
  }
  /** Creates an AlertDialog containing a list with summary output. */
  private static void showDialog(Context context, ArrayList<LocationData> locationDataList) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(context.getString(R.string.title_screen_summary));
    builder.setOnDismissListener(
        new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            recycleNodes(locationDataList);
          }
        });
    builder.setNegativeButton(
        context.getString(R.string.title_cancel_button),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
          }
        });
    AlertDialog alert = builder.create();
    ListView listView = getSummaryListView(locationDataList, context, alert);
    alert.setView(listView);
    // Without setting a window type, alert dialog shows an error.
    DialogUtils.setWindowTypeToDialog(alert.getWindow());
    alert.show();
  }

  /** Given a list of LocationData, create an list view and return it. */
  private static ListView getSummaryListView(
      ArrayList<LocationData> locationDataList, Context context, AlertDialog alert) {

    ArrayList<SummaryViewInfo> viewInfoList = new ArrayList<>();
    // Add a header with the location name and then add list items containing summary elements
    for (LocationData locationData : locationDataList) {
      SummaryViewInfo groupViewInfo = new SummaryViewInfo(TYPE_HEADING, locationData.getHeading());
      viewInfoList.add(groupViewInfo);

      List<NodeData> nodeDataList = locationData.getNodeDataList();
      for (NodeData nodeData : nodeDataList) {
        SummaryViewInfo childViewInfo =
            new SummaryViewInfo(
                TYPE_ITEM, nodeData.getDescription().toString(), nodeData.getNode());
        viewInfoList.add(childViewInfo);
      }
    }

    SummaryListAdapter listAdapter = new SummaryListAdapter(context, viewInfoList, alert);
    ListView listView = new ListView(context);
    listView.setAdapter(listAdapter);
    return listView;
  }

  /** One-time assignment of values to the locationNames array. */
  private static String[] getLocationNames(Context context) {
    String topLeft = context.getString(R.string.value_location_top_left);
    String top = context.getString(R.string.value_location_top);
    String topRight = context.getString(R.string.value_location_top_right);
    String left = context.getString(R.string.value_location_left);
    String center = context.getString(R.string.value_location_center);
    String right = context.getString(R.string.value_location_right);
    String bottomLeft = context.getString(R.string.value_location_bottom_left);
    String bottom = context.getString(R.string.value_location_bottom);
    String bottomRight = context.getString(R.string.value_location_bottom_right);
    String[] locationNames =
        new String[] {topLeft, top, topRight, left, center, right, bottomLeft, bottom, bottomRight};
    return locationNames;
  }

  /**
   * A class to hold location names which will be group headers and their corresponding summary
   * lists.
   */
  public static class LocationData {
    private final String heading;
    private final List<NodeData> nodeDataList;

    public LocationData(String startHeading, List<NodeData> startNodeDataList) {
      heading = startHeading;
      nodeDataList = startNodeDataList;
    }

    public String getHeading() {
      return heading;
    }

    public List<NodeData> getNodeDataList() {
      return nodeDataList;
    }
  }

  /**
   * A class to make easily accessible the info from {@code LocationData} for creating and changing
   * views in {@link SummaryListAdapter}.
   */
  public static class SummaryViewInfo {
    public final @SummaryLineType int viewType;
    public final String text;
    @Nullable public final AccessibilityNode node;

    public SummaryViewInfo(@SummaryLineType int viewType, String text) {
      this.viewType = viewType;
      this.text = text;
      this.node = null;
    }

    /** Creates a SummaryViewInfo object. Caller is responsible for recycling the node. */
    public SummaryViewInfo(@SummaryLineType int viewType, String text, AccessibilityNode node) {
      this.viewType = viewType;
      this.text = text;
      this.node = node;
    }
  }

  /** Will recycle nodes when alert dialog is closed. */
  public static void recycleNodes(List<LocationData> locationList) {
    for (LocationData locationData : locationList) {
      List<NodeData> nodeDataList = locationData.getNodeDataList();
      for (NodeData nodeData : nodeDataList) {
        AccessibilityNode node = nodeData.getNode();
        node.recycle("SummaryOutput.recycleNodes");
      }
    }
  }
}
