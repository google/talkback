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

package com.android.utils;

import com.google.android.marvin.talkback.TalkBackService;

import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.AbsSpinner;
import android.widget.AdapterView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Spinner;
import com.android.utils.compat.CompatUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.Label;
import com.android.utils.traversal.TraversalStrategy;
import com.android.utils.traversal.TraversalStrategyUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides a series of utilities for interacting with AccessibilityNodeInfo
 * objects. NOTE: This class only recycles unused nodes that were collected
 * internally. Any node passed into or returned from a public method is retained
 * and TalkBack should recycle it when appropriate.
 */
public class AccessibilityNodeInfoUtils {
    /**
     * Class for Samsung's TouchWiz implementation of AdapterView. May be
     * {@code null} on non-Samsung devices.
     */
    private static final Class<?> CLASS_TOUCHWIZ_TWADAPTERVIEW = CompatUtils.getClass(
            "com.sec.android.touchwiz.widget.TwAdapterView");

    /**
     * Class for Samsung's TouchWiz implementation of AbsListView. May be
     * {@code null} on non-Samsung devices.
     */
    private static final Class<?> CLASS_TOUCHWIZ_TWABSLISTVIEW = CompatUtils.getClass(
            "com.sec.android.touchwiz.widget.TwAbsListView");

    private static final String CLASS_RECYCLER_VIEW_CLASS_NAME =
            "android.support.v7.widget.RecyclerView";

    private static final NodeFilter DEFAULT_FILTER = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            return node != null;
        }
    };

    private static final int SYSTEM_ACTION_MAX = 0x01FFFFFF;

    /**
     * Filter for scrollable items. One of the following must be true:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#isScrollable()} returns
     * {@code true}</li>
     * <li>{@link AccessibilityNodeInfoCompat#getActions()} supports
     * {@link AccessibilityNodeInfoCompat#ACTION_SCROLL_FORWARD}</li>
     * <li>{@link AccessibilityNodeInfoCompat#getActions()} supports
     * {@link AccessibilityNodeInfoCompat#ACTION_SCROLL_BACKWARD}</li>
     * </ul>
     */
    public static final NodeFilter FILTER_SCROLLABLE = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            return isScrollable(node);
        }
    };

    /**
     * Filter for items that should receive accessibility focus. Equivalent to
     * calling {@link #shouldFocusNode(AccessibilityNodeInfoCompat)}.
     */
    public static final NodeFilter FILTER_SHOULD_FOCUS = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            return node != null && shouldFocusNode(node);
        }
    };

    /**
     * Filter that defines which types of views should be auto-scrolled.
     * Generally speaking, only accepts views that are capable of showing
     * partially-visible data.
     * <p>
     * Accepts the following classes (and sub-classes thereof):
     * <ul>
     * <li>{@link android.widget.AbsListView} (and Samsung's TwAbsListView)
     * <li>{@link android.widget.AbsSpinner}
     * <li>{@link android.widget.ScrollView}
     * <li>{@link android.widget.HorizontalScrollView}
     * </ul>
     * <p>
     * Specifically excludes {@link android.widget.AdapterViewAnimator} and
     * sub-classes, since they represent overlapping views. Also excludes
     * {@link android.support.v4.view.ViewPager} since it exclusively represents
     * off-screen views.
     */
    private static final NodeFilter FILTER_AUTO_SCROLL = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            if (node.isScrollable()) {
                return nodeMatchesAnyClassByType(node,
                        AbsListView.class, AbsSpinner.class, ScrollView.class,
                        HorizontalScrollView.class, CLASS_TOUCHWIZ_TWABSLISTVIEW) ||
                        nodeMatchesClassByName(node, CLASS_RECYCLER_VIEW_CLASS_NAME);
            }

            return false;
        }
    };

    /**
     * This filter accepts scrollable views that break if we place accessibility focus on their
     * child items. Instead, we should just place focus on the entire scrollable view.
     * Note: Only include Android TV views that cannot be updated (i.e. part of a bundled app).
     */
    private static final NodeFilter FILTER_BROKEN_LISTS_TV_M = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            if (node == null) {
                return false;
            }

            CharSequence viewId = node.getViewIdResourceName();
            return "com.android.tv.settings:id/setup_scroll_list".equals(viewId) ||
                    "com.google.android.gsf.notouch:id/setup_scroll_list".equals(viewId) ||
                    "com.android.vending:id/setup_scroll_list".equals(viewId);
        }
    };

    private AccessibilityNodeInfoUtils() {
        // This class is not instantiable.
    }

    /**
     * Gets the text of a <code>node</code> by returning the content description
     * (if available) or by returning the text.
     *
     * @param node The node.
     * @return The node text.
     */
    public static CharSequence getNodeText(AccessibilityNodeInfoCompat node) {
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

        final CharSequence text = node.getText();
        if (!TextUtils.isEmpty(text)
                && (TextUtils.getTrimmedLength(text) > 0)) {
            return text;
        }

        return null;
    }

    public static List<AccessibilityActionCompat> getCustomActions(
            AccessibilityNodeInfoCompat node) {
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

    /**
     * Gets the text of a <code>node</code> by returning the content description
     * (if available) or by returning the text. Will use the specified
     * <code>CustomLabelManager</code> as a fall back if both are null.
     *
     * @param node The node.
     * @param labelManager The label manager.
     * @return The node text.
     */
    public static CharSequence getNodeText(AccessibilityNodeInfoCompat node,
            CustomLabelManager labelManager) {
        CharSequence text = AccessibilityNodeInfoUtils.getNodeText(node);
        if (!TextUtils.isEmpty(text)) {
            return text;
        }

        if (labelManager != null && labelManager.isInitialized()) {
            Label label = labelManager.getLabelForViewIdFromCache(
                    node.getViewIdResourceName());
            if (label != null) {
                return label.getText();
            }
        }
        return null;
    }

    /**
     * Returns the root node of the tree containing {@code node}.
     */
    public static AccessibilityNodeInfoCompat getRoot(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return null;
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

    /**
     * Returns whether a node should receive focus from focus traversal or touch
     * exploration. One of the following must be true:
     * <ul>
     * <li>The node is actionable (see
     * {@link #isActionableForAccessibility(AccessibilityNodeInfoCompat)})</li>
     * <li>The node is a top-level list item (see
     * {@link #isTopLevelScrollItem(AccessibilityNodeInfoCompat)})</li>
     * </ul>
     *
     * @param node The node to check.
     * @return {@code true} of the node is accessibility focusable.
     */
    public static boolean isAccessibilityFocusable(AccessibilityNodeInfoCompat node) {
        Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        try {
            return isAccessibilityFocusableInternal(node, null, visitedNodes);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        }
    }

    private static boolean isAccessibilityFocusableInternal(AccessibilityNodeInfoCompat node,
                                    Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
                                    Set<AccessibilityNodeInfoCompat> visitedNodes) {
        if (node == null) {
            return false;
        }

        // Never focus invisible nodes.
        if (!isVisible(node)) {
            return false;
        }

        // Always focus "actionable" nodes.
        if (isActionableForAccessibility(node)) {
            return true;
        }

        return isTopLevelScrollItem(node) &&
                isSpeakingNode(node, speakingNodeCache, visitedNodes);
    }

    /**
     * Returns whether a node should receive accessibility focus from
     * navigation. This method should never be called recursively, since it
     * traverses up the parent hierarchy on every call.
     *
     * @see #findFocusFromHover(AccessibilityNodeInfoCompat)
     */
    public static boolean shouldFocusNode(AccessibilityNodeInfoCompat node) {
        return shouldFocusNode(node, null, true);
    }

    public static boolean shouldFocusNode(final AccessibilityNodeInfoCompat node,
            final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache) {
        return shouldFocusNode(node, speakingNodeCache, true);
    }

    public static boolean shouldFocusNode(final AccessibilityNodeInfoCompat node,
            final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
            boolean checkChildren) {
        if (node == null) {
            return false;
        }

        // Inside views that support web navigation, we delegate focus to the view itself and
        // assume that it navigates to and focuses the correct elements.
        if (WebInterfaceUtils.supportsWebActions(node)) {
            return true;
        }

        if (!isVisible(node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Don't focus, node is not visible");
            return false;
        }

        // Only allow node with same bounds as window if it is clickable or leaf.
        if (areBoundsIdenticalToWindow(node) && !isClickable(node) && node.getChildCount() > 0) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Don't focus, node bounds are same as window root node bounds");
            return false;
        }

        HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        try {
            boolean accessibilityFocusable =
                    isAccessibilityFocusableInternal(node, speakingNodeCache, visitedNodes);

            if (!checkChildren) {
                // End of the line. Don't check children and don't allow any recursion.
                return accessibilityFocusable;
            }

            if (accessibilityFocusable) {
                AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
                visitedNodes.clear();
                // TODO: This may still result in focusing non-speaking nodes, but it
                // won't prevent unlabeled buttons from receiving focus.
                if (!hasVisibleChildren(node)) {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                            "Focus, node is focusable and has no visible children");
                    return true;
                } else if (isSpeakingNode(node, speakingNodeCache, visitedNodes)) {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                            "Focus, node is focusable and has something to speak");
                    return true;
                } else {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                            "Don't focus, node is focusable but has nothing to speak");
                    return false;
                }
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        }

        // If this node has no focusable ancestors, but it still has text,
        // then it should receive focus from navigation and be read aloud.
        NodeFilter filter = new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
                return shouldFocusNode(node, speakingNodeCache, false);
            }
        };

        if (!hasMatchingAncestor(node, filter) && hasText(node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Focus, node has text and no focusable ancestors");
            return true;
        }

        LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                "Don't focus, failed all focusability tests");
        return false;
    }

    /**
     * Returns the node that should receive focus from hover by starting from
     * the touched node and calling {@link #shouldFocusNode} at each level of
     * the view hierarchy.
     */
    public static AccessibilityNodeInfoCompat findFocusFromHover(
            AccessibilityNodeInfoCompat touched) {
        return AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(touched,
                FILTER_SHOULD_FOCUS);
    }

    private static boolean isSpeakingNode(AccessibilityNodeInfoCompat node,
                                      Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
                                      Set<AccessibilityNodeInfoCompat> visitedNodes) {
        if (speakingNodeCache != null && speakingNodeCache.containsKey(node)) {
            return speakingNodeCache.get(node);
        }

        boolean result = false;
        if (hasText(node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, has text");
            result = true;
        } else if (node.isCheckable()) { // Special case for check boxes.
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, is checkable");
            result = true;
        } else if (WebInterfaceUtils.hasLegacyWebContent(node)) { // Special case for web content.
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, has web content");
            result = true;
        } else if (hasNonActionableSpeakingChildren(node, speakingNodeCache, visitedNodes)) {
            // Special case for containers with non-focusable content.
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, has non-actionable speaking children");
            result = true;
        }

        if (speakingNodeCache != null) {
            speakingNodeCache.put(node, result);
        }

        return result;
    }

    private static boolean hasNonActionableSpeakingChildren(AccessibilityNodeInfoCompat node,
                                    Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache,
                                    Set<AccessibilityNodeInfoCompat> visitedNodes) {
        final int childCount = node.getChildCount();

        AccessibilityNodeInfoCompat child;

        // Has non-actionable, speaking children?
        for (int i = 0; i < childCount; i++) {
            child = node.getChild(i);

            if (child == null) {
                LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                        "Child %d is null, skipping it", i);
                continue;
            }

            if (!visitedNodes.add(child)) {
                child.recycle();
                return false;
            }

            // Ignore invisible nodes.
            if (!isVisible(child)) {
                LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                        "Child %d is invisible, skipping it", i);
                continue;
            }

            // Ignore focusable nodes.
            if (isAccessibilityFocusableInternal(child, speakingNodeCache, visitedNodes)) {
                LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                        "Child %d is focusable, skipping it", i);
                continue;
            }

            // Recursively check non-focusable child nodes.
            if (isSpeakingNode(child, speakingNodeCache, visitedNodes)) {
                LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                        "Does have actionable speaking children (child %d)", i);
                return true;
            }
        }

        LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                "Does not have non-actionable speaking children");
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

    /**
     * Returns whether a node is actionable. That is, the node supports one of
     * the following actions:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#isClickable()}
     * <li>{@link AccessibilityNodeInfoCompat#isFocusable()}
     * <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}
     * </ul>
     * This parities the system method View#isActionableForAccessibility(), which
     * was added in JellyBean.
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

        return supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_FOCUS,
                AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
                AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
    }

    public static boolean isSelfOrAncestorFocused(AccessibilityNodeInfoCompat node) {
        return node != null
                && (node.isAccessibilityFocused() || hasMatchingAncestor(node, new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat node) {
                return node.isAccessibilityFocused();
            }
        }));

    }

    /**
     * Returns whether a node is clickable. That is, the node supports at least one of the
     * following:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#isClickable()}</li>
     * <li>{@link AccessibilityNodeInfoCompat#ACTION_CLICK}</li>
     * </ul>
     *
     * @param node The node to examine.
     * @return {@code true} if node is clickable.
     */
    public static boolean isClickable(AccessibilityNodeInfoCompat node) {
        return node != null && (node.isClickable()
                || supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK));
    }

    /**
     * Returns whether a node is long clickable. That is, the node supports at least one of the
     * following:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}</li>
     * <li>{@link AccessibilityNodeInfoCompat#ACTION_LONG_CLICK}</li>
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
     * Returns whether a node is expandable. That is, the node supports the following action:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#ACTION_EXPAND}</li>
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
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#ACTION_COLLAPSE}</li>
     * </ul>
     *
     * @param node The node to examine.
     * @return {@code true} if node is collapsible.
     */
    public static boolean isCollapsible(AccessibilityNodeInfoCompat node) {
        return node != null && supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_COLLAPSE);
    }

    /**
     * Returns whether a node is editable.
     *
     * @param node The node to examine.
     * @return {@code true} if node is editable.
     */
    public static boolean isEditable(AccessibilityNodeInfoCompat node) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return ((AccessibilityNodeInfo) node.getInfo()).isEditable();
        } else {
            return Role.getRole(node) == Role.ROLE_EDIT_TEXT ||
                    nodeMatchesClassByName(node,
                            "com.google.android.search.searchplate.SimpleSearchText");
        }
    }

    /**
     * Check whether a given node has a scrollable ancestor.
     *
     * @param node The node to examine.
     * @return {@code true} if one of the node's ancestors is scrollable.
     */
    public static boolean hasMatchingAncestor(AccessibilityNodeInfoCompat node,
                                               NodeFilter filter) {
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
     * Check whether a given node or any of its ancestors matches the given filter.
     *
     * @param node The node to examine.
     * @param filter The filter to match the nodes against.
     * @return {@code true} if the node or one of its ancestors matches the filter.
     */
    public static boolean isOrHasMatchingAncestor(AccessibilityNodeInfoCompat node,
            NodeFilter filter) {
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

    /**
     * Check whether a given node has any descendant matching a given filter.
     */
    public static boolean hasMatchingDescendant(AccessibilityNodeInfoCompat node,
            NodeFilter filter) {
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
     * Returns the {@code node} if it matches the {@code filter}, or the first
     * matching ancestor. Returns {@code null} if no nodes match.
     */
    public static AccessibilityNodeInfoCompat getSelfOrMatchingAncestor(
            AccessibilityNodeInfoCompat node, NodeFilter filter) {
        if (node == null) {
            return null;
        }

        if (filter.accept(node)) {
            return AccessibilityNodeInfoCompat.obtain(node);
        }

        return getMatchingAncestor(node, filter);
    }

    /**
     * Returns the {@code node} if it matches the {@code filter}, or the first
     * matching ancestor, ending the ancestor search once it reaches {@code end}.
     * The search is inclusive of {@code node} but exclusive of {@code end}.
     * If {@code node} equals {@code end}, then {@code node} is an eligible match.
     * Returns {@code null} if no nodes match.
     */
    public static AccessibilityNodeInfoCompat getSelfOrMatchingAncestor(
            AccessibilityNodeInfoCompat node,
            AccessibilityNodeInfoCompat end,
            NodeFilter filter) {
        if (node == null) {
            return null;
        }

        if (filter.accept(node)) {
            return AccessibilityNodeInfoCompat.obtain(node);
        }

        return getMatchingAncestor(node, end, filter);
    }

    /**
     * Returns the {@code node} if it matches the {@code filter}, or the first
     * matching descendant. Returns {@code null} if no nodes match.
     */
    public static AccessibilityNodeInfoCompat getSelfOrMatchingDescendant(
            AccessibilityNodeInfoCompat node, NodeFilter filter) {
        if (node == null) {
            return null;
        }

        if (filter.accept(node)) {
            return AccessibilityNodeInfoCompat.obtain(node);
        }

        return getMatchingDescendant(node, filter);
    }

    /**
     * Determines whether the two nodes are in the same branch; that is, they are equal or one
     * is the ancestor of the other.
     */
    public static boolean areInSameBranch(@Nullable final AccessibilityNodeInfoCompat node1,
            @Nullable final AccessibilityNodeInfoCompat node2) {
        if (node1 != null && node2 != null) {
            // Same node?
            if (node1.equals(node2)) {
                return true;
            }

            // Is node1 an ancestor of node2?
            NodeFilter matchNode1 = new NodeFilter() {
                @Override
                public boolean accept(AccessibilityNodeInfoCompat node) {
                    return node != null && node.equals(node1);
                }
            };
            if (AccessibilityNodeInfoUtils.hasMatchingAncestor(node2, matchNode1)) {
                return true;
            }

            // Is node2 an ancestor of node1?
            NodeFilter matchNode2 = new NodeFilter() {
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
     * Returns the first ancestor of {@code node} that matches the
     * {@code filter}. Returns {@code null} if no nodes match.
     */
    private static AccessibilityNodeInfoCompat getMatchingAncestor(AccessibilityNodeInfoCompat node,
                                                                   NodeFilter filter) {
        return getMatchingAncestor(node, null, filter);
    }

    /**
     * Returns the first ancestor of {@code node} that matches the {@code filter}, terminating the
     * search once it reaches {@code end}. The search is exclusive of both {@code node} and
     * {@code end}. Returns {@code null} if no nodes match.
     */
    private static AccessibilityNodeInfoCompat getMatchingAncestor(
            AccessibilityNodeInfoCompat node,
            AccessibilityNodeInfoCompat end,
            NodeFilter filter) {
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
     * Returns the number of ancestors matching the given filter. Does not include the current
     * node in the count, even if it matches the filter. If there is a cycle in the ancestor
     * hierarchy, then this method will return 0.
     */
    public static int countMatchingAncestors(AccessibilityNodeInfoCompat node, NodeFilter filter) {
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
     * Returns the first child (by depth-first search) of {@code node} that matches the
     * {@code filter}. Returns {@code null} if no nodes match.
     * The caller is responsible for recycling all nodes in {@code visitedNodes} and the node
     * returned by this method, if non-{@code null}.
     */
    private static AccessibilityNodeInfoCompat getMatchingDescendant(
            AccessibilityNodeInfoCompat node,
            NodeFilter filter,
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
                AccessibilityNodeInfoCompat childMatch =
                        getMatchingDescendant(child, filter, visitedNodes);
                if (childMatch != null) {
                    return childMatch;
                }
            } finally {
                child.recycle();
            }
        }

        return null;
    }

    private static AccessibilityNodeInfoCompat getMatchingDescendant(
            AccessibilityNodeInfoCompat node,
            NodeFilter filter) {
        final HashSet<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        try {
            return getMatchingDescendant(node, filter, visitedNodes);
        } finally {
            recycleNodes(visitedNodes);
        }
    }

    /**
     * Check whether a given node is scrollable.
     *
     * @param node The node to examine.
     * @return {@code true} if the node is scrollable.
     */
    private static boolean isScrollable(AccessibilityNodeInfoCompat node) {
        return node.isScrollable()
                || supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
                                     AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);

    }

    /**
     * Returns whether the specified node has text.
     * For the purposes of this check, any node with a CollectionInfo is considered to not have
     * text since its text and content description are used only for collection transitions.
     *
     * @param node The node to check.
     * @return {@code true} if the node has text.
     */
    private static boolean hasText(AccessibilityNodeInfoCompat node) {
        return node != null
                && node.getCollectionInfo() == null
                && (!TextUtils.isEmpty(node.getText())
                        || !TextUtils.isEmpty(node.getContentDescription()));

    }

    /**
     * Determines whether a node is a top-level item in a scrollable container.
     *
     * @param node The node to test.
     * @return {@code true} if {@code node} is a top-level item in a scrollable
     *         container.
     */
    public static boolean isTopLevelScrollItem(AccessibilityNodeInfoCompat node) {
        if (node == null) {
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
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M &&
                    FILTER_BROKEN_LISTS_TV_M.accept(parent)) {
                return false;
            }

            if (isScrollable(node)) {
                return true;
            }

            // AdapterView, ScrollView, and HorizontalScrollView are focusable
            // containers, but Spinner is a special case.
            // TODO: Rename or break up this method, since it actually returns
            // whether the parent is scrollable OR is a focusable container that
            // should not block its children from receiving focus.
            //noinspection SimplifiableIfStatement
            if (Role.getRole(parent) == Role.ROLE_DROP_DOWN_LIST) return false;

            // Top-level items in a scrolling pager are actually two levels down since the first
            // level items in pagers are the pages themselves.
            grandparent = parent.getParent();
            if (Role.getRole(grandparent) == Role.ROLE_PAGER) return true;

            return nodeMatchesAnyClassByType(parent, AdapterView.class, ScrollView.class,
                    HorizontalScrollView.class, CLASS_TOUCHWIZ_TWADAPTERVIEW) ||
                    nodeMatchesClassByName(parent, CLASS_RECYCLER_VIEW_CLASS_NAME);
        } finally {
            recycleNodes(parent, grandparent);
        }
    }

    /**
     * Determines if the current item is at the logical edge of a list by checking the
     * scrollable predecessors of the items going forwards and backwards.
     *
     * @param node The node to check.
     * @param traversalStrategy - traversal strategy that is used to define order of node
     * @return true if the current item is at the edge of a list.
     */
    public static boolean isEdgeListItem(AccessibilityNodeInfoCompat node,
                                         TraversalStrategy traversalStrategy) {
        return isEdgeListItem(node, TraversalStrategy.SEARCH_FOCUS_BACKWARD, null,
                traversalStrategy) || isEdgeListItem(node, TraversalStrategy.SEARCH_FOCUS_FORWARD,
                null, traversalStrategy);
    }

    /**
     * Determines if the current item is at the edge of a list by checking the
     * scrollable predecessors of the items in a relative or absolute direction.
     *
     * @param node The node to check.
     * @param direction The direction in which to check.
     * @param filter (Optional) Filter used to validate list-type ancestors.
     * @param traversalStrategy - traversal strategy that is used to define order of node
     * @return true if the current item is at the edge of a list.
     */
    private static boolean isEdgeListItem(AccessibilityNodeInfoCompat node,
            @TraversalStrategy.SearchDirection int direction,
            NodeFilter filter, 
            TraversalStrategy traversalStrategy) {
        if (node == null) {
            return false;
        }

        int scrollAction = TraversalStrategyUtils.convertSearchDirectionToScrollAction(direction);
        if (scrollAction != 0) {
            NodeActionFilter scrollableFilter = new NodeActionFilter(scrollAction);
            NodeFilter comboFilter = scrollableFilter.and(filter);
            return isMatchingEdgeListItem(node, direction, comboFilter, traversalStrategy);
        }

        return false;
    }

    /**
     * Convenience method determining if the current item is at the edge of a
     * list and suitable autoscroll. Calls {@code isEdgeListItem} with
     * {@code FILTER_AUTO_SCROLL}.
     *
     * @param node The node to check.
     * @param direction The direction in which to check, one of:
     *            <ul>
     *            <li>{@code -1} to check backward
     *            <li>{@code 0} to check both backward and forward
     *            <li>{@code 1} to check forward
     *            </ul>
     * @param traversalStrategy - traversal strategy that is used to define order of node
     * @return true if the current item is at the edge of a list.
     */
    public static boolean isAutoScrollEdgeListItem(AccessibilityNodeInfoCompat node,
                                                   int direction,
                                                   TraversalStrategy traversalStrategy) {
        return isEdgeListItem(node, direction, FILTER_AUTO_SCROLL, traversalStrategy);
    }

    /**
     * Utility method for determining if a searching past a particular node will
     * fall off the edge of a scrollable container.
     *
     * @param cursor Node to check.
     * @param direction The direction in which to move from the cursor.
     * @param filter Filter used to validate list-type ancestors.
     * @param traversalStrategy - traversal strategy that is used to define order of node
     * @return {@code true} if focusing search in the specified direction will
     *         fall off the edge of the container.
     */
    private static boolean isMatchingEdgeListItem(AccessibilityNodeInfoCompat cursor,
            @TraversalStrategy.SearchDirection int direction,
            NodeFilter filter,
            TraversalStrategy traversalStrategy) {
        AccessibilityNodeInfoCompat ancestor = null;
        AccessibilityNodeInfoCompat nextFocusNode = null;
        AccessibilityNodeInfoCompat searchedAncestor = null;

        try {
            ancestor = getMatchingAncestor(cursor, filter);
            if (ancestor == null) {
                // Not contained in a scrollable list.
                return false;
            }

            nextFocusNode = searchFocus(traversalStrategy, cursor, direction, FILTER_SHOULD_FOCUS);
            if ((nextFocusNode == null) || nextFocusNode.equals(ancestor)) {
                // Can't move from this position.
                return true;
            }

            // if nextFocusNode is in WebView and not visible to user we still could set
            // accessibility  focus on it and WebView scrolls itself to show newly focused item
            // on the screen. But there could be situation that node is inside WebView bounds but
            // WebView is [partially] outside the screen bounds. In that case we don't ask WebView
            // to set accessibility focus but try to scroll scrollable parent to get the WebView
            // with nextFocusNode inside it to the screen bounds.
            if (!nextFocusNode.isVisibleToUser() &&
                    WebInterfaceUtils.hasNativeWebContent(nextFocusNode)) {
                AccessibilityNodeInfoCompat webViewNode = getMatchingAncestor(nextFocusNode,
                        new NodeFilter() {
                    @Override
                    public boolean accept(AccessibilityNodeInfoCompat node) {
                        return Role.getRole(node) == Role.ROLE_WEB_VIEW;
                    }
                });

                if (webViewNode != null && (!webViewNode.isVisibleToUser() ||
                        isNodeInBoundsOfOther(webViewNode, nextFocusNode))) {
                    return true;
                }
            }

            searchedAncestor = getMatchingAncestor(nextFocusNode, filter);
            while (searchedAncestor != null) {
                if (ancestor.equals(searchedAncestor)) {
                    return false;
                }
                AccessibilityNodeInfoCompat temp = searchedAncestor;
                searchedAncestor = getMatchingAncestor(searchedAncestor, filter);
                temp.recycle();
            }
            // Moves outside of the scrollable container.
            return true;
        } finally {
            recycleNodes(ancestor, nextFocusNode, searchedAncestor);
        }
    }

    private static boolean isNodeInBoundsOfOther(AccessibilityNodeInfoCompat outerNode,
                                                 AccessibilityNodeInfoCompat innerNode) {
        if (outerNode == null || innerNode == null) {
            return false;
        }

        Rect outerRect = new Rect();
        Rect innerRect = new Rect();
        outerNode.getBoundsInScreen(outerRect);
        innerNode.getBoundsInScreen(innerRect);

        if (outerRect.top > innerRect.bottom || outerRect.bottom < innerRect.top) {
            return false;
        }

        //noinspection RedundantIfStatement
        if (outerRect.left > innerRect.right || outerRect.right < innerRect.left) {
            return false;
        }

        return true;
    }

    public static boolean hasAncestor(AccessibilityNodeInfoCompat node,
                                      final AccessibilityNodeInfoCompat targetAncestor) {
        if (node == null || targetAncestor == null) {
            return false;
        }

        NodeFilter filter = new NodeFilter() {
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

    /**
     * Determines if the generating class of an
     * {@link AccessibilityNodeInfoCompat} matches any of the given
     * {@link Class}es by type.
     *
     * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by
     *            the accessibility framework.
     * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object
     *         matches the {@link Class} by type or inherited type,
     *         {@code false} otherwise.
     * @param referenceClasses A variable-length list of {@link Class} objects
     *            to match by type or inherited type.
     */
    private static boolean nodeMatchesAnyClassByType(AccessibilityNodeInfoCompat node,
                                                     Class<?>... referenceClasses) {
        if (node == null)
            return false;

        for (Class<?> referenceClass : referenceClasses) {
            if (ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClass)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if the class of an {@link AccessibilityNodeInfoCompat} matches
     * a given {@link Class} by package and name.
     *
     * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by
     *            the accessibility framework.
     * @param referenceClassName A class name to match.
     * @return {@code true} if the {@link AccessibilityNodeInfoCompat} matches
     *         the class name.
     */
    public static boolean nodeMatchesClassByName(AccessibilityNodeInfoCompat node,
                                                 CharSequence referenceClassName) {
        return node != null &&
                ClassLoadingCache.checkInstanceOf(node.getClassName(), referenceClassName);

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
    public static void recycleNodes(AccessibilityNodeInfoCompat... nodes) {
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
     * Returns {@code true} if the node supports at least one of the specified
     * actions. To check whether a node supports multiple actions, combine them
     * using the {@code |} (logical OR) operator.
     *
     * Note: this method will check against the getActions() method of AccessibilityNodeInfo, which
     * will not contain information for actions introduced in API level 21 or later.
     *
     * @param node The node to check.
     * @param actions The actions to check.
     * @return {@code true} if at least one action is supported.
     */
    public static boolean supportsAnyAction(AccessibilityNodeInfoCompat node,
            int... actions) {
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
     *
     */
    public static boolean supportsAction(AccessibilityNodeInfoCompat node, int action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
        } else {
            // On < API 21, actions aren't guaranteed to appear in getActionsList(), so we need to
            // check getActions() instead.
            return (node.getActions() & action) == action;
        }
    }

    /**
     * Returns the result of applying a filter using breadth-first traversal.
     *
     * @param node The root node to traverse from.
     * @param filter The filter to satisfy.
     * @return The first node reached via BFS traversal that satisfies the
     *         filter.
     */
    public static AccessibilityNodeInfoCompat searchFromBfs(AccessibilityNodeInfoCompat node,
                                                            NodeFilter filter) {
        if (node == null) {
            return null;
        }

        final LinkedList<AccessibilityNodeInfoCompat> queue = new LinkedList<>();
        Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();

        queue.add(AccessibilityNodeInfoCompat.obtain(node));

        try {
            while (!queue.isEmpty()) {
                final AccessibilityNodeInfoCompat item = queue.removeFirst();
                visitedNodes.add(item);

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

    /**
     * Search focus that satisfied specified node filter from currentFocus to specified direction
     * according to OrderTraversal strategy
     * @param traversal - order traversal strategy
     * @param currentFocus - node that is starting point of focus search
     * @param direction - direction the target focus is searching to
     * @param filter - filters focused node candidate
     * @return node that could be focused next
     */
    public static AccessibilityNodeInfoCompat searchFocus(TraversalStrategy traversal,
            AccessibilityNodeInfoCompat currentFocus,
            @TraversalStrategy.SearchDirection int direction,
            NodeFilter filter) {
        if (traversal == null || currentFocus == null) {
            return null;
        }

        if (filter == null) {
            filter = DEFAULT_FILTER;
        }

        AccessibilityNodeInfoCompat targetNode = AccessibilityNodeInfoCompat.obtain(currentFocus);
        Set<AccessibilityNodeInfoCompat> seenNodes = new HashSet<>();
        try {
            do {
                seenNodes.add(targetNode);
                targetNode = traversal.findFocus(targetNode, direction);

                if (seenNodes.contains(targetNode)) {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.ERROR,
                            "Found duplicate during traversal: %s", targetNode.getInfo());
                    return null;
                }
            } while (targetNode != null && !filter.accept(targetNode));
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(seenNodes);
        }

        return targetNode;
    }

    /**
     * Returns a fresh copy of {@code node} with properties that are
     * less likely to be stale.  Returns {@code null} if the node can't be
     * found anymore.
     */
    public static AccessibilityNodeInfoCompat refreshNode(
        AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
            nodeCopy.refresh();
            return nodeCopy;
        }

        AccessibilityNodeInfoCompat result = refreshFromChild(node);
        if (result == null) {
            result = refreshFromParent(node);
        }

        return result;
    }

    private static AccessibilityNodeInfoCompat refreshFromChild(
            AccessibilityNodeInfoCompat node) {
        if (node.getChildCount() > 0) {
            AccessibilityNodeInfoCompat firstChild = node.getChild(0);
            if (firstChild != null) {
                AccessibilityNodeInfoCompat parent = firstChild.getParent();
                firstChild.recycle();
                if (node.equals(parent)) {
                    return parent;
                } else {
                    recycleNodes(parent);
                }
            }
        }
        return null;
    }

    private static AccessibilityNodeInfoCompat refreshFromParent(
            AccessibilityNodeInfoCompat node) {
        AccessibilityNodeInfoCompat parent = node.getParent();
        if (parent != null) {
            try {
                int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    AccessibilityNodeInfoCompat child = parent.getChild(i);
                    if (node.equals(child)) {
                        return child;
                    }
                    recycleNodes(child);
                }
            } finally {
                parent.recycle();
            }
        }
        return null;
    }

    /**
     * Returns a fresh copy of node by traversing the given window for a similar node.
     * For example, the node that you want might be in a popup window that has closed and re-opened,
     * causing the accessibility IDs of its views to be different.
     * Note: you must recycle the node that is returned from this method.
     */
    public static AccessibilityNodeInfoCompat refreshNodeFuzzy(
            final AccessibilityNodeInfoCompat node,
            AccessibilityWindowInfo window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }

        if (window == null || node == null) {
            return null;
        }

        AccessibilityNodeInfo root = window.getRoot();
        if (root == null) {
            return null;
        }

        NodeFilter similarFilter = new NodeFilter() {
            @Override
            public boolean accept(AccessibilityNodeInfoCompat other) {
                return other != null && TextUtils.equals(node.getText(), other.getText());
            }
        };

        AccessibilityNodeInfoCompat rootCompat = new AccessibilityNodeInfoCompat(root);
        try {
            return getMatchingDescendant(rootCompat, similarFilter);
        } finally {
            rootCompat.recycle();
        }
    }

    /**
     * Helper method that returns {@code true} if the specified node is visible
     * to the user
     */
    public static boolean isVisible(AccessibilityNodeInfoCompat node) {
        return node != null && (node.isVisibleToUser() || WebInterfaceUtils.isWebContainer(node));
    }

    /**
     * Determines whether the specified node has bounds identical to the bounds of its window.
     */
    private static boolean areBoundsIdenticalToWindow(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        AccessibilityWindowInfoCompat window = node.getWindow();
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
     * Returns the node to which the given node's window is anchored, if there is an anchor.
     * Note: you must recycle the node that is returned from this method.
     */
    public static AccessibilityNodeInfoCompat getAnchor(
            @Nullable AccessibilityNodeInfoCompat node) {
        if (!BuildCompat.isAtLeastN()) {
            return null;
        }

        if (node == null) {
            return null;
        }

        AccessibilityNodeInfo nativeNode = (AccessibilityNodeInfo) node.getInfo();
        if (nativeNode == null) {
            return null;
        }

        AccessibilityWindowInfo nativeWindow = nativeNode.getWindow();
        if (nativeWindow == null) {
            return null;
        }

        AccessibilityNodeInfo nativeAnchor = nativeWindow.getAnchor();
        if (nativeAnchor == null) {
            return null;
        }

        return new AccessibilityNodeInfoCompat(nativeAnchor);
    }

    /**
     * Convenience class for a {@link NodeFilter} that checks whether nodes
     * support a specific action.
     */
    private static class NodeActionFilter extends NodeFilter {
        private final int mAction;

        /**
         * Creates a new action filter with the specified action mask.
         *
         * @param action The ID of the action to accept.
         */
        public NodeActionFilter(int action) {
            mAction = action;
        }

        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            return supportsAction(node, mAction);
        }
    }
}
