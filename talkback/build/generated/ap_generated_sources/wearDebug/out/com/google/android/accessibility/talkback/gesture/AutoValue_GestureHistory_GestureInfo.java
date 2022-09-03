package com.google.android.accessibility.talkback.gesture;

import android.view.MotionEvent;
import java.util.List;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_GestureHistory_GestureInfo extends GestureHistory.GestureInfo {

  private final int id;

  private final List<MotionEvent> motionEvents;

  AutoValue_GestureHistory_GestureInfo(
      int id,
      List<MotionEvent> motionEvents) {
    this.id = id;
    if (motionEvents == null) {
      throw new NullPointerException("Null motionEvents");
    }
    this.motionEvents = motionEvents;
  }

  @Override
  public int id() {
    return id;
  }

  @Override
  public List<MotionEvent> motionEvents() {
    return motionEvents;
  }

  @Override
  public String toString() {
    return "GestureInfo{"
        + "id=" + id + ", "
        + "motionEvents=" + motionEvents
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof GestureHistory.GestureInfo) {
      GestureHistory.GestureInfo that = (GestureHistory.GestureInfo) o;
      return this.id == that.id()
          && this.motionEvents.equals(that.motionEvents());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= id;
    h$ *= 1000003;
    h$ ^= motionEvents.hashCode();
    return h$;
  }

}
