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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.treenodes.ClearOverlayNode;
import com.google.android.accessibility.switchaccess.treenodes.ShowGlobalMenuNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanLeafNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.libraries.accessibility.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utility class for generating human-understandable feedback. */
public class FeedbackUtils {
  /**
   * Get the formatted speakable text for a given tree. The tree provided should consist the
   * currently focused TreeScanSelection node and all of its descendants. This will usually be a
   * subtree of the tree that contains all actionable items on the screen.
   *
   * @param context The context from which strings can be retrieved
   * @param root The root of the tree
   * @param isGroupScanning {@code true} if group selection is enabled.
   * @param speakFirstAndLastItem Whether the first and last item should be included in the
   *     speakable text. Only used if more than one node is present.
   * @param speakNumberOfItems Whether the number of items should be included in the speakable text.
   *     Only used if more than one node is present.
   * @param speakAllItems Whether to include all items in speakable text. Only used if more than one
   *     node is present.
   * @param speakHints Whether to include usage hints for the items in the spoken feedback.
   */
  public static List<CharSequence> getSpeakableTextForTree(
      Context context,
      TreeScanSelectionNode root,
      boolean isGroupScanning,
      boolean speakFirstAndLastItem,
      boolean speakNumberOfItems,
      boolean speakAllItems,
      boolean speakHints) {
    List<CharSequence> speakableTextList = new ArrayList<>();
    if (!isGroupScanning) {
      // We only have one group of nodes highlighted.
      List<TreeScanLeafNode> nodes = root.getChild(0).getNodesList();
      List<CharSequence> rawTextList = new ArrayList<>();
      for (TreeScanLeafNode node : nodes) {
        rawTextList.addAll(node.getSpeakableText());
      }

      speakableTextList =
          getSpeakableTextListForRawTextList(
              context, rawTextList, speakFirstAndLastItem, speakNumberOfItems, speakAllItems);

      if (speakHints && nodes.size() > 1) {
        speakableTextList.add(
            context.getString(R.string.switch_access_spoken_feedback_row_column_selection_hint));
      }
    } else {
      // We need to process each highlighted group of nodes.
      for (int i = 0; i < root.getChildCount(); i++) {
        // Get the raw speakable text.
        List<TreeScanLeafNode> nodesSublist = root.getChild(i).getNodesList();
        int nodesSublistSize = nodesSublist.size();
        List<CharSequence> childRawTextList = new ArrayList<>();
        for (int j = 0; j < nodesSublistSize; j++) {
          TreeScanLeafNode node = nodesSublist.get(j);
          // Remove all ShowGlobalMenuNodes and all ClearOverlayNodes except the last one.
          // (Otherwise, these nodes can be spoken several times per highlighted group as
          // every selection path in group selection has one of these nodes to prevent
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

          List<CharSequence> speakableTextListForRawTextList =
              getSpeakableTextListForRawTextList(
                  context,
                  childRawTextList,
                  speakFirstAndLastItem,
                  speakNumberOfItems,
                  speakAllItems);

          speakableTextList.addAll(speakableTextListForRawTextList);

          if (speakHints) {
            speakableTextList.add(
                context.getString(
                    (speakableTextListForRawTextList.size() == 1
                        ? R.string.switch_access_spoken_feedback_group_selection_single_item_hint
                        : R.string.switch_access_spoken_feedback_group_selection_hint),
                    Integer.toString(i + 1)));
          }
        }
      }
    }
    return speakableTextList;
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

    List<CharSequence> speakableTextList = new ArrayList<>();

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
   * Gets the speakable text for a given actionable node.
   *
   * @param context the context from which strings can be retrieved
   * @param node the actionable node for which speakable text should be retrieved
   * @return the human-understandable text that should be spoken for the node provided
   */
  public static CharSequence getSpeakableTextForActionableNode(
      Context context, SwitchAccessNodeCompat node) {
    CharSequence speakableText = null;
    if (node.isScrollable()) {
      speakableText = context.getString(R.string.switch_access_spoken_feedback_scroll);
    }

    if (speakableText == null) {
      node.refresh();
      speakableText = node.getNodeText();
    }

    if (StringUtils.isEmpty(speakableText)) {
      speakableText = context.getString(R.string.switch_access_spoken_feedback_unknown);
    }

    return speakableText;
  }

  /**
   * Gets the speakable text for a given non-actionable node.
   *
   * @param node the non-actionable node for which speakable text should be retrieved
   * @return the human-understandable text that should be spoken for the node provided
   */
  public static CharSequence getSpeakableTextForNonActionableNode(SwitchAccessNodeCompat node) {
    node.refresh();
    return node.getNodeText();
  }

  /**
   * Gets the developer-provided text inside a node that will be used to generate spoken feedback.
   * If the node does not have any text, we attempt to get node text of any non-focusable children.
   * If there are no focusable children with text, an empty string will be returned.
   *
   * <p>Note: This method should never be called with nodes returned from
   * AccessibilityNodeInfo#obtain. These nodes do not retain children information, so this method
   * may return the incorrect text. Instead, use SwitchAccessNodeCompat#getNodeText.
   *
   * @param nodeCompat the {@link AccessibilityNodeInfoCompat} of the node from which the
   *     developer-provided text should be retrieved
   * @return the developer-provided text (content description or text) of the given node. If there
   *     is neither content description nor text inside the node or its children, then return an
   *     empty string
   */
  public static String getNodeText(AccessibilityNodeInfoCompat nodeCompat) {
    CharSequence speakableText = nodeCompat.getContentDescription();

    if (StringUtils.isEmpty(speakableText)) {
      speakableText = nodeCompat.getText();
    }

    // If speakable text is empty, see if there are any non-focusable children nodes. If so, use
    // their text for the speakable text of this node. We filter out any focusable children nodes
    // to prevent duplicated speakable text from both a parent and child node.
    if (StringUtils.isEmpty(speakableText)) {
      StringBuilder builder = new StringBuilder();

      int numChildren = nodeCompat.getChildCount();
      for (int i = 0; i < numChildren; i++) {
        AccessibilityNodeInfoCompat child = nodeCompat.getChild(i);
        if ((child != null)
            && AccessibilityNodeInfoUtils.hasMinimumPixelsVisibleOnScreen(child)
            && !AccessibilityNodeInfoUtils.shouldFocusNode(child)) {
          CharSequence childText = getNodeText(child);

          if (!StringUtils.isEmpty(childText)) {
            if (builder.length() != 0) {
              builder.append(" ");
            }
            builder.append(childText);
          }
        }

        if (child != null) {
          child.recycle();
        }
      }

      speakableText = builder.toString();
    }
    if (StringUtils.isEmpty(speakableText)) {
      speakableText = "";
    }

    return speakableText.toString();
  }
}
