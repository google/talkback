/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.compositor.roledescription;

import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
import static androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE;
import static com.google.android.accessibility.talkback.compositor.CompositorUtils.PRUNE_EMPTY;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_NAME_ROLE_STATE_POSITION;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_ROLE_NAME_STATE_POSITION;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_STATE_NAME_ROLE_POSITION;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Provides tree nodes description.
 *
 * <p>Note: the tree nodes description, node tree description, would contain the description of the
 * descendant nodes of the event source node.
 *
 * <p>Note: if the source node has no content description, it should append the nodes description of
 * the child nodes.
 *
 * <p>Note: When the scroll item has content changed and the source node is accessibility live
 * region, it should append the nodes description of the child nodes.
 */
public class TreeNodesDescription {

  private static final String TAG = "TreeNodesDescription";

  private final Context context;
  private final ImageContents imageContents;
  private final GlobalVariables globalVariables;
  private final RoleDescriptionExtractor roleDescriptionExtractor;

  public TreeNodesDescription(
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables,
      RoleDescriptionExtractor roleDescriptionExtractor) {
    this.context = context;
    this.imageContents = imageContents;
    this.globalVariables = globalVariables;
    this.roleDescriptionExtractor = roleDescriptionExtractor;
  }

  /**
   * Returns the aggregate node tree description text that has the node tree status and the
   * description information.
   *
   * @param node the node for description
   * @param event the event for description
   */
  public CharSequence aggregateNodeTreeDescription(
      AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
    if (node == null) {
      LogUtils.w(TAG, "aggregateNodeTreeDescription: node is null");
    }
    int descriptionOrder = globalVariables.getDescriptionOrder();
    CharSequence treeDescription = treeDescriptionWithLabel(node, event);
    CharSequence disabledState = AccessibilityNodeFeedbackUtils.getDisabledStateText(node, context);
    CharSequence selectedState = AccessibilityNodeFeedbackUtils.getSelectedStateText(node, context);

    LogUtils.v(
        TAG,
        "aggregateNodeTreeDescription: %s",
        new StringBuilder()
            .append(String.format(" (%s)", node.hashCode()))
            .append(String.format(", treeDescriptionWithLabel={%s}", treeDescription))
            .append(String.format(", selectedState=%s", selectedState))
            .append(String.format(", descriptionOrder=%s", descriptionOrder))
            .toString());

    // Disabled state announcement should always be a postfix.
    switch (descriptionOrder) {
      case DESC_ORDER_NAME_ROLE_STATE_POSITION:
      case DESC_ORDER_ROLE_NAME_STATE_POSITION:
        return CompositorUtils.joinCharSequences(treeDescription, selectedState, disabledState);
      case DESC_ORDER_STATE_NAME_ROLE_POSITION:
        return CompositorUtils.joinCharSequences(selectedState, treeDescription, disabledState);
      default:
        return "";
    }
  }

  /**
   * Returns the node tree description appended label information if it has the label information.
   */
  private CharSequence treeDescriptionWithLabel(
      AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
    boolean shouldAppendChildNode = shouldAppendChildNode(event);
    CharSequence appendedTreeDescription =
        getAppendedTreeDescription(node, event, shouldAppendChildNode);
    CharSequence labelDescription = getDescriptionFromLabelNode(node);

    LogUtils.v(
        TAG,
        "  treeDescriptionWithLabel: %s",
        new StringBuilder()
            .append(String.format(", appendedTreeDescription={%s}", appendedTreeDescription))
            .append(String.format(", labelDescription=%s", labelDescription))
            .append(String.format(", shouldAppendChildNode=%s", shouldAppendChildNode))
            .toString());

    return TextUtils.isEmpty(labelDescription)
        ? appendedTreeDescription
        : context.getString(
            R.string.template_labeled_item, appendedTreeDescription, labelDescription);
  }

  /** Returns {@code true} if it should append the child node tree description by the event. */
  private static boolean shouldAppendChildNode(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat srcNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    boolean sourceIsLiveRegion =
        (srcNode != null) && (srcNode.getLiveRegion() != ACCESSIBILITY_LIVE_REGION_NONE);
    return (event.getEventType() == TYPE_WINDOW_CONTENT_CHANGED && sourceIsLiveRegion);
  }

  /** Returns the node description text from the label node. */
  private CharSequence getDescriptionFromLabelNode(AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat labelNode = node.getLabeledBy();
    if (labelNode == null) {
      return "";
    }
    return AccessibilityNodeFeedbackUtils.getNodeTextOrLabelOrIdDescription(
        labelNode, context, imageContents, globalVariables);
  }

  /**
   * Returns the appended tree description text that contains nodes description and status.
   *
   * <p>Note: the text is composed of tree nodes, tree status description in description order and
   * appends some accessibility information, error text, hint and tooltip.
   */
  private CharSequence getAppendedTreeDescription(
      AccessibilityNodeInfoCompat node, AccessibilityEvent event, boolean shouldAppendChildNode) {
    int descriptionOrder = globalVariables.getDescriptionOrder();
    CharSequence treeDescription;
    switch (descriptionOrder) {
      case DESC_ORDER_NAME_ROLE_STATE_POSITION:
      case DESC_ORDER_ROLE_NAME_STATE_POSITION:
        treeDescription =
            CompositorUtils.conditionalAppend(
                treeNodesDescription(node, event, shouldAppendChildNode),
                nodeStatusDescription(node),
                CompositorUtils.getSeparator());
        break;
      case DESC_ORDER_STATE_NAME_ROLE_POSITION:
        treeDescription =
            CompositorUtils.conditionalPrepend(
                nodeStatusDescription(node),
                treeNodesDescription(node, event, shouldAppendChildNode),
                CompositorUtils.getSeparator());
        break;
      default:
        treeDescription = "";
    }

    CharSequence accessibilityNodeError =
        AccessibilityNodeFeedbackUtils.getAccessibilityNodeErrorText(node, context);
    CharSequence accessibilityNodeHint = AccessibilityNodeFeedbackUtils.getHintDescription(node);
    CharSequence tooltip =
        AccessibilityNodeFeedbackUtils.getUniqueTooltipText(node, context, globalVariables);

    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            String.format("    getAppendedTreeDescription: (%s)  ", node.hashCode()),
            String.format(", treeDescription={%s} ,", treeDescription),
            StringBuilderUtils.optionalText("accessibilityNodeError", accessibilityNodeError),
            StringBuilderUtils.optionalText("accessibilityNodeHint", accessibilityNodeHint),
            StringBuilderUtils.optionalText("tooltip", tooltip)));

    return CompositorUtils.joinCharSequences(
        treeDescription, accessibilityNodeError, accessibilityNodeHint, tooltip);
  }

  /**
   * Returns the node status description text.
   *
   * <p>Note: the status description provides collapsed/expanded, and checked/unchecked state of the
   * node.
   */
  private CharSequence nodeStatusDescription(AccessibilityNodeInfoCompat node) {
    CharSequence collapsedOrExpandedState =
        AccessibilityNodeFeedbackUtils.getCollapsedOrExpandedStateText(node, context);
    Locale preferredLocale = globalVariables.getPreferredLocaleByNode(node);
    boolean stateDescriptionIsEmpty =
        TextUtils.isEmpty(
            AccessibilityNodeFeedbackUtils.getNodeStateDescription(node, context, preferredLocale));
    int role = Role.getRole(node);
    boolean srcIsCheckable = node.isCheckable();
    boolean srcIsChecked = node.isChecked();

    LogUtils.v(
        TAG,
        "      nodeStatusDescription: %s",
        new StringBuilder()
            .append(String.format("role=%s", Role.roleToString(role)))
            .append(String.format(", collapsedOrExpandedState=%s", collapsedOrExpandedState))
            .append(String.format(", stateDescriptionIsEmpty=%s", stateDescriptionIsEmpty))
            .append(String.format(", srcIsCheckable=%b", srcIsCheckable))
            .append(String.format(", srcIsChecked=%b", srcIsChecked))
            .toString());

    // If the node has set stateDescription, node checked state description in tree nodes
    // description text will be redundant for switch and toggle button to be always announced in
    // tree nodes description text.
    if (stateDescriptionIsEmpty
        && srcIsCheckable
        && (role != Role.ROLE_SWITCH
            && role != Role.ROLE_TOGGLE_BUTTON
            && (role != Role.ROLE_CHECKED_TEXT_VIEW || srcIsChecked))) {
      CharSequence checkedState =
          node.isChecked()
              ? context.getString(R.string.value_checked)
              : context.getString(R.string.value_not_checked);
      return CompositorUtils.joinCharSequences(collapsedOrExpandedState, checkedState);
    }
    return collapsedOrExpandedState;
  }

  /**
   * Returns the tree nodes description text.
   *
   * <p>Note: it appends child node tree description if the source node has no content description.
   *
   * <p>Note: For {@link TYPE_WINDOW_CONTENT_CHANGED}, it appends child node tree description if the
   * node role is a top-level scroll item, such as {@link Role.ROLE_LIST}, {@link Role.ROLE_GRID}
   * and {@link Role.ROLE_PAGER}, and the node is accessibility live region.
   */
  private CharSequence treeNodesDescription(
      AccessibilityNodeInfoCompat node, AccessibilityEvent event, boolean shouldAppendChildNode) {
    int role = Role.getRole(node);
    List<CharSequence> joinList = new ArrayList<>();
    // Join the role description text.
    joinList.add(roleDescriptionExtractor.nodeRoleDescriptionText(node, event));

    boolean isContentDescriptionEmpty =
        TextUtils.isEmpty(
            AccessibilityNodeFeedbackUtils.getNodeContentDescription(
                node, context, globalVariables.getPreferredLocaleByNode(node)));
    StringBuilder logString = new StringBuilder();
    logString
        .append(String.format(" (%s)", node.hashCode()))
        .append(String.format(", role=%s", Role.roleToString(role)))
        .append(String.format(", isContentDescriptionEmpty=%b", isContentDescriptionEmpty))
        .append(String.format(", shouldAppendChildNode=%b", shouldAppendChildNode));

    if (role != Role.ROLE_WEB_VIEW
        && (role == Role.ROLE_GRID
            || role == Role.ROLE_LIST
            || role == Role.ROLE_PAGER
            || isContentDescriptionEmpty)) {
      // Append the node description if needed. It recurse on all visible & un-focusable children,
      // ascending.
      ReorderedChildrenIterator childIterator =
          ReorderedChildrenIterator.createAscendingIterator(node);
      if (!childIterator.hasNext()) {
        logString.append(", hasNoNextChildNode");
      }
      while (childIterator.hasNext()) {
        AccessibilityNodeInfoCompat childNode = childIterator.next();
        if (childNode == null) {
          logString.append(
              String.format("error: sourceNode (%s) has a null child.", node.hashCode()));
        } else {
          boolean isVisible = AccessibilityNodeInfoUtils.isVisible(childNode);
          boolean isAccessibilityFocusable =
              AccessibilityNodeInfoUtils.isAccessibilityFocusable(childNode);
          logString
              .append(String.format("\n        childNode:(%s)", childNode.hashCode()))
              .append(String.format(", isVisible=%b", isVisible))
              .append(String.format(", isAccessibilityFocusable=%b", isAccessibilityFocusable));

          if (isVisible && (!isAccessibilityFocusable || shouldAppendChildNode)) {
            // Join the tree description of child node.
            CharSequence description =
                getAppendedTreeDescription(childNode, event, shouldAppendChildNode);
            logString.append(
                String.format("\n        > appendChildNodeDescription= {%s}", description));
            joinList.add(description);
          }
        }
      }
    }

    LogUtils.v(TAG, "      treeNodesDescription:  %s", logString.toString());

    return CompositorUtils.joinCharSequences(joinList, CompositorUtils.getSeparator(), PRUNE_EMPTY);
  }
}
