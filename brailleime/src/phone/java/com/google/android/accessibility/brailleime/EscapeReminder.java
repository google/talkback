/*
 * Copyright 2020 Google Inc.
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

import android.content.Context;
import android.os.Handler;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.utils.output.SpeechController;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tell user how to escape braille keyboard. The reminder will announce when no any braille
 * characters are produced or any legitimate gestures occur in 15 seconds. Also, the reminder
 * happens only in following cases: 1. option dialog shows less than 3 times. 2. escape keyboard
 * less than 5 times. After the first reminder announces, the second reminder will happen 30 seconds
 * later. We announce at most three times.
 */
public class EscapeReminder {
  interface Callback {
    void onRemind(SpeechController.UtteranceCompleteRunnable utteranceCompleteRunnable);

    boolean shouldAnnounce();
  }

  @VisibleForTesting static final int IDLE_THRESHOLD_DURATION_MS = 15000;
  private static final int MAX_REMINDER_COUNT = 3;
  private static final int MAX_SHOW_OPTION_DIALOG_COUNT = 3;
  private static final int MAX_EXIT_KEYBOARD_COUNT = 5;
  private int reminderCount;
  private final Handler handler;
  private int optionsDialogCounter;
  private int exitKeyboardCounter;
  private final Callback callback;
  private final Context context;
  private final AtomicBoolean finishSpeaking = new AtomicBoolean(true);

  @VisibleForTesting
  SpeechController.UtteranceCompleteRunnable utteranceCompleteRunnable =
      new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
          finishSpeaking.set(true);
          // The announce thrown before keyboard turns off will still play, don't start timer here
          // so it won't repeat.
          if (callback.shouldAnnounce()) {
            startTimer();
          }
        }
      };

  private final Runnable leaveReminder =
      new Runnable() {
        @Override
        public void run() {
          finishSpeaking.set(false);
          callback.onRemind(utteranceCompleteRunnable);
          reminderCount++;
        }
      };

  public EscapeReminder(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
    this.handler = new Handler();
    this.optionsDialogCounter = UserPreferences.readShowOptionDialogCount(context);
    this.exitKeyboardCounter = UserPreferences.readExitKeyboardCount(context);
  }

  /** Starts countdown. */
  public void startTimer() {
    // There is a condition that startTimer() is called between the announce task sent to TalkBack
    // but it hasn't been spoken. Introduce finishSpeaking to stop timer starting here.
    if (!handler.hasCallbacks(leaveReminder) && finishSpeaking.get()) {
      if (shouldAnnounceReminder()) {
        handler.postDelayed(leaveReminder, IDLE_THRESHOLD_DURATION_MS * reminderCount);
      }
    }
  }

  /** Cancels countdown. */
  public void cancelTimer() {
    handler.removeCallbacksAndMessages(null);
  }

  /** Restarts countdown. */
  public void restartTimer() {
    cancelTimer();
    startTimer();
  }

  /** Increase show option dialog counter to decide whether to announce reminder. */
  public void increaseOptionDialogCounter() {
    optionsDialogCounter++;
    if (optionsDialogCounter <= MAX_SHOW_OPTION_DIALOG_COUNT) {
      UserPreferences.writeShowOptionDialogCount(context, optionsDialogCounter);
    }
  }

  /** Increase exit keyboard counter to decide whether to announce reminder. */
  public void increaseExitKeyboardCounter() {
    exitKeyboardCounter++;
    if (optionsDialogCounter <= MAX_EXIT_KEYBOARD_COUNT) {
      UserPreferences.writeExitKeyboardCount(context, exitKeyboardCounter);
    }
  }

  /**
   * Announce every time keyboard is first displayed; otherwise, restrict the announcement to when
   * show context menu counts and exit keyboard counts exceeds constraints.
   */
  private boolean shouldAnnounceReminder() {
    return reminderCount == 0
        || (reminderCount < MAX_REMINDER_COUNT
            && (optionsDialogCounter < MAX_SHOW_OPTION_DIALOG_COUNT
                || exitKeyboardCounter < MAX_EXIT_KEYBOARD_COUNT));
  }
}
