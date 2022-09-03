package com.google.android.accessibility.talkback;

import android.graphics.Rect;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_UiChange extends Feedback.UiChange {

  private final Feedback.UiChange.Action action;

  private final @Nullable Rect sourceBoundsInScreen;

  AutoValue_Feedback_UiChange(
      Feedback.UiChange.Action action,
      @Nullable Rect sourceBoundsInScreen) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    this.sourceBoundsInScreen = sourceBoundsInScreen;
  }

  @Override
  public Feedback.UiChange.Action action() {
    return action;
  }

  @Override
  public @Nullable Rect sourceBoundsInScreen() {
    return sourceBoundsInScreen;
  }

  @Override
  public String toString() {
    return "UiChange{"
        + "action=" + action + ", "
        + "sourceBoundsInScreen=" + sourceBoundsInScreen
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.UiChange) {
      Feedback.UiChange that = (Feedback.UiChange) o;
      return this.action.equals(that.action())
          && (this.sourceBoundsInScreen == null ? that.sourceBoundsInScreen() == null : this.sourceBoundsInScreen.equals(that.sourceBoundsInScreen()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (sourceBoundsInScreen == null) ? 0 : sourceBoundsInScreen.hashCode();
    return h$;
  }

}
