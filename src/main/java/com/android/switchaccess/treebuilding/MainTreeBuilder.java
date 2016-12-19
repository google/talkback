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

package com.android.switchaccess.treebuilding;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.OptionScanActionNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.SwitchAccessNodeCompat;
import com.android.switchaccess.SwitchAccessWindowInfo;
import com.android.talkback.R;
import com.android.utils.SharedPreferencesUtils;

import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;


/**
 * Builder that constructs a hierarchy to scan from a list of windows
 */
public class MainTreeBuilder extends TreeBuilder {

    private final RowColumnTreeBuilder mRowColumnTreeBuilder;
    private final LinearScanTreeBuilder mLinearScanTreeBuilder;
    private final TalkBackOrderNDegreeTreeBuilder mOptionScanTreeBuilder;
    private TreeBuilder mBuilderForViews;
    private boolean mOptionScanningEnabled;

    /**
     * @param context A valid context for interacting with the framework
     */
    public MainTreeBuilder(Context context) {
        super(context);
        mLinearScanTreeBuilder = new LinearScanTreeBuilder(context);
        mRowColumnTreeBuilder = new RowColumnTreeBuilder(context);
        mOptionScanTreeBuilder = new TalkBackOrderNDegreeTreeBuilder(context);
        updatePrefs(SharedPreferencesUtils.getSharedPreferences(mContext));
    }

    @VisibleForTesting
    public MainTreeBuilder(Context context,
            LinearScanTreeBuilder linearScanTreeBuilder,
            RowColumnTreeBuilder rowColumnTreeBuilder,
            TalkBackOrderNDegreeTreeBuilder talkBackOrderNDegreeTreeBuilder) {
        super(context);
        mLinearScanTreeBuilder = linearScanTreeBuilder;
        mRowColumnTreeBuilder = rowColumnTreeBuilder;
        mOptionScanTreeBuilder = talkBackOrderNDegreeTreeBuilder;
    }

    @Override
    public OptionScanNode addWindowListToTree(List<SwitchAccessWindowInfo> windowList,
            OptionScanNode tree) {
        if (windowList != null) {
            List<SwitchAccessWindowInfo> wList = new ArrayList<>(windowList);
            sortWindowListForTraversalOrder(wList);
            removeSystemButtonsWindowFromWindowList(wList);
            if (mOptionScanningEnabled) {
                return mOptionScanTreeBuilder.addWindowListToTree(wList, tree);
            }
            for (SwitchAccessWindowInfo window : wList) {
                SwitchAccessNodeCompat windowRoot = window.getRoot();
                if (windowRoot != null) {
                    if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        tree = mRowColumnTreeBuilder.addViewHierarchyToTree(windowRoot, tree);
                    } else {
                        tree = addViewHierarchyToTree(windowRoot, tree);
                    }
                    windowRoot.recycle();
                }
            }
        }
        return tree;
    }

    @Override
    public OptionScanNode addViewHierarchyToTree(SwitchAccessNodeCompat root,
            OptionScanNode treeToBuildOn) {
        return mBuilderForViews.addViewHierarchyToTree(root, treeToBuildOn);
    }

    @Override
    public OptionScanNode buildContextMenu(List<? extends ContextMenuItem> actionList) {
        TreeBuilder builder = mOptionScanningEnabled ?
                mOptionScanTreeBuilder : mLinearScanTreeBuilder;
        return builder.buildContextMenu(actionList);
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
     * remove them because on some (but not all - see BUG) devices, the highlight rectangles
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
        super.onSharedPreferenceChanged(prefs, key);
        updatePrefs(prefs);
    }

    private void updatePrefs(SharedPreferences prefs) {
        String viewLinearImeRowColKey = mContext.getString(R.string.views_linear_ime_row_col_key);
        String optionScanKey = mContext.getString(R.string.option_scanning_key);
        String scanPref = prefs.getString(
                mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.pref_scanning_methods_default));

        mOptionScanningEnabled = TextUtils.equals(scanPref, optionScanKey);
        mBuilderForViews = (TextUtils.equals(scanPref, viewLinearImeRowColKey)) ?
                mLinearScanTreeBuilder : mRowColumnTreeBuilder;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        mLinearScanTreeBuilder.shutdown();
        mRowColumnTreeBuilder.shutdown();
        mOptionScanTreeBuilder.shutdown();
    }
}
