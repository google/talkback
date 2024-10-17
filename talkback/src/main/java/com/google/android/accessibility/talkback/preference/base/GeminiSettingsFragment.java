/*
 * Copyright 2024 Google Inc.
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.preference.Preference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** A {@link TalkbackBaseFragment} to hold the Gemini feature preferences. */
public class GeminiSettingsFragment extends TalkbackBaseFragment {

  private static final String TAG = "GeminiSettingsFragment";
  private Context context;
  private SharedPreferences prefs;

  public GeminiSettingsFragment() {
    super(R.xml.gemini_settings);
  }

  @Override
  protected CharSequence getTitle() {
    return getText(R.string.title_pref_gemini_settings);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    context = getContext();
    if (context == null) {
      return;
    }
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    setupEnablePreference();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  private void setupEnablePreference() {
    Preference optInPreference = findPreferenceByResId(R.string.pref_gemini_enabled_key);
    if (optInPreference == null) {
      return;
    }

    if (getBooleanPref(R.string.pref_gemini_enabled_key, R.bool.pref_gemini_opt_in_default)) {
      optInPreference.setSummary(R.string.title_pref_enable_gemini_support);
    } else {
      optInPreference.setSummary(R.string.title_pref_disable_gemini_support);
    }

    optInPreference.setOnPreferenceClickListener(
        preference -> {
          new GeminiOptInDialog(
              context,
              R.string.gemini_enable_dialog_title,
              R.string.gemini_disable_dialog_message,
              R.string.pref_gemini_enabled_key,
              R.bool.pref_gemini_opt_in_default) {
            @Override
            public void handleDialogClick(int buttonClicked) {
              super.handleDialogClick(buttonClicked);
              if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {}

              if (getBooleanPref(
                  R.string.pref_gemini_enabled_key, R.bool.pref_gemini_opt_in_default)) {
                preference.setSummary(R.string.title_pref_enable_gemini_support);
              } else {
                preference.setSummary(R.string.title_pref_disable_gemini_support);
              }
            }
          }.showDialog();
          return true;
        });
  }

  private boolean getBooleanPref(int key, int defaultValue) {
    return SharedPreferencesUtils.getBooleanPref(prefs, context.getResources(), key, defaultValue);
  }

  /** A dialog to opt in/out Gemini feature. */
  public static class GeminiOptInDialog extends BaseDialog {
    private final SharedPreferences prefs;
    private RadioGroup switches;
    private final int message;
    private final int key;
    private final int defaultValue;

    public GeminiOptInDialog(Context context, int title, int message, int key, int defaultValue) {
      super(context, title, /* pipeline= */ null);
      prefs = SharedPreferencesUtils.getSharedPreferences(context);
      this.message = message;
      this.key = key;
      this.defaultValue = defaultValue;
      setPositiveButtonStringRes(R.string.switch_auto_image_caption_dialog_positive_button_text);
    }

    @Override
    public String getMessageString() {
      return null;
    }

    @SuppressLint("InflateParams")
    @Override
    public View getCustomizedView() {
      LayoutInflater inflater = LayoutInflater.from(context);
      final ScrollView root =
          (ScrollView) inflater.inflate(R.layout.gemini_opt_in_switch_dialog, /* root= */ null);

      TextView textView = root.findViewById(R.id.gemini_opt_in_switch_dialog_message);
      textView.setText(message);

      switches = root.findViewById(R.id.gemini_opt_in_switch_dialog_radiogroup);
      switches.setOnCheckedChangeListener(
          (group, checkedId) -> {
            TextView disabledSubText =
                group.findViewById(R.id.gemini_opt_in_switch_dialog_radiobutton_disabled_subtext);
            if (checkedId == R.id.gemini_opt_in_switch_dialog_radiobutton_disabled) {
              disabledSubText.setVisibility(View.VISIBLE);
            } else {
              disabledSubText.setVisibility(View.GONE);
            }
          });

      boolean isFeatureEnabled =
          SharedPreferencesUtils.getBooleanPref(prefs, context.getResources(), key, defaultValue);
      ((RadioButton)
              switches.findViewById(
                  isFeatureEnabled
                      ? R.id.gemini_opt_in_switch_dialog_radiobutton_enabled
                      : R.id.gemini_opt_in_switch_dialog_radiobutton_disabled))
          .setChecked(true);
      return root;
    }

    @Override
    public void handleDialogClick(int buttonClicked) {
      if (switches == null) {
        return;
      }

      if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
        SharedPreferencesUtils.putBooleanPref(
            prefs,
            context.getResources(),
            key,
            switches.getCheckedRadioButtonId()
                == R.id.gemini_opt_in_switch_dialog_radiobutton_enabled);
      }
    }

    @Override
    public void handleDialogDismiss() {}
  }
}
