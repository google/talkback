package com.google.android.accessibility.talkback;

import android.accessibilityservice.AccessibilityGestureEvent;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Gesture extends Feedback.Gesture {

  private final Feedback.Gesture.Action action;

  private final @Nullable AccessibilityGestureEvent currentGesture;

  AutoValue_Feedback_Gesture(
      Feedback.Gesture.Action action,
      @Nullable AccessibilityGestureEvent currentGesture) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    this.currentGesture = currentGesture;
  }

  @Override
  public Feedback.Gesture.Action action() {
    return action;
  }

  @Override
  public @Nullable AccessibilityGestureEvent currentGesture() {
    return currentGesture;
  }

  @Override
  public String toString() {
    return "Gesture{"
        + "action=" + action + ", "
        + "currentGesture=" + currentGesture
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Gesture) {
      Feedback.Gesture that = (Feedback.Gesture) o;
      return this.action.equals(that.action())
          && (this.currentGesture == null ? that.currentGesture() == null : this.currentGesture.equals(that.currentGesture()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (currentGesture == null) ? 0 : currentGesture.hashCode();
    return h$;
  }

}
