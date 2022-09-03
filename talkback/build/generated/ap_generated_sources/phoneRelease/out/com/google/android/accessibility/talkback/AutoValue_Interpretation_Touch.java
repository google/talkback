package com.google.android.accessibility.talkback;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_Touch extends Interpretation.Touch {

  private final Interpretation.Touch.Action action;

  private final @Nullable AccessibilityNodeInfoCompat target;

  AutoValue_Interpretation_Touch(
      Interpretation.Touch.Action action,
      @Nullable AccessibilityNodeInfoCompat target) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    this.target = target;
  }

  @Override
  public Interpretation.Touch.Action action() {
    return action;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat target() {
    return target;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.Touch) {
      Interpretation.Touch that = (Interpretation.Touch) o;
      return this.action.equals(that.action())
          && (this.target == null ? that.target() == null : this.target.equals(that.target()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (target == null) ? 0 : target.hashCode();
    return h$;
  }

}
