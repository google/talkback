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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/**
 * A button that is invisble to Switch Access. Used for the menu button that opens the global
 * context menu.
 */
public class ButtonSwitchAccessIgnores extends Button {

  public ButtonSwitchAccessIgnores(Context context) {
    this(context, null);
  }

  public ButtonSwitchAccessIgnores(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ButtonSwitchAccessIgnores(Context context, AttributeSet attrs, int defStyleRes) {
    this(context, attrs, defStyleRes, 0);
  }

  public ButtonSwitchAccessIgnores(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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
