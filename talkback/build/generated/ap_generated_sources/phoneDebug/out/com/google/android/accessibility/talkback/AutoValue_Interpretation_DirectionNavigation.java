package com.google.android.accessibility.talkback;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_DirectionNavigation extends Interpretation.DirectionNavigation {

  private final int direction;

  private final @Nullable AccessibilityNodeInfoCompat destination;

  AutoValue_Interpretation_DirectionNavigation(
      int direction,
      @Nullable AccessibilityNodeInfoCompat destination) {
    this.direction = direction;
    this.destination = destination;
  }

  @TraversalStrategy.SearchDirection
  @Override
  public int direction() {
    return direction;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat destination() {
    return destination;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.DirectionNavigation) {
      Interpretation.DirectionNavigation that = (Interpretation.DirectionNavigation) o;
      return this.direction == that.direction()
          && (this.destination == null ? that.destination() == null : this.destination.equals(that.destination()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= direction;
    h$ *= 1000003;
    h$ ^= (destination == null) ? 0 : destination.hashCode();
    return h$;
  }

}
