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

package com.google.android.accessibility.talkback.imagecaption;

import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.IMAGE_DESCRIPTION;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.imagedescription.ImageDescriptionInfo;
import com.google.android.accessibility.talkback.imagedescription.ImageDescriptionListener;
import com.google.android.accessibility.talkback.imagedescription.ImageDescriptionProcessor;
import com.google.android.accessibility.utils.caption.Result;
import com.google.android.libraries.accessibility.utils.bitmap.BitmapUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** A {@link CaptionRequest} to describe the contents of an image on the screenshot. */
public class ImageDescriptionRequest extends CaptionRequest implements ImageDescriptionListener {

  private static final String TAG = "ImageDescriptionRequest";
  private final Context context;
  private final ImageDescriptionProcessor imageDescriptionProcessor;
  private final Bitmap screenCapture;

  public ImageDescriptionRequest(
      int requestId,
      Context context,
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull Bitmap screenCapture,
      @NonNull ImageDescriptionProcessor imageDescriptionProcessor,
      @NonNull OnFinishListener onFinishListener,
      @NonNull OnErrorListener onErrorListener,
      boolean isUserRequested) {
    super(requestId, node, onFinishListener, onErrorListener, isUserRequested);
    this.context = context;
    this.imageDescriptionProcessor = imageDescriptionProcessor;

    Rect nodeBounds = new Rect();
    node.getBoundsInScreen(nodeBounds);
    Bitmap croppedBitmap = null;
    try {
      croppedBitmap = BitmapUtils.cropBitmap(screenCapture, nodeBounds);
    } catch (IllegalArgumentException e) {
      LogUtils.w(TAG, e.getMessage() == null ? "Fail to crop screenshot." : e.getMessage());
    }

    this.screenCapture = (croppedBitmap == null) ? screenCapture : croppedBitmap;
  }

  @Override
  public void perform() {
    onCaptionStart();
    imageDescriptionProcessor.caption(screenCapture, this);
    runTimeoutRunnable();
  }

  @Override
  public void onSuccess(ImageDescriptionInfo imageDescriptionInfo) {
    stopTimeoutRunnable();
    onCaptionFinish(
        Result.create(
            IMAGE_DESCRIPTION,
            ImageDescriptionInfo.getCaptionText(context, imageDescriptionInfo),
            imageDescriptionInfo.captionQualityScore()));
    LogUtils.d(TAG, "onSuccess=" + imageDescriptionInfo);
  }

  @Override
  public void onFailure(@ErrorCode int errorCode) {
    stopTimeoutRunnable();
    onError(errorCode);
  }
}
