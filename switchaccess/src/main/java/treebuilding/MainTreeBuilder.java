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

package com.google.android.accessibility.switchaccess.treebuilding;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.switchaccess.ClearFocusNode;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceActivity;
import com.google.android.accessibility.switchaccess.SwitchAccessWindowInfo;
import com.google.android.accessibility.switchaccess.TreeScanNode;
import com.google.android.accessibility.switchaccess.TreeScanSelectionNode;
import com.google.android.accessibility.switchaccess.utils.ScreenUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Builder that constructs a hierarchy to scan from a list of windows */
public class MainTreeBuilder extends TreeBuilder
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final RowColumnTreeBuilder mRowColumnTreeBuilder;
  private final LinearScanTreeBuilder mLinearScanTreeBuilder;
  private final TalkBackOrderNDegreeTreeBuilder mOrderNTreeBuilder;
  private TreeBuilder mBuilderForViews;
  private boolean mOptionScanningEnabled;
  private boolean mNomonScanningEnabled;
  private boolean mScanNonActionableItemsEnabled;

  /** @param context A valid context for interacting with the framework */
  public MainTreeBuilder(Context context) {
    super(context);
    mLinearScanTreeBuilder = new LinearScanTreeBuilder(context);
    mRowColumnTreeBuilder = new RowColumnTreeBuilder(context);
    mOrderNTreeBuilder = new TalkBackOrderNDegreeTreeBuilder(context);
    updatePrefs(SharedPreferencesUtils.getSharedPreferences(mContext));
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
    prefs.registerOnSharedPreferenceChangeListener(this);
  }

  @VisibleForTesting
  public MainTreeBuilder(
      Context context,
      LinearScanTreeBuilder linearScanTreeBuilder,
      RowColumnTreeBuilder rowColumnTreeBuilder,
      TalkBackOrderNDegreeTreeBuilder talkBackOrderNDegreeTreeBuilder) {
    super(context);
    mLinearScanTreeBuilder = linearScanTreeBuilder;
    mRowColumnTreeBuilder = rowColumnTreeBuilder;
    mOrderNTreeBuilder = talkBackOrderNDegreeTreeBuilder;
  }

  /**
   * Adds view hierarchies from several windows to the tree.
   *
   * @param windowList The windows whose hierarchies should be added to the tree
   * @param treeToBuildOn The tree to which the hierarchies from windowList should be added
   * @param shouldPlaceTreeFirst Whether the treeToBuildOn should be placed first in the tree; if it
   *     should not be placed first, it will be placed last
   * @return An updated tree that includes {@code treeToBuildOn}
   */
  public TreeScanNode addWindowListToTree(
      List<SwitchAccessWindowInfo> windowList,
      TreeScanNode treeToBuildOn,
      boolean shouldPlaceTreeFirst) {
    if (windowList != null) {
      List<SwitchAccessWindowInfo> wList = new ArrayList<>(windowList);
      sortWindowListForTraversalOrder(wList);
      removeExtraButtonsWindowFromWindowList(wList);
      if (mOptionScanningEnabled || mNomonScanningEnabled) {
        return mOrderNTreeBuilder.addWindowListToTree(
            wList, treeToBuildOn, shouldPlaceTreeFirst, mScanNonActionableItemsEnabled);
      }
      // Make sure that if the user doesn't perform an explicit selection, focus is cleared.
      TreeScanNode newTree = new ClearFocusNode();
      if (!shouldPlaceTreeFirst) {
        if (treeToBuildOn != null) {
          newTree = new TreeScanSelectionNode(treeToBuildOn, new ClearFocusNode());
        }
      } else if (treeToBuildOn == null) {
        shouldPlaceTreeFirst = false;
      }
      for (SwitchAccessWindowInfo window : wList) {
        SwitchAccessNodeCompat windowRoot = window.getRoot();
        if (windowRoot != null) {
          if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            newTree =
                mRowColumnTreeBuilder.addViewHierarchyToTree(
                    windowRoot, newTree, mScanNonActionableItemsEnabled);
          } else {
            newTree = addViewHierarchyToTree(windowRoot, newTree, mScanNonActionableItemsEnabled);
          }
          windowRoot.recycle();
        }
      }
      if (shouldPlaceTreeFirst) {
        newTree = new TreeScanSelectionNode(treeToBuildOn, newTree);
      }
      return newTree;
    }
    return treeToBuildOn;
  }

  @Override
  public TreeScanNode addViewHierarchyToTree(
      SwitchAccessNodeCompat root,
      TreeScanNode treeToBuildOn,
      boolean shouldScanNonActionableItems) {
    return mBuilderForViews.addViewHierarchyToTree(
        root, treeToBuildOn, shouldScanNonActionableItems);
  }

  @Override
  public TreeScanNode addWindowListToTree(
      List<SwitchAccessWindowInfo> windowList,
      TreeScanNode treeToBuildOn,
      boolean shouldPlaceTreeFirst,
      boolean includeNonActionableItems) {
    /* Not currently needed */
    return null;
  }

  /**
   * Sorts windows so that the IME is traversed first, followed by other windows top-to-bottom. Note
   * that the list comes out backwards, which makes it easy to iterate through it when building the
   * tree from the bottom up.
   *
   * @param windowList The list to be sorted.
   */
  private static void sortWindowListForTraversalOrder(List<SwitchAccessWindowInfo> windowList) {
    Collections.sort(
        windowList,
        new Comparator<SwitchAccessWindowInfo>() {
          @Override
          public int compare(SwitchAccessWindowInfo arg0, SwitchAccessWindowInfo arg1) {

            /* Present IME windows first */
            final int type0 = arg0.getType();
            final int type1 = arg1.getType();
            if (type0 == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
              if (type1 == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                return 0;
              }
              return 1;
            }
            if (type1 == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
              return -1;
            }

            /* Then go top to bottom, comparing the vertical center of each view */
            final Rect bounds0 = new Rect();
            arg0.getBoundsInScreen(bounds0);
            final int verticalCenter0 = bounds0.top + bounds0.bottom;
            final Rect bounds1 = new Rect();
            arg1.getBoundsInScreen(bounds1);
            final int verticalCenter1 = bounds1.top + bounds1.bottom;

            if (verticalCenter0 < verticalCenter1) {
              return 1;
            }
            if (verticalCenter0 > verticalCenter1) {
              return -1;
            }

            /* Others are don't care */
            return 0;
          }
        });
  }

  /*
   * Removes the window with navigation bar buttons (BACK, HOME, RECENTS) as well as items on the
   * status bar from the window list. We remove navigation bar buttons because on some (but not
   * all - see) devices, the highlight rectangles don't show up on around the navigation
   * bar buttons. Also, some devices don't have soft navigation bar buttons, therefore, we can't
   * scan them.
   */
  private void removeExtraButtonsWindowFromWindowList(List<SwitchAccessWindowInfo> windowList) {
    Point screenSize = new Point();
    ScreenUtils.getScreenSize(mContext, screenSize);
    int statusBarHeight = ScreenUtils.getStatusBarHeight(mContext);

    final Iterator<SwitchAccessWindowInfo> windowIterator = windowList.iterator();
    while (windowIterator.hasNext()) {
      SwitchAccessWindowInfo window = windowIterator.next();
      /* Keep all non-system buttons */
      if (window.getType() != AccessibilityWindowInfo.TYPE_SYSTEM) {
        continue;
      }

      final Rect windowBounds = new Rect();
      window.getBoundsInScreen(windowBounds);

      /* Filter out items in the status bar */
      if ((windowBounds.bottom <= statusBarHeight)) {
        windowIterator.remove();
        continue;
      }

      /* Keep system dialogs (app has crashed), which don't border any edge */
      if ((windowBounds.top > 0)
          && (windowBounds.bottom < screenSize.y)
          && (windowBounds.left > 0)
          && (windowBounds.right < screenSize.x)) {
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
    updatePrefs(prefs);
    mOrderNTreeBuilder.updatePrefs(prefs);
  }

  private void updatePrefs(SharedPreferences prefs) {
    String viewLinearImeRowColKey = mContext.getString(R.string.views_linear_ime_row_col_key);
    String optionScanKey = mContext.getString(R.string.option_scanning_key);
    String nomonScanKey = mContext.getString(R.string.nomon_clocks_key);
    String scanPref =
        prefs.getString(
            mContext.getString(R.string.pref_scanning_methods_key),
            mContext.getString(R.string.pref_scanning_methods_default));

    mOptionScanningEnabled = TextUtils.equals(scanPref, optionScanKey);
    mNomonScanningEnabled = TextUtils.equals(scanPref, nomonScanKey);
    mScanNonActionableItemsEnabled =
        SwitchAccessPreferenceActivity.shouldScanNonActionableItems(mContext);

    mBuilderForViews =
        (TextUtils.equals(scanPref, viewLinearImeRowColKey))
            ? mLinearScanTreeBuilder
            : mRowColumnTreeBuilder;
  }

  public void shutdown() {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
    prefs.unregisterOnSharedPreferenceChangeListener(this);
  }
}
