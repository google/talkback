package com.google.android.accessibility.utils;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_AccessibilityNodeInfoUtils_ViewResourceName extends AccessibilityNodeInfoUtils.ViewResourceName {

  private final String packageName;

  private final String viewIdName;

  AutoValue_AccessibilityNodeInfoUtils_ViewResourceName(
      String packageName,
      String viewIdName) {
    if (packageName == null) {
      throw new NullPointerException("Null packageName");
    }
    this.packageName = packageName;
    if (viewIdName == null) {
      throw new NullPointerException("Null viewIdName");
    }
    this.viewIdName = viewIdName;
  }

  @Override
  public String packageName() {
    return packageName;
  }

  @Override
  public String viewIdName() {
    return viewIdName;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof AccessibilityNodeInfoUtils.ViewResourceName) {
      AccessibilityNodeInfoUtils.ViewResourceName that = (AccessibilityNodeInfoUtils.ViewResourceName) o;
      return this.packageName.equals(that.packageName())
          && this.viewIdName.equals(that.viewIdName());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= packageName.hashCode();
    h$ *= 1000003;
    h$ ^= viewIdName.hashCode();
    return h$;
  }

}
