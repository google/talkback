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
