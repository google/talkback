/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.trainingcommon.content;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.TtsSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;

/** A tip to show extra information. */
public class Tip extends PageContentConfig {

  @StringRes private final int textResId;
  @StringRes private final int textTtsSpanResId;

  public Tip(@StringRes int textResId) {
    this(textResId, UNKNOWN_RESOURCE_ID);
  }

  public Tip(@StringRes int textResId, @StringRes int textTtsSpanResId) {
    this.textResId = textResId;
    this.textTtsSpanResId = textTtsSpanResId;
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view = inflater.inflate(R.layout.training_tip, container, false);
    final TextView tip = view.findViewById(R.id.training_tip_text);

    String text = context.getString(textResId);
    SpannableString spannableString = new SpannableString(text);
    if (textTtsSpanResId != UNKNOWN_RESOURCE_ID) {
      String ttsSpanText = context.getString(textTtsSpanResId);
      TtsSpan ttsSpan = new TtsSpan.TextBuilder(ttsSpanText).build();
      spannableString.setSpan(ttsSpan, 0, text.length(), 0 /* no flag */);
    }
    tip.setText(spannableString);

    return view;
  }
}
