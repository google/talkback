package com.google.android.accessibility.talkback;

import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_KeyCombo extends Interpretation.KeyCombo {

  private final int id;

  private final @Nullable String text;

  AutoValue_Interpretation_KeyCombo(
      int id,
      @Nullable String text) {
    this.id = id;
    this.text = text;
  }

  @Override
  public int id() {
    return id;
  }

  @Override
  public @Nullable String text() {
    return text;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.KeyCombo) {
      Interpretation.KeyCombo that = (Interpretation.KeyCombo) o;
      return this.id == that.id()
          && (this.text == null ? that.text() == null : this.text.equals(that.text()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= id;
    h$ *= 1000003;
    h$ ^= (text == null) ? 0 : text.hashCode();
    return h$;
  }

}
