/*
 * Copyright (C) 2024 Google Inc.
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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.time.Duration;
import java.time.Instant;

/** An action of image captioning. */
public abstract class Request {

  /** A listener to be invoked when the request has been suspended. */
  public interface OnPendingListener {
    /**
     * Called when the request has been suspended.
     *
     * @param scheduled {@code true} if the request is pending and will be performed later; {@code
     *     false} if the request can't be scheduled and won't be performed.
     * @param intervalTime the time between the request and the previous request.
     */
    void onPending(boolean scheduled, Duration intervalTime);
  }

  private static final String TAG = "ImageCaptionRequest";

  /** Represents the duration is invalid. */
  public static final int INVALID_DURATION = -1;

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
  private final Handler handler;
  private final Runnable timeoutRunnable;
  @Nullable private final OnPendingListener onPendingListener;
  @Nullable private final Duration timeout;
  @Nullable private Instant startTimestamp;
  @Nullable private Instant endTimestamp;

  @VisibleForTesting
  Request() {
    this(/* onPendingListener= */ null, /* timeout= */ null);
  }

  public Request(@Nullable OnPendingListener onPendingListener, @Nullable Duration timeout) {
    this.onPendingListener = onPendingListener;
    this.timeout = timeout;
    handler = new Handler(Looper.myLooper());
    timeoutRunnable =
        () -> {
          LogUtils.e(TAG, "CaptionRequest timeout is reached. " + this);
          onError(ERROR_TIMEOUT);
        };
  }

  public long getDurationMillis() {
    if (startTimestamp == null || endTimestamp == null) {
      return INVALID_DURATION;
    }
    return Duration.between(startTimestamp, endTimestamp).toMillis();
  }

  @Nullable
  public Instant getEndTimestamp() {
    return endTimestamp;
  }

  /** Starts the action. */
  protected abstract void perform();

  /** Invokes when the request is failed. */
  protected abstract void onError(int errorCode);

  protected void onPending(boolean result, Duration intervalTime) {
    if (onPendingListener == null) {
      return;
    }
    onPendingListener.onPending(result, intervalTime);
  }

  /** Sets the time at which the caption request started to perform. */
  protected void setStartTimestamp() {
    startTimestamp = Instant.now();
  }

  /** Sets the time at which the caption request is done. */
  protected void setEndTimestamp() {
    endTimestamp = Instant.now();
  }

  protected void runTimeoutRunnable() {
    if (timeout == null) {
      LogUtils.w(TAG, "runTimeoutRunnable() with invalid timeout.");
      return;
    }

    handler.postDelayed(timeoutRunnable, timeout.toMillis());
  }

  protected void stopTimeoutRunnable() {
    handler.removeCallbacks(timeoutRunnable);
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
}
