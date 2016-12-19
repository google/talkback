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

package com.android.switchaccess.treebuilding;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;

import com.android.switchaccess.AccessibilityNodeActionNode;
import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.SwitchAccessNodeCompat;
import com.android.switchaccess.SwitchAccessPreferenceActivity;
import com.android.switchaccess.SwitchAccessWindowInfo;
import com.android.talkback.R;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.traversal.OrderedTraversalController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Base class for tree building. Includes some common utility methods.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public abstract class TreeBuilder implements SharedPreferences.OnSharedPreferenceChangeListener {
    /* TODO Support all actions, perhaps conditioned on user preferences */
    protected static final Set<Integer> FRAMEWORK_ACTIONS = new HashSet<>(Arrays.asList(
            AccessibilityNodeInfoCompat.ACTION_CLICK,
            AccessibilityNodeInfoCompat.ACTION_DISMISS,
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY));
    protected static final int[] MOVEMENT_GRANULARITIES_ONE_LINE = {
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD};
    protected static final int[] MOVEMENT_GRANULARITIES_MULTILINE = {
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD};
    protected static final int SYSTEM_ACTION_MAX = 0x01FFFFFF;

    protected final Context mContext;

    /* Specifies whether to automatically click on clickable views */
    private boolean mAutoSelect;

    public TreeBuilder(Context context) {
        mContext = context;
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        updatePrefs(prefs);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Clean up when this object is no longer needed
     */
    public void shutdown() {
        SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Add a view hierarchy to the top of a tree
     * @param node The root of the view hierarchy to be added to the tree
     * @param treeToBuildOn The tree that should be traversed if the user doesn't choose anything
     *                      from the view hierarchy
     * @return An updated tree that includes {@code treeToBuildOn}
     */
    abstract public OptionScanNode addViewHierarchyToTree(SwitchAccessNodeCompat node,
            OptionScanNode treeToBuildOn);

    /**
     * Add view hierarchies from several windows to the top of a tree
     * @param windowList The windows whose hierarchies should be added to the tree
     * @param treeToBuildOn The tree that should be traversed if the user doesn't choose anything
     *                      from the view hierarchy
     * @return An updated tree that includes {@code treeToBuildOn}
     */
    abstract public OptionScanNode addWindowListToTree(List<SwitchAccessWindowInfo> windowList,
            OptionScanNode treeToBuildOn);

    /**
     * Build a context menu out of a list of items
     * @param actionList The items that should be in the context menu
     *
     * @return A context menu tree with the specified actions
     */
    abstract public OptionScanNode buildContextMenu(List<? extends ContextMenuItem> actionList);

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        updatePrefs(prefs);
    }

    private void updatePrefs(SharedPreferences prefs) {
        mAutoSelect = SwitchAccessPreferenceActivity.isGlobalMenuAutoselectOn(mContext);
    }

    protected static boolean isActionSupported(
            AccessibilityNodeInfoCompat.AccessibilityActionCompat action) {
        /* White-listed framework actions */
        if (action.getId() <= SYSTEM_ACTION_MAX) {
            return FRAMEWORK_ACTIONS.contains(action.getId());
        }
        /* Support custom actions with proper labels */
        return !TextUtils.isEmpty(action.getLabel());
    }

    /**
     * Obtain a list of nodes in the order TalkBack would traverse them
     *
     * @param root The root of the tree to traverse
     * @return The nodes in {@code root}'s subtree (including root) in the order TalkBack would
     * traverse them.
     */
    protected static LinkedList<SwitchAccessNodeCompat> getNodesInTalkBackOrder(
            SwitchAccessNodeCompat root) {
        LinkedList<SwitchAccessNodeCompat> outList = new LinkedList<>();
        OrderedTraversalController traversalController = new OrderedTraversalController();
        traversalController.initOrder(root, true);
        AccessibilityNodeInfoCompat node = traversalController.findFirst();
        while (node != null) {
            outList.add(new SwitchAccessNodeCompat(node.getInfo(), root.getWindowsAbove()));
            node = traversalController.findNext(node);
        }
        traversalController.recycle();
        return outList;
    }

    /**
     * Get the actions associated with the given compat node.
     *
     * @param compat The node whose actions should be obtained.
     * @return A list of {@code ContextMenuItems}, representing all the actions associated with the
     * specified node. They may be organized into sub-menus if the actions require arguments.
     * If no actions are associated with the node, an empty list is returned.
     */
    protected List<AccessibilityNodeActionNode> getCompatActionNodes(SwitchAccessNodeCompat compat) {
        if(!compat.isVisibleToUser() || compat.getHasSameBoundsAsAncestor()) {
            return new ArrayList<>(0);
        }
        List<AccessibilityNodeActionNode> actionNodes = getCompatActionNodesInternal(compat);
        if (!actionNodes.isEmpty()) {
            List<SwitchAccessNodeCompat> descendantsWithSameBounds =
                    compat.getDescendantsWithDuplicateBounds();
            List<AccessibilityNodeActionNode> actionsFromDescendants = new ArrayList<>();

            int duplicateBoundsDisambiguationNumber = 2;
            for (int i = 0; i < descendantsWithSameBounds.size(); i++) {
                SwitchAccessNodeCompat descendantWithSameBounds = descendantsWithSameBounds.get(i);
                List<AccessibilityNodeActionNode> descendantActions = getCompatActionNodesInternal(
                        descendantWithSameBounds);
                // Append the child actions to the parent's list
                if (!descendantActions.isEmpty()) {
                    for (int j = 0; j < descendantActions.size(); j++) {
                        descendantActions.get(j).setNumberToAppendToDuplicateAction(
                                duplicateBoundsDisambiguationNumber);
                    }
                    actionsFromDescendants.addAll(descendantActions);
                    duplicateBoundsDisambiguationNumber++;
                }
                descendantWithSameBounds.recycle();
            }

            if (!actionsFromDescendants.isEmpty()) {
                // Add a disambiguation number to this node's actions
                for (int i = 0; i < actionNodes.size(); i++) {
                    actionNodes.get(i).setNumberToAppendToDuplicateAction(1);
                }
                actionNodes.addAll(actionsFromDescendants);
            }
        }
        return actionNodes;
    }

    private List<AccessibilityNodeActionNode> getCompatActionNodesInternal(
            SwitchAccessNodeCompat compat) {
        List<AccessibilityNodeActionNode> actionNodes = new ArrayList<>();
        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions =
                compat.getActionList();
        for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action : actions) {
            if (mAutoSelect && (action.getId() == AccessibilityNodeInfoCompat.ACTION_CLICK)) {
                actionNodes.clear();
                actionNodes.add(new AccessibilityNodeActionNode(compat, action));
                return actionNodes;
            }
            if (isActionSupported(action)) {
                if ((action.getId() ==
                        AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                        || (action.getId() ==
                        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
                    /*
                     * These actions can populate a long context menu, and all Views with
                     * content descriptions support them. We therefore try to filter out what
                     * we should surface to provide the user with exactly the set of actions that
                     * are relevant to the view.
                     */
                    boolean canMoveInDirection = !TextUtils.isEmpty(compat.getText());
                    if (canMoveInDirection
                            && (compat.getTextSelectionStart() == compat.getTextSelectionEnd())) {
                        // Nothing is selected
                        int cursorPosition = compat.getTextSelectionStart();
                        boolean forward = (action.getId() ==
                                AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
                        canMoveInDirection &=
                                !(forward && cursorPosition == compat.getText().length());
                        canMoveInDirection &= !(!forward && cursorPosition == 0);
                        canMoveInDirection &= cursorPosition >= 0;
                    }
                    if (compat.isEditable() && canMoveInDirection) {
                        int movementGranularities = compat.getMovementGranularities();
                        int[] supportedGranularities = (compat.isMultiLine()
                                ? MOVEMENT_GRANULARITIES_MULTILINE
                                : MOVEMENT_GRANULARITIES_ONE_LINE);
                        for (int granularity : supportedGranularities) {
                            if ((movementGranularities & granularity) != 0) {
                                Bundle args = new Bundle();
                                args.putInt(AccessibilityNodeInfoCompat
                                        .ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity);
                                AccessibilityNodeActionNode node = new AccessibilityNodeActionNode(
                                        compat, action, args);
                                actionNodes.add(node);
                            }
                        }
                    }
                } else {
                    actionNodes.add(new AccessibilityNodeActionNode(compat, action));
                }
            }
        }
        return actionNodes;
    }
}
