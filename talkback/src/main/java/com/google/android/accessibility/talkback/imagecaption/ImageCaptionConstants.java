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

import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;

/** A class containing string resources and preference keys of image captioning. */
public final class ImageCaptionConstants {

  private static final int ICON_DETECTION_SIZE_MB = 50;
  private static final int IMAGE_DESCRIPTION_SIZE_MB = 110;

  /** An enum containing string resources of the download dialog. */
  public enum DownloadDialogResources {
    ICON_DETECTION(
        R.string.confirm_download_icon_detection_title,
        R.string.confirm_download_icon_detection_message_via_menu,
        R.string.confirm_download_icon_detection_message_via_settings,
        ICON_DETECTION_SIZE_MB),
    IMAGE_DESCRIPTION(
        R.string.confirm_download_image_description_title,
        R.string.confirm_download_image_description_message_via_menu,
        R.string.confirm_download_image_description_message_via_settings,
        IMAGE_DESCRIPTION_SIZE_MB);

    @StringRes public final int downloadTitleRes;
    @StringRes public final int downloadMessageForMenuRes;
    @StringRes public final int downloadMessageForSettingsRes;
    public final int moduleSizeInMb;

    DownloadDialogResources(
        @StringRes int downloadTitleRes,
        @StringRes int downloadMessageForMenuRes,
        @StringRes int downloadMessageForSettingsRes,
        int moduleSizeInMb) {
      this.downloadTitleRes = downloadTitleRes;
      this.downloadMessageForMenuRes = downloadMessageForMenuRes;
      this.downloadMessageForSettingsRes = downloadMessageForSettingsRes;
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
        R.bool.pref_auto_icon_detection_default),
    IMAGE_DESCRIPTION(
        R.string.switch_image_description_dialog_title,
        R.string.switch_image_description_dialog_message,
        R.string.pref_auto_image_description_key,
        R.bool.pref_auto_image_description_default),
    TEXT_RECOGNITION(
        R.string.switch_text_recognition_dialog_title,
        R.string.switch_text_recognition_dialog_message,
        R.string.pref_auto_text_recognition_key,
        R.bool.pref_auto_text_recognition_default);

    @StringRes public final int titleRes;
    @StringRes public final int messageRes;
    public final int switchKey;
    public final int switchDefaultValue;

    FeatureSwitchDialogResources(
        @StringRes int titleRes, @StringRes int messageRes, int switchKey, int switchDefaultValue) {
      this.titleRes = titleRes;
      this.messageRes = messageRes;
      this.switchKey = switchKey;
      this.switchDefaultValue = switchDefaultValue;
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
        R.string.pref_auto_icon_detection_key),
    IMAGE_DESCRIPTION(
        R.string.pref_image_description_download_dialog_shown_times,
        R.string.pref_image_description_download_dialog_do_no_show,
        R.string.pref_image_description_installed,
        R.string.pref_image_description_uninstalled,
        R.string.pref_auto_image_description_key);

    /** A preference key to record how many times the download dialog has been shown. */
    public final int downloadShownTimesKey;
    /** A preference key to record whether TalkBack can show the download dialog again. */
    public final int doNotShowKey;
    /** A preference key to record whether user has installed the module. */
    public final int installedKey;
    /** A preference key to record whether user has uninstalled the module. */
    public final int uninstalledKey;
    /** A preference key to record whether the automatic image caption feature is enabled. */
    public final int switchKey;

    ImageCaptionPreferenceKeys(
        int downloadShownTimesKey,
        int doNotShowKey,
        int installedKey,
        int uninstalledKey,
        int switchKey) {
      this.downloadShownTimesKey = downloadShownTimesKey;
      this.doNotShowKey = doNotShowKey;
      this.installedKey = installedKey;
      this.uninstalledKey = uninstalledKey;
      this.switchKey = switchKey;
    }
  }

  private ImageCaptionConstants() {}
}
