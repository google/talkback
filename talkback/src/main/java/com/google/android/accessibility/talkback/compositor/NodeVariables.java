/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.compositor;

import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.ENUM_LIVE_REGION;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.ENUM_RANGE_INFO_TYPE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.ENUM_ROLE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.ENUM_WINDOW_TYPE;
import static com.google.android.accessibility.talkback.compositor.ParseTreeCreator.RANGE_INFO_UNDEFINED;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree.VariableDelegate;
import com.google.android.accessibility.talkback.compositor.roledescription.EditTextDescription;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A VariableDelegate that maps data from AccessibilityEvent and AccessibilityNodeInfoCompat */
class NodeVariables implements ParseTree.VariableDelegate {

  private static final String TAG = "NodeVariables";

  // IDs of variables.
  private static final int NODE_ROLE = 7000;
  private static final int NODE_TEXT = 7001;
  private static final int NODE_CONTENT_DESCRIPTION = 7002;
  private static final int NODE_CHILDREN = 7003;
  private static final int NODE_CHILDREN_ASCENDING = 7004;
  private static final int NODE_ROLE_DESCRIPTION = 7005;
  private static final int NODE_CHECKABLE = 7006;
  private static final int NODE_CHECKED = 7007;
  private static final int NODE_IS_VISIBLE = 7008;
  private static final int NODE_IS_ACCESSIBILITY_FOCUSABLE = 7009;
  private static final int NODE_IS_FOCUSED = 7010;
  private static final int NODE_IS_ACCESSIBILITY_FOCUSED = 7011;
  private static final int NODE_LIVE_REGION = 7012;
  private static final int NODE_IS_PASSWORD = 7013;
  private static final int NODE_WINDOW_ID = 7014;
  private static final int NODE_WINDOW_TYPE = 7015;
  private static final int NODE_SUPPORTS_ACTION_SET_SELECTION = 7016;
  private static final int NODE_SUPPORTS_ACTION_SELECT = 7017;
  private static final int NODE_LABEL_TEXT = 7018;
  private static final int NODE_LABELED_BY = 7019;
  private static final int NODE_IS_ACTIONABLE = 7020;
  private static final int NODE_IS_ENABLED = 7021;
  private static final int NODE_IS_SELECTED = 7022;
  private static final int NODE_IS_EXPANDABLE = 7023;
  private static final int NODE_IS_COLLAPSIBLE = 7024;
  private static final int NODE_PARENT = 7025;
  private static final int NODE_VISIBLE_CHILD_COUNT = 7026;
  private static final int NODE_SUPPORTS_ACTION_SCROLL_FORWARD = 7027;
  private static final int NODE_SUPPORTS_ACTION_SCROLL_BACKWARD = 7028;
  private static final int NODE_ACTIONS = 7029;
  private static final int NODE_IS_CLICKABLE = 7030;
  private static final int NODE_IS_LONG_CLICKABLE = 7031;
  private static final int NODE_ACTION_CLICK = 7032;
  private static final int NODE_ACTION_LONG_CLICK = 7033;
  private static final int NODE_IS_PIN_KEY = 7034;
  // Used to show accessibility hints for web and edit texts
  private static final int NODE_HINT_DESCRIPTION = 7035;
  private static final int NODE_IS_SHOWING_HINT = 7036;
  private static final int NODE_VIEW_ID_TEXT = 7038;
  private static final int NODE_SELECTED_PAGE_TITLE = 7039;
  private static final int NODE_IS_HEADING = 7040;
  // TODO: May need an array variable for visible children.

  private static final int NODE_RANGE_INFO_TYPE = 7041;
  private static final int NODE_RANGE_CURRENT_VALUE = 7042;

  private static final int NODE_UNIQUE_TOOLTIP_TEXT = 7043;

  private static final int NODE_IS_CONTENT_INVALID = 7045;
  private static final int NODE_ERROR_TEXT = 7046;

  private static final int NODE_PROGRESS_PERCENT = 7047;

  private static final int NODE_HINT_FOR_NODE_ACTIONS_HIGH_VERBOSITY = 7050;
  private static final int NODE_IS_WEB_CONTAINER = 7051;
  private static final int NODE_NEEDS_LABEL = 7052;
  private static final int NODE_STATE_DESCRIPTION = 7053;
  private static final int NODE_IS_WITHIN_ACCESSIBILITY_FOCUS = 7054;
  private static final int NODE_CAPTION_TEXT = 7056;
  private static final int NODE_ALLOW_WINDOW_CONTENT_CHANGE_ANNOUNCEMENT = 7057;

  private static final int NODE_WINDOW_IS_IME = 7059;
  private static final int NODE_ROLE_NAME = 7060;
  private static final int NODE_EDIT_TEXT_STATE = 7061;
  private static final int NODE_EDIT_TEXT_TEXT = 7062;
  private static final int NODE_PAGER_PAGE_ROLE_DESCRIPTION = 7063;
  private static final int NODE_HAS_SPELLING_SUGGESTIONS = 7064;
  private static final int NODE_TYPO_COUNT = 7065;
  private static final int NODE_NOTIFY_DISABLED = 7066;
  private static final int NODE_NOTIFY_SELECTED = 7067;
  private static final int NODE_NOTIFY_COLLAPSED_OR_EXPANDED = 7068;
  private static final int NODE_TEXT_OR_LABEL = 7069;
  private static final int NODE_TEXT_OR_LABEL_OR_ID = 7070;
  private static final int NODE_UNLABELLED_DESCRIPTION = 7071;
  private static final int NODE_ENABLED_STATE = 7072;

  private final Context mContext;
  private final @Nullable ImageContents imageContents;
  private final ParseTree.VariableDelegate mParentVariables;
  private final AccessibilityNodeInfoCompat mNode;
  @Role.RoleName private final int mRole; // Role of mNode.
  private boolean mIsRoot; // Is mNode the root/source node for node tree recursion.
  private Set<AccessibilityNodeInfoCompat> mVisitedNodes;
  private ArrayList<AccessibilityNodeInfoCompat> mChildNodes;
  private ArrayList<AccessibilityNodeInfoCompat> mChildNodesAscending;
  private AccessibilityNodeInfoCompat mLabelNode;
  private AccessibilityNodeInfoCompat mParentNode;
  // Stores the user preferred locale changed using language switcher.
  private final @Nullable Locale mUserPreferredLocale;
  private final GlobalVariables globalVariables;

  /**
   * Constructs a NodeVariables, which contains context variables to help generate feedback for an
   * accessibility event.
   *
   * @param node The view node for which we are generating feedback.
   */
  public NodeVariables(
      Context context,
      @Nullable ImageContents imageContents,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      @Nullable GlobalVariables globalVariables) {
    mContext = context;
    this.imageContents = imageContents;
    mParentVariables = parent;
    mNode = node;
    mRole = Role.getRole(node);
    mIsRoot = true;
    this.globalVariables = globalVariables;
    mUserPreferredLocale = globalVariables.getUserPreferredLocale();
  }

  private static NodeVariables constructForReferredNode(
      Context context,
      @Nullable ImageContents imageContents,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      GlobalVariables globalVariables) {
    NodeVariables instance =
        new NodeVariables(context, imageContents, parent, node, globalVariables);
    instance.mIsRoot = true; // This is a new root for node tree recursion.
    return instance;
  }

  private static NodeVariables constructForChildNode(
      Context context,
      @Nullable ImageContents imageContents,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      Set<AccessibilityNodeInfoCompat> visitedNodes,
      GlobalVariables globalVariables) {
    NodeVariables instance =
        new NodeVariables(context, imageContents, parent, node, globalVariables);
    instance.mIsRoot = false;
    instance.setVisitedNodes(visitedNodes);
    return instance;
  }

  private void setVisitedNodes(Set<AccessibilityNodeInfoCompat> visitedNodes) {
    mVisitedNodes = visitedNodes;
    if (mVisitedNodes != null && !mVisitedNodes.contains(mNode)) {
      mVisitedNodes.add(mNode);
    }
  }

  @Override
  public boolean getBoolean(int variableId) {
    switch (variableId) {
      case NODE_CHECKABLE:
        return mNode.isCheckable();
      case NODE_ALLOW_WINDOW_CONTENT_CHANGE_ANNOUNCEMENT:
        return WindowContentChangeAnnouncementFilter.shouldAnnounce(
            mNode, globalVariables.getTextChangeRateUnlimited());
      case NODE_CHECKED:
        return mNode.isChecked();
      case NODE_IS_VISIBLE:
        return AccessibilityNodeInfoUtils.isVisible(mNode);
      case NODE_IS_ACCESSIBILITY_FOCUSABLE:
        return AccessibilityNodeInfoUtils.isAccessibilityFocusable(mNode);
      case NODE_IS_FOCUSED:
        return mNode.isFocused();
      case NODE_IS_SHOWING_HINT:
        return mNode.isShowingHintText();
      case NODE_IS_ACCESSIBILITY_FOCUSED:
        return mNode.isAccessibilityFocused();
      case NODE_SUPPORTS_ACTION_SET_SELECTION:
        return AccessibilityNodeInfoUtils.supportsAction(
            mNode, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION);
      case NODE_IS_PASSWORD:
        return mNode.isPassword();
      case NODE_SUPPORTS_ACTION_SELECT:
        return AccessibilityNodeInfoUtils.supportsAction(
            mNode, AccessibilityNodeInfoCompat.ACTION_SELECT);
      case NODE_IS_ACTIONABLE:
        return AccessibilityNodeInfoUtils.isActionableForAccessibility(mNode);
      case NODE_IS_ENABLED:
        return mNode.isEnabled();
      case NODE_IS_SELECTED:
        return mNode.isSelected();
      case NODE_IS_EXPANDABLE:
        return AccessibilityNodeInfoUtils.isExpandable(mNode);
      case NODE_IS_COLLAPSIBLE:
        return AccessibilityNodeInfoUtils.isCollapsible(mNode);
      case NODE_SUPPORTS_ACTION_SCROLL_BACKWARD:
        return AccessibilityNodeInfoUtils.supportsAction(
            mNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
      case NODE_SUPPORTS_ACTION_SCROLL_FORWARD:
        return AccessibilityNodeInfoUtils.supportsAction(
            mNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
      case NODE_IS_CLICKABLE:
        return AccessibilityNodeInfoUtils.isClickable(mNode);
      case NODE_IS_LONG_CLICKABLE:
        return AccessibilityNodeInfoUtils.isLongClickable(mNode);
      case NODE_IS_PIN_KEY:
        return AccessibilityNodeInfoUtils.isPinKey(mNode);
      case NODE_IS_HEADING:
        return AccessibilityNodeInfoUtils.isHeading(mNode);
      case NODE_IS_CONTENT_INVALID:
        return mNode.isContentInvalid();
      case NODE_IS_WEB_CONTAINER:
        return WebInterfaceUtils.isWebContainer(mNode);
      case NODE_NEEDS_LABEL:
        return imageContents != null && imageContents.needsLabel(mNode);
      case NODE_IS_WITHIN_ACCESSIBILITY_FOCUS:
        {
          return AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(mNode);
        }
      case NODE_WINDOW_IS_IME:
        return AccessibilityNodeInfoUtils.isKeyboard(mNode);
      case NODE_HAS_SPELLING_SUGGESTIONS:
        return !AccessibilityNodeInfoUtils.getSpellingSuggestions(mNode).isEmpty();
      default:
        return mParentVariables.getBoolean(variableId);
    }
  }

  @Override
  public int getInteger(int variableId) {
    switch (variableId) {
      case NODE_WINDOW_ID:
        return mNode.getWindowId();
      case NODE_VISIBLE_CHILD_COUNT:
        return AccessibilityNodeInfoUtils.countVisibleChildren(mNode);
      case NODE_TYPO_COUNT:
        return AccessibilityNodeInfoUtils.getTypoCount(mNode);
      default:
        return mParentVariables.getInteger(variableId);
    }
  }

  @Override
  public double getNumber(int variableId) {
    switch (variableId) {
      case NODE_RANGE_CURRENT_VALUE:
        {
          RangeInfoCompat rangeInfo = mNode.getRangeInfo();
          if (rangeInfo == null) {
            return 0;
          }

          return Math.round(rangeInfo.getCurrent() * 100.0) / 100.0;
        }
      case NODE_PROGRESS_PERCENT:
        return AccessibilityNodeInfoUtils.getProgressPercent(mNode);
      default:
        return mParentVariables.getNumber(variableId);
    }
  }

  @Override
  public @Nullable CharSequence getString(int variableId) {
    CharSequence result;
    switch (variableId) {
      case NODE_TEXT:
        return AccessibilityNodeFeedbackUtils.getNodeText(
            mNode,
            mContext,
            (mUserPreferredLocale != null)
                ? mUserPreferredLocale
                : AccessibilityNodeInfoUtils.getLocalesByNode(mNode));
      case NODE_HINT_DESCRIPTION:
        return AccessibilityNodeFeedbackUtils.getHintDescription(mNode);
      case NODE_ERROR_TEXT:
        return AccessibilityNodeFeedbackUtils.getAccessibilityNodeErrorText(mNode, mContext);
      case NODE_UNIQUE_TOOLTIP_TEXT:
        return AccessibilityNodeFeedbackUtils.getUniqueTooltipText(
            mNode, mContext, globalVariables);
      case NODE_CONTENT_DESCRIPTION:
        return AccessibilityNodeFeedbackUtils.getNodeContentDescription(
            mNode,
            mContext,
            (mUserPreferredLocale != null)
                ? mUserPreferredLocale
                : AccessibilityNodeInfoUtils.getLocalesByNode(mNode));
      case NODE_ROLE_DESCRIPTION:
        return AccessibilityNodeFeedbackUtils.getNodeRoleDescription(
            mNode, mContext, globalVariables);
      case NODE_ROLE_NAME:
        return AccessibilityNodeFeedbackUtils.getNodeRoleName(mNode, mContext);
      case NODE_LABEL_TEXT:
        return AccessibilityNodeFeedbackUtils.getNodeLabelText(mNode, imageContents);
      case NODE_VIEW_ID_TEXT:
        return AccessibilityNodeInfoUtils.getViewIdText(mNode);
      case NODE_SELECTED_PAGE_TITLE:
        return AccessibilityNodeInfoUtils.getSelectedPageTitle(mNode);
      case NODE_HINT_FOR_NODE_ACTIONS_HIGH_VERBOSITY:
        return AccessibilityNodeFeedbackUtils.getHintForNodeActions(
            mNode, mContext, globalVariables);

      case NODE_STATE_DESCRIPTION:
        return AccessibilityNodeFeedbackUtils.getNodeStateDescription(
            mNode,
            mContext,
            (mUserPreferredLocale != null)
                ? mUserPreferredLocale
                : AccessibilityNodeInfoUtils.getLocalesByNode(mNode));
      case NODE_CAPTION_TEXT:
        return AccessibilityNodeFeedbackUtils.getNodeCaptionText(
            mNode, mContext, imageContents, globalVariables.getUserPreferredLocale());
      case NODE_EDIT_TEXT_STATE:
        return EditTextDescription.stateDescription(mNode, mContext, globalVariables);
      case NODE_EDIT_TEXT_TEXT:
        return EditTextDescription.nameDescription(mNode, mContext, imageContents, globalVariables);
      case NODE_PAGER_PAGE_ROLE_DESCRIPTION:
        return AccessibilityNodeFeedbackUtils.getPagerPageRoleDescription(
            mNode, mContext, globalVariables);
      case NODE_NOTIFY_DISABLED:
        return AccessibilityNodeFeedbackUtils.getDisabledStateText(mNode, mContext);
      case NODE_NOTIFY_SELECTED:
        return AccessibilityNodeFeedbackUtils.getSelectedStateText(mNode, mContext);
      case NODE_NOTIFY_COLLAPSED_OR_EXPANDED:
        return AccessibilityNodeFeedbackUtils.getCollapsedOrExpandedStateText(mNode, mContext);
      case NODE_TEXT_OR_LABEL:
        return AccessibilityNodeFeedbackUtils.getNodeTextOrLabelDescription(
            mNode, mContext, imageContents, globalVariables);
      case NODE_TEXT_OR_LABEL_OR_ID:
        return AccessibilityNodeFeedbackUtils.getNodeTextOrLabelOrIdDescription(
            mNode, mContext, imageContents, globalVariables);
      case NODE_UNLABELLED_DESCRIPTION:
        return AccessibilityNodeFeedbackUtils.getUnlabelledNodeDescription(
            mRole, mNode, mContext, imageContents, globalVariables);
      case NODE_ENABLED_STATE:
        return AccessibilityNodeFeedbackUtils.getAccessibilityEnabledState(mNode, mContext);
      default:
        return mParentVariables.getString(variableId);
    }
  }

  @Override
  public int getEnum(int variableId) {
    switch (variableId) {
      case NODE_ROLE:
        return mRole;
      case NODE_LIVE_REGION:
        return mNode.getLiveRegion();
      case NODE_WINDOW_TYPE:
        return AccessibilityNodeInfoUtils.getWindowType(mNode);
      case NODE_RANGE_INFO_TYPE:
        {
          RangeInfoCompat rangeInfo = mNode.getRangeInfo();
          return (rangeInfo == null) ? RANGE_INFO_UNDEFINED : rangeInfo.getType();
        }
      default: // fall out
    }
    return mParentVariables.getEnum(variableId);
  }

  @Override
  public @Nullable VariableDelegate getReference(int variableId) {
    switch (variableId) {
      case NODE_LABELED_BY:
        {
          if (mLabelNode == null) {
            mLabelNode = mNode.getLabeledBy();
            if (mLabelNode == null) {
              return null;
            }
          }

          // Create a new variable delegate for the node label.
          // Do not use the same visited nodes, because this is not part of a node tree recursion.
          return constructForReferredNode(
              mContext,
              imageContents,
              mParentVariables,
              mLabelNode,
              globalVariables);
        }
      case NODE_PARENT:
        {
          if (mParentNode == null) {
            mParentNode = mNode.getParent();
            if (mParentNode == null) {
              return null;
            }
          }
          // Create a new variable delegate for the node parent.
          // Do not use the same visited nodes, because this is not part of a node tree recursion.
          return constructForReferredNode(
              mContext,
              imageContents,
              mParentVariables,
              mParentNode,
              globalVariables);
        }
      case NODE_ACTION_CLICK:
        for (AccessibilityActionCompat action : mNode.getActionList()) {
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_CLICK) {
            return new ActionVariables(mContext, this, action);
          }
        }
        return null;
      case NODE_ACTION_LONG_CLICK:
        for (AccessibilityActionCompat action : mNode.getActionList()) {
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK) {
            return new ActionVariables(mContext, this, action);
          }
        }
        return null;
      default:
        return mParentVariables.getReference(variableId);
    }
  }

  @Override
  public int getArrayLength(int variableId) {
    switch (variableId) {
      case NODE_CHILDREN:
        collectChildNodes();
        return mChildNodes.size();
      case NODE_CHILDREN_ASCENDING:
        collectChildNodesAscending();
        return mChildNodesAscending.size();
      case NODE_ACTIONS:
        return mNode.getActionList().size();
      default: // fall out
    }
    return mParentVariables.getArrayLength(variableId);
  }

  @Override
  public @Nullable CharSequence getArrayStringElement(int variableId, int index) {
    return mParentVariables.getArrayStringElement(variableId, index);
  }

  @Override
  public @Nullable VariableDelegate getArrayChildElement(int variableId, int index) {
    switch (variableId) {
      case NODE_CHILDREN:
        {
          return constructForChildNode(
              mContext,
              imageContents,
              mParentVariables,
              mChildNodes.get(index),
              mVisitedNodes,
              globalVariables);
        }
      case NODE_CHILDREN_ASCENDING:
        {
          return constructForChildNode(
              mContext,
              imageContents,
              mParentVariables,
              mChildNodesAscending.get(index),
              mVisitedNodes,
              globalVariables);
        }
      case NODE_ACTIONS:
        return new ActionVariables(mContext, this, mNode.getActionList().get(index));

      default:
        // Do nothing.
    }
    return mParentVariables.getArrayChildElement(variableId, index);
  }

  /** Collects child nodes in mChildNodes, to allow random access order and length-finding. */
  private void collectChildNodes() {
    createVisitedNodes();

    // Create new array for children.
    if (mChildNodes != null) {
      return;
    }
    int childNodesLength = mNode.getChildCount();
    mChildNodes = new ArrayList<>();

    // For each unvisited child node... collect a copy.
    AccessibilityNodeInfoCompat childNode = null;
    for (int childIndex = 0; childIndex < childNodesLength; ++childIndex) {
      childNode = mNode.getChild(childIndex);
      if (childNode != null) {
        if (mVisitedNodes.contains(childNode)) {
          childNode = null;
        } else {
          mChildNodes.add(childNode);
          childNode = null;
        }
      } else {
        LogUtils.e(TAG, "Node has a null child at index: " + childIndex);
      }
    }
  }

  /** Collects child nodes in mChildNodesAscending, to allow random access order. */
  private void collectChildNodesAscending() {
    createVisitedNodes();

    // Create new array for children.
    if (mChildNodesAscending != null) {
      return;
    }
    mChildNodesAscending = new ArrayList<>();
    ReorderedChildrenIterator childIterator =
        ReorderedChildrenIterator.createAscendingIterator(mNode);
    while (childIterator.hasNext()) {
      AccessibilityNodeInfoCompat childNode = childIterator.next();
      if (childNode != null) {
        if (!mVisitedNodes.contains(childNode)) {
          mChildNodesAscending.add(childNode);
        }
      } else {
        LogUtils.e(TAG, "Node has a null child");
      }
    }
  }

  /** Creates mVisitedNodes only on demand. */
  private void createVisitedNodes() {
    if (mIsRoot) {
      mVisitedNodes = new HashSet<>();
      mVisitedNodes.add(mNode);
    }
  }

  static void declareVariables(ParseTree parseTree) {
    // Variables.
    // Nodes.
    parseTree.addEnumVariable("node.role", NODE_ROLE, ENUM_ROLE);
    parseTree.addStringVariable("node.roleName", NODE_ROLE_NAME);
    parseTree.addStringVariable("node.text", NODE_TEXT);
    parseTree.addBooleanVariable(
        "node.notFrequentAnnounced", NODE_ALLOW_WINDOW_CONTENT_CHANGE_ANNOUNCEMENT);
    parseTree.addStringVariable("node.contentDescription", NODE_CONTENT_DESCRIPTION);
    parseTree.addStringVariable("node.hintDescription", NODE_HINT_DESCRIPTION);
    parseTree.addStringVariable("node.errorText", NODE_ERROR_TEXT);
    parseTree.addChildArrayVariable("node.children", NODE_CHILDREN);
    parseTree.addChildArrayVariable("node.childrenAscending", NODE_CHILDREN_ASCENDING);
    parseTree.addChildArrayVariable("node.actions", NODE_ACTIONS);
    parseTree.addStringVariable("node.roleDescription", NODE_ROLE_DESCRIPTION);
    parseTree.addBooleanVariable("node.isCheckable", NODE_CHECKABLE);
    parseTree.addBooleanVariable("node.isChecked", NODE_CHECKED);
    parseTree.addBooleanVariable("node.isVisible", NODE_IS_VISIBLE);
    parseTree.addBooleanVariable("node.isAccessibilityFocusable", NODE_IS_ACCESSIBILITY_FOCUSABLE);
    parseTree.addBooleanVariable("node.isImeWindow", NODE_WINDOW_IS_IME);
    parseTree.addBooleanVariable("node.isFocused", NODE_IS_FOCUSED);
    parseTree.addBooleanVariable("node.isAccessibilityFocused", NODE_IS_ACCESSIBILITY_FOCUSED);
    parseTree.addBooleanVariable("node.isShowingHint", NODE_IS_SHOWING_HINT);
    parseTree.addEnumVariable("node.liveRegion", NODE_LIVE_REGION, ENUM_LIVE_REGION);
    parseTree.addBooleanVariable(
        "node.supportsActionSetSelection", NODE_SUPPORTS_ACTION_SET_SELECTION);
    parseTree.addBooleanVariable("node.isPassword", NODE_IS_PASSWORD);
    parseTree.addBooleanVariable("node.isPinKey", NODE_IS_PIN_KEY);
    parseTree.addIntegerVariable("node.windowId", NODE_WINDOW_ID);
    parseTree.addEnumVariable("node.windowType", NODE_WINDOW_TYPE, ENUM_WINDOW_TYPE);
    parseTree.addBooleanVariable("node.supportsActionSelect", NODE_SUPPORTS_ACTION_SELECT);
    parseTree.addStringVariable("node.labelText", NODE_LABEL_TEXT);
    parseTree.addStringVariable("node.viewIdText", NODE_VIEW_ID_TEXT);
    parseTree.addStringVariable("node.selectedPageTitle", NODE_SELECTED_PAGE_TITLE);
    parseTree.addReferenceVariable("node.labeledBy", NODE_LABELED_BY);
    parseTree.addBooleanVariable("node.isActionable", NODE_IS_ACTIONABLE);
    parseTree.addBooleanVariable("node.isEnabled", NODE_IS_ENABLED);
    parseTree.addBooleanVariable("node.isSelected", NODE_IS_SELECTED);
    parseTree.addBooleanVariable("node.isExpandable", NODE_IS_EXPANDABLE);
    parseTree.addBooleanVariable("node.isCollapsible", NODE_IS_COLLAPSIBLE);
    parseTree.addReferenceVariable("node.parent", NODE_PARENT);
    parseTree.addIntegerVariable("node.visibleChildCount", NODE_VISIBLE_CHILD_COUNT);
    parseTree.addBooleanVariable(
        "node.supportsActionScrollForward", NODE_SUPPORTS_ACTION_SCROLL_FORWARD);
    parseTree.addBooleanVariable(
        "node.supportsActionScrollBackward", NODE_SUPPORTS_ACTION_SCROLL_BACKWARD);
    parseTree.addBooleanVariable("node.isClickable", NODE_IS_CLICKABLE);
    parseTree.addBooleanVariable("node.isLongClickable", NODE_IS_LONG_CLICKABLE);
    parseTree.addReferenceVariable("node.actionClick", NODE_ACTION_CLICK);
    parseTree.addReferenceVariable("node.actionLongClick", NODE_ACTION_LONG_CLICK);
    parseTree.addBooleanVariable("node.isHeading", NODE_IS_HEADING);
    parseTree.addEnumVariable("node.rangeInfoType", NODE_RANGE_INFO_TYPE, ENUM_RANGE_INFO_TYPE);
    parseTree.addNumberVariable("node.rangeCurrentValue", NODE_RANGE_CURRENT_VALUE);
    parseTree.addStringVariable("node.uniqueTooltipText", NODE_UNIQUE_TOOLTIP_TEXT);
    parseTree.addBooleanVariable("node.isContentInvalid", NODE_IS_CONTENT_INVALID);
    parseTree.addNumberVariable("node.progressPercent", NODE_PROGRESS_PERCENT);
    parseTree.addStringVariable(
        "node.hintForNodeActionsHighVerbosity", NODE_HINT_FOR_NODE_ACTIONS_HIGH_VERBOSITY);
    parseTree.addBooleanVariable("node.isWebContainer", NODE_IS_WEB_CONTAINER);
    parseTree.addBooleanVariable("node.needsLabel", NODE_NEEDS_LABEL);
    parseTree.addStringVariable("node.stateDescription", NODE_STATE_DESCRIPTION);
    parseTree.addBooleanVariable(
        "node.isWithinAccessibilityFocus", NODE_IS_WITHIN_ACCESSIBILITY_FOCUS);
    parseTree.addStringVariable("node.captionText", NODE_CAPTION_TEXT);
    parseTree.addStringVariable("node.editTextState", NODE_EDIT_TEXT_STATE);
    parseTree.addStringVariable("node.editTextText", NODE_EDIT_TEXT_TEXT);
    parseTree.addStringVariable("node.pagerPageRoleDescription", NODE_PAGER_PAGE_ROLE_DESCRIPTION);
    parseTree.addBooleanVariable("node.hasSpellingSuggestions", NODE_HAS_SPELLING_SUGGESTIONS);
    parseTree.addIntegerVariable("node.typoCount", NODE_TYPO_COUNT);
    parseTree.addStringVariable("node.notifyDisabled", NODE_NOTIFY_DISABLED);
    parseTree.addStringVariable("node.notifySelected", NODE_NOTIFY_SELECTED);
    parseTree.addStringVariable(
        "node.notifyCollapsedOrExpanded", NODE_NOTIFY_COLLAPSED_OR_EXPANDED);
    parseTree.addStringVariable("node.textOrLabel", NODE_TEXT_OR_LABEL);
    parseTree.addStringVariable("node.textOrLabelOrId", NODE_TEXT_OR_LABEL_OR_ID);
    parseTree.addStringVariable("node.unlabelledDescription", NODE_UNLABELLED_DESCRIPTION);
    parseTree.addStringVariable("node.enabledState", NODE_ENABLED_STATE);
  }
}
