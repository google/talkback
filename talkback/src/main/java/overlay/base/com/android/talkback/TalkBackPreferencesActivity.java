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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;
import com.google.android.accessibility.talkback.HelpAndFeedbackUtils;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackDumpAccessibilityEventActivity;
import com.google.android.accessibility.talkback.TalkBackKeyboardShortcutPreferencesActivity;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.TalkBackShortcutPreferencesActivity;
import com.google.android.accessibility.talkback.TalkBackVerbosityPreferencesActivity;
import com.google.android.accessibility.talkback.controller.DimScreenControllerApp;
import com.google.android.accessibility.talkback.labeling.LabelManagerSummaryActivity;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.tutorial.AccessibilityTutorialActivity;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.HardwareUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WebActivity;
import com.google.android.clockwork.remoteintent.RemoteIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import java.util.List;

/** Activity used to set TalkBack's service preferences. */
public class TalkBackPreferencesActivity extends Activity {

  public static final String TUTORIAL_SRC = "preference";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Shows TalkBack's abbreviated version number in the action bar,
    ActionBar actionBar = getActionBar();
    PackageInfo packageInfo = TalkBackPreferenceFragment.getPackageInfo(this);
    if (actionBar != null && packageInfo != null) {
      actionBar.setSubtitle(
          getString(R.string.talkback_preferences_subtitle, packageInfo.versionName));
    }

    getFragmentManager()
        .beginTransaction()
        .replace(android.R.id.content, new TalkBackPreferenceFragment())
        .commit();
  }

  /** Fragment that holds the preference user interface controls. */
  public static class TalkBackPreferenceFragment extends PreferenceFragment {

    public static final int[] HIDDEN_PREFERENCE_KEY_IDS_IN_ARC = {
      R.string.pref_screenoff_key,
      R.string.pref_proximity_key,
      R.string.pref_shake_to_read_threshold_key,
      R.string.pref_vibration_key,
      R.string.pref_use_audio_focus_key,
      R.string.pref_explore_by_touch_reflect_key,
      R.string.pref_auto_scroll_key,
      R.string.pref_single_tap_key,
      R.string.pref_show_context_menu_as_list_key,
      R.string.pref_tutorial_key,
      R.string.pref_two_volume_long_press_key,
      R.string.pref_dim_when_talkback_enabled_key,
      R.string.pref_dim_volume_three_clicks_key,
      R.string.pref_resume_talkback_key
    };

    public static final int[] HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH = {
      R.string.pref_tts_settings_key,
      R.string.pref_manage_labels_key,
      R.string.pref_category_manage_keyboard_shortcut_key
    };

    public static final int[] HIDDEN_PREFERENCE_KEY_IDS_WHEN_A11Y_SHORTCUT = {
      R.string.pref_resume_talkback_key, R.string.pref_two_volume_long_press_key
    };

    public static final int[] HIDDEN_PREF_KEY_IDS_WHEN_A11Y_AUDIO_STREAM = {
      R.string.pref_speech_volume_key
    };

    /** Preferences managed by this activity. */
    private SharedPreferences mPrefs;

    /** AlertDialog to ask if user really wants to disable explore by touch. */
    private AlertDialog mExploreByTouchDialog;

    /** AlertDialog to ask if user really wants to enable node tree debugging. */
    private AlertDialog mTreeDebugDialog;

    /** AlertDialog to ask if user really wants to enable performance statistics. */
    private AlertDialog mPerformanceStatsDialog;

    private boolean mContentObserverRegistered = false;

    /** Id for seeing if the Explore by touch dialog was active when restoring state. */
    private static final String EXPLORE_BY_TOUCH_DIALOG_ACTIVE = "exploreDialogActive";

    /** Id for seeing if the tree debug dialog was active when restoring state. */
    private static final String TREE_DEBUG_DIALOG_ACTIVE = "treeDebugDialogActive";

    /** Id for seeing if the performance statistics dialog was active when restoring state. */
    private static final String PERFORMANCE_STATS_DIALOG_ACTIVE = "performanceStatsDialogActive";

    private static final String HELP_URL =
        "https://support.google.com/accessibility/" + "android/answer/6283677";

    private boolean mIsWatch = false;

    private Context mContext;

    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      // Set preferences to use device-protected storage.
      if (BuildVersionUtils.isAtLeastN()) {
        getPreferenceManager().setStorageDeviceProtected();
      }

      mPrefs = SharedPreferencesUtils.getSharedPreferences(activity);
      addPreferencesFromResource(R.xml.preferences);

      final TwoStatePreference prefTreeDebug =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_tree_debug_reflect_key);
      prefTreeDebug.setOnPreferenceChangeListener(mTreeDebugChangeListener);

      final TwoStatePreference prefPerformanceStats =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_performance_stats_reflect_key);
      prefPerformanceStats.setOnPreferenceChangeListener(mPerformanceStatsChangeListener);

      fixListSummaries(getPreferenceScreen());

      final SwitchPreference selectorActivation =
          (SwitchPreference) findPreferenceByResId(R.string.pref_selector_activation_key);
      selectorActivation.setOnPreferenceChangeListener(mSelectorActivationChangeListener);

      if (selectorActivation != null) {
        enableOrDisableSelectorSettings(selectorActivation.isChecked());
      }

      mIsWatch = FormFactorUtils.getInstance(activity).isWatch();

      // Calling getContext() in fragment crashes on L, so use
      // getActivity().getApplicationContext() instead.
      mContext = getActivity().getApplicationContext();

      assignTtsSettingsIntent();
      assignTutorialIntent();
      assignLabelManagerIntent();
      assignKeyboardShortcutIntent();
      assignVerbosityIntent();
      assignDumpA11yEventIntent();

      updateSpeakPasswordsPreference();

      // Remove preferences for features that are not supported by device.
      checkTelevision();
      maybeUpdatePreferencesForWatch();
      checkTouchExplorationSupport();
      checkWebScriptsSupport();
      checkVibrationSupport();
      maybeUpdatePreferencesForSelectorSupport();
      checkProximitySupport();
      checkAccelerometerSupport();
      showTalkBackVersion();
      updateTalkBackShortcutStatus();

      // We should never try to open the play store in WebActivity.
      assignPlayStoreIntentToPreference(
          R.string.pref_play_store_key, "com.google.android.marvin.talkback");

      assignWebIntentToPreference(
          R.string.pref_policy_key, "http://www.google.com/policies/privacy/");
      assignWebIntentToPreference(
          R.string.pref_show_tos_key, "http://www.google.com/mobile/toscountry");

      assignFeedbackIntentToPreference(R.string.pref_help_and_feedback_key);

      if (FormFactorUtils.getInstance(activity).isArc()) {
        PreferenceSettingsUtils.hidePreferences(
            mContext, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_IN_ARC);
      }
      if (FormFactorUtils.getInstance(activity).hasAccessibilityShortcut()) {
        PreferenceSettingsUtils.hidePreferences(
            mContext, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_WHEN_A11Y_SHORTCUT);
      }
      // Hiding the speech volume preference for devices which have separate audio a11y stream.
      if (FormFactorUtils.hasAcessibilityAudioStream(activity)) {
        PreferenceSettingsUtils.hidePreferences(
            mContext, getPreferenceScreen(), HIDDEN_PREF_KEY_IDS_WHEN_A11Y_AUDIO_STREAM);
      }
    }

    /**
     * In versions O and above, assign a default value for speaking passwords without headphones to
     * the system setting for speaking passwords out loud. This way, if the user already wants the
     * system to speak speak passwords out loud, the user will see no change and passwords will
     * continue to be spoken. In M and below, hide this preference.
     */
    private void updateSpeakPasswordsPreference() {
      if (FormFactorUtils.useSpeakPasswordsServicePref()) {
        // Read talkback speak-passwords preference, with default to system preference.
        boolean speakPassValue = SpeakPasswordsManager.getAlwaysSpeakPasswordsPref(mContext);
        // Update talkback preference display to match read value.
        SwitchPreference prefSpeakPasswords =
            (SwitchPreference)
                findPreferenceByResId(R.string.pref_speak_passwords_without_headphones);
        prefSpeakPasswords.setChecked(speakPassValue);
      } else {
        PreferenceSettingsUtils.hidePreference(
            mContext, getPreferenceScreen(), R.string.pref_speak_passwords_without_headphones);
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
      if (mIsWatch) {
        intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details" + path));
        if (canHandleIntent(intent)) {
          pref.setIntent(intent);
          return;
        }
      }

      Uri uri = Uri.parse("https://play.google.com/store/apps/details" + path);
      intent = new Intent(Intent.ACTION_VIEW, uri);
      if (mIsWatch) {
        // The play.google.com URL goes to ClockworkHome which needs an extra permission,
        // just redirect to the the phone.
        intent = RemoteIntent.intentToOpenUriOnPhone(uri);
      } else if (!canHandleIntent(intent)) {
        category.removePreference(pref);
        return;
      }

      pref.setIntent(intent);
    }

    private void assignWebIntentToPreference(int preferenceId, String url) {
      Preference pref = findPreferenceByResId(preferenceId);
      if (pref == null) {
        return;
      }

      Uri uri = Uri.parse(url);
      Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      Activity activity = getActivity();
      if (activity != null) {
        if (mIsWatch) {
          intent = RemoteIntent.intentToOpenUriOnPhone(uri);
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
      if (!mIsWatch
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
        talkBackService.addServiceStateListener(mServiceStateListener);
        if (talkBackService.supportsTouchScreen()) {
          registerTouchSettingObserver();
        }
      }

      if (mExploreByTouchDialog != null) {
        mExploreByTouchDialog.show();
      }

      if (mTreeDebugDialog != null) {
        mTreeDebugDialog.show();
      }

      if (mPerformanceStatsDialog != null) {
        mPerformanceStatsDialog.show();
      }

      mPrefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

      updateTalkBackShortcutStatus();
      updateDimingPreferenceStatus();
      updateDumpA11yEventPreferenceSummary();
      updateAudioFocusPreference();
    }

    @Override
    public void onPause() {
      super.onPause();
      TalkBackService talkBackService = TalkBackService.getInstance();
      if (talkBackService != null) {
        talkBackService.removeServiceStateListener(mServiceStateListener);
      }
      Activity activity = getActivity();
      if (activity != null && mContentObserverRegistered) {
        activity.getContentResolver().unregisterContentObserver(mTouchExploreObserver);
      }

      if (mExploreByTouchDialog != null) {
        mExploreByTouchDialog.dismiss();
      }

      if (mTreeDebugDialog != null) {
        mTreeDebugDialog.dismiss();
      }

      if (mPerformanceStatsDialog != null) {
        mPerformanceStatsDialog.dismiss();
      }
      mPrefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
      savedInstanceState.putBoolean(EXPLORE_BY_TOUCH_DIALOG_ACTIVE, mExploreByTouchDialog != null);
      savedInstanceState.putBoolean(TREE_DEBUG_DIALOG_ACTIVE, mTreeDebugDialog != null);
      savedInstanceState.putBoolean(
          PERFORMANCE_STATS_DIALOG_ACTIVE, mPerformanceStatsDialog != null);
      super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
      super.onViewStateRestored(savedInstanceState);

      if (savedInstanceState == null) {
        return;
      }

      if (savedInstanceState.getBoolean(EXPLORE_BY_TOUCH_DIALOG_ACTIVE)) {
        mExploreByTouchDialog = createDisableExploreByTouchDialog();
      }

      if (savedInstanceState.getBoolean(TREE_DEBUG_DIALOG_ACTIVE)) {
        mTreeDebugDialog = createEnableTreeDebugDialog();
      }

      if (savedInstanceState.getBoolean(PERFORMANCE_STATS_DIALOG_ACTIVE)) {
        mPerformanceStatsDialog = createEnablePerfStatsDialog();
      }
    }

    private void registerTouchSettingObserver() {
      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      Uri uri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
      activity.getContentResolver().registerContentObserver(uri, false, mTouchExploreObserver);
      mContentObserverRegistered = true;
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

    /** Assigns the appropriate intent to the label manager preference. */
    private void assignLabelManagerIntent() {
      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_touch_exploration_key);
      final Preference prefManageLabels = findPreferenceByResId(R.string.pref_manage_labels_key);

      if ((category == null) || (prefManageLabels == null)) {
        return;
      }

      Activity activity = getActivity();
      if (activity != null) {
        final Intent labelManagerIntent = new Intent(activity, LabelManagerSummaryActivity.class);
        labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        prefManageLabels.setIntent(labelManagerIntent);
      }
    }

    /** Assigns the appropriate intent to the dump accessibility event preference. */
    private void assignDumpA11yEventIntent() {
      final Preference prefDumpA11yEvent = findPreferenceByResId(R.string.pref_dump_a11y_event_key);

      if (prefDumpA11yEvent == null) {
        return;
      }

      Activity activity = getActivity();
      if (activity != null) {
        final Intent filterA11yEventIntent =
            new Intent(activity, TalkBackDumpAccessibilityEventActivity.class);
        prefDumpA11yEvent.setIntent(filterA11yEventIntent);
      }
    }

    /** Assigns the appropriate intent to the keyboard shortcut preference. */
    private void assignKeyboardShortcutIntent() {
      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
      final Preference keyboardShortcutPref =
          findPreferenceByResId(R.string.pref_category_manage_keyboard_shortcut_key);

      if ((category == null) || (keyboardShortcutPref == null)) {
        return;
      }

      Activity activity = getActivity();
      if (activity != null) {
        final Intent keyboardShortcutIntent =
            new Intent(activity, TalkBackKeyboardShortcutPreferencesActivity.class);
        keyboardShortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        keyboardShortcutPref.setIntent(keyboardShortcutIntent);
      }
    }

    /** Assigns verbosity preference item to verbosity activity intent. */
    private void assignVerbosityIntent() {
      // Find preference item for verbosity.
      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
      final Preference preference = findPreferenceByResId(R.string.pref_verbosity_key);
      if ((category == null) || (preference == null)) {
        return;
      }

      // Assign intent that opens verbosity preferences activity.
      Activity activity = getActivity();
      if (activity != null) {
        final Intent intent = new Intent(activity, TalkBackVerbosityPreferencesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        preference.setIntent(intent);
      }
    }

    /** Assigns the appropriate intent to the touch exploration preference. */
    private void checkTouchExplorationSupport() {
      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_touch_exploration_key);
      if (category == null) {
        return;
      }

      checkTouchExplorationSupportInner();
    }

    /** Touch exploration preference management code */
    private void checkTouchExplorationSupportInner() {
      final TwoStatePreference prefTouchExploration =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_explore_by_touch_reflect_key);
      if (prefTouchExploration == null) {
        return;
      }

      // Ensure that changes to the reflected preference's checked state never
      // trigger content observers.
      prefTouchExploration.setPersistent(false);

      // Synchronize the reflected state.
      updateTouchExplorationState();

      // Set up listeners that will keep the state synchronized.
      prefTouchExploration.setOnPreferenceChangeListener(mTouchExplorationChangeListener);

      // Hook in the external PreferenceActivity for gesture management
      final Preference shortcutsScreen =
          findPreferenceByResId(R.string.pref_category_manage_gestures_key);

      Activity activity = getActivity();
      if (activity != null) {
        final Intent shortcutsIntent =
            new Intent(activity, TalkBackShortcutPreferencesActivity.class);
        shortcutsScreen.setIntent(shortcutsIntent);
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
      final TwoStatePreference dimPreference =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_dim_when_talkback_enabled_key);
      final TwoStatePreference dimShortcutPreference =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_dim_volume_three_clicks_key);
      if (dimPreference == null || dimShortcutPreference == null) {
        return;
      }
      final TalkBackService talkBack = TalkBackService.getInstance();
      if (talkBack == null || !DimScreenControllerApp.isSupported(talkBack)) {
        final PreferenceGroup category =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
        if (category == null) {
          return;
        }
        category.removePreference(dimPreference);
        category.removePreference(dimShortcutPreference);
        return;
      }

      // Make sure that we have the latest value of the dim preference before continuing.
      boolean dimEnabled =
          SharedPreferencesUtils.getBooleanPref(
              mPrefs,
              getResources(),
              R.string.pref_dim_when_talkback_enabled_key,
              R.bool.pref_dim_when_talkback_enabled_default);
      dimPreference.setChecked(dimEnabled);

      dimPreference.setEnabled(TalkBackService.isServiceActive() || dimPreference.isChecked());
      dimPreference.setOnPreferenceChangeListener(
          new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
              if (newValue == null || !(newValue instanceof Boolean)) {
                return true;
              }

              boolean dimPreferenceOn = (Boolean) newValue;

              if (dimPreferenceOn) {
                if (talkBack != null) {
                  // A TalkBack instance should be available if you can check the box,
                  // but let's err on the side of safety here.
                  talkBack.getDimScreenController().showDimScreenDialog();
                }
                return false; // The DimScreenController will take care of any pref changes.
              } else {
                if (talkBack != null) {
                  // We allow turning off screen dimming when TalkBack is off, so we
                  // definitely do need to check if a TalkBack instance is available.
                  talkBack.getDimScreenController().disableDimming();
                }
                if (!TalkBackService.isServiceActive()) {
                  dimPreference.setEnabled(false);
                }
                return true; // Let the preferences system turn the preference off.
              }
            }
          });
    }

    private void updateDumpA11yEventPreferenceSummary() {
      final Preference prefDumpA11yEvent = findPreferenceByResId(R.string.pref_dump_a11y_event_key);

      if (prefDumpA11yEvent == null || mPrefs == null) {
        return;
      }

      int count = 0;
      int[] eventTypes = AccessibilityEventUtils.getAllEventTypes();

      for (int id : eventTypes) {
        String prefKey = getString(R.string.pref_dump_event_key_prefix, id);
        if (mPrefs.getBoolean(prefKey, false)) {
          count++;
        }
      }

      prefDumpA11yEvent.setSummary(
          getResources()
              .getQuantityString(
                  R.plurals.template_dump_event_count, /* id */
                  count, /* quantity */
                  count /* formatArgs */));
    }

    /**
     * Updates the preferences state to match the actual state of touch exploration. This is called
     * once when the preferences activity launches and again whenever the actual state of touch
     * exploration changes.
     */
    private void updateTouchExplorationState() {
      final TwoStatePreference prefTouchExploration =
          (TwoStatePreference) findPreferenceByResId(R.string.pref_explore_by_touch_reflect_key);

      if (prefTouchExploration == null) {
        return;
      }

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
      final boolean reflectedState = prefTouchExploration.isChecked();
      final boolean actualState;

      // If accessibility is disabled then touch exploration is always
      // disabled, so the "actual" state should just be the requested state.
      if (TalkBackService.isServiceActive()) {
        actualState = isTouchExplorationEnabled(resolver);
      } else {
        actualState = requestedState;
      }

      // If touch exploration is actually off and we requested it on, the user
      // must have declined the "Enable touch exploration" dialog. Update the
      // requested value to reflect this.
      if (requestedState != actualState) {
        LogUtils.log(
            this,
            Log.DEBUG,
            "Set touch exploration preference to reflect actual state %b",
            actualState);
        SharedPreferencesUtils.putBooleanPref(
            prefs, res, R.string.pref_explore_by_touch_key, actualState);
      }

      // Ensure that the check box preference reflects the requested state,
      // which was just synchronized to match the actual state.
      if (reflectedState != actualState) {
        prefTouchExploration.setChecked(actualState);
      }
    }

    /** Update the audio focus if the activity is visible and the selector has changed the state. */
    private void updateAudioFocusPreference() {
      final SwitchPreference audioFocusPreference =
          (SwitchPreference) findPreferenceByResId(R.string.pref_use_audio_focus_key);
      if (audioFocusPreference == null) {
        return;
      }

      // Make sure that we have the latest value of the audio focus preference before
      // continuing.
      boolean focusEnabled =
          SharedPreferencesUtils.getBooleanPref(
              mPrefs,
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
    public static boolean isTouchExplorationEnabled(ContentResolver resolver) {
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
          mPreferenceChangeListener.onPreferenceChange(
              preference, ((ListPreference) preference).getValue());

          preference.setOnPreferenceChangeListener(mPreferenceChangeListener);
        }
      }
    }

    /**
     * Ensure that web script injection settings do not appear on devices before user-customization
     * of web-scripts were available in the framework.
     */
    private void checkWebScriptsSupport() {
      // TalkBack can control web script injection on API 18+ only.
      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_developer_key);
      final Preference prefWebScripts = findPreferenceByResId(R.string.pref_web_scripts_key);

      if (prefWebScripts != null) {
        category.removePreference(prefWebScripts);
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

    /** Ensure selector setting does not appear on devices without fingerprint sensor. */
    private void maybeUpdatePreferencesForSelectorSupport() {
      boolean hasFingerprintSensor = HardwareUtils.isFingerprintSupported(getActivity());
      boolean androidAtLeastO = BuildVersionUtils.isAtLeastO();
      // If selector not supported...
      if (!hasFingerprintSensor || !androidAtLeastO) {
        // Find selector preferences.
        PreferenceGroup enclosingPrefGroup =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_touch_exploration_key);
        PreferenceGroup selectorPrefs =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_selector_category_settings_key);
        final TwoStatePreference prefSelectorOn =
            (TwoStatePreference) findPreferenceByResId(R.string.pref_selector_activation_key);

        // Turn off selector and remove selector preference group.
        if (prefSelectorOn != null) {
          prefSelectorOn.setChecked(false);
          enclosingPrefGroup.removePreference(selectorPrefs);
        }
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
     * Ensure that the shake to start continuous reading setting does not appear on devices without
     * a proximity sensor.
     */
    private void checkAccelerometerSupport() {
      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      final SensorManager manager = (SensorManager) activity.getSystemService(SENSOR_SERVICE);
      final Sensor accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

      if (accel != null) {
        return;
      }

      final PreferenceGroup category =
          (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
      final ListPreference prefShake =
          (ListPreference) findPreferenceByResId(R.string.pref_shake_to_read_threshold_key);

      if (prefShake != null) {
        category.removePreference(prefShake);
      }
    }

    /**
     * Checks if the device is Android TV and removes preferences that shouldn't be set when on
     * Android TV.
     */
    private void checkTelevision() {
      if (FormFactorUtils.isContextTelevision(getActivity())) {
        final PreferenceGroup touchCategory =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_touch_exploration_key);
        final PreferenceGroup miscCategory =
            (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);

        final Preference dimPreference =
            findPreferenceByResId(R.string.pref_dim_when_talkback_enabled_key);
        final Preference dimShortcutPreference =
            findPreferenceByResId(R.string.pref_dim_volume_three_clicks_key);
        final Preference suspendShortcutPreference =
            findPreferenceByResId(R.string.pref_two_volume_long_press_key);
        final Preference resumePreference =
            findPreferenceByResId(R.string.pref_resume_talkback_key);
        final Preference treeDebugPreference =
            findPreferenceByResId(R.string.pref_tree_debug_reflect_key);

        getPreferenceScreen().removePreference(touchCategory);
        miscCategory.removePreference(dimPreference);
        miscCategory.removePreference(dimShortcutPreference);
        miscCategory.removePreference(suspendShortcutPreference);
        miscCategory.removePreference(resumePreference);
        treeDebugPreference.setSummary(getString(R.string.summary_pref_tree_debug_tv));
      }
    }

    /**
     * Checks if the device is a watch and removes preferences that shouldn't be set when on Android
     * Wear.
     */
    private void maybeUpdatePreferencesForWatch() {
      if (mIsWatch) {
        PreferenceSettingsUtils.hidePreferences(
            mContext, getPreferenceScreen(), HIDDEN_PREFERENCE_KEY_IDS_ON_WATCH);
      }
    }

    private static PackageInfo getPackageInfo(Activity activity) {
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
     * Updates the preference that controls whether TalkBack will attempt to request Explore by
     * Touch.
     *
     * @param requestedState The state requested by the user.
     * @return Whether to update the reflected state.
     */
    private boolean setTouchExplorationRequested(boolean requestedState) {
      Activity activity = getActivity();
      if (activity == null) {
        return false;
      }

      final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(activity);

      // Update the "requested" state. This will trigger a listener in
      // TalkBack that changes the "actual" state.
      SharedPreferencesUtils.putBooleanPref(
          prefs, getResources(), R.string.pref_explore_by_touch_key, requestedState);

      // If TalkBack is inactive, we should immediately reflect the change in
      // "requested" state.
      if (!TalkBackService.isServiceActive()) {
        return true;
      }
      if (requestedState && TalkBackService.getInstance() != null) {
        TalkBackService.getInstance().showTutorialIfNecessary();
      }

      // If accessibility is on, we should wait for the "actual" state to
      // change, then reflect that change. If the user declines the system's
      // touch exploration dialog, the "actual" state will not change and
      // nothing needs to happen.
      LogUtils.log(this, Log.DEBUG, "TalkBack active, waiting for EBT request to take effect");
      return false;
    }

    private AlertDialog createDisableExploreByTouchDialog() {
      Activity activity = getActivity();
      if (activity == null) {
        return null;
      }

      final DialogInterface.OnCancelListener cancel =
          new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
              mExploreByTouchDialog = null;
            }
          };

      final DialogInterface.OnClickListener cancelClick =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              mExploreByTouchDialog = null;
            }
          };

      final DialogInterface.OnClickListener okClick =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              mExploreByTouchDialog = null;
              if (setTouchExplorationRequested(false)) {
                // Manually tick the check box since we're not returning to
                // the preference change listener.
                final TwoStatePreference prefTouchExploration =
                    (TwoStatePreference)
                        findPreferenceByResId(R.string.pref_explore_by_touch_reflect_key);
                prefTouchExploration.setChecked(false);
              }
            }
          };

      return new AlertDialog.Builder(activity)
          .setTitle(R.string.dialog_title_disable_exploration)
          .setMessage(R.string.dialog_message_disable_exploration)
          .setNegativeButton(android.R.string.cancel, cancelClick)
          .setPositiveButton(android.R.string.ok, okClick)
          .setOnCancelListener(cancel)
          .create();
    }

    private AlertDialog createEnableTreeDebugDialog() {
      Activity activity = getActivity();
      if (activity == null) {
        return null;
      }

      final DialogInterface.OnCancelListener cancel =
          new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
              mTreeDebugDialog = null;
            }
          };

      final DialogInterface.OnClickListener cancelClick =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              mTreeDebugDialog = null;
            }
          };

      final DialogInterface.OnClickListener okClick =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              mTreeDebugDialog = null;

              Activity innerActivity = getActivity();
              if (innerActivity == null) {
                return;
              }

              final SharedPreferences prefs =
                  SharedPreferencesUtils.getSharedPreferences(innerActivity);
              SharedPreferencesUtils.putBooleanPref(
                  prefs, getResources(), R.string.pref_tree_debug_key, true);

              // Manually tick the check box since we're not returning to
              // the preference change listener.
              final TwoStatePreference prefTreeDebug =
                  (TwoStatePreference) findPreferenceByResId(R.string.pref_tree_debug_reflect_key);
              prefTreeDebug.setChecked(true);
            }
          };

      return new AlertDialog.Builder(activity)
          .setTitle(R.string.dialog_title_enable_tree_debug)
          .setMessage(R.string.dialog_message_enable_tree_debug)
          .setNegativeButton(android.R.string.cancel, cancelClick)
          .setPositiveButton(android.R.string.ok, okClick)
          .setOnCancelListener(cancel)
          .create();
    }

    private AlertDialog createEnablePerfStatsDialog() {
      Activity activity = getActivity();
      if (activity == null) {
        return null;
      }

      final DialogInterface.OnCancelListener cancel =
          new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
              mPerformanceStatsDialog = null;
            }
          };

      final DialogInterface.OnClickListener cancelClick =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              mPerformanceStatsDialog = null;
            }
          };

      final DialogInterface.OnClickListener okClick =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              mPerformanceStatsDialog = null;

              Activity innerActivity = getActivity();
              if (innerActivity == null) {
                return;
              }

              final SharedPreferences prefs =
                  SharedPreferencesUtils.getSharedPreferences(innerActivity);
              SharedPreferencesUtils.putBooleanPref(
                  prefs, getResources(), R.string.pref_performance_stats_key, true);

              // Manually tick the check box since we're not returning to
              // the preference change listener.
              final TwoStatePreference prefPerformanceStats =
                  (TwoStatePreference)
                      findPreferenceByResId(R.string.pref_performance_stats_reflect_key);
              prefPerformanceStats.setChecked(true);
            }
          };

      return new AlertDialog.Builder(activity)
          .setTitle(R.string.dialog_title_enable_performance_stats)
          .setMessage(R.string.dialog_message_enable_performance_stats)
          .setNegativeButton(android.R.string.cancel, cancelClick)
          .setPositiveButton(android.R.string.ok, okClick)
          .setOnCancelListener(cancel)
          .create();
    }

    private final Handler mHandler = new Handler();

    private final ContentObserver mTouchExploreObserver =
        new ContentObserver(mHandler) {
          @Override
          public void onChange(boolean selfChange) {
            if (selfChange) {
              return;
            }

            // The actual state of touch exploration has changed.
            updateTouchExplorationState();
          }
        };

    private final OnPreferenceChangeListener mTouchExplorationChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            final boolean requestedState = Boolean.TRUE.equals(newValue);

            // If the user is trying to turn touch exploration off, show
            // a confirmation dialog and don't change anything.
            if (!requestedState) {
              (mExploreByTouchDialog = createDisableExploreByTouchDialog()).show();
              return false;
            }

            return setTouchExplorationRequested(true); // requestedState
          }
        };

    private final OnPreferenceChangeListener mTreeDebugChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            Activity activity = getActivity();
            if (activity == null) {
              return false;
            }

            // If the user is trying to turn node tree debugging on, show
            // a confirmation dialog and don't change anything.
            if (Boolean.TRUE.equals(newValue)) {
              (mTreeDebugDialog = createEnableTreeDebugDialog()).show();
              return false;
            }

            // If the user is turning node tree debugging off, then any
            // gestures currently set to print the node tree should be
            // made unassigned.
            disableAndRemoveGesture(
                activity, R.string.pref_tree_debug_key, R.string.shortcut_value_print_node_tree);

            return true;
          }
        };

    private final OnPreferenceChangeListener mSelectorActivationChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (Boolean.TRUE.equals(newValue)) {
              // Assign selector shortcuts.
              assignSelectorShortcuts();
              enableOrDisableSelectorSettings(true); // Enable preferences for settings.
            } else {
              // Remove all selector assignments and restore pre-selector assignments.
              removeSelectorShortcuts();
              enableOrDisableSelectorSettings(false);
            }
            return true;
          }
        };

    /**
     * Initialize gestures with selector shortcuts, or restore any previous selector assignments.
     */
    private void assignSelectorShortcuts() {
      String selectorSavedKeySuffix = getString(R.string.pref_selector_saved_gesture_suffix);

      // If a device has a fingerprint sensor, assign gestures to the selector shortcut values
      // If not, it is up to the user to assign gestures.
      if (HardwareUtils.isFingerprintSupported(getActivity())) {
        // If this is the first time the selector is turned on, assign selector shortcuts to
        // specific gestures.
        if (mPrefs.getBoolean(getString(R.string.pref_selector_first_time_activation_key), true)) {
          setInitialSelectorShortcuts(selectorSavedKeySuffix);
        } else {
          // Reassign saved selector assignments.
          restoreSelectorShortcuts(selectorSavedKeySuffix);
        }
      } else {
        // In case of first time activation, do nothing. Otherwise reassign.
        restoreSelectorShortcuts(selectorSavedKeySuffix);
      }
    }

    /** Set the initial selector assignments for first time activation. */
    private void setInitialSelectorShortcuts(String selectorSavedKeySuffix) {
      String[] initialSelectorGestures =
          mContext.getResources().getStringArray(R.array.initial_selector_gestures);
      String[] selectorShortcutValues =
          mContext.getResources().getStringArray(R.array.selector_shortcut_values);
      String notSelectorSavedKeySuffix = getString(R.string.pref_not_selector_saved_gesture_suffix);

      if (initialSelectorGestures.length != selectorShortcutValues.length) {
        return;
      }

      for (int i = 0; i < initialSelectorGestures.length; i++) {
        if (mPrefs.contains(initialSelectorGestures[i])) {
          // Save the current assignments for initial gestures.
          mPrefs
              .edit()
              .putString(
                  initialSelectorGestures[i] + notSelectorSavedKeySuffix,
                  mPrefs.getString(initialSelectorGestures[i], null)) // Will never return null.
              .apply();
        }

        // Save the selector assignments for initial gestures.
        mPrefs
            .edit()
            .putString(
                initialSelectorGestures[i] + selectorSavedKeySuffix, selectorShortcutValues[i])
            .apply();

        // Assign selector shortcuts to gestures.
        mPrefs.edit().putString(initialSelectorGestures[i], selectorShortcutValues[i]).apply();
      }
      mPrefs
          .edit()
          .putBoolean(getString(R.string.pref_selector_first_time_activation_key), false)
          .apply();
    }

    /** Reassign any saved selector assignments. */
    private void restoreSelectorShortcuts(String selectorSavedKeySuffix) {
      String[] gestureShortcutKeys =
          mContext.getResources().getStringArray(R.array.pref_shortcut_keys);

      for (String gestureShortcutKey : gestureShortcutKeys) {
        // Assign the gesture to its saved selector shortcut. There is no need to backup the
        // non-selector value here, since it gets backed up in the preference change listener for
        // the gesture in TalkBackPreferencesActivity, where we make sure the old value
        // (the value to save) of the preference is non-selector and the new value is selector.
        setPrefWithBackup(gestureShortcutKey, selectorSavedKeySuffix);
      }
    }

    /** Reassign the gestures with selector shortcut assignments to their pre-selector shortcuts. */
    private void removeSelectorShortcuts() {
      String[] gestureShortcutKeys =
          mContext.getResources().getStringArray(R.array.pref_shortcut_keys);

      // Iterate through all the gestures and their shortcut assignments.
      for (String gestureShortcutKey : gestureShortcutKeys) {
        if (mPrefs.contains(gestureShortcutKey)) {
          String gestureAction =
              mPrefs.getString(gestureShortcutKey, null); // Null will never be used.
          // Check if assigned action for a gesture is a selector shortcut. If it is,
          // replace with the saved non-selector preference and save the selector assignment.
          if (isSelectorAction(gestureAction)) {
            handleSelectorShortcutRemoval(gestureShortcutKey, gestureAction);
          }
        }
      }
    }

    /**
     * Handle the backup and restoration of a gesture assigned to a selector shortcut. Backup the
     * selector action, and restore the non-selector action.
     */
    private void handleSelectorShortcutRemoval(String prefKey, String selectorAction) {
      String notSelectorSavedKeySuffix = getString(R.string.pref_not_selector_saved_gesture_suffix);
      String selectorSavedKeySuffix = getString(R.string.pref_selector_saved_gesture_suffix);

      // If the non-selector backup exists, use this backup.
      if (setPrefWithBackup(prefKey, notSelectorSavedKeySuffix)) {
        // Backup the selector value of this gesture.
        mPrefs.edit().putString(prefKey + selectorSavedKeySuffix, selectorAction).apply();
      } else {
        // Non-selector backup doesn't exist, so gesture was never initially assigned before being
        // assigned to a selector shortcut. Remove the key from preferences, so the default value
        // of the gesture is used.
        mPrefs.edit().remove(prefKey).apply();
      }
    }

    /**
     * Assign to a preference its backup value.
     *
     * @param prefKey the key of the preference to restore.
     * @param newBackupSuffix the string to append to the preference key for retrieving the backup
     *     value.
     * @return {@code true} if the backup value exists and the preference is assigned to this value.
     */
    private boolean setPrefWithBackup(String prefKey, String newBackupSuffix) {
      if (mPrefs.contains(prefKey + newBackupSuffix)) {
        String newValue =
            mPrefs.getString(prefKey + newBackupSuffix, null); // Will never return null.
        mPrefs.edit().putString(prefKey, newValue).apply();
        return true;
      }
      return false;
    }

    /** Check if assigned action for a gesture is a selector shortcut. */
    private boolean isSelectorAction(String gestureAction) {
      String[] selectorShortcutValues =
          mContext.getResources().getStringArray(R.array.selector_shortcut_values);
      // Iterate through the selector shortcut values.
      for (String selectorShortcutValue : selectorShortcutValues) {
        if (gestureAction.equals(selectorShortcutValue)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Enables or disables the setting configuration preference category, depending on the on/off
     * state of the selector.
     */
    private void enableOrDisableSelectorSettings(boolean enable) {
      PreferenceCategory settingsCategory =
          (PreferenceCategory)
              findPreferenceByResId(R.string.pref_category_selector_settings_configuration_key);
      if (settingsCategory == null) {
        return;
      }

      final int count = settingsCategory.getPreferenceCount();

      for (int i = 0; i < count; i++) {
        final Preference preference = settingsCategory.getPreference(i);

        if (preference instanceof SwitchPreference) {
          SwitchPreference switchPreference = (SwitchPreference) preference;
          switchPreference.setEnabled(enable);
        }
      }
    }

    private final OnPreferenceChangeListener mPerformanceStatsChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            Activity activity = getActivity();
            if (activity == null) {
              return false;
            }

            // If the user is enabling performance statistics... show confirmation dialog.
            if (Boolean.TRUE.equals(newValue)) {
              (mPerformanceStatsDialog = createEnablePerfStatsDialog()).show();
              return false;
            }

            // If the user is disabling performance statistics... disable & unassign gesture.
            disableAndRemoveGesture(
                activity,
                R.string.pref_performance_stats_key,
                R.string.shortcut_value_print_performance_stats);

            return true;
          }
        };

    protected void disableAndRemoveGesture(Activity activity, int prefKeyRes, int shortcutRes) {
      // Set preference to false
      final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(activity);
      final SharedPreferences.Editor prefEditor = prefs.edit();
      prefEditor.putBoolean(getString(prefKeyRes), false);
      // Gesstures may need to be reassigned if disabling developer options, like node tree
      // debugging and performance tracking.
      final String[] gesturePrefKeys = getResources().getStringArray(R.array.pref_shortcut_keys);

      // For each gesture that matches shortcut... unassign gesture.
      for (String prefKey : gesturePrefKeys) {
        final String currentValue = prefs.getString(prefKey, null);
        if (getString(shortcutRes).equals(currentValue)) {
          prefEditor.putString(prefKey, getString(R.string.shortcut_value_unassigned));
        }
      }
      prefEditor.apply();
    }

    /**
     * Listens for preference changes and updates the summary to reflect the current setting. This
     * shouldn't be necessary, since preferences are supposed to automatically do this when the
     * summary is set to "%s".
     */
    private final OnPreferenceChangeListener mPreferenceChangeListener =
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
                      mPrefs,
                      getResources(),
                      R.string.pref_resume_talkback_key,
                      R.string.pref_resume_talkback_default);
              if (!newValue.equals(oldValue)) {
                // Reset the suspend warning dialog when the resume
                // preference changes.
                SharedPreferencesUtils.putBooleanPref(
                    mPrefs,
                    getResources(),
                    R.string.pref_show_suspension_confirmation_dialog,
                    true);
              }
            }

            return true;
          }
        };

    /** Listens to shared preference changes and updates the preference items accordingly. */
    private final OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
            String dimKey = getString(R.string.pref_dim_when_talkback_enabled_key);
            String audioFocusKey = mContext.getString(R.string.pref_use_audio_focus_key);
            if (key != null && key.equals(dimKey)) {
              updateDimingPreferenceStatus();
            } else if (key != null && key.equals(audioFocusKey)) {
              updateAudioFocusPreference();
            }
          }
        };

    /**
     * Listens to changes in the TalkBack state to determine which preference items should be enable
     * or disabled.
     */
    private final ServiceStateListener mServiceStateListener =
        new ServiceStateListener() {
          @Override
          public void onServiceStateChanged(int newState) {
            updateDimingPreferenceStatus();
          }
        };
  }
}
