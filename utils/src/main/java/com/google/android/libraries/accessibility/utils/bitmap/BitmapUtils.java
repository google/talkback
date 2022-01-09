package com.google.android.libraries.accessibility.utils.bitmap;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.annotation.Nullable;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Utility methods for handling common operations on {@link Bitmap}s */
public final class BitmapUtils {

  private static final String TAG = "BitmapUtils";

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
  @Nullable
  public static Bitmap cropBitmap(Bitmap bitmap, Rect cropRegion) {
    Rect bitmapRegion = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

    // Just in case cropRegion extends past the bitmap, fit bitmapRegion down to cropRegion size.
    // Additionally if they don't intersect, return null
    if (cropRegion.isEmpty() || (!bitmapRegion.intersect(cropRegion))) {
      return null;
    }

    return Bitmap.createBitmap(
        bitmap, bitmapRegion.left, bitmapRegion.top, bitmapRegion.width(), bitmapRegion.height());
  }

  /**
   * Returns a cropped {@link Bitmap} for the specified, rectangular region of pixels from the
   * source bitmap.
   *
   * <p>The source bitmap is unaffected by this operation.
   *
   * @param bitmap The source bitmap to crop.
   * @param left The leftmost coordinate to include in the cropped image
   * @param top The toptmost coordinate to include in the cropped image
   * @param width The width of the cropped image
   * @param height The height of the cropped image
   * @return A new bitmap of the cropped area, or {@code null} if the crop parameters were out of
   *     bounds.
   */
  @Nullable
  public static Bitmap cropBitmap(Bitmap bitmap, int left, int top, int width, int height) {
    try {
      return Bitmap.createBitmap(bitmap, left, top, width, height);
    } catch (IllegalArgumentException ex) {
      LogUtils.e(TAG, ex, "Cropping arguments out of bounds");
      return null;
    }
  }
}
