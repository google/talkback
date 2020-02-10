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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONException;
import org.json.JSONObject;

public class TutorialLessonPage {

  private static final String JSON_KEY_TITLE = "title";
  private static final String JSON_KEY_SUBTITLE = "subtitle";
  private static final String JSON_KEY_DESCRIPTION = "description";
  private static final String JSON_KEY_EXERCISE = "exercise";
  private static final String JSON_KEY_EXTRAS = "extras";

  private String title;
  private String subtitle;
  private String description;
  private Exercise exercise;
  private @NonNull Map<String, Object> extras;

  public TutorialLessonPage(Context context, JSONObject page) throws JSONException {
    String descriptionResourceName = JsonUtils.getString(page, JSON_KEY_DESCRIPTION);
    description = ResourceUtils.readStringByResourceIdFromString(context, descriptionResourceName);
    String titleResourceName = JsonUtils.getString(page, JSON_KEY_TITLE);
    title = ResourceUtils.readStringByResourceIdFromString(context, titleResourceName);
    String subtitleResourceName = JsonUtils.getString(page, JSON_KEY_SUBTITLE);
    subtitle = ResourceUtils.readStringByResourceIdFromString(context, subtitleResourceName);
    extras = getExtras(page);
    String exerciseClassName = JsonUtils.getString(page, JSON_KEY_EXERCISE);
    try {
      exercise = createExercise(exerciseClassName);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to create exercise object");
    }
    exercise.setTutorialLessonPage(this);
  }

  private static Exercise createExercise(String className)
      throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
          InvocationTargetException, InstantiationException {
    Class<?> clazz = Class.forName(className);
    Constructor<?> ctor = clazz.getConstructor();
    return (Exercise) ctor.newInstance();
  }

  private static @NonNull Map<String, Object> getExtras(JSONObject page) throws JSONException {
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
    return Collections.emptyMap();
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public String getTitle() {
    return title;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public boolean hasExtras() {
    return !extras.isEmpty();
  }

  public Map<String, Object> getExtras() {
    return extras;
  }
}
