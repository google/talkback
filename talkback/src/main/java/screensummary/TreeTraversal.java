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
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * An outline of the methods used for visiting all of the {@link AccessibilityNode} objects on
 * screen and obtaining {@link NodeData} objects for summarization. This class will use methods from
 * {@link NodeCharacteristics} to get more information about the nodes.
 */
public class TreeTraversal {
  /** The number of zones, based on the fact that the screen is a 3x3 grid. */
  public static final int ZONE_COUNT = 9;

  /**
   * Returns an arraylist where each index corresponds to a zone and contains a list of {@link
   * NodeData} found in that zone.
   */
  public static ArrayList<ArrayList<NodeData>> checkRootNode(
      AccessibilityWindowInfo window, Context context) {
    ArrayList<ArrayList<NodeData>> nodeDataList = new ArrayList<ArrayList<NodeData>>();

    for (int i = 0; i < ZONE_COUNT; i++) {
      nodeDataList.add(new ArrayList<NodeData>());
    }
    AccessibilityNode rootNode =
        AccessibilityNode.obtainCopy(AccessibilityWindowInfoUtils.getRoot(window));
    manageNodes(rootNode, context, nodeDataList);
    rootNode.recycle("TreeTraversal.checkRootNode");
    return nodeDataList;
  }

  /**
   * Initiates DFS on the root node to begin extracting {@link NodeData} objects.
   *
   * <p>Nodes used to traverse the tree will be recycled, but copies will be made for nodeDataList
   * to be recycled later within SummaryOutput. rootNode will be recycled by its caller
   * checkRootNode.
   */
  private static void manageNodes(
      AccessibilityNode rootNode, Context context, ArrayList<ArrayList<NodeData>> nodeDataList) {

    if (rootNode == null) {
      return;
    }

    HashSet<AccessibilityNode> seen = new HashSet<>();
    extractNodeData(rootNode.obtainCopy(), seen, context, nodeDataList);
    AccessibilityNode.recycle("TreeTraversal.manageNodes", seen);
  }
  /**
   * DFS through node tree. Will not recycle nodes, but will add nodes to the seen hashSet so that
   * they can be recycled by the calling function.
   */
  private static void extractNodeData(
      AccessibilityNode node,
      HashSet<AccessibilityNode> seen,
      Context context,
      ArrayList<ArrayList<NodeData>> nodeDataList) {
    CharSequence description;
    @NodeCharacteristics.Location int location;
    // Add nodes to seen, return if already seen.
    if (!seen.add(node)) {
      return;
    }
    // Add a description of this node to nodeDataList.
    inspectNode(node, context, nodeDataList);

    // Do not visit children if the node is a list, a web view, a tab bar, or a grid.
    int childCount = node.getChildCount();
    int role = node.getRole();

    if (role == Role.ROLE_LIST
        || role == Role.ROLE_WEB_VIEW
        || role == Role.ROLE_TAB_BAR
        || role == Role.ROLE_GRID) {
      childCount = 0;
    }

    // Make a list of this node's children
    ArrayList<AccessibilityNode> children = new ArrayList<AccessibilityNode>();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNode child = node.getChild(i);
      children.add(child);
    }
    // Group nodes by vertical location to see if they form toolbars, group toolbars by class name
    // and size to see if they form grids.
    HashMap<Integer, ArrayList<AccessibilityNode>> locationGroups =
        NodeCharacteristics.groupByLocation(children);
    HashMap<String, ArrayList<ArrayList<AccessibilityNode>>> grids =
        NodeCharacteristics.getGrids(locationGroups);

    // Get a description based on if they are grids or toolbars, add this to nodeDataList
    for (Map.Entry<String, ArrayList<ArrayList<AccessibilityNode>>> entry : grids.entrySet()) {
      ArrayList<ArrayList<AccessibilityNode>> grid = entry.getValue();
      if (grid.size() > 1) {
        description = NodeCharacteristics.getGridDescription(grid, context);
      } else {
        description = NodeCharacteristics.getRowDescription(grid.get(0), context);
      }
      // The first element in the first row will typically be the top left element, which is where
      // we want to focus if the user selects this item.
      AccessibilityNode topLeft = grid.get(0).get(0);
      location = NodeCharacteristics.getVerticalLocation(topLeft, context);
      ArrayList<NodeData> nodeDataSubList = nodeDataList.get(location);

      // Nodes that are obtain()ed will be recycled in the SummaryActivity class after they are used
      // for output.
      nodeDataSubList.add(new NodeData(description, topLeft.obtainCopy()));
    }
    // Remove the nodes from the toolbars and grids from the children list
    removeGroup(locationGroups, children);

    for (AccessibilityNode child : children) {
      if (child == null) {
        continue;
      }
      extractNodeData(child, seen, context, nodeDataList);
    }
  }

  /**
   * Modifies {@code children} by removing nodes that are part of toolbars or grids. Will recycle
   * nodes that are being removed.
   */
  private static void removeGroup(
      HashMap<Integer, ArrayList<AccessibilityNode>> locationGroups,
      ArrayList<AccessibilityNode> children) {

    for (Map.Entry<Integer, ArrayList<AccessibilityNode>> row : locationGroups.entrySet()) {
      ArrayList<AccessibilityNode> nodes = row.getValue();
      if (NodeCharacteristics.isRow(nodes)) {
        // remove nodes
        for (AccessibilityNode node : nodes) {
          children.remove(node);
          node.recycle("TreeTraversal.removeGroup");
        }
      }
    }
  }

  /**
   * Gets the location and description of a node and adds this to {@code nodeDataList}. Will not
   * edit the node itself.
   */
  private static void inspectNode(
      AccessibilityNode node, Context context, ArrayList<ArrayList<NodeData>> nodeDataList) {
    if (!node.isVisibleToUser()) {
      return;
    }

    @NodeCharacteristics.Location int location = NodeCharacteristics.getLocation(node, context);
    CharSequence description = NodeCharacteristics.getDescription(node, context);
    ArrayList<NodeData> nodeDataSubList = nodeDataList.get(location);
    // Nodes that are obtain()ed will be recycled in the SummaryActivity class after they are used
    // for output.

    if (TextUtils.isEmpty(description)) {
      return;
    }
    if (node.getRole() == Role.ROLE_LIST) {
      if (node.getChildCount() == 0) {
        return;
      }
      AccessibilityNode child = node.getChild(0);
      // Child will be recycled after output in SummaryOutput
      nodeDataSubList.add(new NodeData(description, child));
    } else {
      // Node will get recycled in manageNodes so it must be obtained here
      nodeDataSubList.add(new NodeData(description, node.obtainCopy()));
    }
  }

  /**
   * Class containing an {@link AccessibilityNode} reference and a text description. Will be used to
   * send information to {@link SummaryOutput}.
   */
  public static class NodeData {
    private CharSequence description;
    private AccessibilityNode node;

    public NodeData(CharSequence startDescription, AccessibilityNode startNode) {
      description = startDescription;
      node = startNode;
    }

    public CharSequence getDescription() {
      return description;
    }

    public AccessibilityNode getNode() {
      return node;
    }
  }
}
