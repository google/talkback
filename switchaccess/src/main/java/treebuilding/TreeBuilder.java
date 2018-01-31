/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.switchaccess.treebuilding;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.switchaccess.ButtonSwitchAccessIgnores;
import com.google.android.accessibility.switchaccess.NonActionableItemNode;
import com.google.android.accessibility.switchaccess.ShowActionsMenuNode;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessWindowInfo;
import com.google.android.accessibility.switchaccess.TreeScanNode;
import com.google.android.accessibility.switchaccess.TreeScanSystemProvidedNode;
import com.google.android.accessibility.utils.traversal.OrderedTraversalController;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** Base class for tree building. Includes some common utility methods. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public abstract class TreeBuilder {

  protected final Context mContext;

  public TreeBuilder(Context context) {
    mContext = context;
  }

  /**
   * Adds a view hierarchy to the top of a tree.
   *
   * @param node The root of the view hierarchy to be added to the tree
   * @param treeToBuildOn The tree that should be traversed if the user doesn't choose anything from
   *     the view hierarchy
   * @param includeNonActionableItems Whether non-actionable items should be added when building the
   *     tree
   * @return An updated tree that includes {@code treeToBuildOn}
   */
  public abstract TreeScanNode addViewHierarchyToTree(
      SwitchAccessNodeCompat node, TreeScanNode treeToBuildOn, boolean includeNonActionableItems);

  /**
   * Adds view hierarchies from several windows to the tree.
   *
   * @param windowList The windows whose hierarchies should be added to the tree
   * @param treeToBuildOn The tree to which the hierarchies from windowList should be added
   * @param shouldPlaceTreeFirst Whether the treeToBuildOn should be placed first in the tree; if it
   *     should not be placed first, it will be placed last
   * @param includeNonActionableItems Whether non-actionable items should be scanned and added to
   *     the tree
   * @return An updated tree that includes {@code treeToBuildOn}
   */
  public abstract TreeScanNode addWindowListToTree(
      List<SwitchAccessWindowInfo> windowList,
      TreeScanNode treeToBuildOn,
      boolean shouldPlaceTreeFirst,
      boolean includeNonActionableItems);

  /**
   * Obtains a list of nodes in the order TalkBack would traverse them.
   *
   * @param root The root of the tree to traverse
   * @return The nodes in {@code root}'s subtree (including root) in the order TalkBack would
   *     traverse them.
   */
  protected static LinkedList<SwitchAccessNodeCompat> getNodesInTalkBackOrder(
      SwitchAccessNodeCompat root) {
    // Compute windows above this one. Used for cropping node bounds. Filter out any windows
    // that have ButtonSwitchAccessIgnores as the only child of a root view. Any windows that
    // contain only these buttons should not be considered when creating the visible views.
    List<AccessibilityWindowInfo> windowsAbove = root.getWindowsAbove();
    List<AccessibilityWindowInfo> windowsAboveFiltered = new ArrayList<>();
    for (AccessibilityWindowInfo window : windowsAbove) {
      AccessibilityNodeInfo windowRoot = window.getRoot();
      if ((windowRoot != null) && (windowRoot.getChildCount() == 1)) {
        AccessibilityNodeInfo firstChild = windowRoot.getChild(0);
        if (firstChild != null) {
          CharSequence className = firstChild.getClassName();
          if (ButtonSwitchAccessIgnores.class.getName().equals(className)) {
            continue;
          }
        }
      }
      windowsAboveFiltered.add(window);
    }

    // Create our list of nodes.
    LinkedList<SwitchAccessNodeCompat> outList = new LinkedList<>();
    OrderedTraversalController traversalController = new OrderedTraversalController();
    traversalController.initOrder(root, true);
    AccessibilityNodeInfoCompat node = traversalController.findFirst();
    while (node != null) {
      // Ignore buttons that Switch Access shouldn't cache. This includes the "cancel" button
      // as we need special handling for it during option scanning (so that it is always the
      // last item in a given branch). Ignoring it here avoids creating duplicate nodes. The
      // menu button that appears at the top of the screen is also ignored for similar
      // reasons.
      CharSequence className = node.getClassName();
      if (ButtonSwitchAccessIgnores.class.getName().equals(className)) {
        // Ignore the node
      } else {
        outList.add(new SwitchAccessNodeCompat(node.getInfo(), windowsAboveFiltered));
      }
      node = traversalController.findNext(node);
    }
    traversalController.recycle();
    return outList;
  }

  /**
   * Creates a {@link TreeScanSystemProvidedNode} if the provided {@link SwitchAccessNodeCompat} can
   * be used to create a {@link ShowActionsMenuNode} or a {@link NonActionableItemNode}.
   *
   * @param compat The {@link SwitchAccessNodeCompat} that should be used to create a new node
   * @param includeNonActionableItems Whether we should attempt to create a node if the {@link
   *     SwitchAccessNodeCompat} corresponds to a non-actionable item
   * @return The new node if the provided {@link SwitchAccessNodeCompat} can be used to create a
   *     {@link ShowActionsMenuNode} or a {@link NonActionableItemNode}. Returns {@code null}
   *     otherwise.
   */
  protected TreeScanSystemProvidedNode createNodeIfImportant(
      SwitchAccessNodeCompat compat, boolean includeNonActionableItems) {
    TreeScanSystemProvidedNode node = ShowActionsMenuNode.createNodeIfHasActions(mContext, compat);

    if (node == null && includeNonActionableItems) {
      node = NonActionableItemNode.createNodeIfHasText(mContext, compat);
    }
    return node;
  }
}
