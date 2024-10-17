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

import androidx.annotation.IntDef;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** A class containing string resources and preference keys of image captioning. */
public final class ImageCaptionConstants {

  private static final int ICON_DETECTION_SIZE_MB = 50;
  private static final int IMAGE_DESCRIPTION_SIZE_MB = 110;

  /** For Describe image, there are two different types says basic & detailed description. */
  @IntDef({TYPE_BASIC_DESCRIPTION, TYPE_DETAILED_DESCRIPTION})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ImageDescriptionType {}

  public static final int TYPE_BASIC_DESCRIPTION = 0;
  public static final int TYPE_DETAILED_DESCRIPTION = 1;

  /** The state of automatic image captioning features. */
  public enum AutomaticImageCaptioningState {
    /** Image captioning won't perform automatically. */
    OFF,
    /** Image captioning will perform automatically for all images. */
    ON_ALL_IMAGES,
    /** Image captioning will perform automatically for unlabelled images. */
    ON_UNLABELLED_ONLY;
  }

  /** An enum containing string resources of the download dialog. */
  public enum DownloadDialogResources {
    ICON_DETECTION(
        R.string.confirm_download_icon_detection_title,
        R.string.confirm_download_icon_detection_message,
        ICON_DETECTION_SIZE_MB),
    IMAGE_DESCRIPTION(
        R.string.confirm_download_image_description_title,
        R.string.confirm_download_image_description_message,
        IMAGE_DESCRIPTION_SIZE_MB);

    @StringRes public final int downloadTitleRes;
    @StringRes public final int downloadMessageRes;
    public final int moduleSizeInMb;

    DownloadDialogResources(
        @StringRes int downloadTitleRes, @StringRes int downloadMessageRes, int moduleSizeInMb) {
      this.downloadTitleRes = downloadTitleRes;
      this.downloadMessageRes = downloadMessageRes;
      this.moduleSizeInMb = moduleSizeInMb;
    }
  }

  /** An enum containing string resources of the uninstall dialog. */
  public enum UninstallDialogResources {
    ICON_DETECTION(
        R.string.delete_icon_detection_dialog_title, R.string.delete_icon_description_hint),
    IMAGE_DESCRIPTION(
        R.string.delete_image_description_dialog_title, R.string.delete_image_description_hint);

    @StringRes public final int uninstallTitleRes;
    @StringRes public final int deletedHintRes;

    UninstallDialogResources(@StringRes int uninstallTitleRes, @StringRes int deletedHintRes) {
      this.uninstallTitleRes = uninstallTitleRes;
      this.deletedHintRes = deletedHintRes;
    }
  }

  /** An enum containing string resources of the feature switch dialog. */
  public enum FeatureSwitchDialogResources {
    ICON_DETECTION(
        R.string.switch_icon_detection_dialog_title,
        R.string.switch_icon_detection_dialog_message,
        R.string.pref_auto_icon_detection_key,
        R.bool.pref_auto_icon_detection_default,
        R.string.pref_auto_icon_detection_unlabelled_only_key,
        R.bool.pref_auto_icon_detection_unlabelled_only_default),

    IMAGE_DESCRIPTION(
        R.string.title_pref_image_description,
        R.string.switch_image_description_dialog_message,
        R.string.pref_auto_image_description_key,
        R.bool.pref_auto_image_description_default,
        R.string.pref_auto_image_description_unlabelled_only_key,
        R.bool.pref_auto_image_description_unlabelled_only_default),
    TEXT_RECOGNITION(
        R.string.title_pref_text_recognition,
        R.string.switch_text_recognition_dialog_message,
        R.string.pref_auto_text_recognition_key,
        R.bool.pref_auto_text_recognition_default,
        R.string.pref_auto_text_recognition_unlabelled_only_key,
        R.bool.pref_auto_text_recognition_unlabelled_only_default),

    IMAGE_DESCRIPTION_AICORE_OPT_IN(
        R.string.title_pref_image_description,
        R.string.dialog_message_on_device_ai_description,
        R.string.pref_auto_on_devices_image_description_key,
        R.bool.pref_auto_on_device_image_description_default,
        R.string.pref_auto_on_device_image_description_unlabelled_only_key,
        R.bool.pref_auto_on_device_image_description_unlabelled_only_default,
        TYPE_DETAILED_DESCRIPTION),

    IMAGE_DESCRIPTION_AICORE_SCOPE(
        R.string.title_pref_image_description,
        R.string.switch_image_description_dialog_message,
        R.string.pref_auto_on_devices_image_description_key,
        R.bool.pref_auto_on_device_image_description_default,
        R.string.pref_auto_on_device_image_description_unlabelled_only_key,
        R.bool.pref_auto_on_device_image_description_unlabelled_only_default),

    DETAILED_IMAGE_DESCRIPTION(
        R.string.title_pref_detailed_image_description,
        R.string.dialog_message_detailed_ai_description,
        R.string.pref_detailed_image_description_key,
        R.bool.pref_detailed_image_description_default,
        -1,
        -1,
        TYPE_DETAILED_DESCRIPTION);

    @StringRes public final int titleRes;
    @StringRes public final int messageRes;
    public final int switchKey;
    public final int switchDefaultValue;
    public final int switchOnUnlabelledOnlyKey;
    public final int switchOnUnlabelledOnlyDefaultValue;
    @ImageDescriptionType public final int descriptionType;

    FeatureSwitchDialogResources(
        @StringRes int titleRes,
        @StringRes int messageRes,
        int switchKey,
        int switchDefaultValue,
        int switchOnUnlabelledOnlyKey,
        int switchOnUnlabelledOnlyDefaultValue) {
      this(
          titleRes,
          messageRes,
          switchKey,
          switchDefaultValue,
          switchOnUnlabelledOnlyKey,
          switchOnUnlabelledOnlyDefaultValue,
          TYPE_BASIC_DESCRIPTION);
    }

    FeatureSwitchDialogResources(
        @StringRes int titleRes,
        @StringRes int messageRes,
        int switchKey,
        int switchDefaultValue,
        int switchOnUnlabelledOnlyKey,
        int switchOnUnlabelledOnlyDefaultValue,
        @ImageDescriptionType int descriptionType) {
      this.titleRes = titleRes;
      this.messageRes = messageRes;
      this.switchKey = switchKey;
      this.switchDefaultValue = switchDefaultValue;
      this.switchOnUnlabelledOnlyKey = switchOnUnlabelledOnlyKey;
      this.switchOnUnlabelledOnlyDefaultValue = switchOnUnlabelledOnlyDefaultValue;
      this.descriptionType = descriptionType;
    }
  }

  /** An enum containing string resources of the download state listener. */
  public enum DownloadStateListenerResources {
    ICON_DETECTION(
        R.string.download_icon_detection_successful_hint,
        R.string.download_icon_detection_failed_hint,
        R.string.downloading_icon_detection_hint),
    IMAGE_DESCRIPTION(
        R.string.download_image_description_successful_hint,
        R.string.download_image_description_failed_hint,
        R.string.downloading_image_description_hint);

    @StringRes public final int downloadSuccessfulHint;
    @StringRes public final int downloadFailedHint;
    @StringRes public final int downloadingHint;

    DownloadStateListenerResources(
        @StringRes int downloadSuccessfulHint,
        @StringRes int downloadFailedHint,
        @StringRes int downloadingHint) {
      this.downloadSuccessfulHint = downloadSuccessfulHint;
      this.downloadFailedHint = downloadFailedHint;
      this.downloadingHint = downloadingHint;
    }
  }

  /** An enum containing preference keys of image captioning. */
  public enum ImageCaptionPreferenceKeys {
    ICON_DETECTION(
        R.string.pref_icon_detection_download_dialog_shown_times,
        R.string.pref_icon_detection_download_dialog_do_no_show,
        R.string.pref_icon_detection_installed,
        R.string.pref_icon_detection_uninstalled,
        R.string.pref_auto_icon_detection_key,
        R.string.pref_auto_icon_detection_unlabelled_only_key,
        R.bool.pref_auto_icon_detection_unlabelled_only_default),
    IMAGE_DESCRIPTION(
        R.string.pref_image_description_download_dialog_shown_times,
        R.string.pref_image_description_download_dialog_do_no_show,
        R.string.pref_image_description_installed,
        R.string.pref_image_description_uninstalled,
        R.string.pref_auto_image_description_key,
        R.string.pref_auto_image_description_unlabelled_only_key,
        R.bool.pref_auto_image_description_unlabelled_only_default);

    /** A preference key to record how many times the download dialog has been shown. */
    public final int downloadShownTimesKey;

    /** A preference key to record whether TalkBack can show the download dialog again. */
    public final int doNotShowKey;

    /** A preference key to record whether user has installed the module. */
    public final int installedKey;

    /** A preference key to record whether user has uninstalled the module. */
    public final int uninstalledKey;

    // TODO: b/325531956 - creates a new constant for switch keys and default values.
    /** A preference key to record whether the automatic image caption feature is enabled. */
    public final int switchKey;

    /**
     * A preference key to record whether the automatic image caption feature is for unlabelled
     * images only.
     */
    public final int switchOnUnlabelledOnlyKey;

    /**
     * A default value of the preference key to record whether the automatic image caption feature
     * is for unlabelled images only.
     */
    public final int switchOnUnlabelledOnlyDefaultValue;

    ImageCaptionPreferenceKeys(
        int downloadShownTimesKey,
        int doNotShowKey,
        int installedKey,
        int uninstalledKey,
        int switchKey,
        int switchOnUnlabelledOnlyKey,
        int switchOnUnlabelledOnlyDefaultValue) {
      this.downloadShownTimesKey = downloadShownTimesKey;
      this.doNotShowKey = doNotShowKey;
      this.installedKey = installedKey;
      this.uninstalledKey = uninstalledKey;
      this.switchKey = switchKey;
      this.switchOnUnlabelledOnlyKey = switchOnUnlabelledOnlyKey;
      this.switchOnUnlabelledOnlyDefaultValue = switchOnUnlabelledOnlyDefaultValue;
    }
  }

  private ImageCaptionConstants() {}
}
