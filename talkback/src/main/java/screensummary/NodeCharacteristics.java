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


import android.content.Context;
import android.graphics.Rect;
import androidx.annotation.IntDef;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import com.google.android.accessibility.compositor.R;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.Role;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * An outline of the methods that will be used to describe, locate, and group AccessibilityNodeInfo
 * objects. This class will be used by TreeTransversal to categorize nodes as they are visited.
 */
public class NodeCharacteristics {
  /** Represents the 3X3 grid that the screen will be split into, used to locate nodes. */
  @IntDef({
    LOCATION_TOP_LEFT,
    LOCATION_TOP,
    LOCATION_TOP_RIGHT,
    LOCATION_LEFT,
    LOCATION_CENTER,
    LOCATION_RIGHT,
    LOCATION_BOTTOM_LEFT,
    LOCATION_BOTTOM,
    LOCATION_BOTTOM_RIGHT
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface Location {}

  public static final int LOCATION_TOP_LEFT = 0;
  public static final int LOCATION_TOP = 1;
  public static final int LOCATION_TOP_RIGHT = 2;
  public static final int LOCATION_LEFT = 3;
  public static final int LOCATION_CENTER = 4;
  public static final int LOCATION_RIGHT = 5;
  public static final int LOCATION_BOTTOM_LEFT = 6;
  public static final int LOCATION_BOTTOM = 7;
  public static final int LOCATION_BOTTOM_RIGHT = 8;

  /**
   * These arrays determine the percentages that will divide the screen up into blocks for location
   * purposes. These are subject to change in the future based on user feedback.
   */
  private static final double[] horizontalZone = new double[] {0.0, 0.2, 0.8, 1.0};

  private static final double[] verticalZone = new double[] {0.0, 0.33, 0.67, 1.0};

  /** Returns a CharSequence that is a description of a node based on role and contents. */
  public static CharSequence getDescription(AccessibilityNode node, Context context) {
    CharSequence description = "";
    // Describe what type of item it is
    @Role.RoleName int role = node.getRole();
    CharSequence roleString = roleToSummaryString(role, context);
    if (role == Role.ROLE_LIST) {
      roleString = getListDescription(node, context);
    }
    CharSequence contents = node.getNodeText();
    if (TextUtils.isEmpty(roleString)) {
      description = contents;
    } else if (TextUtils.isEmpty(contents)) {
      description = roleString;
    } else {
      description = context.getString(R.string.template_summary_description, roleString, contents);
    }
    return description;
  }

  /**
   * Given a list of nodes, generates a description of them as a row, using getRowItemDescription.
   */
  public static CharSequence getRowDescription(
      ArrayList<AccessibilityNode> nodes, Context context) {
    CharSequence description = context.getString(R.string.value_toolbar);
    return description;
  }
  /**
   * Gets a description for a toolbar item using getSimpleDescription, also keeping in mind that an
   * item might be wrapped by another node.
   */
  public static CharSequence getRowItemDescription(AccessibilityNode node) {
    CharSequence description = "";
    return description;
  }
  /** Generates a simple description of a node, for use in toolbar descriptions */
  public static CharSequence getSimpleDescription(AccessibilityNode node) {
    CharSequence description = "";
    return description;
  }
  /**
   * Returns a description of the grid, containing the number of rows and columns, as well as the
   * type of items in it.
   */
  public static CharSequence getGridDescription(
      ArrayList<ArrayList<AccessibilityNode>> grid, Context context) {
    CharSequence description = context.getString(R.string.value_gridview);
    return description;
  }
  /**
   * Returns a description of a list including its orientation, size, and number of items visible on
   * screen.
   */
  private static CharSequence getListDescription(AccessibilityNode node, Context context) {
    CharSequence description = context.getString(R.string.value_listview);
    return description;
  }

  /** Returns a @Location representing a node's location within a 3x3 grid on screen. */
  public static @Location int getLocation(AccessibilityNode node, Context context) {
    DisplayMetrics metrics = getDisplayMetrics(context);
    int screenHeight = metrics.heightPixels;
    int screenWidth = metrics.widthPixels;
    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    int x = rect.centerX();
    int y = rect.centerY();
    int zoneNum = 0;
    @Location int location = 0;
    for (int row = 0; row < verticalZone.length - 1; row++) {
      for (int col = 0; col < horizontalZone.length - 1; col++) {
        if (x > horizontalZone[col] * screenWidth
            && x < horizontalZone[col + 1] * screenWidth
            && y > verticalZone[row] * screenHeight
            && y < verticalZone[row + 1] * screenHeight) {
          location = zoneNum;
        }
        zoneNum++;
      }
    }
    return location;
  }
  /**
   * Returns a @Location representing a grid's location within a 3x3 grid on screen. We typically
   * only need the vertical location when describing grids or rows, as these normally take up the
   * entire width of the screen, so this method will return LOCATION_TOP, LOCATION_CENTER, or
   * LOCATION_BOTTOM.
   */
  public static @Location int getVerticalLocation(AccessibilityNode node, Context context) {
    DisplayMetrics metrics = getDisplayMetrics(context);
    int screenHeight = metrics.heightPixels;
    @Location int location = 0;
    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    int y = rect.centerY();
    for (int row = 0; row < verticalZone.length - 1; row++) {
      if (y > verticalZone[row] * screenHeight && y < verticalZone[row + 1] * screenHeight) {
        location = row * 3 + 1;
      }
    }
    return location;
  }

  /**
   * Determines if a list of nodes at a vertical location is a row of similar elements based on size
   * and matching node type.
   *
   * <p><b>Note:</b> The nodes are already guaranteed to be accessibility focusable from {@link
   * #groupByLocation}.
   */
  public static boolean isRow(ArrayList<AccessibilityNode> nodes) {
    if (nodes.size() < 3) {
      return false;
    }
    for (int i = 0; i < nodes.size() - 1; i++) {
      AccessibilityNode node1 = nodes.get(i);
      AccessibilityNode node2 = nodes.get(i + 1);
      if (!TextUtils.equals(node1.getClassName(), node2.getClassName())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Given a list of children, return a HashMap of (vertical location, nodes). Obtaining and
   * recycling nodes will be handled in the TreeTraversal class.The integer used in the locations
   * hashMap is android screen coordinates. Will not edit children.
   */
  public static HashMap<Integer, ArrayList<AccessibilityNode>> groupByLocation(
      ArrayList<AccessibilityNode> children) {
    HashMap<Integer, ArrayList<AccessibilityNode>> locationGroups = new HashMap<>();
    int y;
    Rect rect = new Rect();
    ArrayList<AccessibilityNode> entry;

    for (AccessibilityNode child : children) {
      child.getBoundsInScreen(rect);
      y = rect.centerY();

      if (!child.isAccessibilityFocusable()) {
        continue;
      }

      if (locationGroups.containsKey(y)) {
        entry = locationGroups.get(y);
        entry.add(child);
        locationGroups.put(y, entry);
      } else {
        entry = new ArrayList<>();
        entry.add(child);
        locationGroups.put(y, entry);
      }
    }
    return locationGroups;
  }

  /**
   * Returns a HashMap of groups of rows using (className+size, list of rows). Assumes that grids
   * are groups of rows that have the same size and class. Will not edit locations.
   */
  public static HashMap<String, ArrayList<ArrayList<AccessibilityNode>>> getGrids(
      HashMap<Integer, ArrayList<AccessibilityNode>> locations) {

    HashMap<String, ArrayList<ArrayList<AccessibilityNode>>> grids = new HashMap<>();
    ArrayList<ArrayList<AccessibilityNode>> entry;

    // Orders the location keys in ascending order to ensure that the first row processed and added
    // to a grid is the grid’s top-most row on the screen. The node to set focus on will be from
    // this first row.
    ArrayList<Integer> orderedVerticalLocationKeys = new ArrayList<>();
    orderedVerticalLocationKeys.addAll(locations.keySet());
    Collections.sort(orderedVerticalLocationKeys);

    for (Integer key : orderedVerticalLocationKeys) {
      ArrayList<AccessibilityNode> nodes = locations.get(key);
      if (isRow(nodes)) {
        String keyString = Integer.toString(nodes.size()) + nodes.get(0).getClassName();
        if (grids.containsKey(keyString)) {
          entry = grids.get(keyString);
          entry.add(nodes);
          grids.put(keyString, entry);
        } else {
          entry = new ArrayList<>();
          entry.add(nodes);
          grids.put(keyString, entry);
        }
      }
    }
    return grids;
  }

  private static DisplayMetrics getDisplayMetrics(Context context) {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = windowManager.getDefaultDisplay();
    DisplayMetrics metrics = new DisplayMetrics();
    display.getMetrics(metrics);
    return metrics;
  }

  public static String roleToSummaryString(@Role.RoleName int role, Context context) {
    switch (role) {
      case Role.ROLE_BUTTON:
        return context.getString(R.string.value_button);
      case Role.ROLE_CHECK_BOX:
        return context.getString(R.string.value_checkbox);
      case Role.ROLE_DROP_DOWN_LIST:
        return context.getString(R.string.value_spinner);
      case Role.ROLE_EDIT_TEXT:
        return context.getString(R.string.value_edit_box);
      case Role.ROLE_GRID:
        return context.getString(R.string.value_gridview);
      case Role.ROLE_IMAGE:
        return context.getString(R.string.value_image);
      case Role.ROLE_IMAGE_BUTTON:
        return context.getString(R.string.value_button);
      case Role.ROLE_LIST:
        return context.getString(R.string.value_listview);
      case Role.ROLE_RADIO_BUTTON:
        return context.getString(R.string.value_radio_button);
      case Role.ROLE_SEEK_CONTROL:
        return context.getString(R.string.value_seek_bar);
      case Role.ROLE_SWITCH:
        return context.getString(R.string.value_switch);
      case Role.ROLE_TAB_BAR:
        return context.getString(R.string.value_tabwidget);
      case Role.ROLE_TOGGLE_BUTTON:
        return context.getString(R.string.value_button);
      case Role.ROLE_WEB_VIEW:
        return context.getString(R.string.value_webview);
      case Role.ROLE_PAGER:
        return context.getString(R.string.value_pager);
      case Role.ROLE_CHECKED_TEXT_VIEW:
        return context.getString(R.string.value_checkbox);
      case Role.ROLE_PROGRESS_BAR:
        return context.getString(R.string.value_progress_bar);
        // TODO: add in cases for date picker, time picker, scroll view, etc.
      default:
        return "";
    }
    // TODO: Similar to
    // http://google3/java/com/google/android/accessibility/compositor/res/raw/compositor.json. May
    // be able to merge logic.
  }
}
