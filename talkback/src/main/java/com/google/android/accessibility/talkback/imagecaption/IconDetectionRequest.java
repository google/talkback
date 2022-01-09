/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.android.accessibility.talkback.imagecaption;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.screenunderstanding.ScreenAnnotationsDetector;
import java.util.Locale;

/**
 * A {@link CaptionRequest} for detecting screen annotations (including icons) from the screenshot.
 */
public class IconDetectionRequest extends CaptionRequest
    implements ScreenAnnotationsDetector.ProcessScreenshotResultListener {

  private final ScreenAnnotationsDetector screenAnnotationsDetector;
  private final Bitmap screenCapture;
  private final Locale locale;

  public IconDetectionRequest(
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull Bitmap screenCapture,
      @NonNull ScreenAnnotationsDetector screenAnnotationsDetector,
      @NonNull Locale locale,
      @NonNull OnFinishListener onFinishListener,
      @NonNull OnErrorListener onErrorListener) {
    super(node, onFinishListener, onErrorListener);
    this.screenCapture = screenCapture;
    this.screenAnnotationsDetector = screenAnnotationsDetector;
    this.locale = locale;
  }

  @Override
  public void perform() {
    screenAnnotationsDetector.processScreenshotAsync(screenCapture, this);
    runTimeoutRunnable();
  }

  @Override
  public void onDetectionFinished(boolean success) {
    stopTimeoutRunnable();
    if (!success) {
      onError(ERROR_ICON_DETECTION_NO_RESULT);
      return;
    }

    onCaptionFinish(screenAnnotationsDetector.getIconLabel(locale, node));
  }
}
