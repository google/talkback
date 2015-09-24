/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeProviderCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.LinkedList;
import java.util.List;

/**
 * Implements a simplified version of an accessibility node provider.
 * <p>
 * This should be applied to the parent view using
 * {@link ViewCompat#setAccessibilityDelegate}:
 *
 * <pre>
 * mHelper = new ExploreByTouchHelper(context, someView);
 * ViewCompat.setAccessibilityDelegate(someView, mHelper);
 * </pre>
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
abstract class ExploreByTouchHelper extends AccessibilityDelegateCompat {

    /** Virtual node identifier value for invalid nodes. */
    static final int INVALID_ID = Integer.MIN_VALUE;

    /** Virtual node identifier value for the fake root node. */
    private static final int ROOT_ID = (Integer.MIN_VALUE + 1);

    /** The default class name used for virtual views. */
    private static final String DEFAULT_CLASS_NAME = "$VirtualView";

    // Temporary, reusable data structures.
    private final Rect mTempScreenRect = new Rect();
    private final Rect mTempParentRect = new Rect();
    private final Rect mTempVisibleRect = new Rect();
    private final int[] mTempGlobalRect = new int[2];

    /** The accessibility manager, used to check state and send events. */
    private final AccessibilityManager mManager;

    /** The view whose internal structure is exposed through this helper. */
    private final View mHost;

    /** The virtual view id for the currently focused item. */
    private int mFocusedVirtualViewId = INVALID_ID;

    /** The virtual view id for the currently hovered item. */
    private int mHoveredVirtualViewId = INVALID_ID;

    /**
     * Constructs a new Explore by Touch helper.
     *
     * @param host The view whose virtual hierarchy is exposed by this
     *            helper.
     */
    ExploreByTouchHelper(View host) {
        mHost = host;
        mManager = (AccessibilityManager) host.getContext()
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    /**
     * Returns the {@link AccessibilityNodeProviderCompat} for this helper.
     *
     * @return The accessibility node provider for this helper.
     */
    @Override
    public AccessibilityNodeProviderCompat getAccessibilityNodeProvider(View host) {
        return mNodeProvider;
    }

    /**
     * Dispatches hover {@link MotionEvent}s to the virtual view hierarchy when
     * the Explore by Touch feature is enabled.
     * <p>
     * This method should be called by overriding
     * {@link View#dispatchHoverEvent}:
     *
     * <pre>
     * &#64;Override
     * public boolean dispatchHoverEvent(MotionEvent event) {
     *   if (mHelper.dispatchHoverEvent(this, event) {
     *     return true;
     *   }
     *   return super.dispatchHoverEvent(event);
     * }
     * </pre>
     *
     * @param event The hover event to dispatch to the virtual view hierarchy.
     * @return Whether the hover event was handled.
     */
    public boolean dispatchHoverEvent(MotionEvent event) {
        if (!mManager.isTouchExplorationEnabled()) {
            return false;
        }

        int virtualViewId = getVirtualViewIdAt(event.getX(), event.getY());
        if (virtualViewId == INVALID_ID) {
            virtualViewId = ROOT_ID;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                setHoveredVirtualViewId(virtualViewId);
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                setHoveredVirtualViewId(virtualViewId);
                break;
        }

        return true;
    }

    /**
     * Populates an event of the specified type with information about an item
     * and attempts to send it up through the view hierarchy.
     * <p>
     * You should call this method after performing a user action that normally
     * fires an accessibility event, such as clicking on an item.
     *
     * <pre>public void performItemClick(T item) {
     *   ...
     *   sendEventForVirtualViewId(item.id, AccessibilityEvent.TYPE_VIEW_CLICKED);
     * }
     * </pre>
     *
     * @param virtualViewId The virtual view id for which to send an event.
     * @param eventType The type of event to send.
     * @return {@code true} if the event was sent successfully.
     */
    boolean sendEventForVirtualViewId(int virtualViewId, int eventType) {
        if ((virtualViewId == INVALID_ID) || !mManager.isEnabled()) {
            return false;
        }

        final ViewGroup group = (ViewGroup) mHost.getParent();
        if (group == null) {
            return false;
        }

        final AccessibilityEvent event;
        if (virtualViewId == ROOT_ID) {
            event = getEventForRoot(eventType);
        } else {
            event = getEventForVirtualViewId(virtualViewId, eventType);
        }

        return group.requestSendAccessibilityEvent(mHost, event);
    }

    /**
     * Notifies the accessibility framework that the properties of the parent
     * view have changed.
     * <p>
     * You <b>must</b> call this method after adding or removing items from the
     * parent view.
     */
    public void invalidateRoot() {
        invalidateVirtualViewId(ROOT_ID);
    }

    /**
     * Notifies the accessibility framework that the properties of a particular
     * item have changed.
     * <p>
     * You <b>must</b> call this method after changing any of the properties set
     * in {@link #populateNodeForVirtualViewId}.
     *
     * @param virtualViewId The virtual view id to invalidate.
     */
    void invalidateVirtualViewId(int virtualViewId) {
        sendEventForVirtualViewId(virtualViewId, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    /**
     * Sets the currently hovered item, sending hover accessibility events as
     * necessary to maintain the correct state.
     *
     * @param virtualViewId The virtual view id for the item currently being
     *            hovered, or {@code #INVALID_ID} if no item is hovered within
     *            the parent view.
     */
    private void setHoveredVirtualViewId(int virtualViewId) {
        if (mHoveredVirtualViewId == virtualViewId) {
            return;
        }

        final int previousVirtualViewId = mHoveredVirtualViewId;
        mHoveredVirtualViewId = virtualViewId;

        // Stay consistent with framework behavior by sending ENTER/EXIT pairs
        // in reverse order. This is accurate as of API 18.
        sendEventForVirtualViewId(virtualViewId, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        sendEventForVirtualViewId(previousVirtualViewId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
    }

    private AccessibilityEvent getEventForRoot(int eventType) {
        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        mHost.onInitializeAccessibilityEvent(event);

        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        record.setSource(mHost, ROOT_ID);

        return event;
    }

    /**
     * Constructs and returns an {@link AccessibilityEvent} populated with
     * information about the specified item.
     *
     * @param virtualViewId The virtual view id for the item for which to
     *            construct an event.
     * @param eventType The type of event to construct.
     * @return An {@link AccessibilityEvent} populated with information about
     *         the specified item.
     */
    private AccessibilityEvent getEventForVirtualViewId(int virtualViewId, int eventType) {
        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);

        // Ensure the client has good defaults.
        event.setEnabled(true);
        event.setClassName(mHost.getClass().getName() + DEFAULT_CLASS_NAME);

        // Allow the client to populate the event.
        populateEventForVirtualViewId(virtualViewId, event);

        if (event.getText().isEmpty() && TextUtils.isEmpty(event.getContentDescription())) {
            throw new RuntimeException(
                    "You must add text or a content description in populateEventForItem()");
        }

        // Don't allow the client to override these properties.
        event.setPackageName(mHost.getContext().getPackageName());

        // Virtual view hierarchies are only supported in API 16+.
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        record.setSource(mHost, virtualViewId);

        return event;
    }

    /**
     * Constructs and returns an {@link AccessibilityNodeInfoCompat} for the
     * parent view populated with its virtual descendants.
     *
     * @return An {@link AccessibilityNodeInfoCompat} for the parent view.
     */
    private AccessibilityNodeInfoCompat getNodeForHost() {
        // Since we don't want the parent to be focusable, but we can't remove
        // actions from a node, copy over the necessary fields.
        final AccessibilityNodeInfoCompat result = AccessibilityNodeInfoCompat.obtain(mHost);
        final AccessibilityNodeInfoCompat source = AccessibilityNodeInfoCompat.obtain(mHost);
        ViewCompat.onInitializeAccessibilityNodeInfo(mHost, source);

        // Copy over parent and screen bounds.
        source.getBoundsInParent(mTempParentRect);
        source.getBoundsInScreen(mTempScreenRect);
        result.setBoundsInParent(mTempParentRect);
        result.setBoundsInScreen(mTempScreenRect);

        // Set up the parent view, if applicable.
        final ViewParent parent = ViewCompat.getParentForAccessibility(mHost);
        if (parent instanceof View) {
            result.setParent((View) parent);
        }

        // Populate the minimum required fields.
        result.setVisibleToUser(source.isVisibleToUser());
        result.setPackageName(source.getPackageName());
        result.setClassName(source.getClassName());

        // Add the fake root node.
        result.addChild(mHost, ROOT_ID);

        return result;
    }

    /**
     * Constructs and returns an {@link AccessibilityNodeInfoCompat} for the
     * parent view populated with its virtual descendants.
     *
     * @return An {@link AccessibilityNodeInfoCompat} for the parent view.
     */
    private AccessibilityNodeInfoCompat getNodeForRoot() {
        // The root node is identical to the parent node, except that it is a
        // child of the parent view and is populated with virtual descendants.
        final AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.obtain(mHost);
        ViewCompat.onInitializeAccessibilityNodeInfo(mHost, node);

        // Add the virtual descendants.
        final LinkedList<Integer> virtualViewIds = new LinkedList<>();
        getVisibleVirtualViewIds(virtualViewIds);

        for (Integer virtualViewId : virtualViewIds) {
            node.addChild(mHost, virtualViewId);
        }

        // Set up the node as a child of the parent.
        node.setParent(mHost);
        node.setSource(mHost, ROOT_ID);

        return node;
    }

    /**
     * Constructs and returns an {@link AccessibilityNodeInfoCompat} for the
     * specified item. Automatically manages accessibility focus actions.
     * <p>
     * Allows the implementing class to specify most node properties, but
     * overrides the following:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#setPackageName}
     * <li>{@link AccessibilityNodeInfoCompat#setClassName}
     * <li>{@link AccessibilityNodeInfoCompat#setParent(View)}
     * <li>{@link AccessibilityNodeInfoCompat#setSource(View, int)}
     * <li>{@link AccessibilityNodeInfoCompat#setVisibleToUser}
     * <li>{@link AccessibilityNodeInfoCompat#setBoundsInScreen(Rect)}
     * </ul>
     * <p>
     * Uses the bounds of the parent view and the parent-relative bounding
     * rectangle specified by
     * {@link AccessibilityNodeInfoCompat#getBoundsInParent} to automatically
     * update the following properties:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#setVisibleToUser}
     * <li>{@link AccessibilityNodeInfoCompat#setBoundsInParent(Rect)}
     * </ul>
     *
     * @param virtualViewId The virtual view id for item for which to construct
     *            a node.
     * @return An {@link AccessibilityNodeInfoCompat} for the specified item.
     */
    private AccessibilityNodeInfoCompat getNodeForVirtualViewId(int virtualViewId) {
        final AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.obtain();

        // Ensure the client has good defaults.
        node.setEnabled(true);
        node.setClassName(mHost.getClass().getName() + DEFAULT_CLASS_NAME);

        // Allow the client to populate the node.
        populateNodeForVirtualViewId(virtualViewId, node);

        if (TextUtils.isEmpty(node.getText()) && TextUtils.isEmpty(node.getContentDescription())) {
            throw new RuntimeException(
                    "You must add text or a content description in populateNodeForVirtualViewId()");
        }

        // Don't allow the client to override these properties.
        node.setPackageName(mHost.getContext().getPackageName());
        node.setParent(mHost, ROOT_ID);
        node.setSource(mHost, virtualViewId);

        // Manage internal accessibility focus state.
        if (mFocusedVirtualViewId == virtualViewId) {
            node.setAccessibilityFocused(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            node.setAccessibilityFocused(false);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        }

        node.getBoundsInParent(mTempParentRect);
        if (mTempParentRect.isEmpty()) {
            throw new RuntimeException(
                    "You must set parent bounds in populateNodeForVirtualViewId()");
        }

        // Set the visibility based on the parent bound.
        if (intersectVisibleToUser(mTempParentRect)) {
            node.setVisibleToUser(true);
            node.setBoundsInParent(mTempParentRect);
        }

        // Calculate screen-relative bound.
        mHost.getLocationOnScreen(mTempGlobalRect);
        final int offsetX = mTempGlobalRect[0];
        final int offsetY = mTempGlobalRect[1];
        mTempScreenRect.set(mTempParentRect);
        mTempScreenRect.offset(offsetX, offsetY);
        node.setBoundsInScreen(mTempScreenRect);

        return node;
    }

    /**
     * Computes whether the specified {@link Rect} intersects with the visible
     * portion of its parent {@link View}. Modifies {@code localRect} to contain
     * only the visible portion.
     *
     * @param localRect A rectangle in local (parent) coordinates.
     * @return Whether the specified {@link Rect} is visible on the screen.
     */
    private boolean intersectVisibleToUser(Rect localRect) {
        // Missing or empty bounds mean this view is not visible.
        if ((localRect == null) || localRect.isEmpty()) {
            return false;
        }

        // Attached to invisible window means this view is not visible.
        if (mHost.getWindowVisibility() != View.VISIBLE) {
            return false;
        }

        // An invisible predecessor or one with alpha zero means
        // that this view is not visible to the user.
        Object current = this;
        while (current instanceof View) {
            final View view = (View) current;
            // We have attach info so this view is attached and there is no
            // need to check whether we reach to ViewRootImpl on the way up.
            if ((view.getAlpha() <= 0) || (view.getVisibility() != View.VISIBLE)) {
                return false;
            }
            current = view.getParent();
        }

        // If no portion of the parent is visible, this view is not visible.
        if (!mHost.getLocalVisibleRect(mTempVisibleRect)) {
            return false;
        }

        // Check if the view intersects the visible portion of the parent.
        return localRect.intersect(mTempVisibleRect);
    }

    /**
     * Exposes a virtual view hierarchy to the accessibility framework. Only
     * supported in API 16+.
     */
    private AccessibilityNodeProviderCompat mNodeProvider = new AccessibilityNodeProviderCompat() {
        @Override
        public AccessibilityNodeInfoCompat createAccessibilityNodeInfo(int virtualViewId) {
            if (virtualViewId == View.NO_ID) {
                return getNodeForHost();
            } else if (virtualViewId == ROOT_ID) {
                return getNodeForRoot();
            }

            return getNodeForVirtualViewId(virtualViewId);
        }

        @Override
        public boolean performAction(int virtualViewId, int action, Bundle arguments) {
            boolean handled = false;

            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS:
                    // Only handle the FOCUS action if it's placing focus on
                    // a different view that was previously focused.
                    if (mFocusedVirtualViewId != virtualViewId) {
                        mFocusedVirtualViewId = virtualViewId;
                        mHost.invalidate();
                        sendEventForVirtualViewId(virtualViewId,
                                AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                        handled = true;
                    }
                    break;
                case AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                    if (mFocusedVirtualViewId == virtualViewId) {
                        mFocusedVirtualViewId = INVALID_ID;
                    }
                    // Since we're managing focus at the parent level, we are
                    // likely to receive a FOCUS action before a CLEAR_FOCUS
                    // action. We'll give the benefit of the doubt to the
                    // framework and always handle FOCUS_CLEARED.
                    mHost.invalidate();
                    sendEventForVirtualViewId(virtualViewId,
                            AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    handled = true;
                    break;
                default:
                    // Let the node provider handle focus for the root node, but
                    // the root node should handle everything else by itself.
                    if (virtualViewId == View.NO_ID) {
                        return ViewCompat.performAccessibilityAction(mHost, action, arguments);
                    }
            }

            // Since the client implementation may want to do something special
            // when a FOCUS event occurs, let them handle all events.
            handled |= performActionForVirtualViewId(virtualViewId, action);

            return handled;
        }
    };

    /**
     * Returns the virtual view id for the item under the specified
     * parent-relative coordinates.
     *
     * @param x The parent-relative x coordinate.
     * @param y The parent-relative y coordinate.
     * @return The item under coordinates (x,y).
     */
    protected abstract int getVirtualViewIdAt(float x, float y);

    /**
     * Populates a list with the parent view's visible items. The ordering of
     * items within {@code virtualViewIds} specifies order of accessibility
     * focus traversal.
     *
     * @param virtualViewIds The list to populate with visible items.
     */
    protected abstract void getVisibleVirtualViewIds(List<Integer> virtualViewIds);

    /**
     * Populates an event with information about the specified item.
     * <p>
     * Developers <b>must</b> populate the following required fields:
     * <ul>
     * <li>event text, see {@link AccessibilityEvent#getText()} or
     * {@link AccessibilityEvent#setContentDescription(CharSequence)}
     * </ul>
     * <p>
     * The helper class automatically populates some required fields:
     * <ul>
     * <li>item class name, see
     * {@link AccessibilityEvent#setClassName(CharSequence)}
     * <li>package name, see
     * {@link AccessibilityEvent#setPackageName(CharSequence)}
     * <li>event source, see
     * {@link AccessibilityRecordCompat#setSource(View, int)}
     * </ul>
     *
     * @param virtualViewId The virtual view id for the item for which to
     *            populate the event.
     * @param event The event to populate.
     */
    protected abstract void populateEventForVirtualViewId(
            int virtualViewId, AccessibilityEvent event);

    /**
     * Populates a node with information about the specified item.
     * <p>
     * Developers <b>must</b> populate the following required fields:
     * <ul>
     * <li>event text, see
     * {@link AccessibilityNodeInfoCompat#setText(CharSequence)} or
     * {@link AccessibilityNodeInfoCompat#setContentDescription(CharSequence)}
     * <li>parent-relative bounds, see
     * {@link AccessibilityNodeInfoCompat#setBoundsInParent(Rect)}
     * </ul>
     * <p>
     * The helper class automatically populates some required fields:
     * <ul>
     * <li>item class name, see {@link AccessibilityNodeInfoCompat#setClassName}
     * <li>package name, see {@link AccessibilityNodeInfoCompat#setPackageName}
     * <li>parent view, see {@link AccessibilityNodeInfoCompat#setParent(View)}
     * <li>node source, see
     * {@link AccessibilityNodeInfoCompat#setSource(View, int)}
     * <li>visibility, see {@link AccessibilityNodeInfoCompat#setVisibleToUser}
     * <li>screen-relative bounds, see
     * {@link AccessibilityNodeInfoCompat#setBoundsInScreen(Rect)}
     * </ul>
     * <p>
     * The helper class also automatically handles accessibility focus
     * management by adding one of:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS}
     * <li>{@link AccessibilityNodeInfoCompat#ACTION_CLEAR_ACCESSIBILITY_FOCUS}
     * </ul>
     *
     * @param virtualViewId The virtual view id for the item for which to
     *            populate the node.
     * @param node The node to populate.
     */
    protected abstract void populateNodeForVirtualViewId(
            int virtualViewId, AccessibilityNodeInfoCompat node);

    /**
     * Performs an accessibility action on the specified item. See
     * {@link AccessibilityNodeInfoCompat#performAction(int, Bundle)}.
     * <p>
     * Developers <b>must</b> handle any actions added manually in
     * {@link #populateNodeForVirtualViewId}.
     * <p>
     * The helper class automatically handles focus management resulting from
     * {@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS} and
     * {@link AccessibilityNodeInfoCompat#ACTION_CLEAR_ACCESSIBILITY_FOCUS}.
     *
     * @param virtualViewId The virtual view id for the item on which to perform
     *            the action.
     * @param action The accessibility action to perform.
     * @return {@code true} if the action was performed successfully.
     */
    protected abstract boolean performActionForVirtualViewId(
            int virtualViewId, int action);
}
