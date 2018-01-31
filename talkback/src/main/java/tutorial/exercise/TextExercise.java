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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;

public abstract class TextExercise extends Exercise {

  @Override
  public View getContentView(final LayoutInflater inflater, ViewGroup parent) {
    View view = inflater.inflate(R.layout.tutorial_content_text, parent, false);
    TextView textView = (TextView) view.findViewById(R.id.text);
    textView.setText(getText(inflater.getContext()));
    textView.setGravity(getGravity());
    textView.setTextSize(getTextSize());
    return view;
  }

  public abstract CharSequence getText(Context context);

  public int getGravity() {
    return Gravity.NO_GRAVITY;
  }

  public int getTextSize() {
    return 14;
  }
}
