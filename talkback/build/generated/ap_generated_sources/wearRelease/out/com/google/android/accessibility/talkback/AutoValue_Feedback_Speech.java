package com.google.android.accessibility.talkback;

import com.google.android.accessibility.utils.output.SpeechController;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Speech extends Feedback.Speech {

  private final Feedback.Speech.Action action;

  private final @Nullable CharSequence text;

  private final SpeechController.@Nullable SpeakOptions options;

  private final @Nullable CharSequence hint;

  private final SpeechController.@Nullable SpeakOptions hintSpeakOptions;

  private AutoValue_Feedback_Speech(
      Feedback.Speech.Action action,
      @Nullable CharSequence text,
      SpeechController.@Nullable SpeakOptions options,
      @Nullable CharSequence hint,
      SpeechController.@Nullable SpeakOptions hintSpeakOptions) {
    this.action = action;
    this.text = text;
    this.options = options;
    this.hint = hint;
    this.hintSpeakOptions = hintSpeakOptions;
  }

  @Override
  public Feedback.Speech.Action action() {
    return action;
  }

  @Override
  public @Nullable CharSequence text() {
    return text;
  }

  @Override
  public SpeechController.@Nullable SpeakOptions options() {
    return options;
  }

  @Override
  public @Nullable CharSequence hint() {
    return hint;
  }

  @Override
  public SpeechController.@Nullable SpeakOptions hintSpeakOptions() {
    return hintSpeakOptions;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Speech) {
      Feedback.Speech that = (Feedback.Speech) o;
      return this.action.equals(that.action())
          && (this.text == null ? that.text() == null : this.text.equals(that.text()))
          && (this.options == null ? that.options() == null : this.options.equals(that.options()))
          && (this.hint == null ? that.hint() == null : this.hint.equals(that.hint()))
          && (this.hintSpeakOptions == null ? that.hintSpeakOptions() == null : this.hintSpeakOptions.equals(that.hintSpeakOptions()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (text == null) ? 0 : text.hashCode();
    h$ *= 1000003;
    h$ ^= (options == null) ? 0 : options.hashCode();
    h$ *= 1000003;
    h$ ^= (hint == null) ? 0 : hint.hashCode();
    h$ *= 1000003;
    h$ ^= (hintSpeakOptions == null) ? 0 : hintSpeakOptions.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.Speech.Builder {
    private Feedback.Speech.Action action;
    private @Nullable CharSequence text;
    private SpeechController.@Nullable SpeakOptions options;
    private @Nullable CharSequence hint;
    private SpeechController.@Nullable SpeakOptions hintSpeakOptions;
    Builder() {
    }
    @Override
    public Feedback.Speech.Builder setAction(Feedback.Speech.Action action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    public Feedback.Speech.Builder setText(@Nullable CharSequence text) {
      this.text = text;
      return this;
    }
    @Override
    public Feedback.Speech.Builder setOptions(SpeechController.@Nullable SpeakOptions options) {
      this.options = options;
      return this;
    }
    @Override
    public Feedback.Speech.Builder setHint(@Nullable CharSequence hint) {
      this.hint = hint;
      return this;
    }
    @Override
    public Feedback.Speech.Builder setHintSpeakOptions(SpeechController.@Nullable SpeakOptions hintSpeakOptions) {
      this.hintSpeakOptions = hintSpeakOptions;
      return this;
    }
    @Override
    public Feedback.Speech build() {
      if (this.action == null) {
        String missing = " action";
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_Speech(
          this.action,
          this.text,
          this.options,
          this.hint,
          this.hintSpeakOptions);
    }
  }

}
