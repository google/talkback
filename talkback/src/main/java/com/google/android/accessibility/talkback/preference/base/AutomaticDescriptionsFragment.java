/*
 * Copyright 2023 Google Inc.
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

package com.google.android.accessibility.talkback.preference.base;

import static com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.Requester.SETTINGS;
import static com.google.android.accessibility.talkback.imagecaption.CaptionRequest.ERROR_INSUFFICIENT_STORAGE;
import static com.google.android.accessibility.talkback.imagecaption.CaptionRequest.ERROR_NETWORK_ERROR;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.StringRes;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackAnalyticsImpl;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.ImageCaptionLogKeys;
import com.google.android.accessibility.talkback.dynamicfeature.FeatureDownloader;
import com.google.android.accessibility.talkback.dynamicfeature.IconDetectionModuleDownloadPrompter;
import com.google.android.accessibility.talkback.dynamicfeature.ImageDescriptionModuleDownloadPrompter;
import com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter;
import com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.DownloadStateListener;
import com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.UninstallStateListener;
import com.google.android.accessibility.talkback.imagecaption.CaptionRequest.ErrorCode;
import com.google.android.accessibility.talkback.imagecaption.FeatureSwitchDialog;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.DownloadDialogResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.DownloadStateListenerResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.FeatureSwitchDialogResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.ImageCaptionPreferenceKeys;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.UninstallDialogResources;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A {@link TalkbackBaseFragment} to hold a set of automatic descriptions preferences. */
public class AutomaticDescriptionsFragment extends TalkbackBaseFragment {

  private static final String TAG = "AutomaticDescriptionsFragment";
  private Context context;
  private SharedPreferences prefs;
  private FeatureDownloader featureDownloader;
  private @Nullable IconDetectionModuleDownloadPrompter iconDetectionModuleDownloadPrompter;
  private @Nullable ImageDescriptionModuleDownloadPrompter imageDescriptionModuleDownloadPrompter;

  public AutomaticDescriptionsFragment() {
    super(R.xml.automatic_descriptions_preferences);
  }

  @Override
  protected CharSequence getTitle() {
    return getText(R.string.title_pref_auto_image_captioning);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    context = getContext();
    if (context == null) {
      return;
    }
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    featureDownloader = FeatureDownloader.getInstance(context);
    featureDownloader.updateAllInstallStatuses();

    setupIconDetectionPreference();
    setupImageDescriptionPreference();
    setupTextRecognitionPreference();
  }

  @Override
  public void onDestroy() {
    if (iconDetectionModuleDownloadPrompter != null) {
      iconDetectionModuleDownloadPrompter.shutdown();
      iconDetectionModuleDownloadPrompter = null;
    }

    if (imageDescriptionModuleDownloadPrompter != null) {
      imageDescriptionModuleDownloadPrompter.shutdown();
      imageDescriptionModuleDownloadPrompter = null;
    }

    super.onDestroy();
  }

  private void setupIconDetectionPreference() {
    Preference iconDetectionPreference = findPreferenceByResId(R.string.pref_icon_detection_key);
    if (iconDetectionPreference == null) {
      return;
    }

    if (!ImageCaptioner.supportsIconDetection(context)) {
      removePreference(iconDetectionPreference);
      return;
    }

    if (iconDetectionModuleDownloadPrompter == null) {
      iconDetectionModuleDownloadPrompter =
          new IconDetectionModuleDownloadPrompter(context, featureDownloader);
    }

    iconDetectionModuleDownloadPrompter.setDownloadStateListener(
        new AutomaticDescriptionDownloadStateListener(
            iconDetectionPreference,
            DownloadStateListenerResources.ICON_DETECTION,
            ImageCaptionPreferenceKeys.ICON_DETECTION,
            DownloadDialogResources.ICON_DETECTION.moduleSizeInMb,
            ImageCaptionLogKeys.ICON_DETECTION));
    iconDetectionModuleDownloadPrompter.setUninstallStateListener(
        new AutomaticDescriptionUninstallStateListener(
            iconDetectionPreference,
            ImageCaptionPreferenceKeys.ICON_DETECTION,
            ImageCaptionLogKeys.IMAGE_DESCRIPTION,
            UninstallDialogResources.ICON_DETECTION));

    setupPreferenceForDynamicFeature(
        iconDetectionPreference,
        iconDetectionModuleDownloadPrompter,
        FeatureSwitchDialogResources.ICON_DETECTION);
  }

  private void setupImageDescriptionPreference() {
    Preference imageDescriptionPreference =
        findPreferenceByResId(R.string.pref_image_description_key);
    if (imageDescriptionPreference == null) {
      return;
    }

    if (!ImageCaptioner.supportsImageDescription(context)) {
      removePreference(imageDescriptionPreference);
      return;
    }

    if (imageDescriptionModuleDownloadPrompter == null) {
      imageDescriptionModuleDownloadPrompter =
          new ImageDescriptionModuleDownloadPrompter(context, featureDownloader);
    }

    imageDescriptionModuleDownloadPrompter.setDownloadStateListener(
        new AutomaticDescriptionDownloadStateListener(
            imageDescriptionPreference,
            DownloadStateListenerResources.IMAGE_DESCRIPTION,
            ImageCaptionPreferenceKeys.IMAGE_DESCRIPTION,
            DownloadDialogResources.IMAGE_DESCRIPTION.moduleSizeInMb,
            ImageCaptionLogKeys.IMAGE_DESCRIPTION));
    imageDescriptionModuleDownloadPrompter.setUninstallStateListener(
        new AutomaticDescriptionUninstallStateListener(
            imageDescriptionPreference,
            ImageCaptionPreferenceKeys.IMAGE_DESCRIPTION,
            ImageCaptionLogKeys.IMAGE_DESCRIPTION,
            UninstallDialogResources.IMAGE_DESCRIPTION));

    setupPreferenceForDynamicFeature(
        imageDescriptionPreference,
        imageDescriptionModuleDownloadPrompter,
        FeatureSwitchDialogResources.IMAGE_DESCRIPTION);
  }

  /** Updates the summary of preference and sets {@link OnPreferenceClickListener}. */
  private void setupPreferenceForDynamicFeature(
      Preference preference,
      ModuleDownloadPrompter moduleDownloadPrompter,
      FeatureSwitchDialogResources switchDialogResources) {
    // The summary of preference will not be saved when exiting the Settings page, so they should be
    // restored when the preference is created.
    if (moduleDownloadPrompter.isModuleDownloading()) {
      // The module is downloading.
      preference.setSummary(R.string.summary_pref_module_downloading);
    } else if (moduleDownloadPrompter.isModuleAvailable()
        && !moduleDownloadPrompter.isUninstalled()) {
      // The module is available.
      if (prefs.contains(context.getString(switchDialogResources.switchKey))) {
        if (getBooleanPref(
            switchDialogResources.switchKey, switchDialogResources.switchDefaultValue)) {
          preference.setSummary(R.string.summary_pref_feature_enabled);
        } else {
          preference.setSummary(R.string.summary_pref_feature_disabled);
        }
      } else {
        // The module has been downloaded and the feature is enabled by default.
        putBooleanPref(switchDialogResources.switchKey, true);
        preference.setSummary(R.string.summary_pref_feature_enabled);
      }
    } else {
      preference.setSummary(R.string.summary_pref_feature_disabled);
    }

    preference.setOnPreferenceClickListener(
        pref -> {
          if (moduleDownloadPrompter.needDownloadDialog(SETTINGS)) {
            // Shows the dialog to confirm the download the module.
            moduleDownloadPrompter.showDownloadDialog(SETTINGS);
          } else {
            new FeatureSwitchDialog(context, switchDialogResources, /* isDeletable= */ true) {
              @Override
              public void handleDialogClick(int buttonClicked) {
                super.handleDialogClick(buttonClicked);
                switch (buttonClicked) {
                  case DialogInterface.BUTTON_POSITIVE:
                    if (getBooleanPref(
                        switchDialogResources.switchKey,
                        switchDialogResources.switchDefaultValue)) {
                      preference.setSummary(R.string.summary_pref_feature_enabled);
                    } else {
                      preference.setSummary(R.string.summary_pref_feature_disabled);
                    }
                    return;
                  case DialogInterface.BUTTON_NEGATIVE:
                    LogUtils.v(TAG, "Requests a uninstallation.");
                    moduleDownloadPrompter.showUninstallDialog();
                    return;
                  default:
                    // do nothing.
                }
              }
            }.showDialog();
          }
          return true;
        });
  }

  private void setupTextRecognitionPreference() {
    Preference textRecognitionPreference =
        findPreferenceByResId(R.string.pref_text_recognition_key);
    if (textRecognitionPreference == null) {
      return;
    }

    if (getBooleanPref(
        R.string.pref_auto_text_recognition_key, R.bool.pref_auto_text_recognition_default)) {
      textRecognitionPreference.setSummary(R.string.summary_pref_feature_enabled);
    } else {
      textRecognitionPreference.setSummary(R.string.summary_pref_feature_disabled);
    }

    textRecognitionPreference.setOnPreferenceClickListener(
        preference -> {
          new FeatureSwitchDialog(
              context, FeatureSwitchDialogResources.TEXT_RECOGNITION, /* isDeletable= */ false) {
            @Override
            public void handleDialogClick(int buttonClicked) {
              super.handleDialogClick(buttonClicked);
              if (getBooleanPref(
                  R.string.pref_auto_text_recognition_key,
                  R.bool.pref_auto_text_recognition_default)) {
                textRecognitionPreference.setSummary(R.string.summary_pref_feature_enabled);
              } else {
                textRecognitionPreference.setSummary(R.string.summary_pref_feature_disabled);
              }
            }
          }.showDialog();
          return true;
        });
  }

  private void updatePreferenceSummary(Preference preference, @StringRes int summary) {
    if (!isVisible() || !preference.isVisible()) {
      // The fragment is stopped, the preference needn't be updated.
      return;
    }
    preference.setSummary(summary);
  }

  private void removePreference(Preference preference) {
    getPreferenceScreen().removePreference(preference);
  }

  private boolean getBooleanPref(int key, int defaultValue) {
    return SharedPreferencesUtils.getBooleanPref(prefs, context.getResources(), key, defaultValue);
  }

  private void putBooleanPref(int key, boolean value) {
    SharedPreferencesUtils.putBooleanPref(prefs, context.getResources(), key, value);
  }

  private static void showToast(Context context, @StringRes int text) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show();
  }

  private static void showToast(Context context, String text) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show();
  }

  private class AutomaticDescriptionDownloadStateListener implements DownloadStateListener {

    private final Preference preference;
    private final DownloadStateListenerResources listenerResources;
    private final ImageCaptionPreferenceKeys preferenceKeys;
    private final int moduleSize;
    private final ImageCaptionLogKeys logKeys;

    private AutomaticDescriptionDownloadStateListener(
        Preference preference,
        DownloadStateListenerResources listenerResources,
        ImageCaptionPreferenceKeys preferenceKeys,
        int moduleSize,
        ImageCaptionLogKeys logKeys) {
      this.preference = preference;
      this.listenerResources = listenerResources;
      this.preferenceKeys = preferenceKeys;
      this.moduleSize = moduleSize;
      this.logKeys = logKeys;
    }

    @Override
    public void onInstalled() {
      updatePreferenceSummary(preference, R.string.summary_pref_feature_enabled);
      // Message will send to TTS directly if TalkBack is active, no need to show the toast.
      if (!TalkBackService.isServiceActive()) {
        TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(
            prefs, context, logKeys.installSuccess);
        showToast(context, listenerResources.downloadSuccessfulHint);
      }
    }

    @Override
    public void onFailed(@ErrorCode int errorCode) {
      updatePreferenceSummary(preference, R.string.summary_pref_feature_disabled);
      // Message will send to TTS directly if TalkBack is active, no need to show the toast.
      if (!TalkBackService.isServiceActive()) {
        TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(prefs, context, logKeys.installFail);
        switch (errorCode) {
          case ERROR_NETWORK_ERROR:
            showToast(context, R.string.download_network_error_hint);
            break;
          case ERROR_INSUFFICIENT_STORAGE:
            showToast(context, getString(R.string.download_storage_error_hint, moduleSize));
            break;
          default:
            showToast(context, listenerResources.downloadFailedHint);
        }
      }
    }

    @Override
    public void onAccepted() {
      TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(prefs, context, logKeys.installRequest);
      updatePreferenceSummary(preference, R.string.summary_pref_module_downloading);
      putBooleanPref(preferenceKeys.uninstalledKey, false);
    }

    @Override
    public void onRejected() {
      TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(prefs, context, logKeys.installDeny);
    }

    @Override
    public void onDialogDismissed(@Nullable AccessibilityNodeInfoCompat queuedNode) {}
  }

  private class AutomaticDescriptionUninstallStateListener implements UninstallStateListener {

    private final Preference preference;
    private final ImageCaptionPreferenceKeys preferenceKeys;
    private final ImageCaptionLogKeys logKeys;
    private final UninstallDialogResources uninstallDialogResources;

    private AutomaticDescriptionUninstallStateListener(
        Preference preference,
        ImageCaptionPreferenceKeys preferenceKeys,
        ImageCaptionLogKeys logKeys,
        UninstallDialogResources uninstallDialogResources) {
      this.preference = preference;
      this.preferenceKeys = preferenceKeys;
      this.logKeys = logKeys;
      this.uninstallDialogResources = uninstallDialogResources;
    }

    @Override
    public void onAccepted() {
      TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(
          prefs, context, logKeys.uninstallRequest);
      updatePreferenceSummary(preference, R.string.summary_pref_feature_disabled);
      prefs
          .edit()
          .putBoolean(context.getString(preferenceKeys.uninstalledKey), true)
          // Uninstall lib guarantees the installed key to be false.
          .putBoolean(context.getString(preferenceKeys.installedKey), false)
          .putBoolean(context.getString(preferenceKeys.switchKey), false)
          .apply();
      showToast(context, uninstallDialogResources.deletedHintRes);
    }

    @Override
    public void onRejected() {
      TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(prefs, context, logKeys.uninstallDeny);
    }
  }
}
