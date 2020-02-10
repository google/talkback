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

package com.google.android.accessibility.utils.input;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** List of different Granularities for node and cursor movement in TalkBack. */
public enum CursorGranularity {
  // 0 is the default Android value when you want something outside the bit mask
  // TODO: If rewriting this as a class, use a constant for 0.
  DEFAULT(R.string.granularity_default, 1, 0),
  CHARACTER(
      R.string.granularity_character,
      2,
      AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER),
  WORD(R.string.granularity_word, 3, AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD),
  LINE(R.string.granularity_line, 4, AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE),
  PARAGRAPH(
      R.string.granularity_paragraph,
      5,
      AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH),
  WEB_HEADING(R.string.granularity_web_heading, 6, 0),
  WEB_LINK(R.string.granularity_web_link, 7, 0),
  WEB_LIST(R.string.granularity_web_list, 8, 0),
  WEB_CONTROL(R.string.granularity_web_control, 9, 0),
  HEADING(R.string.granularity_native_heading, 10, 0),
  CONTROL(R.string.granularity_native_control, 11, 0),
  LINK(R.string.granularity_native_link, 12, 0),
  WEB_LANDMARK(R.string.granularity_web_landmark, 13, 0);

  /** Used to represent a granularity with no framework value. */
  private static final int NO_VALUE = 0;

  /** The resource identifier for this granularity's user-visible name. */
  public final int resourceId;

  public final int id;

  /**
   * The framework value for this granularity, passed as an argument to {@link
   * AccessibilityNodeInfoCompat#ACTION_NEXT_AT_MOVEMENT_GRANULARITY}.
   */
  public final int value;

  /**
   * Constructs a new granularity with the specified system identifier.
   *
   * @param value The system identifier. See the GRANULARITY_ constants in {@link
   *     AccessibilityNodeInfoCompat} for a complete list.
   */
  private CursorGranularity(int resourceId, int id, int value) {
    this.resourceId = resourceId;
    this.id = id;
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

  public static CursorGranularity fromId(int id) {
    for (CursorGranularity value : values()) {
      if (value.id == id) {
        return value;
      }
    }

    return null;
  }

  /**
   * Populates {@code result} with the {@link CursorGranularity}s represented by the {@code bitmask}
   * of granularity framework values. The {@link #DEFAULT} granularity is always returned as the
   * first item in the list.
   *
   * @param bitmask A bit mask of granularity framework values.
   * @param hasWebContent Whether the view has web content.
   * @param result The list to populate with supported granularities.
   */
  public static void extractFromMask(
      int bitmask,
      boolean hasWebContent,
      String @Nullable [] supportedHtmlElements,
      List<CursorGranularity> result) {
    result.clear();
    result.add(DEFAULT);

    if (hasWebContent) {
      if (supportedHtmlElements == null) {
        result.add(WEB_HEADING);
        result.add(WEB_LIST);
        result.add(WEB_CONTROL);
      } else {
        List<String> elements = Arrays.asList(supportedHtmlElements);
        if (elements.contains(WebInterfaceUtils.HTML_ELEMENT_MOVE_BY_HEADING)) {
          result.add(WEB_HEADING);
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

    if (!hasWebContent) {
      result.add(HEADING);
      result.add(LINK);
      result.add(CONTROL);
    }
  }

  /** @return whether {@code granularity} is a web-specific granularity. */
  public boolean isWebGranularity() {
    // For some reason R.string cannot be used in a switch statement
    return resourceId == R.string.granularity_web_heading
        || resourceId == R.string.granularity_web_link
        || resourceId == R.string.granularity_web_list
        || resourceId == R.string.granularity_web_control
        || resourceId == R.string.granularity_web_landmark;
  }

  /**
   * @return whether {@code granularity} is a native macro granularity. Macro granularity refers to
   *     granularity which helps to navigate across multiple nodes in oppose to micro granularity
   *     (Characters, words, etc) which is used to navigate withing a node.
   */
  public boolean isNativeMacroGranularity() {
    return resourceId == R.string.granularity_native_heading
        || resourceId == R.string.granularity_native_control
        || resourceId == R.string.granularity_native_link;
  }

  public boolean isMicroGranularity() {
    return resourceId == R.string.granularity_character
        || resourceId == R.string.granularity_word
        || resourceId == R.string.granularity_line
        || resourceId == R.string.granularity_paragraph;
  }
}
