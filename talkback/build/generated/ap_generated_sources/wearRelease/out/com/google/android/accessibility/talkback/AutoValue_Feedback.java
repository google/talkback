package com.google.android.accessibility.talkback;

import com.google.android.accessibility.utils.Performance;
import com.google.common.collect.ImmutableList;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback extends Feedback {

  private final Performance.@Nullable EventId eventId;

  private final ImmutableList<Feedback.Part> failovers;

  AutoValue_Feedback(
      Performance.@Nullable EventId eventId,
      ImmutableList<Feedback.Part> failovers) {
    this.eventId = eventId;
    if (failovers == null) {
      throw new NullPointerException("Null failovers");
    }
    this.failovers = failovers;
  }

  @Override
  public Performance.@Nullable EventId eventId() {
    return eventId;
  }

  @Override
  public ImmutableList<Feedback.Part> failovers() {
    return failovers;
  }

  @Override
  public String toString() {
    return "Feedback{"
        + "eventId=" + eventId + ", "
        + "failovers=" + failovers
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback) {
      Feedback that = (Feedback) o;
      return (this.eventId == null ? that.eventId() == null : this.eventId.equals(that.eventId()))
          && this.failovers.equals(that.failovers());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= (eventId == null) ? 0 : eventId.hashCode();
    h$ *= 1000003;
    h$ ^= failovers.hashCode();
    return h$;
  }

}
