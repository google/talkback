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

package com.google.android.accessibility.talkback.imagedescription;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Image description results. */
@AutoValue
public abstract class ImageDescriptionInfo {

  public static final float DEFAULT_QUALITY_SCORE = -1;
  private static final int LABEL_MAX_COUNT = 5;

  public abstract boolean hasCaption();

  public abstract float captionQualityScore();

  @Nullable
  public abstract String captionText();

  @Nullable
  public abstract ImmutableList<String> labels();

  public static ImageDescriptionInfo create(
      boolean hasCaption, float captionQualityScore, String captionText, List<String> labels) {
    return new AutoValue_ImageDescriptionInfo(
        hasCaption, captionQualityScore, captionText, ImmutableList.copyOf(labels));
  }

  public static ImageDescriptionInfo create() {
    return new AutoValue_ImageDescriptionInfo(
        /* hasCaption= */ false,
        /* captionQualityScore= */ DEFAULT_QUALITY_SCORE,
        /* captionText= */ null,
        /* labels= */ ImmutableList.of());
  }

  /**
   * Returns the caption text with a confidence hint.
   *
   * <p>For high confidence, just reads out caption text.
   *
   * <p>For medium confidence, reads out the caption text with a medium confidence hint.
   *
   * <p>For low confidence, reads out the items in the image with a low confidence hint.
   */
  @Nullable
  public static String getCaptionText(Context context, ImageDescriptionInfo info) {
    String captionText = info.captionText();
    if (TextUtils.isEmpty(captionText)) {
      return captionText;
    }

    float score = info.captionQualityScore();
    if (score >= FeatureFlagReader.getImageDescriptionLowQualityThreshold(context)) {
      return captionText;
    } else {
      ImmutableList<String> labels = info.labels();
      if (labels == null || labels.isEmpty()) {
        return null;
      }
      int labelCount = labels.size();
      StringBuilder stringBuilder = new StringBuilder(labels.get(0));
      for (int i = 1; i < LABEL_MAX_COUNT && i < labelCount; i++) {
        stringBuilder.append(", ");
        stringBuilder.append(labels.get(i));
      }
      return stringBuilder.toString();
    }
  }

  @Override
  public final String toString() {
    return "ImageDescriptionInfo="
        + StringBuilderUtils.joinFields(
            StringBuilderUtils.optionalTag("hasCaption", hasCaption()),
            StringBuilderUtils.optionalNum(
                "captionQualityScore", captionQualityScore(), DEFAULT_QUALITY_SCORE),
            StringBuilderUtils.optionalSubObj("captionText", captionText()),
            StringBuilderUtils.optionalSubObj("labels", labels()));
  }
}
