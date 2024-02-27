/*
 * Copyright 2018 Google Inc.
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.fragment.app.FragmentManager;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Fragment holding a set of developer preferences. */
public class DeveloperPrefFragment extends TalkbackBaseFragment {

  private static final String TAG = "DeveloperPrefFragment";
  private String versionInfo;

  /** Preferences managed by this activity. */
  private SharedPreferences prefs;

  private Context context;
  private FragmentManager fragmentManager;

  /** AlertDialog to ask if user really wants to enable node tree debugging. */
  private A11yAlertDialogWrapper treeDebugDialog;

  /** AlertDialog to ask if user really wants to enable performance statistics. */
  private A11yAlertDialogWrapper performanceStatsDialog;

  /** AlertDialog to ask if user really wants to disable explore by touch. */
  private A11yAlertDialogWrapper exploreByTouchDialog;

  /** AlertDialog to ask if user is willing to opt in the debug support. */
  private A11yAlertDialogWrapper logLevelOptInDialog;

  private int logOptInLevel = Log.ERROR;

  /** Flag whether content-observer is watching system touch-explore setting. */
  private boolean contentObserverRegistered = false;

  public DeveloperPrefFragment() {
    super(R.xml.developer_preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_category_developer_settings);
  }

  @Override
  public @Nullable CharSequence getSubTitle() {
    return shouldShowVersionInSubtitle() ? versionInfo : null;
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    context = getContext();
    fragmentManager = getActivity().getSupportFragmentManager();
    prefs = SharedPreferencesUtils.getSharedPreferences(context);

    initVersionInfo();

    // Remove preferences for features that are not supported by device.
    checkTelevision();
    initTouchExplorationPreference();

    final @Nullable Preference prefVersion =
        findPreference(getString(R.string.pref_developer_version_code_key));
    if (!shouldShowVersionInSubtitle() && (prefVersion != null) && (versionInfo != null)) {
      prefVersion.setSummary(versionInfo);
    } else {
      getPreferenceScreen().removePreference(prefVersion);
    }

    // Initialize preference dialogs.
    final @Nullable TwoStatePreference prefTreeDebug =
        findPreference(getString(R.string.pref_tree_debug_reflect_key));
    if (prefTreeDebug != null) {
      prefTreeDebug.setOnPreferenceChangeListener(treeDebugChangeListener);
      treeDebugDialog =
          A11yAlertDialogWrapper.alertDialogBuilder(context, fragmentManager)
              .setNegativeButton(android.R.string.cancel, null)
              .setOnCancelListener(null)
              .setTitle(R.string.dialog_title_enable_tree_debug)
              .setMessage(R.string.dialog_message_enable_tree_debug)
              .setPositiveButton(
                  android.R.string.ok,
                  (DialogInterface dialog, int which) -> {
                    SharedPreferencesUtils.storeBooleanAsync(
                        prefs, getString(R.string.pref_tree_debug_key), true);
                    prefTreeDebug.setChecked(true);
                  })
              .create();
    }

    final TwoStatePreference prefPerformanceStats =
        findPreference(getString(R.string.pref_performance_stats_reflect_key));
    if (prefPerformanceStats != null) {
      prefPerformanceStats.setOnPreferenceChangeListener(performanceStatsChangeListener);
      performanceStatsDialog =
          A11yAlertDialogWrapper.alertDialogBuilder(context, fragmentManager)
              .setNegativeButton(android.R.string.cancel, null)
              .setOnCancelListener(null)
              .setTitle(R.string.dialog_title_enable_performance_stats)
              .setMessage(R.string.dialog_message_enable_performance_stats)
              .setPositiveButton(
                  android.R.string.ok,
                  (DialogInterface dialog, int which) -> {
                    SharedPreferencesUtils.storeBooleanAsync(
                        prefs, getString(R.string.pref_performance_stats_key), true);
                    prefPerformanceStats.setChecked(true);
                  })
              .create();
    }

    final @Nullable ListPreference logLevelPref =
        findPreference(getString(R.string.pref_log_level_key));
    if (logLevelPref != null) {
      logLevelPref.setOnPreferenceChangeListener(logLevelChangeListener);
      logLevelOptInDialog =
          A11yAlertDialogWrapper.alertDialogBuilder(context, fragmentManager)
              .setNegativeButton(android.R.string.cancel, null)
              .setOnCancelListener(null)
              .setTitle(R.string.dialog_title_extend_log_level)
              .setMessage(R.string.dialog_message_extend_log_level)
              .setPositiveButton(
                  R.string.dialog_ok_buggon_extend_log_level,
                  (DialogInterface dialog, int which) -> {
                    SharedPreferencesUtils.putStringPref(
                        prefs,
                        getResources(),
                        R.string.pref_log_level_key,
                        Integer.toString(logOptInLevel));
                    logLevelPref.setValue(Integer.toString(logOptInLevel));
                    LogUtils.setLogLevel(logOptInLevel);
                  })
              .create();
    }

    final @Nullable TwoStatePreference diagnosisModePref =
        findPreference(getString(R.string.pref_diagnosis_mode_key));
    if (diagnosisModePref != null) {

      // Create confirmation-dialog.
      A11yAlertDialogWrapper diagnosisOptInDialog =
          A11yAlertDialogWrapper.alertDialogBuilder(context, fragmentManager)
              .setNegativeButton(android.R.string.cancel, null)
              .setOnCancelListener(null)
              .setTitle(R.string.dialog_title_diagnosis_mode)
              .setMessage(R.string.dialog_message_diagnosis_mode)
              .setPositiveButton(
                  android.R.string.ok,
                  (DialogInterface dialog, int which) -> {
                    SharedPreferencesUtils.storeBooleanAsync(
                        prefs, getString(R.string.pref_diagnosis_mode_key), true);
                    diagnosisModePref.setChecked(true);
                    updateDisplayForDiagnosisModeOn();
                  })
              .create();

      // When diagnosis-mode preference is turned on, show confirmation-dialog.
      diagnosisModePref.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
              diagnosisOptInDialog.show();
              return false;
            } else {
              updateDisplayForDiagnosisModeOff();
              return true;
            }
          });
    }

    final @Nullable TwoStatePreference serviceGestureDetectionPref =
        findPreference(getString(R.string.pref_talkback_gesture_detection_key));
    if (serviceGestureDetectionPref != null) {
      if (FeatureSupport.supportGestureDetection()) {
        serviceGestureDetectionPref.setOnPreferenceChangeListener(
            (preference, newValue) -> {
              if (Boolean.TRUE.equals(newValue)) {
                Toast.makeText(
                        getContext(),
                        R.string.toast_pref_talkback_gesture_detection,
                        Toast.LENGTH_LONG)
                    .show();
              }
              return true;
            });
      } else {
        getPreferenceScreen().removePreference(serviceGestureDetectionPref);
      }
    }

    final @Nullable TwoStatePreference multipleGestureSetPref =
        findPreference(getString(R.string.pref_multiple_gesture_set_key));
    if (multipleGestureSetPref != null) {
      if (FeatureSupport.supportMultipleGestureSet()
          && FeatureFlagReader.useMultipleGestureSet(context)) {
        multipleGestureSetPref.setOnPreferenceChangeListener(
            (preference, newValue) -> {
              final SharedPreferences.Editor prefEditor = prefs.edit();
              // Reset gesture set to set 0
              prefEditor
                  .remove(context.getResources().getString(R.string.pref_gesture_set_key))
                  .apply();
              return true;
            });
      } else {
        getPreferenceScreen().removePreference(multipleGestureSetPref);
      }
    }

    updateDisplayForDiagnosisMode();
  }

  private static boolean shouldShowVersionInSubtitle() {
    // Watch does not have action bar.
    // TV does have an action bar but it doesn't support a subtitle.
    return !FeatureSupport.supportSettingsTheme()
        && !FormFactorUtils.getInstance().isAndroidWear()
        && !FormFactorUtils.getInstance().isAndroidTv();
  }

  private void updateDisplayForDiagnosisMode() {
    if (PreferencesActivityUtils.isDiagnosisModeOn(prefs, context.getResources())) {
      updateDisplayForDiagnosisModeOn();
    } else {
      updateDisplayForDiagnosisModeOff();
    }
  }

  // Turn on diagnosis-mode sub-preferences.
  private void updateDisplayForDiagnosisModeOn() {
    setEnabled(context, R.string.pref_tts_overlay_key, /* enable= */ false);
    setEnabled(context, R.string.pref_echo_recognized_text_speech_key, /* enable= */ false);
    setEnabled(context, R.string.pref_tree_debug_reflect_key, /* enable= */ false);
    setEnabled(context, R.string.pref_log_overlay_key, /* enable= */ false);
    setEnabled(context, R.string.pref_log_level_key, /* enable= */ false);
  }

  // Turn off diagnosis-mode sub-preferences.
  private void updateDisplayForDiagnosisModeOff() {
    setEnabled(context, R.string.pref_tts_overlay_key, /* enable= */ true);
    setEnabled(context, R.string.pref_echo_recognized_text_speech_key, /* enable= */ true);
    setEnabled(context, R.string.pref_tree_debug_reflect_key, /* enable= */ true);
    setEnabled(context, R.string.pref_log_overlay_key, /* enable= */ true);
    setEnabled(context, R.string.pref_log_level_key, /* enable= */ true);
  }

  private void initVersionInfo() {
    versionInfo = null;

    // Shows TalkBack's abbreviated version number in the action bar.
    try {
      PackageInfo packageInfo =
          context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      if (packageInfo != null) {
        long versionCode =
            FeatureSupport.supportLongVersionCode()
                ? packageInfo.getLongVersionCode()
                : packageInfo.versionCode;
        versionInfo =
            getString(
                R.string.talkback_preferences_subtitle,
                packageInfo.versionName + " (" + versionCode + ")");
      }
    } catch (NameNotFoundException e) {
      LogUtils.e(TAG, "Can't find PackageInfo by the package name.");
    }
  }

  /** Checks if the device is Android TV and changes preferences where necessary. */
  private void checkTelevision() {
    if (FormFactorUtils.getInstance().isAndroidTv()) {
      // Add TV-specific explanation for node tree debugging.
      final Preference treeDebugPreference =
          findPreference(getString(R.string.pref_tree_debug_reflect_key));
      treeDebugPreference.setSummary(getString(R.string.summary_pref_tree_debug_tv));
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    updateDumpA11yEventPreferenceSummary();

    // Monitor the touch-explore system-setting.
    @Nullable TalkBackService talkBackService = TalkBackService.getInstance();
    if (talkBackService == null || !talkBackService.supportsTouchScreen()) {
      return;
    }

    Uri uri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
    context.getContentResolver().registerContentObserver(uri, false, touchExploreObserver);
    contentObserverRegistered = true;
  }

  @Override
  public void onPause() {
    // Stop monitoring the touch-explore system-setting.
    if (contentObserverRegistered) {
      context.getContentResolver().unregisterContentObserver(touchExploreObserver);
    }

    super.onPause();
  }

  //////////////////////////////////////////////////////////////////////////////////////////////
  // Touch-explore preference methods.

  private final ContentObserver touchExploreObserver =
      new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
          if (selfChange) {
            return;
          }

          // The actual state of touch exploration has changed.
          updateTouchExplorationDisplay();
        }
      };

  /** Assigns the appropriate intent to the touch exploration preference. */
  private void initTouchExplorationPreference() {
    final TwoStatePreference prefTouchExploration =
        (TwoStatePreference) findPreference(getString(R.string.pref_explore_by_touch_reflect_key));
    if (prefTouchExploration == null) {
      return;
    }

    // Ensure that changes to the reflected preference's checked state never
    // trigger content observers.
    prefTouchExploration.setPersistent(false);

    // Synchronize the reflected state.
    updateTouchExplorationDisplay();

    // Set up listeners that will keep the state synchronized.
    prefTouchExploration.setOnPreferenceChangeListener(touchExplorationChangeListener);

    // Initialize preference dialog
    exploreByTouchDialog =
        A11yAlertDialogWrapper.alertDialogBuilder(context, fragmentManager)
            .setTitle(R.string.dialog_title_disable_exploration)
            .setMessage(R.string.dialog_message_disable_exploration)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnCancelListener(null)
            .setPositiveButton(
                android.R.string.ok,
                (DialogInterface dialog, int which) -> {
                  if (setTouchExplorationRequested(false)) {
                    prefTouchExploration.setChecked(false);
                  }
                })
            .create();
  }

  /**
   * Updates the preferences state to match the actual state of touch exploration. This is called
   * once when the preferences activity launches and again whenever the actual state of touch
   * exploration changes.
   */
  private void updateTouchExplorationDisplay() {
    TwoStatePreference prefTouchExploration =
        (TwoStatePreference) findPreference(getString(R.string.pref_explore_by_touch_reflect_key));
    if (prefTouchExploration == null) {
      return;
    }

    ContentResolver resolver = context.getContentResolver();
    Resources res = getResources();
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);

    boolean requestedState =
        SharedPreferencesUtils.getBooleanPref(
            prefs, res, R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
    boolean reflectedState = prefTouchExploration.isChecked();
    boolean actualState =
        TalkBackService.isServiceActive() ? isTouchExplorationEnabled(resolver) : requestedState;

    // If touch exploration is actually off and we requested it on, the user
    // must have declined the "Enable touch exploration" dialog. Update the
    // requested value to reflect this.
    if (requestedState != actualState) {
      LogUtils.d(TAG, "Set touch exploration preference to reflect actual state %b", actualState);
      SharedPreferencesUtils.putBooleanPref(
          prefs, res, R.string.pref_explore_by_touch_key, actualState);
    }

    // Ensure that the check box preference reflects the requested state,
    // which was just synchronized to match the actual state.
    if (reflectedState != actualState) {
      prefTouchExploration.setChecked(actualState);
    }
  }

  private final OnPreferenceChangeListener touchExplorationChangeListener =
      new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          final boolean requestedState = Boolean.TRUE.equals(newValue);

          // If the user is trying to turn touch exploration off, show
          // a confirmation dialog and don't change anything.
          if (!requestedState) {
            exploreByTouchDialog.show();
            return false;
          }

          return setTouchExplorationRequested(true); // requestedState
        }
      };

  /**
   * Updates the preference that controls whether TalkBack will attempt to request Explore by Touch.
   *
   * @param requestedState The state requested by the user.
   * @return Whether to update the reflected state.
   */
  private boolean setTouchExplorationRequested(boolean requestedState) {

    final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);

    // Update the "requested" state. This will trigger a listener in
    // TalkBack that changes the "actual" state.
    SharedPreferencesUtils.putBooleanPref(
        prefs, getResources(), R.string.pref_explore_by_touch_key, requestedState);

    // If TalkBack is inactive, we should immediately reflect the change in
    // "requested" state.
    if (!TalkBackService.isServiceActive()) {
      return true;
    }

    TalkBackService talkBackService = TalkBackService.getInstance();
    if (requestedState && talkBackService != null && talkBackService.shouldShowTutorial()) {
      talkBackService.showTutorial();
    }

    // If accessibility is on, we should wait for the "actual" state to
    // change, then reflect that change. If the user declines the system's
    // touch exploration dialog, the "actual" state will not change and
    // nothing needs to happen.
    LogUtils.d(TAG, "TalkBack active, waiting for EBT request to take effect");
    return false;
  }

  private static boolean isTouchExplorationEnabled(ContentResolver resolver) {
    return Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  private void updateDumpA11yEventPreferenceSummary() {
    final Preference prefDumpA11yEvent =
        findPreference(getString(R.string.pref_dump_a11y_event_key));

    if (prefDumpA11yEvent == null || prefs == null) {
      return;
    }

    int count = 0;
    int[] eventTypes = AccessibilityEventUtils.getAllEventTypes();

    int dumpEventMask = prefs.getInt(getString(R.string.pref_dump_event_mask_key), 0);

    for (int eventType : eventTypes) {
      if ((eventType & dumpEventMask) != 0) {
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

  // TODO: Separate function for duplicate OnPreferenceChangeListener code.
  private final OnPreferenceChangeListener treeDebugChangeListener =
      new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {

          // If the user is trying to turn node tree debugging on, show
          // a confirmation dialog and don't change anything.
          if (Boolean.TRUE.equals(newValue)) {
            treeDebugDialog.show();
            return false;
          }

          // If the user is turning node tree debugging off, then any
          // gestures currently set to print the node tree should be
          // made unassigned.
          disableAndRemoveGesture(
              R.string.pref_tree_debug_key, R.string.shortcut_value_print_node_tree);

          return true;
        }
      };

  // TODO: Separate function for duplicate OnPreferenceChangeListener code.
  private final OnPreferenceChangeListener performanceStatsChangeListener =
      new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          // If the user is enabling performance statistics... show confirmation dialog.
          if (Boolean.TRUE.equals(newValue)) {
            performanceStatsDialog.show();
            return false;
          }

          // If the user is disabling performance statistics... disable & unassign gesture.
          disableAndRemoveGesture(
              R.string.pref_performance_stats_key, R.string.shortcut_value_print_performance_stats);

          return true;
        }
      };

  private final OnPreferenceChangeListener logLevelChangeListener =
      new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          // If the user is trying to extend log level, shows a confirmation dialog and don't change
          // anything.
          logOptInLevel = Integer.parseInt((String) newValue);
          if (logOptInLevel < Log.ERROR) {
            logLevelOptInDialog.show();
            return false;
          }

          LogUtils.setLogLevel(logOptInLevel);
          return true;
        }
      };

  protected void disableAndRemoveGesture(int prefKeyRes, int shortcutRes) {
    // Set preference to false
    final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    final SharedPreferences.Editor prefEditor = prefs.edit();
    prefEditor.putBoolean(getString(prefKeyRes), false);
    // Gestures may need to be reassigned if disabling developer options, like node tree
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
}
