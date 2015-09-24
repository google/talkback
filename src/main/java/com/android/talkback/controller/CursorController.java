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

package com.android.talkback.controller;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.talkback.CursorGranularity;

/**
 * Handles screen reader cursor management.
 */
public interface CursorController {

    /**
     * Add a new listener for granularity change events.
     *
     * @param listener The new listener.
     */
    void addGranularityListener(GranularityChangeListener listener);

    void removeGranularityListener(GranularityChangeListener listener);

    void addScrollListener(ScrollListener listener);

    /**
     * Releases all resources held by this controller and save any persistent preferences.
     */
    void shutdown();

    /**
     * Clears and replaces focus for the currently focused node.
     *
     * @return Whether the current node was refocused.
     */
    boolean refocus();

    /**
     * Attempts to move to the next item using the current navigation mode.
     *
     * @param shouldWrap Whether navigating past the last item on the screen
     *            should wrap around to the first item on the screen.
     * @param shouldScroll Whether navigating past the last visible item in a
     *            scrollable container should automatically scroll to the next
     *            visible item.
     * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
     *                                    focused editable node if there is no node with
     *                                    accessibility focus
     * @return {@code true} if successful.
     */
    boolean next(boolean shouldWrap, boolean shouldScroll, boolean useInputFocusAsPivotIfEmpty);

    /**
     * Attempts to move to the previous item using the current navigation mode.
     *
     * @param shouldWrap Whether navigating past the last item on the screen
     *            should wrap around to the first item on the screen.
     * @param shouldScroll Whether navigating past the last visible item in a
     *            scrollable container should automatically scroll to the next
     *            visible item.
     * @param useInputFocusAsPivotIfEmpty Whether navigation should start from node that has input
     *                                    focused editable node if there is no node with
     *                                    accessibility focus
     * @return {@code true} if successful.
     */
    boolean previous(boolean shouldWrap, boolean shouldScroll, boolean useInputFocusAsPivotIfEmpty);

    /**
     * Attempts to jump to the first item that appears on the screen.
     *
     * @return {@code true} if successful.
     */
    boolean jumpToTop();

    /**
     * Attempts to jump to the last item that appears on the screen.
     *
     * @return {@code true} if successful.
     */
    boolean jumpToBottom();

    /**
     * Attempts to scroll forward within the current cursor.
     *
     * @return {@code true} if successful.
     */
    boolean more();

    /**
     * Attempts to scroll backward within the current cursor.
     *
     * @return {@code true} if successful.
     */
    boolean less();

    /**
     * Attempts to click on the center of the current cursor.
     *
     * @return {@code true} if successful.
     */
    boolean clickCurrent();

    /**
     * Attempts to move to the next reading level.
     *
     * @return {@code true} if successful.
     */
    boolean nextGranularity();

    /**
     * Attempts to move to the previous reading level.
     *
     * @return {@code true} if successful.
     */
    public boolean previousGranularity();

    /**
     * Adjust the cursor's granularity by moving it directly to the specified
     * granularity. If the granularity is {@link CursorGranularity#DEFAULT},
     * unlocks navigation; otherwise, locks navigation to the current cursor.
     *
     * @param granularity The {@link CursorGranularity} to request.
     * @return {@code true} if the granularity change was successful,
     *         {@code false} otherwise.
     */
    boolean setGranularity(CursorGranularity granularity, boolean fromUser);

    boolean setGranularity(CursorGranularity granularity, AccessibilityNodeInfoCompat node,
                                  boolean fromUser);

    /**
     * Sets the current cursor position.
     *
     * @param node The node to set as the cursor.
     * @return {@code true} if successful.
     */
    boolean setCursor(AccessibilityNodeInfoCompat node);

    /**
     * Sets the current state of selection mode for navigation within text
     * content. When enabled, the manager will attempt to extend selection
     * during navigation. If the target node of selection mode is not locked to
     * a granularity, calling this method will switch to
     * {@link CursorGranularity#CHARACTER}
     *
     * @param node The node on which selection mode should be enabled.
     * @param active {@code true} to activate selection mode, {@code false} to
     *            deactivate.
     */
    void setSelectionModeActive(AccessibilityNodeInfoCompat node, boolean active);

    /**
     * @return {@code true} if selection mode is active, {@code false}
     *         otherwise.
     */
    boolean isSelectionModeActive();

    /**
     * Clears the current cursor position.
     */
    void clearCursor();

    /**
     * Clears the given current cursor position.
     * Caller have to make sure currentNode get recycled
     * @param currentNode  given node to clear focus
     */
    void clearCursor(AccessibilityNodeInfoCompat currentNode);

    /**
     * Returns the node in the active window that has accessibility focus. If no
     * node has focus, or if the focused node is invisible, returns the root
     * node.
     * <p>
     * The client is responsible for recycling the resulting node.
     *
     * @return The node in the active window that has accessibility focus.
     */
    AccessibilityNodeInfoCompat getCursor();

    /**
     * Return the current granularity at the specified node, or
     * {@link CursorGranularity#DEFAULT} if none is set. Always returns
     * {@link CursorGranularity#DEFAULT} if granular navigation is not locked to
     * the specified node.
     *
     * @param node The node to check.
     * @return A cursor granularity.
     */
    CursorGranularity getGranularityAt(AccessibilityNodeInfoCompat node);

    /**
     * Listener for scroll events.
     */
    public interface ScrollListener {
        /**
         * Informs of a scroll caused by the CursorControler.
         *
         * @param action Direction of the scroll.
         */
        public void onScroll(AccessibilityNodeInfoCompat scrolledNode, int action);
    }


    /**
     * Listener for granularity changes.
     */
    public interface GranularityChangeListener {
        /**
         * Informs of a granularity change.
         *
         * @param granularity The new granularity after the change occurred.
         */
        void onGranularityChanged(CursorGranularity granularity);
    }
}
