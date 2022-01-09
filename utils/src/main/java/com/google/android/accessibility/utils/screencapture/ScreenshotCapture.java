/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.accessibility.utils.screencapture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.hardware.HardwareBuffer;
import android.view.Display;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.libraries.accessibility.utils.screencapture.ScreenCaptureController.CaptureListener;

/**
 * The utility class supports to take screenshot by native {@link
 * AccessibilityService#takeScreenshot} API. It's not applicable for the platform before Android R.
 */
public class ScreenshotCapture {
  private static final String TAG = "ScreenshotCapture";

  /** Prevent from instance creation for this utility class. */
  private ScreenshotCapture() {
    throw new AssertionError();
  }

  /**
   * Method to take screenshot with native support method.
   *
   * @param service The Accessibility service which has already been granted this feature by
   *     android:canTakeScreenshot="true" in accessibility-service xml.
   * @param listener Call back when got a result; success/failure depends on the screenCapture
   *     argument.
   */
  public static void takeScreenshot(AccessibilityService service, CaptureListener listener) {
    if (!FeatureSupport.canTakeScreenShotByAccessibilityService()) {
      LogUtils.e(TAG, "Taking screenshot but platform's not support");
      listener.onScreenCaptureFinished(/* screenCapture= */ null, /* isFormatSupported= */ false);
      return;
    }
    service.takeScreenshot(
        Display.DEFAULT_DISPLAY,
        service.getMainExecutor(),
        new TakeScreenshotCallback() {
          @Override
          public void onFailure(int errorCode) {
            LogUtils.e(TAG, "Taking screenshot but failed [error:" + errorCode + "]");
            listener.onScreenCaptureFinished(
                /* screenCapture= */ null, /* isFormatSupported= */ false);
          }

          @Override
          public void onSuccess(ScreenshotResult screenshot) {
            Bitmap bitmap;
            try (HardwareBuffer hardwareBuffer = screenshot.getHardwareBuffer()) {
              bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.getColorSpace());
            }

            if (bitmap != null) {
              Bitmap bitmapCopy =
                  bitmap.copy(Config.ARGB_8888, /* isMutable= */ bitmap.isMutable());
              bitmap.recycle();
              bitmap = bitmapCopy;
            }

            listener.onScreenCaptureFinished(bitmap, /* isFormatSupported= */ bitmap != null);
          }
        });
  }
}
