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

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import androidx.annotation.VisibleForTesting;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.SwitchAccessWindowInfo;
import com.google.android.accessibility.switchaccess.treenodes.ClearFocusNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import com.google.android.accessibility.utils.DisplayUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Builder that constructs a hierarchy to scan from a list of windows */
public class MainTreeBuilder extends TreeBuilder {

  private final RowColumnTreeBuilder rowColumnTreeBuilder;
  private final LinearScanTreeBuilder linearScanTreeBuilder;
  private final TalkBackOrderNDegreeTreeBuilder orderNTreeBuilder;

  /** @param service A valid {@link AccessibilityService} for interacting with the framework */
  public MainTreeBuilder(AccessibilityService service) {
    super(service);
    linearScanTreeBuilder = new LinearScanTreeBuilder(service);
    rowColumnTreeBuilder = new RowColumnTreeBuilder(service);
    orderNTreeBuilder = new TalkBackOrderNDegreeTreeBuilder(service);
  }

  @VisibleForTesting
  public MainTreeBuilder(
      AccessibilityService service,
      LinearScanTreeBuilder linearScanTreeBuilder,
      RowColumnTreeBuilder rowColumnTreeBuilder,
      TalkBackOrderNDegreeTreeBuilder talkBackOrderNDegreeTreeBuilder) {
    super(service);
    this.linearScanTreeBuilder = linearScanTreeBuilder;
    this.rowColumnTreeBuilder = rowColumnTreeBuilder;
    orderNTreeBuilder = talkBackOrderNDegreeTreeBuilder;
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
      removeStatusBarButtonsFromWindowList(wList);
      if (SwitchAccessPreferenceUtils.isGroupSelectionEnabled(service)) {
        return orderNTreeBuilder.addWindowListToTree(
            wList,
            treeToBuildOn,
            SwitchAccessPreferenceUtils.shouldScanNonActionableItems(service));
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
          if (!SwitchAccessPreferenceUtils.isLinearScanningEnabled(service)
              && window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            newTree =
                rowColumnTreeBuilder.addViewHierarchyToTree(
                    windowRoot,
                    newTree,
                    SwitchAccessPreferenceUtils.shouldScanNonActionableItems(service));
          } else {
            newTree =
                addViewHierarchyToTree(
                    windowRoot,
                    newTree,
                    SwitchAccessPreferenceUtils.shouldScanNonActionableItems(service));
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
    return getBuilderForViews()
        .addViewHierarchyToTree(root, treeToBuildOn, shouldScanNonActionableItems);
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
   * Removes items on the status bar from the window list.
   */
  private void removeStatusBarButtonsFromWindowList(List<SwitchAccessWindowInfo> windowList) {
    int statusBarHeight = DisplayUtils.getStatusBarHeightInPixel(service);

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
      }
    }
  }

  private TreeBuilder getBuilderForViews() {
    return (SwitchAccessPreferenceUtils.isLinearScanningWithoutKeyboardEnabled(service)
            || SwitchAccessPreferenceUtils.isLinearScanningEnabled(service))
        ? linearScanTreeBuilder
        : rowColumnTreeBuilder;
  }
}
