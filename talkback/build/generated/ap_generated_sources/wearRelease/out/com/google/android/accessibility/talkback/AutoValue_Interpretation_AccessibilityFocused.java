package com.google.android.accessibility.talkback;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_AccessibilityFocused extends Interpretation.AccessibilityFocused {

  private final boolean needsCaption;

  AutoValue_Interpretation_AccessibilityFocused(
      boolean needsCaption) {
    this.needsCaption = needsCaption;
  }

  @Override
  public boolean needsCaption() {
    return needsCaption;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.AccessibilityFocused) {
      Interpretation.AccessibilityFocused that = (Interpretation.AccessibilityFocused) o;
      return this.needsCaption == that.needsCaption();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= needsCaption ? 1231 : 1237;
    return h$;
  }

}
