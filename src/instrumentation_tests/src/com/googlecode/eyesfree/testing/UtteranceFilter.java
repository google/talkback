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

package com.googlecode.eyesfree.testing;

import com.android.talkback.Utterance;
import com.android.utils.StringBuilderUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Filter used to match utterances against a set of rules.
 */
public class UtteranceFilter {
    private final List<CharSequenceFilter> mTextFilters = new LinkedList<>();

    /**
     * Adds a filter to be applied to the value of {@link Utterance#getText()}.
     */
    public UtteranceFilter addTextFilter(CharSequenceFilter textFilter) {
        mTextFilters.add(textFilter);
        return this;
    }

    /**
     * Returns whether the specified utterance matches all of the rules
     * added to the filter.
     *
     * @param utterance The utterance to test against the filter.
     * @return Whether the utterance matches all of the rules added to the
     *         filter.
     */
    public boolean matches(Utterance utterance) {
        final CharSequence text = StringBuilderUtils.getAggregateText(utterance.getSpoken());

        for (CharSequenceFilter textFilter : mTextFilters) {
            if (!textFilter.matches(text)) {
                return false;
            }
        }

        return true;
    }
}
