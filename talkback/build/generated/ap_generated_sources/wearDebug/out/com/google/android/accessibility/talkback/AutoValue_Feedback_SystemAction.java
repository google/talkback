package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_SystemAction extends Feedback.SystemAction {

  private final int systemActionId;

  AutoValue_Feedback_SystemAction(
      int systemActionId) {
    this.systemActionId = systemActionId;
  }

  @Override
  public int systemActionId() {
    return systemActionId;
  }

  @Override
  public String toString() {
    return "SystemAction{"
        + "systemActionId=" + systemActionId
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.SystemAction) {
      Feedback.SystemAction that = (Feedback.SystemAction) o;
      return this.systemActionId == that.systemActionId();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= systemActionId;
    return h$;
  }

}
