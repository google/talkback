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

package com.google.android.accessibility.braille.brailledisplay.controller;

import android.os.Handler;

/**
 * Timed message controller controls the show and hide of timeliness message event on the braille
 * display.
 */
public class TimedMessager {
  /** Callback for notifying the events. */
  public interface Callback {
    /** Callbacks when a timed message is displayed. */
    void onTimedMessageDisplayed(CellsContent content);

    /** Callbacks when timed messages are cleared. */
    void onTimedMessageCleared();
  }

  private final Handler handler;
  private final Callback callback;
  private boolean timedMessageDisplaying;

  public TimedMessager(Callback callback) {
    this.callback = callback;
    handler = new Handler();
  }

  /** Sets timed message with duration. */
  public void setTimedMessage(CellsContent content, int durationInMilliseconds) {
    timedMessageDisplaying = true;
    callback.onTimedMessageDisplayed(content);
    handler.removeCallbacksAndMessages(null);
    handler.postDelayed(callback::onTimedMessageCleared, durationInMilliseconds);
  }

  /** Clears displaying timed message. */
  public void clearTimedMessage() {
    timedMessageDisplaying = false;
    handler.removeCallbacksAndMessages(null);
    callback.onTimedMessageCleared();
  }

  /** Whether timed message is displaying. */
  public boolean isTimedMessageDisplaying() {
    return timedMessageDisplaying;
  }
}
