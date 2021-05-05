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

import static android.view.animation.Animation.INFINITE;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_0;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_180;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_270;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_90;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Size;
import android.view.View;
import androidx.annotation.ColorInt;
import com.google.android.accessibility.brailleime.BrailleIme.OrientationSensitive;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.tutorial.TutorialAnimationView.SwipeAnimation.Direction;
import java.util.ArrayList;
import java.util.List;

/** View that draws instruction text, gesture animation and action result text. */
class TutorialAnimationView extends View implements OrientationSensitive {

  private final HintToast hintToast;
  private final ActionResult actionResult;
  private final SwipeAnimation swipeAnimation;

  private boolean shouldShowActionResult;
  private boolean shouldShowSwipeAnimation;
  private int orientation;
  private Size screenSize;
  private final boolean isTabletop;

  TutorialAnimationView(Context context, int orientation, Size screenSize, boolean isTabletop) {
    super(context);
    this.orientation = orientation;
    this.isTabletop = isTabletop;
    this.screenSize = getScreenSize(screenSize);

    hintToast = new HintToast(context, this::invalidate);
    actionResult = new ActionResult(context, this::invalidate);
    swipeAnimation = new SwipeAnimation(context, this::invalidate);
  }

  @Override
  public void onOrientationChanged(int orientation, Size screenSize) {
    this.orientation = orientation;
    this.screenSize = getScreenSize(screenSize);
    invalidate();
    requestLayout();
  }

  private Size getScreenSize(Size screenSize) {
    if (Utils.isPhoneSizedDevice(getResources())
        && orientation == Configuration.ORIENTATION_PORTRAIT) {
      screenSize =
          new Size(/* width= */ screenSize.getHeight(), /* height= */ screenSize.getWidth());
    }
    return screenSize;
  }

  @Override
  public void onDraw(Canvas canvas) {
    canvas.save();
    if (Utils.isPhoneSizedDevice(getResources())) {
      if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        canvas.rotate(isTabletop ? ROTATION_90 : ROTATION_270);
        canvas.translate(
            /* dx= */ isTabletop ? 0 : -screenSize.getWidth(),
            /* dy= */ isTabletop ? -screenSize.getHeight() : 0);
      } else {
        canvas.rotate(isTabletop ? ROTATION_180 : ROTATION_0);
        canvas.translate(
            /* dx= */ isTabletop ? -screenSize.getWidth() : 0,
            /* dy= */ isTabletop ? -screenSize.getHeight() : 0);
      }
    }

    hintToast.onDraw(canvas, screenSize);
    if (shouldShowActionResult) {
      actionResult.onDraw(canvas, screenSize);
    }
    if (shouldShowSwipeAnimation) {
      swipeAnimation.onDraw(canvas);
    }
    canvas.restore();
  }

  void startHintToastAnimation(String text) {
    hintToast.setText(text);
    hintToast.startAnimation();
  }

  void startActionResultAnimation(String text) {
    shouldShowActionResult = true;
    actionResult.setText(text);
    actionResult.startAnimation();
  }

  void startSwipeAnimation(int fingerCount, Direction swipeDirection) {
    shouldShowSwipeAnimation = true;
    swipeAnimation.updatePaint(
        fingerCount, screenSize, isTabletop ? mirror(swipeDirection) : swipeDirection);
    swipeAnimation.startAnimation();
  }

  private static Direction mirror(Direction oldDirection) {
    if (oldDirection == Direction.LEFT_TO_RIGHT) {
      return Direction.RIGHT_TO_LEFT;
    } else if (oldDirection == Direction.RIGHT_TO_LEFT) {
      return Direction.LEFT_TO_RIGHT;
    }
    return oldDirection;
  }

  void stopSwipeAnimation() {
    shouldShowSwipeAnimation = false;
    swipeAnimation.stopAnimation();
  }

  void reset() {
    shouldShowActionResult = false;
    shouldShowSwipeAnimation = false;
  }

  /** Draws the tutorial instruction of every stage in the top middle of screen. */
  static class HintToast {

    private static final int ANIMATION_DURATION_MS = 200;
    private static final float HEIGHT_SCALE = 1.5f;

    private final int top;

    private final Paint textBackgroundPaint;
    private final TextPaint textPaint;
    private final RectF textBackgroundRect;
    private final ValueAnimator animator;
    private String text = "";

    HintToast(Context context, Runnable invalidate) {
      top = context.getResources().getDimensionPixelSize(R.dimen.hint_margin);

      int backgroundColor = context.getColor(R.color.hint_background);
      textBackgroundPaint = new Paint();
      textBackgroundPaint.setColor(backgroundColor);
      textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      textPaint.setColor(Color.WHITE);
      textPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.hint_text_size));
      textPaint.setTextAlign(Align.CENTER);
      textBackgroundRect = new RectF();

      animator = ValueAnimator.ofInt(0, Color.alpha(backgroundColor));
      animator.setDuration(ANIMATION_DURATION_MS);
      animator.addUpdateListener(animation -> invalidate.run());
    }

    void startAnimation() {
      animator.start();
    }

    void setText(String text) {
      this.text = text;
    }

    void onDraw(Canvas canvas, Size screenSize) {
      int alpha = (int) animator.getAnimatedValue();
      textBackgroundPaint.setAlpha(alpha);
      // Append 4 spaces to make text look like it has left/right padding.
      float measureTextWidth = textPaint.measureText("    " + text);
      float textLength = min(screenSize.getWidth(), measureTextWidth);
      StaticLayout staticLayout = buildStaticLayout(text, textPaint, (int) textLength);

      float left = (screenSize.getWidth() - textLength) / 2f;
      float top = this.top;
      float textHeight = staticLayout.getHeight();
      textBackgroundRect.set(left, top, left + textLength, top + HEIGHT_SCALE * textHeight);

      float cornerRadius = (textBackgroundRect.bottom - textBackgroundRect.top) / 2f;
      canvas.drawRoundRect(textBackgroundRect, cornerRadius, cornerRadius, textBackgroundPaint);
      canvas.save();
      canvas.translate(textBackgroundRect.centerX(), textBackgroundRect.centerY() - textHeight / 2);
      staticLayout.draw(canvas);
      canvas.restore();
    }
  }

  /** Draws the braille output or gesture event in the middle of screen. */
  static class ActionResult {

    private static final int ANIMATION_DURATION_MS = 300;
    private static final float ROUND_CORNER_RADIUS_DIVISOR = 5f;

    private final Paint textBackgroundPaint;
    private final TextPaint textPaint;
    private final RectF textBackgroundRect;
    private final ValueAnimator animator;
    private String text = "";

    ActionResult(Context context, Runnable invalidate) {
      textBackgroundPaint = new Paint();
      textBackgroundPaint.setColor(context.getColor(R.color.hint_background));
      textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      textPaint.setColor(Color.WHITE);
      textPaint.setTextSize(
          context.getResources().getDimensionPixelSize(R.dimen.action_result_text_size));
      textPaint.setTextAlign(Align.CENTER);
      textBackgroundRect = new RectF();

      animator = ValueAnimator.ofInt(0, Color.alpha(context.getColor(R.color.hint_background)));
      animator.setDuration(ANIMATION_DURATION_MS);
      animator.addUpdateListener(animation -> invalidate.run());
    }

    void startAnimation() {
      animator.start();
    }

    void setText(String text) {
      this.text = text;
    }

    void onDraw(Canvas canvas, Size screenSize) {
      int alpha = (int) animator.getAnimatedValue();
      textBackgroundPaint.setAlpha(alpha);
      // Append 4 spaces to make text look like it has left/right padding.
      float measureTextWidth = (int) textPaint.measureText("    " + text);
      float textLength = min(screenSize.getWidth(), measureTextWidth);
      StaticLayout staticLayout = buildStaticLayout(text, textPaint, (int) textLength);

      float textHeight = staticLayout.getHeight();
      float rectHeight = 2f * textHeight;
      float rectLength = max(textLength, rectHeight);
      float left = (screenSize.getWidth() - rectLength) / 2f;
      float top = (screenSize.getHeight() - rectHeight) / 2f;
      textBackgroundRect.set(left, top, left + rectLength, top + rectHeight);
      float cornerRadius = textHeight / ROUND_CORNER_RADIUS_DIVISOR;
      canvas.drawRoundRect(textBackgroundRect, cornerRadius, cornerRadius, textBackgroundPaint);
      canvas.save();
      canvas.translate(textBackgroundRect.centerX(), textBackgroundRect.centerY() - textHeight / 2);
      staticLayout.draw(canvas);
      canvas.restore();
    }
  }

  private static StaticLayout buildStaticLayout(
      String text, TextPaint textPaint, int desiredLength) {
    StaticLayout.Builder builder =
        StaticLayout.Builder.obtain(text, /* start= */ 0, text.length(), textPaint, desiredLength)
            .setAlignment(Alignment.ALIGN_NORMAL)
            .setLineSpacing(/* spacingAdd= */ 0, /* spacingMult= */ 1)
            .setIncludePad(false);
    return builder.build();
  }

  /** Draws the swipe instruction animation in the middle of screen. */
  static class SwipeAnimation {
    public enum Direction {
      TOP_TO_BOTTOM,
      BOTTOM_TO_TOP,
      LEFT_TO_RIGHT,
      RIGHT_TO_LEFT,
    }

    private static final int DISTANCE_BETWEEN_GESTURES_IN_PIXELS = 75;
    private static final int ANIMATION_DURATION_MS = 1500;
    private static final float SCALE_FACTOR = 0.5f;

    private final Paint circlePaint;
    private final Paint roundRectPaint;
    private ValueAnimator animator;
    private Direction direction = Direction.TOP_TO_BOTTOM;
    private final int dotRadiusInPixels;

    @ColorInt private final int gestureGradientColor;
    private final Runnable invalidate;
    private final List<Point> gesturesCircleCoordinates = new ArrayList<>();

    SwipeAnimation(Context context, Runnable invalidate) {
      this.invalidate = invalidate;

      circlePaint = new Paint();
      circlePaint.setColor(context.getColor(R.color.gesture_circle));

      this.gestureGradientColor = context.getColor(R.color.gesture_circle_gradient);
      roundRectPaint = new Paint();
      roundRectPaint.setColor(gestureGradientColor);
      dotRadiusInPixels =
          context.getResources().getDimensionPixelSize(R.dimen.tutorial_gesture_dot_radius);
    }

    void updatePaint(int fingerCount, Size screenSize, Direction swipeDirection) {
      this.direction = swipeDirection;
      float canvasLength = 0;
      switch (swipeDirection) {
        case TOP_TO_BOTTOM:
        case BOTTOM_TO_TOP:
          canvasLength = screenSize.getHeight() * SCALE_FACTOR;
          break;
        case LEFT_TO_RIGHT:
        case RIGHT_TO_LEFT:
          canvasLength = screenSize.getWidth() * SCALE_FACTOR;
          break;
      }
      updateGesturesCoordinates(fingerCount, (int) canvasLength, screenSize, swipeDirection);
      Rect gradientVariation = determineGradientVariation((int) canvasLength, swipeDirection);
      Shader shader =
          new LinearGradient(
              gradientVariation.left,
              gradientVariation.top,
              gradientVariation.right,
              gradientVariation.bottom,
              Color.TRANSPARENT,
              gestureGradientColor,
              TileMode.CLAMP);
      roundRectPaint.setShader(shader);

      animator = ValueAnimator.ofFloat(0, canvasLength, canvasLength);
      animator.setDuration(ANIMATION_DURATION_MS);
      animator.setRepeatCount(INFINITE);
      animator.addUpdateListener(animation -> invalidate.run());
    }

    void startAnimation() {
      animator.start();
    }

    void stopAnimation() {
      animator.cancel();
    }

    /** Determines the start coordinates of swipe animation. */
    void updateGesturesCoordinates(
        int fingerCount, int canvasLength, Size screenSize, Direction swipeDirection) {
      gesturesCircleCoordinates.clear();
      int gestureInterval = DISTANCE_BETWEEN_GESTURES_IN_PIXELS + 2 * dotRadiusInPixels;
      int distanceToStartPoint = (fingerCount - 1) * gestureInterval / 2;
      for (int i = 0; i < fingerCount; i++) {
        int x = 0;
        int y = 0;
        switch (swipeDirection) {
          case TOP_TO_BOTTOM:
            x = screenSize.getWidth() / 2 - distanceToStartPoint + i * gestureInterval;
            y = (screenSize.getHeight() - canvasLength) / 2;
            break;
          case BOTTOM_TO_TOP:
            x = screenSize.getWidth() / 2 - distanceToStartPoint + i * gestureInterval;
            y = (screenSize.getHeight() - canvasLength) / 2 + canvasLength;
            break;
          case LEFT_TO_RIGHT:
            x = (screenSize.getWidth() - canvasLength) / 2;
            y = screenSize.getHeight() / 2 - distanceToStartPoint + i * gestureInterval;
            break;
          case RIGHT_TO_LEFT:
            x = (screenSize.getWidth() - canvasLength) / 2 + canvasLength;
            y = screenSize.getHeight() / 2 - distanceToStartPoint + i * gestureInterval;
            break;
        }
        gesturesCircleCoordinates.add(new Point(x, y));
      }
    }

    Rect determineGradientVariation(int canvasLength, Direction swipeDirection) {
      Rect gradientVariation = new Rect();
      gradientVariation.left = gesturesCircleCoordinates.get(0).x - dotRadiusInPixels;
      gradientVariation.top = gesturesCircleCoordinates.get(0).y - dotRadiusInPixels;
      switch (swipeDirection) {
        case TOP_TO_BOTTOM:
          gradientVariation.right = gradientVariation.left;
          gradientVariation.bottom = gradientVariation.top + canvasLength;
          break;
        case BOTTOM_TO_TOP:
          gradientVariation.right = gradientVariation.left;
          gradientVariation.top = gesturesCircleCoordinates.get(0).y + dotRadiusInPixels;
          gradientVariation.bottom = gradientVariation.top - canvasLength;
          break;
        case LEFT_TO_RIGHT:
          gradientVariation.right = gradientVariation.left + canvasLength;
          gradientVariation.bottom = gradientVariation.top;
          break;
        case RIGHT_TO_LEFT:
          gradientVariation.left = gesturesCircleCoordinates.get(0).x + dotRadiusInPixels;
          gradientVariation.right = gradientVariation.left - canvasLength;
          gradientVariation.bottom = gradientVariation.top;
          break;
      }
      return gradientVariation;
    }

    void onDraw(Canvas canvas) {
      float range = (float) animator.getAnimatedValue();
      for (Point point : gesturesCircleCoordinates) {
        switch (direction) {
          case TOP_TO_BOTTOM:
            canvas.drawRoundRect(
                point.x - dotRadiusInPixels,
                point.y - dotRadiusInPixels,
                point.x + dotRadiusInPixels,
                point.y + dotRadiusInPixels + range,
                dotRadiusInPixels,
                dotRadiusInPixels,
                roundRectPaint);
            canvas.drawCircle(point.x, point.y + range, dotRadiusInPixels, circlePaint);
            break;
          case BOTTOM_TO_TOP:
            canvas.drawRoundRect(
                point.x - dotRadiusInPixels,
                point.y + dotRadiusInPixels,
                point.x + dotRadiusInPixels,
                point.y - dotRadiusInPixels - range,
                dotRadiusInPixels,
                dotRadiusInPixels,
                roundRectPaint);
            canvas.drawCircle(point.x, point.y - range, dotRadiusInPixels, circlePaint);
            break;
          case LEFT_TO_RIGHT:
            canvas.drawRoundRect(
                point.x - dotRadiusInPixels,
                point.y - dotRadiusInPixels,
                point.x + dotRadiusInPixels + range,
                point.y + dotRadiusInPixels,
                dotRadiusInPixels,
                dotRadiusInPixels,
                roundRectPaint);
            canvas.drawCircle(point.x + range, point.y, dotRadiusInPixels, circlePaint);
            break;
          case RIGHT_TO_LEFT:
            canvas.drawRoundRect(
                point.x + dotRadiusInPixels,
                point.y - dotRadiusInPixels,
                point.x - dotRadiusInPixels - range,
                point.y + dotRadiusInPixels,
                dotRadiusInPixels,
                dotRadiusInPixels,
                roundRectPaint);
            canvas.drawCircle(point.x - range, point.y, dotRadiusInPixels, circlePaint);
            break;
        }
      }
    }
  }
}
