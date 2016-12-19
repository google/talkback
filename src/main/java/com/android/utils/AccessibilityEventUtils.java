/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.utils;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

/**
 * This class contains utility methods.
 */
public class AccessibilityEventUtils {
    private AccessibilityEventUtils() {
        // This class is not instantiable.
    }

    /**
     * Determines if an accessibility event is of a type defined by a mask of
     * qualifying event types.
     *
     * @param event The event to evaluate
     * @param typeMask A mask of event types that will cause this method to
     *            accept the event as matching
     * @return {@code true} if {@code event}'s type is one of types defined in
     *         {@code typeMask}, {@code false} otherwise
     */
    public static boolean eventMatchesAnyType(AccessibilityEvent event, int typeMask) {
        return event != null && (event.getEventType() & typeMask) != 0;
    }

    /**
     * Gets the text of an <code>event</code> by returning the content description
     * (if available) or by concatenating the text members (regardless of their
     * priority) using space as a delimiter.
     *
     * @param event The event.
     * @return The event text.
     */
    public static CharSequence getEventTextOrDescription(AccessibilityEvent event) {
        if (event == null) {
            return null;
        }

        final CharSequence contentDescription = event.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            return contentDescription;
        }

        return getEventAggregateText(event);
    }

    /**
     * Gets the text of an <code>event</code> by concatenating the text members
     * (regardless of their priority) using space as a delimiter.
     *
     * @param event The event.
     * @return The event text.
     */
    public static CharSequence getEventAggregateText(AccessibilityEvent event) {
        if (event == null) {
            return null;
        }

        final SpannableStringBuilder aggregator = new SpannableStringBuilder();
        for (CharSequence text : event.getText()) {
            StringBuilderUtils.appendWithSeparator(aggregator, text);
        }

        return aggregator;
    }

    public static boolean isCharacterTraversalEvent(AccessibilityEvent event) {
        return (event.getEventType() ==
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY &&
                event.getMovementGranularity() ==
                        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER);
    }

    public static int[] getAllEventTypes() {
        return new int[]{
                AccessibilityEvent.TYPE_ANNOUNCEMENT,
                AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT,
                AccessibilityEvent.TYPE_GESTURE_DETECTION_END,
                AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
                AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END,
                AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START,
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
                AccessibilityEvent.TYPE_VIEW_HOVER_EXIT,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_SELECTED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        };
    }
}
