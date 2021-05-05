/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.accessibility.utils;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.GridView;
import android.widget.ListView;
import androidx.annotation.NonNull;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.compat.CompatUtils;
import com.google.android.accessibility.utils.traversal.SpannableTraversalUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.libraries.accessibility.utils.url.SpannableUrl;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

/**
 * Provides a series of utilities for interacting with AccessibilityNodeInfo objects. NOTE: This
 * class only recycles unused nodes that were collected internally. Any node passed into or returned
 * from a public method is retained and TalkBack should recycle it when appropriate.
 */
public class AccessibilityNodeInfoUtils {

  /** Internal AccessibilityNodeInfoCompat extras bundle key constants. */
  private static final String BOOLEAN_PROPERTY_KEY =
      "androidx.view.accessibility.AccessibilityNodeInfoCompat.BOOLEAN_PROPERTY_KEY";

  // TODO Remove them when androidx.core library is available.
  // Add this constant because AccessibilityNodeInfoCompat.setTextEntryKey() is unavailable yet.
  // Copy it from
  // androidx.core.view.accessibility.AccessibilityNodeInfoCompat.BOOLEAN_PROPERTY_IS_TEXT_ENTRY_KEY
  private static final int BOOLEAN_MASK_IS_TEXT_ENTRY_KEY = 8;

  // The minimum amount of pixels that must be visible for a view to be surfaced to the user as
  // visible (i.e. for this node to be added to the tree).
  public static final int MIN_VISIBLE_PIXELS = 15;

  private static final String CLASS_LISTVIEW = ListView.class.getName();
  private static final String CLASS_GRIDVIEW = GridView.class.getName();

  // TODO: When androidx support library is available, change all node.getText() to use
  // AccessibilityNodeInfoCompat.getText() via this wrapper.
  /** Returns text from an accessibility-node, including spans. */
  public static @Nullable CharSequence getText(@Nullable AccessibilityNodeInfoCompat node) {
    return (node == null) ? null : node.getText();
  }

  @FormatMethod
  private static void logError(String functionName, @FormatString String format, Object... args) {
    LogUtils.e(TAG, functionName + "() " + String.format(format, args));
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "AccessibilityNodeInfoUtils";

  /**
   * Class for Samsung's TouchWiz implementation of AdapterView. May be {@code null} on non-Samsung
   * devices.
   */
  private static final Class<?> CLASS_TOUCHWIZ_TWADAPTERVIEW =
      CompatUtils.getClass("com.sec.android.touchwiz.widget.TwAdapterView");

  /** Key to get accessibility web hints from the web */
  private static final String HINT_TEXT_KEY = "AccessibilityNodeInfo.hint";

  private static final Pattern RESOURCE_NAME_SPLIT_PATTERN = Pattern.compile(":id/");

  /** Class used to find clickable-spans in text. */
  public static final Class<?> TARGET_SPAN_CLASS = ClickableSpan.class;

  // Used to identify keys from the pin password keyboard used to unlock the screen.
  private static final Pattern PIN_KEY_PATTERN =
      Pattern.compile("com.android.systemui:id/key\\d{0,9}");

  private static final String VIEW_ID_RESOURCE_NAME_PIN_ENTRY = "com.android.systemui:id/pinEntry";

  /**
   * A wrapper over AccessibilityNodeInfoCompat constructor, so that we can add any desired error
   * checking and memory management.
   *
   * @param nodeInfo The AccessibilityNodeInfo which will be wrapped. The caller retains the
   *     responsibility to recycle nodeInfo.
   * @return Encapsulating AccessibilityNodeInfoCompat, or null if input is null.
   */
  public static @PolyNull AccessibilityNodeInfoCompat toCompat(
      @PolyNull AccessibilityNodeInfo nodeInfo) {
    if (nodeInfo == null) {
      return null;
    }
    return AccessibilityNodeInfoCompat.wrap(nodeInfo);
  }

  private static final int SYSTEM_ACTION_MAX = 0x01FFFFFF;

  public static final int WINDOW_TYPE_NONE = -1;
  public static final int WINDOW_TYPE_PICTURE_IN_PICTURE = 1000;

  /**
   * Filter for scrollable items. One of the following must be true:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isScrollable()} returns {@code true}
   *   <li>{@link AccessibilityNodeInfoCompat#getActions()} supports {@link
   *       AccessibilityNodeInfoCompat#ACTION_SCROLL_FORWARD}
   *   <li>{@link AccessibilityNodeInfoCompat#getActions()} supports {@link
   *       AccessibilityNodeInfoCompat#ACTION_SCROLL_BACKWARD}
   * </ul>
   */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_SCROLLABLE =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return isScrollable(node);
        }
      };

  /** Filter for items that could be scrolled forward. */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_COULD_SCROLL_FORWARD =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null
              && supportsAction(node, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
        }
      };

  /** Filter for items that could be scrolled backward. */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_COULD_SCROLL_BACKWARD =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null
              && supportsAction(node, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
        }
      };

  /**
   * Filter for items that should receive accessibility focus. Equivalent to calling {@link
   * #shouldFocusNode(AccessibilityNodeInfoCompat)}.
   *
   * <p><strong>Note:</strong> Use {@link #FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW} has a filter for
   * {@link AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events.
   */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_SHOULD_FOCUS =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null && shouldFocusNode(node);
        }
      };

  /**
   * Filter for items that should receive accessibility focus from {@link
   * AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events. WebView container node should not be focus
   * for hover enter actions.
   */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW =
      FILTER_SHOULD_FOCUS.and(
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return Role.getRole(node) != Role.ROLE_WEB_VIEW;
            }
          });

  /** Filter for heading items in collections. */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_HEADING =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return (node != null) && isHeading(node);
        }
      };

  /**
   * Filter that also checks for {@param node}'s non-focusable but visible children. Sometimes, a
   * node that passes the filter can be embedded in a parent and might be not focusable by itself.
   * In those cases it is important to focus the parent. Example would be for "Control" granularity,
   * if a switch is not focusable but is embedded into a focusable parent, its parent should be
   * focused.
   */
  public static Filter<AccessibilityNodeInfoCompat> getFilterIncludingChildren(
      final Filter<AccessibilityNodeInfoCompat> filter) {
    return new Filter<AccessibilityNodeInfoCompat>() {
      @Override
      public boolean accept(AccessibilityNodeInfoCompat node) {
        if (node == null) {
          return false;
        }
        boolean val = filter.accept(node);
        // If the node does not pass the filter, check its non focusable, visible children.
        if (!val) {
          return hasMatchingDescendant(node, filter.and(FILTER_NON_FOCUSABLE_VISIBLE_NODE));
        }
        return val;
      }
    };
  }

  /** Filter to identify nodes which are not focusable but visible. */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_NON_FOCUSABLE_VISIBLE_NODE =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return isVisible(node) && !isAccessibilityFocusable(node);
        }
      };

  /** Filter for controllable elements. */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_CONTROL =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          if (node == null) {
            return false;
          }
          @RoleName int role = Role.getRole(node);
          return (role == Role.ROLE_BUTTON)
              || (role == Role.ROLE_IMAGE_BUTTON)
              || (role == Role.ROLE_EDIT_TEXT)
              || (role == Role.ROLE_CHECK_BOX)
              || (role == Role.ROLE_RADIO_BUTTON)
              || (role == Role.ROLE_TOGGLE_BUTTON)
              || (role == Role.ROLE_SWITCH)
              || (role == Role.ROLE_DROP_DOWN_LIST)
              || (role == Role.ROLE_SEEK_CONTROL);
        }
      };

  /** Filter for Spannables with links. */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_LINK =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return SpannableTraversalUtils.hasTargetSpanInNodeTreeDescription(
              node, TARGET_SPAN_CLASS);
        }
      };

  public static final Filter<AccessibilityNodeInfoCompat> FILTER_CLICKABLE =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return AccessibilityNodeInfoUtils.isClickable(node);
        }
      };

  public static final Filter<AccessibilityNodeInfoCompat> FILTER_HAS_TEXT =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node));
        }
      };

  public static final Filter<AccessibilityNodeInfoCompat> FILTER_ILLEGAL_TITLE_NODE_ANCESTOR =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          if (isClickable(node) || isLongClickable(node)) {
            return true;
          }
          @RoleName int role = Role.getRole(node);
          // A window title node should not be a descendant of AdapterView.
          return (role == Role.ROLE_LIST) || (role == Role.ROLE_GRID);
        }
      };

  /**
   * This filter accepts scrollable views that break if we place accessibility focus on their child
   * items. Instead, we should just place focus on the entire scrollable view. Note: Only include
   * Android TV views that cannot be updated (i.e. part of a bundled app).
   */
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_BROKEN_LISTS_TV_M =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          if (node == null) {
            return false;
          }

          String viewId = node.getViewIdResourceName();
          return "com.android.tv.settings:id/setup_scroll_list".equals(viewId)
              || "com.google.android.gsf.notouch:id/setup_scroll_list".equals(viewId)
              || "com.android.vending:id/setup_scroll_list".equals(viewId);
        }
      };

  /**
   * Filter that defines which types of views should be auto-scrolled. Generally speaking, only
   * accepts views that are capable of showing partially-visible data.
   *
   * <p>Accepts the following classes (and sub-classes thereof):
   *
   * <ul>
   *   <li>{@link androidx.recyclerview.widget.RecyclerView} (Should be classified as a List or Grid.)
   *   <li>{@link android.widget.AbsListView} (including both ListView and GridView)
   *   <li>{@link android.widget.AbsSpinner}
   *   <li>{@link android.widget.ScrollView}
   *   <li>{@link android.widget.HorizontalScrollView}
   *   <li>{@code com.sec.android.touchwiz.widget.TwAbsListView}
   * </ul>
   *
   * <p>Specifically excludes {@link android.widget.AdapterViewAnimator} and sub-classes, since they
   * represent overlapping views. Also excludes {@link androidx.viewpager.widget.ViewPager} since it
   * exclusively represents off-screen views.
   */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_AUTO_SCROLL =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          if (!isScrollable(node) || !isVisible(node)) {
            return false;
          }
          @Role.RoleName int role = Role.getRole(node);
          // TODO: Check if we should include ROLE_ADAPTER_VIEW as a target Role.
          return role == Role.ROLE_DROP_DOWN_LIST
              || role == Role.ROLE_LIST
              || role == Role.ROLE_GRID
              || role == Role.ROLE_SCROLL_VIEW
              || role == Role.ROLE_HORIZONTAL_SCROLL_VIEW
              || AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(
                  node, CLASS_TOUCHWIZ_TWADAPTERVIEW);
        }
      };

  public static final Filter<AccessibilityNodeInfoCompat> FILTER_COLLECTION =
      new Filter.NodeCompat(
          (node) -> {
            int role = Role.getRole(node);
            return (role == Role.ROLE_LIST)
                || (role == Role.ROLE_GRID)
                || (role == Role.ROLE_PAGER)
                || (node != null && node.getCollectionInfo() != null);
          });

  private AccessibilityNodeInfoUtils() {
    // This class is not instantiable.
  }

  /**
   * Gets the text of a <code>node</code> by returning the content description (if available) or by
   * returning the text.
   *
   * @param node The node.
   * @return The node text.
   */
  public static @Nullable CharSequence getNodeText(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    // Prefer content description over text.
    // TODO: Why are we checking the trimmed length?
    final CharSequence contentDescription = node.getContentDescription();
    if (!TextUtils.isEmpty(contentDescription)
        && (TextUtils.getTrimmedLength(contentDescription) > 0)) {
      return contentDescription;
    }

    final @Nullable CharSequence text = AccessibilityNodeInfoUtils.getText(node);
    if (!TextUtils.isEmpty(text) && (TextUtils.getTrimmedLength(text) > 0)) {
      return text;
    }

    return null;
  }

  /**
   * Gets the state description of a <code>node</code>.
   *
   * @param node The node.
   * @return The node state description.
   */
  public static @Nullable CharSequence getState(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    final CharSequence state = node.getStateDescription();
    if (!TextUtils.isEmpty(state) && (TextUtils.getTrimmedLength(state) > 0)) {
      return state;
    }

    return null;
  }

  /**
   * Gets the Selected text of a <code>node</code> by returning the selected text.
   *
   * @param node The node.
   * @return The selected node text.
   */
  public static @Nullable CharSequence getSelectedNodeText(
      @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    CharSequence selectedText =
        subsequenceSafe(
            AccessibilityNodeInfoUtils.getText(node),
            node.getTextSelectionStart(),
            node.getTextSelectionEnd());
    if (!TextUtils.isEmpty(selectedText) && (TextUtils.getTrimmedLength(selectedText) > 0)) {
      return selectedText;
    }

    return null;
  }

  /** Returns a sub-string or empty-string, without crashing on invalid subsequence range. */
  public static CharSequence subsequenceSafe(
      @Nullable CharSequence text, int startIndex, int endIndex) {
    if (text == null) {
      return "";
    }
    // Swap start and end.
    if (endIndex < startIndex) {
      int newStartIndex = endIndex;
      endIndex = startIndex;
      startIndex = newStartIndex;
    }
    // Enforce string bounds.
    if (startIndex < 0) {
      startIndex = 0;
    } else if (startIndex > text.length()) {
      startIndex = text.length();
    }
    if (endIndex < 0) {
      endIndex = 0;
    } else if (endIndex > text.length()) {
      endIndex = text.length();
    }

    return text.subSequence(startIndex, endIndex);
  }

  /**
   * Gets the text selection indexes safe by adjusting the checking the selection bounds.
   *
   * @param node The node
   * @return the selection indexes
   */
  public static Pair<Integer, Integer> getSelectionIndexesSafe(
      @NonNull AccessibilityNodeInfoCompat node) {
    int selectionStart = node.getTextSelectionStart();
    int selectionEnd = node.getTextSelectionEnd();
    if (selectionStart < 0) {
      selectionStart = 0;
    }
    if (selectionEnd < 0) {
      selectionEnd = selectionStart;
    }
    if (selectionEnd < selectionStart) {
      // Swap start and end to make sure they are in order.
      int newStart = selectionEnd;
      selectionEnd = selectionStart;
      selectionStart = newStart;
    }
    return Pair.create(selectionStart, selectionEnd);
  }

  /**
   * Gets the textual representation of the view ID that can be used when no custom label is
   * available. For better readability/listenability, the "_" characters are replaced with spaces.
   *
   * @param node The node
   * @return Readable text of the view Id
   */
  public static @Nullable String getViewIdText(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    String resourceName = node.getViewIdResourceName();
    if (resourceName == null) {
      return null;
    }

    String[] parsedResourceName = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2);
    if (parsedResourceName.length != 2
        || TextUtils.isEmpty(parsedResourceName[0])
        || TextUtils.isEmpty(parsedResourceName[1])) {
      return null;
    }

    return parsedResourceName[1].replace('_', ' '); // readable View ID text
  }

  public static @Nullable CharSequence getSelectedPageTitle(AccessibilityNodeInfoCompat viewPager) {
    if ((viewPager == null) || (Role.getRole(viewPager) != Role.ROLE_PAGER)) {
      return null;
    }

    int numChildren = viewPager.getChildCount(); // Not the number of pages!
    CharSequence title = null;
    for (int i = 0; i < numChildren; ++i) {
      AccessibilityNodeInfoCompat child = viewPager.getChild(i);
      if (child != null) {
        try {
          if (child.isVisibleToUser()) {
            if (title == null) {
              // Try to roughly match RulePagerPage, which uses getNodeText
              // (but completely matching all the time is not critical).
              title = getNodeText(child);
            } else {
              // Multiple visible children, abort.
              return null;
            }
          }
        } finally {
          recycleNodes(child);
        }
      }
    }

    return title;
  }

  public static List<AccessibilityActionCompat> getCustomActions(AccessibilityNodeInfoCompat node) {
    List<AccessibilityActionCompat> customActions = new ArrayList<>();
    for (AccessibilityActionCompat action : node.getActionList()) {
      if (isCustomAction(action)) {
        // We don't use custom actions that doesn't have a label
        if (!TextUtils.isEmpty(action.getLabel())) {
          customActions.add(action);
        }
      }
    }

    return customActions;
  }

  public static boolean isCustomAction(AccessibilityActionCompat action) {
    return action.getId() > SYSTEM_ACTION_MAX;
  }

  /** Returns the root node of the tree containing {@code node}. */
  public static @Nullable AccessibilityNodeInfoCompat getRoot(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    AccessibilityWindowInfoCompat window = null;
    try {
      window = getWindow(node);
      if (window != null) {
        return AccessibilityWindowInfoUtils.getRoot(window);
      }
    } finally {
      if (window != null) {
        window.recycle();
      }
    }

    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    AccessibilityNodeInfoCompat current = null;
    AccessibilityNodeInfoCompat parent = AccessibilityNodeInfoCompat.obtain(node);

    try {
      do {
        if (current != null) {
          if (visitedNodes.contains(current)) {
            current.recycle();
            parent.recycle();
            return null;
          }
          visitedNodes.add(current);
        }

        current = parent;
        parent = current.getParent();
      } while (parent != null);
    } finally {
      recycleNodes(visitedNodes);
    }

    return current;
  }

  /** Returns the type of the window containing {@code nodeCompat}. */
  public static int getWindowType(AccessibilityNodeInfoCompat nodeCompat) {
    if (nodeCompat == null) {
      return WINDOW_TYPE_NONE;
    }

    AccessibilityWindowInfoCompat windowInfoCompat = getWindow(nodeCompat);
    if (windowInfoCompat == null) {
      return WINDOW_TYPE_NONE;
    }

    if (isPictureInPicture(nodeCompat)) {
      return WINDOW_TYPE_PICTURE_IN_PICTURE;
    }

    int windowType = windowInfoCompat.getType();
    windowInfoCompat.recycle();
    return windowType;
  }

  /** Wrapper for AccessibilityNodeInfoCompat.getWindow() that handles SecurityException. */
  public static @Nullable AccessibilityWindowInfoCompat getWindow(
      AccessibilityNodeInfoCompat node) {
    // This implementation is redundant with getWindow(AccessibilityNodeInfo) because there are no
    // un/wrap() functions for AccessibilityWindowInfoCompat.

    if (node == null) {
      return null;
    }

    try {
      return node.getWindow();
    } catch (SecurityException e) {
      LogUtils.e(TAG, "SecurityException in AccessibilityWindowInfoCompat.getWindow()");
      return null;
    }
  }

  public static @Nullable AccessibilityWindowInfo getWindow(AccessibilityNodeInfo node) {
    if (node == null) {
      return null;
    }

    try {
      return node.getWindow();
    } catch (SecurityException e) {
      LogUtils.e(TAG, "SecurityException in AccessibilityWindowInfo.getWindow()");
      return null;
    }
  }

  /**
   * Returns whether a node can receive focus from focus traversal or touch exploration. One of the
   * following must be true:
   *
   * <ul>
   *   <li>The node is actionable (see {@link #isFocusableOrClickable(AccessibilityNodeInfoCompat)})
   *   <li>The node is a top-level list item (see {@link
   *       #isTopLevelScrollItem(AccessibilityNodeInfoCompat)} and is a speaking node
   * </ul>
   *
   * @param node The node to check.
   * @return {@code true} of the node is accessibility focusable.
   */
  public static boolean isAccessibilityFocusable(AccessibilityNodeInfoCompat node) {
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    try {
      return isFocusableOrClickable(node)
          || (isTopLevelScrollItem(node) && isSpeakingNode(node, null, visitedNodes));
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
    }
  }

  /**
   * Returns whether a node should receive accessibility focus from navigation. This method should
   * never be called recursively, since it traverses up the parent hierarchy on every call.
   *
   * @see #findFocusFromHover(AccessibilityNodeInfoCompat) for touch exploration
   * @see
   *     com.google.android.accessibility.talkback.focusmanagement.NavigationTarget#createNodeFilter(int,
   *     Map) for linear navigation
   */
  public static boolean shouldFocusNode(AccessibilityNodeInfoCompat node) {
    return shouldFocusNode(node, null, true);
  }

  public static boolean shouldFocusNode(
      final AccessibilityNodeInfoCompat node,
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache) {
    return shouldFocusNode(node, speakingNodeCache, true);
  }

  public static boolean shouldFocusNode(
      final AccessibilityNodeInfoCompat node,
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
      boolean checkChildren) {
    if (node == null) {
      LogUtils.v(TAG, "Don't focus, node=null");
      return false;
    }
    // Inside views that support web navigation, we delegate focus to the view itself and
    // assume that it navigates to and focuses the correct elements.
    if (WebInterfaceUtils.supportsWebActions(node)) {
      // In history, we loosen the "visibility" check for web element: A web node can be focused
      // even if it's not visibleToUser(). However we should hold the baseline that if the WebView
      // container is not visible, we should not focus on its descendants.
      AccessibilityNodeInfoCompat webViewContainer =
          WebInterfaceUtils.ascendToWebViewContainer(node);
      try {
        return webViewContainer != null && webViewContainer.isVisibleToUser();
      } finally {
        recycleNodes(webViewContainer);
      }
    }

    if (!isVisible(node)) {
      logShouldFocusNode(checkChildren, "Don't focus, %s is not visible", printId(node));
      return false;
    }

    if (isPictureInPicture(node)) {
      // For picture-in-picture, allow focusing the root node, and any app controls inside the
      // pic-in-pic window.
      return true;
    } else {
      // Reject all non-leaf nodes that have the same bounds as the window.
      if (areBoundsIdenticalToWindow(node) && node.getChildCount() > 0) {
        logShouldFocusNode(
            checkChildren,
            "Don't focus, %s bounds are same as window root node bounds and node has children",
            printId(node));
        return false;
      }
    }

    HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    try {
      // This checks if a node is clickable, focusable, screen reader focusable, or a direct
      // spekaing child of a scrollable container.
      boolean accessibilityFocusable =
          isFocusableOrClickable(node)
              || (isTopLevelScrollItem(node) && isSpeakingNode(node, null, visitedNodes));

      if (!checkChildren) {
        // End of the line. Don't check children and don't allow any recursion.
        // checkChildren is only false in the shouldFocusNode call below. This is to avoid
        // repetitive checks down the tree when looking up at the ancestors.
        LogUtils.d(
            TAG, "checkChildren=false and isAccessibilityFocusable=%s", accessibilityFocusable);
        return accessibilityFocusable;
      }

      // A node that is deemed accessibility focusable shouldn't actually get focus if it has
      // nothing to speak. For example, a view may be focusable, but if it has no text and all of
      // its children are clickable, focus should go on each child individually and not on this
      // view.
      // Note: This is redundant for nodes that pass isSpeakingNode above
      // Note: A special case exists for unlabeled buttons which otherwise wouldn't get focus.
      if (accessibilityFocusable) {
        AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        visitedNodes.clear();
        // TODO: This may still result in focusing non-speaking nodes, but it
        // won't prevent unlabeled buttons from receiving focus.
        if (!hasVisibleChildren(node)) {
          logShouldFocusNode(
              checkChildren, "Focus, %s is focusable and has no visible children", printId(node));
          return true;
        } else if (isSpeakingNode(node, speakingNodeCache, visitedNodes)) {
          logShouldFocusNode(
              checkChildren, "Focus, %s is focusable and has something to speak", printId(node));
          return true;
        } else {
          logShouldFocusNode(
              checkChildren,
              "Don't focus, %s is focusable but has nothing to speak",
              printId(node));
          return false;
        }
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
    }

    // At this point, the node is an unfocusable target.
    // If it has no focusable ancestors, but it still has text, then it should receive focus and be
    // read aloud.
    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return shouldFocusNode(node, speakingNodeCache, false);
          }
        };

    if (!hasMatchingAncestor(node, filter) && (hasText(node) || hasStateDescription(node))) {
      logShouldFocusNode(
          checkChildren, "Focus %s has text and no focusable ancestors", printId(node));
      return true;
    }

    logShouldFocusNode(
        checkChildren, "Don't focus %s, failed all focusability tests", printId(node));
    return false;
  }

  @FormatMethod
  private static void logShouldFocusNode(
      boolean checkChildren, @FormatString String format, Object... args) {
    // When shouldFocusNode calls itself, the logs get inundated by unnecessary info about the
    // ancestors. So only log when checkChildren is true.
    if (checkChildren) {
      // Show debug logs for #shouldFocusNode. Verbose logs will show for #isSpeakingNode
      LogUtils.v(TAG, format, args);
    }
  }

  public static boolean isPictureInPicture(AccessibilityNodeInfoCompat node) {
    return isPictureInPicture(node.unwrap());
  }

  public static boolean isPictureInPicture(AccessibilityNodeInfo node) {
    return node != null && AccessibilityWindowInfoUtils.isPictureInPicture(getWindow(node));
  }

  /**
   * Returns the node that should receive focus from hover by starting from the touched node and
   * calling {@link #shouldFocusNode} at each level of the view hierarchy and exclude WebView
   * container node.
   */
  public static AccessibilityNodeInfoCompat findFocusFromHover(
      AccessibilityNodeInfoCompat touched) {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
        touched, FILTER_SHOULD_FOCUS_EXCEPT_WEB_VIEW);
  }

  /**
   * Returns whether a node can be spoken.
   *
   * <p>A node should be spoken if it has text, is checkable, or has children that should be spoken
   * but can't be focused themselves. This method can call itself recursively through {@link
   * #hasNonActionableSpeakingChildren}.
   *
   * <p>Note: This is called in the context of looking for a a11y focusable node through {@link
   * #shouldFocusNode} and {@link #isAccessibilityFocusable}
   *
   * @param node the node to check
   * @param speakingNodeCache the cache that holds the speaking results for visited nodes
   * @param visitedNodes the set of nodes that have already been visited
   * @return {@code true} if the node can be spoken
   */
  private static boolean isSpeakingNode(
      AccessibilityNodeInfoCompat node,
      Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
      Set<AccessibilityNodeInfoCompat> visitedNodes) {
    if (speakingNodeCache != null && speakingNodeCache.containsKey(node)) {
      return speakingNodeCache.get(node);
    }

    boolean result = false;
    if (hasText(node)) {
      LogUtils.v(TAG, "Speaking, has text");
      result = true;
    } else if (hasStateDescription(node)) {
      LogUtils.v(TAG, "Speaking, has state description");
      result = true;
    } else if (node.isCheckable()) { // Special case for check boxes.
      LogUtils.v(TAG, "Speaking, is checkable");
      result = true;
    } else if (hasNonActionableSpeakingChildren(node, speakingNodeCache, visitedNodes)) {
      // Special case for containers with non-focusable content. In this case, the container should
      // speak its non-focusable yet speakable content.
      LogUtils.v(TAG, "Speaking, has non-actionable speaking children");
      result = true;
    }

    if (speakingNodeCache != null) {
      speakingNodeCache.put(node, result);
    }

    return result;
  }

  /**
   * Returns whether a node has children that are not actionable/focusable but should be spoken.
   *
   * <p>This is done by ignoring any children nodes that are actionable/focusable, and checking the
   * remaining for speaking ability.
   *
   * @param node the node to check
   * @param speakingNodeCache the cache that holds the speaking results for visited nodes
   * @param visitedNodes the set of nodes that have already been visited. Caller must recycle.
   * @return {@code true} if the node has children that are speaking
   */
  private static boolean hasNonActionableSpeakingChildren(
      AccessibilityNodeInfoCompat node,
      Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
      Set<AccessibilityNodeInfoCompat> visitedNodes) {
    final int childCount = node.getChildCount();

    AccessibilityNodeInfoCompat child;

    for (int i = 0; i < childCount; i++) {
      child = node.getChild(i);

      if (child == null) {
        LogUtils.v(TAG, "Child %d is null, skipping it", i);
        continue;
      }

      if (!visitedNodes.add(child)) {
        child.recycle();
        return false;
      }

      // Ignore invisible nodes.
      if (!isVisible(child)) {
        LogUtils.v(TAG, "Child %d, %s is invisible, skipping it", i, printId(node));
        continue;
      }

      // Ignore focusable nodes
      if (isFocusableOrClickable(child)) {
        LogUtils.v(TAG, "Child %d, %s is focusable or clickable, skipping it", i, printId(node));
        continue;
      }

      // Ignore top level scroll items that 1) are speaking and 2) have non-clickable parents. This
      // means that a scrollable container that is clickable should get focus before its children.
      if ((isTopLevelScrollItem(child) && isSpeakingNode(child, speakingNodeCache, visitedNodes))
          && !(isClickable(node) || isLongClickable(node))) {

        LogUtils.v(TAG, "Child %d, %s is a top level scroll item, skipping it", i, printId(node));
        continue;
      }

      // Recursively check non-focusable child nodes.
      if (isSpeakingNode(child, speakingNodeCache, visitedNodes)) {
        LogUtils.v(TAG, "Does have actionable speaking children (child %d, %s)", i, printId(node));
        return true;
      }
    }

    LogUtils.v(TAG, "Does not have non-actionable speaking children");
    return false;
  }

  private static boolean hasVisibleChildren(AccessibilityNodeInfoCompat node) {
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child != null) {
        try {
          if (child.isVisibleToUser()) {
            return true;
          }
        } finally {
          child.recycle();
        }
      }
    }

    return false;
  }

  public static int countVisibleChildren(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return 0;
    }
    int childCount = node.getChildCount();
    int childVisibleCount = 0;
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child != null) {
        try {
          if (child.isVisibleToUser()) {
            ++childVisibleCount;
          }
        } finally {
          child.recycle();
        }
      }
    }
    return childVisibleCount;
  }

  /**
   * Returns whether a node is actionable. That is, the node supports one of the following actions:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isClickable()}
   *   <li>{@link AccessibilityNodeInfoCompat#isFocusable()}
   *   <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}
   * </ul>
   *
   * This parities the system method View#isActionableForAccessibility(), which was added in
   * JellyBean.
   *
   * @param node The node to examine.
   * @return {@code true} if node is actionable.
   */
  public static boolean isActionableForAccessibility(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    // Nodes that are clickable are always actionable.
    if (isClickable(node) || isLongClickable(node)) {
      return true;
    }

    if (node.isFocusable()) {
      return true;
    }

    if (WebInterfaceUtils.hasNativeWebContent(node)) {
      return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS);
    }

    return supportsAnyAction(
        node,
        AccessibilityNodeInfoCompat.ACTION_FOCUS,
        AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
  }

  public static boolean isSelfOrAncestorFocused(AccessibilityNodeInfoCompat node) {
    return node != null
        && (node.isAccessibilityFocused()
            || hasMatchingAncestor(
                node,
                new Filter<AccessibilityNodeInfoCompat>() {
                  @Override
                  public boolean accept(AccessibilityNodeInfoCompat node) {
                    return (node != null) && node.isAccessibilityFocused();
                  }
                }));
  }

  /**
   * Returns whether a node is clickable. That is, the node supports at least one of the following:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isClickable()}
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_CLICK}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is clickable.
   */
  public static boolean isClickable(AccessibilityNodeInfoCompat node) {
    return node != null
        && (node.isClickable()
            || supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK));
  }

  /**
   * Returns whether a node is long clickable. That is, the node supports at least one of the
   * following:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_LONG_CLICK}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is long clickable.
   */
  public static boolean isLongClickable(AccessibilityNodeInfoCompat node) {
    return node != null
        && (node.isLongClickable()
            || supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK));
  }

  /**
   * Returns whether the node is focusable. That is, the node supports at least one of the
   * following:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#isFocusable()}
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_FOCUS}
   * </ul>
   */
  public static boolean isFocusable(@Nullable AccessibilityNodeInfoCompat node) {
    return node != null
        && (node.isFocusable()
            || supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS));
  }

  /**
   * Returns whether a node is expandable. That is, the node supports the following action:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_EXPAND}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is expandable.
   */
  public static boolean isExpandable(AccessibilityNodeInfoCompat node) {
    return node != null && supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_EXPAND);
  }

  /**
   * Returns whether a node is collapsible. That is, the node supports the following action:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_COLLAPSE}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is collapsible.
   */
  public static boolean isCollapsible(AccessibilityNodeInfoCompat node) {
    return node != null && supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_COLLAPSE);
  }

  /**
   * Returns whether a node can be dismissed by the user. the node supports the following action:
   *
   * <ul>
   *   <li>{@link AccessibilityNodeInfoCompat#ACTION_DISMISS}
   * </ul>
   *
   * @param node The node to examine.
   * @return {@code true} if node is dismissible.
   */
  public static boolean isDismissible(AccessibilityNodeInfoCompat node) {
    return node != null && supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_DISMISS);
  }

  /** Caller retains ownership of source node. */
  public static boolean isKeyboard(AccessibilityEvent event, AccessibilityNodeInfoCompat source) {
    return isKeyboard(event, source.unwrap());
  }

  /** Caller retains ownership of source node. */
  public static boolean isKeyboard(AccessibilityEvent event, AccessibilityNodeInfo source) {

    if (source == null) {
      return false;
    }
    AccessibilityWindowInfo window = getWindow(source);
    if (window == null) {
      return false;
    }
    boolean isKeyboard = (window.getType() == AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD);
    window.recycle();
    return isKeyboard;
  }

  /**
   * Check whether a given node has a scrollable ancestor.
   *
   * @param node The node to examine.
   * @return {@code true} if one of the node's ancestors is scrollable.
   */
  public static boolean hasMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return false;
    }

    final AccessibilityNodeInfoCompat result = getMatchingAncestor(node, filter);
    if (result == null) {
      return false;
    }

    result.recycle();
    return true;
  }

  /**
   * Check whether a given node is a key from the Pin Password keyboard used to unlock the screen.
   *
   * @param node The node to examine.
   * @return {@code true} if the node is a key from the Pin Password keyboard used to unlock the
   *     screen.
   */
  // TODO: Find a better way to identify that its a key for PIN password.
  public static boolean isPinKey(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    String viewIdResourceName = node.getViewIdResourceName();
    return !TextUtils.isEmpty(viewIdResourceName)
        && PIN_KEY_PATTERN.matcher(viewIdResourceName).matches();
  }

  /** Returns whether the node is the Pin edit field at unlock screen. */
  public static boolean isPinEntry(AccessibilityNodeInfo node) {
    return (node != null) && VIEW_ID_RESOURCE_NAME_PIN_ENTRY.equals(node.getViewIdResourceName());
  }

  /**
   * Check whether a given node or any of its ancestors matches the given filter.
   *
   * @param node The node to examine.
   * @param filter The filter to match the nodes against.
   * @return {@code true} if the node or one of its ancestors matches the filter.
   */
  public static boolean isOrHasMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return false;
    }

    final AccessibilityNodeInfoCompat result = getSelfOrMatchingAncestor(node, filter);
    if (result == null) {
      return false;
    }

    result.recycle();
    return true;
  }

  /** Check whether a given node has any descendant matching a given filter. */
  public static boolean hasMatchingDescendant(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return false;
    }

    final AccessibilityNodeInfoCompat result = getMatchingDescendant(node, filter);
    if (result == null) {
      return false;
    }

    result.recycle();
    return true;
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching ancestor.
   * Returns {@code null} if no nodes match.
   */
  public static @Nullable AccessibilityNodeInfoCompat getSelfOrMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    if (filter.accept(node)) {
      return AccessibilityNodeInfoCompat.obtain(node);
    }

    return getMatchingAncestor(node, filter);
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching ancestor,
   * ending the ancestor search once it reaches {@code end}. The search is inclusive of {@code node}
   * but exclusive of {@code end}. If {@code node} equals {@code end}, then {@code node} is an
   * eligible match. Returns {@code null} if no nodes match.
   */
  public static @Nullable AccessibilityNodeInfoCompat getSelfOrMatchingAncestor(
      AccessibilityNodeInfoCompat node,
      AccessibilityNodeInfoCompat end,
      Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    if (filter.accept(node)) {
      return AccessibilityNodeInfoCompat.obtain(node);
    }

    return getMatchingAncestor(node, end, filter);
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching descendant.
   * Returns {@code null} if no nodes match.
   */
  public static @Nullable AccessibilityNodeInfoCompat getSelfOrMatchingDescendant(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    if (filter.accept(node)) {
      return AccessibilityNodeInfoCompat.obtain(node);
    }

    return getMatchingDescendant(node, filter);
  }

  /** Processes subtree of root by {@code filter}. */
  public static void processSubtree(
      AccessibilityNodeInfoCompat root, Filter<AccessibilityNodeInfoCompat> filter) {

    AccessibilityNodeInfoUtils.getSelfOrMatchingDescendant(
        root,
        new Filter.NodeCompat(
            (node) -> {
              filter.accept(node);
              return false; // Force search to traverse whole subtree.
            }));
  }

  /**
   * Determines whether the two nodes are in the same branch; that is, they are equal or one is the
   * ancestor of the other.
   */
  public static boolean areInSameBranch(
      @Nullable final AccessibilityNodeInfoCompat node1,
      @Nullable final AccessibilityNodeInfoCompat node2) {
    if (node1 != null && node2 != null) {
      // Same node?
      if (node1.equals(node2)) {
        return true;
      }

      // Is node1 an ancestor of node2?
      Filter<AccessibilityNodeInfoCompat> matchNode1 =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return node != null && node.equals(node1);
            }
          };
      if (AccessibilityNodeInfoUtils.hasMatchingAncestor(node2, matchNode1)) {
        return true;
      }

      // Is node2 an ancestor of node1?
      Filter<AccessibilityNodeInfoCompat> matchNode2 =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return node != null && node.equals(node2);
            }
          };
      if (AccessibilityNodeInfoUtils.hasMatchingAncestor(node1, matchNode2)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the first ancestor of {@code node} that matches the {@code filter}. Returns {@code
   * null} if no nodes match.
   */
  public static @Nullable AccessibilityNodeInfoCompat getMatchingAncestor(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    return getMatchingAncestor(node, null, filter);
  }

  /**
   * Returns the first ancestor of {@code node} that matches the {@code filter}, terminating the
   * search once it reaches {@code end}. The search is exclusive of both {@code node} and {@code
   * end}. Returns {@code null} if no nodes match.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the returned node.
   */
  private static @Nullable AccessibilityNodeInfoCompat getMatchingAncestor(
      AccessibilityNodeInfoCompat node,
      AccessibilityNodeInfoCompat end,
      Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    final HashSet<AccessibilityNodeInfoCompat> ancestors = new HashSet<>();

    try {
      ancestors.add(AccessibilityNodeInfoCompat.obtain(node));
      node = node.getParent();

      while (node != null) {
        if (!ancestors.add(node)) {
          // Already seen this node, so abort!

          // Return null if node is same object with element inside ancestors. This will skip to
          // recyce node to avoid crash.
          for (AccessibilityNodeInfoCompat element : ancestors) {
            if (node == element) {
              return null;
            }
          }

          node.recycle();
          return null;
        }

        if (end != null && node.equals(end)) {
          // Reached the end node, so abort!
          // Don't recycle the node here, it was added to ancestors and will be recycled.
          return null;
        }

        if (filter.accept(node)) {
          // Send a copy since node gets recycled.
          return AccessibilityNodeInfoCompat.obtain(node);
        }

        node = node.getParent();
      }
    } finally {
      recycleNodes(ancestors);
    }

    return null;
  }

  /**
   * Returns the number of ancestors matching the given filter. Does not include the current node in
   * the count, even if it matches the filter. If there is a cycle in the ancestor hierarchy, then
   * this method will return 0.
   */
  public static int countMatchingAncestors(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return 0;
    }

    final HashSet<AccessibilityNodeInfoCompat> ancestors = new HashSet<>();
    int matchingAncestors = 0;

    try {
      ancestors.add(AccessibilityNodeInfoCompat.obtain(node));
      node = node.getParent();

      while (node != null) {
        if (!ancestors.add(node)) {
          // Already seen this node, so abort!
          node.recycle();
          return 0;
        }

        if (filter.accept(node)) {
          matchingAncestors++;
        }

        node = node.getParent();
      }
    } finally {
      recycleNodes(ancestors);
    }

    return matchingAncestors;
  }

  /**
   * Returns the first child (by depth-first search) of {@code node} that matches the {@code
   * filter}. Returns {@code null} if no nodes match. The caller is responsible for recycling all
   * nodes in {@code visitedNodes} and the node returned by this method, if non-{@code null}.
   */
  private static @Nullable AccessibilityNodeInfoCompat getMatchingDescendant(
      AccessibilityNodeInfoCompat node,
      Filter<AccessibilityNodeInfoCompat> filter,
      HashSet<AccessibilityNodeInfoCompat> visitedNodes) {
    if (node == null) {
      return null;
    }

    if (visitedNodes.contains(node)) {
      return null;
    } else {
      visitedNodes.add(AccessibilityNodeInfoCompat.obtain(node));
    }

    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);

      if (child == null) {
        continue;
      }

      if (filter.accept(child)) {
        return child; // child was already obtained by node.getChild().
      }

      try {
        AccessibilityNodeInfoCompat childMatch = getMatchingDescendant(child, filter, visitedNodes);
        if (childMatch != null) {
          return childMatch;
        }
      } finally {
        child.recycle();
      }
    }

    return null;
  }

  private static @Nullable AccessibilityNodeInfoCompat getMatchingDescendant(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    final HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    try {
      return getMatchingDescendant(node, filter, visitedNodes);
    } finally {
      recycleNodes(visitedNodes);
    }
  }

  /** Returns all descendants that match filter. Caller must recycle returned nodes. */
  public static List<AccessibilityNodeInfoCompat> getMatchingDescendantsOrRoot(
      @Nullable AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }
    HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    List<AccessibilityNodeInfoCompat> matches = new ArrayList<>();
    try {
      getMatchingDescendants(node, filter, /* matchRoot= */ true, visitedNodes, matches);
      return matches;
    } finally {
      recycleNodes(visitedNodes);
    }
  }

  /**
   * Collects all descendants that match filter, into matches.
   *
   * @param node The root node to start searching.
   * @param filter The filter to match the nodes against.
   * @param matchRoot Flag that allows match with root node.
   * @param visitedNodes The set of nodes already visited, for protection against loops. This will
   *     be modified. Caller is responsible to recycle the nodes.
   * @param matches The list of nodes matching filter. This will be appended to. Caller is
   *     responsible to recycle this.
   */
  private static void getMatchingDescendants(
      @Nullable AccessibilityNodeInfoCompat node,
      Filter<AccessibilityNodeInfoCompat> filter,
      boolean matchRoot,
      Set<AccessibilityNodeInfoCompat> visitedNodes,
      List<AccessibilityNodeInfoCompat> matches) {

    if (node == null) {
      return;
    }

    // Update visited nodes.
    if (visitedNodes.contains(node)) {
      return;
    } else {
      visitedNodes.add(AccessibilityNodeInfoCompat.obtain(node)); // Caller must recycle
    }

    // If node matches filter... collect node.
    if (matchRoot && filter.accept(node)) {
      matches.add(AccessibilityNodeInfoCompat.obtain(node)); // Caller must recycle
    }

    // For each child of node...
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i); // Must recycle
      if (child == null) {
        continue;
      }
      try {
        // Recurse on child.
        getMatchingDescendants(child, filter, /* matchRoot= */ true, visitedNodes, matches);
      } finally {
        child.recycle();
      }
    }
  }

  /**
   * Check whether a given node is scrollable.
   *
   * @param node The node to examine.
   * @return {@code true} if the node is scrollable.
   */
  public static boolean isScrollable(AccessibilityNodeInfoCompat node) {
    // In some cases node#isScrollable lies. (Notably, some nodes that correspond to WebViews claim
    // to be scrollable, but do not support any scroll actions. This seems to stem from a bug in the
    // translation from the DOM to the AccessibilityNodeInfo.) To avoid labeling views that don't
    // support scrolling (e.g. REFERTO), check for the explicit presence of
    // AccessibilityActions.
    if (BuildVersionUtils.isM() || BuildVersionUtils.isAtLeastN()) {
      return supportsAnyAction(
          node,
          AccessibilityAction.ACTION_SCROLL_FORWARD,
          AccessibilityAction.ACTION_SCROLL_BACKWARD,
          AccessibilityAction.ACTION_SCROLL_DOWN,
          AccessibilityAction.ACTION_SCROLL_UP,
          AccessibilityAction.ACTION_SCROLL_RIGHT,
          AccessibilityAction.ACTION_SCROLL_LEFT);
    } else {
      // Directional scrolling is not available pre-M.
      return supportsAnyAction(
          node,
          AccessibilityAction.ACTION_SCROLL_FORWARD,
          AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
  }

  /**
   * Returns whether the specified node has text. For the purposes of this check, any node with a
   * CollectionInfo is considered to not have text since its text and content description are used
   * only for collection transitions.
   *
   * @param node The node to check.
   * @return {@code true} if the node has text.
   */
  private static boolean hasText(AccessibilityNodeInfoCompat node) {
    return node != null
        && node.getCollectionInfo() == null
        && (!TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))
            || !TextUtils.isEmpty(node.getContentDescription())
            || !TextUtils.isEmpty(node.getHintText()));
  }

  /**
   * Returns whether the specified node has state description.
   *
   * @param node The node to check.
   * @return {@code true} if the node has state description.
   */
  private static boolean hasStateDescription(@Nullable AccessibilityNodeInfoCompat node) {
    return node != null
        && (!TextUtils.isEmpty(node.getStateDescription())
            || node.isCheckable()
            || hasValidRangeInfo(node));
  }

  /**
   * Returns if a node is focusable or clickable.
   *
   * <p>This is used in {@link #shouldFocusNode} and {@link #isAccessibilityFocusable}
   *
   * @param node the node to check
   * @return {@code true} if the node is focusable or clickable
   */
  private static boolean isFocusableOrClickable(AccessibilityNodeInfoCompat node) {
    return (node != null)
        && isVisible(node)
        && (node.isScreenReaderFocusable() || isActionableForAccessibility(node));
  }

  /**
   * Determines whether a node is a top-level item in a scrollable container.
   *
   * @param node The node to test.
   * @return {@code true} if {@code node} is a top-level item in a scrollable container.
   */
  public static boolean isTopLevelScrollItem(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    if (!isVisible(node)) {
      return false;
    }

    AccessibilityNodeInfoCompat parent = null;
    AccessibilityNodeInfoCompat grandparent = null;

    try {
      parent = node.getParent();
      if (parent == null) {
        // Not a child node of anything.
        return false;
      }

      // Certain scrollable views in M's Android TV SetupWraith are permanently broken and
      // won't ever be fixed because the setup wizard is bundled. This affects <= M only.
      if (!BuildVersionUtils.isAtLeastN() && FILTER_BROKEN_LISTS_TV_M.accept(parent)) {
        return false;
      }

      // Drop down lists (spinners) are not included to retain the old behavior of focusing on
      // the spinner itself rather than on the single visible item.
      // A spinner being scrollable is disingenuous since the scrollable list inside isn't exposed
      // without interaction.
      // TODO: Remove this check?
      if (Role.getRole(parent) == Role.ROLE_DROP_DOWN_LIST) {
        return false;
      }

      // A node with a scrollable parent is a top level scroll item.
      if (isScrollable(parent)) {
        return true;
      }

      @Role.RoleName int parentRole = Role.getRole(parent);
      // Note that ROLE_DROP_DOWN_LIST(Spinner) is not accepted.
      // RecyclerView is classified as a list or grid based on its CollectionInfo.
      // These parents may not be scrollable in some cases, like if the list is too short to be
      // scrolled, but their children should still be considered top level scroll items.
      return parentRole == Role.ROLE_LIST
          || parentRole == Role.ROLE_GRID
          || parentRole == Role.ROLE_SCROLL_VIEW
          || parentRole == Role.ROLE_HORIZONTAL_SCROLL_VIEW
          || nodeMatchesAnyClassByType(parent, CLASS_TOUCHWIZ_TWADAPTERVIEW);
    } finally {
      recycleNodes(parent, grandparent);
    }
  }

  public static boolean hasAncestor(
      AccessibilityNodeInfoCompat node, final AccessibilityNodeInfoCompat targetAncestor) {
    if (node == null || targetAncestor == null) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return targetAncestor.equals(node);
          }
        };

    AccessibilityNodeInfoCompat foundAncestor = getMatchingAncestor(node, filter);
    if (foundAncestor != null) {
      foundAncestor.recycle();
      return true;
    }

    return false;
  }

  public static boolean hasDescendant(
      AccessibilityNodeInfoCompat node, final AccessibilityNodeInfoCompat targetDescendant) {
    if (node == null || targetDescendant == null) {
      return false;
    }

    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return targetDescendant.equals(node);
          }
        };

    AccessibilityNodeInfoCompat foundAncestor = getMatchingDescendant(node, filter);
    if (foundAncestor != null) {
      foundAncestor.recycle();
      return true;
    }

    return false;
  }

  /**
   * Determines if the generating class of an {@link AccessibilityNodeInfoCompat} matches a given
   * {@link Class} by type.
   *
   * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by the accessibility
   *     framework.
   * @param referenceClass A {@link Class} to match by type or inherited type.
   * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object matches the {@link
   *     Class} by type or inherited type, {@code false} otherwise.
   */
  public static boolean nodeMatchesClassByType(
      AccessibilityNodeInfoCompat node, Class<?> referenceClass) {
    if ((node == null) || (referenceClass == null)) {
      return false;
    }

    // Attempt to take a shortcut.
    final CharSequence nodeClassName = node.getClassName();
    if (TextUtils.equals(nodeClassName, referenceClass.getName())) {
      return true;
    }

    return ClassLoadingCache.checkInstanceOf(nodeClassName, referenceClass);
  }

  /**
   * Determines if the generating class of an {@link AccessibilityNodeInfoCompat} matches any of the
   * given {@link Class}es by type.
   *
   * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by the accessibility
   *     framework.
   * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object matches the {@link
   *     Class} by type or inherited type, {@code false} otherwise.
   * @param referenceClasses A variable-length list of {@link Class} objects to match by type or
   *     inherited type.
   */
  public static boolean nodeMatchesAnyClassByType(
      AccessibilityNodeInfoCompat node, Class<?>... referenceClasses) {
    if (node == null) {
      return false;
    }

    for (Class<?> referenceClass : referenceClasses) {
      if (ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClass)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines if the class of an {@link AccessibilityNodeInfoCompat} matches a given {@link Class}
   * by package and name.
   *
   * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by the accessibility
   *     framework.
   * @param referenceClassName A class name to match.
   * @return {@code true} if the {@link AccessibilityNodeInfoCompat} matches the class name.
   */
  public static boolean nodeMatchesClassByName(
      AccessibilityNodeInfoCompat node, CharSequence referenceClassName) {
    return node != null
        && ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClassName);
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(Collection<AccessibilityNodeInfoCompat> nodes) {
    if (nodes == null) {
      return;
    }

    for (AccessibilityNodeInfoCompat node : nodes) {
      if (node != null) {
        node.recycle();
      }
    }

    nodes.clear();
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(@Nullable AccessibilityNodeInfo... nodes) {
    if (nodes == null) {
      return;
    }

    for (AccessibilityNodeInfo node : nodes) {
      if (node != null) {
        node.recycle();
      }
    }
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(@Nullable AccessibilityNodeInfoCompat... nodes) {
    if (nodes == null) {
      return;
    }

    for (AccessibilityNodeInfoCompat node : nodes) {
      if (node != null) {
        node.recycle();
      }
    }
  }

  /**
   * Returns {@code true} if the node supports at least one of the specified actions. This method
   * supports actions introduced in API level 21 and later. However, it does not support bitmasks.
   *
   * @param node The node to check
   * @param actions The actions to check
   * @return {@code true} if at least one action is supported
   */
  // TODO: Use A11yActionCompat once AccessibilityActionCompat#equals is overridden
  public static boolean supportsAnyAction(
      AccessibilityNodeInfoCompat node, AccessibilityAction... actions) {
    if (node == null) {
      return false;
    }
    // Unwrap the node and compare AccessibilityActions because AccessibilityActions, unlike
    // AccessibilityActionCompats, are static (so checks for equality work correctly).
    final List<AccessibilityAction> supportedActions = node.unwrap().getActionList();

    for (AccessibilityAction action : actions) {
      if (supportedActions.contains(action)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns {@code true} if the node supports at least one of the specified actions. To check
   * whether a node supports multiple actions, combine them using the {@code |} (logical OR)
   * operator.
   *
   * <p>Note: this method will check against the getActions() method of AccessibilityNodeInfo, which
   * will not contain information for actions introduced in API level 21 or later.
   *
   * @param node The node to check.
   * @param actions The actions to check.
   * @return {@code true} if at least one action is supported.
   */
  // TODO: Remove this method once AccessibilityActionCompat#equals is overridden
  public static boolean supportsAnyAction(AccessibilityNodeInfoCompat node, int... actions) {
    if (node != null) {
      final int supportedActions = node.getActions();

      for (int action : actions) {
        if ((supportedActions & action) == action) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns {@code true} if the node supports the specified action. This method supports actions
   * introduced in API level 21 and later. However, it does not support bitmasks.
   */
  public static boolean supportsAction(AccessibilityNodeInfoCompat node, int action) {
    // New actions in >= API 21 won't appear in getActions() but in getActionList().
    // On Lollipop+ devices, pre-API 21 actions will also appear in getActionList().
    List<AccessibilityActionCompat> actions = node.getActionList();
    int size = actions.size();
    for (int i = 0; i < size; ++i) {
      AccessibilityActionCompat actionCompat = actions.get(i);
      if (actionCompat.getId() == action) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the result of applying a filter using breadth-first traversal.
   *
   * @param node The root node to traverse from.
   * @param filter The filter to satisfy.
   * @return The first node reached via BFS traversal that satisfies the filter.
   */
  public static AccessibilityNodeInfoCompat searchFromBfs(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    return searchFromBfs(node, filter, /* filterToSkip= */ null);
  }

  /**
   * Returns the result of applying a filter using breadth-first traversal. It allows skip nodes to
   * speed up the BFS traversal.
   *
   * @param node The root node to traverse from.
   * @param filter The filter to satisfy.
   * @param filterToSkip The filter for skipping nodes, all childs under the node will be skipped.
   * @return The first node reached via BFS traversal that satisfies the filter.
   */
  public static AccessibilityNodeInfoCompat searchFromBfs(
      AccessibilityNodeInfoCompat node,
      Filter<AccessibilityNodeInfoCompat> filter,
      @Nullable Filter<AccessibilityNodeInfoCompat> filterToSkip) {
    if (node == null) {
      return null;
    }

    final ArrayDeque<AccessibilityNodeInfoCompat> queue = new ArrayDeque<>();
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();

    queue.add(AccessibilityNodeInfoCompat.obtain(node));

    try {
      while (!queue.isEmpty()) {
        final AccessibilityNodeInfoCompat item = queue.removeFirst();
        visitedNodes.add(item);

        if (filterToSkip != null && filterToSkip.accept(item)) {
          item.recycle();
          continue;
        }

        if (filter.accept(item)) {
          return item;
        }

        final int childCount = item.getChildCount();

        for (int i = 0; i < childCount; i++) {
          final AccessibilityNodeInfoCompat child = item.getChild(i);

          if (child != null && !visitedNodes.contains(child)) {
            queue.addLast(child);
          }
        }
        item.recycle();
      }
    } finally {
      while (!queue.isEmpty()) {
        queue.removeFirst().recycle();
      }
    }

    return null;
  }

  /** Safely obtains a copy of node. Caller must recycle returned node info. */
  public static AccessibilityNodeInfoCompat obtain(AccessibilityNodeInfoCompat node) {
    return (node == null) ? null : AccessibilityNodeInfoCompat.obtain(node);
  }

  /** Safely obtains a copy of node. Caller must recycle returned node info. */
  public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo node) {
    return (node == null) ? null : AccessibilityNodeInfo.obtain(node);
  }

  /**
   * Replaces a node with a refreshed node.
   *
   * @param node A source node which may be stale, and which will be recycled.
   * @return A refreshed node, which the caller must recycle.
   */
  public static AccessibilityNodeInfoCompat replaceWithFreshNode(AccessibilityNodeInfoCompat node) {
    try {
      return AccessibilityNodeInfoUtils.refreshNode(node);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  /**
   * Returns a fresh copy of {@code node} with properties that are less likely to be stale. Returns
   * {@code null} if the node can't be found anymore.
   */
  public static AccessibilityNodeInfoCompat refreshNode(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
    if (nodeCopy.refresh()) {
      return nodeCopy;
    } else {
      nodeCopy.recycle();
      return null;
    }
  }

  /**
   * Returns a fresh copy of node by traversing the given window for a similar node. For example,
   * the node that you want might be in a popup window that has closed and re-opened, causing the
   * accessibility IDs of its views to be different. Note: you must recycle the node that is
   * returned from this method.
   */
  public static AccessibilityNodeInfoCompat refreshNodeFuzzy(
      final AccessibilityNodeInfoCompat node, AccessibilityWindowInfo window) {
    if (window == null || node == null) {
      return null;
    }

    AccessibilityNodeInfo root = AccessibilityWindowInfoUtils.getRoot(window);
    if (root == null) {
      return null;
    }

    Filter<AccessibilityNodeInfoCompat> similarFilter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat other) {
            return other != null
                && TextUtils.equals(
                    AccessibilityNodeInfoUtils.getText(node),
                    AccessibilityNodeInfoUtils.getText(other));
          }
        };

    AccessibilityNodeInfoCompat rootCompat = AccessibilityNodeInfoUtils.toCompat(root);
    try {
      return getMatchingDescendant(rootCompat, similarFilter);
    } finally {
      rootCompat.recycle();
    }
  }

  /**
   * Gets the location of specific range of node text. It returns null if the node doesn't support
   * text location data or the index is incorrect.
   *
   * @param node The node being queried.
   * @param fromCharIndex start index of the queried text range.
   * @param toCharIndex end index of the queried text range.
   */
  @TargetApi(Build.VERSION_CODES.O)
  @Nullable
  public static List<Rect> getTextLocations(
      AccessibilityNodeInfoCompat node, int fromCharIndex, int toCharIndex) {
    return getTextLocations(
        node, AccessibilityNodeInfoUtils.getText(node), fromCharIndex, toCharIndex);
  }

  /**
   * Gets the location of specific range of node {@code text}. It returns null if the node doesn't
   * support text location data or the index is incorrect.
   *
   * @param node The node being queried.
   * @param text The node's text. This is typically the text, but can also be the content
   *     description if the node was not properly created. If the content description is used, its
   *     text location will only be returned if it's visible on the screen.
   * @param fromCharIndex start index of the queried text range.
   * @param toCharIndex end index of the queried text range.
   */
  @TargetApi(Build.VERSION_CODES.O)
  @Nullable
  public static List<Rect> getTextLocations(
      AccessibilityNodeInfoCompat node, CharSequence text, int fromCharIndex, int toCharIndex) {
    if (node == null || !BuildVersionUtils.isAtLeastO()) {
      return null;
    }

    if (fromCharIndex < 0
        || TextUtils.isEmpty(text)
        || !PrimitiveUtils.isInInterval(toCharIndex, fromCharIndex, text.length(), true)) {
      return null;
    }
    AccessibilityNodeInfo info = node.unwrap();
    if (info == null) {
      return null;
    }
    Bundle args = new Bundle();
    args.putInt(
        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, fromCharIndex);
    args.putInt(
        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
        toCharIndex - fromCharIndex);
    if (!info.refreshWithExtraData(
        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)) {
      return null;
    }

    Bundle extras = info.getExtras();
    Parcelable[] data =
        extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
    if (data == null) {
      return null;
    }
    List<Rect> result = new ArrayList<>(data.length);
    for (Parcelable item : data) {
      if (item == null) {
        continue;
      }
      RectF rectF = (RectF) item;
      result.add(
          new Rect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom));
    }
    return result;
  }

  /** Returns true if the node supports text location data. */
  @TargetApi(Build.VERSION_CODES.O)
  public static boolean supportsTextLocation(AccessibilityNodeInfoCompat node) {
    if (!BuildVersionUtils.isAtLeastO() || node == null) {
      return false;
    }
    AccessibilityNodeInfo info = node.unwrap();
    if (info == null) {
      return false;
    }
    List<String> extraData = info.getAvailableExtraData();
    return extraData != null
        && extraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
  }

  /** Helper method that returns {@code true} if the specified node is visible to the user */
  public static boolean isVisible(AccessibilityNodeInfoCompat node) {
    // We need to move focus to invisible node in WebView to scroll it but we don't want to
    // move focus if WebView itself is invisible.
    return node != null
        && (node.isVisibleToUser()
            || (WebInterfaceUtils.isWebContainer(node)
                && Role.getRole(node) != Role.ROLE_WEB_VIEW));
  }

  /** Determines whether the specified node has bounds identical to the bounds of its window. */
  private static boolean areBoundsIdenticalToWindow(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    AccessibilityWindowInfoCompat window = getWindow(node);
    if (window == null) {
      return false;
    }

    Rect windowBounds = new Rect();
    window.getBoundsInScreen(windowBounds);

    Rect nodeBounds = new Rect();
    node.getBoundsInScreen(nodeBounds);

    return windowBounds.equals(nodeBounds);
  }

  /**
   * Returns the node to which the given node's window is anchored, if there is an anchor. Note: you
   * must recycle the node that is returned from this method.
   */
  public static AccessibilityNodeInfoCompat getAnchor(@Nullable AccessibilityNodeInfoCompat node) {
    if (!BuildVersionUtils.isAtLeastN()) {
      return null;
    }

    if (node == null) {
      return null;
    }

    AccessibilityNodeInfo nativeNode = node.unwrap();
    if (nativeNode == null) {
      return null;
    }

    AccessibilityWindowInfo nativeWindow = getWindow(nativeNode);
    if (nativeWindow == null) {
      return null;
    }

    AccessibilityNodeInfo nativeAnchor = nativeWindow.getAnchor();
    if (nativeAnchor == null) {
      return null;
    }

    return AccessibilityNodeInfoUtils.toCompat(nativeAnchor);
  }

  /**
   * Analyses if the edit text has no text.
   *
   * <p>If there is a text field with hint text and no text, {@link
   * AccessibilityNodeInfoUtils.getText()} returns hint text. Hence this method checks for {@link
   * AccessibilityNodeInfo#ACTION_SET_SELECTION} to disregard the hint text.
   */
  public static boolean isEmptyEditTextRegardlessOfHint(
      @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null || !(node.isEditable())) {
      return false;
    }

    if (TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))) {
      return true;
    }
    return !supportsAction(node, AccessibilityNodeInfo.ACTION_SET_SELECTION);
  }

  /**
   * Gets a list of URLs contained within an {@link AccessibilityNodeInfoCompat}.
   *
   * @param node The node that will be searched for links
   * @return A list of {@link SpannableUrl}s from the URLs found within the Node
   */
  public static List<SpannableUrl> getNodeUrls(AccessibilityNodeInfoCompat node) {
    return getNodeClickableElements(
        node, URLSpan.class, input -> SpannableUrl.create(input.first, (URLSpan) input.second));
  }

  /**
   * Gets a list of ClickableSpans paired with the String they span within a node's text.
   *
   * @param node The node that will be searched for spans
   * @return A list of Clickable elements found within the Node.
   */
  public static List<ClickableString> getNodeClickableStrings(AccessibilityNodeInfoCompat node) {
    return getNodeClickableElements(
        node, ClickableSpan.class, input -> ClickableString.create(input.first, input.second));
  }

  /**
   * Gets a list of the clickable elements within a node.
   *
   * @param node The node to get the elements from
   * @param clickableType What type of clickable thing to look for within the node
   * @param clickableElementFn A function taking the visual string representation and the clickable
   *     portion of the clickable element that produces the desired format that will be displayable
   *     to the user
   * @param <E> The displayable format representation of the clickable element
   * @return A list of clickable elements. Empty if there are none.
   */
  private static <E> List<E> getNodeClickableElements(
      AccessibilityNodeInfoCompat node,
      Class<? extends ClickableSpan> clickableType,
      Function<Pair<String, ClickableSpan>, E> clickableElementFn) {
    List<SpannableString> spannableStrings = new ArrayList<>();
    SpannableTraversalUtils.collectSpannableStringsWithTargetSpanInNodeDescriptionTree(
        node, // Root node of description tree
        clickableType, // Target span class
        spannableStrings // List to collect spannable strings
        );

    List<E> clickables = new ArrayList<>(1);
    for (SpannableString spannable : spannableStrings) {
      for (ClickableSpan span : spannable.getSpans(0, spannable.length(), clickableType)) {
        // Child classes may not use #getUrl, so just check that the class is a URLSpan, instead of
        // a child class with "instanceof".
        if ((span.getClass() == URLSpan.class)
            && Strings.isNullOrEmpty(((URLSpan) span).getURL())) {
          continue;
        }
        int start = spannable.getSpanStart(span);
        int end = spannable.getSpanEnd(span);
        if (end > start) {
          char[] chars = new char[end - start];
          spannable.getChars(start, end, chars, 0);
          clickables.add(clickableElementFn.apply(Pair.create(new String(chars), span)));
        }
      }
    }
    return clickables;
  }

  public static int getMovementGranularity(AccessibilityNodeInfoCompat node) {
    // Some nodes in Webview have movement granularities even its content description/text is
    // empty.
    if (WebInterfaceUtils.supportsWebActions(node)
        && TextUtils.isEmpty(node.getContentDescription())
        && TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))) {
      return 0;
    }

    return node.getMovementGranularities();
  }

  @TargetApi(Build.VERSION_CODES.O)
  public static CharSequence getHintText(AccessibilityNodeInfoCompat node) {
    CharSequence hintText = node.getHintText();
    if (TextUtils.isEmpty(hintText)) {
      Bundle bundle = node.getExtras();
      if (bundle != null) {
        // Hint text for WebView.
        hintText = bundle.getCharSequence(HINT_TEXT_KEY);
      }
    }

    return hintText;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for displaying node data

  public static String actionToString(int action) {
    switch (action) {
      case AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS:
        return "ACTION_ACCESSIBILITY_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
        return "ACTION_CLEAR_ACCESSIBILITY_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS:
        return "ACTION_CLEAR_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_CLEAR_SELECTION:
        return "ACTION_CLEAR_SELECTION";
      case AccessibilityNodeInfoCompat.ACTION_CLICK:
        return "ACTION_CLICK";
      case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
        return "ACTION_COLLAPSE";
      case AccessibilityNodeInfoCompat.ACTION_COPY:
        return "ACTION_COPY";
      case AccessibilityNodeInfoCompat.ACTION_CUT:
        return "ACTION_CUT";
      case AccessibilityNodeInfoCompat.ACTION_DISMISS:
        return "ACTION_DISMISS";
      case AccessibilityNodeInfoCompat.ACTION_EXPAND:
        return "ACTION_EXPAND";
      case AccessibilityNodeInfoCompat.ACTION_FOCUS:
        return "ACTION_FOCUS";
      case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
        return "ACTION_LONG_CLICK";
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        return "ACTION_NEXT_AT_MOVEMENT_GRANULARITY";
      case AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT:
        return "ACTION_NEXT_HTML_ELEMENT";
      case AccessibilityNodeInfoCompat.ACTION_PASTE:
        return "ACTION_PASTE";
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        return "ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY";
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT:
        return "ACTION_PREVIOUS_HTML_ELEMENT";
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
        return "ACTION_SCROLL_BACKWARD";
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
        return "ACTION_SCROLL_FORWARD";
      case AccessibilityNodeInfoCompat.ACTION_SELECT:
        return "ACTION_SELECT";
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return "ACTION_SET_SELECTION";
      case AccessibilityNodeInfoCompat.ACTION_SET_TEXT:
        return "ACTION_SET_TEXT";
      default:
        return "(unhandled)";
    }
  }

  /** Caller keeps ownership of node. */
  public static String toStringShort(@Nullable AccessibilityNodeInfo node) {
    return toStringShort(toCompat(node));
  }

  /** Caller keeps ownership of node. */
  public static String toStringShort(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return "null";
    }
    return StringBuilderUtils.joinFields(
        "AccessibilityNodeInfoCompat",
        StringBuilderUtils.optionalInt("id", node.hashCode(), -1),
        StringBuilderUtils.optionalText("class", node.getClassName()),
        StringBuilderUtils.optionalText("package", node.getPackageName()),
        StringBuilderUtils.optionalText("text", AccessibilityNodeInfoUtils.getText(node)),
        StringBuilderUtils.optionalText("description", node.getContentDescription()),
        StringBuilderUtils.optionalText("hint", node.getHintText()),
        StringBuilderUtils.optionalTag("enabled", node.isEnabled()),
        StringBuilderUtils.optionalTag("checkable", node.isCheckable()),
        StringBuilderUtils.optionalTag("checked", node.isChecked()),
        StringBuilderUtils.optionalTag("accessibilityFocused", node.isAccessibilityFocused()),
        StringBuilderUtils.optionalTag("focusable", isFocusable(node)),
        StringBuilderUtils.optionalTag("screenReaderFocusable", node.isScreenReaderFocusable()),
        StringBuilderUtils.optionalTag("focused", node.isFocused()),
        StringBuilderUtils.optionalTag("selected", node.isSelected()),
        StringBuilderUtils.optionalTag("clickable", isClickable(node)),
        StringBuilderUtils.optionalTag("longClickable", isLongClickable(node)),
        StringBuilderUtils.optionalTag("password", node.isPassword()),
        StringBuilderUtils.optionalTag("textEntryKey", isTextEntryKey(node)),
        StringBuilderUtils.optionalTag("scrollable", isScrollable(node)),
        StringBuilderUtils.optionalTag(
            "heading", FeatureSupport.isHeadingWorks() && node.isHeading()),
        StringBuilderUtils.optionalTag("collapsible", isCollapsible(node)),
        StringBuilderUtils.optionalTag("expandable", isExpandable(node)),
        StringBuilderUtils.optionalTag("dismissable", isDismissible(node)),
        StringBuilderUtils.optionalTag("pinKey", isPinKey(node)),
        StringBuilderUtils.optionalTag("pinEntry", isPinEntry(node.unwrap())),
        StringBuilderUtils.optionalTag("visible", node.isVisibleToUser()));
  }

  /** Copied from AccessibilityNodeInfo.java */
  public static @Nullable String getMovementGranularitySymbolicName(int granularity) {
    if (granularity == 0) {
      return null;
    }
    switch (granularity) {
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER:
        return "MOVEMENT_GRANULARITY_CHARACTER";
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD:
        return "MOVEMENT_GRANULARITY_WORD";
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE:
        return "MOVEMENT_GRANULARITY_LINE";
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH:
        return "MOVEMENT_GRANULARITY_PARAGRAPH";
      case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE:
        return "MOVEMENT_GRANULARITY_PAGE";
      default:
        return Integer.toHexString(granularity);
    }
  }

  /**
   * Given a double value, get the int percentage (0 to 100, both inclusive). Only return 0 or 100
   * when percentage is exactly 0 or 100 percent.
   */
  public static int roundForProgressPercent(double percent) {
    if (percent < 0.0f) {
      return 0;
    } else if (percent > 0.0f && percent < 1.0f) {
      return 1;
    } else if (percent > 99.0f && percent < 100.0f) {
      return 99;
    } else if (percent > 100.0f) {
      return 100;
    }
    return (int) Math.round(percent);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for node properties

  /**
   * Returns {@code true} if the height and width of the {@link AccessibilityNodeInfoCompat}'s
   * visible bounds on the screen are greater than a specified number of minimum pixels. This can be
   * used to prune tiny elements or elements off the screen.
   *
   * <p>{@link AccessibilityNodeInfo#isVisibleToUser()} sometimes returns {@code true} for {@link
   * android.webkit.WebView} items off the screen, so this method allows us to better ignore WebView
   * content off the screen.
   *
   * @param node The node that will be checked for a minimum number of pixels on the screen
   * @return {@code true} if the node has at least the number of minimum visible pixels in both
   *     width and height on the screen
   */
  public static boolean hasMinimumPixelsVisibleOnScreen(AccessibilityNodeInfoCompat node) {
    Rect visibleBounds = new Rect();
    node.getBoundsInScreen(visibleBounds);
    return ((Math.abs(visibleBounds.height()) >= MIN_VISIBLE_PIXELS)
        && (Math.abs(visibleBounds.width()) >= MIN_VISIBLE_PIXELS));
  }

  // TODO Remove them when androidx.core library is available.
  /**
   * Returns whether node represents a text entry key that is part of a keyboard or keypad.
   *
   * @param node The node being checked.
   * @return {@code true} if the node is text entry key. library is available.
   */
  public static boolean isTextEntryKey(AccessibilityNodeInfoCompat node) {

    return BuildVersionUtils.isAtLeastQ()
        ? node.unwrap().isTextEntryKey()
        : getBooleanProperty(node, BOOLEAN_MASK_IS_TEXT_ENTRY_KEY);
  }

  /**
   * @param node The node being checked.
   * @param property the property sets in {@code node}
   * @return true if set it successfully.
   */
  private static boolean getBooleanProperty(AccessibilityNodeInfoCompat node, int property) {
    Bundle extras = node.getExtras();
    if (extras == null) {
      return false;
    } else {
      return (extras.getInt(BOOLEAN_PROPERTY_KEY, 0) & property) == property;
    }
  }

  // TODO Remove them when androidx.core library is available.
  /**
   * Sets whether the node represents a text entry key that is part of a keyboard or keypad. We add
   * this method because {@code androidx.core.view.accessibility} is not available in g3.It is only
   * for testing.
   *
   * <p><strong>Note:</strong> Cannot be called from an {@link
   * android.accessibilityservice.AccessibilityService}. This class is made immutable before being
   * delivered to an AccessibilityService.
   *
   * @param node The node being checked.
   * @param isTextEntryKey {@code true} if the node is a text entry key, {@code false} otherwise.
   */
  public static void setTextEntryKey(AccessibilityNodeInfoCompat node, boolean isTextEntryKey) {
    if (BuildVersionUtils.isAtLeastQ()) {
      node.unwrap().setTextEntryKey(isTextEntryKey);
    } else {
      setBooleanProperty(node, BOOLEAN_MASK_IS_TEXT_ENTRY_KEY, isTextEntryKey);
    }
  }

  private static void setBooleanProperty(
      AccessibilityNodeInfoCompat node, int property, boolean value) {
    Bundle extras = node.getExtras();
    if (extras != null) {
      int booleanProperties = extras.getInt(BOOLEAN_PROPERTY_KEY, 0);
      booleanProperties &= ~property;
      booleanProperties |= value ? property : 0;
      extras.putInt(BOOLEAN_PROPERTY_KEY, booleanProperties);
    }
  }

  /**
   * Returns the progress percentage from the node. The value will be in the range [0, 100].
   *
   * @param node The node from which to obtain the progress percentage.
   * @return The progress percentage.
   */
  public static float getProgressPercent(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return 0.0f;
    }

    @Nullable final RangeInfoCompat rangeInfo = node.getRangeInfo();
    if (rangeInfo == null) {
      return 0.0f;
    }

    final float maxProgress = rangeInfo.getMax();
    final float minProgress = rangeInfo.getMin();
    final float currentProgress = rangeInfo.getCurrent();
    final float diffProgress = maxProgress - minProgress;
    if (diffProgress <= 0.0f) {
      logError("getProgressPercent", "Range is invalid. [%f, %f]", minProgress, maxProgress);
      return 0.0f;
    }

    if (currentProgress < minProgress) {
      logError(
          "getProgressPercent",
          "Current percent is out of range. Current: %f Range: [%f, %f]",
          currentProgress,
          minProgress,
          maxProgress);
      return 0.0f;
    }

    if (currentProgress > maxProgress) {
      logError(
          "getProgressPercent",
          "Current percent is out of range. Current: %f Range: [%f, %f]",
          currentProgress,
          minProgress,
          maxProgress);
      return 100.0f;
    }

    final float percent = (currentProgress - minProgress) / diffProgress;
    return (100.0f * Math.max(0.0f, Math.min(1.0f, percent)));
  }

  /**
   * Returns whether the node has valid RangeInfo.
   *
   * @param node The node to check.
   * @return Whether the node has valid RangeInfo.
   */
  public static boolean hasValidRangeInfo(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    @Nullable final RangeInfoCompat rangeInfo = node.getRangeInfo();
    if (rangeInfo == null) {
      return false;
    }

    final float maxProgress = rangeInfo.getMax();
    final float minProgress = rangeInfo.getMin();
    final float currentProgress = rangeInfo.getCurrent();
    final float diffProgress = maxProgress - minProgress;
    return (diffProgress > 0.0f)
        && (currentProgress >= minProgress)
        && (currentProgress <= maxProgress);
  }

  /** Checks whether the given node is still in the window. */
  public static boolean isInWindow(
      AccessibilityNodeInfoCompat checkingNode,
      @Nullable AccessibilityWindowInfoCompat windowInfoCompat) {
    if (windowInfoCompat == null) {
      return false;
    }

    AccessibilityNodeInfoCompat root = windowInfoCompat.getRoot();
    try {
      return hasDescendant(root, checkingNode);
    } finally {
      recycleNodes(root);
    }
  }

  /** Checks whether the given node is still in the window. */
  public static boolean isInWindow(
      AccessibilityNodeInfoCompat checkingNode, @Nullable AccessibilityWindowInfo windowInfo) {
    if (windowInfo == null) {
      return false;
    }

    AccessibilityNodeInfoCompat root = AccessibilityNodeInfoCompat.wrap(windowInfo.getRoot());
    try {
      return hasDescendant(root, checkingNode);
    } finally {
      recycleNodes(root);
    }
  }

  /**
   * Checks whether the given node is a header.
   *
   * <p>On M devices, the return value is always false if the node is an item in ListView or
   * GridView but not in WebView.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the node-argument.
   */
  // TODO On pre-N devices, the framework ListView/GridView will mark non-headers
  // as headers. The workaround should be removed when TalkBack doesn't support android M.
  public static boolean isHeading(AccessibilityNodeInfoCompat node) {
    if (!FeatureSupport.isHeadingWorks()) {
      AccessibilityNodeInfoCompat collectionRoot = getCollectionRoot(node);
      try {
        if (nodeIsListOrGrid(collectionRoot) && !WebInterfaceUtils.isWebContainer(collectionRoot)) {
          return false;
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(collectionRoot);
      }
    }
    return node.isHeading();
  }

  /**
   * Returns a collection root.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the returned node.
   */
  public static @Nullable AccessibilityNodeInfoCompat getCollectionRoot(
      AccessibilityNodeInfoCompat node) {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_COLLECTION);
  }

  /**
   * Checks if given node is ListView or GirdView.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the node-argument.
   */
  public static boolean nodeIsListOrGrid(@Nullable AccessibilityNodeInfoCompat node) {
    return nodeMatchesAnyClassName(node, CLASS_LISTVIEW, CLASS_GRIDVIEW);
  }

  private static boolean nodeMatchesAnyClassName(
      @Nullable AccessibilityNodeInfoCompat node, CharSequence... classNames) {
    if (node == null || node.getClassName() == null || classNames == null) {
      return false;
    }

    for (CharSequence name : classNames) {
      if (TextUtils.equals(node.getClassName(), name)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Represents a {@link ClickableSpan} and the string it spans to reduce the effort of downstream
   * consumers; getting the spanned string is non-trivial.
   */
  @AutoValue
  public abstract static class ClickableString {
    public static ClickableString create(String string, ClickableSpan clickableSpan) {
      return new AutoValue_AccessibilityNodeInfoUtils_ClickableString(string, clickableSpan);
    }

    public abstract String string();

    public abstract ClickableSpan clickableSpan();

    // ClickableSpan.onClick is actually fine with a null param.
    @SuppressWarnings("nullness:argument.type.incompatible")
    public void onClick() {
      clickableSpan().onClick(null);
    }
  }

  private static CharSequence printId(AccessibilityNodeInfoCompat node) {
    return String.format("Node(id=%s class=%s)", node.hashCode(), node.getClassName());
  }
}
