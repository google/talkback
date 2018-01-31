/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.tutorial.exercise;

import android.content.Context;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.apps.common.proguard.UsedByReflection;

/** Provides callbacks to manage Lesson 3 practice */
@UsedByReflection("tutorial.json")
public class Lesson3PracticeExercise extends ContextMenuExercise {

  private boolean mGlobalContextMenuOpened;

  @Override
  public void onAction(Context context, String action) {
    TalkBackService service = TalkBackService.getInstance();
    if (service == null) {
      return;
    }

    if (context.getString(R.string.shortcut_value_talkback_breakout).equals(action)) {
      mGlobalContextMenuOpened = true;
      updateImage();
    }
  }

  @Override
  public int getImageResource() {
    if (mGlobalContextMenuOpened) {
      return R.drawable.up_right_arrow;
    }
    return R.drawable.down_right_arrow;
  }

  @Override
  public CharSequence getContentDescription(Context context) {
    return context.getString(R.string.tutorial_lesson_3_practice_content_description);
  }

  @Override
  public void onMenuCancelButtonClicked() {
    //NoOp
  }

  @Override
  public int getEventTypes() {
    return 0;
  }
}
