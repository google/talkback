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

package com.google.android.accessibility.talkback.tutorial;

import android.content.Context;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.JsonUtils;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

public class TutorialController {

  private Tutorial tutorial;

  public TutorialController(Context context) throws Exception {
    tutorial = createTutorial(context);
  }

  private Tutorial createTutorial(Context context) throws IOException, JSONException {
    JSONObject tutorialJson = JsonUtils.readFromRawFile(context, R.raw.tutorial);
    return new Tutorial(context, tutorialJson);
  }

  public Tutorial getTutorial() {
    return tutorial;
  }

  public @Nullable TutorialLesson getNextLesson(TutorialLesson lesson) {
    int nextIndex = lesson.getLessonIndex() + 1;
    if (nextIndex >= tutorial.getLessonsCount()) {
      return null;
    }

    return tutorial.getLesson(nextIndex);
  }
}
