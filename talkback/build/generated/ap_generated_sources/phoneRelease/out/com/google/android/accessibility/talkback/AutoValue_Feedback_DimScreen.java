package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_DimScreen extends Feedback.DimScreen {

  private final Feedback.DimScreen.Action action;

  AutoValue_Feedback_DimScreen(
      Feedback.DimScreen.Action action) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
  }

  @Override
  public Feedback.DimScreen.Action action() {
    return action;
  }

  @Override
  public String toString() {
    return "DimScreen{"
        + "action=" + action
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.DimScreen) {
      Feedback.DimScreen that = (Feedback.DimScreen) o;
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
