package com.google.android.accessibility.talkback;

import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_ShowToast extends Feedback.ShowToast {

  private final Feedback.ShowToast.Action action;

  private final @Nullable CharSequence message;

  private final boolean durationIsLong;

  AutoValue_Feedback_ShowToast(
      Feedback.ShowToast.Action action,
      @Nullable CharSequence message,
      boolean durationIsLong) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    this.message = message;
    this.durationIsLong = durationIsLong;
  }

  @Override
  public Feedback.ShowToast.Action action() {
    return action;
  }

  @Override
  public @Nullable CharSequence message() {
    return message;
  }

  @Override
  public boolean durationIsLong() {
    return durationIsLong;
  }

  @Override
  public String toString() {
    return "ShowToast{"
        + "action=" + action + ", "
        + "message=" + message + ", "
        + "durationIsLong=" + durationIsLong
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.ShowToast) {
      Feedback.ShowToast that = (Feedback.ShowToast) o;
      return this.action.equals(that.action())
          && (this.message == null ? that.message() == null : this.message.equals(that.message()))
          && this.durationIsLong == that.durationIsLong();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (message == null) ? 0 : message.hashCode();
    h$ *= 1000003;
    h$ ^= durationIsLong ? 1231 : 1237;
    return h$;
  }

}
