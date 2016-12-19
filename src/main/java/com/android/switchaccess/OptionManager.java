/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.android.talkback.R;
import com.android.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages options in a tree of {@code OptionScanNodes} and traverses them as options are
 * selected.
 */
public class OptionManager implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int OPTION_INDEX_CLICK = 0;
    public static final int OPTION_INDEX_NEXT = 1;

    private final OverlayController mOverlayController;

    private final List<OptionManagerListener> mOptionManagerListeners = new ArrayList<>();

    /* TODO Clean up managing the styling information */
    private final Paint[] mOptionPaintArray;
    private final String[] mHighlightColorPrefKeys;
    private final String[] mHighlightColorDefaults;
    private final String[] mHighlightWeightPrefKeys;

    private OptionScanNode mRootNode = null;

    private OptionScanNode mCurrentNode = null;

    private boolean mOptionScanningEnabled = false;
    private ScanListener mScanListener;
    private boolean mStartScanAutomatically = false;

    /**
     * @param overlayController The controller for the overlay on which to present options
     */
    public OptionManager(OverlayController overlayController) {
        mOverlayController = overlayController;
        Context context = mOverlayController.getContext();
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
        mHighlightColorPrefKeys = context.getResources()
                .getStringArray(R.array.switch_access_highlight_color_pref_keys);
        mHighlightColorDefaults = context.getResources()
                .getStringArray(R.array.switch_access_highlight_color_defaults);
        mHighlightWeightPrefKeys = context.getResources()
                .getStringArray(R.array.switch_access_highlight_weight_pref_keys);
        mOptionPaintArray = new Paint[mHighlightColorPrefKeys.length];
        for (int i = 0; i < mOptionPaintArray.length; i++) {
            mOptionPaintArray[i] = new Paint();
            mOptionPaintArray[i].setStyle(Paint.Style.STROKE);
        }
        onSharedPreferenceChanged(prefs, null);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Clean up when this object is no longer needed
     */
    public void shutdown() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(
                mOverlayController.getContext());
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        if (mRootNode != null) {
            mRootNode.recycle();
        }
        mRootNode = null;
    }

    /**
     * Clear any traversal in progress and use the new tree for future traversals
     * @param newTreeRoot The root of the tree to traverse next
     */
    public void clearFocusIfNewTree(OptionScanNode newTreeRoot) {
        if (mRootNode == newTreeRoot) {
            return;
        }
        if (newTreeRoot != null && newTreeRoot.equals(mRootNode)) {
            newTreeRoot.recycle();
            return;
        }
        // new tree is different
        clearFocus();
        if (mRootNode != null) {
            mRootNode.recycle();
        }
        mRootNode = newTreeRoot;
        if (mStartScanAutomatically) {
            selectOption(0);
            for (int i = 0; i < mOptionManagerListeners.size(); i++) {
                OptionManagerListener listener = mOptionManagerListeners.get(i);
                listener.onOptionManagerStartedAutoScan();
            }
        }
    }

    /**
     * Traverse to the child node of the current node that has the specified index and take
     * whatever action is appropriate for that node. If nothing currently has focus, any
     * option moves to the root of the tree.
     * @param optionIndex The index of the child to traverse to. Out-of-bounds indices, such as
     * negative values or those above the index of the last child, cause focus to be reset.
     */
    public void selectOption(int optionIndex) {
        if (optionIndex < 0) {
            clearFocus();
            return;
        }

        /* Move to desired node */
        if (mCurrentNode == null) {
            if (mScanListener != null) {
                mScanListener.onScanStart();
            }
            mCurrentNode = mRootNode;
        } else {
            if (!(mCurrentNode instanceof OptionScanSelectionNode)) {
                /* This should never happen */
                clearFocus();
                return;
            }
            OptionScanSelectionNode selectionNode = (OptionScanSelectionNode) mCurrentNode;
            if (optionIndex >= selectionNode.getChildCount()) {
                // User pressed an option-scan switch for an index greater than this node's order
                if (mScanListener != null) {
                    mScanListener.onScanCompletedWithNoSelection();
                }
                clearFocus();
                return;
            }
            mCurrentNode = selectionNode.getChild(optionIndex);
        }

        onNodeFocused();
    }

    /**
     * Move up the tree to the parent of the current node.
     * @param wrap Controls wrapping when the parent is null. If {@code false}, the current node
     * will not change if the parent is null. If {@code true}, a node from the bottom of the
     * tree will be used instead of a null parent. The bottom node is chosen as the last
     * OptionScanSelectionNode found by repeatedly selecting {@code OPTION_INDEX_NEXT}. Note that
     * this result makes sense for most traditional scanning methods, but may not make perfect
     * sense for all trees.
     */
    public void moveToParent(boolean wrap) {
        if (mCurrentNode != null) {
            mCurrentNode = mCurrentNode.getParent();
            if (mCurrentNode == null) {
                clearFocus();
            } else {
                onNodeFocused();
            }
            return;
        } else if (!wrap) {
            return;
        }

        mCurrentNode = findLastSelectionNode();
        if (mCurrentNode == null) {
            clearFocus();
        } else {
            onNodeFocused();
        }
    }

    /**
     * Register a listener to be notified when focus is cleared
     * @param optionManagerListener A listener that should be called when focus is cleared
     */
    public void addOptionManagerListener(OptionManagerListener optionManagerListener) {
        mOptionManagerListeners.add(optionManagerListener);
    }

    /**
     * Support legacy long click key action.
     * Perform a long click on the currently selected item, if that is possible. Long click is
     * possible only if an AccessibilityNodeActionNode is the only thing highlighted, and if
     * the corresponding AccessibilityNodeInfo accepts the long click action.
     * If the long click goes through, reset the focus.
     */
    public void performLongClick() {
        SwitchAccessNodeCompat compat = findCurrentlyActiveNode();
        if (compat != null) {
            if (compat.performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)) {
                clearFocus();
            }
            compat.recycle();
        }
    }

    /**
     * Support legacy scroll key actions.
     * Perform a scroll on the currently selected item, if it is scrollable, or a scrollable parent
     * if one can be found. If the scroll action is accepted, focus is cleared.
     * @param scrollAction Either {@code AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD} or
     * {@code AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD}.
     */
    public void performScrollAction(int scrollAction) {
        SwitchAccessNodeCompat compat = findCurrentlyActiveNode();
        while (compat != null) {
            if (compat.isScrollable()) {
                if (compat.performAction(scrollAction)) {
                    clearFocus();
                }
                compat.recycle();
                return;
            }
            SwitchAccessNodeCompat parent = compat.getParent();
            compat.recycle();
            compat = parent;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Context context = mOverlayController.getContext();
        mOptionScanningEnabled = SwitchAccessPreferenceActivity.isOptionScanningEnabled(context);
        String defaultWeight =  context.getString(R.string.pref_highlight_weight_default);
        /* Configure highlighting */
        for (int i = 0; i < mOptionPaintArray.length; ++i) {
            /*
             * Always configure element 0 based on preferences. Only configure the others if we're
             * option scanning.
             */
            if ((i == 0) || mOptionScanningEnabled) {
                String hexStringColor =
                        prefs.getString(mHighlightColorPrefKeys[i], mHighlightColorDefaults[i]);
                int color = Integer.parseInt(hexStringColor, 16);
                mOptionPaintArray[i].setColor(color);
                mOptionPaintArray[i].setAlpha(255);

                String stringWeight = prefs.getString(mHighlightWeightPrefKeys[i], defaultWeight);
                int weight = Integer.valueOf(stringWeight);
                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                float strokeWidth =
                        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, weight, dm);
                mOptionPaintArray[i].setStrokeWidth(strokeWidth);
            } else {
                mOptionPaintArray[i].setColor(Color.TRANSPARENT);
            }
        }
        mStartScanAutomatically = prefs.getBoolean(
                context.getString(R.string.switch_access_auto_start_scan_key), false);
    }

    /**
     * Register a listener to notify of auto-scan activity
     * @param listener the listener to be set
     */
    public void setScanListener(ScanListener listener) {
        mScanListener = listener;
    }

    private void clearFocus() {
        mCurrentNode = null;
        mOverlayController.clearOverlay();
        for (OptionManagerListener listener : mOptionManagerListeners) {
            listener.onOptionManagerClearedFocus();
        }
    }

    private void onNodeFocused() {
        if (mScanListener != null) {
            if (mCurrentNode instanceof ClearFocusNode) {
                mScanListener.onScanCompletedWithNoSelection();
            } else if (mCurrentNode instanceof OptionScanActionNode) {
                mScanListener.onScanSelection();
            } else {
                mScanListener.onScanFocusChanged();
            }
        }
        mCurrentNode.performAction();

        /* TODO Any items that we want drawn on the screen could be directly grouped
         * into option groups when the tree is being constructed. That way the drawing of the
         * button or any other items would be data driven. */
        if (mCurrentNode instanceof OptionScanSelectionNode) {
            mOverlayController.clearOverlay();
            final OptionScanSelectionNode selectionNode = (OptionScanSelectionNode) mCurrentNode;
            if (mOptionScanningEnabled) {
                mOverlayController.drawMenuButton();
                /* showSelections() needs to know the location of the button in the screen to
                 * highlight it. Hence run it a handler to give the thread a chance to draw the
                 * overlay.
                 */
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        selectionNode.showSelections(mOverlayController, mOptionPaintArray);
                    }
                });
            } else {
                selectionNode.showSelections(mOverlayController, mOptionPaintArray);
            }
        } else {
            clearFocus();
        }
    }

    /*
     * Find exactly one {@code SwitchAccessNodeCompat} in the current tree
     * @return an {@code obtain}ed SwitchAccessNodeCompat if there is exactly one in the
     * current tree. Returns {@code null} otherwise.
     */
    private SwitchAccessNodeCompat findCurrentlyActiveNode() {
        if (!(mCurrentNode instanceof OptionScanSelectionNode)) {
            return null;
        }
        OptionScanNode startNode = ((OptionScanSelectionNode) mCurrentNode)
                .getChild(OPTION_INDEX_CLICK);
        Set<AccessibilityNodeActionNode> nodeSet = new HashSet<>();
        addAccessibilityNodeActionNodesToSet(startNode, nodeSet);
        SwitchAccessNodeCompat compat = null;
        for (AccessibilityNodeActionNode actionNode : nodeSet) {
            SwitchAccessNodeCompat actionNodeCompat = actionNode.getNodeInfoCompat();
            if (actionNodeCompat == null) {
                continue; // Should never happen
            }
            if (compat == null) {
                compat = actionNodeCompat;
            } else if (compat.equals(actionNodeCompat)) {
                actionNodeCompat.recycle();
            } else {
                compat.recycle();
                actionNodeCompat.recycle();
                return null;
            }
        }
        return compat;
    }

    /*
     * Find all AccessibilityNodeActionNodes in the tree rooted at the current selection
     */
    private void addAccessibilityNodeActionNodesToSet(
            OptionScanNode startNode, Set<AccessibilityNodeActionNode> nodeSet) {
        if (startNode instanceof AccessibilityNodeActionNode) {
            nodeSet.add((AccessibilityNodeActionNode) startNode);
        }
        if (startNode instanceof OptionScanSelectionNode) {
            OptionScanSelectionNode selectionNode = (OptionScanSelectionNode) startNode;
            for (int i = 0; i < selectionNode.getChildCount(); ++i) {
                addAccessibilityNodeActionNodesToSet(selectionNode.getChild(i), nodeSet);
            }
        }
    }

    private OptionScanNode findLastSelectionNode() {
        OptionScanNode newNode = mRootNode;
        if (!(newNode instanceof OptionScanSelectionNode)) {
            return null;
        }
        OptionScanNode possibleNewNode
                = ((OptionScanSelectionNode) newNode).getChild(OPTION_INDEX_NEXT);
        while (possibleNewNode instanceof OptionScanSelectionNode) {
            newNode = possibleNewNode;
            possibleNewNode = ((OptionScanSelectionNode) newNode).getChild(OPTION_INDEX_NEXT);
        }
        return newNode;
    }

    /**
     * Interface to monitor when focus is cleared
     */
    public interface OptionManagerListener {
        /** Called when scanning is automatically started */
        void onOptionManagerStartedAutoScan();

        /** Called when focus clears */
        void onOptionManagerClearedFocus();
    }

    /**
     * Interface to monitor the user's progress of scanning to desired items
     */
    public interface ScanListener {
        /** Called when scanning starts and the first highlighting is drawn */
        void onScanStart();
        /** Called when scanning reaches a new selection node and highlighting changes */
        void onScanFocusChanged();
        /** Called when scanning reaches an action node and an action is taken */
        void onScanSelection();
        /** Called when scanning completes without any action being taken */
        void onScanCompletedWithNoSelection();
    }
}
