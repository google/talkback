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
import com.android.talkback.tutorial.exercise.Exercise;
import com.android.utils.JsonUtils;
import com.android.utils.ResourceUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class TutorialLessonPage {

    private static final String JSON_KEY_TITLE = "title";
    private static final String JSON_KEY_SUBTITLE = "subtitle";
    private static final String JSON_KEY_DESCRIPTION = "description";
    private static final String JSON_KEY_EXERCISE = "exercise";

    private String mTitle;
    private String mSubtitle;
    private String mDescription;
    private Exercise mExercise;

    public TutorialLessonPage(Context context, JSONObject page) throws JSONException {
        String descriptionResourceName = JsonUtils.getString(page, JSON_KEY_DESCRIPTION);
        mDescription = ResourceUtils.readStringByResourceIdFromString(context,
                descriptionResourceName);
        String titleResourceName = JsonUtils.getString(page, JSON_KEY_TITLE);
        mTitle = ResourceUtils.readStringByResourceIdFromString(context,
                titleResourceName);
        String subtitleResourceName = JsonUtils.getString(page, JSON_KEY_SUBTITLE);
        mSubtitle = ResourceUtils.readStringByResourceIdFromString(context,
                subtitleResourceName);
        String exerciseClassName = JsonUtils.getString(page, JSON_KEY_EXERCISE);
        try {
            mExercise = createExercise(exerciseClassName);
            mExercise.setTutorialLessonPage(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create exercise object");
        }
    }

    private Exercise createExercise(String className) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException, InvocationTargetException,
            InstantiationException {
        Class<?> clazz = Class.forName(className);
        Constructor<?> ctor = clazz.getConstructor();
        return (Exercise) ctor.newInstance();
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getSubtitle() {
        return mSubtitle;
    }

    public Exercise getExercise() {
        return mExercise;
    }
}
