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
package com.google.android.accessibility.talkback.compositor.rule;

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_SCROLL_POSITION;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_SCROLL_POSITION} event. These rules will provide the event
 * feedback output function by inputting the {@link HandleEventOptions} and outputting {@link
 * EventFeedback}.
 */
public class ScrollPositionFeedbackRule {

  private static final String TAG = "ScrollPositionFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   * @param globalVariables the global compositor variables
   */
  public static void addFeedbackRules(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context,
      GlobalVariables globalVariables) {
    eventFeedbackRules.put(
        EVENT_SCROLL_POSITION,
        (eventOptions) -> {
          CharSequence ttsOutput =
              getScrollPositionText(eventOptions.eventObject, context, globalVariables);
          LogUtils.v(TAG, " ttsOutputRule= scrollPositionText ");
          return EventFeedback.builder()
              .setTtsOutput(Optional.of(ttsOutput))
              .setQueueMode(QUEUE_MODE_QUEUE)
              .setForceFeedbackEvenIfAudioPlaybackActive(true)
              .setForceFeedbackEvenIfMicrophoneActive(true)
              .setForceFeedbackEvenIfSsbActive(false)
              .setTtsPitch(1.2d)
              .build();
        });
  }

  /** Returns the scroll position text. */
  public static CharSequence getScrollPositionText(
      AccessibilityEvent event, Context context, GlobalVariables globalVariables) {
    if (event == null) {
      return "";
    }

    if (Role.getSourceRole(event) == Role.ROLE_PAGER) {
      LogUtils.v(TAG, " scrollPositionText role_pager");
      return AccessibilityEventFeedbackUtils.getPagerIndexCount(event, context, globalVariables);
    }

    int fromIndex = event.getFromIndex();
    int toIndex = event.getToIndex();
    int itemCount = event.getItemCount();
    LogUtils.v(
        TAG,
        StringBuilderUtils.joinFields(
            " scrollPositionText ",
            StringBuilderUtils.optionalInt("fromIndex", fromIndex, -1),
            StringBuilderUtils.optionalInt("toIndex", toIndex, -1),
            StringBuilderUtils.optionalInt("itemCount", itemCount, -1)));
    if (fromIndex >= 0 && itemCount > 0) {
      if (fromIndex == toIndex || toIndex < 0 || (toIndex + 1) > itemCount) {
        return context.getString(com.google.android.accessibility.utils.R.string.template_scroll_from_count, fromIndex + 1, itemCount);
      } else {
        return context.getString(
            com.google.android.accessibility.utils.R.string.template_scroll_from_to_count, fromIndex + 1, toIndex + 1, itemCount);
      }
    }
    return "";
  }
}
