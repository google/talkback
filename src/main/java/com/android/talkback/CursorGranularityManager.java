/*
 * Copyright (C) 2011 Google Inc.
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

package com.android.talkback;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.PerformActionUtils;
import com.android.utils.WebInterfaceUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Class to manage the navigation granularity for a given {@link AccessibilityNodeInfoCompat}.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class CursorGranularityManager {
    /** Unsupported movement within a granularity */
    private static final int NOT_SUPPORTED = -1;

    /** Movement within a granularity reached the edge of possible movement */
    public static final int HIT_EDGE = 0;

    /** Movement within a granularity was successful */
    public static final int SUCCESS = 1;

    /** Represents an increase in granularity */
    public static final int CHANGE_GRANULARITY_HIGHER = 1;

    /** Represents a decrease in granularity */
    public static final int CHANGE_GRANULARITY_LOWER = -1;

    /**
     * The list of navigable nodes. Computed by {@link #extractNavigableNodes}.
     */
    private final List<AccessibilityNodeInfoCompat> mNavigableNodes = new ArrayList<>();

    /**
     * The list of granularities supported by the navigable nodes. Computed
     * by {@link #extractNavigableNodes}.
     */
    private final ArrayList<CursorGranularity> mSupportedGranularities = new ArrayList<>();

    /** The parent context. */
    private final Context mContext;

    /**
     * The top-level node within which the user is navigating. This node's
     * navigable children are represented in {@link #mNavigableNodes}.
     */
    private AccessibilityNodeInfoCompat mLockedNode;

    /** The index of the current node within {@link #mNavigableNodes}. */
    private int mCurrentNodeIndex;

    // granularity that was chosen by user
    private CursorGranularity mSavedGranularity = CursorGranularity.DEFAULT;
    // it usually equals mSavedGranularity. But sometimes when we move focus between nodes some
    // nodes does not contain previously chosen granularity. In that case default granularity
    // is used for that node. but when we move to next node that support previously chosen
    // granularity (stored in mSavedGranularity) it switch back to that.
    private CursorGranularity mCurrentGranularity = CursorGranularity.DEFAULT;

    /** Used on API 18+ to track when text selection mode is active. */
    private boolean mSelectionModeActive;

    public CursorGranularityManager(Context context) {
        mContext = context;
    }

    /**
     * Releases resources associated with this object.
     */
    public void shutdown() {
        clear();
    }

    /**
     * Whether granular navigation is locked to {@code node}. If the currently
     * requested granularity is {@link CursorGranularity#DEFAULT} this will
     * always return {@code false}.
     *
     * @param node The node to check.
     * @return Whether navigation is locked to {@code node}.
     */
    public boolean isLockedTo(AccessibilityNodeInfoCompat node) {
        // If the requested granularity is default, don't report as locked.
        return mCurrentGranularity != CursorGranularity.DEFAULT
                && ((mLockedNode != null) && mLockedNode.equals(node));

    }

    /**
     * @return The current navigation granularity, or
     *         {@link CursorGranularity#DEFAULT} if the currently requested
     *         granularity is invalid.
     */
    public CursorGranularity getCurrentGranularity() {
        return mCurrentGranularity;
    }

    /**
     * Locks navigation within the specified node, if not already locked, and
     * sets the current granularity.
     *
     * @param granularity The requested granularity.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean setGranularityAt(
            AccessibilityNodeInfoCompat node, CursorGranularity granularity) {
        setLockedNode(node);

        if (!mSupportedGranularities.contains(granularity)) {
            mCurrentGranularity = CursorGranularity.DEFAULT;
            return false;
        }

        mSavedGranularity = granularity;
        mCurrentGranularity = granularity;
        return true;
    }

    /**
     * Sets the current state of selection mode for navigation within text
     * content. When enabled, the manager will attempt to extend selection
     * during navigation within a locked node.
     *
     * @param active {@code true} to activate selection mode, {@code false} to
     *            deactivate.
     */
    public void setSelectionModeActive(boolean active) {
        mSelectionModeActive = active;
    }

    /**
     * @return {@code true} if selection mode is active, {@code false}
     *         otherwise.
     */
    public boolean isSelectionModeActive() {
        return mSelectionModeActive;
    }

    /**
     * Locks navigation within the specified node, if not already locked, and
     * adjusts the current granularity in the specified direction.
     *
     * @param direction The direction to adjust granularity. One of
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_HIGHER} or
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_LOWER}
     * @return {@code true} if the granularity changed.
     */
    public boolean adjustGranularityAt(AccessibilityNodeInfoCompat node, int direction) {
        setLockedNode(node);

        final int count = mSupportedGranularities.size();
        int currentIndex = mSupportedGranularities.indexOf(mCurrentGranularity);
        int nextIndex;

        // Granularity adjustments always wrap around.
        nextIndex = (currentIndex + direction) % count;
        if (nextIndex < 0) {
            nextIndex = count - 1;
        }

        mCurrentGranularity = mSupportedGranularities.get(nextIndex);
        mSavedGranularity = mCurrentGranularity;
        return nextIndex != currentIndex;
    }

    /**
     * Clears the currently locked node and associated state variables. Recycles all currently held
     * nodes. Resets the requested granularity.
     */
    private void clear() {
        mCurrentNodeIndex = 0;
        mSupportedGranularities.clear();

        AccessibilityNodeInfoUtils.recycleNodes(mNavigableNodes);
        mNavigableNodes.clear();

        AccessibilityNodeInfoUtils.recycleNodes(mLockedNode);
        mLockedNode = null;

        setSelectionModeActive(false);
    }

    /**
     * As {@link #clear), but in addition keeps the granularity
     *
     * @param focusedNode The new node to be focused, {@code null} if there is no new node.
     */
    private void clearAndRetainGranularity(AccessibilityNodeInfoCompat focusedNode) {
        CursorGranularity currentGranularity = mSavedGranularity;
        clear();
        setGranularityAt(focusedNode, currentGranularity);
    }

    /**
     * Processes TYPE_VIEW_ACCESSIBILITY_FOCUSED events by clearing the
     * currently locked node and associated state variables if the provided node
     * is different from the locked node and from the same window.
     *
     * @param node The node to compare against the locked node.
     */
    public void onNodeFocused(AccessibilityNodeInfoCompat node) {
        if ((mLockedNode == null) || (node == null)) {
            return;
        }

        if (!mLockedNode.equals(node) && (mLockedNode.getWindowId() == node.getWindowId())) {
            clearAndRetainGranularity(node);
        }
    }

    public void startFromLastNode() {
        mCurrentNodeIndex = mNavigableNodes.size() - 1;
    }

    /**
     * Attempt to navigate within the currently locked node at the current
     * granularity. You should call either {@link #setGranularityAt} or
     * {@link #adjustGranularityAt} before calling this method.
     *
     * @return The result of navigation, which is always {@link #NOT_SUPPORTED}
     *         if there is no locked node or if the requested granularity is
     *         {@link CursorGranularity#DEFAULT}.
     */
    public int navigate(int action) {
        if (mLockedNode == null) {
            return NOT_SUPPORTED;
        }

        final CursorGranularity requestedGranularity = mCurrentGranularity;
        if ((requestedGranularity == null) || (requestedGranularity == CursorGranularity.DEFAULT)) {
            return NOT_SUPPORTED;
        }

        // Handle web granularity separately.
        if (requestedGranularity.isWebGranularity()) {
            return navigateWeb(action, requestedGranularity);
        }

        final Bundle arguments = new Bundle();
        final int count = mNavigableNodes.size();
        final int increment;

        switch (action) {
            case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
                increment = 1;
                if (mCurrentNodeIndex < 0) {
                    mCurrentNodeIndex++;
                }
                break;
            case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
                increment = -1;
                if (mCurrentNodeIndex >= count) {
                    mCurrentNodeIndex--;
                }
                break;
            default:
                return NOT_SUPPORTED;
        }

        while ((mCurrentNodeIndex >= 0) && (mCurrentNodeIndex < count)) {
            if (isSelectionModeActive()) {
                arguments.putBoolean(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true);
            }

            arguments.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    requestedGranularity.value);

            final AccessibilityNodeInfoCompat currentNode = mNavigableNodes.get(mCurrentNodeIndex);
            if (PerformActionUtils.performAction(currentNode, action, arguments)) {
                return SUCCESS;
            }

            LogUtils.log(this, Log.VERBOSE, "Failed to move with granularity %s, trying next node",
                    requestedGranularity.name());

            // If movement failed, advance to the next node and try again.
            mCurrentNodeIndex += increment;
        }

        return HIT_EDGE;
    }

    /**
     * Attempts to navigate web content at the specified granularity.
     *
     * @param action The accessibility action to perform, one of:
     *            <ul>
     *            <li>{@link AccessibilityNodeInfoCompat#ACTION_NEXT_AT_MOVEMENT_GRANULARITY}
     *            <li>{@link AccessibilityNodeInfoCompat#ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY}
     *            </ul>
     * @param granularity The granularity at which to navigate.
     * @return The result of navigation, which is always {@link #NOT_SUPPORTED}
     *         if there is no locked node or if the requested granularity is
     *         {@link CursorGranularity#DEFAULT}.
     */
    private int navigateWeb(int action, CursorGranularity granularity) {
        final int movementType;
        final String htmlElementType;

        switch (action) {
            case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
                movementType = WebInterfaceUtils.DIRECTION_FORWARD;
                break;
            case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
                movementType = WebInterfaceUtils.DIRECTION_BACKWARD;
                break;
            default:
                return NOT_SUPPORTED;

        }

        switch (granularity) {
            case WEB_SECTION:
                htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_SECTION;
                break;
            case WEB_LIST:
                htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_LIST;
                break;
            case WEB_CONTROL:
                htmlElementType = WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_CONTROL;
                break;
            default:
                return NOT_SUPPORTED;
        }

        if (!WebInterfaceUtils.performNavigationToHtmlElementAction(
                mLockedNode, movementType, htmlElementType)) {
            return HIT_EDGE;
        }

        return SUCCESS;
    }

    /**
     * Manages the currently locked node, clearing properties and loading
     * navigable children if necessary.
     *
     * @param node The node the user wishes to navigate within.
     */
    private void setLockedNode(AccessibilityNodeInfoCompat node) {
        if ((mLockedNode != null) && !mLockedNode.equals(node)) {
            clear();
        }

        if (mLockedNode == null) {
            mLockedNode = AccessibilityNodeInfoCompat.obtain(node);

            if (shouldClearSelection(mLockedNode)) {
                PerformActionUtils.performAction(mLockedNode,
                        AccessibilityNodeInfoCompat.ACTION_SET_SELECTION);
            }

            // Extract the navigable nodes and supported granularities.
            final List<CursorGranularity> supported = mSupportedGranularities;
            Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
            final int supportedMask = extractNavigableNodes(mLockedNode, mNavigableNodes,
                    visitedNodes);
            AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
            final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(
                    mContext, mLockedNode);

            CursorGranularity.extractFromMask(supportedMask, hasWebContent, supported);
        }
    }

    /**
     * Return whether selection should be cleared from the specified node when
     * locking navigation to it.
     *
     * @param node The node to check.
     * @return {@code true} if selection should be cleared.
     */
    private boolean shouldClearSelection(AccessibilityNodeInfoCompat node) {
        // EditText has has a stable cursor position, so don't clear selection.
        return !AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, EditText.class);
    }

    /**
     * Populates a list with the set of {@link CursorGranularity}s supported by
     * the specified root node and its navigable children.
     *
     * @param context The parent context.
     * @param root The root node from which to extract granularities.
     * @return A list of supported granularities.
     */
    public static List<CursorGranularity> getSupportedGranularities(
            Context context, AccessibilityNodeInfoCompat root) {
        final LinkedList<CursorGranularity> supported = new LinkedList<>();
        Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        final int supportedMask = extractNavigableNodes(root, null, visitedNodes);
        AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(context, root);

        CursorGranularity.extractFromMask(supportedMask, hasWebContent, supported);

        return supported;
    }

    /**
     * Extract the child nodes from the given root and adds them to the supplied
     * list of nodes.
     *
     * @param root The root node.
     * @param nodes The list of child nodes.
     * @return The mask of supported all granularities supported by the root and
     *         child nodes.
     */
    private static int extractNavigableNodes(AccessibilityNodeInfoCompat root,
            List<AccessibilityNodeInfoCompat> nodes,
            Set<AccessibilityNodeInfoCompat> visitedNodes) {
        if (root == null) {
            return 0;
        }

        AccessibilityNodeInfoCompat visitedNode = AccessibilityNodeInfoCompat.obtain(root);
        if (!visitedNodes.add(visitedNode)) {
            visitedNode.recycle();
            return 0;
        }

        if (nodes != null) {
            nodes.add(AccessibilityNodeInfoCompat.obtain(root));
        }

        int supportedGranularities = root.getMovementGranularities();

        // Don pull children from nodes with content descriptions.
        if (!TextUtils.isEmpty(root.getContentDescription())) {
            return supportedGranularities;
        }

        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final AccessibilityNodeInfoCompat child = root.getChild(i);
            if (child == null) {
                continue;
            }

            PerformActionUtils.performAction(child,
                    AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, null);

            // Only extract nodes that aren't reachable by traversal.
            if (!AccessibilityNodeInfoUtils.shouldFocusNode(child)) {
                supportedGranularities |= extractNavigableNodes(child, nodes, visitedNodes);
            }

            child.recycle();
        }

        return supportedGranularities;
    }
}
