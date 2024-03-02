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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.util.Size;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import com.google.android.accessibility.braille.common.BrailleUtils;
import com.google.android.accessibility.brailleime.BrailleIme.OrientationSensitive;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.tutorial.TutorialAnimationView.CanvasView.Painter;
import com.google.android.accessibility.brailleime.tutorial.TutorialAnimationView.SwipeAnimation.Direction;
import java.util.ArrayList;
import java.util.List;

/** View that draws instruction text, gesture animation and action result text. */
class TutorialAnimationView extends FrameLayout implements OrientationSensitive {
  private static final int HINT_TOAST_ANIMATION_DURATION_MS = 200;
  private static final int ACTION_RESULT_ANIMATION_DURATION_MS = 300;

  private final CanvasView canvasView;
  private final TextView hintToast;
  private final TextView actionResult;
  private final SwipeAnimation swipeAnimation;
  private final Animation actionResultAnimation;
  private final Animation hintToastAnimation;

  private final boolean isTabletop;

  TutorialAnimationView(Context context, int orientation, Size screenSize, boolean isTabletop) {
    super(context);
    inflate(getContext(), R.layout.tutorial_animation_view, this);
    this.isTabletop = isTabletop;
    canvasView = new CanvasView(context, orientation, screenSize, isTabletop);
    canvasView.setLayoutParams(
        new FrameLayout.LayoutParams(screenSize.getWidth(), screenSize.getHeight()));
    addView(canvasView);
    hintToast = findViewById(R.id.hint_toast);
    actionResult = findViewById(R.id.action_result);
    swipeAnimation = new SwipeAnimation(context, canvasView::invalidate);

    actionResultAnimation = new AlphaAnimation(/* fromAlpha= */ 0.0f, /* toAlpha= */ 1.0f);
    actionResultAnimation.setDuration(ACTION_RESULT_ANIMATION_DURATION_MS);
    hintToastAnimation = new AlphaAnimation(/* fromAlpha= */ 0.0f, /* toAlpha= */ 1.0f);
    hintToastAnimation.setDuration(HINT_TOAST_ANIMATION_DURATION_MS);
  }

  @Override
  public void onOrientationChanged(int orientation, Size screenSize) {
    canvasView.setLayoutParams(
        new FrameLayout.LayoutParams(screenSize.getWidth(), screenSize.getHeight()));
    canvasView.onOrientationChanged(orientation, screenSize);
    invalidate();
    requestLayout();
  }

  void startHintToastAnimation(String text) {
    hintToast.setText(text);
    hintToast.setVisibility(VISIBLE);
    hintToast.startAnimation(hintToastAnimation);
  }

  void startActionResultAnimation(String text) {
    actionResult.setText(text);
    actionResult.setVisibility(VISIBLE);
    actionResult.startAnimation(actionResultAnimation);
  }

  void startSwipeAnimation(int fingerCount, Direction swipeDirection) {
    swipeAnimation.updatePaint(
        fingerCount,
        canvasView.getCanvasSize(),
        isTabletop ? mirror(swipeDirection) : swipeDirection);
    canvasView.addPainter(swipeAnimation);
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
    canvasView.removePainter(swipeAnimation);
    swipeAnimation.stopAnimation();
  }

  void reset() {
    hintToast.setVisibility(INVISIBLE);
    actionResult.setVisibility(INVISIBLE);
    canvasView.removePainter(swipeAnimation);
  }

  /** View for drawing with canvas. */
  static class CanvasView extends View implements OrientationSensitive {
    /** Painter drawing on this canvas view. */
    interface Painter {
      void onDraw(Canvas canvas, Size canvasSize);
    }

    private final boolean isTabletop;
    private final List<Painter> painters = new ArrayList<>();
    private Size canvasSize;
    private int orientation;

    private CanvasView(Context context, int orientation, Size screenSize, boolean isTabletop) {
      super(context);
      this.orientation = orientation;
      this.isTabletop = isTabletop;
      this.canvasSize = calculateCanvasSize(screenSize);
    }

    private Size getCanvasSize() {
      return canvasSize;
    }

    private void addPainter(Painter painter) {
      if (!painters.contains(painter)) {
        painters.add(painter);
      }
    }

    private void removePainter(Painter painter) {
      painters.remove(painter);
    }

    @Override
    public void onOrientationChanged(int orientation, Size screenSize) {
      this.orientation = orientation;
      this.canvasSize = calculateCanvasSize(screenSize);
      invalidate();
      requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
      canvas.save();
      if (BrailleUtils.isPhoneSizedDevice(getResources())) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          canvas.rotate(isTabletop ? TutorialView.ROTATION_90 : TutorialView.ROTATION_270);
          canvas.translate(
              /* dx= */ isTabletop ? 0 : -canvasSize.getWidth(),
              /* dy= */ isTabletop ? -canvasSize.getHeight() : 0);
        } else {
          canvas.rotate(isTabletop ? TutorialView.ROTATION_180 : TutorialView.ROTATION_0);
          canvas.translate(
              /* dx= */ isTabletop ? -canvasSize.getWidth() : 0,
              /* dy= */ isTabletop ? -canvasSize.getHeight() : 0);
        }
      }

      for (Painter painter : painters) {
        painter.onDraw(canvas, canvasSize);
      }
      canvas.restore();
    }

    private Size calculateCanvasSize(Size screenSize) {
      if (BrailleUtils.isPhoneSizedDevice(getResources())
          && orientation == Configuration.ORIENTATION_PORTRAIT) {
        screenSize =
            new Size(/* width= */ screenSize.getHeight(), /* height= */ screenSize.getWidth());
      }
      return screenSize;
    }
  }

  /** Draws the swipe instruction animation in the middle of screen. */
  static class SwipeAnimation implements Painter {
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

    private void updatePaint(int fingerCount, Size canvasSize, Direction swipeDirection) {
      this.direction = swipeDirection;
      float canvasLength = 0;
      switch (swipeDirection) {
        case TOP_TO_BOTTOM:
        case BOTTOM_TO_TOP:
          canvasLength = canvasSize.getHeight() * SCALE_FACTOR;
          break;
        case LEFT_TO_RIGHT:
        case RIGHT_TO_LEFT:
          canvasLength = canvasSize.getWidth() * SCALE_FACTOR;
          break;
      }
      updateGesturesCoordinates(fingerCount, (int) canvasLength, canvasSize, swipeDirection);
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
        int fingerCount, int canvasLength, Size canvasSize, Direction swipeDirection) {
      gesturesCircleCoordinates.clear();
      int gestureInterval = DISTANCE_BETWEEN_GESTURES_IN_PIXELS + 2 * dotRadiusInPixels;
      int distanceToStartPoint = (fingerCount - 1) * gestureInterval / 2;
      for (int i = 0; i < fingerCount; i++) {
        int x = 0;
        int y = 0;
        switch (swipeDirection) {
          case TOP_TO_BOTTOM:
            x = canvasSize.getWidth() / 2 - distanceToStartPoint + i * gestureInterval;
            y = (canvasSize.getHeight() - canvasLength) / 2;
            break;
          case BOTTOM_TO_TOP:
            x = canvasSize.getWidth() / 2 - distanceToStartPoint + i * gestureInterval;
            y = (canvasSize.getHeight() - canvasLength) / 2 + canvasLength;
            break;
          case LEFT_TO_RIGHT:
            x = (canvasSize.getWidth() - canvasLength) / 2;
            y = canvasSize.getHeight() / 2 - distanceToStartPoint + i * gestureInterval;
            break;
          case RIGHT_TO_LEFT:
            x = (canvasSize.getWidth() - canvasLength) / 2 + canvasLength;
            y = canvasSize.getHeight() / 2 - distanceToStartPoint + i * gestureInterval;
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

    @Override
    public void onDraw(Canvas canvas, Size canvasSize) {
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
