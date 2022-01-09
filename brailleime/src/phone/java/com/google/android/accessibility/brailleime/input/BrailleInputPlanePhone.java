package com.google.android.accessibility.brailleime.input;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.util.Size;
import com.google.android.accessibility.brailleime.BrailleImeLog;
import com.google.android.accessibility.brailleime.UserPreferences;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.input.MultitouchHandler.HoldRecognizer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/** {@link BrailleInputPlane} for phone. */
public class BrailleInputPlanePhone extends BrailleInputPlane {

  /**
   * Constructs a BrailleInputPlane.
   *
   * <p>The orientation argument should be one of {@link Configuration#ORIENTATION_LANDSCAPE} or
   * {@link Configuration#ORIENTATION_PORTRAIT}, depending on the current orientation of the device.
   */
  BrailleInputPlanePhone(
      Context context,
      Size sizeInPixels,
      int orientation,
      boolean reverseDots,
      HoldRecognizer holdRecognizer,
      boolean isTutorial) {
    super(context, sizeInPixels, orientation, reverseDots, holdRecognizer, isTutorial);
  }

  @Override
  List<PointF> readLayoutPoints(Size screenSize) {
    try {
      return UserPreferences.readCalibrationPointsPhone(
          context, isTableTopMode, orientation, screenSize);
    } catch (ParseException e) {
      BrailleImeLog.logE(TAG, "Read saved dots failed.", e);
      return new ArrayList<>();
    }
  }

  @Override
  void writeLayoutPoints(List<PointF> centerPoints, Size screenSize) {
    try {
      UserPreferences.writeCalibrationPointsPhone(
          context, isTableTopMode, orientation, centerPoints, screenSize);
    } catch (ParseException e) {
      BrailleImeLog.logE(TAG, "Write points failed.");
    }
  }

  @Override
  List<PointF> buildDotTargetCenters(Size screenSize) {
    // First we build the centers of the DotTargets in landscape coordinates (even if in
    // portrait).
    Size newScreenSize = screenSize;
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      newScreenSize =
          new Size(/* width= */ screenSize.getHeight(), /* height= */ screenSize.getWidth());
    }
    List<PointF> dotCenters = buildDotTargetCentersLandscape(newScreenSize);
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      for (int i = 0; i < dotCenters.size(); i++) {
        dotCenters.set(
            i, Utils.mapLandscapeToPortraitForPhone(dotCenters.get(i), newScreenSize, screenSize));
      }
    }
    return dotCenters;
  }

  @Override
  void sortDotCentersFirstTime(List<PointF> dotCenters) {
    dotCenters.sort(
        (o1, o2) -> {
          if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return isTableTopMode ? Float.compare(o2.y, o1.y) : Float.compare(o1.y, o2.y);
          } else {
            return isTableTopMode ? Float.compare(o1.x, o2.x) : Float.compare(o2.x, o1.x);
          }
        });
  }

  @Override
  void sortDotCentersByGroup(List<PointF> group, boolean isFirstGroup) {
    group.sort(
        (o1, o2) -> {
          int result;
          if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            result = isTableTopMode ? Float.compare(o2.y, o1.y) : Float.compare(o1.x, o2.x);
            if (result != 0) {
              return result;
            }
            if (isTableTopMode) {
              result = isFirstGroup ? Float.compare(o1.x, o2.x) : Float.compare(o2.x, o1.x);
            } else {
              result = Float.compare(o2.y, o1.y);
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
    return BrailleInputPlaneResult.createSwipeForPhone(swipe, orientation, isTableTopMode);
  }

  @Override
  int getRotateDegree() {
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return 270;
    } else {
      return 0;
    }
  }

  @Override
  PointF getCaptionCenterPoint(Size screenSize) {
    PointF pointF = new PointF();
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      pointF.x = isTableTopMode ? screenSize.getWidth() / 3.0f : screenSize.getWidth() / 2.0f;
      pointF.y = screenSize.getHeight() / 2.0f;
    } else {
      pointF.x = screenSize.getWidth() / 2.0f;
      pointF.y = isTableTopMode ? screenSize.getHeight() / 3.0f : screenSize.getHeight() / 2.0f;
    }
    return pointF;
  }

  @Override
  int[] getInputViewCaptionTranslate(Size screenSize) {
    int[] dxy = new int[2];
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      dxy[0] = -screenSize.getWidth();
      dxy[1] = 0;
    }
    return dxy;
  }

  @Override
  Size getInputViewCaptionScreenSize(Size screenSize) {
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      return new Size(/* width= */ screenSize.getHeight(), /* height= */ screenSize.getWidth());
    }
    return screenSize;
  }
}
