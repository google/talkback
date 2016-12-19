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

package com.android.talkback.eventprocessor;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.talkback.controller.CursorController;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.Role;
import com.android.utils.StringBuilderUtils;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.controller.FullScreenReadController;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.WeakReferenceHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Manages scroll position feedback. If a VIEW_SCROLLED event passes through
 * this processor and no further events are received for a specified duration, a
 * "scroll position" message is spoken.
 */
public class ProcessorScrollPosition
        implements AccessibilityEventListener, CursorController.ScrollListener {
    /** Default pitch adjustment for text event feedback. */
    private static final float DEFAULT_PITCH = 1.2f;

    /** Default rate adjustment for text event feedback. */
    private static final float DEFAULT_RATE = 1.0f;

    private final HashMap<EventId, Integer> mCachedFromValues = new HashMap<>();
    private final HashMap<EventId, Integer> mCachedItemCounts = new HashMap<>();
    private final Bundle mSpeechParams = new Bundle();
    private final ScrollPositionHandler mHandler = new ScrollPositionHandler(this);

    private final Context mContext;
    private final SpeechController mSpeechController;
    private final FullScreenReadController mFullScreenReadController;

    /** The last node that was auto-scrolled by the CursorController. */
    private AccessibilityNodeInfoCompat mAutoScrollNode;

    public ProcessorScrollPosition(FullScreenReadController fullScreenReadController,
                                   SpeechController speechController,
                                   CursorController cursorController,
                                   TalkBackService context) {
        if (speechController == null) throw new IllegalStateException();
        if (fullScreenReadController == null) throw new IllegalStateException();
        if (cursorController == null) throw new IllegalStateException();
        mContext = context;
        mSpeechController = speechController;
        mFullScreenReadController = fullScreenReadController;
        mSpeechParams.putFloat(SpeechController.SpeechParam.PITCH, DEFAULT_PITCH);
        mSpeechParams.putFloat(SpeechController.SpeechParam.RATE, DEFAULT_RATE);
        cursorController.addScrollListener(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (shouldIgnoreEvent(event)) {
            return;
        }

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Window state changes clear the cache.
                mCachedFromValues.clear();
                mCachedItemCounts.clear();
                mHandler.cancelScrollFeedback();
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                mHandler.postScrollFeedback(event);
                break;
        }
    }

    @Override
    public void onScroll(AccessibilityNodeInfoCompat scrolledNode, int action, boolean auto) {
        AccessibilityNodeInfoUtils.recycleNodes(mAutoScrollNode);
        if (auto) {
            mAutoScrollNode = AccessibilityNodeInfoCompat.obtain(scrolledNode);
        } else {
            mAutoScrollNode = null;
        }
    }

    private boolean shouldIgnoreEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
            case AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
                return true;
            case AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED:
            case AccessibilityEventCompat.TYPE_VIEW_SCROLLED:
                return shouldIgnoreUpdateListEvent(event);
            default:
                return false;
        }
    }

    private boolean shouldIgnoreUpdateListEvent(AccessibilityEvent event) {
        // Don't speak during full-screen read.
        if (mFullScreenReadController.isActive()) {
            return true;
        }

        final int fromIndex = event.getFromIndex() + 1;
        final int itemCount = event.getItemCount();
        if (itemCount <= 0 || fromIndex <= 0) {
            return true;
        }

        EventId eventId;
        try {
            eventId = new EventId(event);
        } catch (Exception e) {
            return true;
        }

        final Integer cachedFromIndex = mCachedFromValues.get(eventId);
        final Integer cachedItemCount = mCachedItemCounts.get(eventId);

        if ((cachedFromIndex != null) && (cachedFromIndex == fromIndex) &&
                (cachedItemCount != null) && (cachedItemCount == itemCount)) {
            // The from index hasn't changed, which means the event is coming
            // from a re-layout or resize and should not be spoken.
            return true;
        }

        // The behavior of put() for an existing key is unspecified, so we can't
        // recycle the old or new key nodes.
        mCachedFromValues.put(eventId, fromIndex);
        mCachedItemCounts.put(eventId, itemCount);

        // Allow the list indices to be cached, but don't actually speak after auto-scroll.
        if (mAutoScrollNode != null) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                try {
                    if (source.equals(mAutoScrollNode.getInfo())) {
                        mAutoScrollNode.recycle();
                        mAutoScrollNode = null;
                        return true;
                    }
                } finally {
                    source.recycle();
                }
            }
        }

        return false;
    }

    /**
     * Given an {@link AccessibilityEvent}, speaks a scroll position.
     *
     * @param event The source event.
     */
    private void handleScrollFeedback(AccessibilityEvent event) {
        final CharSequence text;
        AccessibilityNodeInfo source = event.getSource();
        if (Role.getRole(source) == Role.ROLE_PAGER) {
            text = getDescriptionForPageEvent(event, source);
        } else {
            text = getDescriptionForScrollEvent(event);
        }
        if (source != null) {
            source.recycle();
        }

        if (TextUtils.isEmpty(text)) {
            return;
        }

        // don't pronounce non-visible nodes
        AccessibilityNodeInfo node = event.getSource();
        if (node != null && !node.isVisibleToUser()) {
            return;
        }

        // Use QUEUE mode so that we don't interrupt more important messages.
        mSpeechController.speak(text, SpeechController.QUEUE_MODE_QUEUE, 0, mSpeechParams);
    }

    private CharSequence getDescriptionForScrollEvent(AccessibilityEvent event) {
        // If the from index or item count are invalid, don't announce anything.
        final int fromIndex = (event.getFromIndex() + 1);
        final int itemCount = event.getItemCount();
        if ((fromIndex <= 0) || (itemCount <= 0)) {
            return null;
        }

        // If the to and from indices are the same, or if the to index is
        // invalid, only announce the item at the from index.
        final int toIndex = event.getToIndex() + 1;
        if ((fromIndex == toIndex) || (toIndex <= 0) || (toIndex > itemCount)) {
            return mContext.getString(R.string.template_scroll_from_count, fromIndex, itemCount);
        }

        // Announce the range of visible items.
        return mContext.getString(
                R.string.template_scroll_from_to_count, fromIndex, toIndex, itemCount);
    }

    private CharSequence getDescriptionForPageEvent(AccessibilityEvent event,
            AccessibilityNodeInfo source) {
        final int fromIndex = (event.getFromIndex() + 1);
        final int itemCount = event.getItemCount();
        if ((fromIndex <= 0) || (itemCount <= 0)) {
            return null;
        }

        CharSequence pageTitle = getSelectedPageTitle(source);
        if (!TextUtils.isEmpty(pageTitle)) {
            CharSequence count = mContext.getString(R.string.template_viewpager_index_count_short,
                    fromIndex, itemCount);

            SpannableStringBuilder output = new SpannableStringBuilder();
            StringBuilderUtils.appendWithSeparator(output, pageTitle, count);
            return output;
        }

        return mContext.getString(R.string.template_viewpager_index_count, fromIndex, itemCount);
    }

    private static CharSequence getSelectedPageTitle(AccessibilityNodeInfo node) {
        // We need to refresh() after the scroll to get an accurate page title but we can only
        // do that on API 18+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return null;
        }

        if (node == null) {
            return null;
        }

        AccessibilityNodeInfoCompat nodeCompat = new AccessibilityNodeInfoCompat(node);
        nodeCompat.refresh();

        int numChildren = nodeCompat.getChildCount(); // Not the number of pages!
        CharSequence title = null;
        for (int i = 0; i < numChildren; ++i) {
            AccessibilityNodeInfoCompat child = nodeCompat.getChild(i);
            if (child != null) {
                try {
                    if (child.isVisibleToUser()) {
                        if (title == null) {
                            // Try to roughly match RulePagerPage, which uses getNodeText
                            // (but completely matching all the time is not critical).
                            title = AccessibilityNodeInfoUtils.getNodeText(child);
                        } else {
                            // Multiple visible children, abort.
                            return null;
                        }
                    }
                } finally {
                    child.recycle();
                }
            }
        }

        return title;
    }

    private static class ScrollPositionHandler
            extends WeakReferenceHandler<ProcessorScrollPosition> {
        /** Message identifier for a scroll position notification. */
        private static final int SCROLL_FEEDBACK = 1;

        /** Delay before reading a scroll position notification. */
        private static final long DELAY_SCROLL_FEEDBACK = 1000;

        /** Delay before reading a page position notification. */
        private static final long DELAY_PAGE_FEEDBACK = 500;

        public ScrollPositionHandler(ProcessorScrollPosition parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorScrollPosition parent) {
            final AccessibilityEvent event = (AccessibilityEvent) msg.obj;
            switch (msg.what) {
                case SCROLL_FEEDBACK:
                    parent.handleScrollFeedback(event);
                    break;
            }

            event.recycle();
        }

        /**
         * Posts the delayed scroll position feedback. Call this for every
         * VIEW_SCROLLED event.
         */
        private void postScrollFeedback(AccessibilityEvent event) {
            cancelScrollFeedback();
            final AccessibilityEvent eventClone = AccessibilityEvent.obtain(event);
            final Message msg = obtainMessage(SCROLL_FEEDBACK, eventClone);

            AccessibilityNodeInfo source = event.getSource();
            if (Role.getRole(source) == Role.ROLE_PAGER) {
                sendMessageDelayed(msg, DELAY_PAGE_FEEDBACK);
            } else {
                sendMessageDelayed(msg, DELAY_SCROLL_FEEDBACK);
            }
            if (source != null) {
                source.recycle();
            }
        }

        /**
         * Removes any pending scroll position feedback. Call this for every
         * event.
         */
        private void cancelScrollFeedback() {
            removeMessages(SCROLL_FEEDBACK);
        }
    }

    private static class EventId {
        public long nodeId;
        public int windowId;
        private final int hashcode;

        private static Method sGetSourceNodeIdMethod;
        private static final String LOGTAG = "EventId";
        static {
            try {
                sGetSourceNodeIdMethod =
                        AccessibilityRecord.class.getDeclaredMethod("getSourceNodeId");
                sGetSourceNodeIdMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                Log.d(LOGTAG, "Error setting up fields: " + e.toString());
                e.printStackTrace();
            }
        }

        public EventId(long nodeId, int windowId) {
            this.nodeId = nodeId;
            this.windowId = windowId;
            hashcode = (int) (nodeId ^ (nodeId >>> 32)) + windowId * 7;
        }

        public EventId(AccessibilityEvent event) throws InvocationTargetException,
                IllegalAccessException {
            this((long) sGetSourceNodeIdMethod.invoke(event), event.getWindowId());
        }

        @Override
        public boolean equals(Object other) {
            if (! (other instanceof EventId)) {
                return false;
            }

            EventId otherId = (EventId) other;
            return windowId == otherId.windowId && nodeId == otherId.nodeId;
        }

        @Override
        public int hashCode() {
            return hashcode;
        }
    }
}