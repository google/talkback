/*
 * Copyright (C) 2022 Google Inc.
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

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_WINDOW;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_MAGNIFICATION_CHANGED;
import static com.google.android.accessibility.talkback.compositor.MagnificationState.STATE_OFF;
import static com.google.android.accessibility.talkback.compositor.MagnificationState.STATE_ON;
import static com.google.android.accessibility.talkback.compositor.MagnificationState.STATE_SCALE_CHANGED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.content.Context;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.MagnificationState;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rule for {@link EVENT_MAGNIFICATION_CHANGED}. This rule will provide the event
 * feedback output.
 */
public class MagnificationStateChangedFeedbackRule {

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
        EVENT_MAGNIFICATION_CHANGED,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(
                    Optional.of(
                        getMagnificationStateChangedText(
                            context, globalVariables.getMagnificationState())))
                .setQueueMode(QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH)
                .setTtsClearQueueGroup(SpeechController.UTTERANCE_GROUP_SCREEN_MAGNIFICATION)
                .setTtsInterruptSameGroup(true)
                .setForceFeedbackEvenIfAudioPlaybackActive(false)
                .setForceFeedbackEvenIfMicrophoneActive(false)
                .setForceFeedbackEvenIfSsbActive(false)
                .build());
  }

  /** Returns magnification state changed text. */
  public static CharSequence getMagnificationStateChangedText(
      Context context, MagnificationState state) {
    int currentScale = (int) (state.currentScale() * 100);
    int currentState = state.state();
    Integer mode = state.mode();
    if (currentState == STATE_ON) {
      if (mode == null) {
        return context.getString(R.string.template_magnification_on, currentScale);
      } else if (mode.equals(MAGNIFICATION_MODE_FULLSCREEN)) {
        return context.getString(R.string.template_fullscreen_magnification_on, currentScale);
      } else if (mode.equals(MAGNIFICATION_MODE_WINDOW)) {
        return context.getString(R.string.template_partial_magnification_on, currentScale);
      }
    }
    if (currentState == STATE_OFF) {
      return context.getString(R.string.magnification_off);
    }
    if (currentState == STATE_SCALE_CHANGED) {
      if (mode == null) {
        return context.getString(R.string.template_magnification_scale_changed, currentScale);
      } else if (mode.equals(MAGNIFICATION_MODE_FULLSCREEN)) {
        return context.getString(
            R.string.template_fullscreen_magnification_scale_changed, currentScale);
      } else if (mode.equals(MAGNIFICATION_MODE_WINDOW)) {
        return context.getString(
            R.string.template_partial_magnification_scale_changed, currentScale);
      }
    }
    return "";
  }

  private MagnificationStateChangedFeedbackRule() {}
}
