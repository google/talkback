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

import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.AutomaticImageCaptioningState.OFF;
import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.AutomaticImageCaptioningState.ON_ALL_IMAGES;
import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.AutomaticImageCaptioningState.ON_UNLABELLED_ONLY;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.AutomaticImageCaptioningState;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.FeatureSwitchDialogResources;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.caption.Result;

/** Utils class for Image Caption feature. */
public class ImageCaptionUtils {

  /** Returns the state of automatic image captioning features. */
  public static AutomaticImageCaptioningState getAutomaticImageCaptioningState(
      Context context,
      SharedPreferences prefs,
      FeatureSwitchDialogResources switchDialogResources) {
    return SharedPreferencesUtils.getBooleanPref(
            prefs,
            context.getResources(),
            switchDialogResources.switchKey,
            switchDialogResources.switchDefaultValue)
        ? (!FeatureFlagReader.enableAutomaticCaptioningForAllImages(context)
                || SharedPreferencesUtils.getBooleanPref(
                    prefs,
                    context.getResources(),
                    switchDialogResources.switchOnUnlabelledOnlyKey,
                    switchDialogResources.switchOnUnlabelledOnlyDefaultValue))
            ? ON_UNLABELLED_ONLY
            : ON_ALL_IMAGES
        : OFF;
  }

  /**
   * Constructs the result text for manually triggered image caption. Returns empty string if all of
   * the arguments are empty or null.
   */
  public static String constructCaptionTextForManually(
      Context context,
      @Nullable Result imageDescriptionResult,
      @Nullable Result iconLabelResult,
      @Nullable Result ocrTextResult) {
    StringBuilder stringBuilder = new StringBuilder();
    // Order: Image -> Icon -> Text (OCR).
    if (!Result.isEmpty(imageDescriptionResult)) {
      stringBuilder.append(constructImageDescriptionText(context, imageDescriptionResult));
    }
    if (!Result.isEmpty(iconLabelResult)) {
      stringBuilder.append(context.getString(R.string.detected_icon_label, iconLabelResult.text()));
    }
    if (!Result.isEmpty(ocrTextResult)) {
      stringBuilder.append(
          context.getString(R.string.detected_recognized_text, ocrTextResult.text()));
    }
    return TextUtils.isEmpty(stringBuilder)
        ? context.getString(R.string.image_caption_no_result)
        : context.getString(R.string.detected_result, stringBuilder);
  }

  /**
   * Constructs the result text for auto triggered image caption. Returns empty string if all of the
   * arguments are empty or null.
   */
  public static String constructCaptionTextForAuto(
      Context context,
      @Nullable Result imageDescriptionResult,
      @Nullable Result iconLabelResult,
      @Nullable Result ocrTextResult) {
    StringBuilder stringBuilder = new StringBuilder();
    // Order: Icon -> Image -> Text (OCR).
    if (!Result.isEmpty(iconLabelResult)) {
      stringBuilder.append(context.getString(R.string.detected_icon_label, iconLabelResult.text()));
    }
    if (!Result.isEmpty(imageDescriptionResult)) {
      stringBuilder.append(constructImageDescriptionText(context, imageDescriptionResult));
    }
    if (!Result.isEmpty(ocrTextResult)) {
      stringBuilder.append(
          context.getString(R.string.detected_recognized_text, ocrTextResult.text()));
    }
    return TextUtils.isEmpty(stringBuilder)
        ? ""
        : context.getString(R.string.detected_result, stringBuilder);
  }

  private static String constructImageDescriptionText(
      Context context, Result imageDescriptionResult) {
    String resultText =
        context.getString(R.string.detected_image_description, imageDescriptionResult.text());
    if (imageDescriptionResult.confidence()
        >= FeatureFlagReader.getImageDescriptionHighQualityThreshold(context)) {
      return resultText;
    } else if (imageDescriptionResult.confidence()
        >= FeatureFlagReader.getImageDescriptionLowQualityThreshold(context)) {
      return context.getString(R.string.medium_confidence_image_description, resultText);
    } else {
      return context.getString(R.string.low_confidence_image_description, resultText);
    }
  }

  private ImageCaptionUtils() {}
}
