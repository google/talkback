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

import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.ICON_LABEL;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.caption.Result;
import com.google.android.accessibility.utils.screenunderstanding.IconAnnotationsDetector;
import java.util.Locale;

/**
 * A {@link CaptionRequest} for detecting screen annotations (including icons) from the screenshot.
 */
public class IconDetectionRequest extends CaptionRequest
    implements IconAnnotationsDetector.ProcessScreenshotResultListener {

  private final IconAnnotationsDetector iconAnnotationsDetector;
  private final Bitmap screenCapture;
  private final Locale locale;

  public IconDetectionRequest(
      int requestId,
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull Bitmap screenCapture,
      @NonNull IconAnnotationsDetector iconAnnotationsDetector,
      @NonNull Locale locale,
      @NonNull OnFinishListener onFinishListener,
      @NonNull OnErrorListener onErrorListener,
      boolean isUserRequested) {
    super(requestId, node, onFinishListener, onErrorListener, isUserRequested);
    this.screenCapture = screenCapture;
    this.iconAnnotationsDetector = iconAnnotationsDetector;
    this.locale = locale;
  }

  @Override
  public void perform() {
    onCaptionStart();
    iconAnnotationsDetector.processScreenshotAsync(screenCapture, this);
    runTimeoutRunnable();
  }

  @Override
  public void onDetectionFinished(boolean success) {
    stopTimeoutRunnable();
    if (!success) {
      onError(ERROR_ICON_DETECTION_NO_RESULT);
      return;
    }

    onCaptionFinish(Result.create(ICON_LABEL, iconAnnotationsDetector.getIconLabel(locale, node)));
  }
}
