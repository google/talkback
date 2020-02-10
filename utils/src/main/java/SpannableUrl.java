package com.google.android.accessibility.utils;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;

/**
 * Represents a URL from a {@link android.text.SpannableString} with a URL path and its associated
 * text from the {@link android.text.SpannableString}. This implements {@link Parcelable} in order
 * to send a list of SpannableUrls via an Intent.
 */
@AutoValue
public abstract class SpannableUrl implements Parcelable {

  public static final Parcelable.Creator<SpannableUrl> CREATOR =
      new Parcelable.Creator<SpannableUrl>() {
        @Override
        public SpannableUrl createFromParcel(Parcel in) {
          return create(in.readString() /* text */, in.readString() /* path */);
        }

        @Override
        public SpannableUrl[] newArray(int size) {
          return new SpannableUrl[size];
        }
      };

  public static SpannableUrl create(@NonNull String text, @NonNull String path) {
    return new AutoValue_SpannableUrl(text, path);
  }

  /**
   * The text from the {@link android.text.SpannableString} associated with this SpannableUrl. For
   * example, if a spannable string "Click here" has a {@link android.text.style.URLSpan} on the
   * text "here," the text value would be "here." It is possible for this to be the same value as
   * the URL path.
   */
  public abstract String text();

  /** A URL path from a {@link android.text.style.URLSpan}. */
  public abstract String path();

  /** Returns {@code true} if the URL text and path are the same. */
  public boolean isTextAndPathEquivalent() {
    return path().equals(text());
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(text());
    dest.writeString(path());
  }
}
