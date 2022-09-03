package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_VoiceRecognition extends Feedback.VoiceRecognition {

  private final Feedback.VoiceRecognition.Action action;

  private final boolean checkDialog;

  AutoValue_Feedback_VoiceRecognition(
      Feedback.VoiceRecognition.Action action,
      boolean checkDialog) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    this.checkDialog = checkDialog;
  }

  @Override
  public Feedback.VoiceRecognition.Action action() {
    return action;
  }

  @Override
  public boolean checkDialog() {
    return checkDialog;
  }

  @Override
  public String toString() {
    return "VoiceRecognition{"
        + "action=" + action + ", "
        + "checkDialog=" + checkDialog
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.VoiceRecognition) {
      Feedback.VoiceRecognition that = (Feedback.VoiceRecognition) o;
      return this.action.equals(that.action())
          && this.checkDialog == that.checkDialog();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= checkDialog ? 1231 : 1237;
    return h$;
  }

}
