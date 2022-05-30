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

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.imagecaption.RequestList.Request;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * An image caption action. Subclass should implement how to perform image caption and define a
 * {@link OnFinishListener} to handle the result of image caption for Talkback.
 *
 * <p>When the image caption is finished, subclasses should call {@link
 * CaptionRequest#onCaptionFinish(CharSequence)} (String) or {@link CaptionRequest#onError(int)} to
 * notify the request is completed. Otherwise, The request will be cancelled if it isn't finished
 * within {@link CaptionRequest#CAPTION_TIMEOUT_MS}.
 */
public abstract class CaptionRequest implements Request {

  /** A listener to be invoked when the image caption is finished. */
  public interface OnFinishListener {
    /**
     * Called when the image caption is finished.
     *
     * @param node caption is finished for this node.
     * @param result ocr result
     * @param isUserRequested return true if the user asks the request
     */
    void onCaptionFinish(
        AccessibilityNode node, @Nullable CharSequence result, boolean isUserRequested);
  }

  /** A listener to be invoked when the image caption is failed. */
  public interface OnErrorListener {
    /** Called when the image caption ends in failure. */
    void onError(AccessibilityNode node, @ErrorCode int errorCode, boolean isUserRequested);
  }

  /** The reasons of image captions. */
  @IntDef({ERROR_IMAGE_CAPTION_NO_RESULT, ERROR_ICON_DETECTION_NO_RESULT, ERROR_TIMEOUT})
  public @interface ErrorCode {}

  public static final int ERROR_IMAGE_CAPTION_NO_RESULT = 0;
  public static final int ERROR_ICON_DETECTION_NO_RESULT = 1;
  public static final int ERROR_TIMEOUT = 2;

  /** Maximal caption request execution time. */
  public static final int CAPTION_TIMEOUT_MS = 10000;

  private static final String TAG = "CaptionRequest";

  @NonNull protected final AccessibilityNodeInfoCompat node;

  @NonNull private final OnFinishListener onFinishListener;
  @NonNull private final OnErrorListener onErrorListener;
  private final Handler handler;
  private final Runnable timeoutRunnable;

  private final boolean isUserRequested;

  protected CaptionRequest(
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull OnFinishListener onFinishListener,
      @NonNull OnErrorListener onErrorListener,
      boolean isUserRequested) {
    this.node = AccessibilityNodeInfoCompat.obtain(node);
    this.onFinishListener = onFinishListener;
    this.onErrorListener = onErrorListener;
    this.isUserRequested = isUserRequested;
    handler = new Handler(Looper.myLooper());
    timeoutRunnable =
        () -> {
          LogUtils.e(TAG, "CaptionRequest timeout is reached. " + this);
          onErrorListener.onError(
              AccessibilityNode.obtainCopy(node), ERROR_TIMEOUT, isUserRequested);
        };
  }

  /** Performs the image caption. */
  @Override
  public abstract void perform();

  public boolean isUserRequested() {
    return isUserRequested;
  }

  protected void runTimeoutRunnable() {
    handler.postDelayed(timeoutRunnable, CAPTION_TIMEOUT_MS);
  }

  protected void stopTimeoutRunnable() {
    handler.removeCallbacks(timeoutRunnable);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "= "
        + StringBuilderUtils.joinFields(StringBuilderUtils.optionalSubObj("node", node));
  }

  public static String errorName(@ErrorCode int errorCode) {
    switch (errorCode) {
      case ERROR_IMAGE_CAPTION_NO_RESULT:
        return "ERROR_IMAGE_CAPTION_NO_RESULT";
      case ERROR_ICON_DETECTION_NO_RESULT:
        return "ERROR_ICON_DETECTION_NO_RESULT";
      case ERROR_TIMEOUT:
        return "ERROR_TIMEOUT";
      default:
        return "";
    }
  }

  protected void onCaptionFinish(@Nullable CharSequence result) {
    LogUtils.v(
        TAG,
        "onCaptionFinish() "
            + StringBuilderUtils.joinFields(
                StringBuilderUtils.optionalSubObj("node", node),
                StringBuilderUtils.optionalText("ocrText", result)));
    onFinishListener.onCaptionFinish(AccessibilityNode.obtainCopy(node), result, isUserRequested);
  }

  protected void onError(@ErrorCode int errorCode) {
    stopTimeoutRunnable();
    LogUtils.e(TAG, "onError() error= %s", errorName(errorCode));
    onErrorListener.onError(AccessibilityNode.obtainCopy(node), errorCode, isUserRequested);
  }
}
