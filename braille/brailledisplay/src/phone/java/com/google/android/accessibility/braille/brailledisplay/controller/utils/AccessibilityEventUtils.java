/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller.utils;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

/** This class contains utility methods. */
public class AccessibilityEventUtils {
  private AccessibilityEventUtils() {
    // This class is not instantiable.
  }

  /**
   * Gets the text of an <code>event</code> by returning the content description (if available) or
   * by concatenating the text members (regardless of their priority) using space as a delimiter.
   *
   * @param event The event.
   * @return The event text.
   */
  @Nullable
  public static CharSequence getEventText(AccessibilityEvent event) {
    if (event == null) {
      return null;
    }

    final CharSequence contentDescription = event.getContentDescription();

    if (!TextUtils.isEmpty(contentDescription)) {
      return contentDescription;
    } else {
      return getEventAggregateText(event);
    }
  }

  /**
   * Gets the text of an <code>event</code> by concatenating the text members (regardless of their
   * priority) using space as a delimiter.
   *
   * @param event The event.
   * @return The event text.
   */
  private static CharSequence getEventAggregateText(AccessibilityEvent event) {
    final StringBuilder aggregator = new StringBuilder();
    final List<CharSequence> eventText = event.getText();
    final Iterator<CharSequence> it = eventText.iterator();

    while (it.hasNext()) {
      final CharSequence text = it.next();

      if (it.hasNext()) {
        StringUtils.appendWithSpaces(aggregator, text);
      } else {
        aggregator.append(text);
      }
    }

    return aggregator;
  }
}
