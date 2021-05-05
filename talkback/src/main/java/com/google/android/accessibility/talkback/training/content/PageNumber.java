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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;

/** Shows current page name and total page number. */
public class PageNumber extends PageContentConfig {
  private final int pageNumber;
  private final int totalNumber;

  public PageNumber(int pageNumber, int totalNumber) {
    this.pageNumber = pageNumber;
    this.totalNumber = totalNumber;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view = inflater.inflate(R.layout.training_page_number, container, false);
    final TextView pageNumberText = view.findViewById(R.id.training_page_number);
    pageNumberText.setText(
        context.getString(R.string.training_page_number, pageNumber, totalNumber));
    return view;
  }
}
