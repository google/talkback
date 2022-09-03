package com.google.android.accessibility.talkback.interpreters;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_ManualScrollInterpreter_ManualScrollInterpretation extends ManualScrollInterpreter.ManualScrollInterpretation {

  private final Performance.@Nullable EventId eventId;

  private final AccessibilityEvent event;

  private final int direction;

  AutoValue_ManualScrollInterpreter_ManualScrollInterpretation(
      Performance.@Nullable EventId eventId,
      AccessibilityEvent event,
      int direction) {
    this.eventId = eventId;
    if (event == null) {
      throw new NullPointerException("Null event");
    }
    this.event = event;
    this.direction = direction;
  }

  @Override
  public Performance.@Nullable EventId eventId() {
    return eventId;
  }

  @Override
  public AccessibilityEvent event() {
    return event;
  }

  @TraversalStrategy.SearchDirection
  @Override
  public int direction() {
    return direction;
  }

  @Override
  public String toString() {
    return "ManualScrollInterpretation{"
        + "eventId=" + eventId + ", "
        + "event=" + event + ", "
        + "direction=" + direction
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ManualScrollInterpreter.ManualScrollInterpretation) {
      ManualScrollInterpreter.ManualScrollInterpretation that = (ManualScrollInterpreter.ManualScrollInterpretation) o;
      return (this.eventId == null ? that.eventId() == null : this.eventId.equals(that.eventId()))
          && this.event.equals(that.event())
          && this.direction == that.direction();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= (eventId == null) ? 0 : eventId.hashCode();
    h$ *= 1000003;
    h$ ^= event.hashCode();
    h$ *= 1000003;
    h$ ^= direction;
    return h$;
  }

}
