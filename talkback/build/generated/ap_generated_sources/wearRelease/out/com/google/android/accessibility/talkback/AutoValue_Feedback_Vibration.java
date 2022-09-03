package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Vibration extends Feedback.Vibration {

  private final int resourceId;

  AutoValue_Feedback_Vibration(
      int resourceId) {
    this.resourceId = resourceId;
  }

  @Override
  public int resourceId() {
    return resourceId;
  }

  @Override
  public String toString() {
    return "Vibration{"
        + "resourceId=" + resourceId
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Vibration) {
      Feedback.Vibration that = (Feedback.Vibration) o;
      return this.resourceId == that.resourceId();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= resourceId;
    return h$;
  }

}
