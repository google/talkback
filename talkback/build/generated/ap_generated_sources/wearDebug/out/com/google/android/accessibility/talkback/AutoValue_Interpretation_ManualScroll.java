package com.google.android.accessibility.talkback;

import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_ManualScroll extends Interpretation.ManualScroll {

  private final int direction;

  private final @Nullable ScreenState screenState;

  AutoValue_Interpretation_ManualScroll(
      int direction,
      @Nullable ScreenState screenState) {
    this.direction = direction;
    this.screenState = screenState;
  }

  @TraversalStrategy.SearchDirection
  @Override
  public int direction() {
    return direction;
  }

  @Override
  public @Nullable ScreenState screenState() {
    return screenState;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.ManualScroll) {
      Interpretation.ManualScroll that = (Interpretation.ManualScroll) o;
      return this.direction == that.direction()
          && (this.screenState == null ? that.screenState() == null : this.screenState.equals(that.screenState()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= direction;
    h$ *= 1000003;
    h$ ^= (screenState == null) ? 0 : screenState.hashCode();
    return h$;
  }

}
