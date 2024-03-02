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
import com.google.android.accessibility.utils.caption.Result;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.time.Duration;
import java.time.Instant;

/**
 * An image caption action. Subclass should implement how to perform image caption and define a
 * {@link OnFinishListener} to handle the result of image caption for Talkback.
 *
 * <p>When the image caption is finished, subclasses should call {@link
 * CaptionRequest#onCaptionFinish(Result)} (String) or {@link CaptionRequest#onError(int)} to notify
 * the request is completed. Otherwise, The request will be cancelled if it isn't finished within
 * {@link CaptionRequest#CAPTION_TIMEOUT_MS}.
 */
public abstract class CaptionRequest implements Request {

  /** A listener to be invoked when the image caption is finished. */
  public interface OnFinishListener {
    /**
     * Called when the image caption is finished.
     *
     * @param request the request itself
     * @param node caption is finished for this node
     * @param result ocr result
     * @param isUserRequested return true if the user asks the request
     */
    void onCaptionFinish(
        CaptionRequest request,
        AccessibilityNode node,
        @Nullable Result result,
        boolean isUserRequested);
  }

  /** A listener to be invoked when the image caption is failed. */
  public interface OnErrorListener {
    /** Called when the image caption ends in failure. */
    void onError(
        CaptionRequest request,
        AccessibilityNode node,
        @ErrorCode int errorCode,
        boolean isUserRequested);
  }

  /** The reasons of image captions. */
  @IntDef({
    ERROR_UNKNOWN,
    ERROR_TIMEOUT,
    ERROR_NETWORK_ERROR,
    ERROR_INSUFFICIENT_STORAGE,
    ERROR_TEXT_RECOGNITION_NO_RESULT,
    ERROR_ICON_DETECTION_NO_RESULT,
    ERROR_IMAGE_DESCRIPTION_NO_RESULT,
    ERROR_IMAGE_DESCRIPTION_FAILURE,
    ERROR_IMAGE_DESCRIPTION_INITIALIZATION_FAILURE,
  })
  public @interface ErrorCode {}

  public static final int ERROR_UNKNOWN = 100;
  public static final int ERROR_TIMEOUT = 101;
  public static final int ERROR_NETWORK_ERROR = 102;
  public static final int ERROR_INSUFFICIENT_STORAGE = 103;
  // For text recognition.
  public static final int ERROR_TEXT_RECOGNITION_NO_RESULT = 200;
  // For icon detection.
  public static final int ERROR_ICON_DETECTION_NO_RESULT = 300;
  // For image description.
  public static final int ERROR_IMAGE_DESCRIPTION_NO_RESULT = 400;
  public static final int ERROR_IMAGE_DESCRIPTION_FAILURE = 401;
  public static final int ERROR_IMAGE_DESCRIPTION_INITIALIZATION_FAILURE = 402;

  /** Maximal caption request execution time. */
  public static final int CAPTION_TIMEOUT_MS = 10000;

  /** Represents the duration is invalid. */
  public static final int INVALID_DURATION = -1;

  private static final String TAG = "CaptionRequest";

  /** An unique ID for the request. */
  private final int requestId;

  @NonNull protected final AccessibilityNodeInfoCompat node;

  @NonNull private final OnFinishListener onFinishListener;
  @NonNull private final OnErrorListener onErrorListener;
  private final Handler handler;
  private final Runnable timeoutRunnable;

  private final boolean isUserRequested;
  @Nullable private Instant startTimestamp;
  @Nullable private Instant endTimestamp;

  protected CaptionRequest(
      int requestId,
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull OnFinishListener onFinishListener,
      @NonNull OnErrorListener onErrorListener,
      boolean isUserRequested) {
    this.requestId = requestId;
    this.node = AccessibilityNodeInfoCompat.obtain(node);
    this.onFinishListener = onFinishListener;
    this.onErrorListener = onErrorListener;
    this.isUserRequested = isUserRequested;
    handler = new Handler(Looper.myLooper());
    timeoutRunnable =
        () -> {
          LogUtils.e(TAG, "CaptionRequest timeout is reached. " + this);
          onErrorListener.onError(
              this, AccessibilityNode.takeOwnership(node), ERROR_TIMEOUT, isUserRequested);
        };
  }

  public int getRequestId() {
    return requestId;
  }

  public long getDurationMillis() {
    if (startTimestamp == null || endTimestamp == null) {
      return INVALID_DURATION;
    }
    return Duration.between(startTimestamp, endTimestamp).toMillis();
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
      case ERROR_UNKNOWN:
        return "ERROR_UNKNOWN";
      case ERROR_TIMEOUT:
        return "ERROR_TIMEOUT";
      case ERROR_NETWORK_ERROR:
        return "ERROR_NETWORK_ERROR";
      case ERROR_INSUFFICIENT_STORAGE:
        return "ERROR_INSUFFICIENT_STORAGE";
      case ERROR_TEXT_RECOGNITION_NO_RESULT:
        return "ERROR_TEXT_RECOGNITION_NO_RESULT";
      case ERROR_ICON_DETECTION_NO_RESULT:
        return "ERROR_ICON_DETECTION_NO_RESULT";
      case ERROR_IMAGE_DESCRIPTION_NO_RESULT:
        return "ERROR_IMAGE_DESCRIPTION_NO_RESULT";
      case ERROR_IMAGE_DESCRIPTION_FAILURE:
        return "ERROR_IMAGE_DESCRIPTION_FAILURE";
      case ERROR_IMAGE_DESCRIPTION_INITIALIZATION_FAILURE:
        return "ERROR_IMAGE_DESCRIPTION_INITIALIZATION_FAILURE";
      default:
        return "";
    }
  }

  protected void onCaptionStart() {
    LogUtils.v(TAG, "onCaptionStart() name=\"%s\"", getClass().getSimpleName());
    setStartTimestamp();
  }

  protected void onCaptionFinish(Result result) {
    setEndTimestamp();
    LogUtils.v(
        TAG,
        "onCaptionFinish() "
            + StringBuilderUtils.joinFields(
                StringBuilderUtils.optionalText("name", getClass().getSimpleName()),
                StringBuilderUtils.optionalInt("time", getDurationMillis(), /* defaultValue= */ 0),
                StringBuilderUtils.optionalSubObj("result", result),
                StringBuilderUtils.optionalSubObj("node", node)));
    onFinishListener.onCaptionFinish(
        this, AccessibilityNode.takeOwnership(node), result, isUserRequested);
  }

  protected void onError(@ErrorCode int errorCode) {
    setEndTimestamp();
    stopTimeoutRunnable();
    LogUtils.e(
        TAG,
        "onError() "
            + StringBuilderUtils.joinFields(
                StringBuilderUtils.optionalText("name", getClass().getSimpleName()),
                StringBuilderUtils.optionalText("error", errorName(errorCode))));
    onErrorListener.onError(
        this, AccessibilityNode.takeOwnership(node), errorCode, isUserRequested);
  }

  /** Sets the time at which the caption request started to perform. */
  private void setStartTimestamp() {
    startTimestamp = Instant.now();
  }

  /** Sets the time at which the caption request is done. */
  private void setEndTimestamp() {
    endTimestamp = Instant.now();
  }
}
