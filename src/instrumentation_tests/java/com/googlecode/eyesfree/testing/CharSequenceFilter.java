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

import android.text.TextUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filter used to match character sequences against a set of rules.
 */
public class CharSequenceFilter {
    private final List<String> mContainsIgnoreCase = new LinkedList<>();
    private final List<Pattern> mMatchesPattern = new LinkedList<>();

    private String mCachedToString = null;

    /**
     * Adds a case-insensitive containment rule to the filter.
     */
    public CharSequenceFilter addContainsIgnoreCase(CharSequence... partialText) {
        mCachedToString = null;
        for (CharSequence item : partialText) {
            mContainsIgnoreCase.add(item.toString().toLowerCase());
        }
        return this;
    }

    /**
     * Adds a regular expression matching rule to the filter.
     *
     * @see java.util.regex.Pattern#compile(String, int)
     */
    public CharSequenceFilter addMatchesPattern(String regularExpression, int flags) {
        mCachedToString = null;
        mMatchesPattern.add(Pattern.compile(regularExpression, flags));
        return this;
    }

    /**
     * Returns whether the specified character sequence matches all of the
     * rules added to the filter.
     *
     * @param text The character sequence to test against the filter.
     * @return Whether the character sequence matches all of the rules added
     *         to the filter.
     */
    public boolean matches(CharSequence text) {
        if (text == null) {
            return false;
        }

        final String textString = text.toString();
        final String textLowerCase = textString.toLowerCase();

        // Test all case-insensitive containment rules.
        for (String partialTextLowerCase : mContainsIgnoreCase) {
            if (!textLowerCase.contains(partialTextLowerCase)) {
                return false;
            }
        }

        // Test all pattern matching rules.
        for (Pattern pattern : mMatchesPattern) {
            if (!pattern.matcher(text).matches()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        if (mCachedToString != null) {
            return mCachedToString;
        }

        final StringBuilder result = new StringBuilder();

        final StringBuilder containsIgnoreCase = new StringBuilder();
        for (String partialTextLowerCase : mContainsIgnoreCase) {
            containsIgnoreCase.append("\"");
            containsIgnoreCase.append(partialTextLowerCase);
            containsIgnoreCase.append("\", ");
        }

        if (!TextUtils.isEmpty(containsIgnoreCase)) {
            result.append("containsIgnoreCase: {");
            result.append(containsIgnoreCase, 0, containsIgnoreCase.length() - 2);
            result.append("}, ");
        }

        final StringBuilder matchesPattern = new StringBuilder();
        for (Pattern pattern : mMatchesPattern) {
            matchesPattern.append("\"");
            matchesPattern.append(pattern.pattern());
            matchesPattern.append("\", ");
        }

        if (!TextUtils.isEmpty(matchesPattern)) {
            result.append("matches: {");
            result.append(matchesPattern, 0, matchesPattern.length() - 2);
            result.append("}, ");
        }

        if (!TextUtils.isEmpty(result)) {
            result.setLength(result.length() - 2);
        }

        mCachedToString = "{" + result + "}";

        return mCachedToString;
    }
}