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

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.talkback.CollectionState;
import com.android.talkback.FeedbackItem;
import com.android.talkback.InputModeManager;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.eventprocessor.EventState;
import com.android.utils.Role;
import com.android.utils.StringBuilderUtils;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.Utterance;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.traversal.SimpleTraversalStrategy;
import com.android.utils.traversal.TraversalStrategy;
import com.android.utils.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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

    /** Whether the last region the user explored was scrollable. */
    private boolean mLastNodeWasScrollable;

    private int mLastFocusedWindowId = -1;

    /**
     * The node processor used to generate spoken descriptions. Should be set
     * only while this class is formatting an event, {@code null} otherwise.
     */
    private NodeSpeechRuleProcessor mNodeProcessor;

    private TalkBackService mService;

    private @Nullable CollectionState mCollectionState;

    private final HashMap<Integer, CharSequence> mWindowTitlesMap =
            new HashMap<Integer, CharSequence>();

    @Override
    public void initialize(TalkBackService service) {
        service.addEventListener(this);
        mService = service;
        mNodeProcessor = NodeSpeechRuleProcessor.getInstance();
        mCollectionState = new CollectionState();
        mLastFocusedWindowId = -1;
        mWindowTitlesMap.clear();
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Store window title in the map.
                    List<CharSequence> titles = event.getText();
                    if (titles.size() > 0) {
                        AccessibilityNodeInfo node = event.getSource();
                        if (node != null) {
                            int windowType = getWindowType(node);
                            if (windowType == AccessibilityWindowInfo.TYPE_APPLICATION ||
                                    windowType == AccessibilityWindowInfo.TYPE_SYSTEM) {
                                mWindowTitlesMap.put(node.getWindowId(), titles.get(0));
                            }
                            node.recycle();
                        }
                    }
                }
                break;
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Copy key set not to modify original map.
                    HashSet<Integer> windowIdsToBeRemoved = new HashSet<Integer>();
                    windowIdsToBeRemoved.addAll(mWindowTitlesMap.keySet());

                    // Enumerate window ids to be removed.
                    List<AccessibilityWindowInfo> windows = mService.getWindows();
                    for (AccessibilityWindowInfo window : windows) {
                        windowIdsToBeRemoved.remove(window.getId());
                    }

                    // Delete titles of non-existing window ids.
                    for (Integer windowId : windowIdsToBeRemoved) {
                        mWindowTitlesMap.remove(windowId);
                    }
                }
                break;
        }
    }

    /**
     * Formatter that returns an utterance to announce touch exploration.
     */
    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED &&
                EventState.getInstance().checkAndClearRecentEvent(
                        EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE)) {
            return false;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED &&
                EventState.getInstance().checkAndClearRecentEvent(
                        EventState.EVENT_SKIP_FOCUS_PROCESSING_AFTER_CURSOR_CONTROL)) {
            return false;
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

        // Transition the collection state if necessary.
        mCollectionState.updateCollectionInformation(focusedNode, event);

        // Populate the utterance.
        addEarconWhenAccessibilityFocusMovesToTheDivider(utterance, focusedNode);
        addSpeechFeedback(utterance, focusedNode, event, sourceNode);
        addAuditoryHapticFeedback(utterance, focusedNode);

        // By default, touch exploration flushes all other events.
        utterance.getMetadata().putInt(Utterance.KEY_METADATA_QUEUING, DEFAULT_QUEUING_MODE);

        // Events formatted by this class should always advance continuous
        // reading, if active.
        utterance.addSpokenFlag(FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mLastFocusedWindowId = focusedNode.getWindowId();
        }

        AccessibilityNodeInfoUtils.recycleNodes(sourceNode, focusedNode);

        return true;
    }

    private void addEarconWhenAccessibilityFocusMovesToTheDivider(Utterance utterance,
            AccessibilityNodeInfoCompat announcedNode) {
        if (!BuildCompat.isAtLeastN() || mLastFocusedWindowId == announcedNode.getWindowId()) {
            return;
        }

        // TODO: Use AccessibilityWindowInfoCompat.TYPE_SPLIT_SCREEN_DIVIDER once it's
        // added.
        if (getWindowType(announcedNode) != AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
            return;
        }

        utterance.addAuditory(R.raw.complete);
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
     * Populates utterance about window transition. We populate this feedback only when user is in
     * split screen mode to avoid verbosity of feedback.
     */
    private void addWindowTransition(
            Utterance utterance, AccessibilityNodeInfoCompat announcedNode) {
        int windowId = announcedNode.getWindowId();
        if (windowId == mLastFocusedWindowId) {
            return;
        }

        int windowType = getWindowType(announcedNode);
        if (windowType != AccessibilityWindowInfoCompat.TYPE_APPLICATION &&
                windowType != AccessibilityWindowInfoCompat.TYPE_SYSTEM) {
            return;
        }

        List<AccessibilityWindowInfo> windows = mService.getWindows();
        List<AccessibilityWindowInfo> applicationWindows = new ArrayList<>();
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                if (window.getParent() == null) {
                    applicationWindows.add(window);
                }
            }
        }

        // Provide window transition feedback only when user is in split screen mode or navigating
        // with keyboard. We consider user is in split screen mode if there are two none-parented
        // application windows.
        if (applicationWindows.size() != 2 &&
                mService.getInputModeManager().getInputMode() !=
                InputModeManager.INPUT_MODE_KEYBOARD) {
            return;
        }

        WindowManager windowManager = new WindowManager(mService.isScreenLayoutRTL());
        windowManager.setWindows(windows);

        CharSequence title = null;
        if (!applicationWindows.isEmpty() && windowManager.isStatusBar(windowId)) {
            title = mService.getString(R.string.status_bar);
        } else if (!applicationWindows.isEmpty() && windowManager.isNavigationBar(windowId)) {
            title = mService.getString(R.string.navigation_bar);
        } else {
            title = mWindowTitlesMap.get(windowId);

            if (title == null && BuildCompat.isAtLeastN()) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window.getId() == windowId) {
                        title = window.getTitle();
                        break;
                    }
                }
            }

            if (title == null) {
                title = mService.getApplicationLabel(announcedNode.getPackageName());
            }
        }

        int templateId = windowType == AccessibilityWindowInfo.TYPE_APPLICATION ?
                R.string.template_window_switch_application :
                R.string.template_window_switch_system;
        utterance.addSpoken(mService.getString(templateId,
                    WindowManager.formatWindowTitleForFeedback(title, mService)));
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

    private void addCollectionTransition(Utterance utterance) {
        @CollectionState.CollectionTransition int collectionTransition =
                mCollectionState.getCollectionTransition();
        if (collectionTransition != CollectionState.NAVIGATE_ENTER &&
                collectionTransition != CollectionState.NAVIGATE_EXIT) {
            return;
        }

        CharSequence transitionText;
        if (collectionTransition == CollectionState.NAVIGATE_ENTER) {
            CharSequence collectionDescription = getCollectionDescription(mCollectionState, true);
            transitionText = mService.getString(R.string.template_collection_start,
                    collectionDescription);
        } else { // NAVIGATE_EXIT
            CharSequence collectionDescription = getCollectionDescription(mCollectionState, false);
            if (!mCollectionState.doesCollectionExist()) {
                // If the collection root no longer exists, then skip the exit announcement.
                // The app has probably switched its activity/fragment/other UI.
                LogUtils.log(this, Log.VERBOSE, "Exit announcement skipped: %s",
                        collectionDescription);
                return;
            }

            transitionText = mService.getString(R.string.template_collection_end,
                    collectionDescription);
        }

        utterance.addSpoken(transitionText);
    }

    private void addCollectionItemTransition(Utterance utterance,
                AccessibilityNodeInfoCompat announcedNode) {
        @CollectionState.RowColumnTransition int rowColumnTransition =
                mCollectionState.getRowColumnTransition();
        if (rowColumnTransition == CollectionState.TYPE_NONE) {
            return;
        }

        // Add heading label only if item has no role description, so that we don't end up
        // duplicating the role description.
        boolean hasRoleDescription = announcedNode != null &&
                announcedNode.getRoleDescription() != null;
        if (mCollectionState.getCollectionRole() == Role.ROLE_GRID) {
            // For tables, we want to be selective with what we say since there's a lot of
            // information (e.g. row name, column name, heading).
            CollectionState.TableItemState tableItem = mCollectionState.getTableItemState();

            if (!hasRoleDescription) {
                switch (tableItem.getHeadingType()) {
                    case CollectionState.TYPE_COLUMN:
                        utterance.addSpoken(mService.getString(R.string.column_heading_template));
                        break;
                    case CollectionState.TYPE_ROW:
                        utterance.addSpoken(mService.getString(R.string.row_heading_template));
                        break;
                    case CollectionState.TYPE_INDETERMINATE:
                        utterance.addSpoken(mService.getString(R.string.heading_template));
                        break;
                }
            }

            if ((rowColumnTransition & CollectionState.TYPE_ROW) != 0 &&
                    tableItem.getHeadingType() != CollectionState.TYPE_ROW &&
                    tableItem.getRowIndex() != -1) {
                if (tableItem.getRowName() != null) {
                    utterance.addSpoken(tableItem.getRowName());
                } else {
                    utterance.addSpoken(mService.getString(R.string.row_index_template,
                            tableItem.getRowIndex() + 1));
                }
            }

            if ((rowColumnTransition & CollectionState.TYPE_COLUMN) != 0 &&
                    tableItem.getHeadingType() != CollectionState.TYPE_COLUMN &&
                    tableItem.getColumnIndex() != -1) {
                if (tableItem.getColumnName() != null) {
                    utterance.addSpoken(tableItem.getColumnName());
                } else {
                    utterance.addSpoken(mService.getString(R.string.column_index_template,
                            tableItem.getColumnIndex() + 1));
                }
            }
        } else {
            // For lists, we can just say everything since the additional feedback is limited.
            CollectionState.ListItemState listItem = mCollectionState.getListItemState();

            // Add heading label only if item has no role description.
            if (listItem.isHeading() && !hasRoleDescription) {
                utterance.addSpoken(mService.getString(R.string.heading_template));
            }
        }
    }

    /**
     * Adds speech feedback for a focused node. This speech feedback depends on both the previously
     * focused node and the currently focused node.
     *
     * @param utterance The target utterance.
     * @param announcedNode The computed announced node.
     * @param event The source event, only used to providing a description when
     *            the source node is a progress bar.
     * @param source The source node, used to determine whether the source event
     *            should be passed to the node formatter.
     */
    private void addSpeechFeedback(Utterance utterance,
            @Nullable AccessibilityNodeInfoCompat announcedNode,
            AccessibilityEvent event,
            AccessibilityNodeInfoCompat source) {
        // Ensure that we speak touch exploration, even during speech reco.
        utterance.addSpokenFlag(FeedbackItem.FLAG_DURING_RECO);

        // Add the current node's description.
        addDescription(utterance, announcedNode, event, source);

        // Append extra list information, e.g. "2 of 5".
        addCollectionItemTransition(utterance, announcedNode);

        // Determine whether we have entered a different/new collection.
        addCollectionTransition(utterance);

        // Add spoken feedback about window transition if it had happened.
        addWindowTransition(utterance, announcedNode);
    }

    /**
     * Adds auditory and haptic feedback for a focused node.
     *
     * @param utterance The utterance to which to add the earcons.
     * @param announcedNode The node that is announced.
     */
    private void addAuditoryHapticFeedback(Utterance utterance,
            @Nullable AccessibilityNodeInfoCompat announcedNode) {
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

    /**
     * Returns the collection's name plus its role. If {@code detailed} is true, then adds
     * the collection row/column count as well.
     * */
    private CharSequence getCollectionDescription(@NonNull CollectionState state,
          boolean detailed) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        StringBuilderUtils.append(builder,
                state.getCollectionName(),
                state.getCollectionRoleDescription(mService));

        if (detailed) {
            int collectionLevel = state.getCollectionLevel();
            if (collectionLevel >= 0) {
                String levelText =
                        mService.getString(R.string.template_collection_level, collectionLevel + 1);
                StringBuilderUtils.appendWithSeparator(builder, levelText);
            }

            int rowCount = state.getCollectionRowCount();
            int columnCount = state.getCollectionColumnCount();

            if (state.getCollectionRole() == Role.ROLE_GRID &&
                    rowCount != -1 &&
                    columnCount != -1) {
                String rowText = mService.getResources().getQuantityString(
                        R.plurals.template_list_row_count,
                        rowCount,
                        rowCount);
                String columnText = mService.getResources().getQuantityString(
                        R.plurals.template_list_column_count,
                        columnCount,
                        columnCount);
                StringBuilderUtils.appendWithSeparator(builder, rowText, columnText);
            } else if (state.getCollectionRole() == Role.ROLE_LIST) {
                if (state.getCollectionAlignment() == CollectionState.ALIGNMENT_VERTICAL &&
                        rowCount != -1) {
                    String totalText = mService.getResources().getQuantityString(
                            R.plurals.template_list_total_count,
                            rowCount,
                            rowCount);
                    StringBuilderUtils.appendWithSeparator(builder, totalText);
                } else if (state.getCollectionAlignment() == CollectionState.ALIGNMENT_HORIZONTAL &&
                        columnCount != -1) {
                    String totalText = mService.getResources().getQuantityString(
                            R.plurals.template_list_total_count,
                            columnCount,
                            columnCount);
                    StringBuilderUtils.appendWithSeparator(builder, totalText);
                }
            }
        }

        return builder;
    }

    private static int getWindowType(AccessibilityNodeInfo node) {
        if (node == null) {
            return -1;
        }

        return getWindowType(new AccessibilityNodeInfoCompat(node));
    }

    private static int getWindowType(AccessibilityNodeInfoCompat nodeCompat) {
        if (nodeCompat == null) {
            return -1;
        }

        AccessibilityWindowInfoCompat windowInfoCompat = nodeCompat.getWindow();
        if (windowInfoCompat == null) {
            return -1;
        }

        int windowType = windowInfoCompat.getType();
        windowInfoCompat.recycle();
        return windowType;
    }
}
