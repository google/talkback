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

import static com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_TYPE_NONE;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_NOT_SPEAKABLE;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_NOT_VISIBLE;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.NONE;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.GridView;
import android.widget.ListView;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.DiagnosticType;
import com.google.android.accessibility.utils.Role.RoleName;
import com.google.android.accessibility.utils.compat.CompatUtils;
import com.google.android.accessibility.utils.traversal.SpannableTraversalUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.libraries.accessibility.utils.url.SpannableUrl;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

/** Provides a series of utilities for interacting with AccessibilityNodeInfo objects. */
public class AccessibilityNodeInfoUtils {

  /** Internal AccessibilityNodeInfoCompat extras bundle key constants. */
  // The minimum amount of pixels that must be visible for a view to be surfaced to the user as
  // visible (i.e. for this node to be added to the tree).
  public static final int MIN_VISIBLE_PIXELS = 15;

  private static final String CLASS_LISTVIEW = ListView.class.getName();
  private static final String CLASS_GRIDVIEW = GridView.class.getName();

  private static final HashMap<Integer, String> actionIdToName = initActionIds();

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

  @VisibleForTesting static final int THRESHOLD_HEIGHT_DP_FOR_SMALL_NODE = 32;

  /**
   * A wrapper over AccessibilityNodeInfoCompat constructor, so that we can add any desired error
   * checking and memory management.
   *
   * @param nodeInfo The AccessibilityNodeInfo which will be wrapped.
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
   * Filter for focusable containers with a descendant that is an unfocusable heading. This filter
   * aids navigation by headings granularity when the node that is semantically a heading isn't
   * focusable (for instance, because its text is combined with the text of other nodes to create
   * speakable text for a container in a list context).
   */
  public static final Filter<AccessibilityNodeInfoCompat>
      FILTER_CONTAINER_WITH_UNFOCUSABLE_HEADING =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return searchFromBfs(
                      node,
                      FILTER_HEADING.and(
                          new Filter<AccessibilityNodeInfoCompat>() {
                            @Override
                            public boolean accept(AccessibilityNodeInfoCompat childNode) {
                              return childNode.getChildCount() == 0 && !shouldFocusNode(childNode);
                            }
                          }))
                  != null;
            }
          };

  /** Filter for scrollable grids. */
  public static final Filter<AccessibilityNodeInfoCompat> FILTER_SCROLLABLE_GRID =
      FILTER_SCROLLABLE.and(Filter.node((n) -> Role.getRole(n) == Role.ROLE_GRID));

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
        // If the node does not pass the filter, check its non focusable, visible children.
        if (!filter.accept(node)) {
          return hasMatchingDescendant(node, filter.and(FILTER_NON_FOCUSABLE_VISIBLE_NODE));
        }
        return true;
      }
    };
  }

  // TODO: Provides an overall experience of focusing on small nodes on both watch and
  // phone devices.
  /** Filters out nodes which are small and located on the top and bottom borders. */
  public static Filter<AccessibilityNodeInfoCompat> getFilterExcludingSmallTopAndBottomBorderNode(
      final Context context) {
    // For a watch device, we don't want to put focus on the small border nodes. These nodes
    // could be located at the middle of AdapterView and they could be distorted to fit in a
    // round screen when they are near top or bottom borders.
    final Point screenPxSize = DisplayUtils.getScreenPixelSizeWithoutWindowDecor(context);
    return new Filter<AccessibilityNodeInfoCompat>() {
      @Override
      public boolean accept(AccessibilityNodeInfoCompat node) {
        return !AccessibilityNodeInfoUtils.isSmallNodeInHeight(context, node)
            || !AccessibilityNodeInfoUtils.isTopOrBottomBorderNode(screenPxSize, node);
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

  /** Filter to identify nodes which are not focusable and not visible but has text. */
  public static final Filter<AccessibilityNodeInfoCompat>
      FILTER_NON_FOCUSABLE_NON_VISIBLE_HAS_TEXT_NODE =
          new Filter<AccessibilityNodeInfoCompat>() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
              return !isVisible(node)
                  && !isAccessibilityFocusable(node)
                  && !TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(node));
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
              || (role == Role.ROLE_SEEK_CONTROL)
              // The clickable view in a collection may not be a control, such as each setting item
              // in the Settings page.
              || (!nodeIsListOrGridItem(node) && (isClickable(node) || isLongClickable(node)));
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

  public static Filter<AccessibilityNodeInfoCompat> getFilterIllegalTitleNodeAncestor(
      Context context) {
    return new Filter<AccessibilityNodeInfoCompat>() {
      @Override
      public boolean accept(AccessibilityNodeInfoCompat node) {
        if (isClickable(node) || isLongClickable(node)) {
          return true;
        }

        if (FeatureSupport.isWatch(context)) {
          // A window title node can be a descendant of AdapterView in a watch device since the
          // title node may be the first node in a AdapterView.
          return false;
        } else {
          @RoleName int role = Role.getRole(node);
          // A window title node should not be a descendant of AdapterView.
          return (role == Role.ROLE_LIST) || (role == Role.ROLE_GRID);
        }
      }
    };
  }

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
      Filter.node(
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

  public static boolean isPage(@Nullable AccessibilityNodeInfoCompat node) {
    @Nullable AccessibilityNodeInfoCompat parent = (node == null) ? null : node.getParent();
    return (parent != null) && (Role.getRole(parent) == Role.ROLE_PAGER);
  }

  public static @Nullable CharSequence getSelectedPageTitle(AccessibilityNodeInfoCompat viewPager) {
    if ((viewPager == null) || (Role.getRole(viewPager) != Role.ROLE_PAGER)) {
      return null;
    }

    int numChildren = viewPager.getChildCount(); // Not the number of pages!
    CharSequence title = null;
    for (int i = 0; i < numChildren; ++i) {
      AccessibilityNodeInfoCompat child = viewPager.getChild(i);
      if (child != null && child.isVisibleToUser()) {
        if (title == null) {
          // Try to roughly match RulePagerPage, which uses getNodeText
          // (but completely matching all the time is not critical).
          title = getNodeText(child);
        } else {
          // Multiple visible children, abort.
          return null;
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

    AccessibilityWindowInfoCompat window = getWindow(node);
    if (window != null) {
      return AccessibilityWindowInfoUtils.getRoot(window);
    }

    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    AccessibilityNodeInfoCompat current = null;
    AccessibilityNodeInfoCompat parent = node;

    do {
      if (current != null) {
        if (visitedNodes.contains(current)) {
          return null;
        }
        visitedNodes.add(current);
      }

      current = parent;
      parent = current.getParent();
    } while (parent != null);

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

    return windowInfoCompat.getType();
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
    return isFocusableOrClickable(node)
        || (isTopLevelScrollItem(node) && isSpeakingNode(node, null, new HashSet<>()));
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
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache) {
    return shouldFocusNode(node, speakingNodesCache, true);
  }

  public static boolean shouldFocusNode(
      final AccessibilityNodeInfoCompat node,
      final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache,
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
      return webViewContainer != null && webViewContainer.isVisibleToUser();
    }

    if (!isVisible(node)) {
      logShouldFocusNode(
          checkChildren, FOCUS_FAIL_NOT_VISIBLE, "Don't focus, is not visible: ", node);
      return false;
    }

    if (isPictureInPicture(node)) {
      // For picture-in-picture, allow focusing the root node, and any app controls inside the
      // pic-in-pic window.
      return true;
    } else {
      // Reject all non-leaf nodes that are neither actionable nor focusable, and have the same
      // bounds as the window.
      if (areBoundsIdenticalToWindow(node)
          && node.getChildCount() > 0
          && !isFocusableOrClickable(node)) {
        logShouldFocusNode(
            checkChildren,
            FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN,
            "Don't focus, bounds are same as window root node bounds, node has children and"
                + " is neither actionable nor focusable: ",
            node);
        return false;
      }
    }

    HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
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
      visitedNodes.clear();
      // TODO: This may still result in focusing non-speaking nodes, but it
      // won't prevent unlabeled buttons from receiving focus.
      if (!canKeepSearchChildren(node)) {
        logShouldFocusNode(
            checkChildren, NONE, "Focus, is focusable and cannot keep search children: ", node);
        return true;
      } else if (isSpeakingNode(node, speakingNodesCache, visitedNodes)) {
        logShouldFocusNode(
            checkChildren, NONE, "Focus, is focusable and has something to speak: ", node);
        return true;
      } else {
        logShouldFocusNode(
            checkChildren,
            FOCUS_FAIL_NOT_SPEAKABLE,
            "Don't focus, is focusable but has nothing to speak: ",
            node);
        return false;
      }
    }

    // At this point, the node is an unfocusable target.
    // If it has no focusable ancestors, but it still has text, then it should receive focus and be
    // read aloud.
    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return shouldFocusNode(node, speakingNodesCache, false);
          }
        };

    if (!hasMatchingAncestor(node, filter) && (hasText(node) || hasStateDescription(node))) {
      logShouldFocusNode(checkChildren, NONE, "Focus, has text and no focusable ancestors: ", node);
      return true;
    }

    logShouldFocusNode(
        checkChildren,
        FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS,
        "Don't focus, failed all focusability tests: ",
        node);
    return false;
  }

  private static void logShouldFocusNode(
      boolean checkChildren,
      @DiagnosticType @Nullable Integer diagnosticType,
      String message,
      AccessibilityNodeInfoCompat node) {
    // When shouldFocusNode calls itself, the logs get inundated by unnecessary info about the
    // ancestors. So only log when checkChildren is true.
    if (checkChildren) {
      if (diagnosticType != NONE) {
        DiagnosticOverlayUtils.appendLog(diagnosticType, node);
      }
      // Show debug logs for #shouldFocusNode. Verbose logs will show for #isSpeakingNode
      LogUtils.v(TAG, "%s %s", message, node);
    }
  }

  public static boolean isPictureInPicture(@NonNull AccessibilityNodeInfoCompat node) {
    return isPictureInPicture(node.unwrap());
  }

  public static boolean isPictureInPicture(@Nullable AccessibilityNodeInfo node) {
    return node != null && AccessibilityWindowInfoUtils.isPictureInPicture(getWindow(node));
  }

  /**
   * Returns the node that should receive focus from hover by starting from the touched node and
   * calling {@link #shouldFocusNode} at each level of the view hierarchy and exclude WebView
   * container node.
   */
  public static AccessibilityNodeInfoCompat findFocusFromHover(
      @Nullable AccessibilityNodeInfoCompat touched) {
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
   * @param speakingNodesCache the cache that holds the speaking results for visited nodes
   * @param visitedNodes the set of nodes that have already been visited
   * @return {@code true} if the node can be spoken
   */
  private static boolean isSpeakingNode(
      @NonNull AccessibilityNodeInfoCompat node,
      @Nullable Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache,
      @NonNull Set<AccessibilityNodeInfoCompat> visitedNodes) {
    if (speakingNodesCache != null && speakingNodesCache.containsKey(node)) {
      return speakingNodesCache.get(node);
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
    } else if (hasNonActionableSpeakingChildren(node, speakingNodesCache, visitedNodes)) {
      // Special case for containers with non-focusable content. In this case, the container should
      // speak its non-focusable yet speakable content.
      LogUtils.v(TAG, "Speaking, has non-actionable speaking children");
      result = true;
    }

    if (speakingNodesCache != null) {
      speakingNodesCache.put(node, result);
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
   * @param speakingNodesCache the cache that holds the speaking results for visited nodes
   * @param visitedNodes the set of nodes that have already been visited.
   * @return {@code true} if the node has children that are speaking
   */
  private static boolean hasNonActionableSpeakingChildren(
      @NonNull AccessibilityNodeInfoCompat node,
      @Nullable Map<AccessibilityNodeInfoCompat, Boolean> speakingNodesCache,
      @NonNull Set<AccessibilityNodeInfoCompat> visitedNodes) {
    final int childCount = node.getChildCount();

    AccessibilityNodeInfoCompat child;

    for (int i = 0; i < childCount; i++) {
      child = node.getChild(i);

      if (child == null) {
        LogUtils.v(TAG, "Child %d is null, skipping it", i);
        continue;
      }

      if (!visitedNodes.add(child)) {
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
      if ((isTopLevelScrollItem(child) && isSpeakingNode(child, speakingNodesCache, visitedNodes))
          && !(isClickable(node) || isLongClickable(node))) {

        LogUtils.v(TAG, "Child %d, %s is a top level scroll item, skipping it", i, printId(node));
        continue;
      }

      // Recursively check non-focusable child nodes.
      if (isSpeakingNode(child, speakingNodesCache, visitedNodes)) {
        LogUtils.v(TAG, "Does have actionable speaking children (child %d, %s)", i, printId(node));
        return true;
      }
    }

    LogUtils.v(TAG, "Does not have non-actionable speaking children");
    return false;
  }

  private static boolean canKeepSearchChildren(@NonNull AccessibilityNodeInfoCompat node) {
    return hasVisibleChildren(node) || hasChildrenForWear(node);
  }

  private static boolean hasVisibleChildren(@NonNull AccessibilityNodeInfoCompat node) {
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child != null && child.isVisibleToUser()) {
        return true;
      }
    }

    return false;
  }

  private static boolean hasChildrenForWear(@NonNull AccessibilityNodeInfoCompat node) {
    int childCount = node.getChildCount();
    // While scrolling on Wear, the children could be null even though the count is larger than 0.
    // In this case, we don't want to put focus on this parent and will try to keep searching.
    return FormFactorUtils.getInstance().isAndroidWear() && childCount > 0;
  }

  public static int countVisibleChildren(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return 0;
    }
    int childCount = node.getChildCount();
    int childVisibleCount = 0;
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child != null && child.isVisibleToUser()) {
        ++childVisibleCount;
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
  public static boolean isActionableForAccessibility(@Nullable AccessibilityNodeInfoCompat node) {
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

  public static boolean isSelfOrAncestorFocused(@Nullable AccessibilityNodeInfoCompat node) {
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
  public static boolean isClickable(@Nullable AccessibilityNodeInfoCompat node) {
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
  public static boolean isLongClickable(@Nullable AccessibilityNodeInfoCompat node) {
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
  public static boolean isExpandable(@Nullable AccessibilityNodeInfoCompat node) {
    return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_EXPAND);
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
  public static boolean isCollapsible(@Nullable AccessibilityNodeInfoCompat node) {
    return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_COLLAPSE);
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
  public static boolean isDismissible(@Nullable AccessibilityNodeInfoCompat node) {
    return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_DISMISS);
  }

  /** Returns {@code true} if the node is on keyboard. */
  public static boolean isKeyboard(@Nullable AccessibilityNodeInfo source) {
    return isKeyboard(AccessibilityNodeInfoUtils.toCompat(source));
  }

  /** Returns {@code true} if the node is on keyboard. */
  public static boolean isKeyboard(@Nullable AccessibilityNodeInfoCompat source) {
    if (source == null) {
      return false;
    }
    AccessibilityWindowInfoCompat window = getWindow(source);
    if (window == null) {
      return false;
    }
    return AccessibilityWindowInfoUtils.isImeWindow(window);
  }

  /**
   * Check whether a given node has a scrollable ancestor.
   *
   * @param node The node to examine.
   * @return {@code true} if one of the node's ancestors is scrollable.
   */
  public static boolean hasMatchingAncestor(
      @Nullable AccessibilityNodeInfoCompat node,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    return (node != null) && (getMatchingAncestor(node, filter) != null);
  }

  /**
   * Check whether a given node is a key from the Pin Password keyboard used to unlock the screen.
   *
   * @param node The node to examine.
   * @return {@code true} if the node is a key from the Pin Password keyboard used to unlock the
   *     screen.
   */
  // TODO: Find a better way to identify that it's a key for PIN password.
  public static boolean isPinKey(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    String viewIdResourceName = node.getViewIdResourceName();
    return !TextUtils.isEmpty(viewIdResourceName)
        && PIN_KEY_PATTERN.matcher(viewIdResourceName).matches();
  }

  /** Returns whether the node is the Pin edit field at unlock screen. */
  public static boolean isPinEntry(@Nullable AccessibilityNodeInfo node) {
    return isPinEntry(AccessibilityNodeInfoUtils.toCompat(node));
  }

  public static boolean isPinEntry(@Nullable AccessibilityNodeInfoCompat node) {
    return (node != null)
        && Objects.equals(node.getViewIdResourceName(), VIEW_ID_RESOURCE_NAME_PIN_ENTRY);
  }

  /**
   * Check whether a given node or any of its ancestors matches the given filter.
   *
   * @param node The node to examine.
   * @param filter The filter to match the nodes against.
   * @return {@code true} if the node or one of its ancestors matches the filter.
   */
  public static boolean isOrHasMatchingAncestor(
      @Nullable AccessibilityNodeInfoCompat node,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    return (node != null) && (getSelfOrMatchingAncestor(node, filter) != null);
  }

  /** Check whether a given node has any descendant matching a given filter. */
  public static boolean hasMatchingDescendant(
      @Nullable AccessibilityNodeInfoCompat node,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    return (node != null) && (getMatchingDescendant(node, filter) != null);
  }

  /** Returns depth of node in node-tree, where root has depth=0. */
  public static int findDepth(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return -1;
    }
    NodeCounter counter = new NodeCounter();
    processSelfAndAncestors(node, counter);
    return counter.count - 1;
  }

  private static class NodeCounter extends Filter<AccessibilityNodeInfoCompat> {
    public int count = 0;

    @Override
    public boolean accept(AccessibilityNodeInfoCompat node) {
      ++count;
      return false;
    }
  }

  /** Applies filter to ancestor nodes. */
  public static void processSelfAndAncestors(
      @Nullable AccessibilityNodeInfoCompat node,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    if (node != null) {
      isOrHasMatchingAncestor(node, filter);
    }
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching ancestor.
   * Returns {@code null} if no nodes match.
   */
  public static @Nullable AccessibilityNodeInfoCompat getSelfOrMatchingAncestor(
      @Nullable AccessibilityNodeInfoCompat node,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }
    if (filter.accept(node)) {
      return node;
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
      @Nullable AccessibilityNodeInfoCompat node,
      @Nullable AccessibilityNodeInfoCompat end,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }
    if (filter.accept(node)) {
      return node;
    }
    return getMatchingAncestor(node, end, filter);
  }

  /**
   * Returns the {@code node} if it matches the {@code filter}, or the first matching descendant.
   * Returns {@code null} if no nodes match.
   */
  public static @Nullable AccessibilityNodeInfoCompat getSelfOrMatchingDescendant(
      @Nullable AccessibilityNodeInfoCompat node,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }
    if (filter.accept(node)) {
      return node;
    }
    return getMatchingDescendant(node, filter);
  }

  /** Processes subtree of root by {@code filter}. */
  public static void processSubtree(
      @Nullable AccessibilityNodeInfoCompat root,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {

    AccessibilityNodeInfoUtils.getSelfOrMatchingDescendant(
        root,
        Filter.node(
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
      final @Nullable AccessibilityNodeInfoCompat node1,
      final @Nullable AccessibilityNodeInfoCompat node2) {
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
      @Nullable AccessibilityNodeInfoCompat node,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    return getMatchingAncestor(node, null, filter);
  }

  /**
   * Returns the first ancestor of {@code node} that matches the {@code filter}, terminating the
   * search once it reaches {@code end}. The search is exclusive of both {@code node} and {@code
   * end}. Returns {@code null} if no nodes match.
   */
  private static @Nullable AccessibilityNodeInfoCompat getMatchingAncestor(
      @Nullable AccessibilityNodeInfoCompat node,
      @Nullable AccessibilityNodeInfoCompat end,
      @NonNull Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }

    final HashSet<AccessibilityNodeInfoCompat> ancestors = new HashSet<>();

    ancestors.add(node);
    node = node.getParent();

    while (node != null) {
      if (!ancestors.add(node)) {
        // Already seen this node, so abort!
        return null;
      }

      if (end != null && node.equals(end)) {
        // Reached the end node, so abort!
        return null;
      }

      if (filter.accept(node)) {
        return node;
      }

      node = node.getParent();
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

    ancestors.add(node);
    node = node.getParent();

    while (node != null) {
      if (!ancestors.add(node)) {
        // Already seen this node, so abort!
        return 0;
      }

      if (filter.accept(node)) {
        matchingAncestors++;
      }

      node = node.getParent();
    }

    return matchingAncestors;
  }

  /**
   * Returns the first child (by depth-first search) of {@code node} that matches the {@code
   * filter}. Returns {@code null} if no nodes match.
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
      visitedNodes.add(node);
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

      AccessibilityNodeInfoCompat childMatch = getMatchingDescendant(child, filter, visitedNodes);
      if (childMatch != null) {
        return childMatch;
      }
    }

    return null;
  }

  public static @Nullable AccessibilityNodeInfoCompat getMatchingDescendant(
      AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    return getMatchingDescendant(node, filter, new HashSet<>());
  }

  /** Returns all descendants that match filter. */
  public static @Nullable List<AccessibilityNodeInfoCompat> getMatchingDescendantsOrRoot(
      @Nullable AccessibilityNodeInfoCompat node, Filter<AccessibilityNodeInfoCompat> filter) {
    if (node == null) {
      return null;
    }
    List<AccessibilityNodeInfoCompat> matches = new ArrayList<>();
    getMatchingDescendants(node, filter, /* matchRoot= */ true, new HashSet<>(), matches);
    return matches;
  }

  /**
   * Collects all descendants that match filter, into matches.
   *
   * @param node The root node to start searching.
   * @param filter The filter to match the nodes against.
   * @param matchRoot Flag that allows match with root node.
   * @param visitedNodes The set of nodes already visited, for protection against loops. This will
   *     be modified.
   * @param matches The list of nodes matching filter. This will be appended to.
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
      visitedNodes.add(node);
    }

    // If node matches filter... collect node.
    if (matchRoot && filter.accept(node)) {
      matches.add(node);
    }

    // For each child of node...
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child == null) {
        continue;
      }
      getMatchingDescendants(child, filter, /* matchRoot= */ true, visitedNodes, matches);
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
    return supportsAnyAction(
        node,
        AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
        AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
        AccessibilityActionCompat.ACTION_SCROLL_DOWN,
        AccessibilityActionCompat.ACTION_SCROLL_UP,
        AccessibilityActionCompat.ACTION_SCROLL_RIGHT,
        AccessibilityActionCompat.ACTION_SCROLL_LEFT);
  }

  /**
   * Returns whether the specified node has text. For the purposes of this check, any node with a
   * CollectionInfo is considered to not have text since its text and content description are used
   * only for collection transitions.
   *
   * @param node The node to check.
   * @return {@code true} if the node has text.
   */
  private static boolean hasText(@Nullable AccessibilityNodeInfoCompat node) {
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

    AccessibilityNodeInfoCompat parent = node.getParent();
    return isScrollItem(parent);
  }

  private static boolean isScrollItem(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      // Not a child node of anything.
      return false;
    }

    // Drop down lists (spinners) are not included to retain the old behavior of focusing on
    // the spinner itself rather than on the single visible item.
    // A spinner being scrollable is disingenuous since the scrollable list inside isn't exposed
    // without interaction.
    if (Role.getRole(node) == Role.ROLE_DROP_DOWN_LIST) {
      return false;
    }

    // A node with a scrollable parent is a top level scroll item.
    if (isScrollable(node)) {
      return true;
    }

    @Role.RoleName int parentRole = Role.getRole(node);
    // Note that ROLE_DROP_DOWN_LIST(Spinner) is not accepted.
    // RecyclerView is classified as a list or grid based on its CollectionInfo.
    // These parents may not be scrollable in some cases, like if the list is too short to be
    // scrolled, but their children should still be considered top level scroll items.
    return parentRole == Role.ROLE_LIST
        || parentRole == Role.ROLE_GRID
        || parentRole == Role.ROLE_SCROLL_VIEW
        || parentRole == Role.ROLE_HORIZONTAL_SCROLL_VIEW
        || nodeMatchesAnyClassByType(node, CLASS_TOUCHWIZ_TWADAPTERVIEW);
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

    return (getMatchingAncestor(node, filter) != null);
  }

  public static boolean hasDescendant(
      @Nullable AccessibilityNodeInfoCompat node,
      @Nullable AccessibilityNodeInfoCompat targetDescendant) {
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

    return (getMatchingDescendant(node, filter) != null);
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
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(Collection<AccessibilityNodeInfoCompat> nodes) {
    nodes.clear();
  }

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(@Nullable AccessibilityNodeInfo... nodes) {}

  /**
   * Recycles the given nodes.
   *
   * @param nodes The nodes to recycle.
   */
  public static void recycleNodes(@Nullable AccessibilityNodeInfoCompat... nodes) {}

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
      AccessibilityNodeInfoCompat node, AccessibilityActionCompat... actions) {
    if (node == null) {
      return false;
    }
    // Unwrap the node and compare AccessibilityActions because AccessibilityActions, unlike
    // AccessibilityActionCompats, are static (so checks for equality work correctly).
    final List<AccessibilityActionCompat> supportedActions = node.getActionList();

    for (AccessibilityActionCompat action : actions) {
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
  public static boolean supportsAnyAction(
      @Nullable AccessibilityNodeInfoCompat node, int... actions) {
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
  public static boolean supportsAction(@NonNull AccessibilityNodeInfoCompat node, int action) {
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
   * Returns the action label on the node by given action ID, or an empty text if the node doesn't
   * support the action.
   */
  public static @Nullable CharSequence getActionLabelById(
      @NonNull AccessibilityNodeInfoCompat node, int action) {
    List<AccessibilityActionCompat> actions = node.getActionList();
    int size = actions.size();
    for (int i = 0; i < size; ++i) {
      AccessibilityActionCompat actionCompat = actions.get(i);
      if (actionCompat.getId() == action) {
        return actionCompat.getLabel();
      }
    }
    return "";
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
  public static @Nullable AccessibilityNodeInfoCompat searchFromBfs(
      AccessibilityNodeInfoCompat node,
      Filter<AccessibilityNodeInfoCompat> filter,
      @Nullable Filter<AccessibilityNodeInfoCompat> filterToSkip) {
    if (node == null) {
      return null;
    }

    final ArrayDeque<AccessibilityNodeInfoCompat> queue = new ArrayDeque<>();
    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();

    queue.add(node);

    while (!queue.isEmpty()) {
      final AccessibilityNodeInfoCompat item = queue.removeFirst();
      visitedNodes.add(item);

      if (filterToSkip != null && filterToSkip.accept(item)) {
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
    }
    return null;
  }

  /** Safely obtains a copy of node. */
  @Deprecated
  public static @Nullable AccessibilityNodeInfoCompat obtain(AccessibilityNodeInfoCompat node) {
    return (node == null) ? null : AccessibilityNodeInfoCompat.obtain(node);
  }

  /**
   * Returns a fresh copy of {@code node} with properties that are less likely to be stale. Returns
   * {@code null} if the node can't be found anymore.
   */
  public static @Nullable AccessibilityNodeInfoCompat refreshNode(
      AccessibilityNodeInfoCompat node) {
    return ((node == null) || !node.refresh()) ? null : node;
  }

  /**
   * Gets the location of specific range of node text. It returns null if the node doesn't support
   * text location data or the index is incorrect.
   *
   * @param node The node being queried.
   * @param fromCharIndex start index of the queried text range.
   * @param toCharIndex end index of the queried text range.
   */
  public static @Nullable List<Rect> getTextLocations(
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
  public static @Nullable List<Rect> getTextLocations(
      AccessibilityNodeInfoCompat node, CharSequence text, int fromCharIndex, int toCharIndex) {
    if (node == null) {
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
  public static boolean supportsTextLocation(AccessibilityNodeInfoCompat node) {
    if (node == null) {
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

  /**
   * Checks whether the node's height is smaller than the threshold
   *
   * @param context the context
   * @param node the node to check
   * @return {@code true} if the node's height is smaller than the dp threshold.
   */
  public static boolean isSmallNodeInHeight(Context context, AccessibilityNodeInfoCompat node) {
    final Rect nodeRect = new Rect();
    node.getBoundsInScreen(nodeRect);

    return nodeRect.height() < DisplayUtils.dpToPx(context, THRESHOLD_HEIGHT_DP_FOR_SMALL_NODE);
  }

  /**
   * Checks whether the node is a top or bottom border node or not. Horizontal scrolling with a
   * check of left or right border isn't yet supported in this method.
   *
   * @param screenPxSize the pixel size of a screen
   * @param node the node to check
   * @return {@code true} if the node is at top or bottom border.
   */
  public static boolean isTopOrBottomBorderNode(
      Point screenPxSize, AccessibilityNodeInfoCompat node) {

    final Rect nodeRect = new Rect();
    node.getBoundsInScreen(nodeRect);

    // check the screen's border
    if (isTopOrBottomBorderNode(nodeRect, screenPxSize)) {
      return true;
    }

    // check the scrollable container's border
    final Rect parentRect = new Rect();
    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat parent) {
            if (isScrollItem(parent)) {
              parent.getBoundsInScreen(parentRect);
              return parentRect.top == nodeRect.top || parentRect.bottom == nodeRect.bottom;
            }
            return false;
          }
        };

    return hasMatchingAncestor(node, filter);
  }

  private static boolean isTopOrBottomBorderNode(Rect nodeRect, Point screenPxSize) {
    return nodeRect.top <= 0 || nodeRect.bottom >= screenPxSize.y;
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
   * Analyses if the edit text has no text.
   *
   * <p>If there is a text field with hint text and no text, {@link
   * AccessibilityNodeInfoUtils#getText()} returns hint text. Hence this method checks for {@link
   * AccessibilityNodeInfo#ACTION_SET_SELECTION} to disregard the hint text.
   */
  public static boolean isEmptyEditTextRegardlessOfHint(
      @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null || !node.isEditable()) {
      return false;
    }

    if (TextUtils.isEmpty(AccessibilityNodeInfoUtils.getText(node))) {
      return true;
    }
    return !supportsAction(node, AccessibilityNodeInfo.ACTION_SET_SELECTION);
  }

  /** * Checks if node represents non-editable selectable text. */
  public static boolean isNonEditableSelectableText(AccessibilityNodeInfoCompat node) {
    if (node != null && FeatureSupport.supportsIsTextSelectable()) {
      return !node.isEditable() && node.unwrap().isTextSelectable();
    }
    return false;
  }

  /** * Checks if node represents selectable text. Editable text is selectable. */
  public static boolean isTextSelectable(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    boolean isEditable = Role.getRole(node) == Role.ROLE_EDIT_TEXT || node.isEditable();
    boolean isNonEditableSelectableText =
        AccessibilityNodeInfoUtils.isNonEditableSelectableText(node);
    return isEditable || isNonEditableSelectableText;
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

  /**
   * To setup a hashmap for AccessibilityAction id and the display string. We only build into the
   * hash map with identifiers which are supported in the running platform.
   */
  private static HashMap<Integer, String> initActionIds() {
    HashMap<Integer, String> actionIdHashMap = new HashMap<>();

    actionIdHashMap.put(AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId(), "ACTION_SHOW_ON_SCREEN");
    actionIdHashMap.put(
        AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), "ACTION_SCROLL_TO_POSITION");
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_UP.getId(), "ACTION_SCROLL_UP");
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_LEFT.getId(), "ACTION_SCROLL_LEFT");
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_DOWN.getId(), "ACTION_SCROLL_DOWN");
    actionIdHashMap.put(AccessibilityAction.ACTION_SCROLL_RIGHT.getId(), "ACTION_SCROLL_RIGHT");
    actionIdHashMap.put(AccessibilityAction.ACTION_CONTEXT_CLICK.getId(), "ACTION_CONTEXT_CLICK");
    actionIdHashMap.put(AccessibilityAction.ACTION_SET_PROGRESS.getId(), "ACTION_SET_PROGRESS");
    actionIdHashMap.put(AccessibilityAction.ACTION_MOVE_WINDOW.getId(), "ACTION_MOVE_WINDOW");

    if (BuildVersionUtils.isAtLeastP()) {
      actionIdHashMap.put(AccessibilityAction.ACTION_SHOW_TOOLTIP.getId(), "ACTION_SHOW_TOOLTIP");
      actionIdHashMap.put(AccessibilityAction.ACTION_HIDE_TOOLTIP.getId(), "ACTION_HIDE_TOOLTIP");
    }
    if (BuildVersionUtils.isAtLeastQ()) {
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_RIGHT.getId(), "ACTION_PAGE_RIGHT");
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_LEFT.getId(), "ACTION_PAGE_LEFT");
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_DOWN.getId(), "ACTION_PAGE_DOWN");
      actionIdHashMap.put(AccessibilityAction.ACTION_PAGE_UP.getId(), "ACTION_PAGE_UP");
    }
    if (BuildVersionUtils.isAtLeastR()) {
      actionIdHashMap.put(
          AccessibilityAction.ACTION_PRESS_AND_HOLD.getId(), "ACTION_PRESS_AND_HOLD");
      actionIdHashMap.put(AccessibilityAction.ACTION_IME_ENTER.getId(), "ACTION_IME_ENTER");
    }
    return actionIdHashMap;
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
        break;
    }
    @Nullable String actionName = actionIdToName.get(action);
    return actionName == null ? "(unhandled action:" + action + ")" : actionName;
  }

  public static String toStringShort(@Nullable AccessibilityNodeInfo node) {
    return toStringShort(toCompat(node));
  }

  public static String toStringShort(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return "null";
    }
    return StringBuilderUtils.joinFields(
        "AccessibilityNodeInfoCompat",
        StringBuilderUtils.optionalInt("id", node.hashCode(), -1),
        StringBuilderUtils.optionalText("class", node.getClassName()),
        StringBuilderUtils.optionalText("package", node.getPackageName()),
        // TODO: Uses hash value in production build
        StringBuilderUtils.optionalText(
            "text",
            (AccessibilityNodeInfoUtils.getText(node) == null)
                ? null
                : FeatureSupport.logcatIncludePsi()
                    // Logs for DEBUG build or user had opt-in
                    ? AccessibilityNodeInfoUtils.getText(node)
                    : "***"),
        StringBuilderUtils.optionalText("state", node.getStateDescription()),
        StringBuilderUtils.optionalText("content", node.getContentDescription()),
        StringBuilderUtils.optionalText("viewIdResName", node.getViewIdResourceName()),
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
        StringBuilderUtils.optionalTag("textEntryKey", node.isTextEntryKey()),
        StringBuilderUtils.optionalTag("scrollable", isScrollable(node)),
        StringBuilderUtils.optionalTag(
            "heading", FeatureSupport.isHeadingWorks() && node.isHeading()),
        StringBuilderUtils.optionalTag("collapsible", isCollapsible(node)),
        StringBuilderUtils.optionalTag("expandable", isExpandable(node)),
        StringBuilderUtils.optionalTag("dismissable", isDismissible(node)),
        StringBuilderUtils.optionalTag("pinKey", isPinKey(node)),
        StringBuilderUtils.optionalTag("pinEntry", isPinEntry(node)),
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

    final @Nullable RangeInfoCompat rangeInfo = node.getRangeInfo();
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

    final @Nullable RangeInfoCompat rangeInfo = node.getRangeInfo();
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

    return hasDescendant(windowInfoCompat.getRoot(), checkingNode);
  }

  /** Checks whether the given node is still in the window. */
  public static boolean isInWindow(
      AccessibilityNodeInfoCompat checkingNode, @Nullable AccessibilityWindowInfo windowInfo) {
    if (windowInfo == null) {
      return false;
    }

    return hasDescendant(toCompat(windowInfo.getRoot()), checkingNode);
  }

  /**
   * Checks whether the given node is a header.
   *
   * <p>On M devices, the return value is always false if the node is an item in ListView or
   * GridView but not in WebView.
   */
  // TODO On pre-N devices, the framework ListView/GridView will mark non-headers
  // as headers. The workaround should be removed when TalkBack doesn't support android M.
  public static boolean isHeading(AccessibilityNodeInfoCompat node) {
    if (!FeatureSupport.isHeadingWorks()) {
      AccessibilityNodeInfoCompat collectionRoot = getCollectionRoot(node);
      if (nodeIsListOrGrid(collectionRoot) && !WebInterfaceUtils.isWebContainer(collectionRoot)) {
        return false;
      }
    }
    return node.isHeading();
  }

  /** Returns a collection root. */
  public static @Nullable AccessibilityNodeInfoCompat getCollectionRoot(
      @Nullable AccessibilityNodeInfoCompat node) {
    return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_COLLECTION);
  }

  /** Checks if given node is ListView or GirdView. */
  public static boolean nodeIsListOrGrid(@Nullable AccessibilityNodeInfoCompat node) {
    return nodeMatchesAnyClassName(node, CLASS_LISTVIEW, CLASS_GRIDVIEW);
  }

  /** Returns {@code true} if the parent of the {@code node} is a collection. */
  public static boolean nodeIsListOrGridItem(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    @Nullable AccessibilityNodeInfoCompat parent = node.getParent();
    if (parent == null) {
      return false;
    }

    @RoleName int role = Role.getRole(parent);
    return role == Role.ROLE_LIST || role == Role.ROLE_GRID;
  }

  /** Returns true if the {@code node} is in a collection. */
  public static boolean isInCollection(AccessibilityNodeInfoCompat node) {
    return AccessibilityNodeInfoUtils.hasMatchingAncestor(
        node,
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat ancestor) {
            @RoleName int role = Role.getRole(ancestor);
            return role == Role.ROLE_LIST
                || role == Role.ROLE_GRID
                || (ancestor != null && ancestor.getCollectionInfo() != null);
          }
        });
  }

  public static @Nullable String geGridRowTitle(AccessibilityNodeInfoCompat node) {
    if (FeatureSupport.supportGridTitle() && node.unwrap() != null) {
      CollectionItemInfo itemInfo = node.unwrap().getCollectionItemInfo();
      if (itemInfo != null) {
        return itemInfo.getRowTitle();
      }
    }
    return null;
  }

  public static @Nullable String geGridColumnTitle(AccessibilityNodeInfoCompat node) {
    if (FeatureSupport.supportGridTitle() && node.unwrap() != null) {
      CollectionItemInfo itemInfo = node.unwrap().getCollectionItemInfo();
      if (itemInfo != null) {
        return itemInfo.getColumnTitle();
      }
    }
    return null;
  }

  /**
   * Returns true if the {@link
   * androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat} associated
   * with {@code node} is not null and reflects the presence of at least 1 row and 1 column.
   */
  public static boolean hasUsableCollectionInfo(AccessibilityNodeInfoCompat node) {
    return node != null
        && node.getCollectionInfo() != null
        && node.getCollectionInfo().getRowCount() >= 1
        && node.getCollectionInfo().getColumnCount() >= 1;
  }

  /**
   * Returns true if the {@link
   * androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat}
   * associated with {@code node} is not null and contains legal collection row and column indices.
   */
  public static boolean hasUsableCollectionItemInfo(AccessibilityNodeInfoCompat node) {
    return node != null
        && node.getCollectionItemInfo() != null
        && node.getCollectionItemInfo().getRowIndex() >= 0
        && node.getCollectionItemInfo().getColumnIndex() >= 0;
  }

  /**
   * Returns true if the {@link
   * androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat}
   * associated with {@code node} is not null, and it contains legal collection row and column
   * indices, which fall within the row and column bounds of {@code parent}.
   */
  public static boolean hasUsableCollectionItemInfo(
      AccessibilityNodeInfoCompat item, AccessibilityNodeInfoCompat collection) {
    return hasUsableCollectionItemInfo(item)
        && hasUsableCollectionInfo(collection)
        && item.getCollectionItemInfo().getRowIndex() < collection.getCollectionInfo().getRowCount()
        && item.getCollectionItemInfo().getColumnIndex()
            < collection.getCollectionInfo().getColumnCount();
  }

  /**
   * Returns the {@link Rect} of the node bounds in screen coordinates, and returns an empty Rect if
   * the given node is null.
   */
  public static Rect getNodeBoundsInScreen(@Nullable AccessibilityNodeInfoCompat node) {
    Rect nodeBounds = new Rect();
    if (node != null) {
      node.getBoundsInScreen(nodeBounds);
    }
    return nodeBounds;
  }

  /**
   * Returns a list of {@link SpellingSuggestion} for all {@link SuggestionSpan}s at the cursor
   * position in the given {@link AccessibilityNodeInfoCompat}.
   *
   * @param node The node to check
   */
  public static ImmutableList<SpellingSuggestion> getSpellingSuggestions(
      AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return ImmutableList.of();
    }

    int start = node.getTextSelectionStart();
    int end = node.getTextSelectionEnd();

    if (start != end) {
      LogUtils.v(TAG, "Spelling suggestion does not work when text is selected.");
      return ImmutableList.of();
    }

    return getSpellingSuggestions(node, end);
  }

  /**
   * Returns a list of {@link SpellingSuggestion} for all {@link SuggestionSpan}s at the cursor
   * position in the given {@link AccessibilityNodeInfoCompat}.
   *
   * @param node the node to check
   * @param cursorPosition index of the cursor position
   */
  public static ImmutableList<SpellingSuggestion> getSpellingSuggestions(
      AccessibilityNodeInfoCompat node, int cursorPosition) {
    ImmutableList<SuggestionSpan> spans = getSuggestionSpans(node);
    if (spans.isEmpty()) {
      return ImmutableList.of();
    }

    List<SpellingSuggestion> spellingSuggestions = new ArrayList<>();
    CharSequence text = node.getText();
    Spanned spannedText = (Spanned) text;

    // Returns the suggestion if just a space or punctuation is between the typo and the cursor.
    // For example: helllo,|
    if (cursorPosition > 0) {
      if (cursorPosition < text.length()) {
        // Do not return the suggestion if a word is after the cursor. For example: helllo |world
        if (!Character.isLetterOrDigit(text.charAt(cursorPosition - 1))
            && !Character.isLetterOrDigit(text.charAt(cursorPosition))) {
          cursorPosition--;
        }
      } else if (cursorPosition == text.length()) {
        // It is unnecessary to check the character after the cursor because the cursor is at the
        // end of the line. For example: helllo |
        if (!Character.isLetterOrDigit(text.charAt(cursorPosition - 1))) {
          cursorPosition--;
        }
      }
    }

    StringBuilder logMessage =
        new StringBuilder(String.format(Locale.ENGLISH, "suggestion_spans text=[%s]", text));
    // TODO: Uses stream to simplify it.
    for (SuggestionSpan span : spans) {
      int start = spannedText.getSpanStart(span);
      int end = spannedText.getSpanEnd(span);
      if (start <= cursorPosition && end >= cursorPosition) {
        SpellingSuggestion spellingSuggestion =
            SpellingSuggestion.create(start, end, text.subSequence(start, end), span);
        // Ignore the span which has no suggestion to avoid announcing suggestions available but
        // there is no suggestion that can be chosen.
        if (span.getSuggestions().length > 0) {
          spellingSuggestions.add(spellingSuggestion);
        }

        logMessage.append("\n");
        logMessage.append(spellingSuggestion);
      }
    }

    LogUtils.v(TAG, logMessage.toString());
    return ImmutableList.copyOf(spellingSuggestions);
  }

  /** Returns the total number of typos which are in the edit field. */
  public static int getTypoCount(AccessibilityNodeInfoCompat node) {
    return getSuggestionSpans(node).size();
  }

  /**
   * Returns {@code true} if the given {@link AccessibilityNodeInfoCompat} text includes misspelled
   * words which have spelling suggestions.
   */
  public static boolean hasSpellingSuggestionsForTypos(AccessibilityNodeInfoCompat node) {
    ImmutableList<SuggestionSpan> spans = getSuggestionSpans(node);
    for (SuggestionSpan span : spans) {
      if (span.getSuggestions().length > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@link Locale} if the given {@link AccessibilityNodeInfoCompat} supports App Locale.
   */
  public static @Nullable Locale getLocalesByNode(AccessibilityNodeInfoCompat node) {
    if (node == null || !FeatureSupport.supportAccessibilityAppLocale()) {
      return null;
    }
    AccessibilityWindowInfoCompat windowInfoCompat = node.getWindow();
    if (windowInfoCompat == null) {
      return null;
    }
    AccessibilityWindowInfo windowInfo = windowInfoCompat.unwrap();
    if (windowInfo == null) {
      return null;
    }
    LocaleList localeList = windowInfo.getLocales();
    Locale defaultLocal = Locale.getDefault();

    int count = (localeList == null) ? 0 : localeList.size();
    if (count == 0 || defaultLocal.equals(localeList.get(0))) {
      // AccessibilityWindowInfo#getLocales may return the system default locale. When the 1st entry
      // matches the default locale, we don't insert the locale which will invalidate the locale
      // embedded within the content.
      return null;
    }
    return localeList.get(0);
  }

  /** Returns whether the node has requested initial accessibility focus. */
  public static boolean hasRequestInitialAccessibilityFocus(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    boolean hasRequestInitialAccessibilityFocus = node.hasRequestInitialAccessibilityFocus();

    // In the early version of AndroidX, the property was retrieved from the AccessibilityNodeInfo.
    // See b/279108748 for details.
    if (!hasRequestInitialAccessibilityFocus
        && FeatureSupport.supportRequestInitialAccessibilityFocusNative()) {
      AccessibilityNodeInfo unwrap = node.unwrap();
      if (unwrap != null) {
        hasRequestInitialAccessibilityFocus = unwrap.hasRequestInitialAccessibilityFocus();
      }
    }

    return hasRequestInitialAccessibilityFocus;
  }

  /**
   * Returns the rate update limitation (in milli-second) if the given {@link
   * AccessibilityNodeInfoCompat} supports it.
   */
  public static long getMinDurationBetweenContentChangesMillis(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      LogUtils.w(TAG, "Failed to getMinDurationBetweenContentChangesMillis/node is null");
      return 0L;
    }
    return node.getMinDurationBetweenContentChangesMillis();
  }

  /**
   * Returns a list of {@link SuggestionSpan} in the given {@link AccessibilityNodeInfoCompat} text.
   */
  private static ImmutableList<SuggestionSpan> getSuggestionSpans(
      AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return ImmutableList.of();
    }

    CharSequence text = node.getText();
    if (TextUtils.isEmpty(text) || !(text instanceof Spanned)) {
      return ImmutableList.of();
    }

    Spanned spannedText = (Spanned) text;
    SuggestionSpan[] spans = spannedText.getSpans(0, text.length(), SuggestionSpan.class);
    if (spans.length == 0) {
      return ImmutableList.of();
    }

    return ImmutableList.copyOf(spans);
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
   * Splits a fully-qualified resource identifier name into its package and ID name. For example,
   * "com.android.deskclock:id/analog_appwidget" which provides by {@link
   * AccessibilityNodeInfoCompat#getViewIdResourceName()}
   */
  @AutoValue
  public abstract static class ViewResourceName {
    public abstract String packageName();

    public abstract String viewIdName();

    /** Creates a ViewResourceName instance by {@link AccessibilityNodeInfoCompat}. */
    public static @Nullable ViewResourceName create(AccessibilityNodeInfoCompat node) {
      String resourceName = node.getViewIdResourceName();
      if (TextUtils.isEmpty(resourceName)) {
        return null;
      }

      final String[] splitId = RESOURCE_NAME_SPLIT_PATTERN.split(resourceName, 2);
      if (splitId.length != 2 || TextUtils.isEmpty(splitId[0]) || TextUtils.isEmpty(splitId[1])) {
        // Invalid view resource name.
        LogUtils.w(TAG, "Failed to parse resource: %s", resourceName);
        return null;
      }

      return new AutoValue_AccessibilityNodeInfoUtils_ViewResourceName(splitId[0], splitId[1]);
    }

    @Override
    public final String toString() {
      return "ViewResourceName= "
          + StringBuilderUtils.joinFields(
              StringBuilderUtils.optionalText("packageName", packageName()),
              StringBuilderUtils.optionalText("viewIdName", viewIdName()));
    }
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
    public void onClick() {
      clickableSpan().onClick(null);
    }
  }

  /** A wrapper of {@link SuggestionSpan}. */
  @AutoValue
  public abstract static class SpellingSuggestion {
    public abstract int start();

    public abstract int end();

    public abstract CharSequence misspelledWord();

    public abstract SuggestionSpan suggestionSpan();

    public static SpellingSuggestion create(
        int start, int end, CharSequence misspelledWord, SuggestionSpan suggestionSpan) {
      return new AutoValue_AccessibilityNodeInfoUtils_SpellingSuggestion(
          start, end, misspelledWord, suggestionSpan);
    }

    @NonNull
    @Override
    public final String toString() {
      StringBuilder suggestionsString =
          new StringBuilder()
              .append(
                  String.format(Locale.ENGLISH, "[%d-%d][%s]", start(), end(), misspelledWord()));
      for (String suggestion : suggestionSpan().getSuggestions()) {
        suggestionsString.append(String.format(Locale.ENGLISH, "[suggestion=%s]", suggestion));
      }

      return suggestionsString.toString();
    }
  }

  private static String printId(AccessibilityNodeInfoCompat node) {
    return String.format("Node(id=%s class=%s)", node.hashCode(), node.getClassName());
  }
}
