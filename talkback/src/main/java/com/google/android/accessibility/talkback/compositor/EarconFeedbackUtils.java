/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.compositor;

import static android.view.View.ACCESSIBILITY_LIVE_REGION_NONE;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import java.util.Locale;

/**
 * Provides earcon, earcon rate and earcon volume values for the event feedback rules to compose
 * {@link EventFeedback}
 */
public final class EarconFeedbackUtils {

  /**
   * Returns the progress bar earcon when the progress bar values changes.
   *
   * <p>Note: If the source node is not progress bar, it returns the default value. And {@link
   * TYPE_WINDOW_CONTENT_CHANGED} and {@link TYPE_VIEW_SELECTED} usually inform that progress bar
   * changes.
   */
  public static @RawRes int getProgressBarChangeEarcon(
      AccessibilityEvent event, AccessibilityNodeInfoCompat srcNode, Locale preferredLocale) {
    int eventType = event.getEventType();
    if (srcNode == null
        || Role.getRole(srcNode) != Role.ROLE_PROGRESS_BAR
        || (eventType != TYPE_WINDOW_CONTENT_CHANGED && eventType != TYPE_VIEW_SELECTED)) {
      return -1;
    }

    CharSequence eventDescription =
        AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
            event, preferredLocale);
    if ((event.getSource() == null
        || srcNode.isFocused()
        || srcNode.isAccessibilityFocused()
        || srcNode.getLiveRegion() != ACCESSIBILITY_LIVE_REGION_NONE)) {
      return (!event.getText().isEmpty() && !srcNode.getText().equals(eventDescription))
          ? -1
          : R.raw.scroll_tone;
    }
    return -1;
  }

  /**
   * Returns the progress bar earcon rate when the progress bar values changes.
   *
   * <p>Note: If the source node is not progress bar, it returns the default value. And {@link
   * TYPE_WINDOW_CONTENT_CHANGED} and {@link TYPE_VIEW_SELECTED} usually inform that progress bar
   * changes.
   */
  public static double getProgressBarChangeEarconRate(
      AccessibilityEvent event, AccessibilityNodeInfoCompat srcNode) {
    int eventType = event.getEventType();
    if (srcNode == null
        || Role.getRole(srcNode) != Role.ROLE_PROGRESS_BAR
        || (eventType != TYPE_WINDOW_CONTENT_CHANGED && eventType != TYPE_VIEW_SELECTED)) {
      return 1;
    }

    double value;
    @Nullable RangeInfoCompat rangeInfo = srcNode.getRangeInfo();
    if (rangeInfo == null) {
      value = AccessibilityEventUtils.getProgressPercent(event);
    } else {
      int type = rangeInfo.getType();
      value =
          (type == RangeInfoCompat.RANGE_TYPE_PERCENT)
              ? rangeInfo.getCurrent()
              : AccessibilityNodeInfoUtils.getProgressPercent(srcNode);
    }
    return Math.pow(2.0, ((value / 50.0) - 1));
  }

  /**
   * Returns the progress bar earcon volume when the progress bar values changes.
   *
   * <p>Note: If the source node is not progress bar, it returns the default value. And {@link
   * TYPE_WINDOW_CONTENT_CHANGED} and {@link TYPE_VIEW_SELECTED} usually inform that progress bar
   * changes.
   */
  public static double getProgressBarChangeEarconVolume(
      AccessibilityEvent event, AccessibilityNodeInfoCompat srcNode) {
    int eventType = event.getEventType();
    if (Role.getRole(srcNode) == Role.ROLE_PROGRESS_BAR
        && (eventType == TYPE_WINDOW_CONTENT_CHANGED || eventType == TYPE_VIEW_SELECTED)) {
      return 0.5;
    }
    return 1.0d;
  }

  private EarconFeedbackUtils() {}
}
