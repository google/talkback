package com.google.android.accessibility.utils;

import android.text.style.ClickableSpan;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_AccessibilityNodeInfoUtils_ClickableString extends AccessibilityNodeInfoUtils.ClickableString {

  private final String string;

  private final ClickableSpan clickableSpan;

  AutoValue_AccessibilityNodeInfoUtils_ClickableString(
      String string,
      ClickableSpan clickableSpan) {
    if (string == null) {
      throw new NullPointerException("Null string");
    }
    this.string = string;
    if (clickableSpan == null) {
      throw new NullPointerException("Null clickableSpan");
    }
    this.clickableSpan = clickableSpan;
  }

  @Override
  public String string() {
    return string;
  }

  @Override
  public ClickableSpan clickableSpan() {
    return clickableSpan;
  }

  @Override
  public String toString() {
    return "ClickableString{"
        + "string=" + string + ", "
        + "clickableSpan=" + clickableSpan
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof AccessibilityNodeInfoUtils.ClickableString) {
      AccessibilityNodeInfoUtils.ClickableString that = (AccessibilityNodeInfoUtils.ClickableString) o;
      return this.string.equals(that.string())
          && this.clickableSpan.equals(that.clickableSpan());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= string.hashCode();
    h$ *= 1000003;
    h$ ^= clickableSpan.hashCode();
    return h$;
  }

}
