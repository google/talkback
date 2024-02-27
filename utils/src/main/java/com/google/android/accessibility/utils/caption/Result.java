package com.google.android.accessibility.utils.caption;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType;
import com.google.auto.value.AutoValue;
import java.util.Objects;

/** Result for the image caption request. */
@AutoValue
public abstract class Result {
  public static Result create(CaptionType type, @Nullable CharSequence result, float confidence) {
    return new AutoValue_Result(type, result, confidence);
  }

  public static Result create(CaptionType type, @Nullable CharSequence result) {
    return new AutoValue_Result(type, result, /* confidence= */ 1.0f);
  }

  public abstract CaptionType type();

  @Nullable
  public abstract CharSequence text();

  public abstract float confidence();

  public boolean isResultEmpty() {
    return TextUtils.isEmpty(text());
  }

  public Result copy() {
    return new AutoValue_Result(type(), text(), confidence());
  }

  /** Whether the {@code result} is null or the text is empty. */
  public static boolean isEmpty(Result result) {
    return result == null || result.isResultEmpty();
  }

  @NonNull
  @Override
  public final String toString() {
    return "type: " + type() + ", " + "result: " + text() + ", " + "confidence: " + confidence();
  }

  @Override
  public final boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Result)) {
      return false;
    }
    Result that = (Result) o;
    return this.type() == that.type()
        && TextUtils.equals(this.text(), that.text())
        && this.confidence() == that.confidence();
  }

  @Override
  public final int hashCode() {
    return Objects.hash(type(), text(), confidence());
  }
}
