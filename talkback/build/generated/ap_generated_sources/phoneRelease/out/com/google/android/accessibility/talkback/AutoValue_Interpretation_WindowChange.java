package com.google.android.accessibility.talkback;

import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_WindowChange extends Interpretation.WindowChange {

  private final boolean forceRestoreFocus;

  private final @Nullable ScreenState screenState;

  AutoValue_Interpretation_WindowChange(
      boolean forceRestoreFocus,
      @Nullable ScreenState screenState) {
    this.forceRestoreFocus = forceRestoreFocus;
    this.screenState = screenState;
  }

  @Override
  public boolean forceRestoreFocus() {
    return forceRestoreFocus;
  }

  @Override
  public @Nullable ScreenState screenState() {
    return screenState;
  }

  @Override
  public String toString() {
    return "WindowChange{"
        + "forceRestoreFocus=" + forceRestoreFocus + ", "
        + "screenState=" + screenState
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.WindowChange) {
      Interpretation.WindowChange that = (Interpretation.WindowChange) o;
      return this.forceRestoreFocus == that.forceRestoreFocus()
          && (this.screenState == null ? that.screenState() == null : this.screenState.equals(that.screenState()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= forceRestoreFocus ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= (screenState == null) ? 0 : screenState.hashCode();
    return h$;
  }

}
