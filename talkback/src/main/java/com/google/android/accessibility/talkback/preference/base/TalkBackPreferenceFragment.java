/*
 * Copyright 2010 Google Inc.
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

import static com.google.android.accessibility.talkback.NotificationActivity.HELP_WEB_URL;
import static com.google.android.accessibility.talkback.preference.PreferencesActivityUtils.HELP_URL;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import com.android.talkback.TalkBackPreferencesActivity.HatsRequesterViewModel;
import com.google.android.accessibility.talkback.HatsSurveyRequester;
import com.google.android.accessibility.talkback.NotificationActivity;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.training.OnboardingInitiator;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.talkback.trainingcommon.tv.TvTutorialInitiator;
import com.google.android.accessibility.talkback.trainingcommon.tv.VendorConfigReader;
import com.google.android.accessibility.talkback.utils.RemoteIntentUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SettingsUtils;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Fragment that holds the preference of Talkback settings. */
public class TalkBackPreferenceFragment extends TalkbackBaseFragment {

  private Context context;
  private final FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();

  private Optional<HatsSurveyRequester> hatsSurveyRequester;

  public TalkBackPreferenceFragment() {
    super(R.xml.preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.talkback_preferences_title);
  }

  /**
   * Loads the preferences from the XML preference definition and defines an
   * onPreferenceChangeListener
   */
  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);

    context = getContext();
    if (context == null) {
      return;
    }

    fixListSummaries(getPreferenceScreen());

    HatsRequesterViewModel viewModel =
        new ViewModelProvider(getActivity()).get(HatsRequesterViewModel.class);
    hatsSurveyRequester = Optional.ofNullable(viewModel.getHatsSurveyRequester());
    hatsSurveyRequester.ifPresent(
        listener -> listener.setOnSurveyAvailableListener(() -> updateSurveyOption()));

    assignNewFeaturesIntent();

    showTalkBackVersion();

    if (SettingsUtils.allowLinksOutOfSettings(context) || formFactorUtils.isAndroidTv()) {
      assignTtsSettingsIntent();

      // We should never try to open the play store in WebActivity.
      assignPlayStoreIntentToPreference();
    } else {
      // During setup, do not allow access to web.
      PreferenceSettingsUtils.hidePreference(
          context, getPreferenceScreen(), R.string.pref_play_store_key);
      removeCategory(R.string.pref_category_legal_and_privacy_key);

      // During setup, do not allow access to other apps via custom-labeling.
      removePreference(R.string.pref_category_advanced_key, R.string.pref_manage_labels_key);

      // During setup, do not allow access to main settings via text-to-speech settings.
      removePreference(R.string.pref_category_audio_key, R.string.pref_tts_settings_key);
    }

    // Changes title from Sound and Vibration to Sound if this device doesn't support vibration.
    if (!FeatureSupport.isVibratorSupported(context)) {
      Preference preference = findPreferenceByResId(R.string.pref_sound_and_vibration_key);
      if (preference != null) {
        preference.setTitle(R.string.title_pref_sound);
      }
    }

    // Remove braille category if none of braille feature supported.
    if (!FeatureSupport.supportBrailleDisplay(context)
        && !FeatureSupport.supportBrailleKeyboard(context)) {
      removeCategory(R.string.pref_category_braille_key);
    }

    if (formFactorUtils.isAndroidTv()) {
      Preference preference = findPreferenceByResId(R.string.pref_category_help_and_feedback_key);
      if (preference != null) {
        preference.setTitle(
            TvTutorialInitiator.shouldShowTraining(VendorConfigReader.retrieveConfig(context))
                ? R.string.title_pref_category_tutorial_and_help
                : R.string.title_pref_category_help_no_tutorial);
      }
    } else if (formFactorUtils.isAndroidWear()) {
      Preference prefTutorial = findPreferenceByResId(R.string.pref_tutorial_key);
      if (prefTutorial != null) {
        prefTutorial.setIntent(TutorialInitiator.createTutorialIntent(getActivity()));
      }
      Preference prefHelp = findPreferenceByResId(R.string.pref_help_key);
      if (prefHelp != null) {
        RemoteIntentUtils.assignWebIntentToPreference(this, prefHelp, HELP_URL);
      }
    }

    if (ImageCaptioner.supportsImageCaption(context)) {
      Preference prefAutoImageCaption =
          findPreferenceByResId(R.string.pref_auto_image_captioning_key);
      if (prefAutoImageCaption != null) {
        if (ImageCaptioner.supportsIconDetection(context)) {
          if (ImageCaptioner.supportsImageDescription(context)) {
            prefAutoImageCaption.setSummary(R.string.summary_pref_auto_image_captioning);
          } else {
            // No image description.
            prefAutoImageCaption.setSummary(R.string.summary_pref_auto_image_captioning_no_image);
          }
        } else {
          if (ImageCaptioner.supportsImageDescription(context)) {
            // No icon description.
            prefAutoImageCaption.setSummary(R.string.summary_pref_auto_image_captioning_no_icon);
          } else {
            // No icon and image descriptions.
            prefAutoImageCaption.setSummary(R.string.summary_pref_auto_image_captioning_text_only);
          }
        }
      }
    } else {
      removePreference(
          R.string.pref_category_controls_key, R.string.pref_auto_image_captioning_key);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    updateSurveyOption();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    hatsSurveyRequester.ifPresent(requester -> requester.setOnSurveyAvailableListener(null));
  }

  private void removePreference(int categoryKeyId, int preferenceKeyId) {
    final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(categoryKeyId);
    if (category != null) {
      PreferenceSettingsUtils.hidePreference(context, category, preferenceKeyId);
    }
  }

  private void removeCategory(int categoryKeyId) {
    final PreferenceCategory category = (PreferenceCategory) findPreferenceByResId(categoryKeyId);
    if (category != null) {
      getPreferenceScreen().removePreference(category);
    }
  }

  private void assignPlayStoreIntentToPreference() {

    Preference pref = findPreferenceByResId(R.string.pref_play_store_key);
    if (pref == null) {
      return;
    }

    PreferenceGroup category =
        (PreferenceGroup) findPreferenceByResId(R.string.pref_category_general_key);
    if (!getResources().getBoolean(R.bool.show_play_store)) {
      if (category != null) {
        category.removePreference(pref);
      }
      return;
    }

    String packageName = PackageManagerUtils.TALKBACK_PACKAGE;

    Uri uri;
    if (formFactorUtils.isAndroidWear()) {
      // Only for watches, try the "market://" URL first. If there is a Play Store on the
      // device, this should succeed. Only for LE devices, there will be no Play Store.
      uri = Uri.parse("market://details?id=" + packageName);
    } else {
      uri = Uri.parse("https://play.google.com/store/apps/details?id=" + packageName);
    }

    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    if (canHandleIntent(intent)) {
      pref.setIntent(intent);
    } else {
      if (category != null) {
        category.removePreference(pref);
      }
    }
  }

  private boolean canHandleIntent(Intent intent) {
    PackageManager manager = context.getPackageManager();
    List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
    return infos != null && !infos.isEmpty();
  }

  /** Assigns the intent to open text-to-speech settings. */
  private void assignTtsSettingsIntent() {
    PreferenceGroup category =
        (PreferenceGroup) findPreferenceByResId(R.string.pref_category_audio_key);
    Preference ttsSettingsPreference = findPreferenceByResId(R.string.pref_tts_settings_key);

    if (category == null || ttsSettingsPreference == null) {
      return;
    }

    final String intentId =
        formFactorUtils.isAndroidTv()
            ? TalkBackService.INTENT_TTS_TV_SETTINGS
            : TalkBackService.INTENT_TTS_SETTINGS;
    Intent ttsSettingsIntent = new Intent(intentId);
    if (!canHandleIntent(ttsSettingsIntent)) {
      // Need to remove preference item if no TTS Settings intent filter in settings app.
      category.removePreference(ttsSettingsPreference);
    }

    ttsSettingsPreference.setIntent(ttsSettingsIntent);
  }

  private void assignNewFeaturesIntent() {
    final Preference prefNewFeatures =
        findPreferenceByResId(R.string.pref_new_feature_in_talkback_entry_point_key);

    if (prefNewFeatures == null) {
      return;
    }

    if (formFactorUtils.isAndroidTv()) {
      return;
    }

    Intent newFeatureIntent;
    if (formFactorUtils.isAndroidWear()) {
      newFeatureIntent =
          NotificationActivity.createStartIntent(
              context,
              R.string.notification_title_talkback_gestures_changed,
              R.string.default_action_changed_details,
              Integer.MIN_VALUE,
              R.string.talkback_built_in_gesture_open_url,
              HELP_WEB_URL);
    } else {
      newFeatureIntent = OnboardingInitiator.createOnboardingIntent(context);
    }
    prefNewFeatures.setIntent(newFeatureIntent);
  }

  private void updateSurveyOption() {
    final Preference prefSurvey =
        findPreferenceByResId(R.string.pref_survey_setting_entry_point_key);

    if (prefSurvey == null) {
      return;
    }

    if (hatsSurveyRequester.isEmpty()) {
      prefSurvey.setVisible(false);
      return;
    }

    if (!hatsSurveyRequester.get().isSurveyAvailable()) {
      prefSurvey.setVisible(false);
      return;
    }

    prefSurvey.setVisible(true);
    prefSurvey.setOnPreferenceClickListener(
        preference -> {
          hatsSurveyRequester.ifPresent(
              requester -> {
                boolean nouse = requester.presentCachedSurvey();
              });
          prefSurvey.setVisible(false);
          return true;
        });
  }

  /**
   * Since the "%s" summary is currently broken, this sets the preference change listener for all
   * {@link ListPreference} views to fill in the summary with the current entry value.
   */
  private void fixListSummaries(PreferenceGroup group) {
    if (group == null) {
      return;
    }

    final int count = group.getPreferenceCount();

    for (int i = 0; i < count; i++) {
      final Preference preference = group.getPreference(i);

      if (preference instanceof PreferenceGroup) {
        fixListSummaries((PreferenceGroup) preference);
      } else if (preference instanceof ListPreference) {
        // First make sure the current summary is correct, then set the
        // listener. This is necessary for summaries to show correctly
        // on SDKs < 14.
        preferenceChangeListener.onPreferenceChange(
            preference, ((ListPreference) preference).getValue());

        preference.setOnPreferenceChangeListener(preferenceChangeListener);
      }
    }
  }

  private static @Nullable PackageInfo getPackageInfo(Context context) {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (NameNotFoundException e) {
      return null;
    }
  }

  /** Show TalkBack version in the Play Store button. */
  private void showTalkBackVersion() {
    PackageInfo packageInfo = getPackageInfo(context);
    if (packageInfo == null) {
      return;
    }
    final Preference playStoreButton = findPreferenceByResId(R.string.pref_play_store_key);
    if (playStoreButton == null) {
      return;
    }
    Pattern pattern = Pattern.compile("[0-9]+\\.[0-9]+");
    Matcher matcher = pattern.matcher(String.valueOf(packageInfo.versionName));
    String summary;
    if (matcher.find()) {
      summary = getString(R.string.summary_pref_play_store, matcher.group());
    } else {
      summary =
          getString(R.string.summary_pref_play_store, String.valueOf(packageInfo.versionName));
    }
    playStoreButton.setSummary(summary);
  }

  /**
   * Listens for preference changes and updates the summary to reflect the current setting. This
   * shouldn't be necessary, since preferences are supposed to automatically do this when the
   * summary is set to "%s".
   */
  private final OnPreferenceChangeListener preferenceChangeListener =
      new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          if (preference instanceof ListPreference && newValue instanceof String) {
            final ListPreference listPreference = (ListPreference) preference;
            final int index = listPreference.findIndexOfValue((String) newValue);
            final CharSequence[] entries = listPreference.getEntries();

            if (index >= 0 && index < entries.length) {
              preference.setSummary(entries[index].toString().replaceAll("%", "%%"));
            } else {
              preference.setSummary("");
            }
          }

          return true;
        }
      };
}
