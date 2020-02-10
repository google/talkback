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

package com.google.android.accessibility.compositor;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.Spannable;
import android.text.style.LocaleSpan;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.labeling.LabelManager;
import com.google.android.accessibility.utils.parsetree.ParseTree;
import com.google.android.accessibility.utils.parsetree.ParseTree.VariableDelegate;
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
  // Variables used for Switch Access
  private static final int NODE_IS_SCROLLABLE = 7037;
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
  private static final int NODE_HAS_NATIVE_WEB_CONTENT = 7048;

  private static final int NODE_SELF_MENU_ACTION_IS_AVAILABLE = 7049;
  private static final int NODE_SELF_MENU_ACTION_TYPE = 7050;
  private static final int NODE_IS_WEB_CONTAINER = 7051;

  private final Context mContext;
  private final @Nullable LabelManager mLabelManager;
  private final ParseTree.VariableDelegate mParentVariables;
  private boolean mOwnsParentVariables; // Should this object cleanup mParentVariables.
  private final AccessibilityNodeInfoCompat mNode; // Recycled by this.cleanup().
  private final @Role.RoleName int mRole; // Role of mNode.
  private boolean mIsRoot; // Is mNode the root/source node for node tree recursion.
  private Set<AccessibilityNodeInfoCompat> mVisitedNodes; // Recycled by this.cleanup()
  private ArrayList<AccessibilityNodeInfoCompat> mChildNodes; // Recycled by this.cleanup()
  private ArrayList<AccessibilityNodeInfoCompat> mChildNodesAscending; // Recycled by this.cleanup()
  private AccessibilityNodeInfoCompat mLabelNode; // Recycled by this.cleanup()
  private AccessibilityNodeInfoCompat mParentNode; // Recycled by this.cleanup()
  // Used to store keyboard Locale.
  private final @Nullable Locale mLocale;
  // Stores the user preferred locale changed using language switcher.
  private @Nullable final Locale mUserPreferredLocale;
  @Nullable private final NodeMenuProvider nodeMenuProvider;

  /**
   * Constructs a NodeVariables, which contains context variables to help generate feedback for an
   * accessibility event. Caller must call {@code cleanup()} when done with this object.
   *
   * @param node The view node for which we are generating feedback. The NodeVariables will recycle
   *     this node.
   */
  public NodeVariables(
      Context context,
      @Nullable LabelManager labelManager,
      @Nullable NodeMenuProvider nodeMenuProvider,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      @Nullable Locale userPreferredLocale) {
    mContext = context;
    mLabelManager = labelManager;
    mParentVariables = parent;
    mNode = node;
    mRole = Role.getRole(node);
    mIsRoot = true;
    mOwnsParentVariables = true;
    // Locale captured to wrap ttsOutput for IME input. Needed only for the old versions of Gboard.
    // New version wraps the content description and text in the locale of the IME and mLocale will
    // be null for it.
    mLocale = getLocaleForIME(node, context);
    mUserPreferredLocale = userPreferredLocale;
    this.nodeMenuProvider = nodeMenuProvider;
  }

  private static NodeVariables constructForReferredNode(
      Context context,
      @Nullable LabelManager labelManager,
      @Nullable NodeMenuProvider nodeMenuProvider,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      @Nullable Locale userPreferredLocale) {
    NodeVariables instance =
        new NodeVariables(
            context, labelManager, nodeMenuProvider, parent, node, userPreferredLocale);
    instance.mIsRoot = true; // This is a new root for node tree recursion.
    instance.mOwnsParentVariables = false; // Not responsible to recycle mParentVariables.
    return instance;
  }

  private static NodeVariables constructForChildNode(
      Context context,
      @Nullable LabelManager labelManager,
      @Nullable NodeMenuProvider nodeMenuProvider,
      ParseTree.VariableDelegate parent,
      AccessibilityNodeInfoCompat node,
      Set<AccessibilityNodeInfoCompat> visitedNodes,
      @Nullable Locale userPreferredLocale) {
    NodeVariables instance =
        new NodeVariables(
            context, labelManager, nodeMenuProvider, parent, node, userPreferredLocale);
    instance.mIsRoot = false; // Not responsible to recycle mVisitedNodes.
    instance.mOwnsParentVariables = false; // Not responsible to recycle mParentVariables.
    instance.setVisitedNodes(visitedNodes);
    return instance;
  }

  /**
   * Checks if the window type is TYPE_INPUT_METHOD and returns the locale. This only works for the
   * old versions of Gboard. The new version wraps the content description and text in the locale of
   * the IME and returns null here.
   */
  private static @Nullable Locale getLocaleForIME(
      AccessibilityNodeInfoCompat node, Context context) {
    Locale locale = null;
    if (isKeyboardEvent(node)) {
      return getKeyboardLocale(context);
    }
    return locale;
  }

  private static boolean isKeyboardEvent(AccessibilityNodeInfoCompat node) {
    if (node != null) {
      AccessibilityWindowInfoCompat window = AccessibilityNodeInfoUtils.getWindow(node);
      if (window != null && window.getType() == AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the locale of the IME. This only works for the old versions of Gboard. The new version
   * wraps the content description and text in the locale of the IME and returns an empty string
   * when InputMethodSubtype is queried for the locale.
   */
  private static @Nullable Locale getKeyboardLocale(Context context) {
    Locale locale = null;
    InputMethodManager imm =
        (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    InputMethodSubtype ims = imm.getCurrentInputMethodSubtype();
    if (ims == null) {
      return locale;
    }
    String localeString = ims.getLocale();
    locale = LocaleUtils.parseLocaleString(localeString);
    return locale;
  }

  private void setVisitedNodes(Set<AccessibilityNodeInfoCompat> visitedNodes) {
    mVisitedNodes = visitedNodes;
    if (mVisitedNodes != null && !mVisitedNodes.contains(mNode)) {
      mVisitedNodes.add(AccessibilityNodeInfoUtils.obtain(mNode));
    }
  }

  @Override
  public void cleanup() {
    AccessibilityNodeInfoUtils.recycleNodes(mNode);
    AccessibilityNodeInfoUtils.recycleNodes(mChildNodes);
    AccessibilityNodeInfoUtils.recycleNodes(mChildNodesAscending);
    AccessibilityNodeInfoUtils.recycleNodes(mLabelNode);
    AccessibilityNodeInfoUtils.recycleNodes(mParentNode);
    if (mIsRoot) {
      AccessibilityNodeInfoUtils.recycleNodes(mVisitedNodes);
    }
    // Many NodeVariables are spawned from another NodeVariables, and share parent variables.
    if (mOwnsParentVariables && mParentVariables != null) {
      mParentVariables.cleanup();
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  @Override
  public boolean getBoolean(int variableId) {
    switch (variableId) {
      case NODE_CHECKABLE:
        return mNode.isCheckable();
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
      case NODE_IS_SCROLLABLE:
        return AccessibilityNodeInfoUtils.isScrollable(mNode);
      case NODE_IS_PIN_KEY:
        return AccessibilityNodeInfoUtils.isPinKey(mNode);
      case NODE_IS_HEADING:
        return AccessibilityNodeInfoUtils.isHeading(mNode);
      case NODE_IS_COLLECTION_ITEM:
        return mNode.getCollectionItemInfo() != null;
      case NODE_IS_CONTENT_INVALID:
        return mNode.isContentInvalid();
      case NODE_HAS_NATIVE_WEB_CONTENT:
        return WebInterfaceUtils.hasNativeWebContent(mNode);
      case NODE_IS_WEB_CONTAINER:
        return WebInterfaceUtils.isWebContainer(mNode);
      case NODE_SELF_MENU_ACTION_IS_AVAILABLE:
        {
          return nodeMenuProvider != null
              && !nodeMenuProvider.getSelfNodeMenuActionTypes(mNode).isEmpty();
        }
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
        {
          CharSequence text = mNode.getText();
          // Cleans up the edit text's text if it has just 1 symbol.
          // Do not double clean up the password.
          if (Role.getRole(mNode) == Role.ROLE_EDIT_TEXT && !mNode.isPassword()) {
            text = SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(mContext, text);
          }
          // Wrapping with locale incase of IME input for old versions of GBoard. New version wraps
          // the text in the locale of the IME so this is not needed.
          if (mLocale != null && text != null) {
            return LocaleUtils.wrapWithLocaleSpan(text, mLocale);
          }
          /**
           * Wrap the text with user preferred locale changed using language switcher, with an
           * exception for all talkback nodes. As talkback text is always in the system language.
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
      case NODE_HINT:
        return AccessibilityNodeInfoUtils.getHintText(mNode);
      case NODE_ERROR:
        return mNode.getError();
      case NODE_TOOLTIP_TEXT:
        return mNode.getTooltipText();
      case NODE_CONTENT_DESCRIPTION:
        {
          CharSequence description = mNode.getContentDescription();
          // Cleans up the edit text's content description if it has just 1 symbol.
          // Do not double clean up the password.
          if (Role.getRole(mNode) == Role.ROLE_EDIT_TEXT && !mNode.isPassword()) {
            description =
                SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(mContext, description);
          }
          // Wrapping with locale incase of IME input for old versions of GBoard. New version wraps
          // the content description in the locale of the IME so this is not needed.
          if (mLocale != null && description != null) {
            return LocaleUtils.wrapWithLocaleSpan(description, mLocale);
          }
          /**
           * Wrap the text with user preferred locale changed using language switcher, with an
           * exception for all talkback nodes. As talkback text is always in the system language.
           */
          if (PackageManagerUtils.isTalkBackPackage(mNode.getPackageName())) {
            return description;
          }
          // mUserPreferredLocale will take precedence over any LocaleSpan that is attached to the
          // description except in case of IMEs.
          if (!isKeyboardEvent(mNode) && mUserPreferredLocale != null) {
            if (description instanceof Spannable) {
              Spannable ss = (Spannable) description;
              LocaleSpan[] spans = ss.getSpans(0, description.length(), LocaleSpan.class);
              for (LocaleSpan span : spans) {
                ss.removeSpan(span);
              }
            }
            return LocaleUtils.wrapWithLocaleSpan(description, mUserPreferredLocale);
          }
          return description;
        }
      case NODE_ROLE_DESCRIPTION:
        return mNode.getRoleDescription();
      case NODE_LABEL_TEXT:
        {
          if (mLabelManager == null) {
            return "";
          }
          final Label label =
              mLabelManager.getLabelForViewIdFromCache(mNode.getViewIdResourceName());
          if (label != null && label.getText() != null) {
            return label.getText();
          }
          return "";
        }
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
            mLabelNode = mNode.getLabeledBy(); // Recycled by cleanup()
            if (mLabelNode == null) {
              return null;
            }
          }

          // Create a new variable delegate for the node label.
          // Do not use the same visited nodes, because this is not part of a node tree recursion.
          // NodeVariables instance will recycle() obtain()ed copy of mLabelNode.
          return constructForReferredNode(
              mContext,
              mLabelManager,
              nodeMenuProvider,
              mParentVariables,
              AccessibilityNodeInfoUtils.obtain(mLabelNode),
              mUserPreferredLocale);
        }
      case NODE_PARENT:
        {
          if (mParentNode == null) {
            mParentNode = mNode.getParent(); // Recycled by cleanup()
            if (mParentNode == null) {
              return null;
            }
          }
          // Create a new variable delegate for the node parent.
          // Do not use the same visited nodes, because this is not part of a node tree recursion.
          // NodeVariables instance will recycle() obtain()ed copy of mParentNode.
          return constructForReferredNode(
              mContext,
              mLabelManager,
              nodeMenuProvider,
              mParentVariables,
              AccessibilityNodeInfoUtils.obtain(mParentNode),
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

  /** Caller must call VariableDelegate.cleanup() on returned instance. */
  @Override
  public @Nullable VariableDelegate getArrayChildElement(int variableId, int index) {
    switch (variableId) {
      case NODE_CHILDREN:
        {
          // NodeVariables instance will recycle() obtain()ed copy of child node.
          return constructForChildNode(
              mContext,
              mLabelManager,
              nodeMenuProvider,
              mParentVariables,
              AccessibilityNodeInfoUtils.obtain(mChildNodes.get(index)),
              mVisitedNodes,
              mUserPreferredLocale);
        }
      case NODE_CHILDREN_ASCENDING:
        {
          // NodeVariables instance will recycle() obtain()ed copy of child node.
          return constructForChildNode(
              mContext,
              mLabelManager,
              nodeMenuProvider,
              mParentVariables,
              AccessibilityNodeInfoUtils.obtain(mChildNodesAscending.get(index)),
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
    try {
      for (int childIndex = 0; childIndex < childNodesLength; ++childIndex) {
        childNode = mNode.getChild(childIndex); // Must be recycled.
        if (childNode != null) {
          if (mVisitedNodes.contains(childNode)) {
            AccessibilityNodeInfoUtils.recycleNodes(childNode);
            childNode = null;
          } else {
            mChildNodes.add(childNode);
            childNode = null;
            // VariableDelegate.cleanup() will recycle node created by getChild().
          }
        } else {
          LogUtils.e(TAG, "Node has a null child at index: " + childIndex);
        }
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(childNode);
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
    AccessibilityNodeInfoCompat childNode = null;
    try {
      while (childIterator.hasNext()) {
        childNode = childIterator.next(); // Must be recycled.
        if (childNode != null) {
          if (mVisitedNodes.contains(childNode)) {
            AccessibilityNodeInfoUtils.recycleNodes(childNode);
            childNode = null;
          } else {
            mChildNodesAscending.add(childNode);
            childNode = null;
            // VariableDelegate will recycle node created by next().
          }
        } else {
          LogUtils.e(TAG, "Node has a null child");
        }
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(childNode);
    }
  }

  /** Creates mVisitedNodes only on demand. */
  private void createVisitedNodes() {
    if (mIsRoot && mVisitedNodes == null) {
      mVisitedNodes = new HashSet<>();
      if (!mVisitedNodes.contains(mNode)) {
        mVisitedNodes.add(AccessibilityNodeInfoUtils.obtain(mNode));
      }
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
    parseTree.addBooleanVariable("node.isFocused", NODE_IS_FOCUSED);
    parseTree.addBooleanVariable("node.isAccessibilityFocused", NODE_IS_ACCESSIBILITY_FOCUSED);
    parseTree.addBooleanVariable("node.isShowingHint", NODE_IS_SHOWING_HINT);
    parseTree.addBooleanVariable("node.isScrollable", NODE_IS_SCROLLABLE);
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
    parseTree.addBooleanVariable("node.hasNativeWebContent", NODE_HAS_NATIVE_WEB_CONTENT);
    parseTree.addBooleanVariable(
        "node.selfMenuActionAvailable", NODE_SELF_MENU_ACTION_IS_AVAILABLE);
    parseTree.addStringVariable("node.selfMenuActions", NODE_SELF_MENU_ACTION_TYPE);
    parseTree.addBooleanVariable("node.isWebContainer", NODE_IS_WEB_CONTAINER);
  }
}
