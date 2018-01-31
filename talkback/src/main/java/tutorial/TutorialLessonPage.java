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
import com.google.android.accessibility.talkback.tutorial.exercise.Exercise;
import com.google.android.accessibility.utils.JsonUtils;
import com.google.android.accessibility.utils.ResourceUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class TutorialLessonPage {

  private static final String JSON_KEY_TITLE = "title";
  private static final String JSON_KEY_SUBTITLE = "subtitle";
  private static final String JSON_KEY_DESCRIPTION = "description";
  private static final String JSON_KEY_EXERCISE = "exercise";
  private static final String JSON_KEY_EXTRAS = "extras";

  private String mTitle;
  private String mSubtitle;
  private String mDescription;
  private Exercise mExercise;
  private Map<String, Object> mExtras;

  public TutorialLessonPage(Context context, JSONObject page) throws JSONException {
    String descriptionResourceName = JsonUtils.getString(page, JSON_KEY_DESCRIPTION);
    mDescription = ResourceUtils.readStringByResourceIdFromString(context, descriptionResourceName);
    String titleResourceName = JsonUtils.getString(page, JSON_KEY_TITLE);
    mTitle = ResourceUtils.readStringByResourceIdFromString(context, titleResourceName);
    String subtitleResourceName = JsonUtils.getString(page, JSON_KEY_SUBTITLE);
    mSubtitle = ResourceUtils.readStringByResourceIdFromString(context, subtitleResourceName);
    mExtras = getExtras(page);
    String exerciseClassName = JsonUtils.getString(page, JSON_KEY_EXERCISE);
    try {
      mExercise = createExercise(exerciseClassName);
      mExercise.setTutorialLessonPage(this);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create exercise object");
    }
  }

  private Exercise createExercise(String className)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          InvocationTargetException, InstantiationException {
    Class<?> clazz = Class.forName(className);
    Constructor<?> ctor = clazz.getConstructor();
    return (Exercise) ctor.newInstance();
  }

  private Map<String, Object> getExtras(JSONObject page) throws JSONException {
    JSONObject extrasJson = JsonUtils.getJsonObject(page, JSON_KEY_EXTRAS);
    if (extrasJson != null) {
      Map<String, Object> extras = new HashMap<>();
      Iterator<String> extrasKeys = extrasJson.keys();
      while (extrasKeys != null && extrasKeys.hasNext()) {
        String key = extrasKeys.next();
        extras.put(key, extrasJson.get(key));
      }
      return extras;
    }
    return null;
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

  public boolean hasExtras() {
    return mExtras != null && mExtras.size() > 0;
  }

  public Map<String, Object> getExtras() {
    return mExtras;
  }
}
