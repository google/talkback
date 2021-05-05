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

package com.google.android.accessibility.brailleime;

import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;

/** Exposes some TalkBack behavior to BrailleIme classes. */
public interface TalkBackForBrailleImeInternal {

  void speak(
      CharSequence text,
      int delayMs,
      int queueMode,
      UtteranceCompleteRunnable utteranceCompleteRunnable);

  default void speakInterrupt(CharSequence text, int delayMs) {
    speak(text, delayMs, SpeechController.QUEUE_MODE_INTERRUPT, null);
  }

  default void speakInterrupt(CharSequence text) {
    speak(text, /* delayMs= */ 0, SpeechController.QUEUE_MODE_INTERRUPT, null);
  }

  default void speakInterrupt(
      CharSequence text, int delayMs, UtteranceCompleteRunnable utteranceCompleteRunnable) {
    speak(text, delayMs, SpeechController.QUEUE_MODE_INTERRUPT, utteranceCompleteRunnable);
  }

  default void speakEnqueue(CharSequence text, int delayMs) {
    speak(text, delayMs, SpeechController.QUEUE_MODE_QUEUE, null);
  }

  default void speakEnqueue(CharSequence text) {
    speak(text, /* delayMs= */ 0, SpeechController.QUEUE_MODE_QUEUE, null);
  }

  default void speakEnqueue(
      CharSequence text, int delayMs, UtteranceCompleteRunnable utteranceCompleteRunnable) {
    speak(text, delayMs, SpeechController.QUEUE_MODE_QUEUE, utteranceCompleteRunnable);
  }
}
