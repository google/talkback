/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.accessibility.talkback.eventprocessor;

import static com.google.android.accessibility.talkback.Feedback.GESTURE_VIBRATION;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;

/**
 * Produces continuous vibration feedback during framework gesture recognition. This class currently
 * is a mix of event-interpreter and feedback-mapper.
 */
public class ProcessorGestureVibrator implements AccessibilityEventListener {
  /** Delay after a gesture starts before feedback begins. */
  private static final int FEEDBACK_DELAY = 70;

  /** Event types that are handled by ProcessorGestureVibrator. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_GESTURE_VIBRATOR =
      AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START
          | AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END;

  /** Callback to return generated feedback to pipeline. */
  private final Pipeline.FeedbackReturner feedbackReturner;

  public ProcessorGestureVibrator(Pipeline.FeedbackReturner feedbackReturner) {
    this.feedbackReturner = feedbackReturner;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_GESTURE_VIBRATOR;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START:
        {
          feedbackReturner.returnFeedback(
              eventId,
              Feedback.interrupt(GESTURE_VIBRATION, /* level= */ 1)
                  .setDelayMs(FEEDBACK_DELAY)
                  .vibration(R.array.gesture_detection_repeated_pattern)
                  .sound(R.raw.gesture_begin));
        break;
        }
      case AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END:
        {
          feedbackReturner.returnFeedback(
              eventId,
              Feedback.interrupt(GESTURE_VIBRATION, /* level= */ 1)
                  .setInterruptSoundAndVibration(true));
        break;
        }
      default: // fall out
    }
  }

}
