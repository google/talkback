/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.os.Bundle;
import android.view.WindowManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.HelpAndFeedbackUtils;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Activity for TalkBack tutorial and help contents. */
public class TalkBackHelpPreferencesActivity extends BasePreferencesActivity {

  private static final String HELP_URL =
      "https://support.google.com/accessibility/" + "android/answer/6283677";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WindowManager.LayoutParams localLayoutParams = getWindow().getAttributes();
    localLayoutParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    getWindow().setAttributes(localLayoutParams);
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new TutorialAndHelpFragment();
  }

  /** Fragment to display tutorial and help page. */
  public static class TutorialAndHelpFragment extends TalkbackBaseFragment {
    public TutorialAndHelpFragment() {
      super(R.xml.help_preferences);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      super.onCreatePreferences(savedInstanceState, rootKey);

      if (getActivity() == null) {
        return;
      }

      assignTutorialIntent();
      assignPracticeGesturesIntent();
      assignFeedbackIntentToPreference();
    }

    private void assignTutorialIntent() {
      final Preference prefTutorial =
          findPreference(getString(R.string.pref_tutorial_entry_point_key));

      if (prefTutorial == null) {
        return;
      }

      prefTutorial.setIntent(TutorialInitiator.createTutorialIntent(getActivity()));
    }

    private void assignPracticeGesturesIntent() {
      final Preference prefPracticeGestures =
          findPreference(getString(R.string.pref_practice_gestures_entry_point_key));

      if (prefPracticeGestures == null) {
        return;
      }

      prefPracticeGestures.setIntent(TutorialInitiator.createPracticeGesturesIntent(getActivity()));
    }

    private void assignFeedbackIntentToPreference() {
      final Preference pref = findPreference(getString(R.string.pref_help_and_feedback_key));

      if (pref == null) {
        return;
      }

      if (HelpAndFeedbackUtils.supportsHelpAndFeedback(getContext())) {
        pref.setTitle(R.string.title_pref_help_and_feedback);
        pref.setOnPreferenceClickListener(
            preference -> {
              HelpAndFeedbackUtils.launchHelpAndFeedback(getActivity());
              return true;
            });
      } else {
        pref.setTitle(R.string.title_pref_help);
        PreferenceSettingsUtils.assignWebIntentToPreference(this, pref, HELP_URL);
      }
    }
  }
}
