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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.caption.Result;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.time.Duration;

/**
 * An image caption action. Subclass should implement how to perform image caption and define a
 * {@link OnFinishListener} to handle the result of image caption for Talkback.
 *
 * <p>When the image caption is finished, subclasses should call {@link
 * CaptionRequest#onCaptionFinish(Result)} (String) or {@link CaptionRequest#onError(int)} to notify
 * the request is completed. Otherwise, The request will be cancelled if it isn't finished within
 * {@link CaptionRequest#CAPTION_TIMEOUT_MS}.
 */
public abstract class CaptionRequest extends Request {

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

  /** Maximal caption request execution time. */
  public static final Duration CAPTION_TIMEOUT_MS = Duration.ofMillis(10000);

  private static final String TAG = "CaptionRequest";

  /** An unique ID for the request. */
  private final int requestId;

  @NonNull protected final AccessibilityNodeInfoCompat node;

  @NonNull private final OnFinishListener onFinishListener;
  @NonNull private final OnErrorListener onErrorListener;

  private final boolean isUserRequested;

  protected CaptionRequest(
      int requestId,
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull OnFinishListener onFinishListener,
      @NonNull OnErrorListener onErrorListener,
      boolean isUserRequested) {
    super(/* onPendingListener= */ null, CAPTION_TIMEOUT_MS);
    this.requestId = requestId;
    this.node = AccessibilityNodeInfoCompat.obtain(node);
    this.onFinishListener = onFinishListener;
    this.onErrorListener = onErrorListener;
    this.isUserRequested = isUserRequested;
  }

  public int getRequestId() {
    return requestId;
  }

  /** Performs the image caption. */
  @Override
  public abstract void perform();

  public boolean isUserRequested() {
    return isUserRequested;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "= "
        + StringBuilderUtils.joinFields(StringBuilderUtils.optionalSubObj("node", node));
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

  @Override
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
}
