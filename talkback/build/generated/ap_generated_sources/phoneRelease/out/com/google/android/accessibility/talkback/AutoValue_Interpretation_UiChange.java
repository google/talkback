package com.google.android.accessibility.talkback;

import android.graphics.Rect;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_UiChange extends Interpretation.UiChange {

  private final @Nullable Rect sourceBoundsInScreen;

  private final Interpretation.UiChange.UiChangeType uiChangeType;

  AutoValue_Interpretation_UiChange(
      @Nullable Rect sourceBoundsInScreen,
      Interpretation.UiChange.UiChangeType uiChangeType) {
    this.sourceBoundsInScreen = sourceBoundsInScreen;
    if (uiChangeType == null) {
      throw new NullPointerException("Null uiChangeType");
    }
    this.uiChangeType = uiChangeType;
  }

  @Override
  public @Nullable Rect sourceBoundsInScreen() {
    return sourceBoundsInScreen;
  }

  @Override
  public Interpretation.UiChange.UiChangeType uiChangeType() {
    return uiChangeType;
  }

  @Override
  public String toString() {
    return "UiChange{"
        + "sourceBoundsInScreen=" + sourceBoundsInScreen + ", "
        + "uiChangeType=" + uiChangeType
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.UiChange) {
      Interpretation.UiChange that = (Interpretation.UiChange) o;
      return (this.sourceBoundsInScreen == null ? that.sourceBoundsInScreen() == null : this.sourceBoundsInScreen.equals(that.sourceBoundsInScreen()))
          && this.uiChangeType.equals(that.uiChangeType());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= (sourceBoundsInScreen == null) ? 0 : sourceBoundsInScreen.hashCode();
    h$ *= 1000003;
    h$ ^= uiChangeType.hashCode();
    return h$;
  }

}
