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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import androidx.core.view.ViewCompat;
import com.google.android.accessibility.braille.common.BrailleUtils;
import com.google.android.accessibility.braille.common.Constants.BrailleType;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleIme.OrientationSensitive;
import com.google.android.accessibility.brailleime.BrailleInputOptions;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.input.BrailleInputPlane.CustomOnGestureListener;
import com.google.android.accessibility.brailleime.input.BrailleInputPlane.DotTarget;
import com.google.android.accessibility.brailleime.input.MultitouchHandler.HoldRecognizer;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.List;
import java.util.Optional;

/**
 * View that displays braille dots and handles braille input and gestures. Itself doesn't
 * automatically save dot regions. Please call {@link #savePoints} to save them.
 *
 * <p>Important signals, such as the commission of input, are fed through the {@link Callback},
 * which is a required parameter for the constructor.
 *
 * <p>We do not provide a constructor with {@link android.util.AttributeSet} parameter because we
 * have no need for it and we have a {@link Callback} that we need passed in during construction.
 */
@SuppressWarnings("ViewConstructor")
public class BrailleInputView extends View
    implements OrientationSensitive, OnAttachStateChangeListener {

  /** A callback for receiving signals from BrailleInputView. */
  public interface Callback {

    /**
     * Signals that {@link Swipe} input has been produced. Returns true if the action is consumed.
     */
    @CanIgnoreReturnValue
    boolean onSwipeProduced(Swipe swipe);

    /**
     * Signals that hold and dot swipe input has been produced. Returns true if the action is
     * consumed.
     */
    @CanIgnoreReturnValue
    boolean onDotHoldAndDotSwipe(Swipe swipe, BrailleCharacter heldBrailleCharacter);

    /**
     * Allows the client to state which potential hold events it cares about. If the client returns
     * true, the BrailleInputView will invoke onHoldProduced, and then it (along with the input
     * chain with which it interfaces) will halt subsequent callbacks until the user releases (lifts
     * up) the press.
     */
    @CanIgnoreReturnValue
    boolean isCalibrationHoldRecognized(boolean inTwoStepCalibration, int pointersHeldCount);

    /** Signals that hold has been produced. Returns true if the action is consumed. */
    @CanIgnoreReturnValue
    boolean onHoldProduced(int pointersHeldCount);

    /**
     * Signals that {@link BrailleCharacter} input has been produced.
     *
     * @return the client's notion of the latest update to the print text so that this View can
     *     render it (for low-vision users).
     */
    String onBrailleProduced(BrailleCharacter brailleCharacter);

    /** Signals that calibration has been produced. Returns true if the action is consumed. */
    @CanIgnoreReturnValue
    boolean onCalibration(CalibrationTriggeredType type, FingersPattern hand);

    /** Signals that calibration has failed. */
    void onCalibrationFailed(CalibrationTriggeredType calibration);

    /** Signals that two step calibration needs retry. */
    void onTwoStepCalibrationRetry(boolean isFirstStep);
  }

  /** Indicates which finger pattern. */
  public enum FingersPattern {
    NO_FINGERS,
    SIX_FINGERS,
    SEVEN_FINGERS,
    EIGHT_FINGERS,
    FIVE_FINGERS,
    FIRST_THREE_FINGERS,
    REMAINING_THREE_FINGERS,
    FIRST_FOUR_FINGERS,
    REMAINING_FOUR_FINGERS,
  }

  /** Calibration triggered types. */
  public enum CalibrationTriggeredType {
    FIVE_FINGERS,
    SIX_FINGERS,
    SEVEN_FINGERS,
    EIGHT_FINGERS,
    MANUAL
  }

  private static final String TAG = "BrailleInputView";
  private static final boolean DRAW_DEBUG_BACKGROUND = false; // Leave as false in version control.

  private final Callback callback;
  private final InputViewCaption inputViewCaption;
  private final BrailleInputPlane inputPlane;
  private CaptionText captionText;
  private Size screenSizeInPixels;
  private int orientation;
  private boolean isTableMode;
  private AutoPerformer autoPerformer;
  private BrailleInputOptions options;
  private boolean touchInteracting;
  private CalibrationTriggeredType calibrationType;

  /**
   * Construct a BrailleInputView.
   *
   * <p>We do not provide a constructor with {@link android.util.AttributeSet} because we have no
   * need for it and we have a Callback that we need passed in during construction.
   */
  public BrailleInputView(
      Context context, Callback callback, Size screenSizeInPixels, BrailleInputOptions options) {
    super(context);
    this.callback = callback;
    this.screenSizeInPixels = screenSizeInPixels;
    this.orientation = getResources().getConfiguration().orientation;
    this.options = options;
    this.inputPlane = getInputPlane(context);
    setBackgroundColor(getResources().getColor(R.color.input_plane_background));
    this.inputViewCaption = new InputViewCaption(context.getString(R.string.input_view_caption));
    addOnAttachStateChangeListener(this);
  }

  private BrailleInputPlane getInputPlane(Context context) {
    HoldRecognizer holdRecognizer =
        new HoldRecognizer() {
          @Override
          public boolean isCalibrationHoldRecognized(int pointersHeldCount) {
            return callback.isCalibrationHoldRecognized(
                inputPlane.inTwoStepCalibration(), pointersHeldCount);
          }

          @Override
          public boolean isHoldRecognized(int pointersHeldCount) {
            return !inputPlane.inTwoStepCalibration() && pointersHeldCount <= 3;
          }
        };
    return BrailleUtils.isPhoneSizedDevice(context.getResources())
        ? new BrailleInputPlanePhone(
            context,
            screenSizeInPixels,
            holdRecognizer,
            orientation,
            options,
            customOnGestureListener)
        : new BrailleInputPlaneTablet(
            context,
            screenSizeInPixels,
            holdRecognizer,
            orientation,
            options,
            customOnGestureListener);
  }

  @Override
  public void onOrientationChanged(int orientation, Size screenSize) {
    this.orientation = orientation;
    this.screenSizeInPixels = screenSize;
    this.inputPlane.setOrientation(orientation, screenSizeInPixels);
    invalidate();
    requestLayout();
  }

  @Override
  public void onViewAttachedToWindow(View view) {}

  @Override
  public void onViewDetachedFromWindow(View view) {
    if (inTwoStepCalibration()) {
      callback.onCalibrationFailed(calibrationType);
    }
    removeOnAttachStateChangeListener(this);
  }

  public void setOptions(BrailleInputOptions options) {
    this.options = options;
    inputPlane.setOptions(options);
    invalidate();
    requestLayout();
  }

  /** Sets keyboard to table layout. */
  public void setTableMode(boolean enabled) {
    if (isTableMode != enabled) {
      inputPlane.setTableTopMode(enabled);
      isTableMode = enabled;
      invalidate();
      requestLayout();
    }
  }

  /** Stores points positions in the SharedPreference. */
  public void savePoints() {
    inputPlane.storeLayoutPoints();
  }

  /**
   * Sets the accumulation mode.
   *
   * <p>An accumulation mode set to true means that all press-and-releases contribute to a potential
   * commission even if the release occurred far before the release of the final touch point.
   */
  public void setAccumulationMode(boolean accumulationMode) {
    inputPlane.setAccumulationMode(accumulationMode);
  }

  /** Gets the accumulation mode. */
  public boolean isAccumulationMode() {
    return inputPlane.isAccumulationMode();
  }

  /** Returns whether in the process of two step calibration. */
  public boolean inTwoStepCalibration() {
    return inputPlane.inTwoStepCalibration();
  }

  public List<DotTarget> getDotTargets() {
    return inputPlane.getDotTargets();
  }

  /** Gets braille input view dot count. */
  public int getBrailleDotCount() {
    return options.brailleType().getDotCount();
  }
  /** Boolean value for whether a finger is currently touching the braille input view. */
  public boolean isTouchInteracting() {
    return touchInteracting;
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed) {
      reduceSystemGestureArea();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (DRAW_DEBUG_BACKGROUND) {
      drawDebugBackground(canvas);
    }
    inputPlane.onDraw(canvas);
    if (captionText != null) {
      captionText.onDraw(canvas);
    }
    inputViewCaption.onDraw(canvas);
  }

  private final CustomOnGestureListener customOnGestureListener =
      new CustomOnGestureListener() {
        @Override
        public boolean detect(Optional<BrailleInputPlaneResult> resultOptional) {
          if (resultOptional.isPresent()) {
            if (shouldPerformCalibrationAnimation(
                resultOptional.get().type, resultOptional.get().pointersHeldCount)) {
              inputPlane.createAnimator(BrailleInputView.this);
            }
            boolean result = processResult(resultOptional.get());
            invalidate();
            return result;
          }
          return false;
        }

        @Override
        public void onTwoStepCalibrationFailed() {
          callback.onCalibrationFailed(calibrationType);
          invalidate();
        }

        @Override
        public void onTwoStepCalibrationRetry(boolean isFirstStep) {
          callback.onTwoStepCalibrationRetry(isFirstStep);
        }
      };

  @Override
  @SuppressWarnings("ClickableViewAccessibility")
  public boolean onTouchEvent(MotionEvent event) {
    updateTouchAction(event);
    boolean result = inputPlane.onTouchEvent(event);
    invalidate();
    return result;
  }

  /** Calibrates dots positions. */
  public void calibrateByTwoSteps() {
    inputPlane.calibrateByTwoSteps();
    calibrationType = CalibrationTriggeredType.MANUAL;
    callback.onCalibration(CalibrationTriggeredType.MANUAL, FingersPattern.NO_FINGERS);
    invalidate();
  }

  private void updateTouchAction(MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        touchInteracting = true;
        break;
      case MotionEvent.ACTION_UP:
        touchInteracting = false;
        break;
      default: // fall out
    }
  }

  private boolean shouldPerformCalibrationAnimation(int type, int heldCount) {
    if (type != BrailleInputPlaneResult.TYPE_CALIBRATION) {
      return false;
    }
    if (options.brailleType() == BrailleType.EIGHT_DOT) {
      return heldCount == BrailleType.EIGHT_DOT.getDotCount()
          || heldCount == BrailleType.EIGHT_DOT.getDotCount() / 2;
    } else {
      return heldCount == BrailleType.SIX_DOT.getDotCount()
          || heldCount == BrailleType.SIX_DOT.getDotCount() / 2;
    }
  }

  @CanIgnoreReturnValue
  private boolean processResult(BrailleInputPlaneResult result) {
    if (result.type == MultitouchResult.TYPE_TAP) {
      String newAddition = callback.onBrailleProduced(result.releasedBrailleCharacter);
      if (newAddition != null) {
        if (captionText != null) {
          captionText.animator.cancel();
        }
        captionText = new CaptionText(newAddition);
        captionText.animator.start();
      }
    } else if (result.type == MultitouchResult.TYPE_SWIPE) {
      boolean processed = callback.onSwipeProduced(result.swipe);
      if (Utils.isDebugBuild()) {
        if (result.swipe.getTouchCount() == 5) {
          if (autoPerformer == null) {
            this.autoPerformer = new AutoPerformer(getContext(), new AutoPerformerCallback());
            this.autoPerformer.start();
          }
        }
      }
      return processed;
    } else if (result.type == BrailleInputPlaneResult.TYPE_CALIBRATION) {
      if (result.pointersHeldCount == 5) {
        calibrationType = CalibrationTriggeredType.FIVE_FINGERS;
        return callback.onCalibration(calibrationType, FingersPattern.FIVE_FINGERS);
      } else if (result.pointersHeldCount == 6) {
        calibrationType = CalibrationTriggeredType.SIX_FINGERS;
        return callback.onCalibration(calibrationType, FingersPattern.SIX_FINGERS);
      } else if (result.pointersHeldCount == 7) {
        calibrationType = CalibrationTriggeredType.SEVEN_FINGERS;
        return callback.onCalibration(calibrationType, FingersPattern.SEVEN_FINGERS);
      } else if (result.pointersHeldCount == 8) {
        calibrationType = CalibrationTriggeredType.EIGHT_FINGERS;
        return callback.onCalibration(calibrationType, FingersPattern.EIGHT_FINGERS);
      } else if (result.pointersHeldCount == 3) {
        return callback.onCalibration(
            calibrationType,
            result.isLeft
                ? FingersPattern.FIRST_THREE_FINGERS
                : FingersPattern.REMAINING_THREE_FINGERS);
      } else if (result.pointersHeldCount == 4) {
        return callback.onCalibration(
            calibrationType,
            result.isLeft
                ? FingersPattern.FIRST_FOUR_FINGERS
                : FingersPattern.REMAINING_FOUR_FINGERS);
      } else {
        callback.onCalibrationFailed(calibrationType);
      }
    } else if (result.type == BrailleInputPlaneResult.TYPE_HOLD) {
      return callback.onHoldProduced(result.pointersHeldCount);
    } else if (result.type == BrailleInputPlaneResult.TYPE_HOLD_AND_SWIPE) {
      return callback.onDotHoldAndDotSwipe(result.swipe, result.heldBrailleCharacter);
    }
    return false;
  }

  private void drawDebugBackground(Canvas canvas) {
    // Drawing the debug background helps expose problems with coordinates and screensize.
    Paint paint = new Paint();
    paint.setColor(getResources().getColor(R.color.input_plane_debug_background));
    int backgroundInset =
        getResources().getDimensionPixelSize(R.dimen.input_plane_debug_background_inset);
    RectF rect =
        new RectF(
            backgroundInset,
            backgroundInset,
            screenSizeInPixels.getWidth() - backgroundInset,
            screenSizeInPixels.getHeight() - backgroundInset);
    canvas.drawRect(rect, paint);
  }

  private void reduceSystemGestureArea() {
    // Reduce the possibility of triggering system-level gestures; note that the exclusion zone
    // is restricted to 200dp, and is counted from the bottom edge upward.
    Rect boundingBox = new Rect();
    boundingBox.set(getLeft(), getTop(), getRight(), getBottom());
    List<Rect> exclusions = ImmutableList.of(boundingBox);
    ViewCompat.setSystemGestureExclusionRects(this, exclusions);
  }

  /** Draws the print translation of the most recently inputted text (for low-vision users). */
  private class CaptionText {
    private final String text;
    private final ValueAnimator animator;
    private final Paint textPaint;
    private static final int ANIMATION_DURATION_MS = 400;

    private CaptionText(String text) {
      this.text = text;
      Resources resources = getResources();
      animator = ValueAnimator.ofFloat(1f, 0f);
      animator.setDuration(ANIMATION_DURATION_MS);
      animator.addListener(
          new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
              BrailleInputView.this.captionText = null;
            }
          });
      animator.addUpdateListener(valueAnimator -> BrailleInputView.this.invalidate());

      textPaint = new Paint();
      textPaint.setColor(resources.getColor(R.color.input_plane_most_recent_animation));
      textPaint.setTextAlign(Paint.Align.CENTER);
      textPaint.setTextSize(
          resources.getDimensionPixelSize(R.dimen.input_view_most_recent_text_size));
      textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    private void onDraw(Canvas canvas) {
      int alpha = (int) (((Float) animator.getAnimatedValue()) * 255);
      textPaint.setAlpha(alpha);

      Rect textBounds = new Rect();
      textPaint.getTextBounds(text, 0, text.length(), textBounds);
      PointF textPoint = inputPlane.getCaptionCenterPoint(screenSizeInPixels);
      canvas.save();
      canvas.rotate(inputPlane.getRotateDegree(), textPoint.x, textPoint.y);
      canvas.drawText(text, textPoint.x, textPoint.y, textPaint);
      canvas.restore();
    }
  }

  /** Draws the caption text on the bottom-center of the screen. */
  private class InputViewCaption {
    private final String text;
    private final Paint textPaint;
    private final int captionBottomMarginInPixels;

    private InputViewCaption(String text) {
      this.text = text;
      Resources resources = getResources();
      textPaint = new Paint();
      textPaint.setColor(resources.getColor(R.color.input_plane_caption));
      textPaint.setTextAlign(Paint.Align.CENTER);
      textPaint.setTextSize(resources.getDimensionPixelSize(R.dimen.input_view_caption_text_size));
      textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
      textPaint.setTypeface(
          Typeface.create(getContext().getString(com.google.android.accessibility.utils.R.string.accessibility_font), Typeface.NORMAL));
      captionBottomMarginInPixels =
          resources.getDimensionPixelOffset(R.dimen.input_view_caption_bottom_margin);
    }

    private void onDraw(Canvas canvas) {
      Rect textBounds = new Rect();
      canvas.save();
      textPaint.getTextBounds(text, 0, text.length(), textBounds);
      Size screenSize = inputPlane.getInputViewCaptionScreenSize(screenSizeInPixels);
      canvas.rotate(inputPlane.getRotateDegree());
      int[] dxy = inputPlane.getInputViewCaptionTranslate(screenSize);
      canvas.translate(/* dx= */ dxy[0], /* dy= */ dxy[1]);
      drawText(canvas, screenSize);
      canvas.restore();
    }

    private void drawText(Canvas canvas, Size screenSize) {
      float textX = screenSize.getWidth() / 2.0f;
      float textY = screenSize.getHeight() - captionBottomMarginInPixels;
      canvas.drawText(text, textX, textY, textPaint);
    }
  }

  private class AutoPerformerCallback implements AutoPerformer.Callback {

    @Override
    public void onPerform(BrailleInputPlaneResult bipr) {
      processResult(bipr);
    }

    @Override
    public BrailleInputPlaneResult createSwipe(Swipe swipe) {
      return inputPlane.createSwipe(swipe);
    }

    @Override
    public void onFinish() {
      autoPerformer = null;
    }
  }
}
