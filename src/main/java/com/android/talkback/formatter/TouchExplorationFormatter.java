/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback.formatter;

import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.eventprocessor.EventState;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.traversal.SimpleTraversalStrategy;
import com.android.utils.traversal.TraversalStrategy;

/**
 * This class is a formatter for handling touch exploration events. Current
 * implementation is simple and handles only hover enter events.
 */
public final class TouchExplorationFormatter
        implements EventSpeechRule.AccessibilityEventFormatter, EventSpeechRule.ContextBasedRule,
        AccessibilityEventListener {
    /** The default queuing mode for touch exploration feedback. */
    private static final int DEFAULT_QUEUING_MODE = SpeechController.QUEUE_MODE_FLUSH_ALL;

    /** The default text spoken for nodes with no description. */
    private static final CharSequence DEFAULT_DESCRIPTION = "";

    private static final long SKIP_GRANULARITY_MOVE_FOCUS_TIMEOUT = 1000;

    /** Whether the last region the user explored was scrollable. */
    private boolean mLastNodeWasScrollable;

    /**
     * The node processor used to generate spoken descriptions. Should be set
     * only while this class is formatting an event, {@code null} otherwise.
     */
    private NodeSpeechRuleProcessor mNodeProcessor;

    @Override
    public void initialize(TalkBackService context) {
        context.addEventListener(this);
        mNodeProcessor = NodeSpeechRuleProcessor.getInstance();
    }

    /**
     * Resets cached scrollable state when touch exploration after window state
     * changes.
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Reset cached scrollable state.
                mLastNodeWasScrollable = false;
                break;
        }
    }

    /**
     * Formatter that returns an utterance to announce touch exploration.
     */
    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED &&
                EventState.getInstance().hasEvent(
                        EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE,
                        SKIP_GRANULARITY_MOVE_FOCUS_TIMEOUT)) {
            EventState.getInstance().clearEvent(
                    EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE);
            return true;
        }

        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        final AccessibilityNodeInfoCompat sourceNode = record.getSource();
        final AccessibilityNodeInfoCompat focusedNode = getFocusedNode(
                event.getEventType(), sourceNode);

        // Drop the event if the source node was non-null, but the focus
        // algorithm decided to drop the event by returning null.
        if ((sourceNode != null) && (focusedNode == null)) {
            AccessibilityNodeInfoUtils.recycleNodes(sourceNode);
            return false;
        }

        LogUtils.log(this, Log.VERBOSE, "Announcing node: %s", focusedNode);

        // Populate the utterance.
        addDescription(utterance, focusedNode, event, sourceNode);
        addFeedback(utterance, focusedNode);

        // By default, touch exploration flushes all other events.
        utterance.getMetadata().putInt(Utterance.KEY_METADATA_QUEUING, DEFAULT_QUEUING_MODE);

        // Events formatted by this class should always advance continuous
        // reading, if active.
        utterance.addSpokenFlag(FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING);

        AccessibilityNodeInfoUtils.recycleNodes(sourceNode, focusedNode);

        return true;
    }

    /**
     * Computes a focused node based on the device's supported APIs and the
     * event type.
     *
     * @param eventType The event type.
     * @param sourceNode The source node.
     * @return The focused node, or {@code null} to drop the event.
     */
    private AccessibilityNodeInfoCompat getFocusedNode(
            int eventType, AccessibilityNodeInfoCompat sourceNode) {
        if (sourceNode == null) {
            return null;
        }
        if (eventType != AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            return null;
        }
        return AccessibilityNodeInfoCompat.obtain(sourceNode);
    }

    /**
     * Populates an utterance with text, either from the node or event.
     *
     * @param utterance The target utterance.
     * @param announcedNode The computed announced node.
     * @param event The source event, only used to providing a description when
     *            the source node is a progress bar.
     * @param source The source node, used to determine whether the source event
     *            should be passed to the node formatter.
     * @return {@code true} if a description could be obtained for the node.
     */
    private boolean addDescription(Utterance utterance, AccessibilityNodeInfoCompat announcedNode,
            AccessibilityEvent event, AccessibilityNodeInfoCompat source) {
        // Ensure that we speak touch exploration, even during speech reco.
        utterance.addSpokenFlag(FeedbackItem.FLAG_DURING_RECO);

        final CharSequence treeDescription = mNodeProcessor.getDescriptionForTree(
                announcedNode, event, source);
        if (!TextUtils.isEmpty(treeDescription)) {
            utterance.addSpoken(treeDescription);
            return true;
        }

        final CharSequence eventDescription =
                AccessibilityEventUtils.getEventTextOrDescription(event);
        if (!TextUtils.isEmpty(eventDescription)) {
            utterance.addSpoken(eventDescription);
            return true;
        }

        // Full-screen reading requires onUtteranceCompleted to occur, which
        // requires that we always speak something when focusing an item.
        utterance.addSpoken(DEFAULT_DESCRIPTION);
        return false;
    }

    /**
     * Adds auditory and haptic feedback for a focused node.
     *
     * @param utterance The utterance to which to add the earcons.
     * @param announcedNode The node that is announced.
     */
    private void addFeedback(Utterance utterance, AccessibilityNodeInfoCompat announcedNode) {
        if (announcedNode == null) {
            return;
        }

        final AccessibilityNodeInfoCompat scrollableNode =
                AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(announcedNode,
                        AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);
        final boolean userCanScroll = (scrollableNode != null);

        AccessibilityNodeInfoUtils.recycleNodes(scrollableNode);

        // Announce changes in whether the user can scroll the item they are
        // touching. This includes items with scrollable parents.
        if (mLastNodeWasScrollable != userCanScroll) {
            mLastNodeWasScrollable = userCanScroll;

            if (userCanScroll) {
                utterance.addAuditory(R.raw.chime_up);
            } else {
                utterance.addAuditory(R.raw.chime_down);
            }
        }

        // If the user can scroll, also check whether this item is at the edge
        // of a list and provide feedback if the user can scroll for more items.
        // Don't run this for API < 16 because it's slow without node caching.
        AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoUtils.getRoot(announcedNode);
        TraversalStrategy traversalStrategy = new SimpleTraversalStrategy();

        try {
            if (userCanScroll &&
                    AccessibilityNodeInfoUtils.isEdgeListItem(announcedNode, traversalStrategy)) {
                utterance.addAuditory(R.raw.scroll_more);
            }
        } finally {
            traversalStrategy.recycle();
            AccessibilityNodeInfoUtils.recycleNodes(rootNode);
        }

        // Actionable items provide different feedback than non-actionable ones.
        if (AccessibilityNodeInfoUtils.isActionableForAccessibility(announcedNode)) {
            utterance.addAuditory(R.raw.focus_actionable);
            utterance.addHaptic(R.array.view_actionable_pattern);
        } else {
            utterance.addAuditory(R.raw.focus);
            utterance.addHaptic(R.array.view_hovered_pattern);
        }
    }
}
