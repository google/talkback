package com.google.android.accessibility.brailleime.input;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.util.Size;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleImeLog;
import com.google.android.accessibility.brailleime.BrailleInputOptions;
import com.google.android.accessibility.brailleime.input.MultitouchHandler.HoldRecognizer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/** {@link BrailleInputPlane} for tablet. */
public class BrailleInputPlaneTablet extends BrailleInputPlane {
  private static final String TAG = "BrailleInputPlaneTablet";
  /**
   * Constructs a BrailleInputPlane.
   *
   * <p>The orientation argument should be one of {@link Configuration#ORIENTATION_LANDSCAPE} or
   * {@link Configuration#ORIENTATION_PORTRAIT}, depending on the current orientation of the device.
   */
  BrailleInputPlaneTablet(
      Context context,
      Size sizeInPixels,
      HoldRecognizer holdRecognizer,
      int orientation,
      BrailleInputOptions options,
      CustomOnGestureListener customGestureDetector) {
    super(context, sizeInPixels, holdRecognizer, orientation, options, customGestureDetector);
  }

  @Override
  List<PointF> buildDotTargetCenters(Size screenSize) {
    // First we build the centers of the DotTargets in landscape coordinates (even if in
    // portrait).
    return buildDotTargetCentersLandscape(screenSize);
  }

  @Override
  void sortDotCentersFirstTime(List<PointF> dotCenters) {
    dotCenters.sort(
        (o1, o2) -> isTableTopMode ? Float.compare(o1.x, o2.x) : Float.compare(o2.x, o1.x));
  }

  @Override
  void sortDotCentersByGroup(List<PointF> group, boolean isFirstGroup) {
    group.sort(
        (o1, o2) -> {
          int result;
          if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            result = isTableTopMode ? Float.compare(o1.x, o2.x) : Float.compare(o1.y, o2.y);
            if (result != 0) {
              return result;
            }
            if (isTableTopMode) {
              result = isFirstGroup ? Float.compare(o1.y, o2.y) : Float.compare(o2.y, o1.y);
            } else {
              result = isFirstGroup ? Float.compare(o1.x, o2.x) : Float.compare(o2.y, o1.y);
            }
          } else {
            result = isTableTopMode ? Float.compare(o1.x, o2.x) : Float.compare(o1.y, o2.y);
            if (result != 0) {
              return result;
            }
            if (isTableTopMode) {
              result = isFirstGroup ? Float.compare(o1.y, o2.y) : Float.compare(o2.y, o1.y);
            } else {
              result = Float.compare(o1.x, o2.x);
            }
          }
          return result;
        });
  }

  @Override
  BrailleInputPlaneResult createSwipe(Swipe swipe) {
    return BrailleInputPlaneResult.createSwipeForTablet(swipe);
  }

  @Override
  BrailleInputPlaneResult createDotHoldAndSwipe(
      Swipe swipe, BrailleCharacter heldBrailleCharacter) {
    BrailleInputPlaneResult result = BrailleInputPlaneResult.createSwipeForTablet(swipe);
    return BrailleInputPlaneResult.createDotHoldAndDotSwipe(result.swipe, heldBrailleCharacter);
  }

  @Override
  int getRotateDegree() {
    return 0;
  }

  @Override
  PointF getCaptionCenterPoint(Size screenSize) {
    PointF pointF = new PointF();
    pointF.x = screenSize.getWidth() / 2.0f;
    pointF.y = isTableTopMode ? screenSize.getHeight() / 3.0f : screenSize.getHeight() / 2.0f;
    return pointF;
  }

  @Override
  int[] getInputViewCaptionTranslate(Size screenSize) {
    return new int[2];
  }

  @Override
  Size getInputViewCaptionScreenSize(Size screenSize) {
    return screenSize;
  }

  @Override
  List<PointF> readLayoutPoints(Size screenSize) {
    try {
      return BrailleUserPreferences.readCalibrationPointsTablet(context, orientation);
    } catch (ParseException e) {
      BrailleImeLog.logE(TAG, "Read saved dots failed.", e);
      return new ArrayList<>();
    }
  }

  @Override
  void writeLayoutPoints(List<PointF> centerPoints, Size screenSize) {
    try {
      BrailleUserPreferences.writeCalibrationPointsTablet(
          context, orientation, centerPoints, screenSize);
    } catch (ParseException e) {
      BrailleImeLog.logE(TAG, "Write points failed.");
    }
  }
}
