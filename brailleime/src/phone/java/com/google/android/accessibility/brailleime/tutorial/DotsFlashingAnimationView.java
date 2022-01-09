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

import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_0;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_180;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_270;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_90;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Size;
import android.view.View;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleIme.OrientationSensitive;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.input.BrailleInputPlane.DotTarget;
import java.util.ArrayList;
import java.util.List;

/**
 * View that draws flashing dots animation. The given DotTargets should be the same with the ones in
 * {@link com.google.android.accessibility.brailleime.input.BrailleInputPlane}
 */
class DotsFlashingAnimationView extends View implements OrientationSensitive {

  private static final int ANIMATION_DURATION_MS = 1200;
  private static final int START_DELAY_DURATION_MS = 1000;
  private static final int ANIMATION_DEFAULT_ALPHA = 180;
  private static final float CIRCLE_ANIMATION_MAX_SCALE = 1.3f;
  private static final float CIRCLE_ANIMATION_MIN_SCALE = 0.7f;

  private final Paint dotBackgroundPaint;
  private final Paint dotNumberPaint;
  private final Paint dotFlashingPaint;
  private final ValueAnimator animator;
  private final int dotRadiusInPixels;
  private final boolean isTabletop;

  private List<DotTarget> dotTargets;
  private int orientation;

  DotsFlashingAnimationView(
      Context context, List<DotTarget> dotTargets, int orientation, boolean isTabletop) {
    super(context);
    this.dotTargets = dotTargets;
    this.orientation = orientation;
    this.isTabletop = isTabletop;
    dotRadiusInPixels =
        context.getResources().getDimensionPixelSize(R.dimen.input_plane_dot_radius);

    dotBackgroundPaint = new Paint();
    dotBackgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    dotBackgroundPaint.setColor(context.getColor(R.color.input_plane_dot_background_default));

    dotNumberPaint = new Paint();
    dotNumberPaint.setTextAlign(Paint.Align.CENTER);
    float scaleFactor =
        Utils.getResourcesFloat(getResources(), R.dimen.input_plane_dot_number_size_multiplier);
    dotNumberPaint.setTextSize(scaleFactor * dotRadiusInPixels);
    float textStrokeWidth =
        getResources().getDimension(R.dimen.input_plane_dot_number_stroke_width);
    dotNumberPaint.setStrokeWidth(textStrokeWidth);
    dotNumberPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    dotNumberPaint.setColor(context.getColor(R.color.input_plane_dot_number_default));

    dotFlashingPaint = new Paint();
    dotFlashingPaint.setColor(context.getColor(R.color.gesture_circle));
    animator =
        ValueAnimator.ofFloat(
            CIRCLE_ANIMATION_MIN_SCALE, CIRCLE_ANIMATION_MAX_SCALE, CIRCLE_ANIMATION_MIN_SCALE);
    animator.setDuration(ANIMATION_DURATION_MS);
    animator.addUpdateListener(animation -> invalidate());
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

  /** Returns the flashing braille character. */
  BrailleCharacter getFlashingCharacter() {
    List<Integer> dotNumbers = new ArrayList<>();
    for (DotTarget dotTarget : dotTargets) {
      dotNumbers.add(dotTarget.getDotNumber());
    }
    return new BrailleCharacter(dotNumbers);
  }

  /** Updates the location of flashing character. */
  void updateDotTarget(List<DotTarget> dotTargets) {
    this.dotTargets = dotTargets;
  }

  @Override
  public void onOrientationChanged(int orientation, Size screenSize) {
    this.orientation = orientation;
    invalidate();
    requestLayout();
  }

  @Override
  public void onDraw(Canvas canvas) {
    for (DotTarget dot : dotTargets) {
      canvas.save();
      int degree = 0;
      if (Utils.isPhoneSizedDevice(getResources())) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          degree = isTabletop ? ROTATION_90 : ROTATION_270;
        } else {
          degree = isTabletop ? ROTATION_180 : ROTATION_0;
        }
      }
      canvas.rotate(degree, dot.getCenter().x, dot.getCenter().y);
      // Draw dot background and number (text).
      int textBaseline = Utils.getPaintTextBaselineInPixels(dotNumberPaint);
      canvas.drawCircle(
          dot.getCenter().x, dot.getCenter().y, dotRadiusInPixels, dotBackgroundPaint);
      canvas.drawText(
          Integer.toString(dot.getDotNumber()),
          dot.getCenter().x,
          dot.getCenter().y + textBaseline,
          dotNumberPaint);

      // Draw dot flashing background.
      float scale = (float) animator.getAnimatedValue();
      float animationRadius = scale * dotRadiusInPixels;
      int alpha =
          (int)
              (ANIMATION_DEFAULT_ALPHA
                  * (scale - CIRCLE_ANIMATION_MIN_SCALE)
                  / (CIRCLE_ANIMATION_MAX_SCALE - CIRCLE_ANIMATION_MIN_SCALE));
      dotFlashingPaint.setAlpha(alpha);
      canvas.drawCircle(dot.getCenter().x, dot.getCenter().y, animationRadius, dotFlashingPaint);
      canvas.restore();
    }
  }
}
