/*
 * Copyright (C) 2011 Google Inc.
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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.utils.WebInterfaceUtils;

import java.util.Arrays;
import java.util.List;

/**
 * List of different Granularities for node and cursor movement in TalkBack.
 */
public enum CursorGranularity {
    // 0 is the default Android value when you want something outside the bit mask
    // TODO: If rewriting this as a class, use a constant for 0.
    DEFAULT(R.string.granularity_default, 0),
    CHARACTER(R.string.granularity_character,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER),
    WORD(R.string.granularity_word, AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD),
    LINE(R.string.granularity_line, AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE),
    PARAGRAPH(R.string.granularity_paragraph,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH),
    WEB_SECTION(R.string.granularity_web_section, 0),
    WEB_LINK(R.string.granularity_web_link, 0),
    WEB_LIST(R.string.granularity_web_list, 0),
    WEB_CONTROL(R.string.granularity_web_control, 0);

    /** Used to to represent a granularity with no framework value. */
    private static final int NO_VALUE = 0;

    /** The resource identifier for this granularity's user-visible name. */
    public final int resourceId;

    /**
     * The framework value for this granularity, passed as an argument to
     * {@link AccessibilityNodeInfoCompat#ACTION_NEXT_AT_MOVEMENT_GRANULARITY}.
     */
    public final int value;

    /**
     * Constructs a new granularity with the specified system identifier.
     * @param value The system identifier. See the GRANULARITY_ constants in
     *            {@link AccessibilityNodeInfoCompat} for a complete list.
     */
    private CursorGranularity(int resourceId, int value) {
        this.resourceId = resourceId;
        this.value = value;
    }

    /**
     * Returns the granularity associated with a particular key.
     *
     * @param resourceId The key associated with a granularity.
     * @return The granularity associated with the key, or {@code null} if the key is invalid.
     */
    public static CursorGranularity fromResourceId(int resourceId) {
        for (CursorGranularity value : values()) {
            if (value.resourceId == resourceId) {
                return value;
            }
        }

        return null;
    }

    /**
     * Populates {@code result} with the {@link CursorGranularity}s represented
     * by the {@code bitmask} of granularity framework values. The
     * {@link #DEFAULT} granularity is always returned as the first item in the
     * list.
     *
     * @param bitmask A bit mask of granularity framework values.
     * @param hasWebContent Whether the view has web content.
     * @param result The list to populate with supported granularities.
     */
    public static void extractFromMask(
            int bitmask, boolean hasWebContent, String[] supportedHtmlElements,
            List<CursorGranularity> result) {
        result.clear();
        result.add(DEFAULT);

        if (hasWebContent) {
            if (supportedHtmlElements == null) {
                result.add(WEB_SECTION);
                result.add(WEB_LIST);
                result.add(WEB_CONTROL);
            } else {
                List<String> elements = Arrays.asList(supportedHtmlElements);
                if (elements.contains(WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_SECTION)) {
                    result.add(WEB_SECTION);
                }
                if (elements.contains(WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_LINK)) {
                    result.add(WEB_LINK);
                }
                if (elements.contains(WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_CONTROL)) {
                    result.add(WEB_CONTROL);
                }
            }
        }

        for (CursorGranularity value : values()) {
            if (value.value == NO_VALUE) {
                continue;
            }

            if ((bitmask & value.value) == value.value) {
                result.add(value);
            }
        }
    }

    /**
     * @return Whether {@code granularity} is a web-specific granularity.
     */
    public boolean isWebGranularity() {
        // For some reason R.string cannot be used in a switch statement
        return resourceId == R.string.granularity_web_section ||
                resourceId == R.string.granularity_web_link ||
                resourceId == R.string.granularity_web_list ||
                resourceId == R.string.granularity_web_control;
    }
}
