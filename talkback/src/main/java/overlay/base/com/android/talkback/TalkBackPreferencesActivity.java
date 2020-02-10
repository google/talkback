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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import androidx.appcompat.app.ActionBar;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.talkback.HelpAndFeedbackUtils;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.controller.DimScreenControllerApp;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.tutorial.AccessibilityTutorialActivity;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.RemoteIntentUtils;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WebActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Activity used to set TalkBack's service preferences.
 *
 * <p>Never change preference types. This is because of AndroidManifest.xml setting
 * android:restoreAnyVersion="true", which supports restoring preferences from a new play-store
 * installed talkback onto a clean device with older bundled talkback. See
 * 
 */
public class TalkBackPreferencesActivity extends BasePreferencesActivity {

  private static final String TAG = "PreferencesActivity";

  public static final String TUTORIAL_SRC = "preference";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Shows TalkBack's abbreviated version number in the action bar,
    ActionBar actionBar = getSupportActionBar();
    PackageInfo packageInfo = TalkBackPreferenceFragment.getPackageInfo(this);
    if (actionBar != null && packageInfo != null) {
      actionBar.setSubtitle(
          getString(R.string.talkback_preferences_subtitle, packageInfo.versionName));
    }
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new TalkBackPreferenceFragment();
  }

  /** Fragment that holds the preference user interface controls. */
  public static class TalkBackPreferenceFragment extends PreferenceFragmentCompat {

    public static final int[] HIDDEN_PREFERENCE_KEY_IDS_IN_ARC = {
      R.string.pref_screenoff_key,
      R.string.pref_proximity_key,
      R.string.pref_vibration_key,
      R.string.pref_use_audio_focus_key,
      R.string.pref_explore_by_touch_reflect_key,
      R.string.pref_single_tap_key,
      R.string.pref_show_context_menu_as_list_key,
      R.string.pref_tutorial_key,
      R.string.pref_two_volume_long_press_key,
      R.string.pref_dim_volume_three_clicks_key,
      R.string.pref_resume_talkback_key
    };

    public static final int[] HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH = {
      R.string.pref_tts_settings_key,
      R.string.pref_manage_labels_key,
      R.string.pref_category_manage_keyboard_shortcut_key
    };

    public static final int[] HIDDEN_PREFERENCE_KEY_IDS_ON_JASPER = {
      // No tutorial because it has a button for disabling Talkback which does not interact well
      // with the Talkback setting stored in libassistant. Also a lot of the content in the tutorial
      // is not relevant on Jasper since it has such a simple GUI.
      R.string.pref_tutorial_key,
      // Dim with three clicks disabled because the volume keys on Jasper don't work like Talkback
      // expects.
      R.string.pref_dim_volume_three_clicks_key,
      // Disabled because the suspend and resume shortcut is disabled.
      R.string.pref_resume_talkback_key,
      // Suspend and resume shortcut disabled because the volume keys on Jasper don't work like
      // Talkback expects.
      R.string.pref_two_volume_long_press_key,
    };

    public static final int[] HIDDEN_PREFERENCE_KEY_IDS_WHEN_A11Y_SHORTCUT = {
      R.string.pref_resume_talkback_key, R.string.pref_two_volume_long_press_key
    };

    public static final int[] HIDDEN_PREF_KEY_IDS_WHEN_A11Y_AUDIO_STREAM = {
      R.string.pref_speech_volume_key
    };

    /** Preferences managed by this activity. */
    private SharedPreferences prefs;

    private static final String HELP_URL =
        "https://support.google.com/accessibility/" + "android/answer/6283677";

    private boolean isWatch = false;

    private Context context;

    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      PreferenceSettingsUtils.setPreferencesFromResource(this, R.xml.preferences, rootKey);

      prefs = SharedPreferencesUtils.getSharedPreferences(activity);

      fixListSummaries(getPreferenceScreen());

      isWatch = FeatureSupport.isWatch(activity);

      // Calling getContext() in fragment crashes on L, so use
      // getActivity().getApplicationContext() instead.
      context = getActivity().getApplicationContext();

      assignTutorialIntent();

      updateSpeakPasswordsPreference();

      // Remove preferences for features that are not supported by device.
      checkTelevision();
      maybeUpdatePreferencesForWatch();
      maybeUpdatePreferencesForJasper();
      updateTouchExplorationState();
      checkVibrationSupport();
      checkProximitySupport();
      checkDimScreenShortcutSupport();
      showTalkBackVersion();
      updateTalkBackShortcutStatus();

      if (SettingsUtils.allowLinksOutOfSettings(context)) {
        assignTtsSettingsIntent();

        // We should never try to open the play store in WebActivity.
        assignPlayStoreIntentToPreference(
            R.string.pref_play_store_key, PackageManagerUtils.TALBACK_PACKAGE);

        // Link preferences to web-viewer.
        assignWebIntentToPreference(
            R.string.pref_policy_key, "http://www.google.com/policies/privacy/");
        assignWebIntentToPreference(
            R.string.pref_show_tos_key, "http://www.google.com/mobile/toscountry");
        assignFeedbackIntentToPreference(R.string.pref_help_and_feedback_key);
      } else {
        // During setup, do not allow access to web.
        removePreference(R.string.pref_category_miscellaneous_key, R.string.pref_play_store_key);
        removePreference(R.string.pref_category_miscellaneous_key, R.string.pref_policy_key);
        removePreference(R.string.pref_category_miscellaneous_key, R.string.pref_show_tos_key);
        removePreference(
            R.string.pref_category_miscellaneous_key, R.string.pref_help_and_feedback_key);

        // During setup, do not allow access to other apps via custom-labeling.
        removePreference(
            R.string.pref_category_touch_exploration_key, R.string.pref_manage_labels_key);

        // During setup, do not allow access to main settings via text-to-speech settings.
        removePreference(R.string.pref_category_when_to_speak_key, R.string.pref_tts_settings_key);
      }

      if (FeatureSupport.isArc()) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_IN_ARC);
      }
      if (FeatureSupport.hasAccessibilityShortcut(activity)) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_WHEN_A11Y_SHORTCUT);
      }
      // Hiding the speech volume preference for devices which have separate audio a11y stream.
      if (FeatureSupport.hasAcessibilityAudioStream(activity)) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREF_KEY_IDS_WHEN_A11Y_AUDIO_STREAM);
      }
    }

    private void removePreference(int categoryKeyId, int preferenceKeyId) {
      final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(categoryKeyId);
      if (category != null) {
        PreferenceSettingsUtils.hidePreference(context, category, preferenceKeyId);
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

    private void assignPlayStoreIntentToPreference(int preferenceId, String packageName) {
      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
      final Preference pref = findPreferenceByResId(preferenceId);
      if (pref == null) {
        return;
      }

      String path = "?id=" + packageName;

      Intent intent;
      // Only for watches, try the "market://" URL first. If there is a Play Store on the
      // device, this should succeed. Only for LE devices, there will be no Play Store.
      if (isWatch) {
        intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details" + path));
        if (canHandleIntent(intent)) {
          pref.setIntent(intent);
          return;
        }
      }

      Uri uri = Uri.parse("https://play.google.com/store/apps/details" + path);
      intent = new Intent(Intent.ACTION_VIEW, uri);
      if (isWatch) {
        // The play.google.com URL goes to ClockworkHome which needs an extra permission,
        // just redirect to the phone.
        intent = RemoteIntentUtils.intentToOpenUriOnPhone(uri);
      } else if (!canHandleIntent(intent)) {
        category.removePreference(pref);
        return;
      }

      pref.setIntent(intent);
    }

    private void assignWebIntentToPreference(int preferenceId, String url) {

      if (!SettingsUtils.allowLinksOutOfSettings(context)) {
        return;
      }

      Preference pref = findPreferenceByResId(preferenceId);
      if (pref == null) {
        return;
      }

      Uri uri = Uri.parse(url);
      Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      Activity activity = getActivity();
      if (activity != null) {
        if (isWatch) {
          intent = RemoteIntentUtils.intentToOpenUriOnPhone(uri);
        } else if (!canHandleIntent(intent)) {
          intent = new Intent(activity, WebActivity.class);
          intent.setData(uri);
        }
      }

      pref.setIntent(intent);
    }

    private void assignFeedbackIntentToPreference(int preferenceId) {
      Preference pref = findPreferenceByResId(preferenceId);
      if (pref == null) {
        return;
      }
      // We are not supporting feedback on the Wear device itself for initial launch of
      // TalkBack on Wear.
      if (!isWatch
          && HelpAndFeedbackUtils.supportsHelpAndFeedback(getActivity().getApplicationContext())) {
        pref.setTitle(R.string.title_pref_help_and_feedback);
        pref.setOnPreferenceClickListener(
            new Preference.OnPreferenceClickListener() {
              @Override
              public boolean onPreferenceClick(Preference preference) {
                HelpAndFeedbackUtils.launchHelpAndFeedback(getActivity());
                return true;
              }
            });
      } else {
        pref.setTitle(R.string.title_pref_help);
        assignWebIntentToPreference(preferenceId, HELP_URL);
      }
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

    @Override
    public void onResume() {
      super.onResume();
      TalkBackService talkBackService = TalkBackService.getInstance();
      if (talkBackService != null) {
        talkBackService.addServiceStateListener(serviceStateListener);
      }

      prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

      updateTalkBackShortcutStatus();
      updateDimingPreferenceStatus();
      updateAudioFocusPreference();
      updateTouchExplorationState(); // Developer-preferences sub-activity may set touch-explore.
    }

    @Override
    public void onPause() {
      TalkBackService talkBackService = TalkBackService.getInstance();
      if (talkBackService != null) {
        talkBackService.removeServiceStateListener(serviceStateListener);
      }

      prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
      super.onPause();
    }

    /** Assigns the intent to open text-to-speech settings. */
    private void assignTtsSettingsIntent() {
      PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
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

    /** Assigns the appropriate intent to the tutorial preference. */
    private void assignTutorialIntent() {
      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
      final Preference prefTutorial = findPreferenceByResId(R.string.pref_tutorial_key);

      if ((category == null) || (prefTutorial == null)) {
        return;
      }

      final int touchscreenState = getResources().getConfiguration().touchscreen;
      if (touchscreenState == Configuration.TOUCHSCREEN_NOTOUCH) {
        category.removePreference(prefTutorial);
        return;
      }

      Activity activity = getActivity();
      if (activity != null) {
        final Intent tutorialIntent = new Intent(activity, AccessibilityTutorialActivity.class);
        tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        tutorialIntent.putExtra(TalkBackService.EXTRA_TUTORIAL_INTENT_SOURCE, TUTORIAL_SRC);
        prefTutorial.setIntent(tutorialIntent);
      }
    }

    private void updateTalkBackShortcutStatus() {
      final TwoStatePreference preference =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_two_volume_long_press_key);
      if (preference == null) {
        return;
      }
      preference.setEnabled(TalkBackService.getInstance() != null || preference.isChecked());
    }

    private void updateDimingPreferenceStatus() {
      // Log an error if the device supports volume key shortcuts (i.e. those running Android N or
      // earlier) but the dim screen shortcut switch is not available. Don't exit the function
      // because we still want to set up the other switch.
      final TwoStatePreference dimShortcutPreference =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_dim_volume_three_clicks_key);
      if (FeatureSupport.supportsVolumeKeyShortcuts() && dimShortcutPreference == null) {
        LogUtils.e(TAG, "Expected switch for dim screen shortcut, but switch is not present.");
      }

      final TalkBackService talkBack = TalkBackService.getInstance();
      if (talkBack == null || !DimScreenControllerApp.isSupportedbyPlatform(talkBack)) {
        LogUtils.i(
            TAG,
            "Either TalkBack could not be found, or the platform does not support screen dimming.");
        final PreferenceGroup category =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
        if (category == null) {
          return;
        }
        if (dimShortcutPreference != null) {
          category.removePreference(dimShortcutPreference);
        }
        return;
      }
    }

    /**
     * Updates the preferences state to match the actual state of touch exploration. This is called
     * once when the preferences activity launches and again whenever the actual state of touch
     * exploration changes.
     */
    private void updateTouchExplorationState() {

      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      final ContentResolver resolver = activity.getContentResolver();
      final Resources res = getResources();
      final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(activity);
      final boolean requestedState =
          SharedPreferencesUtils.getBooleanPref(
              prefs, res, R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
      final boolean actualState;

      // If accessibility is disabled then touch exploration is always
      // disabled, so the "actual" state should just be the requested state.
      if (TalkBackService.isServiceActive()) {
        actualState = isTouchExplorationEnabled(resolver);
      } else {
        actualState = requestedState;
      }

      // Enable/disable preferences that depend on explore-by-touch.
      // Cannot use "dependency" attribute in preferences XML file, because touch-explore-preference
      // is in a different preference-activity (developer preferences).
      Preference singleTapPref = findPreferenceByResId(R.string.pref_single_tap_key);
      if (singleTapPref != null) {
        singleTapPref.setEnabled(actualState);
      }
      Preference tutorialPref = findPreferenceByResId(R.string.pref_tutorial_key);
      if (tutorialPref != null) {
        tutorialPref.setEnabled(actualState);
      }
    }

    /** Update the audio focus if the activity is visible and the selector has changed the state. */
    private void updateAudioFocusPreference() {
      final TwoStatePreference audioFocusPreference =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_use_audio_focus_key);
      if (audioFocusPreference == null) {
        return;
      }

      // Make sure that we have the latest value of the audio focus preference before
      // continuing.
      boolean focusEnabled =
          SharedPreferencesUtils.getBooleanPref(
              prefs,
              getResources(),
              R.string.pref_use_audio_focus_key,
              R.bool.pref_use_audio_focus_default);

      audioFocusPreference.setChecked(focusEnabled);
    }

    /**
     * Returns whether touch exploration is enabled. This is more reliable than {@link
     * AccessibilityManager#isTouchExplorationEnabled()} because it updates atomically.
     *
     * <p>TODO: Move this method to TalkBackService.
     */
    private static boolean isTouchExplorationEnabled(ContentResolver resolver) {
      return Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1;
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

    /** Ensure that the vibration setting does not appear on devices without a vibrator. */
    private void checkVibrationSupport() {
      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      final Vibrator vibrator = (Vibrator) activity.getSystemService(VIBRATOR_SERVICE);

      if (vibrator != null && vibrator.hasVibrator()) {
        return;
      }

      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_feedback_key);
      final TwoStatePreference prefVibration =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_vibration_key);

      if (prefVibration != null) {
        prefVibration.setChecked(false);
        category.removePreference(prefVibration);
      }
    }

    /**
     * Ensure that the proximity sensor setting does not appear on devices without a proximity
     * sensor.
     */
    private void checkProximitySupport() {
      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      final SensorManager manager = (SensorManager) activity.getSystemService(SENSOR_SERVICE);
      final Sensor proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

      if (proximity != null) {
        return;
      }

      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
      final TwoStatePreference prefProximity =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_proximity_key);

      if (prefProximity != null) {
        prefProximity.setChecked(false);
        category.removePreference(prefProximity);
      }
    }

    /**
     * Ensure that dim-screen volume-key shortcut does not appear on android where volume-key
     * shortcuts do not work.
     */
    private void checkDimScreenShortcutSupport() {
      if (!FeatureSupport.supportsVolumeKeyShortcuts()) {
        removePreference(
            R.string.pref_category_miscellaneous_key, R.string.pref_dim_volume_three_clicks_key);
      }
    }


    /**
     * Checks if the device is Android TV and removes preferences that shouldn't be set when on
     * Android TV.
     */
    private void checkTelevision() {
      if (FeatureSupport.isTv(getActivity())) {
        final PreferenceGroup touchCategory =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_touch_exploration_key);
        final PreferenceGroup miscCategory =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);

        final Preference dimShortcutPreference =
            findPreferenceByResId(R.string.pref_dim_volume_three_clicks_key);
        final Preference suspendShortcutPreference =
            findPreferenceByResId(R.string.pref_two_volume_long_press_key);
        final Preference resumePreference =
            findPreferenceByResId(R.string.pref_resume_talkback_key);

        getPreferenceScreen().removePreference(touchCategory);
        miscCategory.removePreference(dimShortcutPreference);
        miscCategory.removePreference(suspendShortcutPreference);
        miscCategory.removePreference(resumePreference);
      }
    }

    /**
     * Checks if the device is a watch and removes preferences that shouldn't be set when on Android
     * Wear.
     */
    private void maybeUpdatePreferencesForWatch() {
      if (isWatch) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH);
      }
    }

    /**
     * Checks if the device is a Jasper Assistant with a screen and removes preferences that
     * shouldn't be set there.
     */
    private void maybeUpdatePreferencesForJasper() {
      TalkBackService service = TalkBackService.getInstance();
      if (service != null && service.getCompositorFlavor() == Compositor.FLAVOR_JASPER) {
        PreferenceSettingsUtils.hidePreferences(
            context, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_ON_JASPER);
      }
    }

    private static @Nullable PackageInfo getPackageInfo(Activity activity) {
      try {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
      } catch (NameNotFoundException e) {
        return null;
      }
    }

    /** Show TalkBack full version number in the Play Store button. */
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

      if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity)
          != ConnectionResult.SUCCESS) {
        // Not needed, but playing safe since this is hard to test outside of China
        playStoreButton.setIntent(null);
        final PreferenceGroup category =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
        if (category != null) {
          category.removePreference(playStoreButton);
        }
      }

      if (playStoreButton.getIntent() != null
          && activity
                  .getPackageManager()
                  .queryIntentActivities(playStoreButton.getIntent(), 0)
                  .size()
              == 0) {
        // Not needed, but playing safe since this is hard to test outside of China
        playStoreButton.setIntent(null);
        final PreferenceGroup category =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
        if (category != null) {
          category.removePreference(playStoreButton);
        }
      } else {
        playStoreButton.setSummary(
            getString(R.string.summary_pref_play_store, String.valueOf(packageInfo.versionCode)));
      }
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

            final String key = preference.getKey();
            if (getString(R.string.pref_resume_talkback_key).equals(key)) {
              final String oldValue =
                  SharedPreferencesUtils.getStringPref(
                      prefs,
                      getResources(),
                      R.string.pref_resume_talkback_key,
                      R.string.pref_resume_talkback_default);
              if (!newValue.equals(oldValue)) {
                // Reset the suspend warning dialog when the resume
                // preference changes.
                SharedPreferencesUtils.putBooleanPref(
                    prefs, getResources(), R.string.pref_show_suspension_confirmation_dialog, true);
              }
            }

            return true;
          }
        };

    /** Listens to shared preference changes and updates the preference items accordingly. */
    private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
            String audioFocusKey = context.getString(R.string.pref_use_audio_focus_key);
            if (key != null && key.equals(audioFocusKey)) {
              updateAudioFocusPreference();
            }
          }
        };

    /**
     * Listens to changes in the TalkBack state to determine which preference items should be enable
     * or disabled.
     */
    private final ServiceStateListener serviceStateListener =
        new ServiceStateListener() {
          @Override
          public void onServiceStateChanged(int newState) {
            updateDimingPreferenceStatus();
          }
        };
  }
}
