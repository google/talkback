package com.google.android.accessibility.talkback;

import java.util.Locale;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Language extends Feedback.Language {

  private final Feedback.Language.Action action;

  private final @Nullable Locale currentLanguage;

  AutoValue_Feedback_Language(
      Feedback.Language.Action action,
      @Nullable Locale currentLanguage) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    this.currentLanguage = currentLanguage;
  }

  @Override
  public Feedback.Language.Action action() {
    return action;
  }

  @Override
  public @Nullable Locale currentLanguage() {
    return currentLanguage;
  }

  @Override
  public String toString() {
    return "Language{"
        + "action=" + action + ", "
        + "currentLanguage=" + currentLanguage
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Language) {
      Feedback.Language that = (Feedback.Language) o;
      return this.action.equals(that.action())
          && (this.currentLanguage == null ? that.currentLanguage() == null : this.currentLanguage.equals(that.currentLanguage()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (currentLanguage == null) ? 0 : currentLanguage.hashCode();
    return h$;
  }

}
