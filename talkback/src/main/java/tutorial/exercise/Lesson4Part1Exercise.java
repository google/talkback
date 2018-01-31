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
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.apps.common.proguard.UsedByReflection;

/** Provides callbacks to check for completion of Lesson 4, part 1 */
@UsedByReflection("tutorial.json")
public class Lesson4Part1Exercise extends TextExercise
    implements CursorController.GranularityChangeListener {

  @Override
  public void onInitialized(Context context) {
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      CursorController cursorController = service.getCursorController();
      cursorController.addGranularityListener(this);
    }
  }

  @Override
  public void clear() {
    TalkBackService service = TalkBackService.getInstance();
    if (service != null) {
      CursorController cursorController = service.getCursorController();
      cursorController.removeGranularityListener(this);
    }
  }

  @Override
  public CharSequence getText(Context context) {
    return context.getString(R.string.tutorial_lesson_4_sample_text);
  }

  @Override
  public void onAction(Context context, String action) {
    TalkBackService service = TalkBackService.getInstance();
    if (service == null) {
      return;
    }

    if (context.getString(R.string.shortcut_value_local_breakout).equals(action)) {
      SpeechController speechController = service.getSpeechController();
      speechController.speak(
          context.getString(R.string.tutorial_lesson_4_page1_message),
          SpeechController.QUEUE_MODE_INTERRUPT,
          0,
          null,
          (EventId) null);
    }
  }

  @Override
  public void onGranularityChanged(CursorGranularity granularity) {}

  @Override
  public int getEventTypes() {
    return 0;
  }
}
