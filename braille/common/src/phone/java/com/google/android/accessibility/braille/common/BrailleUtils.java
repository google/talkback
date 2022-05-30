package com.google.android.accessibility.braille.common;


import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.Size;

/** Utils calss for braille common. */
public class BrailleUtils {
  private BrailleUtils() {}

  /** Returns true if device is a phone sized device. */
  public static boolean isPhoneSizedDevice(Resources resources) {
    return resources.getBoolean(R.bool.is_phone_sized);
  }

  /**
   * Transforms the given {@code point} from portrait coordinates to landscape coordinates for
   * phones, given the {@param Size} of the portrait (source) region and the {@param Size} of desire
   * landscape region.
   *
   * <p>This can be used to keep visual elements in the relative physical location across a change
   * of orientation.
   */
  public static PointF mapPortraitToLandscapeForPhone(
      PointF point, Size portraitScreenSize, Size landscapeScreenSize) {
    Matrix matrix = new Matrix();
    matrix.preRotate(90f);
    matrix.postTranslate(landscapeScreenSize.getWidth(), 0);
    matrix.preScale(
        (float) landscapeScreenSize.getHeight() / portraitScreenSize.getWidth(),
        (float) landscapeScreenSize.getWidth() / portraitScreenSize.getHeight());
    float[] dst = new float[2];
    float[] src = {point.x, point.y};
    matrix.mapPoints(dst, 0, src, 0, 1);
    return new PointF(dst[0], dst[1]);
  }

  /**
   * Transforms the given {@code point} from landscape coordinates to portrait coordinates, given
   * the {@param Size} of the landscape (source) region and the {@param Size} of desire portrait
   * region.
   *
   * <p>This can be used to keep visual elements in the relative physical location across a change
   * of orientation.
   */
  public static PointF mapLandscapeToPortraitForPhone(
      PointF point, Size landscapeScreenSize, Size portraitScreenSize) {
    Matrix matrix = new Matrix();
    matrix.postRotate(-90f);
    matrix.postTranslate(0, portraitScreenSize.getHeight());
    matrix.preScale(
        (float) portraitScreenSize.getHeight() / landscapeScreenSize.getWidth(),
        (float) portraitScreenSize.getWidth() / landscapeScreenSize.getHeight());
    float[] dst = new float[2];
    float[] src = {point.x, point.y};
    matrix.mapPoints(dst, 0, src, 0, 1);
    return new PointF(dst[0], dst[1]);
  }
}
