package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_TriggerIntent extends Feedback.TriggerIntent {

  private final Feedback.TriggerIntent.Action action;

  AutoValue_Feedback_TriggerIntent(
      Feedback.TriggerIntent.Action action) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
  }

  @Override
  public Feedback.TriggerIntent.Action action() {
    return action;
  }

  @Override
  public String toString() {
    return "TriggerIntent{"
        + "action=" + action
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.TriggerIntent) {
      Feedback.TriggerIntent that = (Feedback.TriggerIntent) o;
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
