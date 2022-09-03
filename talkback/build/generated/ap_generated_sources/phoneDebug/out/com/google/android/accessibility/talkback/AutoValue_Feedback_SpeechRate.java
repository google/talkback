package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_SpeechRate extends Feedback.SpeechRate {

  private final Feedback.SpeechRate.Action action;

  AutoValue_Feedback_SpeechRate(
      Feedback.SpeechRate.Action action) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
  }

  @Override
  public Feedback.SpeechRate.Action action() {
    return action;
  }

  @Override
  public String toString() {
    return "SpeechRate{"
        + "action=" + action
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.SpeechRate) {
      Feedback.SpeechRate that = (Feedback.SpeechRate) o;
      return this.action.equals(that.action());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    return h$;
  }

}
