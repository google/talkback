/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.talkback.trainingcommon.tv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.google.android.accessibility.talkback.R;

/** View for a navigation button on TV. Becomes bigger when focused. */
@SuppressLint("AppCompatCustomView")
public class TvNavigationButton extends Button {
  private static final int ANIMATION_DURATION = 300;
  private final float scaleDefault =
      getResources().getFloat(R.dimen.tv_training_button_scale_default);
  private final float scaleFocused =
      getResources().getFloat(R.dimen.tv_training_button_scale_focused);

  public TvNavigationButton(Context context) {
    super(context);

    int horizontalPadding =
        getResources().getDimensionPixelSize(R.dimen.tv_training_button_padding_horizontal);
    int verticalPadding =
        getResources().getDimensionPixelSize(R.dimen.tv_training_button_padding_vertical);
    setPadding(
        /* left= */ horizontalPadding,
        /* top= */ verticalPadding,
        /* right= */ horizontalPadding,
        /* bottom= */ verticalPadding);

    int marginBottom = getResources().getDimensionPixelSize(R.dimen.tv_training_button_margin);
    LayoutParams layoutParams =
        new LinearLayout.LayoutParams(
            /* width= */ LayoutParams.MATCH_PARENT, /* height= */ LayoutParams.WRAP_CONTENT);
    layoutParams.setMargins(0, 0, 0, marginBottom);
    setLayoutParams(layoutParams);
    setTextSize(
        TypedValue.COMPLEX_UNIT_PX,
        context.getResources().getDimension(R.dimen.tv_training_button_text_size));

    setTypeface(Typeface.create("google-sans-text-medium", Typeface.NORMAL));
    setAllCaps(false);
    setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    setIncludeFontPadding(false);

    setFocusable(true);
    setFocusableInTouchMode(true);
    setOnFocusChangeListener(
        (View button, boolean hasFocus) -> {
          if (hasFocus) {
            onFocus();
          } else {
            onBlur();
          }
        });

    onBlur(); // To apply initial style.
  }

  private void onFocus() {
    setBackgroundResource(R.drawable.tv_training_button_focused);
    setTextColor(
        getResources()
            .getColor(R.color.tv_training_button_focused_text_color, getContext().getTheme()));
    startScaleAnimation(getScaleX(), scaleFocused);
  }

  private void onBlur() {
    setBackgroundResource(R.drawable.tv_training_button);
    setTextColor(
        getResources().getColor(R.color.tv_training_button_text_color, getContext().getTheme()));
    startScaleAnimation(getScaleX(), scaleDefault);
  }

  private void startScaleAnimation(float startScale, float endScale) {
    Animation animation =
        new ScaleAnimation(
            startScale,
            endScale,
            startScale,
            endScale,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f);
    animation.setFillAfter(true);
    animation.setDuration(ANIMATION_DURATION);
    startAnimation(animation);
  }
}
