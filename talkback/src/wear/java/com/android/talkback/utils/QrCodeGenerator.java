/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/** A generator for QR code images, using zxing project. */
public final class QrCodeGenerator {
  /**
   * Generates a barcode image with {@code contents}.
   *
   * @param contents the contents to encode in the barcode
   * @param size the preferred image size in pixels
   * @param invert Whether to invert the black/white pixels (e.g. for dark mode)
   * @return null if an exception is thrown, the bitmap of encoding a qrcode otherwise
   */
  public static Bitmap encodeQrCode(String contents, int size, boolean invert)
      throws WriterException {
    BitMatrix bitMatrix =
        new MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, size, size);
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
    int setColor = invert ? Color.WHITE : Color.BLACK;
    int unsetColor = invert ? Color.BLACK : Color.WHITE;
    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size; y++) {
        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? setColor : unsetColor);
      }
    }
    return bitmap;
  }

  private QrCodeGenerator() {}
}
