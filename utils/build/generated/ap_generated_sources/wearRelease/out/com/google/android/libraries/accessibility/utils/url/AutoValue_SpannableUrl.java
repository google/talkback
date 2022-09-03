package com.google.android.libraries.accessibility.utils.url;

import android.text.style.URLSpan;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_SpannableUrl extends SpannableUrl {

  private final URLSpan urlSpan;

  private final String text;

  AutoValue_SpannableUrl(
      URLSpan urlSpan,
      String text) {
    if (urlSpan == null) {
      throw new NullPointerException("Null urlSpan");
    }
    this.urlSpan = urlSpan;
    if (text == null) {
      throw new NullPointerException("Null text");
    }
    this.text = text;
  }

  @Override
  public URLSpan urlSpan() {
    return urlSpan;
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public String toString() {
    return "SpannableUrl{"
        + "urlSpan=" + urlSpan + ", "
        + "text=" + text
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SpannableUrl) {
      SpannableUrl that = (SpannableUrl) o;
      return this.urlSpan.equals(that.urlSpan())
          && this.text.equals(that.text());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= urlSpan.hashCode();
    h$ *= 1000003;
    h$ ^= text.hashCode();
    return h$;
  }

}
