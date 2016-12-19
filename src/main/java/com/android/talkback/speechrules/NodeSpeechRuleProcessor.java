/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback.speechrules;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.Spannable;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.talkback.R;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.Role;
import com.android.utils.StringBuilderUtils;
import com.android.utils.traversal.ReorderedChildrenIterator;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Rule-based processor for {@link AccessibilityNodeInfoCompat}s.
 */
public class NodeSpeechRuleProcessor {

    private static final LinkedList<NodeSpeechRule> mRules = new LinkedList<>();
    private static final RuleSwitch mRuleSwitch = new RuleSwitch();

    private static NodeSpeechRuleProcessor sInstance;

    static {
        // Rules are matched in the order they are added, so make sure to place
        // general rules after specific ones (e.g. Button after RadioButton).
        mRules.add(new RuleSimpleHintTemplate(Role.ROLE_DROP_DOWN_LIST,
                R.string.template_hint_spinner, R.string.template_hint_spinner_keyboard));
        mRules.add(mRuleSwitch);
        mRules.add(new RuleNonTextViews()); // ImageViews and ImageButtons
        mRules.add(new RuleEditText());
        mRules.add(new RuleSeekBar());
        mRules.add(new RulePager());
        mRules.add(new RulePagerPage());
        mRules.add(new RuleContainer());

        mRules.add(new RuleViewGroup());

        // Always add the default rule last.
        mRules.add(new RuleDefault());
    }

    // TODO: remove this
    public static void initialize(Context context) {
        sInstance = new NodeSpeechRuleProcessor(context);
    }

    // TODO: remove this
    public static NodeSpeechRuleProcessor getInstance() {
        if (sInstance == null) {
            throw new RuntimeException("NodeSpeechRuleProcessor not initialized");
        }

        return sInstance;
    }

    /** The parent context. */
    private final Context mContext;

    private NodeSpeechRuleProcessor(Context context) {
        mContext = context;
    }

    /**
     * Returns the best description for the subtree rooted at
     * {@code announcedNode}.
     *
     * @param announcedNode The root node of the subtree to describe.
     * @param event The source event, may be {@code null} when called with
     *            non-source nodes.
     * @param source The event's source node.
     * @return The best description for a node.
     */
    public CharSequence getDescriptionForTree(AccessibilityNodeInfoCompat announcedNode,
            AccessibilityEvent event, AccessibilityNodeInfoCompat source) {
        if (announcedNode == null) {
            return null;
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();

        Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        appendDescriptionForTree(announcedNode, builder, event, source, visitedNodes);
        AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        formatTextWithLabel(announcedNode, builder);
        appendRootMetadataToBuilder(announcedNode, builder);

        return builder;
    }

    /**
     * Returns hint text for a node.
     *
     * @param node The node to provide hint text for.
     * @return The node's hint text.
     */
    public CharSequence getHintForNode(AccessibilityNodeInfoCompat node) {
        // Disabled items don't have any hint text.
        if (!node.isEnabled()) {
            return null;
        }

        for (NodeSpeechRule rule : mRules) {
            if ((rule instanceof NodeHintRule) && rule.accept(node, null)) {
                LogUtils.log(this, Log.VERBOSE, "Processing node hint using %s", rule);
                return ((NodeHintRule) rule).getHintText(mContext, node);
            }
        }

        return null;
    }

    private void appendDescriptionForTree(AccessibilityNodeInfoCompat announcedNode,
            SpannableStringBuilder builder, AccessibilityEvent event,
            AccessibilityNodeInfoCompat source, Set<AccessibilityNodeInfoCompat> visitedNodes) {
        if (announcedNode == null) {
            return;
        }

        AccessibilityNodeInfoCompat visitedNode = AccessibilityNodeInfoCompat.obtain(announcedNode);
        if (!visitedNodes.add(visitedNode)) {
            visitedNode.recycle();
            return;
        }

        final AccessibilityEvent nodeEvent = (announcedNode.equals(source)) ? event : null;
        final CharSequence nodeDesc = getDescriptionForNode(announcedNode, nodeEvent);
        final boolean blockChildDescription = hasOverridingContentDescription(announcedNode);

        SpannableStringBuilder childStringBuilder = new SpannableStringBuilder();

        if (!blockChildDescription) {
            // Recursively append descriptions for visible and non-focusable child nodes.
            ReorderedChildrenIterator iterator = ReorderedChildrenIterator
                    .createAscendingIterator(announcedNode);
            while (iterator.hasNext()) {
                AccessibilityNodeInfoCompat child = iterator.next();
                if (AccessibilityNodeInfoUtils.isVisible(child)
                        && !AccessibilityNodeInfoUtils.isAccessibilityFocusable(child)) {
                    appendDescriptionForTree(child, childStringBuilder, event, source,
                            visitedNodes);
                }
            }

            iterator.recycle();
        }

        // If any one of the following is satisfied:
        // 1. The root node has a description.
        // 2. The root has no override content description and the children have some description.
        // Then we should append the status information for this node.
        // This is used to avoid displaying checked/expanded status alone without node description.
        //
        if (!TextUtils.isEmpty(nodeDesc) || !TextUtils.isEmpty(childStringBuilder)) {
            appendExpandedOrCollapsedStatus(announcedNode, event, builder);
            appendCheckedStatus(announcedNode, event, builder);
        }

        StringBuilderUtils.appendWithSeparator(builder, nodeDesc);
        StringBuilderUtils.appendWithSeparator(builder, childStringBuilder);
    }

    /**
     * Determines whether the node has a contentDescription that should cause its subtree's
     * description to be ignored.
     * The traditional behavior in TalkBack has been to return {@code true} for any node with a
     * non-empty contentDescription. In this function, we thus whitelist certain roles where it
     * doesn't make sense for the contentDescription to override the entire subtree.
     */
    private static boolean hasOverridingContentDescription(AccessibilityNodeInfoCompat node) {
        switch (Role.getRole(node)) {
            case Role.ROLE_PAGER:
            case Role.ROLE_GRID:
            case Role.ROLE_LIST:
                return false;
            default:
                return node != null && !TextUtils.isEmpty(node.getContentDescription());
        }
    }

    /**
     * Processes the specified node using a series of speech rules.
     *
     * @param node The node to process.
     * @param event The source event, may be {@code null} when called with
     *            non-source nodes.
     * @return A string representing the given node, or {@code null} if the node
     *         could not be processed.
     */
    public CharSequence getDescriptionForNode(
            AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        for (NodeSpeechRule rule : mRules) {
            if (rule.accept(node, event)) {
                LogUtils.log(this, Log.VERBOSE, "Processing node using %s", rule);
                return rule.format(mContext, node, event);
            }
        }

        return null;
    }

    /**
     * If the supplied node has a label, replaces the builder text with a
     * version formatted with the label.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void formatTextWithLabel(
            AccessibilityNodeInfoCompat node, SpannableStringBuilder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return;

        // TODO: add getLabeledBy to support lib
        AccessibilityNodeInfo info = (AccessibilityNodeInfo) node.getInfo();
        if (info == null) return;
        AccessibilityNodeInfo labeledBy = info.getLabeledBy();
        if (labeledBy == null) return;
        AccessibilityNodeInfoCompat labelNode = new AccessibilityNodeInfoCompat(labeledBy);

        final SpannableStringBuilder labelDescription = new SpannableStringBuilder();
        Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
        appendDescriptionForTree(labelNode, labelDescription, null, null, visitedNodes);
        AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
        if (TextUtils.isEmpty(labelDescription)) {
            return;
        }

        final String labeled = mContext.getString(
                R.string.template_labeled_item, builder, labelDescription);
        Spannable spannableLabeledText = StringBuilderUtils.createSpannableFromTextWithTemplate(
                labeled, builder);

        // Replace the text of the builder.
        builder.clear();
        builder.append(spannableLabeledText);
    }

    /**
     * Appends meta-data about node's disabled state (if actionable).
     * <p>
     * This should only be applied to the root node of a tree.
     */
    private void appendRootMetadataToBuilder(
            AccessibilityNodeInfoCompat node, SpannableStringBuilder descriptionBuilder) {
        // Append state for actionable but disabled nodes.
        if (AccessibilityNodeInfoUtils.isActionableForAccessibility(node) && !node.isEnabled()) {
            StringBuilderUtils.appendWithSeparator(
                    descriptionBuilder, mContext.getString(R.string.value_disabled));
        }

        // Append the control's selected state.
        if (node.isSelected()) {
            StringBuilderUtils.appendWithSeparator(descriptionBuilder,
                    mContext.getString(R.string.value_selected));
        }
    }

    /**
     * Appends meta-data about the node's checked (if checkable) states.
     * <p>
     * This should be applied to all nodes in a tree, including the root.
     */
    private void appendCheckedStatus(AccessibilityNodeInfoCompat node,
                                     AccessibilityEvent event,
                                     SpannableStringBuilder descriptionBuilder) {
        // Append the control's checked state, if applicable. Ignore nodes that
        // were accepted by the switch rule, since they already include the
        // checkable state.
        if (node.isCheckable() && !mRuleSwitch.accept(node, event)) {
            CharSequence checkedString = mContext.getString(
                    node.isChecked() ? R.string.value_checked : R.string.value_not_checked);
            StringBuilderUtils.appendWithSeparator(descriptionBuilder, checkedString);
        }
    }

    /**
     * Appends meta-data about the node's expandable/collapsible states.
     * <p>
     * This should be applied to all nodes in a tree, including the root.
     */
    private void appendExpandedOrCollapsedStatus(AccessibilityNodeInfoCompat node,
                                                 AccessibilityEvent event,
                                                 SpannableStringBuilder descriptionBuilder) {
        // Append the control's expandable/collapsible state, if applicable.
        if (AccessibilityNodeInfoUtils.isExpandable(node)) {
            CharSequence collapsedString = mContext.getString(
                    R.string.value_collapsed);
            StringBuilderUtils.appendWithSeparator(descriptionBuilder, collapsedString);
        }
        if (AccessibilityNodeInfoUtils.isCollapsible(node)) {
            CharSequence expandedString = mContext.getString(
                    R.string.value_expanded);
            StringBuilderUtils.appendWithSeparator(descriptionBuilder, expandedString);
        }
    }
}
