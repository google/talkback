/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime.tutorial;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;

/** Drawable that draws a flashing dot. */
class TapMeAnimationDrawable extends Drawable {

  private static final int ANIMATION_DURATION_MS = 1200;
  private static final int START_DELAY_DURATION_MS = 1000;

  private final Paint circlePaint;
  private final ValueAnimator animator;

  TapMeAnimationDrawable(Context context) {
    circlePaint = new Paint();
    circlePaint.setColor(context.getColor(R.color.text_highlight_color));

    // These values are the scale parameters of radius vary in the animation.
    animator = ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f);
    animator.setDuration(ANIMATION_DURATION_MS);
    animator.addUpdateListener(animation -> invalidateSelf());
    animator.addListener(
        new Animator.AnimatorListener() {
          @Override
          public void onAnimationStart(Animator animator) {}

          @Override
          public void onAnimationEnd(Animator animator) {
            animator.setStartDelay(START_DELAY_DURATION_MS);
            // This might not be a good solution but it works, do not start animator because
            // Robolectric tests run on main thread also, otherwise animator will repeat in endless
            // loop and cause the test timeout.
            if (!Utils.isRobolectric()) {
              animator.start();
            }
          }

          @Override
          public void onAnimationCancel(Animator animator) {}

          @Override
          public void onAnimationRepeat(Animator animator) {}
        });
    animator.start();
  }

  @Override
  public void draw(Canvas canvas) {
    float radius = (float) animator.getAnimatedValue() * getBounds().height() / 2f;
    canvas.drawCircle(getBounds().width() / 2f, getBounds().height() / 2f, radius, circlePaint);
  }

  @Override
  public void setAlpha(int alpha) {
    circlePaint.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(ColorFilter colorFilter) {}

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSPARENT;
  }
}
