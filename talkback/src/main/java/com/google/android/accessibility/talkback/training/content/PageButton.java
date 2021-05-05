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

import static android.widget.Toast.LENGTH_LONG;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;

/** A button with a text on training pages. */
public class PageButton extends PageContentConfig {

  private final @StringRes int textResId;

  public PageButton(@StringRes int textResId) {
    this.textResId = textResId;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view = inflater.inflate(R.layout.training_button, container, false);
    final Button button = view.findViewById(R.id.training_button);
    button.setText(textResId);
    button.setOnClickListener(
        btn ->
            Toast.makeText(
                    context,
                    context.getString(R.string.activated_view, context.getString(textResId)),
                    LENGTH_LONG)
                .show());
    return view;
  }
}
