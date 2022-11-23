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
import static com.google.android.accessibility.talkback.actor.ImageCaptioner.CaptionType.ICON_LABEL;
import static com.google.android.accessibility.talkback.actor.ImageCaptioner.CaptionType.OCR;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_CAPTION_REQUEST;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_CAPTION_REQUEST_MANUAL;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_ICON_DETECT_ABORT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_ICON_DETECT_FAIL;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_ICON_DETECT_NO_RESULT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_ICON_DETECT_PERFORM;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_ICON_DETECT_SUCCEED;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_IMAGE_CAPTION_CACHE_HIT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_OCR_ABORT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_OCR_PERFORM;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_OCR_PERFORM_FAIL;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED_EMPTY;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.IMAGE_CAPTION_EVENT_SCREENSHOT_FAILED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Performs image caption and manages related state. */
public class ImageCaptioner extends Handler
    implements WindowEventHandler, OnSharedPreferenceChangeListener {

  /** Type of image caption. */
  enum CaptionType {
    OCR,
    ICON_LABEL
  }

  private static final String TAG = "ImageCaptioner";
  public static final int CAPTION_REQUEST_CAPACITY = 10;
  @VisibleForTesting static boolean SUPPORT_ICON_DETECTION = true;
  private static final int MSG_RESULT_TIMEOUT = 0;
  private static final long RESULT_MAX_WAITING_TIME_MS = 5000;

  private final AccessibilityService service;
  private Pipeline.FeedbackReturner pipeline;
  private ActorState actorState;
  private final SharedPreferences prefs;
  private final ImageCaptionStorage imageCaptionStorage;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final TalkBackAnalytics analytics;
  private final IconDetectionModuleDownloadPrompter iconDetectionModuleDownloadPrompter;
  @VisibleForTesting @Nullable IconAnnotationsDetector iconAnnotationsDetector;
  private boolean iconAnnotationsDetectorStarted = false;
  @Nullable private AccessibilityNodeInfoCompat queuedNode;
  private final Map<Integer, CaptionResult> captionResults;
  /**
   * The unique ID for caption requests. The ID of different caption requests for a node are the
   * same.
   */
  private int requestId = 0;

  private final RequestList<ScreenshotCaptureRequest> screenshotRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);
  private final RequestList<CharacterCaptionRequest> characterCaptionRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);
  private final RequestList<IconDetectionRequest> iconDetectionRequests =
      new RequestList<>(CAPTION_REQUEST_CAPACITY);

  public ImageCaptioner(
      AccessibilityService service,
      ImageCaptionStorage imageCaptionStorage,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      TalkBackAnalytics analytics) {
    this.service = service;
    prefs = SharedPreferencesUtils.getSharedPreferences(service);
    this.imageCaptionStorage = imageCaptionStorage;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.captionResults = new HashMap<>();
    this.analytics = analytics;
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
                prefs
                    .edit()
                    .putBoolean(service.getString(R.string.pref_icon_detection_uninstalled), false)
                    .apply();

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
    SharedPreferencesUtils.getSharedPreferences(service)
        .registerOnSharedPreferenceChangeListener(this);

    // Try to initialize icon detection when TalkBack on.
    initIconDetection();
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
    if (!supportsIconDetection(service)
        || !iconDetectionModuleDownloadPrompter.isIconDetectionModuleAvailable()
        || isIconDetectedUninstalled()) {
      return false;
    }

    if (iconAnnotationsDetector != null) {
      // Icon detector has been initialized.
      return true;
    }

    // Allows immediate access to the code and resource of the dynamic feature module.
    SplitCompatUtils.installActivity(service);

    iconAnnotationsDetector =
        IconAnnotationsDetectorFactory.create(service.getApplicationContext());
    if (iconAnnotationsDetector == null) {
      return false;
    }

    imageCaptionStorage.setIconAnnotationsDetector(iconAnnotationsDetector);
    startIconDetector();
    return true;
  }

  @VisibleForTesting
  void startIconDetector() {
    if (iconAnnotationsDetector != null) {
      synchronized (this) {
        if (!iconAnnotationsDetectorStarted) {
          iconAnnotationsDetectorStarted = true;
          iconAnnotationsDetector.start();
        }
      }
    }
  }

  @VisibleForTesting
  void shutdownIconDetector() {
    removeMessages(MSG_RESULT_TIMEOUT);
    captionResults.clear();

    if (iconAnnotationsDetector != null) {
      synchronized (this) {
        if (iconAnnotationsDetectorStarted) {
          iconAnnotationsDetectorStarted = false;
          iconAnnotationsDetector.shutdown();
        }
      }
      iconAnnotationsDetector = null;
    }
  }

  public void shutdown() {
    shutdownIconDetector();
    iconDetectionModuleDownloadPrompter.shutdown();
    SharedPreferencesUtils.getSharedPreferences(service)
        .unregisterOnSharedPreferenceChangeListener(this);
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

    if (iconDetectionModuleDownloadPrompter.isIconDetectionModuleAvailable()
        && !isIconDetectedUninstalled()) {
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
    analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_CAPTION_REQUEST);
    if (isUserRequested) {
      analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_CAPTION_REQUEST_MANUAL);
    }
    if (savedResult != null) {
      analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_IMAGE_CAPTION_CACHE_HIT);
      if (isUserRequested) {
        LogUtils.v(TAG, "caption() result exists %s", savedResult);
        returnCaptionResult(
            savedResult.getOcrText(),
            savedResult.getDetectedIconLabel(),
            /* isUserRequested= */ true);
      }

      // Caption results for the focused view will be read out by node_text_or_label method of the
      // compositor.
      return true;
    }

    if (iconAnnotationsDetector != null) {
      if (!iconAnnotationsDetectorStarted) {
        startIconDetector();
      }

      // If the label for the matched icon is available in English, there is no need to process
      // the screenshot to detect icons. And it is possible that the label for the matched icon is
      // available in English but not available in current speech language.
      CharSequence iconLabel = iconAnnotationsDetector.getIconLabel(Locale.ENGLISH, node);
      if (iconLabel != null) {
        analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_IMAGE_CAPTION_CACHE_HIT);
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

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (service.getString(R.string.pref_icon_detection_uninstalled).equals(key)
        && isIconDetectedUninstalled()) {
      iconDetectionRequests.clear();
      shutdownIconDetector();
    }
  }

  @VisibleForTesting
  void handleScreenshotCaptureResponse(
      AccessibilityNodeInfoCompat node, @Nullable Bitmap screenCapture, boolean isUserRequested) {
    if (screenCapture == null) {
      analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_SCREENSHOT_FAILED);
      // TODO: Retry taking screenshot if the error code is
      // AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
      LogUtils.v(TAG, "onScreenCaptureFinish() taking screenshot is failed.");
      screenshotRequests.performNextRequest();
      return;
    }

    LogUtils.v(TAG, "onScreenCaptureFinish() taking screenshot is successful.");
    requestId++;

    CaptionResult captionResult =
        new CaptionResult(AccessibilityNode.obtainCopy(node), isUserRequested);
    captionResults.put(requestId, captionResult);
    sendResultTimeoutMessage();

    if (iconAnnotationsDetector == null) {
      // Icon detection doesn't support.
      captionResult.setIconLabel(null);
    } else {
      analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_ICON_DETECT_PERFORM);
      addIconDetectionRequest(requestId, node, screenCapture, isUserRequested);
    }

    analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_OCR_PERFORM);
    addCaptionRequest(requestId, node, screenCapture, isUserRequested);
    screenshotRequests.performNextRequest();
  }

  @VisibleForTesting
  void onCharacterCaptionFinish(
      int id, AccessibilityNode node, @Nullable CharSequence result, boolean isUserRequested) {
    analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED);
    LogUtils.v(
        TAG,
        "onCharacterCaptionFinish() "
            + StringBuilderUtils.joinFields(
                StringBuilderUtils.optionalSubObj("result", result),
                StringBuilderUtils.optionalSubObj("node", node)));
    characterCaptionRequests.performNextRequest();

    handleResult(id, node, OCR, result, isUserRequested);
    imageCaptionStorage.updateCharacterCaptionResult(node, result);
  }

  @VisibleForTesting
  void addCaptionRequest(
      int id, AccessibilityNodeInfoCompat node, Bitmap screenCapture, boolean isUserRequested) {
    characterCaptionRequests.addRequest(
        new CharacterCaptionRequest(
            id,
            service,
            node,
            screenCapture,
            /* onFinishListener= */ this::onCharacterCaptionFinish,
            /* onErrorListener= */ (errorRequestId, errorNode, errorCode, userRequest) -> {
              analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_OCR_PERFORM_FAIL);
              LogUtils.v(TAG, "onError(), error= %s", CaptionRequest.errorName(errorCode));
              characterCaptionRequests.performNextRequest();
              handleResult(
                  errorRequestId,
                  AccessibilityNode.obtainCopy(node),
                  OCR,
                  /* result= */ null,
                  userRequest);
            },
            isUserRequested));
  }

  @VisibleForTesting
  void onIconDetectionFinish(
      int id, AccessibilityNode node, @Nullable CharSequence result, boolean isUserRequested) {
    analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_ICON_DETECT_SUCCEED);
    LogUtils.v(TAG, "onIconDetectionFinish() result=%s node=%s", result, node);
    iconDetectionRequests.performNextRequest();

    handleResult(id, node, ICON_LABEL, result, isUserRequested);
    imageCaptionStorage.updateDetectedIconLabel(node, result);
  }

  @VisibleForTesting
  void addIconDetectionRequest(
      int id, AccessibilityNodeInfoCompat node, Bitmap screenCapture, boolean isUserRequested) {
    iconDetectionRequests.addRequest(
        new IconDetectionRequest(
            id,
            node,
            screenCapture,
            iconAnnotationsDetector,
            getSpeechLocale(),
            /* onFinishListener= */ this::onIconDetectionFinish,
            /* onErrorListener= */ (errorRequestId, errorNode, errorCode, userRequest) -> {
              analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_ICON_DETECT_FAIL);
              LogUtils.v(TAG, "onError(), error=%s", CaptionRequest.errorName(errorCode));
              iconDetectionRequests.performNextRequest();
              handleResult(
                  errorRequestId,
                  AccessibilityNode.obtainCopy(node),
                  ICON_LABEL,
                  /* result= */ null,
                  userRequest);
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
    captionResults.clear();
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

  /** Checks if the user has executed uninstallation of the icon detection. */
  private boolean isIconDetectedUninstalled() {
    return prefs.getBoolean(service.getString(R.string.pref_icon_detection_uninstalled), false);
  }

  private void handleResult(
      int id,
      AccessibilityNode node,
      CaptionType type,
      CharSequence result,
      boolean isUserRequested) {
    @Nullable
    AccessibilityNode focusedNode =
        AccessibilityNode.takeOwnership(
            accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false));

    @Nullable CaptionResult captionResult = captionResults.get(id);
    if (node.equals(focusedNode)) {
      if (captionResult == null) {
        // There is no generate runnable for the request, so announcing the result directly.
        switch (type) {
          case OCR:
            returnCaptionResult(result, /* iconLabel= */ null, isUserRequested);
            break;
          case ICON_LABEL:
            returnCaptionResult(/* ocrText= */ null, result, isUserRequested);
            break;
        }
      } else {
        switch (type) {
          case OCR:
            captionResult.setOcrText(result);
            break;
          case ICON_LABEL:
            captionResult.setIconLabel(result);
            break;
        }
        if (captionResult.isFinished()) {
          returnCaptionResult(captionResult);
          captionResults.remove(id);
          removeMessages(MSG_RESULT_TIMEOUT);
        }
      }
    } else {
      if (iconAnnotationsDetector != null) {
        analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_ICON_DETECT_ABORT);
      }
      analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_OCR_ABORT);
      captionResults.remove(id);
      if (captionResults.isEmpty()) {
        removeMessages(MSG_RESULT_TIMEOUT);
      }
    }
  }

  private void returnCaptionResult(CaptionResult captionResult) {
    returnCaptionResult(
        captionResult.ocrText, captionResult.iconLabel, captionResult.isUserRequest);
  }

  private void returnCaptionResult(
      @Nullable CharSequence ocrText, @Nullable CharSequence iconLabel, boolean isUserRequested) {
    boolean hasOcr = !TextUtils.isEmpty(ocrText);
    boolean hasIcon = !TextUtils.isEmpty(iconLabel);
    if (hasOcr) {
      if (hasIcon) {
        returnFeedback(service.getString(R.string.detected_image_label, iconLabel, ocrText));
      } else {
        if (iconAnnotationsDetector != null) {
          analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_ICON_DETECT_NO_RESULT);
        }
        returnFeedback(service.getString(R.string.character_recognition_text, ocrText));
      }
    } else {
      analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED_EMPTY);
      if (hasIcon) {
        returnFeedback(service.getString(R.string.detected_icon_label, iconLabel));
      } else {
        if (iconAnnotationsDetector != null) {
          analytics.onImageCaptionEvent(IMAGE_CAPTION_EVENT_ICON_DETECT_NO_RESULT);
        }
        if (isUserRequested) {
          returnFeedback(service.getString(R.string.image_caption_no_result));
        }
      }
    }
  }

  private void returnFeedback(@StringRes int text) {
    returnFeedback(service.getString(text));
  }

  private void returnFeedback(CharSequence text) {
    pipeline.returnFeedback(
        EVENT_ID_UNTRACKED,
        Feedback.Part.builder()
            .setSpeech(Speech.builder().setAction(Speech.Action.SPEAK).setText(text).build()));
  }

  /** Sends a message to handle results timeout. */
  private void sendResultTimeoutMessage() {
    removeMessages(MSG_RESULT_TIMEOUT);

    Message message = new Message();
    message.what = MSG_RESULT_TIMEOUT;
    message.arg1 = requestId;

    sendMessageAtTime(message, System.currentTimeMillis() + RESULT_MAX_WAITING_TIME_MS);
  }

  @Override
  public void handleMessage(@NonNull Message msg) {
    super.handleMessage(msg);
    if (msg.what == MSG_RESULT_TIMEOUT) {
      // Obtains the caption result by requestId.
      @Nullable CaptionResult captionResult = captionResults.get(msg.arg1);
      if (captionResult != null) {
        @Nullable
        AccessibilityNode focusedNode =
            AccessibilityNode.takeOwnership(
                accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false));
        if (captionResult.node.equals(focusedNode)) {
          returnCaptionResult(captionResult);
        }
      }
      captionResults.clear();
    }
  }

  /** Stores all caption results to announce them together. */
  private static class CaptionResult {
    private final AccessibilityNode node;
    private final boolean isUserRequest;
    private boolean isOcrFinished;
    @Nullable private CharSequence ocrText;
    private boolean isIconDetectionFinished;
    @Nullable private CharSequence iconLabel;

    private CaptionResult(AccessibilityNode node, boolean isUserRequest) {
      this.node = node;
      this.isUserRequest = isUserRequest;
    }

    private void setOcrText(CharSequence ocrText) {
      isOcrFinished = true;
      this.ocrText = ocrText;
    }

    private void setIconLabel(CharSequence iconLabel) {
      isIconDetectionFinished = true;
      this.iconLabel = iconLabel;
    }

    private boolean isFinished() {
      return isOcrFinished && isIconDetectionFinished;
    }
  }
}
