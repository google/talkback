/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.switchaccess.ClearOverlayNode;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.ShowGlobalMenuNode;
import com.google.android.accessibility.switchaccess.TreeScanLeafNode;
import com.google.android.accessibility.switchaccess.TreeScanSelectionNode;
import com.google.android.libraries.accessibility.utils.StringUtils;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/** Utility class for generating human-understandable feedback. */
public class FeedbackUtils {

  // Placed between elements of a list when collapsing the list into a single sequence.
  private static final char COLLAPSED_LIST_DELIMITER = ' ';

  /**
   * Get the formatted speakable text for a given tree. The tree provided should consist the
   * currently focused TreeScanSelection node and all of its descendents. This will usually be a
   * subtree of the tree that contains all actionable items on the screen.
   *
   * @param context The context from which strings can be retrieved
   * @param root The root of the tree
   * @param isGroupScanning {@code true} if group selection or nomon clocks are enabled.
   * @param speakFirstAndLastItem Whether the first and last item should be included in the
   *     speakable text. Only used if more than one node is present.
   * @param speakNumberOfItems Whether the number of items should be included in the speakable text.
   *     Only used if more than one node is present.
   * @param speakAllItems Whether to include all items in speakable text. Only used if more than one
   *     node is present.
   */
  public static CharSequence getSpeakableTextForTree(
      Context context,
      TreeScanSelectionNode root,
      boolean isGroupScanning,
      boolean speakFirstAndLastItem,
      boolean speakNumberOfItems,
      boolean speakAllItems) {
    List<CharSequence> speakableTextList = new LinkedList<>();
    if (!isGroupScanning) {
      // We only have one group of nodes highlighted.
      List<TreeScanLeafNode> nodes = root.getChild(0).getNodesList();
      List<CharSequence> rawTextList = new LinkedList<>();
      for (TreeScanLeafNode node : nodes) {
        rawTextList.addAll(node.getSpeakableText());
      }

      speakableTextList =
          getSpeakableTextListForRawTextList(
              context, rawTextList, speakFirstAndLastItem, speakNumberOfItems, speakAllItems);
    } else {
      // We need to process each highlighted group of nodes.
      for (int i = 0; i < root.getChildCount(); i++) {
        // Get the raw speakable text.
        List<TreeScanLeafNode> nodesSublist = root.getChild(i).getNodesList();
        int nodesSublistSize = nodesSublist.size();
        List<CharSequence> childRawTextList = new LinkedList<>();
        for (int j = 0; j < nodesSublistSize; j++) {
          TreeScanLeafNode node = nodesSublist.get(j);
          // Remove all ShowGlobalMenuNodes and all ClearOverlayNodes except the last one.
          // (Otherwise, these nodes can be spoken several times per highlighted group as
          // every selection path in option scanning has one of these nodes to prevent
          // the user from getting stuck if they make a mistake during selection.)
          if ((!(node instanceof ShowGlobalMenuNode) && !(node instanceof ClearOverlayNode))
              || (j == nodesSublistSize - 2)
              || (j == nodesSublistSize - 1)) {
            childRawTextList.addAll(node.getSpeakableText());
          }
        }

        // Format the text based on the provided parameters.
        if (!childRawTextList.isEmpty()) {
          speakableTextList.add(
              context.getString(
                  R.string.switch_access_spoken_feedback_group, Integer.toString(i + 1)));
          speakableTextList.add(
              collapseList(
                  getSpeakableTextListForRawTextList(
                      context,
                      childRawTextList,
                      speakFirstAndLastItem,
                      speakNumberOfItems,
                      speakAllItems)));
        }
      }
    }
    return collapseList(speakableTextList);
  }

  /**
   * Get the formatted, speakable text for a raw list of node text.
   *
   * @param context The context from which strings can be retrieved
   * @param speakFirstAndLastItem Whether the first and last item should be included in the
   *     speakable text. Only used if more than one node is present.
   * @param speakNumberOfItems Whether the number of items should be included in the speakable text.
   *     Only used if more than one node is present.
   * @param speakAllItems Whether to include all items in speakable text. Only used if more than one
   *     node is present.
   */
  private static List<CharSequence> getSpeakableTextListForRawTextList(
      Context context,
      List<CharSequence> rawText,
      boolean speakFirstAndLastItem,
      boolean speakNumberOfItems,
      boolean speakAllItems) {
    if (rawText.isEmpty()) {
      return Collections.emptyList();
    } else if (rawText.size() == 1) {
      return rawText;
    }

    List<CharSequence> speakableTextList = new LinkedList<>();

    // Determine what we should speak based on user preferences.
    if (speakFirstAndLastItem) {
      speakableTextList.add(
          context.getString(
              R.string.switch_access_spoken_feedback_to,
              rawText.get(0),
              rawText.get(rawText.size() - 1)));
    }
    if (speakNumberOfItems) {
      speakableTextList.add(
          context.getString(
              R.string.switch_access_spoken_feedback_items, Integer.toString(rawText.size())));
    }
    if (speakAllItems) {
      speakableTextList.addAll(rawText);
    }

    return speakableTextList;
  }

  /**
   * Collapse a list of speakable text.
   *
   * @param list The list of speakable text
   * @return The collapsed list, with spaces placed between each item in the original list
   */
  private static CharSequence collapseList(List<CharSequence> list) {
    if (list.isEmpty()) {
      return "";
    }

    StringBuilder builder = new StringBuilder(list.get(0));
    for (int i = 1; i < list.size(); i++) {
      builder.append(COLLAPSED_LIST_DELIMITER);
      builder.append(list.get(i));
    }
    return builder.toString();
  }

  /**
   * Get the speakable text for a given node.
   *
   * @param context The context from which strings can be retrieved
   * @param node The node for which speakable text should be retrieved
   * @return The human-understandable text that should be spoken for the node provided
   */
  public static CharSequence getSpeakableTextForNode(
      Context context, AccessibilityNodeInfoCompat node) {
    CharSequence speakableText = null;
    if (node.isScrollable()) {
      speakableText = context.getString(R.string.switch_access_spoken_feedback_scroll);
    }

    if (speakableText == null) {
      speakableText = getNodeText(node);
    }

    int numChildren = node.getChildCount();
    // TODO: Voice text from all children if a node has no text.
    for (int i = 0; (speakableText == null && i < numChildren); i++) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child != null) {
        speakableText = getSpeakableTextForNode(context, child);
      }
    }

    if (speakableText == null) {
      speakableText = context.getString(R.string.switch_access_spoken_feedback_unknown);
    }

    return speakableText;
  }

  /**
   * Gets the developer-provided text inside a node that will be used to generate spoken feedback.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfoCompat} of the node from which the
   *     developer-provided text should be retrieved.
   * @return The developer-provided text (content description or text) of the given node. If there
   *     is neither content description nor text inside the node, then return {@code null}.
   */
  public static String getNodeText(@NonNull AccessibilityNodeInfoCompat nodeCompat) {
    CharSequence speakableText = nodeCompat.getContentDescription();

    if (StringUtils.isEmpty(speakableText)) {
      speakableText = nodeCompat.getText();
    }

    return (speakableText == null) ? null : speakableText.toString().trim();
  }
}
