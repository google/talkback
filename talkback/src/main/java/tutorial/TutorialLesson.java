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
import com.google.android.accessibility.utils.JsonUtils;
import com.google.android.accessibility.utils.ResourceUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TutorialLesson {

  private static final String JSON_KEY_TITLE = "title";
  private static final String JSON_KEY_SHORT_DESCRIPTION = "short_description";
  private static final String JSON_KEY_PAGES = "pages";
  private static final String JSON_KEY_PRACTICE = "practice";
  private static final String JSON_KEY_INDEX = "lesson_index";

  private String title;
  private String shortDescription;
  private TutorialLessonPage[] pages;
  private int lessonIndex;

  public TutorialLesson(Context context, JSONObject lesson) throws JSONException {
    String titleResourceName = JsonUtils.getString(lesson, JSON_KEY_TITLE);
    title = ResourceUtils.readStringByResourceIdFromString(context, titleResourceName);
    String descriptionResourceName = JsonUtils.getString(lesson, JSON_KEY_SHORT_DESCRIPTION);
    shortDescription =
        ResourceUtils.readStringByResourceIdFromString(context, descriptionResourceName);
    lessonIndex = JsonUtils.getInt(lesson, JSON_KEY_INDEX);

    JSONArray pagesArray = JsonUtils.getJsonArray(lesson, JSON_KEY_PAGES);
    JSONObject practice = JsonUtils.getJsonObject(lesson, JSON_KEY_PRACTICE);
    int pageCount = 0;
    if (pagesArray != null) {
      pageCount += pagesArray.length();
    }

    if (practice != null) {
      pageCount++;
    }

    pages = new TutorialLessonPage[pageCount];

    if (pagesArray != null) {
      int lessonPagesCount = pagesArray.length();
      for (int i = 0; i < lessonPagesCount; i++) {
        JSONObject pageJson = pagesArray.getJSONObject(i);
        pages[i] = new TutorialLessonPage(context, pageJson);
      }
    }

    if (practice != null) {
      pages[pageCount - 1] = new TutorialLessonPage(context, practice);
    }
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }

  public void setShortDescription(String description) {
    shortDescription = description;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public void setTutorialLessonPages(TutorialLessonPage[] pages) {
    this.pages = pages;
  }

  public TutorialLessonPage getLessonPage(int pageIndex) {
    return pages[pageIndex];
  }

  public int getPracticePage() {
    return Math.max(0, pages.length - 1);
  }

  public int getPagesCount() {
    return pages.length;
  }

  public int getLessonIndex() {
    return lessonIndex;
  }
}
