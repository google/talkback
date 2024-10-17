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

package com.google.android.accessibility.braille.brailledisplay.controller;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/** Triggers its callback at a fixed frequency. */
class Pulser {

  interface Callback {
    void pulse();
  }

  private final Callback callback;
  private int frequencyMillis;

  private final Handler handler = new MainHandler();

  public Pulser(Callback callback, int frequencyMillis) {
    super();
    this.callback = callback;
    this.frequencyMillis = frequencyMillis;
  }

  public void schedulePulse() {
    if (!handler.hasMessages(0)) {
      handler.sendEmptyMessageDelayed(0, frequencyMillis);
    }
  }

  public void cancelPulse() {
    handler.removeMessages(0);
  }

  /** Sets {@code frequencyMillis} for triggering callback. */
  public void setFrequencyMillis(int frequencyMillis) {
    this.frequencyMillis = frequencyMillis;
  }

  @SuppressLint("HandlerLeak")
  private class MainHandler extends Handler {

    public MainHandler() {
      super(Looper.getMainLooper());
    }

    @Override
    public void handleMessage(Message msg) {
      callback.pulse();
    }
  }
}
