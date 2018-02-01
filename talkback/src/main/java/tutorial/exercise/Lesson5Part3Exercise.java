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
import com.google.android.apps.common.proguard.UsedByReflection;

/** Provides callbacks to check for completion of Lesson 5, part 3 */
@UsedByReflection("tutorial.json")
public class Lesson5Part3Exercise extends EditTextExercise {

  @Override
  public CharSequence getText(Context context) {
    return context.getString(R.string.tutorial_lesson_5_page3_sample_text);
  }
}
