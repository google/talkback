/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.common;

import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;

/** Exposes TalkBack readout behavior to Braille classes. */
public interface TalkBackSpeaker {

  /** Declares the announcement type from Braille keyboard. */
  enum AnnounceType {
    INTERRUPT,
    INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH,
    ENQUEUE;

    public static int getQueueMode(AnnounceType announceType) {
      switch (announceType) {
        case ENQUEUE:
          return SpeechController.QUEUE_MODE_QUEUE;
        case INTERRUPT:
          return SpeechController.QUEUE_MODE_INTERRUPT;
        case INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH:
          return SpeechController.QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH;
      }
      return SpeechController.QUEUE_MODE_INTERRUPT;
    }
  }

  void speak(CharSequence text, int delayMs, SpeakOptions speakOptions);

  default void speak(CharSequence text) {
    speak(text, /* delayMs= */ 0, buildSpeakOptions(AnnounceType.ENQUEUE, null));
  }

  default void speak(CharSequence text, AnnounceType announceType) {
    speak(text, /* delayMs= */ 0, buildSpeakOptions(announceType, null));
  }

  default void speak(CharSequence text, int delayMs) {
    speak(text, delayMs, buildSpeakOptions(AnnounceType.ENQUEUE, null));
  }

  default void speak(CharSequence text, int delayMs, AnnounceType announceType) {
    speak(text, delayMs, buildSpeakOptions(announceType, null));
  }

  default void speak(CharSequence text, UtteranceCompleteRunnable utteranceCompleteRunnable) {
    speak(
        text, /* delayMs= */ 0, buildSpeakOptions(AnnounceType.ENQUEUE, utteranceCompleteRunnable));
  }

  default void speak(
      CharSequence text, int delayMs, UtteranceCompleteRunnable utteranceCompleteRunnable) {
    speak(text, delayMs, buildSpeakOptions(AnnounceType.ENQUEUE, utteranceCompleteRunnable));
  }

  default void speak(
      CharSequence text,
      AnnounceType announceType,
      UtteranceCompleteRunnable utteranceCompleteRunnable) {
    speak(text, /* delayMs= */ 0, buildSpeakOptions(announceType, utteranceCompleteRunnable));
  }

  default void speak(
      CharSequence text,
      int delayMs,
      AnnounceType announceType,
      UtteranceCompleteRunnable utteranceCompleteRunnable) {
    speak(text, delayMs, buildSpeakOptions(announceType, utteranceCompleteRunnable));
  }

  default SpeakOptions buildSpeakOptions(
      AnnounceType announceType, UtteranceCompleteRunnable utteranceCompleteRunnable) {
    return SpeakOptions.create()
        .setQueueMode(TalkBackSpeaker.AnnounceType.getQueueMode(announceType))
        .setFlags(
            FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE)
        .setCompletedAction(utteranceCompleteRunnable);
  }
}
