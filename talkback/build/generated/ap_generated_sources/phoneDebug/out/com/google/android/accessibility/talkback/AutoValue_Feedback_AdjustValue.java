package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_AdjustValue extends Feedback.AdjustValue {

  private final Feedback.AdjustValue.Action action;

  AutoValue_Feedback_AdjustValue(
      Feedback.AdjustValue.Action action) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
  }

  @Override
  public Feedback.AdjustValue.Action action() {
    return action;
  }

  @Override
  public String toString() {
    return "AdjustValue{"
        + "action=" + action
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.AdjustValue) {
      Feedback.AdjustValue that = (Feedback.AdjustValue) o;
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
