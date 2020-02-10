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

package com.google.android.accessibility.talkback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Activity used to set TalkBack's developer preferences. */
public class TalkBackDeveloperPreferencesActivity extends BasePreferencesActivity {

  private static final String TAG = "DeveloperPreferencesActivity";

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new DeveloperPrefFragment();
  }

  /** Panel holding a set of developer preferences. */
  public static class DeveloperPrefFragment extends PreferenceFragmentCompat {

    /** Preferences managed by this activity. */
    private SharedPreferences prefs;

    private Context context;

    /** AlertDialog to ask if user really wants to enable node tree debugging. */
    private AlertDialog treeDebugDialog;

    /** AlertDialog to ask if user really wants to enable performance statistics. */
    private AlertDialog performanceStatsDialog;

    /** AlertDialog to ask if user really wants to disable explore by touch. */
    private AlertDialog exploreByTouchDialog;

    /** Flag whether content-observer is watching system touch-explore setting. */
    private boolean contentObserverRegistered = false;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      context = getActivity().getApplicationContext();
      prefs = SharedPreferencesUtils.getSharedPreferences(context);

      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.developer_preferences);

      // Remove preferences for features that are not supported by device.
      checkTelevision();
      checkReducedWindowDelaySupport();
      initTouchExplorationPreference();

      // Initialize preference dialogs.
      final TwoStatePreference prefTreeDebug =
          findPreference(getString(R.string.pref_tree_debug_reflect_key));
      if (prefTreeDebug != null) {
        prefTreeDebug.setOnPreferenceChangeListener(treeDebugChangeListener);
        treeDebugDialog =
            new AlertDialog.Builder(this.getActivity())
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
            new AlertDialog.Builder(this.getActivity())
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
    }

    /**
     * Checks if the device is Android TV and removes preferences that shouldn't be set when on
     * Android TV.
     */
    private void checkTelevision() {
      if (FeatureSupport.isTv(getActivity())) {
        final Preference treeDebugPreference =
            findPreference(getString(R.string.pref_tree_debug_reflect_key));
        treeDebugPreference.setSummary(getString(R.string.summary_pref_tree_debug_tv));
      }
    }

    /** Ensure window-delay setting does not appear on devices without animation toggle. */
    private void checkReducedWindowDelaySupport() {
      if (!FeatureSupport.disableAnimation()) {
        final PreferenceGroup category =
            (PreferenceGroup) findPreference(getString(R.string.pref_category_developer_key));
        if (category != null) {
          PreferenceSettingsUtils.hidePreference(
              context, category, R.string.pref_reduce_window_delay_key);
        }
      }
    }

    @Override
    public void onResume() {
      super.onResume();
      updateDumpA11yEventPreferenceSummary();

      // Monitor the touch-explore system-setting.
      TalkBackService talkBackService = TalkBackService.getInstance();
      if (talkBackService == null || !talkBackService.supportsTouchScreen()) {
        return;
      }
      Activity activity = getActivity();
      if (activity == null) {
        return;
      }
      Uri uri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
      activity.getContentResolver().registerContentObserver(uri, false, touchExploreObserver);
      contentObserverRegistered = true;
    }

    @Override
    public void onPause() {
      // Stop monitoring the touch-explore system-setting.
      Activity activity = getActivity();
      if (activity != null && contentObserverRegistered) {
        activity.getContentResolver().unregisterContentObserver(touchExploreObserver);
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
          (TwoStatePreference)
              findPreference(getString(R.string.pref_explore_by_touch_reflect_key));
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
          new AlertDialog.Builder(this.getActivity())
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
          (TwoStatePreference)
              findPreference(getString(R.string.pref_explore_by_touch_reflect_key));
      if (prefTouchExploration == null) {
        return;
      }

      Activity activity = getActivity();
      if (activity == null) {
        return;
      }

      ContentResolver resolver = activity.getContentResolver();
      Resources res = getResources();
      SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(activity);

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
            Activity activity = getActivity();
            if (activity == null) {
              return false;
            }

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
                activity, R.string.pref_tree_debug_key, R.string.shortcut_value_print_node_tree);

            return true;
          }
        };

    // TODO: Separate function for duplicate OnPreferenceChangeListener code.
    private final OnPreferenceChangeListener performanceStatsChangeListener =
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            Activity activity = getActivity();
            if (activity == null) {
              return false;
            }

            // If the user is enabling performance statistics... show confirmation dialog.
            if (Boolean.TRUE.equals(newValue)) {
              performanceStatsDialog.show();
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
}
