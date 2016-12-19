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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.TwoStatePreference;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.os.BuildCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import com.android.talkback.controller.DimScreenControllerApp;
import com.android.talkback.controller.TelevisionNavigationController;
import com.android.talkback.eventprocessor.ProcessorFocusAndSingleTap;
import com.android.talkback.eventprocessor.ProcessorVolumeStream;
import com.android.talkback.labeling.LabelManagerSummaryActivity;
import com.android.talkback.tutorial.AccessibilityTutorialActivity;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.LogUtils;
import com.android.utils.PackageManagerUtils;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity used to set TalkBack's service preferences.
 */
public class TalkBackPreferencesActivity extends Activity {
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

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new TalkBackPreferenceFragment()).commit();
    }

    public static class TalkBackPreferenceFragment extends PreferenceFragment {
        /** The gestures that may need to be reassigned if node tree debugging is disabled. */
        private static final int[] GESTURE_PREF_KEY_IDS = {
                R.string.pref_shortcut_down_and_left_key,
                R.string.pref_shortcut_down_and_right_key,
                R.string.pref_shortcut_left_and_down_key,
                R.string.pref_shortcut_left_and_up_key,
                R.string.pref_shortcut_right_and_down_key,
                R.string.pref_shortcut_right_and_up_key,
                R.string.pref_shortcut_up_and_left_key,
                R.string.pref_shortcut_up_and_right_key,
                R.string.pref_shortcut_single_tap_key,
                R.string.pref_shortcut_double_tap_key
        };

        private static final int[] HIDDEN_PREFERENCE_KEY_IDS_IN_ARC = {
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

        /** Preferences managed by this activity. */
        private SharedPreferences mPrefs;

        /** AlertDialog to ask if user really wants to disable explore by touch. */
        private AlertDialog mExploreByTouchDialog;

        /** AlertDialog to ask if user really wants to enable node tree debugging. */
        private AlertDialog mTreeDebugDialog;

        private boolean mContentObserverRegistered = false;

        /** Id for seeing if the Explore by touch dialog was active when restoring state. */
        private static final String EXPLORE_BY_TOUCH_DIALOG_ACTIVE = "exploreDialogActive";

        /** Id for seeing if the tree debug dialog was active when restoring state. */
        private static final String TREE_DEBUG_DIALOG_ACTIVE = "treeDebugDialogActive";

        private static final String HELP_URL = "https://support.google.com/accessibility/" +
                "android/answer/6283677";

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
            if (BuildCompat.isAtLeastN()) {
                getPreferenceManager().setStorageDeviceProtected();
            }

            mPrefs = SharedPreferencesUtils.getSharedPreferences(activity);
            addPreferencesFromResource(R.xml.preferences);

            final TwoStatePreference prefTreeDebug = (TwoStatePreference) findPreferenceByResId(
                    R.string.pref_tree_debug_reflect_key);
            prefTreeDebug.setOnPreferenceChangeListener(mTreeDebugChangeListener);

            fixListSummaries(getPreferenceScreen());

            assignTtsSettingsIntent();
            assignTutorialIntent();
            assignLabelManagerIntent();
            assignKeyboardShortcutIntent();
            assignDumpA11yEventIntent();

            checkTelevision();
            checkTouchExplorationSupport();
            checkWebScriptsSupport();
            checkTelephonySupport();
            checkVibrationSupport();
            checkProximitySupport();
            checkAccelerometerSupport();
            checkInstalledBacks();
            showTalkBackVersion();
            updateTalkBackShortcutStatus();

            // We should never try to open the play store in WebActivity.
            assignPlayStoreIntentToPreference(R.string.pref_play_store_key,
                    "https://play.google.com/store/apps/details" +
                            "?id=com.google.android.marvin.talkback");

            assignWebIntentToPreference(R.string.pref_policy_key,
                    "http://www.google.com/policies/privacy/");
            assignWebIntentToPreference(R.string.pref_show_tos_key,
                    "http://www.google.com/mobile/toscountry");

            assignFeedbackIntentToPreference(R.string.pref_help_and_feedback_key);

            if (TalkBackService.isInArc()) {
                hidePreferencesForArc();
            }
        }

        private void assignPlayStoreIntentToPreference(int preferenceId, String url) {
            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_miscellaneous_key);
            final Preference pref = findPreferenceByResId(preferenceId);
            if (pref == null) {
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!canHandleIntent(intent)) {
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

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            Activity activity = getActivity();
            if (activity != null && !canHandleIntent(intent)) {
                intent = new Intent(activity, WebActivity.class);
                intent.setData(Uri.parse(url));
            }

            pref.setIntent(intent);
        }

        private void assignFeedbackIntentToPreference(int preferenceId) {
            Preference pref = findPreferenceByResId(preferenceId);
            if (pref == null) {
                return;
            }
            if (HelpAndFeedbackUtils.supportsHelpAndFeedback(
                    getActivity().getApplicationContext())) {
                pref.setTitle(R.string.title_pref_help_and_feedback);
                pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
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

            mPrefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

            updateTalkBackShortcutStatus();
            updateDimingPreferenceStatus();
            updateDumpA11yEventPreferenceSummary();
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

            mPrefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
            savedInstanceState.putBoolean(
                    EXPLORE_BY_TOUCH_DIALOG_ACTIVE, mExploreByTouchDialog != null);
            savedInstanceState.putBoolean(
                    TREE_DEBUG_DIALOG_ACTIVE, mTreeDebugDialog != null);
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
        }

        private void registerTouchSettingObserver() {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            Uri uri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
            activity.getContentResolver().registerContentObserver(
                    uri, false, mTouchExploreObserver);
            mContentObserverRegistered = true;
        }

        /**
         * Assigns the intent to open text-to-speech settings.
         */
        private void assignTtsSettingsIntent() {
            PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                R.string.pref_category_when_to_speak_key);
            Preference ttsSettingsPreference =
                    findPreferenceByResId(R.string.pref_tts_settings_key);

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

        /**
         * Assigns the appropriate intent to the tutorial preference.
         */
        private void assignTutorialIntent() {
            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_miscellaneous_key);
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
                final Intent tutorialIntent = new Intent(
                        activity, AccessibilityTutorialActivity.class);
                tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                prefTutorial.setIntent(tutorialIntent);
            }
        }

        /**
         * Assigns the appropriate intent to the label manager preference.
         */
        private void assignLabelManagerIntent() {
            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_touch_exploration_key);
            final Preference prefManageLabels = findPreferenceByResId(
                    R.string.pref_manage_labels_key);

            if ((category == null) || (prefManageLabels == null)) {
                return;
            }

            if (Build.VERSION.SDK_INT < LabelManagerSummaryActivity.MIN_API_LEVEL) {
                category.removePreference(prefManageLabels);
                return;
            }

            Activity activity = getActivity();
            if (activity != null) {
                final Intent labelManagerIntent = new Intent(
                        activity, LabelManagerSummaryActivity.class);
                labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                prefManageLabels.setIntent(labelManagerIntent);
            }
        }

        /**
         * Assigns the appropriate intent to the dump accessibility event preference.
         */
        private void assignDumpA11yEventIntent() {
            final Preference prefDumpA11yEvent = findPreferenceByResId(
                    R.string.pref_dump_a11y_event_key);

            if (prefDumpA11yEvent == null) {
                return;
            }

            Activity activity = getActivity();
            if (activity != null) {
                final Intent filterA11yEventIntent = new Intent(
                        activity, TalkBackDumpAccessibilityEventActivity.class);
                prefDumpA11yEvent.setIntent(filterA11yEventIntent);
            }
        }

        /**
         * Assigns the appropriate intent to the keyboard shortcut preference.
         */
        private void assignKeyboardShortcutIntent() {
            final PreferenceGroup category =
                    (PreferenceGroup) findPreferenceByResId(
                            R.string.pref_category_miscellaneous_key);
            final Preference keyboardShortcutPref = findPreferenceByResId(
                    R.string.pref_category_manage_keyboard_shortcut_key);

            if ((category == null) || (keyboardShortcutPref == null)) {
                return;
            }

            if (Build.VERSION.SDK_INT < KeyComboManager.MIN_API_LEVEL) {
                category.removePreference(keyboardShortcutPref);
                return;
            }

            Activity activity = getActivity();
            if (activity != null) {
                final Intent labelManagerIntent = new Intent(activity,
                        TalkBackKeyboardShortcutPreferencesActivity.class);
                labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                keyboardShortcutPref.setIntent(labelManagerIntent);
            }
        }

        /**
         * Assigns the appropriate intent to the touch exploration preference.
         */
        private void checkTouchExplorationSupport() {
            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_touch_exploration_key);
            if (category == null) {
                return;
            }

            checkTouchExplorationSupportInner(category);
        }

        /**
         * Touch exploration preference management code
         *
         * @param category The touch exploration category.
         */
        private void checkTouchExplorationSupportInner(PreferenceGroup category) {
            final TwoStatePreference prefTouchExploration =
                    (TwoStatePreference) findPreferenceByResId(
                            R.string.pref_explore_by_touch_reflect_key);
            if (prefTouchExploration == null) {
                return;
            }

            // Remove single-tap preference if it's not supported on this device.
            final TwoStatePreference prefSingleTap = (TwoStatePreference) findPreferenceByResId(
                    R.string.pref_single_tap_key);
            if ((prefSingleTap != null)
                    && (Build.VERSION.SDK_INT <
                    ProcessorFocusAndSingleTap.MIN_API_LEVEL_SINGLE_TAP)) {
                category.removePreference(prefSingleTap);
            }

            // Ensure that changes to the reflected preference's checked state never
            // trigger content observers.
            prefTouchExploration.setPersistent(false);

            // Synchronize the reflected state.
            updateTouchExplorationState();

            // Set up listeners that will keep the state synchronized.
            prefTouchExploration.setOnPreferenceChangeListener(mTouchExplorationChangeListener);

            // Hook in the external PreferenceActivity for gesture management
            final Preference shortcutsScreen = findPreferenceByResId(
                    R.string.pref_category_manage_gestures_key);

            Activity activity = getActivity();
            if (activity != null) {
                final Intent shortcutsIntent = new Intent(
                        activity, TalkBackShortcutPreferencesActivity.class);
                shortcutsScreen.setIntent(shortcutsIntent);
            }
        }

        private void updateTalkBackShortcutStatus() {
            final TwoStatePreference preference = (TwoStatePreference) findPreferenceByResId(
                    R.string.pref_two_volume_long_press_key);
            if (preference == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= ProcessorVolumeStream.MIN_API_LEVEL) {
                preference.setEnabled(
                        TalkBackService.getInstance() != null || preference.isChecked());
            } else {
                final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                        R.string.pref_category_miscellaneous_key);
                if (category == null) {
                    return;
                }
                category.removePreference(preference);
            }

        }

        private void updateDimingPreferenceStatus() {
            final TwoStatePreference dimPreference = (TwoStatePreference) findPreferenceByResId(
                    R.string.pref_dim_when_talkback_enabled_key);
            final TwoStatePreference dimShortcutPreference =
                    (TwoStatePreference) findPreferenceByResId(
                            R.string.pref_dim_volume_three_clicks_key);
            if (dimPreference == null || dimShortcutPreference == null) {
                return;
            }
            final TalkBackService talkBack = TalkBackService.getInstance();
            if (!DimScreenControllerApp.IS_SUPPORTED_PLATFORM) {
                final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                        R.string.pref_category_miscellaneous_key);
                if (category == null) {
                    return;
                }
                category.removePreference(dimPreference);
                category.removePreference(dimShortcutPreference);
                return;
            }

            // Make sure that we have the latest value of the dim preference before continuing.
            boolean dimEnabled = SharedPreferencesUtils.getBooleanPref(mPrefs, getResources(),
                    R.string.pref_dim_when_talkback_enabled_key,
                    R.bool.pref_dim_when_talkback_enabled_default);
            dimPreference.setChecked(dimEnabled);

            dimPreference.setEnabled(
                    TalkBackService.isServiceActive() || dimPreference.isChecked());
            dimPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
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
            final Preference prefDumpA11yEvent = findPreferenceByResId(
                    R.string.pref_dump_a11y_event_key);

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

            prefDumpA11yEvent.setSummary(getResources().getQuantityString(
                    R.plurals.template_dump_event_count, /* id */
                    count, /* quantity */
                    count /* formatArgs */));
        }

        /**
         * Updates the preferences state to match the actual state of touch
         * exploration. This is called once when the preferences activity launches
         * and again whenever the actual state of touch exploration changes.
         */
        private void updateTouchExplorationState() {
            final TwoStatePreference prefTouchExploration =
                    (TwoStatePreference) findPreferenceByResId(
                            R.string.pref_explore_by_touch_reflect_key);

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
            final boolean requestedState = SharedPreferencesUtils.getBooleanPref(prefs, res,
                    R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
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
                LogUtils.log(this, Log.DEBUG,
                        "Set touch exploration preference to reflect actual state %b", actualState);
                SharedPreferencesUtils.putBooleanPref(
                        prefs, res, R.string.pref_explore_by_touch_key, actualState);
            }

            // Ensure that the check box preference reflects the requested state,
            // which was just synchronized to match the actual state.
            if (reflectedState != actualState) {
                prefTouchExploration.setChecked(actualState);
            }
        }

        /**
         * Returns whether touch exploration is enabled. This is more reliable than
         * {@link AccessibilityManager#isTouchExplorationEnabled()} because it
         * updates atomically.
         *
         * TODO: Move this method to TalkBackService.
         */
        public static boolean isTouchExplorationEnabled(ContentResolver resolver) {
            return Settings.Secure.getInt(resolver,
                    Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1;
        }

        /**
         * Since the "%s" summary is currently broken, this sets the preference
         * change listener for all {@link ListPreference} views to fill in the
         * summary with the current entry value.
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
                    mPreferenceChangeListener.onPreferenceChange(preference,
                            ((ListPreference) preference).getValue());

                    preference.setOnPreferenceChangeListener(mPreferenceChangeListener);
                }
            }
        }

        /**
         * Ensure that web script injection settings do not appear on devices before
         * user-customization of web-scripts were available in the framework.
         */
        private void checkWebScriptsSupport() {
            // TalkBack can control web script injection on API 18+ only.
            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_developer_key);
            final Preference prefWebScripts = findPreferenceByResId(R.string.pref_web_scripts_key);

            if (prefWebScripts != null) {
                category.removePreference(prefWebScripts);
            }
        }

        /**
         * Ensure that telephony-related settings do not appear on devices without
         * telephony.
         */
        private void checkTelephonySupport() {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            final TelephonyManager telephony =
                    (TelephonyManager) activity.getSystemService(TELEPHONY_SERVICE);
            final int phoneType = telephony.getPhoneType();

            if (phoneType != TelephonyManager.PHONE_TYPE_NONE) {
                return;
            }
        }

        /**
         * Ensure that the vibration setting does not appear on devices without a
         * vibrator.
         */
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
         * Ensure that the proximity sensor setting does not appear on devices
         * without a proximity sensor.
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

            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_when_to_speak_key);
            final TwoStatePreference prefProximity =
                    (TwoStatePreference) findPreferenceByResId(R.string.pref_proximity_key);

            if (prefProximity != null) {
                prefProximity.setChecked(false);
                category.removePreference(prefProximity);
            }
        }

        /**
         * Ensure that the shake to start continuous reading setting does not
         * appear on devices without a proximity sensor.
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

            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_when_to_speak_key);
            final ListPreference prefShake = (ListPreference) findPreferenceByResId(
                    R.string.pref_shake_to_read_threshold_key);

            if (prefShake != null) {
                category.removePreference(prefShake);
            }
        }

        /**
         * Ensure that sound and vibration preferences are removed if the latest
         * versions of KickBack and SoundBack are installed.
         */
        private void checkInstalledBacks() {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            final PreferenceGroup category =
                    (PreferenceGroup) findPreferenceByResId(R.string.pref_category_feedback_key);
            final TwoStatePreference prefVibration =
                    (TwoStatePreference) findPreferenceByResId(R.string.pref_vibration_key);
            final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                    activity, TalkBackUpdateHelper.KICKBACK_PACKAGE);
            final boolean removeKickBack = (kickBackVersionCode
                    >= TalkBackUpdateHelper.KICKBACK_REQUIRED_VERSION);

            if (removeKickBack) {
                if (prefVibration != null) {
                    category.removePreference(prefVibration);
                }
            }

            final TwoStatePreference prefSoundBack =
                    (TwoStatePreference) findPreferenceByResId(R.string.pref_soundback_key);
            final Preference prefSoundBackVolume =
                    findPreferenceByResId(R.string.pref_soundback_volume_key);
            final int soundBackVersionCode = PackageManagerUtils.getVersionCode(
                    activity, TalkBackUpdateHelper.SOUNDBACK_PACKAGE);
            final boolean removeSoundBack = (soundBackVersionCode
                    >= TalkBackUpdateHelper.SOUNDBACK_REQUIRED_VERSION);

            if (removeSoundBack) {
                if (prefSoundBackVolume != null) {
                    category.removePreference(prefSoundBackVolume);
                }

                if (prefSoundBack != null) {
                    category.removePreference(prefSoundBack);
                }
            }

            if (removeKickBack && removeSoundBack) {
                if (category != null) {
                    getPreferenceScreen().removePreference(category);
                }
            }
        }

        /**
         * Checks if the device is Android TV and removes preferences that shouldn't be set when on
         * Android TV.
         **/
        private void checkTelevision() {
            if (TelevisionNavigationController.isContextTelevision(getActivity())) {
                final PreferenceGroup touchCategory = (PreferenceGroup) findPreferenceByResId(
                        R.string.pref_category_touch_exploration_key);
                final PreferenceGroup miscCategory = (PreferenceGroup) findPreferenceByResId(
                        R.string.pref_category_miscellaneous_key);

                final Preference dimPreference = findPreferenceByResId(
                        R.string.pref_dim_when_talkback_enabled_key);
                final Preference dimShortcutPreference = findPreferenceByResId(
                        R.string.pref_dim_volume_three_clicks_key);
                final Preference suspendShortcutPreference = findPreferenceByResId(
                        R.string.pref_two_volume_long_press_key);
                final Preference resumePreference = findPreferenceByResId(
                        R.string.pref_resume_talkback_key);

                getPreferenceScreen().removePreference(touchCategory);
                miscCategory.removePreference(dimPreference);
                miscCategory.removePreference(dimShortcutPreference);
                miscCategory.removePreference(suspendShortcutPreference);
                miscCategory.removePreference(resumePreference);
            }
        }

        private static PackageInfo getPackageInfo(Activity activity) {
            try {
                return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            } catch (NameNotFoundException e) {
                return null;
            }
        }

        /**
         * Show TalkBack full version number in the Play Store button.
         */
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
                final PreferenceGroup category = (PreferenceGroup)
                        findPreferenceByResId(R.string.pref_category_miscellaneous_key);
                if (category != null) {
                    category.removePreference(playStoreButton);
                }
            }

            if (playStoreButton.getIntent() != null &&
                    activity.getPackageManager().queryIntentActivities(
                            playStoreButton.getIntent(), 0).size() == 0) {
                // Not needed, but playing safe since this is hard to test outside of China
                playStoreButton.setIntent(null);
                final PreferenceGroup category = (PreferenceGroup)
                        findPreferenceByResId(R.string.pref_category_miscellaneous_key);
                if (category != null) {
                    category.removePreference(playStoreButton);
                }
            } else {
                final String versionNumber = String.valueOf(packageInfo.versionCode);
                final int length = versionNumber.length();

                playStoreButton.setSummary(getString(R.string.summary_pref_play_store,
                        String.valueOf(Integer.parseInt(versionNumber.substring(0, length-7))) +
                                "." +
                                String.valueOf(Integer.parseInt(
                                        versionNumber.substring(length-7, length-5))) +
                                "." +
                                String.valueOf(Integer.parseInt(
                                        versionNumber.substring(length-5, length-3))) +
                                "." +
                                String.valueOf(Integer.parseInt(
                                        versionNumber.substring(length-3)))));
            }
        }

        private void hidePreferencesForArc() {
            Set<String> hiddenPreferenceKeysInArc = new HashSet<String>();
            for (int hiddenPreferenceKeyId : HIDDEN_PREFERENCE_KEY_IDS_IN_ARC) {
                hiddenPreferenceKeysInArc.add(getString(hiddenPreferenceKeyId));
            }

            hidePreferences(getPreferenceScreen(), hiddenPreferenceKeysInArc);
        }

        private void hidePreferences(PreferenceGroup root, Set<String> preferenceKeysToBeHidden) {
            for (int i = 0; i < root.getPreferenceCount(); i++) {
                Preference preference = root.getPreference(i);
                if (preferenceKeysToBeHidden.contains(preference.getKey())) {
                    root.removePreference(preference);
                    i--;
                } else if (preference instanceof PreferenceGroup) {
                    hidePreferences((PreferenceGroup) preference, preferenceKeysToBeHidden);
                }
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
         * Updates the preference that controls whether TalkBack will attempt to
         * request Explore by Touch.
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
            SharedPreferencesUtils.putBooleanPref(prefs, getResources(),
                    R.string.pref_explore_by_touch_key, requestedState);

            // If TalkBack is inactive, we should immediately reflect the change in
            // "requested" state.
            if (!TalkBackService.isServiceActive()) {
                return true;
            }
            if(requestedState && TalkBackService.getInstance() != null) {
                TalkBackService.getInstance().showTutorial();
            }

            // If accessibility is on, we should wait for the "actual" state to
            // change, then reflect that change. If the user declines the system's
            // touch exploration dialog, the "actual" state will not change and
            // nothing needs to happen.
            LogUtils.log(this, Log.DEBUG,
                    "TalkBack active, waiting for EBT request to take effect");
            return false;
        }

        private AlertDialog createDisableExploreByTouchDialog() {
            Activity activity = getActivity();
            if (activity == null) {
                return null;
            }

            final DialogInterface.OnCancelListener cancel  = new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mExploreByTouchDialog = null;
                }
            };

            final DialogInterface.OnClickListener cancelClick  = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mExploreByTouchDialog = null;
                }
            };

            final DialogInterface.OnClickListener okClick = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mExploreByTouchDialog = null;
                    if (setTouchExplorationRequested(false)) {
                        // Manually tick the check box since we're not returning to
                        // the preference change listener.
                        final TwoStatePreference prefTouchExploration =
                                (TwoStatePreference) findPreferenceByResId(
                                        R.string.pref_explore_by_touch_reflect_key);
                        prefTouchExploration.setChecked(false);
                    }
                }
            };

            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.dialog_title_disable_exploration)
                    .setMessage(R.string.dialog_message_disable_exploration)
                    .setNegativeButton(android.R.string.cancel, cancelClick)
                    .setPositiveButton(android.R.string.yes, okClick)
                    .setOnCancelListener(cancel)
                    .create();
        }

        private AlertDialog createEnableTreeDebugDialog() {
            Activity activity = getActivity();
            if (activity == null) {
                return null;
            }

            final DialogInterface.OnCancelListener cancel = new DialogInterface.OnCancelListener() {
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

            final DialogInterface.OnClickListener okClick = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mTreeDebugDialog = null;

                    Activity innerActivity = getActivity();
                    if (innerActivity == null) {
                        return;
                    }

                    final SharedPreferences prefs =
                            SharedPreferencesUtils.getSharedPreferences(innerActivity);
                    SharedPreferencesUtils.putBooleanPref(prefs, getResources(),
                            R.string.pref_tree_debug_key, true);

                    // Manually tick the check box since we're not returning to
                    // the preference change listener.
                    final TwoStatePreference prefTreeDebug =
                            (TwoStatePreference) findPreferenceByResId(
                                    R.string.pref_tree_debug_reflect_key);
                    prefTreeDebug.setChecked(true);
                }
            };

            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.dialog_title_enable_tree_debug)
                    .setMessage(R.string.dialog_message_enable_tree_debug)
                    .setNegativeButton(android.R.string.cancel, cancelClick)
                    .setPositiveButton(android.R.string.yes, okClick)
                    .setOnCancelListener(cancel)
                    .create();
        }

        private final Handler mHandler = new Handler();

        private final ContentObserver mTouchExploreObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                if (selfChange) {
                    return;
                }

                // The actual state of touch exploration has changed.
                updateTouchExplorationState();
            }
        };

        private final OnPreferenceChangeListener
                mTouchExplorationChangeListener = new OnPreferenceChangeListener() {
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

        private final OnPreferenceChangeListener
                mTreeDebugChangeListener = new OnPreferenceChangeListener() {
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
                final SharedPreferences prefs =
                        SharedPreferencesUtils.getSharedPreferences(activity);
                final SharedPreferences.Editor prefEditor = prefs.edit();

                prefEditor.putBoolean(getString(R.string.pref_tree_debug_key), false);

                for (int prefKey : GESTURE_PREF_KEY_IDS) {
                    final String currentValue = prefs.getString(getString(prefKey), null);
                    if (getString(R.string.shortcut_value_print_node_tree).equals(currentValue)) {
                        prefEditor.putString(getString(prefKey),
                                getString(R.string.shortcut_value_unassigned));
                    }
                }

                prefEditor.apply();
                return true;
            }
        };

        /**
         * Listens for preference changes and updates the summary to reflect the
         * current setting. This shouldn't be necessary, since preferences are
         * supposed to automatically do this when the summary is set to "%s".
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
                                preference.setSummary(
                                        entries[index].toString().replaceAll("%", "%%"));
                            } else {
                                preference.setSummary("");
                            }
                        }

                        final String key = preference.getKey();
                        if (getString(R.string.pref_resume_talkback_key).equals(key)) {
                            final String oldValue = SharedPreferencesUtils.getStringPref(
                                    mPrefs, getResources(), R.string.pref_resume_talkback_key,
                                    R.string.pref_resume_talkback_default);
                            if (!newValue.equals(oldValue)) {
                                // Reset the suspend warning dialog when the resume
                                // preference changes.
                                SharedPreferencesUtils.putBooleanPref(mPrefs, getResources(),
                                        R.string.pref_show_suspension_confirmation_dialog, true);
                            }
                        }

                        return true;
                    }
                };

        /**
         * Listens to shared preference changes and updates the preference items accordingly.
         */
        private final OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
                new OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
                        String dimKey = getString(R.string.pref_dim_when_talkback_enabled_key);
                        if (key != null && key.equals(dimKey)) {
                            updateDimingPreferenceStatus();
                        }
                    }
                };

        /**
         * Listens to changes in the TalkBack state to determine which preference items should be
         * enable or disabled.
         */
        private final TalkBackService.ServiceStateListener mServiceStateListener =
                new TalkBackService.ServiceStateListener() {
                    @Override
                    public void onServiceStateChanged(int newState) {
                        updateDimingPreferenceStatus();
                    }
                };
    }
}
