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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.accessibility.AccessibilityManager;

import android.widget.CheckBox;
import android.widget.ScrollView;
import com.android.talkback.eventprocessor.ProcessorFocusAndSingleTap;
import com.android.talkback.eventprocessor.ProcessorVolumeStream;
import com.android.talkback.labeling.LabelManagerSummaryActivity;
import com.android.talkback.tutorial.AccessibilityTutorialActivity;
import com.android.utils.LogUtils;
import com.android.utils.PackageManagerUtils;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.List;

/**
 * Activity used to set TalkBack's service preferences.
 */
@SuppressWarnings("deprecation")
public class TalkBackPreferencesActivity extends PreferenceActivity {
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

    /** Preferences managed by this activity. */
    private SharedPreferences mPrefs;

    /** AlertDialog to ask if user really wants to disable explore by touch. */
    private AlertDialog mExploreByTouchDialog;

    /** AlertDialog to ask if user really wants to enable node tree debugging. */
    private AlertDialog mTreeDebugDialog;

    /** Id for seeing if the Explore by touch dialog was active when restoring state. */
    private static final String EXPLORE_BY_TOUCH_DIALOG_ACTIVE = "exploreDialogActive";

    /** Id for seeing if the tree debug dialog was active when restoring state. */
    private static final String TREE_DEBUG_DIALOG_ACTIVE = "treeDebugDialogActive";

    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        addPreferencesFromResource(R.xml.preferences);

        final CheckBoxPreference prefDimEnabled = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_dim_when_talkback_enabled_key);

        mPrefs.registerOnSharedPreferenceChangeListener(
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String key) {
                    String dimKey = getString(R.string.pref_dim_when_talkback_enabled_key);
                    if (key != null &&  key.equals(dimKey)) {
                        boolean value = mPrefs.getBoolean(dimKey,
                                getResources().getBoolean(
                                        R.bool.pref_dim_when_talkback_enabled_default));
                        prefDimEnabled.setChecked(value);
                    }
                }
            });

        final CheckBoxPreference prefTreeDebug = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_tree_debug_reflect_key);
        prefTreeDebug.setOnPreferenceChangeListener(mTreeDebugChangeListener);

        fixListSummaries(getPreferenceScreen());

        assignTutorialIntent();
        assignLabelManagerIntent();
        assignKeyboardShortcutIntent();

        checkTouchExplorationSupport();
        checkWebScriptsSupport();
        checkTelephonySupport();
        checkVibrationSupport();
        checkProximitySupport();
        checkAccelerometerSupport();
        checkInstalledBacks();
        showTalkBackVersion();
        updateTalkBackShortcutStatus();

        assignWebIntentToPreference(R.string.pref_play_store_key,
                "https://play.google.com/store/apps/details?id=com.google.android.marvin.talkback");
        assignWebIntentToPreference(R.string.pref_policy_key,
                "http://www.google.com/policies/privacy/");
        assignWebIntentToPreference(R.string.pref_show_tos_key,
                "http://www.google.com/mobile/toscountry");
    }

    private void assignWebIntentToPreference(int preferenceId, String url) {
        Preference pref = findPreferenceByResId(preferenceId);
        if (pref == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (!canHandleIntent(intent)) {
            intent = new Intent(this, WebActivity.class);
            intent.setData(Uri.parse(url));
        }

        pref.setIntent(intent);
    }

    private boolean canHandleIntent(Intent intent) {
        PackageManager manager = getPackageManager();
        List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
        return infos != null && infos.size() > 0;
    }

    @Override
    public void onResume() {
        super.onResume();
        TalkBackService talkBackService = TalkBackService.getInstance();
        if (talkBackService != null && talkBackService.supportsTouchScreen()) {
            registerTouchSettingObserver();
        }

        if (mExploreByTouchDialog != null) {
            mExploreByTouchDialog.show();
        }

        if (mTreeDebugDialog != null) {
            mTreeDebugDialog.show();
        }

        updateTalkBackShortcutStatus();
        updateDimingPreferenceStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        TalkBackService talkBackService = TalkBackService.getInstance();
        if (talkBackService != null && talkBackService.supportsTouchScreen()) {
            getContentResolver().unregisterContentObserver(mTouchExploreObserver);
        }

        if (mExploreByTouchDialog != null) {
            mExploreByTouchDialog.dismiss();
        }

        if (mTreeDebugDialog != null) {
            mTreeDebugDialog.dismiss();
        }
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
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(EXPLORE_BY_TOUCH_DIALOG_ACTIVE)) {
            mExploreByTouchDialog = createDisableExploreByTouchDialog();
        }

        if (savedInstanceState.getBoolean(TREE_DEBUG_DIALOG_ACTIVE)) {
            mTreeDebugDialog = createEnableTreeDebugDialog();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void registerTouchSettingObserver() {
        final Uri uri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
        getContentResolver().registerContentObserver(uri, false, mTouchExploreObserver);
    }

    /**
     * Assigns the appropriate intent to the tutorial preference.
     */
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

        final Intent tutorialIntent = new Intent(this, AccessibilityTutorialActivity.class);
        tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        prefTutorial.setIntent(tutorialIntent);
    }

    /**
     * Assigns the appropriate intent to the label manager preference.
     */
    private void assignLabelManagerIntent() {
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(
                        R.string.pref_category_touch_exploration_key);
        final Preference prefManageLabels = findPreferenceByResId(R.string.pref_manage_labels_key);

        if ((category == null) || (prefManageLabels == null)) {
            return;
        }

        if (Build.VERSION.SDK_INT < LabelManagerSummaryActivity.MIN_API_LEVEL) {
            category.removePreference(prefManageLabels);
            return;
        }

        final Intent labelManagerIntent = new Intent(this, LabelManagerSummaryActivity.class);
        labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        prefManageLabels.setIntent(labelManagerIntent);
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

        final Intent labelManagerIntent = new Intent(this,
                TalkBackKeyboardShortcutPreferencesActivity.class);
        labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        labelManagerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        keyboardShortcutPref.setIntent(labelManagerIntent);
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
     * Touch exploration preference management code specific to devices running
     * Jelly Bean and above.
     *
     * @param category The touch exploration category.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void checkTouchExplorationSupportInner(PreferenceGroup category) {
        final CheckBoxPreference prefTouchExploration = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_explore_by_touch_reflect_key);
        if (prefTouchExploration == null) {
            return;
        }

        // Remove single-tap preference if it's not supported on this device.
        final CheckBoxPreference prefSingleTap = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_single_tap_key);
        if ((prefSingleTap != null)
                && (Build.VERSION.SDK_INT < ProcessorFocusAndSingleTap.MIN_API_LEVEL_SINGLE_TAP)) {
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
        final Intent shortcutsIntent = new Intent(this, TalkBackShortcutPreferencesActivity.class);
        shortcutsScreen.setIntent(shortcutsIntent);
    }

    private void updateTalkBackShortcutStatus() {
        final CheckBoxPreference preference = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_two_volume_long_press_key);
        if (preference == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= ProcessorVolumeStream.MIN_API_LEVEL) {
            preference.setEnabled(TalkBackService.getInstance() != null || preference.isChecked());
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
        final CheckBoxPreference dimPreference = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_dim_when_talkback_enabled_key);
        if (dimPreference == null) {
            return;
        }

        if (Build.VERSION.SDK_INT < ProcessorVolumeStream.MIN_API_LEVEL) {
            final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                    R.string.pref_category_miscellaneous_key);
            if (category == null) {
                return;
            }
            category.removePreference(dimPreference);
            return;
        }

        dimPreference.setEnabled(TalkBackService.isServiceActive() || dimPreference.isChecked());
        dimPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue == null || !(newValue instanceof Boolean)) {
                    return true;
                }

                boolean boolValue = (Boolean) newValue;

                if (!boolValue && !TalkBackService.isServiceActive()) {
                    dimPreference.setEnabled(false);
                } else if (boolValue &&
                    mPrefs.getBoolean(getString(R.string.pref_show_dim_screen_confirmation_dialog),
                                true)) {
                    showDimScreenDialog(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dimPreference.setChecked(true);
                        }
                    });
                    return false;
                }
                return true;
            }
        });
    }

    private void showDimScreenDialog(final DialogInterface.OnClickListener onConfirmListener) {
        LayoutInflater inflater = LayoutInflater.from(this);
        @SuppressLint("InflateParams") final ScrollView root = (ScrollView) inflater.inflate(
                R.layout.dim_screen_confirmation_dialog, null);
        final CheckBox confirmCheckBox = (CheckBox) root.findViewById(R.id.show_warning_checkbox);

        final DialogInterface.OnClickListener okayClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (!confirmCheckBox.isChecked()) {
                        SharedPreferencesUtils.putBooleanPref(mPrefs, getResources(),
                                R.string.pref_show_dim_screen_confirmation_dialog, false);
                    }

                    if (onConfirmListener != null) {
                        onConfirmListener.onClick(dialog, which);
                    }
                }
            }
        };

        Dialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_dim_screen)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, okayClick)
                .create();
        dialog.show();
    }

    /**
     * Updates the preferences state to match the actual state of touch
     * exploration. This is called once when the preferences activity launches
     * and again whenever the actual state of touch exploration changes.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void updateTouchExplorationState() {
        final CheckBoxPreference prefTouchExploration = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_explore_by_touch_reflect_key);

        if (prefTouchExploration == null) {
            return;
        }

        final ContentResolver resolver = getContentResolver();
        final Resources res = getResources();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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
     */
    public static boolean isTouchExplorationEnabled(ContentResolver resolver) {
        return Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1;
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
        final TelephonyManager telephony = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        final int phoneType = telephony.getPhoneType();

        if (phoneType != TelephonyManager.PHONE_TYPE_NONE) {
            return;
        }

        final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                R.string.pref_category_when_to_speak_key);
        final Preference prefCallerId = findPreferenceByResId(R.string.pref_caller_id_key);

        if (prefCallerId != null) {
            category.removePreference(prefCallerId);
        }
    }

    /**
     * Ensure that the vibration setting does not appear on devices without a
     * vibrator.
     */
    private void checkVibrationSupport() {
        final Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (vibrator != null && vibrator.hasVibrator()) {
            return;
        }

        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_feedback_key);
        final CheckBoxPreference prefVibration =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_vibration_key);

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
        final SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        final Sensor proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (proximity != null) {
            return;
        }

        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
        final CheckBoxPreference prefProximity =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_proximity_key);

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
        final SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
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
     * Ensure that sound and vibration preferences are removed if the latest
     * versions of KickBack and SoundBack are installed.
     */
    private void checkInstalledBacks() {
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_feedback_key);
        final CheckBoxPreference prefVibration =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_vibration_key);
        final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                this, TalkBackUpdateHelper.KICKBACK_PACKAGE);
        final boolean removeKickBack = (kickBackVersionCode
                >= TalkBackUpdateHelper.KICKBACK_REQUIRED_VERSION);

        if (removeKickBack) {
            if (prefVibration != null) {
                category.removePreference(prefVibration);
            }
        }

        final CheckBoxPreference prefSoundBack =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_soundback_key);
        final Preference prefSoundBackVolume =
                findPreferenceByResId(R.string.pref_soundback_volume_key);
        final int soundBackVersionCode = PackageManagerUtils.getVersionCode(
                this, TalkBackUpdateHelper.SOUNDBACK_PACKAGE);
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
     * Shows TalkBack's abbreviated version number in the action bar, and the
     * full version number in the Play Store button.
     */
    private void showTalkBackVersion() {
        try {
            final PackageInfo packageInfo =
                    getPackageManager().getPackageInfo(getPackageName(), 0);

            final ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(
                        getString(R.string.talkback_preferences_subtitle,
                                packageInfo.versionName));
            }

            final Preference playStoreButton = findPreferenceByResId(R.string.pref_play_store_key);
            if (playStoreButton == null) {
                return;
            }

            if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
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
                    getPackageManager().queryIntentActivities(playStoreButton.getIntent(), 0).size()
                            == 0) {
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

        } catch (NameNotFoundException e) {
            // Nothing to do if we can't get the package name.
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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                TalkBackPreferencesActivity.this);

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
        LogUtils.log(this, Log.DEBUG, "TalkBack active, waiting for EBT request to take effect");
        return false;
    }

    private AlertDialog createDisableExploreByTouchDialog() {
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
                    final CheckBoxPreference prefTouchExploration =
                            (CheckBoxPreference) findPreferenceByResId(
                                    R.string.pref_explore_by_touch_reflect_key);
                    prefTouchExploration.setChecked(false);
                }
            }
        };

        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_disable_exploration)
                .setMessage(R.string.dialog_message_disable_exploration)
                .setNegativeButton(android.R.string.cancel, cancelClick)
                .setPositiveButton(android.R.string.yes, okClick)
                .setOnCancelListener(cancel)
                .create();
    }

    private AlertDialog createEnableTreeDebugDialog() {
        final DialogInterface.OnCancelListener cancel  = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mTreeDebugDialog = null;
            }
        };

        final DialogInterface.OnClickListener cancelClick  = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mTreeDebugDialog = null;
            }
        };

        final DialogInterface.OnClickListener okClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mTreeDebugDialog = null;

                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                        TalkBackPreferencesActivity.this);
                SharedPreferencesUtils.putBooleanPref(prefs, getResources(),
                        R.string.pref_tree_debug_key, true);

                // Manually tick the check box since we're not returning to
                // the preference change listener.
                final CheckBoxPreference prefTreeDebug =
                        (CheckBoxPreference) findPreferenceByResId(
                                R.string.pref_tree_debug_reflect_key);
                prefTreeDebug.setChecked(true);
            }
        };

        return new AlertDialog.Builder(this)
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
                    // If the user is trying to turn node tree debugging on, show
                    // a confirmation dialog and don't change anything.
                    if (Boolean.TRUE.equals(newValue)) {
                        (mTreeDebugDialog = createEnableTreeDebugDialog()).show();
                        return false;
                    }

                    // If the user is turning node tree debugging off, then any
                    // gestures currently set to print the node tree should be
                    // made unassigned.
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                            TalkBackPreferencesActivity.this);
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
                            preference.setSummary(entries[index].toString().replaceAll("%", "%%"));
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
}
