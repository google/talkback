/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.talkback.selector;

import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_TYPO;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.selector.SelectorController.ContextualSetting;
import com.google.android.accessibility.talkback.selector.SelectorController.Setting;

/** Contextual setting for adjustable widgets, like a TimePicker or SeekBar. */
public class TypoGranularity implements ContextualSetting {
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  public TypoGranularity(AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  @Override
  public Setting getSetting() {
    return GRANULARITY_TYPO;
  }

  @Override
  public boolean isNodeSupportSetting(Context context, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      // When using braille keyboard, accessibility focus is null so use input focus.
      node = accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    }
    if (node == null) {
      return false;
    }
    // Always provide Spell Check for edit text so that user can make sure whether there's any typo.
    return (accessibilityFocusMonitor.getNodeForEditingActions(node) != null);
  }

  @Override
  public boolean shouldActivateSetting(Context context, AccessibilityNodeInfoCompat node) {
    return false;
  }
}
