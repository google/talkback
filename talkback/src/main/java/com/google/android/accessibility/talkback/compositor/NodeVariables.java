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

import android.content.Context;
import android.text.Spannable;
import android.text.style.LocaleSpan;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree.VariableDelegate;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
  private static final int NODE_HINT = 7035;
  private static final int NODE_IS_SHOWING_HINT = 7036;
  private static final int NODE_VIEW_ID_TEXT = 7038;
  private static final int NODE_SELECTED_PAGE_TITLE = 7039;
  private static final int NODE_IS_HEADING = 7040;
  // TODO: May need an array variable for visible children.

  private static final int NODE_RANGE_INFO_TYPE = 7041;
  private static final int NODE_RANGE_CURRENT_VALUE = 7042;

  private static final int NODE_TOOLTIP_TEXT = 7043;

  private static final int NODE_IS_COLLECTION_ITEM = 7044;

  private static final int NODE_IS_CONTENT_INVALID = 7045;
  private static final int NODE_ERROR = 7046;

  private static final int NODE_PROGRESS_PERCENT = 7047;

  private static final int NODE_SELF_MENU_ACTION_IS_AVAILABLE = 7049;
  private static final int NODE_SELF_MENU_ACTION_TYPE = 7050;
  private static final int NODE_IS_WEB_CONTAINER = 7051;
  private static final int NODE_NEEDS_LABEL = 7052;
  private static final int NODE_STATE_DESCRIPTION = 7053;
  private static final int NODE_IS_WITHIN_ACCESSIBILITY_FOCUS = 7054;
  private static final int NODE_IS_KEYBOARD_WINDOW = 7055;
  private static final int NODE_CAPTION_TEXT = 7056;

  private static final int NODE_ANNOUNCE_DISABLED = 7057;
  private static final int NODE_DETECTED_ICON_LABEL = 7058;
  private static final int NODE_WINDOW_IS_IME = 7059;

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
  private final @Nullable NodeMenuProvider nodeMenuProvider;

  /**
   * Constructs a NodeVariables, which contains context variables to help generate feedback for an
   * accessibility event.
   *
   * @param node The view node for which we are generating feedback.
   */
  public NodeVariables(
      Context context,
      @Nullable ImageContents imageContents,
      @Nullable NodeMenuProvider nodeMenuProvider,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      @Nullable Locale userPreferredLocale) {
    mContext = context;
    this.imageContents = imageContents;
    mParentVariables = parent;
    mNode = node;
    mRole = Role.getRole(node);
    mIsRoot = true;
    mUserPreferredLocale = userPreferredLocale;
    this.nodeMenuProvider = nodeMenuProvider;
  }

  private static NodeVariables constructForReferredNode(
      Context context,
      @Nullable ImageContents imageContents,
      @Nullable NodeMenuProvider nodeMenuProvider,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      @Nullable Locale userPreferredLocale) {
    NodeVariables instance =
        new NodeVariables(
            context, imageContents, nodeMenuProvider, parent, node, userPreferredLocale);
    instance.mIsRoot = true; // This is a new root for node tree recursion.
    return instance;
  }

  private static NodeVariables constructForChildNode(
      Context context,
      @Nullable ImageContents imageContents,
      @Nullable NodeMenuProvider nodeMenuProvider,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      Set<AccessibilityNodeInfoCompat> visitedNodes,
      @Nullable Locale userPreferredLocale) {
    NodeVariables instance =
        new NodeVariables(
            context, imageContents, nodeMenuProvider, parent, node, userPreferredLocale);
    instance.mIsRoot = false;
    instance.setVisitedNodes(visitedNodes);
    return instance;
  }

  private static boolean isKeyboardEvent(AccessibilityNodeInfoCompat node) {
    if (node != null) {
      AccessibilityWindowInfoCompat window = AccessibilityNodeInfoUtils.getWindow(node);
      if (window != null && window.getType() == AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD) {
        // TalkBack stops supporting getCurrentInputMethodSubtype() for locale. If you'd like to
        // provide speech with locale setting, please use LocaleSpan instead.
        return true;
      }
    }
    return false;
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
      case NODE_CHECKED:
        // REFERTO. On M devices, the state of the switch is always the same until the
        // focus changes. Refreshes the node before getting the state as a workaround.
        if (BuildVersionUtils.isM()) {
          mNode.refresh();
        }
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
      case NODE_IS_COLLECTION_ITEM:
        return mNode.getCollectionItemInfo() != null;
      case NODE_IS_CONTENT_INVALID:
        return mNode.isContentInvalid();
      case NODE_IS_WEB_CONTAINER:
        return WebInterfaceUtils.isWebContainer(mNode);
      case NODE_SELF_MENU_ACTION_IS_AVAILABLE:
        {
          return nodeMenuProvider != null
              && !nodeMenuProvider.getSelfNodeMenuActionTypes(mNode).isEmpty();
        }
      case NODE_NEEDS_LABEL:
        return imageContents != null && imageContents.needsLabel(mNode);
      case NODE_IS_WITHIN_ACCESSIBILITY_FOCUS:
        {
          return AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(mNode);
        }
      case NODE_IS_KEYBOARD_WINDOW:
        return isKeyboardEvent(mNode);
      case NODE_ANNOUNCE_DISABLED:
        {
          // In some situations Views marked as headings (see ViewCompat#setAccessibilityHeading)
          // are in the disabled state, even though being disabled is not very appropriate. An
          // example are TextViews styled as preferenceCategoryStyle in certain themes.
          if (mNode.isHeading()) {
            return false;
          }
          if (BuildVersionUtils.isAtLeastS()) {
            return !mNode.isEnabled();
          }
          return !mNode.isEnabled()
              && (WebInterfaceUtils.hasNativeWebContent(mNode)
                  || AccessibilityNodeInfoUtils.isActionableForAccessibility(mNode));
        }
      case NODE_WINDOW_IS_IME:
        AccessibilityWindowInfoCompat window = AccessibilityNodeInfoUtils.getWindow(mNode);
        return (window != null)
            && (window.getType() == AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD);
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
          return (rangeInfo == null) ? 0 : rangeInfo.getCurrent();
        }
      case NODE_PROGRESS_PERCENT:
        return AccessibilityNodeInfoUtils.getProgressPercent(mNode);
      default:
        return mParentVariables.getNumber(variableId);
    }
  }

  @Override
  public @Nullable CharSequence getString(int variableId) {
    switch (variableId) {
      case NODE_TEXT:
        return prepareSpans(AccessibilityNodeInfoUtils.getText(mNode));
      case NODE_HINT:
        return AccessibilityNodeInfoUtils.getHintText(mNode);
      case NODE_ERROR:
        return mNode.getError();
      case NODE_TOOLTIP_TEXT:
        return mNode.getTooltipText();
      case NODE_CONTENT_DESCRIPTION:
        return prepareSpans(mNode.getContentDescription());
      case NODE_ROLE_DESCRIPTION:
        return mNode.getRoleDescription();
      case NODE_LABEL_TEXT:
        return imageContents == null ? "" : imageContents.getLabel(mNode);
      case NODE_VIEW_ID_TEXT:
        return AccessibilityNodeInfoUtils.getViewIdText(mNode);
      case NODE_SELECTED_PAGE_TITLE:
        return AccessibilityNodeInfoUtils.getSelectedPageTitle(mNode);
      case NODE_SELF_MENU_ACTION_TYPE:
        {
          String returnString = "";
          if (nodeMenuProvider != null) {
            List<String> menuTypeList = nodeMenuProvider.getSelfNodeMenuActionTypes(mNode);
            returnString = Joiner.on(",").join(menuTypeList);
          }
          return returnString;
        }
      case NODE_STATE_DESCRIPTION:
        return prepareSpans(AccessibilityNodeInfoUtils.getState(mNode));
      case NODE_CAPTION_TEXT:
        {
          @Nullable CharSequence ocrText =
              (imageContents == null) ? null : imageContents.getCaptionResult(mNode);
          return (ocrText == null)
              ? ""
              : mContext.getString(R.string.ocr_text_description, ocrText);
        }
      case NODE_DETECTED_ICON_LABEL:
        {
          Locale locale =
              (mUserPreferredLocale == null) ? Locale.getDefault() : mUserPreferredLocale;
          @Nullable CharSequence iconLabel =
              (imageContents == null) ? null : imageContents.getDetectedIconLabel(locale, mNode);
          return (iconLabel == null)
              ? ""
              : mContext.getString(R.string.detected_icon_description, iconLabel);
        }
      default:
        return mParentVariables.getString(variableId);
    }
  }

  private @Nullable CharSequence prepareSpans(@Nullable CharSequence text) {
    // Cleans up the edit text's text if it has just 1 symbol.
    // Do not double clean up the password.
    if (!mNode.isPassword()) {
      text = SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(mContext, text);
    }
    /**
     * Wrap the text with user preferred locale changed using language switcher, with an exception
     * for all talkback nodes. As talkback text is always in the system language.
     */
    if (PackageManagerUtils.isTalkBackPackage(mNode.getPackageName())) {
      return text;
    }
    // mUserPreferredLocale will take precedence over any LocaleSpan that is attached to the
    // text except in case of IMEs.
    if (!isKeyboardEvent(mNode) && mUserPreferredLocale != null) {
      if (text instanceof Spannable) {
        Spannable ss = (Spannable) text;
        LocaleSpan[] spans = ss.getSpans(0, text.length(), LocaleSpan.class);
        for (LocaleSpan span : spans) {
          ss.removeSpan(span);
        }
      }
      return LocaleUtils.wrapWithLocaleSpan(text, mUserPreferredLocale);
    }
    return text;
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
          return (rangeInfo == null) ? Compositor.RANGE_INFO_UNDEFINED : rangeInfo.getType();
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
              nodeMenuProvider,
              mParentVariables,
              mLabelNode,
              mUserPreferredLocale);
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
              nodeMenuProvider,
              mParentVariables,
              mParentNode,
              mUserPreferredLocale);
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
              nodeMenuProvider,
              mParentVariables,
              mChildNodes.get(index),
              mVisitedNodes,
              mUserPreferredLocale);
        }
      case NODE_CHILDREN_ASCENDING:
        {
          return constructForChildNode(
              mContext,
              imageContents,
              nodeMenuProvider,
              mParentVariables,
              mChildNodesAscending.get(index),
              mVisitedNodes,
              mUserPreferredLocale);
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
    parseTree.addEnumVariable("node.role", NODE_ROLE, Compositor.ENUM_ROLE);
    parseTree.addStringVariable("node.text", NODE_TEXT);
    parseTree.addStringVariable("node.contentDescription", NODE_CONTENT_DESCRIPTION);
    parseTree.addStringVariable("node.hint", NODE_HINT);
    parseTree.addStringVariable("node.error", NODE_ERROR);
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
    parseTree.addEnumVariable("node.liveRegion", NODE_LIVE_REGION, Compositor.ENUM_LIVE_REGION);
    parseTree.addBooleanVariable(
        "node.supportsActionSetSelection", NODE_SUPPORTS_ACTION_SET_SELECTION);
    parseTree.addBooleanVariable("node.isPassword", NODE_IS_PASSWORD);
    parseTree.addBooleanVariable("node.isPinKey", NODE_IS_PIN_KEY);
    parseTree.addIntegerVariable("node.windowId", NODE_WINDOW_ID);
    parseTree.addEnumVariable("node.windowType", NODE_WINDOW_TYPE, Compositor.ENUM_WINDOW_TYPE);
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
    parseTree.addEnumVariable(
        "node.rangeInfoType", NODE_RANGE_INFO_TYPE, Compositor.ENUM_RANGE_INFO_TYPE);
    parseTree.addNumberVariable("node.rangeCurrentValue", NODE_RANGE_CURRENT_VALUE);
    parseTree.addStringVariable("node.tooltipText", NODE_TOOLTIP_TEXT);
    parseTree.addBooleanVariable("node.isCollectionItem", NODE_IS_COLLECTION_ITEM);
    parseTree.addBooleanVariable("node.isContentInvalid", NODE_IS_CONTENT_INVALID);
    parseTree.addNumberVariable("node.progressPercent", NODE_PROGRESS_PERCENT);
    parseTree.addBooleanVariable(
        "node.selfMenuActionAvailable", NODE_SELF_MENU_ACTION_IS_AVAILABLE);
    parseTree.addStringVariable("node.selfMenuActions", NODE_SELF_MENU_ACTION_TYPE);
    parseTree.addBooleanVariable("node.isWebContainer", NODE_IS_WEB_CONTAINER);
    parseTree.addBooleanVariable("node.needsLabel", NODE_NEEDS_LABEL);
    parseTree.addStringVariable("node.stateDescription", NODE_STATE_DESCRIPTION);
    parseTree.addBooleanVariable(
        "node.isWithinAccessibilityFocus", NODE_IS_WITHIN_ACCESSIBILITY_FOCUS);
    parseTree.addBooleanVariable("node.isKeyboardWindow", NODE_IS_KEYBOARD_WINDOW);
    parseTree.addStringVariable("node.captionText", NODE_CAPTION_TEXT);
    parseTree.addBooleanVariable("node.announceDisabled", NODE_ANNOUNCE_DISABLED);
    parseTree.addStringVariable("node.detectedIconLabel", NODE_DETECTED_ICON_LABEL);
  }
}
