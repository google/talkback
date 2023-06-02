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

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.MUTE_NEXT_FOCUS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.icondetection.IconAnnotationsDetectorFactory;
import com.google.android.accessibility.talkback.icondetection.IconDetectionModuleDownloadPrompter;
import com.google.android.accessibility.talkback.icondetection.IconDetectionModuleDownloadPrompter.DownloadStateListener;
import com.google.android.accessibility.talkback.imagecaption.CaptionRequest;
import com.google.android.accessibility.talkback.imagecaption.CharacterCaptionRequest;
import com.google.android.accessibility.talkback.imagecaption.IconDetectionRequest;
import com.google.android.accessibility.talkback.imagecaption.RequestList;
import com.google.android.accessibility.talkback.imagecaption.ScreenshotCaptureRequest;
import com.google.android.accessibility.talkback.utils.SplitCompatUtils;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.caption.ImageCaptionStorage;
import com.google.android.accessibility.utils.caption.ImageNode;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.WindowEventHandler;
import com.google.android.accessibility.utils.screenunderstanding.IconAnnotationsDetector;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Locale;

/** Performs image caption and manages related state. */
public class ImageCaptioner implements WindowEventHandler {

  private static final String TAG = "ImageCaptioner";
  public static final int CAPTION_REQUEST_CAPACITY = 10;
  @VisibleForTesting static boolean SUPPORT_ICON_DETECTION = true;

  private final AccessibilityService service;
  private Pipeline.FeedbackReturner pipeline;
  private ActorState actorState;
  private final ImageCaptionStorage imageCaptionStorage;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final IconDetectionModuleDownloadPrompter iconDetectionModuleDownloadPrompter;
  @VisibleForTesting @Nullable IconAnnotationsDetector iconAnnotationsDetector;
  private boolean iconAnnotationsDetectorStarted = false;
  @Nullable private AccessibilityNodeInfoCompat queuedNode;

  private final RequestList<ScreenshotCaptureRequest> screenshotRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);
  private final RequestList<CharacterCaptionRequest> characterCaptionRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);
  private final RequestList<IconDetectionRequest> iconDetectionRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);

  public ImageCaptioner(
      AccessibilityService service,
      ImageCaptionStorage imageCaptionStorage,
      AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.service = service;
    this.imageCaptionStorage = imageCaptionStorage;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.iconDetectionModuleDownloadPrompter =
        new IconDetectionModuleDownloadPrompter(
            service,
            /* triggeredByTalkBackMenu= */ true,
            new DownloadStateListener() {
              @Override
              public void onInstalled() {
                returnFeedback(R.string.download_icon_detection_successful_hint);
              }

              @Override
              public void onFailed() {
                returnFeedback(R.string.download_icon_detection_failed_hint);
              }

              @Override
              public void onAccepted() {
                // Clears the node which is waiting for recognition because it's unnecessary to
                // perform other captions if the user accepts the download.
                returnFeedback(R.string.downloading_icon_detection_hint);
              }

              @Override
              public void onRejected() {
                // Records how many times the button is tapped because the dialog shouldn't be shown
                // if the download is rejected more than three times.
                SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
                String dialogShownTimesKey =
                    service.getString(R.string.pref_icon_detection_download_dialog_shown_times);
                int dialogShownTimes = prefs.getInt(dialogShownTimesKey, /* defValue= */ 0);
                prefs.edit().putInt(dialogShownTimesKey, ++dialogShownTimes).apply();

                returnFeedback(R.string.confirm_download_icon_detection_negative_button_hint);
              }

              @Override
              public void onDialogDismissed(@Nullable AccessibilityNodeInfoCompat queuedNode) {
                pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.focus(MUTE_NEXT_FOCUS));
                // Image captioning for the node will be executed when windows are stable.
                ImageCaptioner.this.queuedNode = queuedNode;
              }
            });
  }

  public static boolean supportsImageCaption(Context context) {
    return FeatureSupport.canTakeScreenShotByAccessibilityService()
        && !FeatureSupport.isWatch(context);
  }

  public static boolean supportsIconDetection(Context context) {
    return FeatureSupport.canTakeScreenShotByAccessibilityService()
        && !FeatureSupport.isWatch(context)
        && SUPPORT_ICON_DETECTION;
  }

  public boolean initIconDetection() {
    if (!supportsIconDetection(service)) {
      return false;
    }

    // Allows immediate access to the code and resource of the dynamic feature module.
    SplitCompatUtils.installActivity(service);

    iconAnnotationsDetector =
        IconAnnotationsDetectorFactory.create(service.getApplicationContext());
    if (iconAnnotationsDetector == null) {
      return false;
    }

    imageCaptionStorage.setIconAnnotationsDetector(iconAnnotationsDetector);
    return true;
  }

  public void start() {
    if (iconAnnotationsDetector != null) {
      synchronized (this) {
        if (!iconAnnotationsDetectorStarted) {
          iconAnnotationsDetectorStarted = true;
          iconAnnotationsDetector.start();
        }
      }
    }
  }

  public void shutdown() {
    if (iconAnnotationsDetector != null) {
      synchronized (this) {
        if (iconAnnotationsDetectorStarted) {
          iconAnnotationsDetectorStarted = false;
          iconAnnotationsDetector.shutdown();
        }
      }
    }
    iconDetectionModuleDownloadPrompter.shutdown();
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
    iconDetectionModuleDownloadPrompter.setPipeline(pipeline);
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  @Override
  public void handle(EventInterpretation interpretation, @Nullable Performance.EventId eventId) {
    // Performs other image captions if there is a node waited for recognition.
    if (interpretation.areWindowsStable() && queuedNode != null) {
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.performImageCaptions(queuedNode, /* isUserRequested= */ true));
      queuedNode = null;
    }
  }

  /**
   * Shows the confirmation dialog to download the icon detection module and perform image captions
   * for the given node.
   *
   * @return false, the caption request has not been performed and must wait until the icon
   *     detection module is installed or the dialog is cancelled.
   */
  public boolean confirmDownloadAndPerformCaption(AccessibilityNodeInfoCompat node) {
    if (iconAnnotationsDetector != null) {
      caption(node, /* isUserRequested= */ true);
      return true;
    }

    if (iconDetectionModuleDownloadPrompter.isIconDetectionModuleAvailable()) {
      if (iconAnnotationsDetector == null) {
        initIconDetection();
      }
      caption(node, /* isUserRequested= */ true);
      return true;
    }

    // Icon detection module hasn't been downloaded.
    // Checks if it is necessary to show the download confirmation dialog.
    if (iconDetectionModuleDownloadPrompter.needDownloadDialog()) {
      iconDetectionModuleDownloadPrompter.setCaptionNode(node);
      iconDetectionModuleDownloadPrompter.showConfirmationDialog();
      return false;
    }

    caption(node, /* isUserRequested= */ true);
    return true;
  }

  /**
   * Creates a {@link CaptionRequest} to perform corresponding image caption or reads out the result
   * if it exists in the cache.
   */
  public boolean caption(AccessibilityNodeInfoCompat node, boolean isUserRequested) {
    @Nullable ImageNode savedResult = imageCaptionStorage.getCaptionResults(node);
    if (savedResult != null) {
      if (isUserRequested) {
        LogUtils.v(TAG, "caption() result exists %s", savedResult);
        @Nullable CharSequence ocrText = savedResult.getOcrText();
        @Nullable CharSequence iconLabel = savedResult.getDetectedIconLabel();
        SpannableStringBuilder stringBuilder = getResult(service, ocrText, iconLabel);
        if (TextUtils.isEmpty(stringBuilder)) {
          returnNoResultFeedbackForUserRequest();
        } else {
          returnFeedback(stringBuilder.toString());
        }
      }

      // Caption results for the focused view will be read out by node_text_or_label method of the
      // compositor.
      return true;
    }

    if (iconAnnotationsDetector != null) {
      if (!iconAnnotationsDetectorStarted) {
        start();
      }

      // If the label for the matched icon is available in English, there is no need to process
      // the screenshot to detect icons. And it is possible that the label for the matched icon is
      // available in English but not available in current speech language.
      CharSequence iconLabel = iconAnnotationsDetector.getIconLabel(Locale.ENGLISH, node);
      if (iconLabel != null) {
        LogUtils.v(TAG, "perform() icon label exists %s", iconLabel);
        return true;
      }
    }

    screenshotRequests.addRequest(
        new ScreenshotCaptureRequest(
            service, node, this::handleScreenshotCaptureResponse, isUserRequested));
    return true;
  }

  /**
   * Notifies the {@link IconAnnotationsDetector} that the whole screen content has changed if the
   * {@link IconAnnotationsDetector} is not {@code null}.
   *
   * @return {@code true} if {@link IconAnnotationsDetector} is not null
   */
  public boolean clearWholeScreenCache() {
    if (iconAnnotationsDetector == null) {
      return false;
    }

    iconAnnotationsDetector.clearWholeScreenCache();
    return true;
  }

  /**
   * Notifies the {@link IconAnnotationsDetector} that the content inside the specific {@code rect}
   * has changed if the {@link IconAnnotationsDetector} is not {@code null}.
   *
   * @return {@code true} if {@link IconAnnotationsDetector} is not null
   */
  public boolean clearPartialScreenCache(Rect rect) {
    if (iconAnnotationsDetector == null) {
      return false;
    }

    iconAnnotationsDetector.clearPartialScreenCache(rect);
    return true;
  }

  /**
   * Invalidates cached results for the content inside the {@code rect} because an icon inside a
   * view may change due to the click.
   *
   * @return {@code true} if {@link IconAnnotationsDetector} is not null
   */
  public boolean clearCacheForView(Rect rect) {
    if (iconAnnotationsDetector == null) {
      return false;
    }

    clearPartialScreenCache(rect);

    // In Talkback only the view being focused can be clicked by the user
    @Nullable
    AccessibilityNode focusedNode =
        AccessibilityNode.takeOwnership(
            accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false));
    if (focusedNode == null) {
      return true;
    }
    imageCaptionStorage.invalidateCaptionForNode(focusedNode);
    return true;
  }

  @VisibleForTesting
  void handleScreenshotCaptureResponse(
      AccessibilityNodeInfoCompat node, @Nullable Bitmap screenCapture, boolean isUserRequested) {
    if (screenCapture == null) {
      // TODO: Retry taking screenshot if the error code is
      // AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
      LogUtils.v(TAG, "onScreenCaptureFinish() taking screenshot is failed.");
      screenshotRequests.performNextRequest();
      return;
    }

    LogUtils.v(TAG, "onScreenCaptureFinish() taking screenshot is successful.");
    if (iconAnnotationsDetector != null) {
      addIconDetectionRequest(node, screenCapture, isUserRequested);
    }

    addCaptionRequest(node, screenCapture, isUserRequested);
    screenshotRequests.performNextRequest();
  }

  @VisibleForTesting
  void onCharacterCaptionFinish(
      AccessibilityNode node, @Nullable CharSequence result, boolean isUserRequested) {
    LogUtils.v(
        TAG,
        "onCharacterCaptionFinish() "
            + StringBuilderUtils.joinFields(
                StringBuilderUtils.optionalSubObj("result", result),
                StringBuilderUtils.optionalSubObj("node", node)));
    characterCaptionRequests.performNextRequest();

    @Nullable
    AccessibilityNode focusedNode =
        AccessibilityNode.takeOwnership(
            accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false));

    if (TextUtils.isEmpty(result)) {
      if (isUserRequested && node.equals(focusedNode)) {
        returnNoResultFeedbackForUserRequest();
      }
      return;
    }

    // Announces the result if the accessibility focus is on the target node.
    if (node.equals(focusedNode)) {
      returnFeedback(service.getString(R.string.character_recognition_text, result));
    }
    imageCaptionStorage.updateCharacterCaptionResult(node, result);
  }

  @VisibleForTesting
  void addCaptionRequest(
      AccessibilityNodeInfoCompat node, Bitmap screenCapture, boolean isUserRequested) {
    characterCaptionRequests.addRequest(
        new CharacterCaptionRequest(
            service,
            node,
            screenCapture,
            /* onFinishListener= */ this::onCharacterCaptionFinish,
            /* onErrorListener= */ (errorNode, errorCode, userRequest) -> {
              LogUtils.v(TAG, "onError(), error= %s", CaptionRequest.errorName(errorCode));
              characterCaptionRequests.performNextRequest();
              @Nullable
              AccessibilityNode focusedNode =
                  AccessibilityNode.takeOwnership(
                      accessibilityFocusMonitor.getAccessibilityFocus(
                          /* useInputFocusIfEmpty= */ false));
              if (userRequest && errorNode.equals(focusedNode)) {
                returnNoResultFeedbackForUserRequest();
              }
            },
            isUserRequested));
  }

  @VisibleForTesting
  void onIconDetectionFinish(
      AccessibilityNode node, @Nullable CharSequence result, boolean isUserRequested) {
    LogUtils.v(TAG, "onIconDetectionFinish() result=%s node=%s", result, node);
    iconDetectionRequests.performNextRequest();

    @Nullable
    AccessibilityNode focusedNode =
        AccessibilityNode.takeOwnership(
            accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false));

    if (TextUtils.isEmpty(result)) {
      if (isUserRequested && node.equals(focusedNode)) {
        returnNoResultFeedbackForUserRequest();
      }
      return;
    }

    // Announces the result if the accessibility focus is on the target node.
    if (node.equals(focusedNode)) {
      returnFeedback(service.getString(R.string.detected_icon_label, result));
    }
    imageCaptionStorage.updateDetectedIconLabel(node, result);
  }

  @VisibleForTesting
  void addIconDetectionRequest(
      AccessibilityNodeInfoCompat node, Bitmap screenCapture, boolean isUserRequested) {
    iconDetectionRequests.addRequest(
        new IconDetectionRequest(
            node,
            screenCapture,
            iconAnnotationsDetector,
            getSpeechLocale(),
            /* onFinishListener= */ this::onIconDetectionFinish,
            /* onErrorListener= */ (errorNode, errorCode, userRequest) -> {
              LogUtils.v(TAG, "onError(), error=%s", CaptionRequest.errorName(errorCode));
              iconDetectionRequests.performNextRequest();
              @Nullable
              AccessibilityNode focusedNode =
                  AccessibilityNode.takeOwnership(
                      accessibilityFocusMonitor.getAccessibilityFocus(
                          /* useInputFocusIfEmpty= */ false));
              if (userRequest && errorNode.equals(focusedNode)) {
                returnNoResultFeedbackForUserRequest();
              }
            },
            isUserRequested));
  }

  @VisibleForTesting
  Locale getSpeechLocale() {
    Locale locale = actorState.getLanguageState().getCurrentLanguage();
    return (locale == null) ? Locale.getDefault() : locale;
  }

  @VisibleForTesting
  void clearRequests() {
    screenshotRequests.clear();
    characterCaptionRequests.clear();
    iconDetectionRequests.clear();
  }

  @VisibleForTesting
  int getWaitingCharacterCaptionRequestSize() {
    return characterCaptionRequests.getWaitingRequestSize();
  }

  @VisibleForTesting
  int getWaitingIconDetectionRequestSize() {
    return iconDetectionRequests.getWaitingRequestSize();
  }

  @VisibleForTesting
  int getWaitingScreenshotRequestSize() {
    return screenshotRequests.getWaitingRequestSize();
  }

  @VisibleForTesting
  static SpannableStringBuilder getResult(
      Context context, @Nullable CharSequence ocrText, @Nullable CharSequence iconLabel) {
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
    if (ocrText != null) {
      StringBuilderUtils.appendWithSeparator(
          stringBuilder, context.getString(R.string.character_recognition_text, ocrText));
    }
    if (iconLabel != null) {
      StringBuilderUtils.appendWithSeparator(
          stringBuilder, context.getString(R.string.detected_icon_label, iconLabel));
    }
    return stringBuilder;
  }

  private void returnNoResultFeedbackForUserRequest() {
    returnFeedback(service.getString(R.string.image_caption_no_text_found));
  }

  private void returnFeedback(@StringRes int text) {
    returnFeedback(service.getString(text));
  }

  private void returnFeedback(String caption) {
    // TODO: Delay announcement until all results arrive or 200ms.
    pipeline.returnFeedback(
        EVENT_ID_UNTRACKED,
        Feedback.Part.builder()
            .setSpeech(Speech.builder().setAction(Speech.Action.SPEAK).setText(caption).build()));
  }
}
