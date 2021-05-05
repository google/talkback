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

package com.google.android.accessibility.talkback.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.talkback.utils.AlertDialogUtils;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** Activity used to set TalkBack's gesture preferences. */
public class TalkBackShortcutPreferencesActivity extends BasePreferencesActivity {

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new ShortcutPrefFragment();
  }

  private void restartFragment() {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(android.R.id.content, new ShortcutPrefFragment())
        .commit();
  }

  /** Panel holding a set of shortcut preferences. Recreated when preset value changes. */
  public static class ShortcutPrefFragment extends TalkbackBaseFragment {
    public ShortcutPrefFragment() {
      super(R.xml.gesture_preferences);
    }

    /** Preferences managed by this activity. */
    private SharedPreferences prefs;

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
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);
      Context context = getActivity().getApplicationContext();

      prefs = SharedPreferencesUtils.getSharedPreferences(context);

      // Launches Practice gestures page.
      Preference practiceGesturesPreference =
          findPreference(getString(R.string.pref_practice_gestures_entry_point_key));
      practiceGesturesPreference.setIntent(TutorialInitiator.createPracticeGesturesIntent(context));

      Preference resetGesturePreferenceScreen =
          (Preference) findPreference(getString(R.string.pref_reset_gesture_settings_key));
      resetGesturePreferenceScreen.setOnPreferenceClickListener(
          resetGesturePreferenceClickListener);

      // Disable 2-finger swipe gestures which are reserved as 2-finger pass-through.
      if (FeatureSupport.isMultiFingerGestureSupported()) {
        String[] twoFingerPassThroughGestureSet =
            context.getResources().getStringArray(R.array.pref_2finger_pass_through_shortcut_keys);
        String[] summaries =
            context
                .getResources()
                .getStringArray(R.array.pref_2finger_pass_through_shortcut_summary);
        if (twoFingerPassThroughGestureSet.length == summaries.length) {
          for (int i = 0; i < twoFingerPassThroughGestureSet.length; i++) {
            GestureListPreference twoFingerGesturePreference =
                findPreference(twoFingerPassThroughGestureSet[i]);
            if (twoFingerGesturePreference != null) {
              twoFingerGesturePreference.setEnabled(false);
              twoFingerGesturePreference.setSummaryWhenDisabled(
                  context
                      .getResources()
                      .getString(R.string.shortcut_not_customizable, summaries[i]));
            }
          }
        }
      }
    }

    private void resetGestureShortcut() {
      // Reset preference to default
      final SharedPreferences.Editor prefEditor = prefs.edit();
      final String[] gesturePrefKeys = getResources().getStringArray(R.array.pref_shortcut_keys);

      for (String prefKey : gesturePrefKeys) {
        if (prefs.contains(prefKey)) {
          prefEditor.remove(prefKey);
        }
      }
      prefEditor.apply();
      ((TalkBackShortcutPreferencesActivity) getActivity()).restartFragment();
    }

    private final DialogInterface.OnClickListener resetGestureConfirmDialogPositive =
        (dialogInterface, i) -> {
          // Reset logic here
          resetGestureShortcut();
          dialogInterface.dismiss();
          PreferencesActivityUtils.announceText(
              getString(R.string.gestures_announce_reset_gesture_settings), getActivity());
        };

    private static void focusCancelButton(AlertDialog alertDialog) {
      Button cancelButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
      cancelButton.setFocusableInTouchMode(true);
      cancelButton.requestFocus();
    }

    private final Preference.OnPreferenceClickListener resetGesturePreferenceClickListener =
        new Preference.OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            // Show confirm dialog.
            AlertDialog alertDialog =
                AlertDialogUtils.createBuilder(getActivity())
                    .setTitle(getString(R.string.Reset_gesture_settings_dialog_title))
                    .setMessage(getString(R.string.message_reset_gesture_settings_confirm_dialog))
                    .setPositiveButton(
                        R.string.reset_button_in_reset_gesture_settings_confirm_dialog,
                        resetGestureConfirmDialogPositive)
                    .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.cancel())
                    .create();
            alertDialog.show();

            focusCancelButton(alertDialog);

            return true;
          }
        };
  }
}
