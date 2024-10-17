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

import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.GEMINI_OPT_IN_CONSENT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.GEMINI_OPT_IN_DISSENT;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.GEMINI_OPT_IN_SHOW_DIALOG;
import static com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.Requester.SETTINGS;
import static com.google.android.accessibility.talkback.imagecaption.CaptionRequest.ERROR_INSUFFICIENT_STORAGE;
import static com.google.android.accessibility.talkback.imagecaption.CaptionRequest.ERROR_NETWORK_ERROR;
import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.FeatureSwitchDialogResources.IMAGE_DESCRIPTION_AICORE_OPT_IN;
import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionUtils.getAutomaticImageCaptioningState;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackAnalyticsImpl;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.actor.gemini.AiCoreEndpoint;
import com.google.android.accessibility.talkback.actor.gemini.GeminiConfiguration;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.ImageCaptionLogKeys;
import com.google.android.accessibility.talkback.dynamicfeature.Downloader;
import com.google.android.accessibility.talkback.dynamicfeature.DownloaderFactory;
import com.google.android.accessibility.talkback.dynamicfeature.IconDetectionModuleDownloadPrompter;
import com.google.android.accessibility.talkback.dynamicfeature.ImageDescriptionModuleDownloadPrompter;
import com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter;
import com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.DownloadStateListener;
import com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.UninstallStateListener;
import com.google.android.accessibility.talkback.imagecaption.FeatureSwitchDialog;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.AutomaticImageCaptioningState;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.DownloadDialogResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.DownloadStateListenerResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.FeatureSwitchDialogResources;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.ImageCaptionPreferenceKeys;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.UninstallDialogResources;
import com.google.android.accessibility.talkback.imagecaption.Request.ErrorCode;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A {@link TalkbackBaseFragment} to hold a set of automatic descriptions preferences. */
public class AutomaticDescriptionsFragment extends TalkbackBaseFragment {

  private static final String TAG = "AutomaticDescriptionsFragment";
  private Context context;
  private SharedPreferences prefs;
  private Downloader downloader;
  private @Nullable IconDetectionModuleDownloadPrompter iconDetectionModuleDownloadPrompter;
  private @Nullable ImageDescriptionModuleDownloadPrompter imageDescriptionModuleDownloadPrompter;
  private @Nullable AiCoreEndpoint aiCoreEndpoint;
  private @Nullable ListenableFuture<Boolean> hasAiCoreFuture;
  private Optional<Boolean> useAiCore = Optional.empty();

  public AutomaticDescriptionsFragment() {
    super(R.xml.automatic_descriptions_preferences);
  }

  @Override
  protected CharSequence getTitle() {
    return getText(R.string.title_pref_icon_image_description);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    context = getContext();
    if (context == null) {
      return;
    }
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    downloader = DownloaderFactory.create(context);
    downloader.updateAllDownloadStatus();

    if (aiCoreEndpoint == null) {
      aiCoreEndpoint = new AiCoreEndpoint(context, /* withService= */ false);
    }
    hasAiCoreFuture = aiCoreEndpoint.hasAiCoreAsynchronous();

    setupIconDetectionPreference();
    setupImageDescriptionPreference();
    setupTextRecognitionPreference();
    setupDetailedImageDescriptionPreference(
        FeatureSwitchDialogResources.DETAILED_IMAGE_DESCRIPTION);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (useAiCore.isPresent()) {
      // Refresh the UI of the image description preference.
      Preference imageDescriptionPreference =
          findPreferenceByResId(R.string.pref_image_description_key);

      if (imageDescriptionPreference == null || !ImageCaptioner.supportsIconDetection(context)) {
        return;
      }

      if (useAiCore.get()) {
        setupPreferenceForGeminiNano(imageDescriptionPreference);
      } else {
        setupPreferenceForGarcon(imageDescriptionPreference);
      }
    }
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

    if (aiCoreEndpoint != null) {
      aiCoreEndpoint.onUnbind();
      aiCoreEndpoint = null;
    }

    super.onDestroy();
  }

  @VisibleForTesting
  void setModuleDownloadPrompter(
      IconDetectionModuleDownloadPrompter iconDetectionModuleDownloadPrompter,
      ImageDescriptionModuleDownloadPrompter imageDescriptionModuleDownloadPrompter) {
    this.iconDetectionModuleDownloadPrompter = iconDetectionModuleDownloadPrompter;
    this.imageDescriptionModuleDownloadPrompter = imageDescriptionModuleDownloadPrompter;
  }

  @VisibleForTesting
  void setAiCoreEndpoint(AiCoreEndpoint endpoint) {
    if (aiCoreEndpoint != null) {
      aiCoreEndpoint.onUnbind();
    }
    aiCoreEndpoint = endpoint;
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
          new IconDetectionModuleDownloadPrompter(context, downloader);
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

    Futures.addCallback(
        hasAiCoreFuture,
        new FutureCallback<Boolean>() {
          @Override
          public void onSuccess(Boolean hasAiCore) {
            if (hasAiCore && GeminiConfiguration.isOnDeviceGeminiImageCaptioningEnabled(context)) {
              useAiCore = Optional.of(true);
              getActivity()
                  .runOnUiThread(() -> setupPreferenceForGeminiNano(imageDescriptionPreference));
            } else {
              useAiCore = Optional.of(false);
              getActivity()
                  .runOnUiThread(() -> setupPreferenceForGarcon(imageDescriptionPreference));
            }
          }

          @Override
          public void onFailure(Throwable t) {
            useAiCore = Optional.of(false);
            getActivity().runOnUiThread(() -> setupPreferenceForGarcon(imageDescriptionPreference));
          }
        },
        directExecutor());
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
      if (!prefs.contains(context.getString(switchDialogResources.switchKey))) {
        // The module has been downloaded and the feature is enabled by default.
        putBooleanPref(switchDialogResources.switchKey, true);
      }
      preference.setSummary(
          getSummaryFromFeatureSwitchDialog(context, prefs, switchDialogResources));
    } else {
      preference.setSummary(R.string.summary_pref_auto_image_captioning_disabled);
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
                    preference.setSummary(
                        getSummaryFromFeatureSwitchDialog(context, prefs, switchDialogResources));
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

  private void setupPreferenceForGarcon(Preference imageDescriptionPreference) {
    if (imageDescriptionModuleDownloadPrompter == null) {
      imageDescriptionModuleDownloadPrompter =
          new ImageDescriptionModuleDownloadPrompter(context, downloader);
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

  private void setupPreferenceForGeminiNano(Preference optInPreference) {
    FeatureSwitchDialogResources switchDialogResources;
    if (getBooleanPref(
        IMAGE_DESCRIPTION_AICORE_OPT_IN.switchKey,
        IMAGE_DESCRIPTION_AICORE_OPT_IN.switchDefaultValue)) {
      switchDialogResources = FeatureSwitchDialogResources.IMAGE_DESCRIPTION_AICORE_SCOPE;
      optInPreference.setSummary(
          getSummaryFromFeatureSwitchDialog(context, prefs, switchDialogResources));
      optInPreference.setOnPreferenceClickListener(
          pref -> {
            if (displayDialogForGeminiNano(optInPreference)) {
              return true;
            }

            new FeatureSwitchDialog(context, switchDialogResources, /* isDeletable= */ false) {
              @Override
              public void handleDialogClick(int buttonClicked) {
                super.handleDialogClick(buttonClicked);
                switch (buttonClicked) {
                  case DialogInterface.BUTTON_POSITIVE:
                    setupPreferenceForGeminiNano(optInPreference);
                    return;
                  case DialogInterface.BUTTON_NEGATIVE:
                    return;
                  default:
                    // do nothing.
                }
              }
            }.setIncludeNegativeButton(false).showDialog();

            return true;
          });
    } else {
      switchDialogResources = IMAGE_DESCRIPTION_AICORE_OPT_IN;
      FeatureSwitchDialog optinDialog =
          new FeatureSwitchDialog(
              context, switchDialogResources, /* isDeletable= */ false, R.string.enable_gemini) {
            @Override
            public void handleDialogClick(int buttonClicked) {
              switch (buttonClicked) {
                case DialogInterface.BUTTON_POSITIVE:
                  SharedPreferencesUtils.putBooleanPref(
                      prefs, context.getResources(), switchDialogResources.switchKey, true);
                  setupPreferenceForGeminiNano(optInPreference);
                  return;
                case DialogInterface.BUTTON_NEGATIVE:
                  return;
                default:
                  // do nothing.
              }
            }
          };

      optInPreference.setSummary(R.string.summary_pref_auto_image_captioning_disabled);
      optInPreference.setOnPreferenceClickListener(
          pref -> {
            if (displayDialogForGeminiNano(optInPreference)) {
              return true;
            }

            optinDialog.showDialog();
            return true;
          });
    }
  }

  private boolean displayDialogForGeminiNano(Preference optInPreference) {
    if (aiCoreEndpoint != null) {
      if (aiCoreEndpoint.needAiCoreUpdate()) {
        aiCoreEndpoint.displayAiCoreUpdateDialog();
        return true;
      } else if (aiCoreEndpoint.needAstreaUpdate()) {
        aiCoreEndpoint.displayAstreaUpdateDialog();
        return true;
      } else if (aiCoreEndpoint.isAiFeatureDownloadable()) {
        // Show the feature download dialog if the feature state backs to DOWNLOADABLE.
        aiCoreEndpoint.displayAiFeatureDownloadDialog(
            unused -> {
              setupPreferenceForGeminiNano(optInPreference);
            });
        return true;
      }
    }

    return false;
  }

  private boolean getBooleanPref(int key, int defaultValue) {
    return SharedPreferencesUtils.getBooleanPref(prefs, context.getResources(), key, defaultValue);
  }

  private void setupDetailedImageDescriptionPreference(
      FeatureSwitchDialogResources switchDialogResources) {
    SwitchPreference optInPreference =
        (SwitchPreference) findPreferenceByResId(R.string.pref_detailed_image_description_key);
    if (optInPreference == null) {
      return;
    }
    String summary =
        getString(R.string.summary_pref_ai_description, getString(R.string.title_image_caption));
    optInPreference.setSummary(summary);
    optInPreference.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          if (Boolean.TRUE.equals(newValue)) {
            new FeatureSwitchDialog(
                context, switchDialogResources, /* isDeletable= */ false, R.string.enable_gemini) {
              @Override
              public void handleDialogClick(int buttonClicked) {
                super.handleDialogClick(buttonClicked);
                switch (buttonClicked) {
                  case DialogInterface.BUTTON_POSITIVE:
                    optInPreference.setChecked(true);
                    TalkBackAnalyticsImpl.onGeminiOptInFromSettings(
                        prefs, context, GEMINI_OPT_IN_CONSENT);
                    return;
                  case DialogInterface.BUTTON_NEGATIVE:
                    LogUtils.v(TAG, "Does not accept the Opt-in.");
                    TalkBackAnalyticsImpl.onGeminiOptInFromSettings(
                        prefs, context, GEMINI_OPT_IN_DISSENT);
                    return;
                  default:
                    // do nothing.
                }
              }
            }.showDialog();
            TalkBackAnalyticsImpl.onGeminiOptInFromSettings(
                prefs, context, GEMINI_OPT_IN_SHOW_DIALOG);
            return false;
          } else {
            return true;
          }
        });
  }

  private void setupTextRecognitionPreference() {
    Preference textRecognitionPreference =
        findPreferenceByResId(R.string.pref_text_recognition_key);
    if (textRecognitionPreference == null) {
      return;
    }

    textRecognitionPreference.setSummary(
        getSummaryFromFeatureSwitchDialog(
            context, prefs, FeatureSwitchDialogResources.TEXT_RECOGNITION));

    textRecognitionPreference.setOnPreferenceClickListener(
        preference -> {
          new FeatureSwitchDialog(
              context, FeatureSwitchDialogResources.TEXT_RECOGNITION, /* isDeletable= */ false) {
            @Override
            public void handleDialogClick(int buttonClicked) {
              super.handleDialogClick(buttonClicked);
              textRecognitionPreference.setSummary(
                  getSummaryFromFeatureSwitchDialog(
                      context, prefs, FeatureSwitchDialogResources.TEXT_RECOGNITION));
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

  private void putBooleanPref(int key, boolean value) {
    SharedPreferencesUtils.putBooleanPref(prefs, context.getResources(), key, value);
  }

  private static void showToast(Context context, @StringRes int text) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show();
  }

  private static void showToast(Context context, String text) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show();
  }

  @StringRes
  private static int getSummaryFromFeatureSwitchDialog(
      Context context, SharedPreferences prefs, FeatureSwitchDialogResources featureResource) {
    AutomaticImageCaptioningState state =
        getAutomaticImageCaptioningState(context, prefs, featureResource);
    switch (state) {
      case ON_ALL_IMAGES:
        if (featureResource == FeatureSwitchDialogResources.ICON_DETECTION) {
          return R.string.summary_pref_auto_icon_detection_enabled;
        } else {
          return R.string.summary_pref_auto_image_captioning_enabled;
        }
      case ON_UNLABELLED_ONLY:
        if (featureResource == FeatureSwitchDialogResources.ICON_DETECTION) {
          return R.string.summary_pref_auto_icon_detection_enabled_unlabelled_only;
        } else {
          return R.string.summary_pref_auto_image_captioning_enabled_unlabelled_only;
        }
      case OFF:
        if (featureResource == FeatureSwitchDialogResources.ICON_DETECTION) {
          return R.string.summary_pref_auto_icon_detection_disabled;
        } else {
          return R.string.summary_pref_auto_image_captioning_disabled;
        }
    }

    return -1;
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
      FeatureSwitchDialogResources type =
          listenerResources == DownloadStateListenerResources.ICON_DETECTION
              ? FeatureSwitchDialogResources.ICON_DETECTION
              : FeatureSwitchDialogResources.IMAGE_DESCRIPTION;
      updatePreferenceSummary(preference, getSummaryFromFeatureSwitchDialog(context, prefs, type));
      // Message will send to TTS directly if TalkBack is active, no need to show the toast.
      if (!TalkBackService.isServiceActive()) {
        TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(
            prefs, context, logKeys.installSuccess);
        showToast(context, listenerResources.downloadSuccessfulHint);
      }
    }

    @Override
    public void onFailed(@ErrorCode int errorCode) {
      updatePreferenceSummary(preference, R.string.summary_pref_auto_image_captioning_disabled);
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
      updatePreferenceSummary(preference, R.string.summary_pref_auto_image_captioning_disabled);
      prefs
          .edit()
          .putBoolean(context.getString(preferenceKeys.uninstalledKey), true)
          // Uninstall lib guarantees the installed key to be false.
          .putBoolean(context.getString(preferenceKeys.installedKey), false)
          .putBoolean(context.getString(preferenceKeys.switchKey), false)
          // The key will be set as default once the library is uninstalled.
          .remove(context.getString(preferenceKeys.switchOnUnlabelledOnlyKey))
          .apply();
      showToast(context, uninstallDialogResources.deletedHintRes);
    }

    @Override
    public void onRejected() {
      TalkBackAnalyticsImpl.onImageCaptionEventFromSettings(prefs, context, logKeys.uninstallDeny);
    }
  }
}
