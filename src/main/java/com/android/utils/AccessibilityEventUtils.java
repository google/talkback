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

}
