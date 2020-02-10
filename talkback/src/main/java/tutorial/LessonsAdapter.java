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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;

public class LessonsAdapter extends BaseAdapter {

  private Context context;
  private Tutorial tutorial;
  private TutorialNavigationCallback callback;

  public LessonsAdapter(Context context, Tutorial tutorial, TutorialNavigationCallback callback) {
    this.context = context;
    this.tutorial = tutorial;
    this.callback = callback;
  }

  @Override
  public int getCount() {
    return tutorial.getLessonsCount();
  }

  @Override
  public Object getItem(int position) {
    return tutorial.getLesson(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView =
          LayoutInflater.from(context).inflate(R.layout.tutorial_main_lesson_item, parent, false);

      ViewHolder holder = new ViewHolder();
      holder.title = (TextView) convertView.findViewById(R.id.lesson_title);
      holder.description = (TextView) convertView.findViewById(R.id.lesson_description);
      holder.practice = convertView.findViewById(R.id.practice);
      holder.startLesson = convertView.findViewById(R.id.start_lesson);

      convertView.setTag(holder);
    }

    final TutorialLesson lesson = tutorial.getLesson(position);
    ViewHolder holder = (ViewHolder) convertView.getTag();
    holder.title.setText(lesson.getTitle());
    holder.description.setText(lesson.getShortDescription());

    holder.practice.setContentDescription(
        context.getString(R.string.tutorial_practice_content, position + 1));
    holder.startLesson.setContentDescription(
        context.getString(R.string.tutorial_start_lesson_content, position + 1));
    holder.practice.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            callback.onLessonPracticeSelected(lesson);
          }
        });
    holder.startLesson.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            callback.onLessonSelected(lesson);
          }
        });

    return convertView;
  }

  private static final class ViewHolder {
    public TextView title;
    public TextView description;
    public View startLesson;
    public View practice;
  }
}
