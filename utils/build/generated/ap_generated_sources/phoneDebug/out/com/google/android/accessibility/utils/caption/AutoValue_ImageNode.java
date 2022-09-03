package com.google.android.accessibility.utils.caption;

import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_ImageNode extends ImageNode {

  private final AccessibilityNodeInfoUtils.ViewResourceName viewResourceName;

  AutoValue_ImageNode(
      AccessibilityNodeInfoUtils.ViewResourceName viewResourceName) {
    if (viewResourceName == null) {
      throw new NullPointerException("Null viewResourceName");
    }
    this.viewResourceName = viewResourceName;
  }

  @Override
  public AccessibilityNodeInfoUtils.ViewResourceName viewResourceName() {
    return viewResourceName;
  }

}
