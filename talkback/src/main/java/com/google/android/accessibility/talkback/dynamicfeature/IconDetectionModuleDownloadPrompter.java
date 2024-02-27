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

package com.google.android.accessibility.talkback.dynamicfeature;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.ICON_LABEL;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.DownloadDialogResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.ImageCaptionPreferenceKeys;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.UninstallDialogResources;

/**
 * Shows a confirmation dialog to guide users through the download of the icon detection module
 * dynamically.
 */
public class IconDetectionModuleDownloadPrompter extends ModuleDownloadPrompter {

  @Nullable private Pipeline.FeedbackReturner pipeline;

  /**
   * Creates a {@link ModuleDownloadPrompter} to handle the process of icon detection download and
   * uninstallation.
   */
  public IconDetectionModuleDownloadPrompter(Context context, FeatureDownloader featureDownloader) {
    super(
        context,
        featureDownloader,
        ICON_LABEL,
        ImageCaptionPreferenceKeys.ICON_DETECTION,
        DownloadDialogResources.ICON_DETECTION,
        UninstallDialogResources.ICON_DETECTION);
  }

  public void setPipeline(@Nullable Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  protected boolean initModule() {
    if (pipeline == null) {
      return false;
    }
    return pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.initializeIconDetection());
  }
}
