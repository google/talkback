/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.accessibility.talkback.actor.gemini;

import static com.google.android.accessibility.talkback.PrimesController.TimerAction.GEMINI_ON_DEVICE_RESPONSE_LATENCY;
import static com.google.android.accessibility.talkback.PrimesController.TimerAction.GEMINI_RESPONSE_LATENCY;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.PrimesController;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.gemini.AiCoreEndpoint.AiFeatureDownloadCallback;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** GeminiActor performs Gemini commands, via {@link GeminiEndpoint}. */
public class GeminiActor {
  /** Enumerates the possible reasons that is associated with a Gemini response. */
  enum FinishReason {
    STOP, // Natural stop point of the model or provided stop sequence.
    ERROR_PARSING_RESULT,
    ERROR_RESPONSE,
    ERROR_BLOCKED;
  }

  /** Enumerates the possible error reasons for requesting a Gemini command. */
  enum ErrorReason {
    UNSUPPORTED,
    DISABLED,
    NETWORK_ERROR,
    NO_IMAGE,
    EMPTY_PROMPT,
    BITMAP_COMPRESSION_FAIL,
    FEATURE_DOWNLOADING,
    JOB_CANCELLED,
  }

  /** Defines a callback interface for handling responses received from Gemini server. */
  public interface GeminiResponseListener {
    void onResponse(FinishReason finishReason, String text);

    void onError(ErrorReason errorReason);
  }

  /** Defines the core contract for classes implementing a connection to Gemini server. */
  public interface GeminiEndpoint {
    boolean createRequestGeminiCommand(
        String text,
        Bitmap image,
        boolean manualTrigger,
        GeminiResponseListener geminiResponseListener);

    void cancelCommand();

    /**
     * In general, TalkBack will maintain at most one outstanding Gemini transaction at all time. It
     * will abort the pending request before requesting a new one. One exception is when a manual
     * triggered transaction is pending, then an auto triggered transaction is not allowed until the
     * manual triggered one is done.
     *
     * @return if any manual triggered transaction is pending.
     */
    boolean hasPendingTransaction();
  }

  /** Read-only interface for GeminiActor state data. */
  public class State {
    public boolean hasAiCore() {
      return aiCoreEndpoint.hasAiCore();
    }

    public boolean isAiFeatureAvailable() {
      return aiCoreEndpoint.isAiFeatureAvailable();
    }
  }

  private static final String TAG = "GeminiActor";
  private static final int MAX_EARCON_PLAY_COUNT = 12;
  private static final int EARCON_PLAY_CYCLE = 1000; // in milli-second
  private static final int KEEP_WAITING_TIME_SEC = 15;
  private final Context context;
  private final GeminiEndpoint geminiEndpoint;
  private final AiCoreEndpoint aiCoreEndpoint;
  private Pipeline.FeedbackReturner pipeline;
  private final TalkBackAnalytics analytics;
  private final PrimesController primesController;
  // Record the start time of Gemini request, with which TalkBack can measure the latency when the
  // Gemini response is received.
  private long startTime;
  private final Handler mainHandler;
  private final RequestProgressToneDelayed progressToneDelayed;

  public final State state;

  private long downloadedSizeInBytes = -1;
  private long featureSizeInBytes = -1;
  private GeminiResultDialog geminiResultDialog;
  // Record the Gemini requestId to Gemini type(Server-side/On-device) mapping. Theoretically, the
  // map would be at most only one entry. We check each time before a new entry added, and clear the
  // map when it reaches the maximum value(REQUEST_ID_MAP_CAPACITY).
  // TODO: Consider to remove this protection scheme in the future.
  private final Map<Integer, Boolean> requestIdMap = new HashMap<>();
  private static final int REQUEST_ID_MAP_CAPACITY = 100;

  private AiFeatureDownloadCallback aiFeatureDownloadCallback =
      new AiFeatureDownloadCallback() {
        @Override
        public void onDownloadProgress(long currentSizeInBytes, long totalSizeInBytes) {
          downloadedSizeInBytes = currentSizeInBytes;
          featureSizeInBytes = totalSizeInBytes;
        }

        @Override
        public void onDownloadCompleted() {
          LogUtils.d(TAG, "GeminiActor - Feature download completed.");
        }
      };

  public GeminiActor(
      Context context,
      TalkBackAnalytics analytics,
      PrimesController primesController,
      GeminiEndpoint geminiEndpoint,
      AiCoreEndpoint aiCoreEndpoint) {
    this.context = context;
    this.analytics = analytics;
    this.primesController = primesController;
    this.geminiEndpoint = geminiEndpoint;
    this.mainHandler = new Handler(context.getMainLooper());
    this.progressToneDelayed =
        new RequestProgressToneDelayed(this, MAX_EARCON_PLAY_COUNT, EARCON_PLAY_CYCLE);
    this.aiCoreEndpoint = aiCoreEndpoint;
    this.aiCoreEndpoint.setAiFeatureDownloadCallback(aiFeatureDownloadCallback);
    state = new State();
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Requests a new Gemini online session, utilizing the provided text and image data.
   *
   * @param requestId The requestId for identify the result
   * @param text The text content to be included in the Gemini session.
   * @param image The image to be associated with the Gemini session.
   */
  public void requestOnlineGeminiCommand(int requestId, String text, Bitmap image) {
    analytics.onGeminiEvent(TalkBackAnalytics.GEMINI_REQUEST);
    startTime = SystemClock.uptimeMillis();
    GeminiResponseListener responseListener =
        new GeminiResponseListener() {
          @Override
          public void onResponse(FinishReason finishReason, String text) {
            handleResponse(requestId, finishReason, text, /* manualTrigger= */ true);
          }

          @Override
          public void onError(ErrorReason errorReason) {
            handleErrorResponse(requestId, errorReason, /* manualTrigger= */ true);
          }
        };

    if (geminiEndpoint.createRequestGeminiCommand(
        text, image, /* manualTrigger= */ true, responseListener)) {
      if (requestIdMap.size() > REQUEST_ID_MAP_CAPACITY) {
        LogUtils.w(TAG, "The requestIdMap reaches its max capacity.");
        requestIdMap.clear();
      }
      requestIdMap.put(requestId, /* isServerSide= */ true);
      progressToneDelayed.cancel();
      aiCoreEndpoint.cancelCommand();
      progressToneDelayed.post(/* playTone= */ true);
    }
  }

  /**
   * Requests a new on-device Gemini session for image captioning.
   *
   * @param requestId The requestId for identify the result
   * @param image The image to be associated with the Gemini session.
   * @param manualTrigger Whether the request is triggered by user manually.
   */
  public void requestAiCoreImageCaptioning(int requestId, Bitmap image, boolean manualTrigger) {
    if (!aiCoreEndpoint.hasAiCore()) {
      handleErrorResponse(requestId, ErrorReason.UNSUPPORTED, manualTrigger);
      return;
    }
    // TODO: Add log for on-device AI feature.
    analytics.onGeminiEvent(TalkBackAnalytics.GEMINI_REQUEST);
    startTime = SystemClock.uptimeMillis();
    if (!manualTrigger
        && (aiCoreEndpoint.hasPendingTransaction() || geminiEndpoint.hasPendingTransaction())) {
      return;
    }
    if (aiCoreEndpoint.createRequestGeminiCommand(
        context.getString(R.string.image_caption_with_gemini_prefix),
        image,
        manualTrigger,
        new GeminiResponseListener() {
          @Override
          public void onResponse(FinishReason finishReason, String text) {
            handleResponse(requestId, finishReason, text, manualTrigger);
          }

          @Override
          public void onError(ErrorReason errorReason) {
            handleErrorResponse(requestId, errorReason, manualTrigger);
          }
        })) {
      if (requestIdMap.size() > REQUEST_ID_MAP_CAPACITY) {
        LogUtils.w(TAG, "The requestIdMap reaches its max capacity.");
        requestIdMap.clear();
      }
      requestIdMap.put(requestId, /* isServerSide= */ false);
      progressToneDelayed.cancel();
      geminiEndpoint.cancelCommand();
      progressToneDelayed.post(manualTrigger);
    }
  }

  public void onUnbind() {
    if (aiCoreEndpoint != null) {
      aiCoreEndpoint.onUnbind();
    }
  }

  private void playProgressTone() {
    pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.sound(R.raw.volume_beep));
  }

  private void handleResponse(
      int requestId, FinishReason finishReason, String text, boolean manualTrigger) {
    switch (finishReason) {
      case STOP:
        if (manualTrigger) {
          mainHandler.post(
              () -> {
                if (geminiResultDialog != null) {
                  geminiResultDialog.dismissDialog();
                }
                geminiResultDialog =
                    new GeminiResultDialog(
                        context,
                        R.string.title_gemini_result_dialog,
                        text,
                        R.string.positive_button_gemini_result_dialog);
                geminiResultDialog.showDialog();
              });
        } else {
          responseImageCaptionResult(requestId, text, /* isSuccess= */ true, manualTrigger);
        }
        analytics.onGeminiEvent(TalkBackAnalytics.GEMINI_SUCCESS);
        PrimesController.TimerAction action = GEMINI_RESPONSE_LATENCY;
        if (requestIdMap.containsKey(requestId)
            && Objects.equals(requestIdMap.get(requestId), Boolean.FALSE)) {
          action = GEMINI_ON_DEVICE_RESPONSE_LATENCY;
        }
        primesController.recordDuration(action, startTime, SystemClock.uptimeMillis());
        break;
      case ERROR_PARSING_RESULT:
        responseImageCaptionResult(
            requestId, R.string.gemini_error_parsing_result, /* isSuccess= */ false, manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_FAIL_TO_PARSE_RESPONSE);
        break;
      case ERROR_RESPONSE:
        responseImageCaptionResult(
            requestId, R.string.gemini_error_message, /* isSuccess= */ false, manualTrigger);

        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_PROTOCOL_ERROR);
        break;
      case ERROR_BLOCKED:
        responseImageCaptionResult(
            requestId, R.string.gemini_block_message, /* isSuccess= */ false, manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_CONTENT_BLOCKED);
        break;
    }
    if (requestIdMap.containsKey(requestId)) {
      requestIdMap.remove(requestId);
    }
    progressToneDelayed.cancel();
  }

  private void handleErrorResponse(int requestId, ErrorReason errorReason, boolean manualTrigger) {
    switch (errorReason) {
      case UNSUPPORTED:
        responseImageCaptionResult(
            requestId, R.string.gemini_error_message, /* isSuccess= */ false, manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_APIKEY_NOT_AVAILABLE);
        break;
      case DISABLED:
        responseImageCaptionResult(
            requestId,
            R.string.summary_pref_gemini_support_disabled,
            /* isSuccess= */ false,
            manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_USER_NOT_OPT_IN);
        break;
      case NETWORK_ERROR:
        responseImageCaptionResult(
            requestId, R.string.gemini_network_error, /* isSuccess= */ false, manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_NETWORK_UNAVAILABLE);
        break;
      case NO_IMAGE:
        responseImageCaptionResult(
            requestId,
            R.string.gemini_screenshot_unavailable,
            /* isSuccess= */ false,
            manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_NO_SCREENSHOT_PROVIDED);
        break;
      case BITMAP_COMPRESSION_FAIL:
        responseImageCaptionResult(
            requestId,
            R.string.gemini_screenshot_unavailable,
            /* isSuccess= */ false,
            manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_FAIL_TO_ENCODE_PICTURE);
        break;
      case EMPTY_PROMPT:
        responseImageCaptionResult(
            requestId,
            context.getString(
                R.string.voice_commands_partial_result,
                context.getString(R.string.title_pref_help)),
            /* isSuccess= */ false,
            manualTrigger);
        analytics.onGeminiFailEvent(TalkBackAnalytics.GEMINI_FAIL_COMMAND_NOT_PROVIDED);
        break;
      case FEATURE_DOWNLOADING:
        if (featureSizeInBytes > 0 && downloadedSizeInBytes >= 0) {
          long downloadedSizeInMb = downloadedSizeInBytes / (1024 * 1024);
          long sizeInMb = featureSizeInBytes / (1024 * 1024);
          responseImageCaptionResult(
              requestId,
              context.getString(
                  R.string.message_aifeature_downloading_with_progress,
                  downloadedSizeInMb,
                  sizeInMb),
              /* isSuccess= */ false,
              manualTrigger);
        } else {
          LogUtils.w(TAG, "Can't get the download progress.");
          responseImageCaptionResult(
              requestId,
              R.string.message_aifeature_downloading,
              /* isSuccess= */ false,
              manualTrigger);
        }
        break;
      case JOB_CANCELLED:
        // TODO: add metric for the cancelled tasks.
        break;
    }
    progressToneDelayed.cancel();
  }

  private void speak(CharSequence text) {
    pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.speech(text));
  }

  private void responseImageCaptionResult(
      int requestId, @StringRes int textId, boolean isSuccess, boolean manualTrigger) {
    responseImageCaptionResult(requestId, context.getString(textId), isSuccess, manualTrigger);
  }

  private void responseImageCaptionResult(
      int requestId, String text, boolean isSuccess, Boolean manualTrigger) {
    // For manual trigger the result doesn't integrate with other image captioning module(OCR, icon
    // detection) yet, so speak the result directly.
    if (manualTrigger) {
      speak(text);
    } else {
      // Send the result to ImageCaptioner to integrate the resul with OCR and Icon detection.
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.responseImageCaptionResult(
              requestId, text, isSuccess, /* manualTrigger= */ false));
    }
  }

  private static class RequestProgressToneDelayed extends Handler {
    private final int playCountLimit;
    private final int delay;
    private int playCount;
    private final GeminiActor parent;
    private boolean playTone;

    private static final int PLAY_TONE_CYCLE_TIME_UP = 0;

    public RequestProgressToneDelayed(GeminiActor parent, int playCountLimit, int delay) {
      this.parent = parent;
      this.delay = delay;
      this.playCountLimit = playCountLimit;
    }

    private void post(boolean playTone) {
      removeMessages(PLAY_TONE_CYCLE_TIME_UP);
      playCount = 0;
      this.playTone = playTone;
      if (playTone) {
        parent.playProgressTone();
      }
      sendEmptyMessageDelayed(PLAY_TONE_CYCLE_TIME_UP, delay);
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == PLAY_TONE_CYCLE_TIME_UP) {
        // TODO: This comparison counts on the cycle(EARCON_PLAY_CYCLE) of playing tone is exactly
        // one second. If the requirement changed to others (such as 500ms) then the value will be
        // affected. It's necessary to redesign this part to adapt the cycle time change.
        if (++playCount >= KEEP_WAITING_TIME_SEC) {
          parent.geminiEndpoint.cancelCommand();
          parent.aiCoreEndpoint.cancelCommand();
        } else {
          if (playCount <= playCountLimit && playTone) {
            parent.playProgressTone();
          }
          sendEmptyMessageDelayed(PLAY_TONE_CYCLE_TIME_UP, delay);
        }
      }
    }

    public void cancel() {
      removeMessages(PLAY_TONE_CYCLE_TIME_UP);
    }
  }
}
