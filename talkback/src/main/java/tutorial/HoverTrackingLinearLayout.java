/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback.tutorial;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

public class HoverTrackingLinearLayout extends LinearLayout {

  private boolean mDidHoverEnter;

  public HoverTrackingLinearLayout(Context context) {
    super(context);
  }

  public HoverTrackingLinearLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public HoverTrackingLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean onHoverEvent(MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
      mDidHoverEnter = true;
    }
    return super.onHoverEvent(event);
  }

  public void clearTracking() {
    mDidHoverEnter = false;
  }

  public boolean didHoverEnter() {
    return mDidHoverEnter;
  }
}
