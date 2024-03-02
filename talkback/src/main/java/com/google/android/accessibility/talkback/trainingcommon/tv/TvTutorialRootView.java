/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.trainingcommon.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.google.android.accessibility.talkback.R;
import org.checkerframework.checker.nullness.qual.Nullable;

class TvTutorialRootView extends LinearLayout {
  public TvTutorialRootView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public View focusSearch(View focused, int direction) {
    if (direction == FOCUS_RIGHT && !(focused instanceof TvNavigationButton)) {
      try {
        // Return first button on the right.
        return ((ViewGroup) getRootView().findViewById(R.id.training_navigation)).getChildAt(0);
      } catch (NullPointerException ignored) {
        // May happen if the page is not properly initialized.
      }
    }
    return super.focusSearch(focused, direction);
  }
}
