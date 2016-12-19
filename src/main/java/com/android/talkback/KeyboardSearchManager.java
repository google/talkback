/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.talkback;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.talkback.InputModeManager;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoRef;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.FocusFinder;
import com.android.utils.NodeSearch;
import com.android.utils.PerformActionUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.traversal.NodeFocusFinder;
import com.google.android.marvin.talkback.TalkBackService;

/**
 * Handles keyboard search of the nodes on the screen.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class KeyboardSearchManager
        implements TalkBackService.KeyEventListener, KeyComboManager.KeyComboListener,
        AccessibilityEventListener {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    /** The delay, in milliseconds, between the user's last action and the hint speech. */
    private static final int HINT_DELAY = 5000;

    /** The parent context. */
    private final TalkBackService mContext;

    /** The custom label manager that may be used for hint speech. */
    private final CustomLabelManager mLabelManager;

    /** The NodeSearch instance used to execute searches. */
    private final NodeSearch mNodeSearch;

    /** The SpeechController used to speak hints and announce actions. */
    private final SpeechController mSpeechController;

    /** The handler used to speak hints when the user takes no action for the hint delay time. */
    private Handler mHandler = new Handler();

    /**
     * The node that was focused before entering search mode. The focus is moved back to this node
     * if the search is canceled.
     */
    private final AccessibilityNodeInfoRef mInitialNode = new AccessibilityNodeInfoRef();

    /** Whether the user has navigated within search mode using the arrow keys. */
    private boolean mHasNavigated;

    public KeyboardSearchManager(TalkBackService context,
            CustomLabelManager labelManager) {
        mContext = context;
        mLabelManager = labelManager;

        NodeSearch.SearchTextFormatter formatter = new NodeSearch.SearchTextFormatter() {
            @Override
            public float getTextSize() {
                return mContext.getResources()
                        .getDimensionPixelSize(R.dimen.search_text_font_size);
            }

            @Override
            public String getDisplayText(String queryText) {
                return mContext.getString(R.string.search_dialog_label, queryText);
            }
        };

        mNodeSearch = new NodeSearch(context, labelManager, formatter);
        mSpeechController = context.getSpeechController();
    }

    /**
     * Toggle search mode.
     */
    void toggleSearch() {
        if (mNodeSearch.isActive()) {
            cancelSearch();
        } else {
            startSearch();
        }
    }

    /**
     * To be called when TalkBack receives a gesture.
     *
     * @return {@code true} if search mode consumed the gesture, or {@code false} otherwise.
     */
    public boolean onGesture() {
        // All gestures cancel the search.
        if (mNodeSearch.isActive()) {
            cancelSearch();
            return true;
        }

        return false;
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        // Only handle single-key events here. The KeyComboManager will pass us combos.
        if (event.getModifiers() != 0 || !mNodeSearch.isActive()) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_ENTER:
                    if (mHasNavigated || mNodeSearch.hasMatch()) {
                        finishSearch();
                        mContext.getCursorController().clickCurrent();
                    } else {
                        cancelSearch();
                    }
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    resetHintTime();
                    final String queryText = mNodeSearch.getCurrentQuery();
                    if (queryText.isEmpty()) {
                        cancelSearch();
                    } else {
                        final String lastChar = queryText.substring(queryText.length() - 1);
                        mNodeSearch.backspaceQueryText();
                        mSpeechController.speak(
                                mContext.getString(R.string.template_text_removed, lastChar),
                                SpeechController.QUEUE_MODE_FLUSH_ALL,
                                FeedbackItem.FLAG_NO_HISTORY, null);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveToEnd(NodeFocusFinder.SEARCH_BACKWARD);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    moveToNext(NodeFocusFinder.SEARCH_BACKWARD);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveToEnd(NodeFocusFinder.SEARCH_FORWARD);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveToNext(NodeFocusFinder.SEARCH_FORWARD);
                    return true;
                case KeyEvent.KEYCODE_SPACE:
                    resetHintTime();
                    if (mNodeSearch.tryAddQueryText(" ")) {
                        mSpeechController.speak(mContext.getString(R.string.symbol_space),
                                SpeechController.QUEUE_MODE_FLUSH_ALL,
                                FeedbackItem.FLAG_NO_HISTORY, null);
                    } else {
                        mContext.getFeedbackController().playAuditory(R.raw.complete);
                    }
                    return true;
                default:
                    if (event.isPrintingKey()) {
                        resetHintTime();
                        final String key = String.valueOf(event.getDisplayLabel());
                        if (mNodeSearch.tryAddQueryText(key)) {
                            mSpeechController.speak(key.toLowerCase(),
                                    SpeechController.QUEUE_MODE_FLUSH_ALL,
                                    FeedbackItem.FLAG_NO_HISTORY, null);
                        } else {
                            mContext.getFeedbackController().playAuditory(R.raw.complete);
                        }
                        return true;
                    }
                    break;
            }
        }

        return false;
    }

    @Override
    public boolean processWhenServiceSuspended() {
        return false;
    }

    @Override
    public boolean onComboPerformed(int id) {
        if (id == KeyComboManager.ACTION_TOGGLE_SEARCH) {
            toggleSearch();
            return true;
        }

        // No other combos should be consumed if search mode is not active.
        if (!mNodeSearch.isActive()) {
            return false;
        }

        switch (id) {
            case KeyComboManager.ACTION_NAVIGATE_PREVIOUS:
                moveToNext(NodeFocusFinder.SEARCH_BACKWARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_NEXT:
                moveToNext(NodeFocusFinder.SEARCH_FORWARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_FIRST:
                moveToEnd(NodeFocusFinder.SEARCH_BACKWARD);
                return true;
            case KeyComboManager.ACTION_NAVIGATE_LAST:
                moveToEnd(NodeFocusFinder.SEARCH_FORWARD);
                return true;
            case KeyComboManager.ACTION_PERFORM_CLICK:
                if (mHasNavigated || mNodeSearch.hasMatch()) {
                    finishSearch();
                    mContext.getCursorController().clickCurrent();
                } else {
                    cancelSearch();
                }
                return true;
        }

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mNodeSearch.isActive()) {
            return;
        }

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                cancelSearch();
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                if (mNodeSearch.hasMatch()) {
                    mNodeSearch.reEvaluateSearch();
                }
                break;
            default:
                break;
        }
    }

    /**
     * Move accessibility focus to the next matching node in the specified direction. If no match
     * has been found yet, simply focus the next node in that direction on the screen.
     *
     * @param direction The direction in which to move, either
     * {@link NodeFocusFinder#SEARCH_BACKWARD} or {@link NodeFocusFinder#SEARCH_FORWARD}.
     * @return {@code true} if the accessibility focus was moved, or {@code false} otherwise.
     */
    private boolean moveToNext(int direction) {
        resetHintTime();
        final boolean result;
        if (mNodeSearch.hasMatch()) {
            result = mNodeSearch.nextResult(direction);
        } else if (direction == NodeFocusFinder.SEARCH_BACKWARD) {
            result = mContext.getCursorController().previous(
                    false /* shouldWrap */, true /* shouldScroll */,
                    false /*useInputFocusAsPivotIfEmpty*/, InputModeManager.INPUT_MODE_KEYBOARD);
        } else {
            result = mContext.getCursorController().next(
                    false /* shouldWrap */, true /* shouldScroll */,
                    false /*useInputFocusAsPivotIfEmpty*/, InputModeManager.INPUT_MODE_KEYBOARD);
        }

        mHasNavigated = true;
        return result;
    }

    /**
     * Move accessibility focus to the last matching node in the specified direction. If no match
     * has been found yet, simply focus the last node in that direction on the screen.
     *
     * @param direction The direction in which to move, either
     * {@link NodeFocusFinder#SEARCH_BACKWARD} or {@link NodeFocusFinder#SEARCH_FORWARD}.
     * @return {@code true} if the accessibility focus was moved, or {@code false} otherwise.
     */
    private boolean moveToEnd(int direction) {
        resetHintTime();
        final boolean result;
        if (mNodeSearch.hasMatch()) {
            result = mNodeSearch.nextResult(direction);
            while (mNodeSearch.nextResult(direction)) {}
        } else if (direction == NodeFocusFinder.SEARCH_BACKWARD) {
            result = mContext.getCursorController().jumpToTop(InputModeManager.INPUT_MODE_KEYBOARD);
        } else {
            result = mContext.getCursorController().jumpToBottom(
                    InputModeManager.INPUT_MODE_KEYBOARD);
        }

        mHasNavigated = true;
        return result;
    }

    /**
     * Reset the hint's delay time so that the delay is counted from the time this method is called.
     */
    private void resetHintTime() {
        mHandler.removeCallbacks(mHint);
        mHandler.postDelayed(mHint, HINT_DELAY);
    }

    /**
     * Start search mode.
     */
    private void startSearch() {
        AccessibilityNodeInfoCompat focused = FocusFinder.getFocusedNode(mContext, true);
        mInitialNode.reset(focused);
        mHasNavigated = false;
        mNodeSearch.startSearch();
        mSpeechController.speak(mContext.getString(R.string.search_mode_open),
                SpeechController.QUEUE_MODE_FLUSH_ALL, FeedbackItem.FLAG_NO_HISTORY, null);
        mHandler.postDelayed(mHint, HINT_DELAY);
    }

    /**
     * Finish the current search. Exit search mode and leave the accessibility focus on the result.
     */
    private void finishSearch() {
        mHandler.removeCallbacks(mHint);
        mNodeSearch.stopSearch();
        mSpeechController.speak(mContext.getString(R.string.search_mode_finish),
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, FeedbackItem.FLAG_NO_HISTORY, null);
    }

    /**
     * Cancel the current search. Return accessibility focus to the initial node.
     */
    private void cancelSearch() {
        mHandler.removeCallbacks(mHint);
        mNodeSearch.stopSearch();

        mSpeechController.speak(mContext.getString(R.string.search_mode_cancel),
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, FeedbackItem.FLAG_NO_HISTORY, null);

        AccessibilityNodeInfoCompat focused = FocusFinder.getFocusedNode(mContext, false);
        if (focused == null) {
            return;
        }

        try {
            mInitialNode.reset(AccessibilityNodeInfoUtils.refreshNode(mInitialNode.get()));
            if (!AccessibilityNodeInfoRef.isNull(mInitialNode)) {
                if (mInitialNode.get().isAccessibilityFocused()) {
                    return;
                }
                PerformActionUtils.performAction(mInitialNode.get(),
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            } else {
                PerformActionUtils.performAction(focused,
                        AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }
        } finally {
            focused.recycle();
        }
    }

    /** The runnable that speaks the hint. */
    private final Runnable mHint = new Runnable() {
        @Override
        public void run() {
            String hint = mContext.getString(R.string.search_mode_hint_start);

            hint += " ";

            final String queryText = mNodeSearch.getCurrentQuery();
            if (queryText.isEmpty()) {
                hint += mContext.getString(R.string.search_mode_hint_no_query);
            } else {
                final int length = queryText.length();
                String separatedQuery = "";

                for (int i = 0; i < length; i++) {
                    final Character currentChar = queryText.charAt(i);
                    if (Character.isWhitespace(currentChar)) {
                        separatedQuery += mContext.getString(R.string.symbol_space);
                    } else {
                        separatedQuery += currentChar;
                    }
                    separatedQuery += ", ";
                }

                // Remove the extra comma and space.
                separatedQuery = separatedQuery.substring(0, separatedQuery.length() - 2);
                hint += mContext.getString(R.string.search_mode_hint_query, separatedQuery);
            }

            hint += " ";

            if (mHasNavigated || mNodeSearch.hasMatch()) {
                AccessibilityNodeInfoCompat selected = FocusFinder.getFocusedNode(mContext, false);
                if (selected != null) {
                    final CharSequence matchText =
                            AccessibilityNodeInfoUtils.getNodeText(selected, mLabelManager);
                    if (matchText != null && matchText.length() > 0) {
                        hint += mContext.getString(R.string.search_mode_hint_selection, matchText);
                        mSpeechController.speak(hint, SpeechController.QUEUE_MODE_FLUSH_ALL,
                                FeedbackItem.FLAG_NO_HISTORY, null);
                        return;
                    }
                }
            }

            hint += mContext.getString(R.string.search_mode_hint_no_selection);
            mSpeechController.speak(hint, SpeechController.QUEUE_MODE_FLUSH_ALL,
                    FeedbackItem.FLAG_NO_HISTORY, null);
        }
    };
}
