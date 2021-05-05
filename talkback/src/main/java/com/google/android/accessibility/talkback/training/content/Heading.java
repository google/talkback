/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.training.content;

import android.content.Context;
import androidx.core.view.ViewCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;

/** A heading shows at the top of context in general. */
public class Heading extends PageContentConfig {

  private final @StringRes int textResId;

  public Heading(@StringRes int textResId) {
    this.textResId = textResId;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view = inflater.inflate(R.layout.training_heading, container, false);
    final TextView heading = view.findViewById(R.id.training_heading);
    heading.setText(textResId);
    ViewCompat.setAccessibilityHeading(heading, true);
    return view;
  }
}
