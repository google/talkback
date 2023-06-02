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

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceGroup;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.utils.A11yAlertDialogWrapper;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Panel holding a set of shortcut preferences. Recreated when preset value changes. */
public class TalkBackGestureShortcutPreferenceFragment extends TalkbackBaseFragment {
  private static final String TAG = "TalkBackGestureShortcutPreferenceFragment";

  /** Preferences managed by this activity. */
  private SharedPreferences prefs;

  public TalkBackGestureShortcutPreferenceFragment() {
    super(R.xml.gesture_preferences);
  }

  private final DialogInterface.OnClickListener resetGestureConfirmDialogPositive =
      (dialogInterface, i) -> {
        PreferencesActivityUtils.announceText(
            getString(R.string.gestures_announce_reset_gesture_settings), getContext());
        // Reset logic here
        resetGestureShortcut();
        dialogInterface.dismiss();
      };

  private final Preference.OnPreferenceClickListener resetGesturePreferenceClickListener =
      (preference) -> {
        // Show confirm dialog.
        A11yAlertDialogWrapper alertDialog =
            A11yAlertDialogWrapper.alertDialogBuilder(getContext())
                .setTitle(getString(R.string.Reset_gesture_settings_dialog_title))
                .setMessage(getString(R.string.message_reset_gesture_settings_confirm_dialog))
                .setPositiveButton(
                    R.string.reset_button_in_reset_gesture_settings_confirm_dialog,
                    resetGestureConfirmDialogPositive)
                .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.cancel())
                .create();
        alertDialog.show();
        if (!FeatureSupport.isWatch(getContext())) {
          // Does not need give default input focus on cancel button, because Wear won't connect to
          // external keyboard.
          A11yAlertDialogWrapper.focusCancelButton(alertDialog);
        }

        return true;
      };

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    if (preference instanceof GestureListPreference) {
      PreferenceDialogFragmentCompat dialogFragment =
          ((GestureListPreference) preference).createDialogFragment();
      dialogFragment.setTargetFragment(this, 0);
      dialogFragment.show(getParentFragmentManager(), preference.getKey());
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_category_manage_gestures);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    Context context = getContext();

    prefs = SharedPreferencesUtils.getSharedPreferences(context);

    // Launches Practice gestures page.
    @Nullable
    Preference practiceGesturesPreference =
        findPreference(getString(R.string.pref_practice_gestures_entry_point_key));
    if (practiceGesturesPreference != null) {
      practiceGesturesPreference.setIntent(TutorialInitiator.createPracticeGesturesIntent(context));
    }

    Preference resetGesturePreferenceScreen =
        (Preference) findPreference(getString(R.string.pref_reset_gesture_settings_key));
    resetGesturePreferenceScreen.setOnPreferenceClickListener(resetGesturePreferenceClickListener);

    // Disable 2-finger swipe gestures which are reserved as 2-finger pass-through.
    if (FeatureSupport.isMultiFingerGestureSupported()) {
      String[] twoFingerPassThroughGestureSet =
          context.getResources().getStringArray(R.array.pref_2finger_pass_through_shortcut_keys);
      String[] summaries =
          context.getResources().getStringArray(R.array.pref_2finger_pass_through_shortcut_summary);
      if (twoFingerPassThroughGestureSet.length == summaries.length) {
        for (int i = 0; i < twoFingerPassThroughGestureSet.length; i++) {
          GestureListPreference twoFingerGesturePreference =
              findPreference(twoFingerPassThroughGestureSet[i]);
          if (twoFingerGesturePreference != null) {
            twoFingerGesturePreference.setEnabled(false);
            twoFingerGesturePreference.setSummaryWhenDisabled(
                context.getResources().getString(R.string.shortcut_not_customizable, summaries[i]));
          }
        }
      }
    }
  }

  private void resetGestureShortcut() {
    // Reset preference to default
    final SharedPreferences.Editor prefEditor = prefs.edit();
    final String[] gesturePrefKeys = getResources().getStringArray(R.array.pref_shortcut_keys);

    PreferenceGroup root = getPreferenceScreen();

    for (String prefKey : gesturePrefKeys) {
      if (prefs.contains(prefKey)) {
        GestureListPreference preference = (GestureListPreference) root.findPreference(prefKey);
        preference.updateSummaryToDefaultValue();

        prefEditor.remove(prefKey);
      }
    }
    prefEditor.apply();
  }
}
