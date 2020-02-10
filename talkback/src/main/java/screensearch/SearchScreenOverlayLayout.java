/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.screensearch;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Helper class of {@link SearchScreenOverlay}, provide callback for handling key events. This is
 * only useful for hardware keyboards.
 */
public final class SearchScreenOverlayLayout extends LinearLayout {
  int overlayId;
  @Nullable private OnKeyListener keyListener;

  public SearchScreenOverlayLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    keyListener = null;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if ((keyListener != null) && keyListener.onKey(this, event.getKeyCode(), event)) {
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    overlayId = info.getWindowId();
    super.onInitializeAccessibilityNodeInfo(info);
  }

  @Override
  public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
    overlayId = event.getWindowId();
    return super.requestSendAccessibilityEvent(child, event);
  }

  /**
   * Sets the key listener.
   *
   * @param keyListener
   */
  public void setOnKeyListener(OnKeyListener keyListener) {
    this.keyListener = keyListener;
  }

  int getOverlayId() {
    return overlayId;
  }

  /** Checks if screen search has accessibility focus. */
  boolean hasAccessibilityFocus() {
    return isSelfOrChildHasAccessibilityFocus(this, new HashSet<>());
  }

  /** Checks if {@code target} or its child views has accessibility focus or not. */
  private boolean isSelfOrChildHasAccessibilityFocus(View target, Set<View> visited) {
    if (visited.contains(target)) {
      return false;
    }

    visited.add(target);

    if (target == null) {
      return false;
    }

    if (target.isAccessibilityFocused()) {
      return true;
    }

    if (!(target instanceof ViewGroup)) {
      return false;
    }

    ViewGroup viewGroup = (ViewGroup) target;
    int childCount = viewGroup.getChildCount();
    for (int i = 0; i < childCount; i++) {
      if (isSelfOrChildHasAccessibilityFocus(viewGroup.getChildAt(i), visited)) {
        return true;
      }
    }

    return false;
  }
}
