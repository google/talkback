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

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.android.talkback.R;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.*;

/**
 * Builder that constructs a hierarchy to scan from a list of windows
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MultiWindowTreeBuilder implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Context mContext;
    private final RowColumnTreeBuilder mRowColumnTreeBuilder;
    private final LinearScanTreeBuilder mLinearScanTreeBuilder;
    private TreeBuilder mBuilderForViews;
    private TreeBuilder mBuilderForIMEs;
    private TalkBackOrderNDegreeTreeBuilder mOptionScanTreeBuilder;
    private boolean mOptionScanningEnabled = false;

    /**
     * @param context A valid context for interacting with the framework
     */
    public MultiWindowTreeBuilder(Context context,
            LinearScanTreeBuilder linearScanTreeBuilder,
            RowColumnTreeBuilder rowColumnTreeBuilder,
            TalkBackOrderNDegreeTreeBuilder talkBackOrderNDegreeTreeBuilder) {
        mContext = context;
        mLinearScanTreeBuilder = linearScanTreeBuilder;
        mRowColumnTreeBuilder = rowColumnTreeBuilder;
        mOptionScanTreeBuilder = talkBackOrderNDegreeTreeBuilder;
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mContext);
        onSharedPreferenceChanged(prefs, null);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Build a tree of nodes from a window list, including global actions
     * @param windowList The list of windows. This list is resorted and elements are removed to
     * match what will is included in the tree
     * @param service The service that will perform the global actions
     * @return A tree including nodes from all windows in the list
     */
    public OptionScanNode buildTreeFromWindowList(List<AccessibilityWindowInfo> windowList,
            AccessibilityService service) {
        OptionScanNode tree;
        List<GlobalActionNode> globalActions = GlobalActionNode.getGlobalActionList(service);
        /* Put global actions at the end of the tree */
        if (mOptionScanningEnabled) {
            tree = mOptionScanTreeBuilder.buildContextMenuTree(globalActions);
        } else {
            tree = LinearScanTreeBuilder.buildContextMenuTree(globalActions);
        }
        if (windowList != null) {
            List<SwitchAccessWindowInfo> wList =
                    SwitchAccessWindowInfo.convertZOrderWindowList(windowList);
            sortWindowListForTraversalOrder(wList);
            removeSystemButtonsWindowFromWindowList(wList);
            if (mOptionScanningEnabled) {
                return mOptionScanTreeBuilder.buildTreeFromWindowList(wList, tree);
            }
            for (SwitchAccessWindowInfo window : wList) {
                SwitchAccessNodeCompat windowRoot = window.getRoot();
                if (windowRoot != null) {
                    if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        tree = mBuilderForIMEs.buildTreeFromNodeTree(windowRoot, tree);
                    } else {
                        tree = mBuilderForViews.buildTreeFromNodeTree(windowRoot, tree);
                    }
                    windowRoot.recycle();
                }
            }
        }
        return tree;
    }

    /**
     * Sort windows so that the IME is traversed first, and the system windows last. Note that
     * the list comes out backwards, which makes it easy to iterate through it when building the
     * tree from the bottom up.
     * @param windowList The list to be sorted.
     */
    private static void sortWindowListForTraversalOrder(List<SwitchAccessWindowInfo> windowList) {
        Collections.sort(windowList, new Comparator<SwitchAccessWindowInfo>() {
            @Override
            public int compare(SwitchAccessWindowInfo arg0, SwitchAccessWindowInfo arg1) {
                // Compare based on window type
                final int type0 = arg0.getType();
                final int type1 = arg1.getType();
                if (type0 == type1) {
                    return 0;
                }

                /* Present IME windows first */
                if (type0 == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return 1;
                }

                if (type1 == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    return -1;
                }

                /* Present system windows last */
                if (type0 == AccessibilityWindowInfo.TYPE_SYSTEM) {
                    return -1;
                }

                if (type1 == AccessibilityWindowInfo.TYPE_SYSTEM) {
                    return 1;
                }

                /* Others are don't care */
                return 0;
            }
        });
    }

    /*
     * Remove the window with system buttons (BACK, HOME, RECENTS) from the window list. We
     * remove them because on some (but not all - see b/17305024) devices, the highlight rectangles
     * don't show up on around the system buttons.
     */
    private void removeSystemButtonsWindowFromWindowList(List<SwitchAccessWindowInfo> windowList) {
        final WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final Point screenSize = new Point();
        display.getSize(screenSize);

        final Iterator<SwitchAccessWindowInfo> windowIterator = windowList.iterator();
        while (windowIterator.hasNext()) {
            SwitchAccessWindowInfo window = windowIterator.next();
            /* Keep all non-system buttons */
            if (window.getType() != AccessibilityWindowInfo.TYPE_SYSTEM) {
                continue;
            }

            final Rect windowBounds = new Rect();
            window.getBoundsInScreen(windowBounds);

            /* Keep system dialogs (app has crashed), which don't border any edge */
            if ((windowBounds.top > 0) && (windowBounds.bottom < screenSize.y)
                    && (windowBounds.left > 0) && (windowBounds.right < screenSize.x)) {
                continue;
            }

            /* Keep notifications, which start at the top and cover more than half the width */
            if ((windowBounds.top <= 0) && (windowBounds.width() > screenSize.x / 2)) {
                continue;
            }

            /* Keep large system overlays like the context menu */
            final int windowArea = windowBounds.width() * windowBounds.height();
            final int screenArea = screenSize.x * screenSize.y;
            if (windowArea > (screenArea / 2)) {
                continue;
            }

            windowIterator.remove();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        String viewLinearImeRowColKey = mContext.getString(R.string.views_linear_ime_row_col_key);
        String optionScanKey = mContext.getString(R.string.option_scanning_key);
        String scanPref = prefs.getString(
                mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.pref_scanning_methods_default));

        mOptionScanningEnabled = TextUtils.equals(scanPref, optionScanKey);
        mBuilderForIMEs = mRowColumnTreeBuilder;
        mBuilderForViews = (TextUtils.equals(scanPref, viewLinearImeRowColKey)) ?
                mLinearScanTreeBuilder : mRowColumnTreeBuilder;
    }

    /**
     * Clean up when this object is no longer needed
     */
    public void shutdown() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }
}
