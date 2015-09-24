/*
 * Copyright (C) 2014 Google Inc.
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

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.traversal.NodeFocusFinder;
import com.android.utils.widget.SimpleOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Facilitates search of the nodes on the screen. Nodes are matched by description, and the
 * accessibility focus is moved to the matched node.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NodeSearch {
    /**
     * A formatter that determines the text to display given the search query, and the size at
     * which that text should be displayed.
     */
    public interface SearchTextFormatter {
        /**
         * Get the size, in pixels, at which the text returned by {@link #getDisplayText} should be
         * displayed.
         *
         * @return The desired text size.
         */
        public float getTextSize();

        /**
         * Get the text that should be displayed for a certain search query.
         *
         * @param queryText The search query.
         * @return The text that should be displayed for this search.
         */
        public String getDisplayText(String queryText);
    }

    /**
     * A filter that may exclude some nodes from the search.
     */
    public interface SearchResultFilter {
        /**
         * Check if a node should be excluded from the search.
         *
         * @param node The node that is being checked.
         * @return {@code true} if the node should be excluded, or {@code false} otherwise.
         */
        public boolean shouldFilter(AccessibilityNodeInfoCompat node);
    }

    /** The current search query. */
    private final StringBuilder mQueryText = new StringBuilder();

    /** The last matched node for the current search. */
    private final AccessibilityNodeInfoRef mMatchedNode = new AccessibilityNodeInfoRef();

    /** The accessibility service. */
    private final AccessibilityService mAccessibilityService;

    /** The label manager used to obtain node descriptions. */
    private final CustomLabelManager mLabelManager;

    /** The search result filters that should be used, if any. */
    private final List<SearchResultFilter> mFilters = new ArrayList<>();

    /** The overlay that is used to show the current search. */
    private final SearchOverlay mSearchOverlay;

    /** Whether or not there is an active search. */
    private boolean mActive;

    /**
     * Create a new NodeSearch instance.
     *
     * @param accessibilityService The accessibility service.
     * @param labelManager The custom label manager, or {@code null} if the API version does not
     * support custom labels.
     * @param textFormatter The formatter for the search display.
     */
    public NodeSearch(AccessibilityService accessibilityService,
            CustomLabelManager labelManager, SearchTextFormatter textFormatter) {
        mAccessibilityService = accessibilityService;
        mLabelManager = labelManager;
        mSearchOverlay = new SearchOverlay(accessibilityService, mQueryText, textFormatter);
    }

    /**
     * Create a new NodeSearch instance with filters.
     *
     * @param accessibilityService The accessibility service.
     * @param labelManager The custom label manager, or {@code null} if the API version does not
     * support custom labels.
     * @param textFormatter The formatter for the search display.
     * @param filters The filters that should be used.
     */
    public NodeSearch(AccessibilityService accessibilityService,
            CustomLabelManager labelManager, SearchTextFormatter textFormatter,
            List<SearchResultFilter> filters) {
        this(accessibilityService, labelManager, textFormatter);
        mFilters.addAll(filters);
    }

    /**
     * Start a search. Shows the search overlay.
     */
    public void startSearch() {
        mSearchOverlay.show();
        mActive = true;
    }

    /**
     * Stop the current search. Hides the search overlay and clears the search query.
     */
    public void stopSearch() {
        mMatchedNode.clear();
        mQueryText.setLength(0);
        mSearchOverlay.hide();
        mActive = false;
    }

    /**
     * Check if this instance is actively handling a search.
     *
     * @return {@code true} if there is an active search, or {@code false} otherwise.
     */
    public boolean isActive() {
        return mActive;
    }

    /**
     * Try to add some text to the search query. The text is only added if there are search results
     * for the new query, in which case the matched node may change.
     *
     * @param newText The text to add.
     * @return {@code true} if the text was added successfully, or {@code false} otherwise.
     */
    public boolean tryAddQueryText(CharSequence newText) {
        int initLength = mQueryText.length();
        mQueryText.append(newText);
        if (evaluateSearch()) {
            mSearchOverlay.refreshOverlay();
            return true;
        }
        // Search failed, go back to old text.
        mQueryText.delete(initLength, mQueryText.length());
        return false;
    }

    /**
     * Delete the last entered character if it exists. Has no effect on the matched node.
     *
     * @return {@code true} if a character was successfully deleted, or {@code false} if there was
     * no character to delete.
     */
    public boolean backspaceQueryText() {
        int length = mQueryText.length();
        if (length > 0) {
            mQueryText.deleteCharAt(length - 1);
            mSearchOverlay.refreshOverlay();
            return true;
        }

        return false;
    }

    /**
     * Get the current search query.
     *
     * @return The current search query. May be empty.
     */
    public String getCurrentQuery() {
        return mQueryText.toString();
    }

    /**
     * Get the text of the currently matched node.
     *
     * @return The text of the current match. May be empty, for example if no match has been found.
     */
    public String getMatchText() {
        final AccessibilityNodeInfoCompat currentMatch = mMatchedNode.get();
        if (currentMatch == null) {
            return "";
        }

        final CharSequence nodeText =
                AccessibilityNodeInfoUtils.getNodeText(currentMatch, mLabelManager);
        if (nodeText == null) {
            return "";
        }

        return nodeText.toString();
    }

    /**
     * Searches for the next result matching the current search query in the specified direction.
     * Ordering of results taken from linear navigation.
     *
     * @param direction The direction in which to search, {@link NodeFocusFinder#SEARCH_FORWARD} or
     * {@link NodeFocusFinder#SEARCH_BACKWARD}.
     * @return {@code true} if a match was found, or {@code false} otherwise.
     */
    public boolean nextResult(int direction) {
        AccessibilityNodeInfoRef next = new AccessibilityNodeInfoRef();
        next.reset(NodeFocusFinder.focusSearch(getCurrentNode(), direction));

        AccessibilityNodeInfoCompat focusableNext = null;
        try {
            while (next.get() != null) {
                if (nodeMatchesQuery(next.get())) {
                    // Even if the text matches, we need to make sure the node should be focused or
                    // has a parent that should be focused.
                    focusableNext = AccessibilityNodeInfoUtils.findFocusFromHover(next.get());

                    // Only count this as a match if it doesn't lead to the same parent.
                    if (focusableNext != null && !focusableNext.isAccessibilityFocused()) {
                        break;
                    }
                }
                next.reset(NodeFocusFinder.focusSearch(next.get(), direction));
                if (focusableNext != null) {
                    focusableNext.recycle();
                    focusableNext = null;
                }
            }

            if (focusableNext == null) {
                return false;
            }

            mMatchedNode.reset(next);
            return PerformActionUtils.performAction(focusableNext,
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            if (focusableNext != null) {
                focusableNext.recycle();
            }
            next.recycle();
        }
    }

    /**
     * Re-evaluate the search (perhaps, for example, because the screen content changed).
     */
    public void reEvaluateSearch() {
        mMatchedNode.reset(AccessibilityNodeInfoUtils.refreshNode(mMatchedNode.get()));
        evaluateSearch();
    }

    /**
     * Check if the search has found anything.
     *
     * @return {@code true} if a match has been found, or {@code false} otherwise.
     */
    public boolean hasMatch() {
        return !AccessibilityNodeInfoRef.isNull(mMatchedNode);
    }

    /**
     * Get the last matching node, if available, or the currently focused node otherwise.
     *
     * @return The last matched node, if a match was previously made. If no match has been made yet,
     * returns the currently focused node.
     */
    AccessibilityNodeInfoCompat getCurrentNode() {
        return AccessibilityNodeInfoRef.isNull(mMatchedNode)
                ? FocusFinder.getFocusedNode(mAccessibilityService, true) : mMatchedNode.get();
    }

    /**
     * Evaluates the search with the current query, searching from the last matched node forward.
     *
     * @return {@code true} if a match was found, or {@code false} otherwise.
     */
    private boolean evaluateSearch() {
        // First check if current selected result still matches.
        return nodeMatchesQuery(mMatchedNode.get()) || nextResult(NodeFocusFinder.SEARCH_FORWARD);

    }

    /**
     * Check if the specified node's description matches the current query text (case insensitive).
     *
     * @param node The node to check.
     * @return {@code true} if the node's description contains the current query text (ignoring
     * case), or {@code false} otherwise.
     */
    private boolean nodeMatchesQuery(AccessibilityNodeInfoCompat node) {
        // When no query text, consider everything a match.
        if (TextUtils.isEmpty(mQueryText)) {
            return AccessibilityNodeInfoUtils.shouldFocusNode(node);
        }

        if (node == null) {
            return false;
        }

        for (SearchResultFilter filter : mFilters) {
            if (filter.shouldFilter(node)) {
                return false;
            }
        }

        CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node, mLabelManager);
        if (nodeText == null) {
            return false;
        }

        String queryText = mQueryText.toString().toLowerCase();
        return nodeText.toString().toLowerCase().contains(queryText);
    }

    /* Package private methods for testing. */

    /* package */ void setQueryTextForTest(String text) {
        mQueryText.setLength(0);
        mQueryText.append(text);
    }

    /**
     * Controls the view that shows search overlay content.
     */
    private static class SearchOverlay extends SimpleOverlay implements DialogInterface {
        /** The search view. */
        private final SearchView mSearchView;

        /**
         * Creates the overlay with it initially invisible.
         */
        public SearchOverlay(Context context, StringBuilder queryText, SearchTextFormatter
                textFormatter) {
            super(context);

            mSearchView = new SearchView(context, queryText, textFormatter);

            // Make overlay appear on everything it can.
            LayoutParams params = getParams();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else {
                params.type = LayoutParams.TYPE_SYSTEM_ERROR;
            }
            setParams(params);

            setContentView(mSearchView);
        }

        /**
         * Called when the the overlay is shown.
         */
        @Override
        protected void onShow() {
            mSearchView.show();
        }

        /**
         * Refreshes the overlaid text display.
         */
        public void refreshOverlay() {
            mSearchView.invalidate();
        }

        @Override
        public void cancel() {
            dismiss();
        }

        @Override
        public void dismiss() {
            // This also effectively hides the search view.
            hide();
        }
    }

    /**
     * View handling drawing of incremental search overlay.
     */
    private static class SearchView extends SurfaceView {
        /** The colors to use for the gradient background. */
        private static final int GRADIENT_INNER_COLOR = 1996488704; // #7000
        private static final int GRADIENT_OUTER_COLOR = 1996488704;

        /** The surface holder onto which the view is drawn. */
        private SurfaceHolder mHolder;

        /** The background. */
        private final GradientDrawable mGradientBackground;

        /** The formatter for the text that will be displayed in this view. */
        private final SearchTextFormatter mTextFormatter;

        /**
         * The search query text. Synced to the StringBuilder in NodeSearch so we shouldn't
         * modify it here.
         */
        private final StringBuilder mQueryText;

        private final SurfaceHolder.Callback mSurfaceCallback =
                new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mHolder = holder;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mHolder = null;
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                invalidate();
            }
        };

        public SearchView(Context context, StringBuilder queryText, SearchTextFormatter
                textFormatter) {
            super(context);

            mQueryText = queryText;
            mTextFormatter = textFormatter;

            final SurfaceHolder holder = getHolder();
            holder.setFormat(PixelFormat.TRANSLUCENT);
            holder.addCallback(mSurfaceCallback);

            // Gradient colors.
            final int[] colors = new int[] {GRADIENT_INNER_COLOR, GRADIENT_OUTER_COLOR};
            mGradientBackground = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
            mGradientBackground.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        }

        public void show() {
            invalidate();
        }

        @Override
        public void invalidate() {
            super.invalidate();

            final SurfaceHolder holder = mHolder;
            if (holder == null) {
                return;
            }

            final Canvas canvas = holder.lockCanvas();
            if (canvas == null) {
                return;
            }

            // Clear the canvas.
            canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);

            if (getVisibility() != View.VISIBLE) {
                holder.unlockCanvasAndPost(canvas);
                return;
            }

            final int width = getWidth();
            final int height = getHeight();

            // Draw the pretty gradient background.
            mGradientBackground.setBounds(0, 0, width, height);
            mGradientBackground.draw(canvas);

            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStyle(Style.FILL);
            paint.setTextAlign(Align.CENTER);
            paint.setTextSize(mTextFormatter.getTextSize());
            canvas.drawText(
                    mTextFormatter.getDisplayText(mQueryText.toString()),
                    width / 2.0f,
                    height / 2.0f,
                    paint);

            holder.unlockCanvasAndPost(canvas);
        }
    }
}
