/*
 * Copyright (C) 2016 Google Inc.
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

package com.googlecode.eyesfree.testing;

import com.android.talkback.FeedbackItem;

import java.util.LinkedList;
import java.util.List;

/**
 * Filter used to match raw speech feedback items against a set of rules.
 */
public class FeedbackItemFilter {
    private final List<CharSequenceFilter> mTextFilters = new LinkedList<>();

    /**
     * Adds a filter to be applied to the value of {@link FeedbackItem#getAggregateText()}.
     */
    public FeedbackItemFilter addTextFilter(CharSequenceFilter textFilter) {
        mTextFilters.add(textFilter);
        return this;
    }

    /**
     * Returns whether the specified feedback item matches all of the rules added to the filter.
     *
     * @param feedbackItem The feedback item to test against the filter.
     * @return Whether the feedback item matches all of the rules added to the filter.
     */
    public boolean matches(FeedbackItem feedbackItem) {
        final CharSequence text = feedbackItem.getAggregateText();

        for (CharSequenceFilter textFilter : mTextFilters) {
            if (!textFilter.matches(text)) {
                return false;
            }
        }

        return true;
    }
}
