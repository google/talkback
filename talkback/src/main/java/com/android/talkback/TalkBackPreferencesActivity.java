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

package com.android.talkback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.talkback.HatsSurveyRequester;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.preference.TalkbackBaseFragment;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.training.OnboardingInitiator;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.RemoteIntentUtils;
import com.google.android.accessibility.utils.SettingsUtils;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Activity used to set TalkBack's service preferences.
 *
 * <p>Never change preference types. This is because of AndroidManifest.xml setting
 * android:restoreAnyVersion="true", which supports restoring preferences from a new play-store
 * installed talkback onto a clean device with older bundled talkback.
 * REFERTO
 */
public class TalkBackPreferencesActivity extends BasePreferencesActivity {

  private static final String TAG = "PreferencesActivity";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Request the HaTS.
    new HatsSurveyRequester(this).requestSurvey();
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new TalkBackPreferenceFragment();
  }

  @Override
  protected boolean supportHatsSurvey() {
    return true;
  }

  /** Fragment that holds the preference user interface controls. */
  public static class TalkBackPreferenceFragment extends TalkbackBaseFragment {

    private boolean isWatch = false;

    private Context context;

    public TalkBackPreferenceFragment() {
      super(R.xml.preferences);
    }

    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);

      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      fixListSummaries(getPreferenceScreen());
      isWatch = FeatureSupport.isWatch(activity);

      // Calling getContext() in fragment crashes on L, so use
      // getActivity().getApplicationContext() instead.
      context = getActivity().getApplicationContext();

      assignNewFeaturesIntent();
      updateSpeakPasswordsPreference();

      showTalkBackVersion();

      if (SettingsUtils.allowLinksOutOfSettings(context)) {
        assignTtsSettingsIntent();

        // We should never try to open the play store in WebActivity.
        assignPlayStoreIntentToPreference(R.string.pref_play_store_key);
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

    /**
     * In versions O and above, assign a default value for speaking passwords without headphones to
     * the system setting for speaking passwords out loud. This way, if the user already wants the
     * system to speak speak passwords out loud, the user will see no change and passwords will
     * continue to be spoken. In M and below, hide this preference.
     */
    private void updateSpeakPasswordsPreference() {
      if (FeatureSupport.useSpeakPasswordsServicePref()) {
        // Read talkback speak-passwords preference, with default to system preference.
        boolean speakPassValue = SpeakPasswordsManager.getAlwaysSpeakPasswordsPref(context);
        // Update talkback preference display to match read value.
        TwoStatePreference prefSpeakPasswords =
            (TwoStatePreference)
                findPreferenceByResId(R.string.pref_speak_passwords_without_headphones);
        if (prefSpeakPasswords != null) {
          prefSpeakPasswords.setChecked(speakPassValue);
        }
      } else {
        PreferenceSettingsUtils.hidePreference(
            context, getPreferenceScreen(), R.string.pref_speak_passwords_without_headphones);
      }
    }

    private void assignPlayStoreIntentToPreference(int preferenceId) {
      final Preference pref = findPreferenceByResId(preferenceId);
      if (pref == null) {
        return;
      }
      String packageName = PackageManagerUtils.TALBACK_PACKAGE;

      // Only for watches, try the "market://" URL first. If there is a Play Store on the
      // device, this should succeed. Only for LE devices, there will be no Play Store.
      if (isWatch) {
        Uri uri = Uri.parse("market://details?id=" + packageName);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (canHandleIntent(intent)) {
          pref.setIntent(intent);
          return;
        }
      }

      Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=" + packageName);
      Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      if (isWatch) {
        // The play.google.com URL goes to ClockworkHome which needs an extra permission,
        // just redirect to the phone.
        intent = RemoteIntentUtils.intentToOpenUriOnPhone(uri);
      } else if (!canHandleIntent(intent)) {
        getPreferenceScreen().removePreference(pref);
        return;
      }

      pref.setIntent(intent);
    }

    private boolean canHandleIntent(Intent intent) {
      Activity activity = getActivity();
      if (activity == null) {
        return false;
      }

      PackageManager manager = activity.getPackageManager();
      List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
      return infos != null && infos.size() > 0;
    }

    /** Assigns the intent to open text-to-speech settings. */
    private void assignTtsSettingsIntent() {
      PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_audio_key);
      Preference ttsSettingsPreference = findPreferenceByResId(R.string.pref_tts_settings_key);

      if (category == null || ttsSettingsPreference == null) {
        return;
      }

      Intent ttsSettingsIntent = new Intent(TalkBackService.INTENT_TTS_SETTINGS);
      ttsSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (!canHandleIntent(ttsSettingsIntent)) {
        // Need to remove preference item if no TTS Settings intent filter in settings app.
        category.removePreference(ttsSettingsPreference);
      }

      ttsSettingsPreference.setIntent(ttsSettingsIntent);
    }

    private void assignNewFeaturesIntent() {
      final Preference prefNewFeatures =
          findPreferenceByResId(R.string.pref_new_feature_talkback91_entry_point_key);

      if (prefNewFeatures == null) {
        return;
      }

      Activity activity = getActivity();
      if (activity == null || FeatureSupport.isTv(activity.getApplicationContext())) {
        return;
      }

      prefNewFeatures.setIntent(OnboardingInitiator.createOnboardingIntent(activity));
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

    private static @Nullable PackageInfo getPackageInfo(Activity activity) {
      try {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
      } catch (NameNotFoundException e) {
        return null;
      }
    }

    /** Show TalkBack version in the Play Store button. */
    private void showTalkBackVersion() {
      Activity activity = getActivity();
      if (activity == null) {
        return;
      }
      PackageInfo packageInfo = getPackageInfo(activity);
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
     * Returns the preference associated with the specified resource identifier.
     *
     * @param resId A string resource identifier.
     * @return The preference associated with the specified resource identifier.
     */
    private Preference findPreferenceByResId(int resId) {
      return findPreference(getString(resId));
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
}
