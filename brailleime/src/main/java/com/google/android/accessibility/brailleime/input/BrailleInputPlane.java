/*
 * Copyright 2019 Google Inc.
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

package com.google.android.accessibility.brailleime.input;

import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.TwoStepCalibrationState.NONE;
import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.TwoStepCalibrationState.STEP1;
import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.TwoStepCalibrationState.STEP2;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.input.MultitouchHandler.HoldRecognizer;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders visual braille dots, along with their pressed state, and processes touch input for them
 * (for use in BrailleInputView).
 *
 * <p>BrailleInputPlane holds a two-dimensional array of dot targets. Generally speaking, each
 * DotTarget serves as the centroid of polygonal region that is associated with specific dot number
 * (such as Dot 1). The union of these disjoint regions covers a rectangular region (which could be
 * the entire display, for example).
 *
 * <p>The primary methods are {@link BrailleInputPlane#onDraw}, which handles the rendering, and
 * {@link BrailleInputPlane#onTouchEvent(MotionEvent)}, which handles touch input and returns a
 * {@link BrailleInputPlaneResult} in case commission occurs.
 */
public abstract class BrailleInputPlane {
  static final String TAG = "BrailleInputPlane";
  public static final int DOT_COUNT = 6;
  public static final int GROUP_COUNT = 2;
  public static final int NUMBER_OF_COLUMNS_SCREEN_AWAY = 2;
  public static final int NUMBER_OF_COLUMNS_TABLETOP = DOT_COUNT;
  public static final int NUMBER_OF_ROWS_TABLETOP = 1;
  public static final int NUMBER_OF_ROWS_SCREEN_AWAY = DOT_COUNT / NUMBER_OF_COLUMNS_SCREEN_AWAY;
  private static final int ANIMATION_DURATION_MS = 100;
  private static final boolean DOT_MIGRATION_FEATURE = false;

  private final Resources resources;
  private final MultitouchHandler multitouchHandler;
  private final int dotRadius;
  boolean isTableTopMode;
  private final Paint dotBackgroundPaint;
  @ColorInt private final int dotBackgroundColorDefault;
  @ColorInt private final int dotBackgroundColorDefaultCalibration;
  @ColorInt private final int dotBackgroundColorPressedDefault;
  @ColorInt private final int dotBackgroundColorPressedCalibration;

  private final Paint dotNumberPaint;
  @ColorInt private final int dotNumberColorDefaultCalibration;
  @ColorInt private final int dotNumberColorDefault;
  @ColorInt private final int dotNumberColorPressedDefault;
  @ColorInt private final int dotNumberColorPressedCalibration;
  private final boolean reverseDots;
  private final Paint touchCirclesPaint;
  private final int textBaseline;

  private Set<DotTarget> currentlyPressedDots;
  private List<DotTarget> oldDotTargets;
  private List<DotTarget> dotTargets;
  int orientation;
  private Size sizeInPixels;
  final Context context;
  private final boolean isTutorial;
  private PointF[] textPosition;
  private PointF[] dotCenterPosition;
  private TwoStepCalibrationState twoStepCalibrationState = NONE;

  /** state of two step calibration. */
  enum TwoStepCalibrationState {
    NONE,
    STEP1, // If reverse dot, it's right 3 fingers hold; otherwise, left 3 fingers hold.
    STEP2, // If reverse dot, it's left 3 fingers hold; otherwise, right 3 fingers hold.
  }

  /**
   * Constructs a BrailleInputPlane.
   *
   * <p>The orientation argument should be one of {@link Configuration#ORIENTATION_LANDSCAPE} or
   * {@link Configuration#ORIENTATION_PORTRAIT}, depending on the current orientation of the device.
   */
  BrailleInputPlane(
      Context context,
      Size sizeInPixels,
      int orientation,
      boolean reverseDots,
      HoldRecognizer holdRecognizer,
      boolean isTutorial) {
    this.resources = context.getResources();
    this.multitouchHandler = new MultitouchHandler(resources, holdRecognizer);
    this.orientation = orientation;
    this.sizeInPixels = sizeInPixels;
    this.reverseDots = reverseDots;
    this.context = context;
    this.isTutorial = isTutorial;
    dotRadius = resources.getDimensionPixelSize(R.dimen.input_plane_dot_radius);
    dotNumberPaint = new Paint();
    dotNumberPaint.setTextAlign(Paint.Align.CENTER);
    float scaleFactor =
        Utils.getResourcesFloat(resources, R.dimen.input_plane_dot_number_size_multiplier);
    dotNumberPaint.setTextSize(scaleFactor * dotRadius);
    float textStrokeWidth = resources.getDimension(R.dimen.input_plane_dot_number_stroke_width);
    dotNumberPaint.setStrokeWidth(textStrokeWidth);
    dotNumberPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    textBaseline = Utils.getPaintTextBaselineInPixels(dotNumberPaint);
    dotTargets = buildDotTargets(sizeInPixels);
    currentlyPressedDots = new HashSet<>();

    dotBackgroundPaint = new Paint();
    dotBackgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    int background = context.getColor(R.color.input_plane_background);
    dotBackgroundColorDefault = resources.getColor(R.color.input_plane_dot_background_default);
    dotBackgroundColorPressedDefault =
        resources.getColor(R.color.input_plane_dot_background_pressed);
    dotBackgroundColorDefaultCalibration =
        Utils.averageColors(dotBackgroundColorDefault, background);
    dotBackgroundColorPressedCalibration =
        Utils.averageColors(dotBackgroundColorPressedDefault, background);

    dotNumberColorDefault = resources.getColor(R.color.input_plane_dot_number_default);
    dotNumberColorPressedDefault = resources.getColor(R.color.input_plane_dot_number_pressed);
    dotNumberColorPressedCalibration =
        Utils.averageColors(dotNumberColorPressedDefault, background);
    dotNumberColorDefaultCalibration = Utils.averageColors(dotNumberColorDefault, background);

    touchCirclesPaint = new Paint();
    touchCirclesPaint.setColor(resources.getColor(R.color.input_plane_touch_circle));
    touchCirclesPaint.setStyle(Paint.Style.STROKE);
    touchCirclesPaint.setStrokeWidth(
        resources.getDimension(R.dimen.input_plane_touch_circle_stroke_width));
  }

  /** Reads saved points from SharedPreference. */
  abstract List<PointF> readLayoutPoints(Size screenSize);

  /** Writes points to SharedPreference. */
  abstract void writeLayoutPoints(List<PointF> centerPoints, Size size);

  /** Creates default dot points. */
  abstract List<PointF> buildDotTargetCenters(Size screenSize);

  /** Sorts dot points to divide them into two groups. */
  abstract void sortDotCentersFirstTime(List<PointF> dotCenters);

  /** Sorts grouped dot points. */
  abstract void sortDotCentersByGroup(List<PointF> group, boolean isFirstGroup);

  /** Recreates swipe result to match expectation */
  abstract BrailleInputPlaneResult createSwipe(Swipe swipe);

  abstract int getRotateDegree();

  abstract PointF getCaptionCenterPoint(Size screenSize);

  abstract int[] getInputViewCaptionTranslate(Size screenSize);

  abstract Size getInputViewCaptionScreenSize(Size screenSize);

  public void setTableTopMode(boolean isTableTopMode) {
    this.isTableTopMode = isTableTopMode;
    dotTargets = buildDotTargets(sizeInPixels);
    // Cancel calibration.
    twoStepCalibrationState = NONE;
  }

  public List<DotTarget> getDotTargets() {
    return Collections.unmodifiableList(ImmutableList.copyOf(dotTargets));
  }

  private List<DotTarget> buildCalibratedDotTargets(Collection<PointF> points) {
    List<DotTarget> dotTargets = new ArrayList<>();
    List<PointF> pointList = new ArrayList<>(points);
    sortDotCenters(pointList);
    for (int i = 0; i < pointList.size(); i++) {
      dotTargets.add(new DotTarget(getDotNumber(i), pointList.get(i).x, pointList.get(i).y));
    }
    return dotTargets;
  }

  @VisibleForTesting
  List<DotTarget> buildDotTargets(Size screenSize) {
    List<PointF> dotCenters = new ArrayList<>();
    if (!isTutorial) {
      dotCenters = readLayoutPoints(screenSize);
    }
    if (dotCenters.isEmpty()) {
      dotCenters = buildDotTargetCenters(screenSize);
    }
    sortDotCenters(dotCenters);

    // Now we create the DotTarget.
    List<DotTarget> dotTargets = new ArrayList<>();
    for (int i = 0; i < dotCenters.size(); i++) {
      PointF center = dotCenters.get(i);
      dotTargets.add(new DotTarget(getDotNumber(i), center.x, center.y));
    }
    updateDotPosition(dotTargets);
    return dotTargets;
  }

  private void updateDotPosition(List<DotTarget> dotTargets) {
    dotCenterPosition = new PointF[DOT_COUNT];
    textPosition = new PointF[DOT_COUNT];
    for (int i = 0; i < DOT_COUNT; i++) {
      dotCenterPosition[i] = new PointF(dotTargets.get(i).center.x, dotTargets.get(i).center.y);
      textPosition[i] =
          new PointF(dotTargets.get(i).center.x, dotTargets.get(i).center.y + textBaseline);
    }
  }

  private int getDotNumber(int i) {
    return isTableTopMode
        ? getTableTopModeDotNumber(i)
        : (reverseDots ? (i + DOT_COUNT / GROUP_COUNT) % DOT_COUNT : i) + 1;
  }

  private int getTableTopModeDotNumber(int i) {
    return reverseDots
        ? /*654123*/ i / (DOT_COUNT / GROUP_COUNT) > 0
            ? (i - DOT_COUNT / GROUP_COUNT) + 1
            : DOT_COUNT - i
        : /*321456*/ i / (DOT_COUNT / GROUP_COUNT) > 0
            ? i + 1
            : (i + DOT_COUNT / GROUP_COUNT) % DOT_COUNT - (i * DOT_COUNT / GROUP_COUNT - i);
  }

  @VisibleForTesting
  void sortDotCenters(List<PointF> dotCenters) {
    // Sort first time to decide rough position.
    sortDotCentersFirstTime(dotCenters);
    // Sort second time to deal with same vertical/horizontal position dots.
    List<PointF> copy = new ArrayList<>(dotCenters);
    dotCenters.clear();
    for (int i = 0; i < GROUP_COUNT; i++) {
      List<PointF> group =
          new ArrayList<>(
              copy.subList(i * DOT_COUNT / GROUP_COUNT, DOT_COUNT / GROUP_COUNT * (i + 1)));
      sortDotCentersByGroup(group, i == 0);
      dotCenters.addAll(group);
    }
  }

  List<PointF> buildDotTargetCentersLandscape(Size screenSize) {
    List<PointF> dotCenters = new ArrayList<>();
    int width = screenSize.getWidth();
    int height = screenSize.getHeight();
    int dotDiameter = 2 * dotRadius;
    int columnCount = isTableTopMode ? NUMBER_OF_COLUMNS_TABLETOP : NUMBER_OF_COLUMNS_SCREEN_AWAY;
    int rowCount = isTableTopMode ? NUMBER_OF_ROWS_TABLETOP : NUMBER_OF_ROWS_SCREEN_AWAY;

    // Variables rS and cS are rowSpacer and columnSpacer, respectively.
    float rS = ((float) height - rowCount * dotDiameter) / (rowCount + 1);
    float cS = ((float) width - columnCount * dotDiameter) / (columnCount + 1);

    // Build the center point of each dot as if we are in landscape mode.
    for (int column = 0; column < columnCount; column++) {
      float centerX = width - (cS + dotRadius + column * (dotDiameter + cS));
      for (int row = 0; row < rowCount; row++) {
        float centerY = rS + dotRadius + row * (dotDiameter + rS);
        dotCenters.add(new PointF(centerX, centerY));
      }
    }
    return dotCenters;
  }

  /**
   * Sets orientation of BrailleInputPlane
   *
   * @param orientation the orientation of the view
   * @param screenSize used as the height and width of the canvas
   */
  public void setOrientation(int orientation, Size screenSize) {
    this.orientation = orientation;
    this.sizeInPixels = screenSize;
    dotTargets = buildDotTargets(screenSize);
    // Cancel calibration.
    twoStepCalibrationState = NONE;
  }

  /** Set the accumulation mode. See {@link MultitouchHandler#setAccumulationMode(boolean)}. */
  void setAccumulationMode(boolean accumulationMode) {
    multitouchHandler.setAccumulationMode(accumulationMode);
  }

  /** Get the accumulation mode. See {@link MultitouchHandler#isAccumulationMode()}. */
  boolean isAccumulationMode() {
    return multitouchHandler.isAccumulationMode();
  }

  /**
   * Process touch input and return a {@link BrailleInputPlaneResult} if commission occurred.
   *
   * @param event the MotionEvent as received by the owning View's onTouchEvent() method.
   */
  Optional<BrailleInputPlaneResult> onTouchEvent(MotionEvent event) {
    Optional<MultitouchResult> touchResultOptional = multitouchHandler.onTouchEvent(event);
    // Update the dots state even if we received an empty result.
    currentlyPressedDots = matchTouchToTargets(multitouchHandler.getActivePoints());
    if (!touchResultOptional.isPresent()) {
      return Optional.empty();
    }
    return processMultitouchResult(touchResultOptional.get());
  }

  private Optional<BrailleInputPlaneResult> processMultitouchResult(MultitouchResult touchResult) {
    if (twoStepCalibrationState != NONE) {
      if (touchResult.type != MultitouchResult.TYPE_HOLD || touchResult.pointersHeldCount != 3) {
        dotTargets = buildDotTargets(sizeInPixels);
        twoStepCalibrationState = NONE;
        return Optional.of(
            BrailleInputPlaneResult.createCalibration(
                /* isLeft= */ false, /* pointersHeldCount= */ 0));
      }
    }
    switch (touchResult.type) {
      case MultitouchResult.TYPE_TAP:
        if (DOT_MIGRATION_FEATURE && !isTutorial) {
          doMigration(touchResult.points);
        }
        Set<Integer> committedDotNumbers = matchTouchToTargetNumbers(touchResult.points);
        BrailleCharacter brailleCharacter = new BrailleCharacter(committedDotNumbers);
        return Optional.of(BrailleInputPlaneResult.createTapAndRelease(brailleCharacter));
      case MultitouchResult.TYPE_SWIPE:
        Swipe swipe = touchResult.swipe;
        return Optional.of(createSwipe(swipe));
      case MultitouchResult.TYPE_HOLD:
        if (twoStepCalibrationState != NONE) {
          Optional<BrailleInputPlaneResult> result =
              Optional.of(
                  BrailleInputPlaneResult.createCalibration(
                      twoStepCalibrationState == STEP1, touchResult.pointersHeldCount));
          doTwoStepCalibration(touchResult.points);
          return result;
        } else if (touchResult.pointersHeldCount == 5 || touchResult.pointersHeldCount == 6) {
          Optional<BrailleInputPlaneResult> result =
              Optional.of(
                  BrailleInputPlaneResult.createCalibration(false, touchResult.pointersHeldCount));
          doCalibration(touchResult.points);
          return result;
        }
        // fall through
      default:
        return Optional.empty();
    }
  }

  /**
   * Draw on to the given {@link Canvas}
   *
   * @param canvas the Canvas as received by the owning View.
   */
  void onDraw(Canvas canvas) {
    drawDots(canvas);
    drawTouchCircles(canvas, multitouchHandler.getActivePoints());
  }

  /**
   * Figure which dots are pressed by the given points.
   *
   * <p>We iterate through the touch points and at each iteration identify, and remove the point
   * after saving an association link between that point at the dot closest to it.
   */
  private Set<DotTarget> matchTouchToTargets(Collection<PointF> points) {
    Set<DotTarget> result = new HashSet<>();
    Set<PointF> pointsYetToBeProcessed = new HashSet<>(points);
    Set<DotTarget> dotsYetToBeProcessed = new HashSet<>(dotTargets);
    while (!pointsYetToBeProcessed.isEmpty() && !dotsYetToBeProcessed.isEmpty()) {
      double bestDistance = Double.MAX_VALUE;
      DotTarget bestDot = null;
      PointF bestPoint = null;
      for (DotTarget dot : dotsYetToBeProcessed) {
        for (PointF point : pointsYetToBeProcessed) {
          double thisDistance = Utils.distance(dot.center, point);
          if (thisDistance < bestDistance) {
            bestDistance = thisDistance;
            bestDot = dot;
            bestPoint = point;
          }
        }
      }
      result.add(new DotTarget(bestDot.dotNumber, bestPoint.x, bestPoint.y));
      pointsYetToBeProcessed.remove(bestPoint);
      dotsYetToBeProcessed.remove(bestDot);
    }
    return result;
  }

  /** Figure which dot numbers are pressed by the given points. */
  private Set<Integer> matchTouchToTargetNumbers(Collection<PointF> points) {
    return matchTouchToTargets(points).stream()
        .map(dotTarget -> dotTarget.dotNumber)
        .collect(Collectors.toSet());
  }

  private void drawDots(Canvas canvas) {
    for (int i = 0; i < dotTargets.size(); i++) {
      boolean pressed = false;
      for (DotTarget dotTarget : currentlyPressedDots) {
        pressed = dotTarget.dotNumber == dotTargets.get(i).dotNumber;
        if (pressed) {
          break;
        }
      }
      boolean useDefaultColor =
          twoStepCalibrationState == NONE
              || (twoStepCalibrationState == STEP2
                  && (reverseDots ? i >= DOT_COUNT / GROUP_COUNT : i < DOT_COUNT / GROUP_COUNT));
      // Draw dot background.
      int dotBackgroundColor =
          useDefaultColor ? dotBackgroundColorDefault : dotBackgroundColorDefaultCalibration;
      int dotBackgroundColorPressed =
          useDefaultColor ? dotBackgroundColorPressedDefault : dotBackgroundColorPressedCalibration;
      dotBackgroundPaint.setColor(pressed ? dotBackgroundColorPressed : dotBackgroundColor);
      canvas.drawCircle(
          dotCenterPosition[i].x, dotCenterPosition[i].y, dotRadius, dotBackgroundPaint);
      int dotNumberColor =
          useDefaultColor ? dotNumberColorDefault : dotNumberColorDefaultCalibration;
      int dotNumberColorPressed =
          useDefaultColor ? dotNumberColorPressedDefault : dotNumberColorPressedCalibration;
      // Draw dot number (text).
      dotNumberPaint.setColor(pressed ? dotNumberColorPressed : dotNumberColor);
      String text = Integer.toString(dotTargets.get(i).dotNumber);
      canvas.save();
      if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        canvas.rotate(getRotateDegree(), dotCenterPosition[i].x, dotCenterPosition[i].y);
      } else {
        canvas.rotate(getRotateDegree(), dotCenterPosition[i].x, dotCenterPosition[i].y);
      }
      canvas.drawText(text, textPosition[i].x, textPosition[i].y, dotNumberPaint);
      canvas.restore();
    }
  }

  public void createAnimator(View view) {
    if (isTutorial) {
      return;
    }
    dotCenterPosition = new PointF[DOT_COUNT];
    textPosition = new PointF[DOT_COUNT];
    for (int i = 0; i < DOT_COUNT; i++) {
      dotCenterPosition[i] = new PointF();
      textPosition[i] = new PointF();
    }
    AnimatorSet animatorSet = new AnimatorSet();
    List<Animator> animatorList = new ArrayList<>();
    for (int i = 0; i < dotTargets.size(); i++) {
      PropertyValuesHolder propertyX =
          PropertyValuesHolder.ofFloat(
              "ScaleX", oldDotTargets.get(i).center.x, dotTargets.get(i).center.x);
      PropertyValuesHolder propertyY =
          PropertyValuesHolder.ofFloat(
              "ScaleY", oldDotTargets.get(i).center.y, dotTargets.get(i).center.y);
      PropertyValuesHolder propertyAlpha = PropertyValuesHolder.ofInt("alpha", 0, 255);
      ValueAnimator animator = new ValueAnimator();
      animator.setValues(propertyX, propertyY, propertyAlpha);
      animator.setDuration(ANIMATION_DURATION_MS);
      animator.setInterpolator(new LinearInterpolator());
      int finalI = i;
      animator.addUpdateListener(
          valueAnimator -> {
            float xPosition = (Float) valueAnimator.getAnimatedValue("ScaleX");
            float yPosition = (Float) valueAnimator.getAnimatedValue("ScaleY");
            textPosition[finalI].x = xPosition;
            textPosition[finalI].y = yPosition + textBaseline;
            dotCenterPosition[finalI].x = xPosition;
            dotCenterPosition[finalI].y = yPosition;
            view.invalidate();
          });
      animatorList.add(animator);
    }
    animatorSet.playTogether(animatorList);
    animatorSet.start();
  }

  private void drawTouchCircles(Canvas canvas, Collection<PointF> points) {
    float scaleFactor =
        Utils.getResourcesFloat(resources, R.dimen.input_plane_touch_circle_size_multiplier);
    int touchCircleRadius = (int) (scaleFactor * dotRadius);
    for (PointF point : points) {
      canvas.drawCircle(point.x, point.y, touchCircleRadius, touchCirclesPaint);
    }
  }

  private void doMigration(List<PointF> points) {
    // Revise dot targets when fingers up.
    Set<DotTarget> matchedDotTarget = matchTouchToTargets(points);
    float[] deltaX = new float[GROUP_COUNT];
    float[] deltaY = new float[GROUP_COUNT];
    int[] count = new int[GROUP_COUNT];
    for (int i = 0; i < dotTargets.size(); i++) {
      DotTarget standard = dotTargets.get(i);
      for (DotTarget dotTarget : matchedDotTarget) {
        if (dotTarget.dotNumber == standard.dotNumber) {
          int index =
              (reverseDots ? i / (DOT_COUNT / GROUP_COUNT) < 1 : i / (DOT_COUNT / GROUP_COUNT) > 0)
                  ? 1
                  : 0;
          deltaX[index] += dotTarget.center.x - standard.center.x;
          deltaY[index] += dotTarget.center.y - standard.center.y;
          count[index]++;
        }
      }
    }
    for (int i = 0; i < dotTargets.size(); i++) {
      int index =
          (reverseDots ? i / (DOT_COUNT / GROUP_COUNT) < 1 : i / (DOT_COUNT / GROUP_COUNT) > 0)
              ? 1
              : 0;
      if (count[index] > 0) {
        dotTargets.get(i).center.x += deltaX[index] / count[index];
        dotTargets.get(i).center.y += deltaY[index] / count[index];
      }
    }
    updateDotPosition(dotTargets);
  }

  /** Stores points positions in the SharedPreference. */
  void storeLayoutPoints() {
    List<PointF> centerPoints = new ArrayList<>();
    for (DotTarget dotTarget : dotTargets) {
      centerPoints.add(dotTarget.center);
    }
    writeLayoutPoints(centerPoints, sizeInPixels);
  }

  private void doTwoStepCalibration(List<PointF> pointList) {
    if (!isTutorial) {
      sortDotCentersByGroup(pointList, twoStepCalibrationState == STEP1);
      oldDotTargets = new ArrayList<>(dotTargets);
      for (int i = 0; i < pointList.size(); i++) {
        int index =
            reverseDots
                ? (twoStepCalibrationState == STEP1 ? DOT_COUNT / GROUP_COUNT : 0) + i
                : (twoStepCalibrationState == STEP1 ? 0 : DOT_COUNT / GROUP_COUNT) + i;
        dotTargets.set(
            index, new DotTarget(getDotNumber(index), pointList.get(i).x, pointList.get(i).y));
      }
    }
    if (twoStepCalibrationState == STEP1) {
      twoStepCalibrationState = STEP2;
    } else if (twoStepCalibrationState == STEP2) {
      twoStepCalibrationState = NONE;
    }
  }

  private void doCalibration(List<PointF> holdPoints) {
    if (holdPoints.size() == 5) {
      twoStepCalibrationState = STEP1;
    } else if (holdPoints.size() == 6 && !isTutorial) {
      oldDotTargets = new ArrayList<>(dotTargets);
      dotTargets = buildCalibratedDotTargets(holdPoints);
    }
  }

  /** Contains dot coordinate and its dot number. */
  public static class DotTarget {
    private final PointF center;
    private final int dotNumber;

    private DotTarget(int dotNumber, float centerX, float centerY) {
      this.dotNumber = dotNumber;
      this.center = new PointF(centerX, centerY);
    }

    public PointF getCenter() {
      return new PointF(center.x, center.y);
    }

    public int getDotNumber() {
      return dotNumber;
    }
  }
}
