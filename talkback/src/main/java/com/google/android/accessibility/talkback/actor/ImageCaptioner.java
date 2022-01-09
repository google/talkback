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

package com.google.android.accessibility.talkback.actor;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.imagecaption.CaptionRequest;
import com.google.android.accessibility.talkback.imagecaption.CharacterCaptionRequest;
import com.google.android.accessibility.talkback.imagecaption.RequestList;
import com.google.android.accessibility.talkback.imagecaption.ScreenshotCaptureRequest;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.caption.ImageCaptionStorage;
import com.google.android.accessibility.utils.caption.ImageNode;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Performs image caption and manages related state. */
public class ImageCaptioner {

  private static final String TAG = "ImageCaptioner";
  public static final int CAPTION_REQUEST_CAPACITY = 10;

  private final AccessibilityService service;
  private Pipeline.FeedbackReturner pipeline;
  private ActorState actorState;
  private final ImageCaptionStorage imageCaptionStorage;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  private final RequestList<ScreenshotCaptureRequest> screenshotRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);
  private final RequestList<CharacterCaptionRequest> characterCaptionRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);

  public ImageCaptioner(
      AccessibilityService service,
      ImageCaptionStorage imageCaptionStorage,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.service = service;
    this.imageCaptionStorage = imageCaptionStorage;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  public static boolean supportsImageCaption() {
    return FeatureSupport.canTakeScreenShotByAccessibilityService();
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  /**
   * Creates a {@link CaptionRequest} to perform corresponding image caption or reads out the result
   * if it exists in the cache.
   */
  public boolean caption(AccessibilityNodeInfoCompat node) {
    @Nullable ImageNode savedResult = imageCaptionStorage.getCaptionResults(node);
    if (savedResult != null) {
      LogUtils.v(TAG, "perform() caption result exists " + savedResult);
      return true;
    }

    screenshotRequests.addRequest(
        new ScreenshotCaptureRequest(
            service,
            node,
            (nodeForCaption, screenCapture) -> {
              if (screenCapture == null) {
                // TODO: Retry taking screenshot if the error code is
                // AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                LogUtils.v(TAG, "onScreenCaptureFinish() taking screenshot is failed.");
                screenshotRequests.performNextRequest();
                return;
              }

              LogUtils.v(TAG, "onScreenCaptureFinish() taking screenshot is successful.");
              addCaptionRequest(nodeForCaption, screenCapture);
              screenshotRequests.performNextRequest();
            }));
    return true;
  }

  @VisibleForTesting
  void onCharacterCaptionFinish(AccessibilityNode node, @Nullable CharSequence result) {
    @Nullable AccessibilityNode focusedNode = null;
    try {
      LogUtils.v(
          TAG,
          "onCharacterCaptionFinish() "
              + StringBuilderUtils.joinFields(
                  StringBuilderUtils.optionalSubObj("result", result),
                  StringBuilderUtils.optionalSubObj("node", node)));
      characterCaptionRequests.performNextRequest();

      if (TextUtils.isEmpty(result)) {
        return;
      }

      focusedNode =
          AccessibilityNode.takeOwnership(
              accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false));
      if (focusedNode == null) {
        return;
      }

      // Announces the result if the accessibility focus is on the target node.
      if (node.equals(focusedNode)) {
        // TODO: Delay announcement until all results arrive or 200ms.
        pipeline.returnFeedback(
            EVENT_ID_UNTRACKED,
            Feedback.Part.builder()
                .setSpeech(
                    Speech.builder()
                        .setAction(Speech.Action.SPEAK)
                        .setText(
                            String.format(
                                service.getString(R.string.character_recognition_text), result))
                        .build()));
      }
      imageCaptionStorage.updateCharacterCaptionResult(node, result);
    } finally {
      AccessibilityNode.recycle("ImageCaptioner.onCharacterCaptionFinish()", node, focusedNode);
    }
  }

  @VisibleForTesting
  void addCaptionRequest(AccessibilityNodeInfoCompat node, Bitmap screenCapture) {
    characterCaptionRequests.addRequest(
        new CharacterCaptionRequest(
            service,
            node,
            screenCapture,
            /* onFinishListener= */ this::onCharacterCaptionFinish,
            /* onErrorListener= */ (errorCode) -> {
              LogUtils.v(TAG, "onError(), error=" + CaptionRequest.errorName(errorCode));
              characterCaptionRequests.performNextRequest();
            }));
  }

  @VisibleForTesting
  void clearRequests() {
    screenshotRequests.clear();
    characterCaptionRequests.clear();
  }

  @VisibleForTesting
  int getWaitingCharacterCaptionRequestSize() {
    return characterCaptionRequests.getWaitingRequestSize();
  }
}
