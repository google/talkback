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

package com.android.talkback.tutorial;

import android.content.Context;
import com.android.utils.JsonUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Tutorial {

    private static final String JSON_KEY_LESSONS = "lessons";

    private TutorialLesson[] mLessons;

    public Tutorial(Context context, JSONObject tutorial) throws JSONException {
        JSONArray lessons = JsonUtils.getJsonArray(tutorial, JSON_KEY_LESSONS);
        if (lessons != null && lessons.length() > 0) {
            int lessonCount = lessons.length();
            mLessons = new TutorialLesson[lessonCount];
            for (int i = 0; i < lessonCount; i++) {
                JSONObject lesson = lessons.getJSONObject(i);
                mLessons[i] = new TutorialLesson(context, lesson);
            }
        } else {
            mLessons = new TutorialLesson[0];
        }
    }

    public int getLessonsCount() {
        return mLessons.length;
    }

    public TutorialLesson getLesson(int index) {
        return mLessons[index];
    }
}
