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

package com.google.android.accessibility.talkback.actor.gemini;

import static java.util.stream.Collectors.toMap;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.style.ClickableSpan;
import android.view.View;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.actor.gemini.GeminiFunctionUtils.DescribeImageDecision.Decision;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.NetworkUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import java.lang.ref.WeakReference;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utils class that provides common methods that provide Gemini opt-in information. */
public class GeminiFunctionUtils {
  // If the 1st time pop up of opt-in Gemini dialog is not dismissed by either [OK], or [No thanks],
  // the dialog will have a second chance to inform user of "Detailed image description".
  public static final int GEMINI_REPEAT_OPT_IN_COUNT = 1;
  private static final String TAG = "GeminiFunctionUtils";
  private static final String GEMINI_TOS_URL = "https://policies.google.com/terms";

  /** The whole decision tree considering the 6 configuration parameters. */
  static class DescribeImageDecision {
    /**
     * Depending on the configuration parameter, the empire picks an appropriate candidate as the
     * target of "Describe image"
     */
    public enum Decision {
      VALID,
      INVALID,
      DONT_CARE
    }

    int index;
    Decision aiCoreSupport;
    Decision aiCoreReady;
    Decision networkAvailable;
    Decision serverSideGeminiOptedIn;
    Decision serverSideGeminiRejected;
    Decision onDeviceGeminiOptedIn;

    DescribeImageDecision(
        int index,
        Decision aiCoreSupport,
        Decision aiCoreReady,
        Decision networkAvailable,
        Decision serverSideGeminiOptedIn,
        Decision serverSideGeminiRejected,
        Decision onDeviceGeminiOptedIn) {
      this.index = index;
      this.aiCoreSupport = aiCoreSupport;
      this.aiCoreReady = aiCoreReady;
      this.networkAvailable = networkAvailable;
      this.serverSideGeminiOptedIn = serverSideGeminiOptedIn;
      this.serverSideGeminiRejected = serverSideGeminiRejected;
      this.onDeviceGeminiOptedIn = onDeviceGeminiOptedIn;
    }
  }

  /**
   * Interface defines the Feedback part when the decision's made(Server-side/On-device Gemini,
   * ...,etc.
   */
  public interface FeedbackCandidate {
    Feedback.Part.Builder get(AccessibilityNodeInfoCompat node);
  }

  /** Preference of Describe Image */
  public static class DescribeImageCandidate extends Filter<Context> {
    // Symbolic name for debugging.
    private final String name;

    /** Returns the Feedback Part to construct the Describe image component. */
    final FeedbackCandidate getter;

    DescribeImageCandidate(FeedbackCandidate getter, String name) {
      this.getter = getter;
      this.name = name;
    }

    /**
     * Returns true if it's the preferred candidate. As a fallback, it returns [true] by default.
     */
    @Override
    public boolean accept(Context obj) {
      return true;
    }
  }

  public static final DescribeImageCandidate confirmDownloadOrPerformImageCaptioning =
      new DescribeImageCandidate(
          Feedback::confirmDownloadAndPerformCaptions, "confirmDownloadOrPerformImageCaptioning");

  public static final DescribeImageCandidate nonGeminiImageCaptioning =
      new DescribeImageCandidate(
          Feedback::confirmDownloadAndPerformCaptions, "nonGeminiImageCaptioning") {
        @Override
        public boolean accept(Context context) {
          return imageCaptioner != null && imageCaptioner.get().descriptionLibraryReady();
        }
      };

  public static final DescribeImageCandidate optInServerSide =
      new DescribeImageCandidate(Feedback::performGeminiOptIn, "optInServerSide") {
        @Override
        public boolean accept(Context context) {
          return GeminiConfiguration.isServerSideGeminiImageCaptioningEnabled(context);
        }
      };

  public static final DescribeImageCandidate showDetailedDescriptionUIFallBack =
      new DescribeImageCandidate(
          Feedback::performPopDetailedImageDescriptionSettings,
          "showDetailedDescriptionUIFallBack");

  public static final DescribeImageCandidate showDetailedDescriptionWhenGeminiFeatured =
      new DescribeImageCandidate(
          Feedback::performPopDetailedImageDescriptionSettings,
          "showDetailedDescriptionWhenGeminiFeatured") {
        @Override
        public boolean accept(Context context) {
          return GeminiConfiguration.isServerSideGeminiImageCaptioningEnabled(context)
              || GeminiConfiguration.isOnDeviceGeminiImageCaptioningEnabled(context);
        }
      };

  public static final DescribeImageCandidate showDetailedDescriptionWhenGeminiOnDeviceFeatured =
      new DescribeImageCandidate(
          Feedback::performPopDetailedImageDescriptionSettings,
          "showDetailedDescriptionWhenGeminiOnDeviceFeatured") {
        @Override
        public boolean accept(Context context) {
          return GeminiConfiguration.isOnDeviceGeminiImageCaptioningEnabled(context);
        }
      };

  public static final DescribeImageCandidate geminiServerSide =
      new DescribeImageCandidate(Feedback::performDetailedImageCaption, "geminiServerSide") {
        @Override
        public boolean accept(Context context) {
          return GeminiConfiguration.isServerSideGeminiImageCaptioningEnabled(context);
        }
      };

  public static final DescribeImageCandidate optInOnDevice =
      new DescribeImageCandidate(Feedback::performOnDeviceGeminiOptIn, "optInOnDevice") {
        @Override
        public boolean accept(Context context) {
          return GeminiConfiguration.isOnDeviceGeminiImageCaptioningEnabled(context);
        }
      };

  public static final DescribeImageCandidate geminiOnDevice =
      new DescribeImageCandidate(Feedback::performDetailedOnDeviceImageCaption, "geminiOnDevice") {
        @Override
        public boolean accept(Context context) {
          return GeminiConfiguration.isOnDeviceGeminiImageCaptioningEnabled(context);
        }
      };

  /**
   * the following are lists of DescriptionOptions each represents the ordered candidates for a
   * configuration tuple which is specified in decisionMap.
   */
  public static final List<DescribeImageCandidate> imageDescriptionOption1 =
      ImmutableList.of(
          nonGeminiImageCaptioning, optInServerSide, showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption2 =
      ImmutableList.of(confirmDownloadOrPerformImageCaptioning);

  public static final List<DescribeImageCandidate> imageDescriptionOption3 =
      ImmutableList.of(
          nonGeminiImageCaptioning, geminiServerSide, showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption4 =
      ImmutableList.of(optInServerSide, confirmDownloadOrPerformImageCaptioning);

  public static final List<DescribeImageCandidate> imageDescriptionOption5 =
      ImmutableList.of(confirmDownloadOrPerformImageCaptioning);

  public static final List<DescribeImageCandidate> imageDescriptionOption6 =
      ImmutableList.of(geminiServerSide, confirmDownloadOrPerformImageCaptioning);

  public static final List<DescribeImageCandidate> imageDescriptionOption7 =
      ImmutableList.of(
          optInServerSide,
          optInOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);
  public static final List<DescribeImageCandidate> imageDescriptionOption8 =
      ImmutableList.of(
          optInServerSide,
          geminiOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption9 =
      ImmutableList.of(
          showDetailedDescriptionWhenGeminiFeatured,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption10 =
      ImmutableList.of(geminiOnDevice, nonGeminiImageCaptioning, showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption11 =
      ImmutableList.of(
          geminiServerSide,
          optInOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption12 =
      ImmutableList.of(
          geminiServerSide,
          geminiOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption13 =
      ImmutableList.of(
          optInServerSide,
          optInOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption14 =
      ImmutableList.of(
          optInServerSide,
          geminiOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption15 =
      ImmutableList.of(
          showDetailedDescriptionWhenGeminiFeatured,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption16 =
      ImmutableList.of(
          optInOnDevice,
          optInServerSide,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption17 =
      ImmutableList.of(optInOnDevice, nonGeminiImageCaptioning, showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption18 =
      ImmutableList.of(
          optInOnDevice,
          geminiServerSide,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption19 =
      ImmutableList.of(
          geminiOnDevice,
          showDetailedDescriptionWhenGeminiFeatured,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption20 =
      ImmutableList.of(
          optInServerSide,
          optInOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption21 =
      ImmutableList.of(
          optInServerSide,
          geminiOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption22 =
      ImmutableList.of(
          showDetailedDescriptionWhenGeminiOnDeviceFeatured,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption23 =
      ImmutableList.of(geminiOnDevice, nonGeminiImageCaptioning, showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption24 =
      ImmutableList.of(
          geminiServerSide,
          optInServerSide,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  public static final List<DescribeImageCandidate> imageDescriptionOption25 =
      ImmutableList.of(
          geminiServerSide,
          geminiOnDevice,
          nonGeminiImageCaptioning,
          showDetailedDescriptionUIFallBack);

  // TODO: Refactor this for maintenance consideration.
  static final Map<DescribeImageDecision, List<DescribeImageCandidate>> decisionMap =
      Stream.of(
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      1,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.DONT_CARE),
                  imageDescriptionOption1),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      2,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.DONT_CARE),
                  imageDescriptionOption2),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      3,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.DONT_CARE,
                      Decision.DONT_CARE),
                  imageDescriptionOption3),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      4,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.DONT_CARE),
                  imageDescriptionOption4),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      5,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.DONT_CARE),
                  imageDescriptionOption5),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      6,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.DONT_CARE,
                      Decision.DONT_CARE),
                  imageDescriptionOption6),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      7,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID),
                  imageDescriptionOption7),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      8,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.VALID),
                  imageDescriptionOption8),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      9,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.INVALID),
                  imageDescriptionOption9),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      10,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.VALID),
                  imageDescriptionOption10),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      11,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.VALID,
                      Decision.DONT_CARE,
                      Decision.INVALID),
                  imageDescriptionOption11),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      12,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.VALID,
                      Decision.DONT_CARE,
                      Decision.VALID),
                  imageDescriptionOption12),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      13,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID),
                  imageDescriptionOption13),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      14,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.VALID),
                  imageDescriptionOption14),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      15,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.INVALID),
                  imageDescriptionOption15),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      16,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID),
                  imageDescriptionOption16),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      17,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.INVALID),
                  imageDescriptionOption17),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      18,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.DONT_CARE,
                      Decision.INVALID),
                  imageDescriptionOption18),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      19,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.DONT_CARE,
                      Decision.DONT_CARE,
                      Decision.VALID),
                  imageDescriptionOption19),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      20,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.INVALID),
                  imageDescriptionOption20),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      21,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.INVALID,
                      Decision.VALID),
                  imageDescriptionOption21),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      22,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.INVALID),
                  imageDescriptionOption22),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      23,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.INVALID,
                      Decision.VALID,
                      Decision.VALID),
                  imageDescriptionOption23),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      24,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.DONT_CARE,
                      Decision.INVALID),
                  imageDescriptionOption24),
              new SimpleEntry<>(
                  new DescribeImageDecision(
                      25,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.VALID,
                      Decision.DONT_CARE,
                      Decision.VALID),
                  imageDescriptionOption25))
          .collect(toMap(SimpleEntry::getKey, SimpleEntry::getValue));
  private static WeakReference<ImageCaptioner> imageCaptioner;

  private GeminiFunctionUtils() {}

  public static void setImageCaptioner(ImageCaptioner imageCaptioner) {
    GeminiFunctionUtils.imageCaptioner = new WeakReference<>(imageCaptioner);
  }

  private static boolean matchDecisionWithConfiguration(Decision decision, boolean configuration) {
    return decision == Decision.DONT_CARE || (decision == Decision.VALID) == configuration;
  }

  public static Feedback.Part.Builder getPreferredImageDescriptionFeedback(
      Context context, ActorState actorState, @Nullable AccessibilityNodeInfoCompat node) {

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);

    @Nullable List<DescribeImageCandidate> candidates = null;
    for (DescribeImageDecision decision : decisionMap.keySet()) {
      if (matchDecisionWithConfiguration(
              decision.aiCoreSupport, actorState.getGeminiState().hasAiCore())
          && matchDecisionWithConfiguration(
              decision.aiCoreReady, actorState.getGeminiState().isAiFeatureAvailable())
          && matchDecisionWithConfiguration(
              decision.networkAvailable, NetworkUtils.isNetworkConnected(context))
          && matchDecisionWithConfiguration(
              decision.serverSideGeminiOptedIn,
              SharedPreferencesUtils.getBooleanPref(
                  prefs,
                  context.getResources(),
                  R.string.pref_detailed_image_description_key,
                  R.bool.pref_detailed_image_description_default))
          && matchDecisionWithConfiguration(
              decision.onDeviceGeminiOptedIn,
              SharedPreferencesUtils.getBooleanPref(
                  prefs,
                  context.getResources(),
                  R.string.pref_auto_on_devices_image_description_key,
                  R.bool.pref_auto_on_device_image_description_default))
          && matchDecisionWithConfiguration(
              decision.serverSideGeminiRejected,
              prefs.getInt(context.getString(R.string.pref_gemini_repeat_opt_in_count_key), 0)
                  >= GEMINI_REPEAT_OPT_IN_COUNT)) {
        candidates = decisionMap.get(decision);
        LogUtils.d(TAG, "getPreferredImageDescriptionFeedback/decision index:%d", decision.index);
        break;
      }
    }

    if (candidates == null) {
      LogUtils.e(TAG, "getPreferredImageDescriptionFeedback/candidates is empty");
      return null;
    }

    for (DescribeImageCandidate candidate : candidates) {
      LogUtils.d(TAG, "getPreferredImageDescriptionFeedback/name:%s", candidate.name);
      if (candidate.accept(context)) {
        LogUtils.v(TAG, "getPreferredImageDescriptionFeedback/accepted");
        return candidate.getter.get(node);
      }
    }

    return null;
  }

  public static ClickableSpan createClickableSpanForGeminiTOS(Context context, BaseDialog dialog) {
    return new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(GEMINI_TOS_URL));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        dialog.dismissDialog();
      }
    };
  }

  private static boolean isDetailedImageDescriptionEnabled(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return SharedPreferencesUtils.getBooleanPref(
        prefs,
        context.getResources(),
        R.string.pref_detailed_image_description_key,
        R.bool.pref_detailed_image_description_default);
  }

  /**
   * This method provides the decision whether the Gemini opt-in dialog should be popped up. To
   * avoid annoying the user, TalkBack would not pop up the dialog when 1. User has acknowledged the
   * query by clicking either the positive or negative button, or 2. The dialog has been dismissed
   * for GEMINI_REPEAT_OPT_IN_COUNT times.
   */
  private static boolean needAnnounceToOptInGemini(Context context) {
    if (isDetailedImageDescriptionEnabled(context)) {
      return false;
    }
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    String repeatOptInCountKey = context.getString(R.string.pref_gemini_repeat_opt_in_count_key);
    return prefs.getInt(repeatOptInCountKey, 0) < GEMINI_REPEAT_OPT_IN_COUNT;
  }
}
