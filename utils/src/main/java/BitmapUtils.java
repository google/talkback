package com.google.android.accessibility.utils;

import android.graphics.Bitmap;
import android.graphics.Rect;

/** Utility methods for handling common operations on {@link Bitmap}s */
public final class BitmapUtils {
  private BitmapUtils() {}

  /**
   * Return a cropped {@link Bitmap} given a certain {@link Rect} cropping region.
   *
   * @param bitmap The {@link Bitmap} to be cropped to the size of the {@code cropRegion}.
   * @param cropRegion The {@link Rect} representing the desired crop area with respect to the
   *     {@code bitmap}. Note that if the cropRegion extends outside the bitmap's area, the crop
   *     will happen at the overlap of the crop area and the bitmap's area (assuming they overlap).
   * @return A new {@link Bitmap} containing the cropped image, or {@code null} if the crop region
   *     does not intersect the bitmap's region (e.g. the cropRegion is of size 0, or it is
   *     completely outside of the Bitmap).
   */
  public static Bitmap cropBitmap(Bitmap bitmap, Rect cropRegion) {
    Rect bitmapRegion = new Rect(0, 0, bitmap.getWidth() - 1, bitmap.getHeight() - 1);

    // Just in case cropRegion extends past the bitmap, fit bitmapRegion down to cropRegion size.
    // Additionally if they don't intersect, return null
    if (!bitmapRegion.intersect(cropRegion)) {
      return null;
    }

    return Bitmap.createBitmap(
        bitmap, bitmapRegion.left, bitmapRegion.top, bitmapRegion.width(), bitmapRegion.height());
  }
}
