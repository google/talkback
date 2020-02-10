/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.switchaccess.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Menu button for "Cancel." As this button needs special handling during scanning, any view with
 * this class name will be ignored.
 */
public class SwitchAccessCancelMenuButton extends SmallMenuButton {

  public SwitchAccessCancelMenuButton(Context context) {
    this(context, null);
  }

  public SwitchAccessCancelMenuButton(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SwitchAccessCancelMenuButton(
      Context context, @Nullable AttributeSet attrs, int defStyleRes) {
    this(context, attrs, defStyleRes, 0);
  }

  // This constructor needs to be public because it is for an inflated view.
  @SuppressWarnings("WeakerAccess")
  public SwitchAccessCancelMenuButton(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return ButtonSwitchAccessIgnores.class.getName();
  }

  @Override
  public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
    super.onInitializeAccessibilityEvent(event);
    event.setClassName(ButtonSwitchAccessIgnores.class.getName());
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(ButtonSwitchAccessIgnores.class.getName());
  }
}
