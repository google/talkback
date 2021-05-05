/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Mappers;
import com.google.android.accessibility.talkback.Mappers.Variables;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Accessibility-focus feedback-mapper. */
public class AccessibilityFocusManager {

  private AccessibilityFocusManager() {} // Not instantiable

  /** Feedback-mapping function. */
  public static Feedback.Part.Builder onViewTargeted(
      @Nullable EventId eventId, Variables variables, int depth) {
    LogDepth.logFunc(Mappers.LOG_TAG, ++depth, "onViewTargeted");

    @Nullable AccessibilityNodeInfoCompat targetedNode = variables.inputFocusTarget(depth);
    if (targetedNode == null || !targetedNode.refresh()) {
      return null;
    }

    FocusActionInfo focusActionInfo =
        FocusActionInfo.builder().setSourceAction(FocusActionInfo.FOCUS_SYNCHRONIZATION).build();
    return Feedback.part().setFocus(Feedback.focus(targetedNode, focusActionInfo).build());
  }

  /** Feedback-mapping function. */
  public static Feedback.Part.Builder onScrollEvent(
      @Nullable EventId eventId, Variables variables, int depth, FocusFinder focusFinder) {
    return FocusProcessorForManualScroll.onNodeManuallyScrolled(
        eventId, variables, depth, focusFinder);
  }

}
