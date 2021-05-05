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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;

/** A text that starts with a number. */
public class TextWithNumber extends PageContentConfig {

  private static final int GAP_WIDTH = 50;
  @StringRes private final int textResId;
  private final int number;

  public TextWithNumber(@StringRes int textResId, int number) {
    this.textResId = textResId;
    this.number = number;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view = inflater.inflate(R.layout.training_text_with_number, container, false);
    final TextView textView = view.findViewById(R.id.training_text_with_number);
    SpannableString spannableString = new SpannableString(context.getString(textResId));
    spannableString.setSpan(
        new NumberLeadingMarginSpan(number),
        0,
        spannableString.length(),
        Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    textView.setText(spannableString, BufferType.SPANNABLE);
    return view;
  }

  /** A {@link LeadingMarginSpan} with a number at the beginning of paragraph. */
  private static class NumberLeadingMarginSpan implements LeadingMarginSpan {

    private final int number;

    private NumberLeadingMarginSpan(int number) {
      this.number = number;
    }

    @Override
    public int getLeadingMargin(boolean first) {
      return GAP_WIDTH;
    }

    @Override
    public void drawLeadingMargin(
        Canvas canvas,
        Paint paint,
        int current,
        int dir,
        int top,
        int baseline,
        int bottom,
        CharSequence text,
        int start,
        int end,
        boolean first,
        Layout layout) {
      boolean isFirst = ((Spanned) text).getSpanStart(this) == start;
      if (isFirst) {
        canvas.drawText(number + ".", current, baseline, paint);
      }
    }
  }
}
