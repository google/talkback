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

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.SparseArray;

import com.android.talkback.R;
import com.android.utils.PerformActionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Option Scanning node to perform an action on an {@code AccessibilityNodeInfoCompat}
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class AccessibilityNodeActionNode extends OptionScanActionNode {
    private static final SparseArray<Integer> MAP_FORWARD_GRANULARITY_IDS = new SparseArray<>();
    private static final SparseArray<Integer> MAP_BACKWARD_GRANULARITY_IDS = new SparseArray<>();

    private final SwitchAccessNodeCompat mNodeCompat;
    private final AccessibilityNodeInfoCompat.AccessibilityActionCompat mAction;
    private final Bundle mArgs;

    /*
     * If this action can easily be confused with another because, for example, two views
     * with identical bounds expose the same action, this value is appended to the action's
     * description so the user can see the duplicated action. -1 indicates no duplication.
     */
    private int mNumberToAppendToDuplicateAction;

    static {
        MAP_FORWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
                R.string.switch_access_move_next_character);
        MAP_FORWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
                R.string.switch_access_move_next_line);
        MAP_FORWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
                R.string.switch_access_move_next_page);
        MAP_FORWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH,
                R.string.switch_access_move_next_paragraph);
        MAP_FORWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD,
                R.string.switch_access_move_next_word);

        MAP_BACKWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
                R.string.switch_access_move_prev_character);
        MAP_BACKWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
                R.string.switch_access_move_prev_line);
        MAP_BACKWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
                R.string.switch_access_move_prev_page);
        MAP_BACKWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH,
                R.string.switch_access_move_prev_paragraph);
        MAP_BACKWARD_GRANULARITY_IDS.put(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD,
                R.string.switch_access_move_prev_word);
    }

    /**
     * Note that this object must be recycled to prevent nodeCompat from leaking.
     * @param nodeCompat The node on which to perform the action
     * @param action The action the node should perform.
     */
    public AccessibilityNodeActionNode(SwitchAccessNodeCompat nodeCompat,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat action) {
        this(nodeCompat, action, null, -1);
    }

    /**
     * Note that this object must be recycled to prevent nodeCompat from leaking.
     * @param nodeCompat The node on which to perform the action
     * @param action The action the node should perform.
     * @param args The arguments that should be used when performing the action
     */
    public AccessibilityNodeActionNode(SwitchAccessNodeCompat nodeCompat,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat action,
            Bundle args) {
        this(nodeCompat, action, args, -1);
    }

    /**
     * Note that this object must be recycled to prevent nodeCompat from leaking.
     *
     * @param nodeCompat The node on which to perform the action
     * @param action The action the node should perform.
     * @param args The arguments that should be used when performing the action
     * @param numberToAppendToDuplicateAction If >= 0, the number will be added to the action's
     * description
     */
    public AccessibilityNodeActionNode(SwitchAccessNodeCompat nodeCompat,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat action,
            Bundle args, int numberToAppendToDuplicateAction) {
        mNodeCompat = nodeCompat.obtainCopy();
        mAction = action;
        mArgs = args;
        mNumberToAppendToDuplicateAction = numberToAppendToDuplicateAction;
    }

    /**
     * Get the underlying {@code SwitchAccessNodeCompat}
     * @return The {@code SwitchAccessNodeCompat} on which actions are performed.
     */
    public SwitchAccessNodeCompat getNodeInfoCompat() {
        return mNodeCompat.obtainCopy();
    }

    /**
     * Set the number to append to the action's description. Negative values indicate that
     * no value will be appended. The default number is -1.
     *
     * @param numberToAppendToDuplicateAction If 0 or greater, number to be appended to action's
     * description.
     */
    public void setNumberToAppendToDuplicateAction(int numberToAppendToDuplicateAction) {
        mNumberToAppendToDuplicateAction = numberToAppendToDuplicateAction;
    }

    /**
     * This method must be called to free the copy of the AccessibilityNodeInfoCompat made
     * when the object was created
     */
    @Override
    public void recycle() {
        mNodeCompat.recycle();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AccessibilityNodeActionNode)) {
            return false;

        }
        AccessibilityNodeActionNode otherNode = (AccessibilityNodeActionNode) other;
        if (otherNode.mAction.getId() != mAction.getId()) {
            return false;
        }
        if (!otherNode.mNodeCompat.equals(mNodeCompat)) {
            return false;
        }

        if (!otherNode.getRectsForNodeHighlight().equals(
                getRectsForNodeHighlight())) {
            return false;
        }
        return true;
    }

    @Override
    public void performAction() {
        PerformActionUtils.performAction(mNodeCompat, mAction.getId(), mArgs);
    }

    @Override
    public Set<Rect> getRectsForNodeHighlight() {
        Rect boundsInScreen = new Rect();
        mNodeCompat.getVisibleBoundsInScreen(boundsInScreen);
        Set<Rect> rects = new HashSet<>();
        rects.add(boundsInScreen);
        return Collections.unmodifiableSet(rects);
    }

    @Override
    public CharSequence getActionLabel(Context context) {
        CharSequence label = getActionLabelInternal(context);
        if (mNumberToAppendToDuplicateAction >= 0) {
            String formatString =
                    context.getResources().getString(R.string.switch_access_dup_bounds_format);
            return String.format(
                    formatString, label.toString(), mNumberToAppendToDuplicateAction);
        }
        return label;
    }

    public CharSequence getActionLabelInternal(Context context) {
        CharSequence label = mAction.getLabel();
        if (label != null) {
            return label;
        }

        int id = mAction.getId();
        switch (id) {
            case AccessibilityNodeInfoCompat.ACTION_CLICK:
                return context.getResources().getString(R.string.action_name_click);
            case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
                return context.getResources().getString(R.string.action_name_long_click);
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
                return context.getResources().getString(R.string.action_name_scroll_backward);
            case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
                return context.getResources().getString(R.string.action_name_scroll_forward);
            case AccessibilityNodeInfoCompat.ACTION_DISMISS:
                return context.getResources().getString(R.string.action_name_dismiss);
            case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY: {
                // Movement requires a granularity argument
                int granularity = mArgs.getInt(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
                Integer stringId = MAP_FORWARD_GRANULARITY_IDS.get(granularity);
                return (stringId == null) ? null : context.getString(stringId);
            }
            case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY: {
                // Movement requires a granularity argument
                int granularity = mArgs.getInt(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
                Integer stringId = MAP_BACKWARD_GRANULARITY_IDS.get(granularity);
                return (stringId == null) ? null : context.getString(stringId);
            }
            default:
                return null;
        }
    }
}
