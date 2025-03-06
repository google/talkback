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

package com.google.android.accessibility.brailleime.settings;

import static android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.VisibleForTesting;
import androidx.core.text.HtmlCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.BraillePreferenceUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.TouchDots;
import com.google.android.accessibility.braille.common.settings.BrailleLanguagesActivity;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme.ServiceStatus;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.KeyboardUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;
import com.google.android.accessibility.utils.preference.PreferencesActivity;
import java.util.Arrays;

/** Activity used to set BrailleIme's user options. */
public class BrailleImePreferencesActivity extends PreferencesActivity {

  private static final String TAG = "BrailleImePreferencesActivity";
  private static final int REQUEST_CODE_IME_SETTINGS = 100;
  private static final String KEYBOARD_ICON_TOKEN = "KEYBOARD_ICON";
  private PreferenceFragmentCompat preferenceFragmentCompat;

  private static TalkBackForBrailleIme talkBackForBrailleIme;

  /** TalkBack invokes this to provide us with the TalkBackForBrailleIme instance. */
  public static void initialize(TalkBackForBrailleIme talkBackForBrailleIme) {
    BrailleImePreferencesActivity.talkBackForBrailleIme = talkBackForBrailleIme;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      Intent startTalkBackSettings = new Intent();
      startTalkBackSettings.setComponent(Constants.SETTINGS_ACTIVITY);
      startTalkBackSettings.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
      startActivity(startTalkBackSettings);
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    preferenceFragmentCompat = new BrailleImePrefFragment();
    return preferenceFragmentCompat;
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getContentResolver()
        .registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
            /* notifyForDescendants= */ false,
            imeSettingsContentObserver);
  }

  @Override
  protected void onDestroy() {
    getContentResolver().unregisterContentObserver(imeSettingsContentObserver);
    super.onDestroy();
  }

  /** Panel holding a set of developer preferences. */
  public static class BrailleImePrefFragment extends PreferenceFragmentCompat {
    private Preference brailleGradePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      getPreferenceManager()
          .setSharedPreferencesName(BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.brailleime_preferences);
      brailleGradePreference = findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_braille_contracted_mode));

      getPreferenceManager()
          .getSharedPreferences()
          .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    public void onResume() {
      super.onResume();
      configurePrefs();
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      getPreferenceManager()
          .getSharedPreferences()
          .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    private void configurePrefs() {
      {
        // Turn on keyboard preference.
        Preference turnOnKeyboardPref =
            findPreference(getString(R.string.pref_brailleime_turn_on_braille_keyboard));
        turnOnKeyboardPref.setOnPreferenceClickListener(
            preference -> {
              ((BrailleImePreferencesActivity) getActivity()).showTurnOnKeyboardDialog();
              return true;
            });
        turnOnKeyboardPref.setTitle(
            ((BrailleImePreferencesActivity) getActivity()).isImeEnabled()
                ? getString(R.string.how_to_use_braille_keyboard)
                : getString(R.string.set_up_braille_keyboard));
      }

      {
        // Typing codes preference.
        Preference typingCodesPref =
            findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_translator_codes_preferred));
        typingCodesPref.setIntent(new Intent(getContext(), BrailleLanguagesActivity.class));
        BraillePreferenceUtils.setupPreferredCodePreference(
            getContext(),
            typingCodesPref,
            (preference, newValue) -> {
              configurePrefs();
              return false;
            });
      }

      {
        // Preferred input codes preference.
        ListPreference preferredInputCodePref =
            findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_translator_code));
        if (preferredInputCodePref != null) {
          BraillePreferenceUtils.setupLanguageListPreference(
              getContext(),
              preferredInputCodePref,
              BrailleUserPreferences::readCurrentActiveInputCodeAndCorrect,
              BrailleUserPreferences::writeCurrentActiveInputCode,
              (preference, newValue) -> {
                if (BrailleUserPreferences.readShowSwitchBrailleKeyboardInputCodeGestureTip(
                    getContext())) {
                  showSwitchInputCodeGestureTipDialog();
                }
                return false;
              });
        }
      }

      {
        // See all actions preference.
        Preference seeAllActionPref =
            findPreference(getString(R.string.pref_brailleime_review_all_gestures));
        seeAllActionPref.setIntent(new Intent(getContext(), BrailleImeGestureActivity.class));
      }

      {
        // Accumulate mode preference.
        SwitchPreference accumulateModePref =
            findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_accumulate_mode));
        accumulateModePref.setChecked(BrailleUserPreferences.readAccumulateMode(getContext()));
        accumulateModePref.setOnPreferenceClickListener(
            preference -> {
              BrailleUserPreferences.writeAccumulateMode(
                  getContext(), ((SwitchPreference) preference).isChecked());
              return true;
            });
        accumulateModePref.setChecked(BrailleUserPreferences.readAccumulateMode(getContext()));
      }

      {
        // Reverse dots mode preference.
        SwitchPreference reverseDotsModePref =
            findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_reverse_dots_mode));
        reverseDotsModePref.setChecked(BrailleUserPreferences.readReverseDotsMode(getContext()));
        reverseDotsModePref.setOnPreferenceClickListener(
            preference -> {
              BrailleUserPreferences.writeReverseDotsMode(
                  getContext(), ((SwitchPreference) preference).isChecked());
              return true;
            });
        reverseDotsModePref.setChecked(BrailleUserPreferences.readReverseDotsMode(getContext()));
      }

      {
        // Layout settings preference.
        ListPreference layoutModePref =
            findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_layout_mode));
        if (layoutModePref != null) {
            layoutModePref.setEntryValues(
                Arrays.stream(TouchDots.values()).map(Enum::name).toArray(CharSequence[]::new));
            layoutModePref.setEntries(
                Arrays.stream(TouchDots.values())
                    .map(value -> value.getLayoutDescription(getResources()))
                    .toArray(CharSequence[]::new));
            layoutModePref.setValue(BrailleUserPreferences.readLayoutMode(getContext()).name());
            layoutModePref.setSummaryProvider(
                preference ->
                    BrailleUserPreferences.readLayoutMode(getContext())
                        .getLayoutName(getResources()));
            layoutModePref.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                  BrailleUserPreferences.writeLayoutMode(
                      getContext(), TouchDots.valueOf(newValue.toString()));
                  return true;
                });
        }
      }

      // Preferred braille grade
      updateBrailleGradeSummary();
    }

    private void showSwitchInputCodeGestureTipDialog() {
      BraillePreferenceUtils.createTipAlertDialog(
              getContext(),
              getString(R.string.switch_input_code_gesture_tip_dialog_title),
              getString(R.string.switch_input_code_gesture_tip_dialog_message),
              BrailleUserPreferences::writeShowSwitchBrailleKeyboardInputCodeGestureTip)
          .show();
    }

    private void updateBrailleGradeSummary() {
      brailleGradePreference.setSummary(
          getString(
              BrailleUserPreferences.readContractedMode(getContext())
                  ? R.string.bd_preference_braille_contracted
                  : R.string.bd_preference_braille_uncontracted));
    }

    private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
        new OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_braille_contracted_mode))) {
              updateBrailleGradeSummary();
            }
          }
        };
  }

  private void showTurnOnKeyboardDialog() {
    AlertDialog.Builder builder = MaterialComponentUtils.alertDialogBuilder(this);
    builder.setTitle(
        isImeEnabled()
            ? getString(R.string.how_to_use_braille_keyboard)
            : getString(R.string.set_up_braille_keyboard));
    if (isImeEnabled()) {
      builder
          .setMessage(getDialogMessageForImeEnabled())
          .setPositiveButton(android.R.string.ok, null);
    } else {
      builder
          .setMessage(getDialogMessageForImeDisabled())
          .setPositiveButton(
              getString(
                  supportEnableIme()
                      ? R.string.use_brailleime_pref_button_case_ime_disabled_turn_on
                      : R.string.use_brailleime_pref_button_case_ime_disabled_settings),
              (dialogInterface, i) -> {
                if (supportEnableIme() && talkBackForBrailleIme.setInputMethodEnabled()) {
                  ((BrailleImePrefFragment) getSupportFragmentManager().findFragmentByTag(TAG))
                      .configurePrefs();
                  return;
                }
                if (!BrailleCommonUtils.isInputMethodEnabled(this, Constants.BRAILLE_KEYBOARD)) {
                  Utils.setComponentEnabled(this, Constants.BRAILLE_KEYBOARD, true);
                }
                startActivityForResult(
                    new Intent(ACTION_INPUT_METHOD_SETTINGS), REQUEST_CODE_IME_SETTINGS);
              })
          .setNegativeButton(
              android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss());
    }
    AlertDialog dialog = builder.create();
    if (!isImeEnabled() && supportEnableIme()) {
      dialog.setOnShowListener(
          dialogInterface ->
              dialog
                  .getButton(AlertDialog.BUTTON_POSITIVE)
                  .setContentDescription(
                      getString(
                          R.string
                              .use_brailleime_pref_button_case_ime_disabled_turn_on_announcement)));
    }
    dialog.show();

    // Set movement method to url link.
    View message = dialog.findViewById(android.R.id.message);
    if (message instanceof TextView) {
      ((TextView) message).setMovementMethod(LinkMovementMethod.getInstance());
    }
  }

  private boolean supportEnableIme() {
    return FeatureSupport.supportEnableDisableIme()
        && talkBackForBrailleIme != null
        && talkBackForBrailleIme.getServiceStatus() != ServiceStatus.OFF;
  }

  private boolean isImeEnabled() {
    return KeyboardUtils.isImeEnabled(this, new ComponentName(this, BrailleIme.class.getName()));
  }

  @VisibleForTesting
  Spanned getDialogMessageForImeEnabled() {
    String gboardName = getString(R.string.gboard_name);
    String message =
        this.getString(
            R.string.use_brailleime_pref_dialog_case_ime_enabled,
            KEYBOARD_ICON_TOKEN,
            getString(R.string.braille_ime_service_name),
            gboardName);

    SpannableString spannableMessageString =
        SpannableString.valueOf(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY));
    spannableMessageString = replaceKeyboardIconTokenToIconDrawable(spannableMessageString);
    if (SettingsUtils.allowLinksOutOfSettings(this)) {
      insertHyperLinkToSubString(spannableMessageString, gboardName);
    }

    return spannableMessageString;
  }

  @VisibleForTesting
  Spanned getDialogMessageForImeDisabled() {
    String gboardName = getString(R.string.gboard_name);
    String message =
        this.getString(
            /* resId= */ supportEnableIme()
                ? R.string.use_brailleime_pref_dialog_case_ime_disabled_turn_on
                : R.string.use_brailleime_pref_dialog_case_ime_disabled_settings,
            getString(R.string.braille_ime_service_name),
            KEYBOARD_ICON_TOKEN,
            gboardName);

    SpannableString spannableMessageString =
        SpannableString.valueOf(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY));
    spannableMessageString = replaceKeyboardIconTokenToIconDrawable(spannableMessageString);
    if (SettingsUtils.allowLinksOutOfSettings(this)) {
      insertHyperLinkToSubString(spannableMessageString, gboardName);
    }
    return spannableMessageString;
  }

  private SpannableString replaceKeyboardIconTokenToIconDrawable(SpannableString spannableString) {
    return Utils.formatSubstringAsDrawable(
        spannableString,
        KEYBOARD_ICON_TOKEN,
        getDrawable(R.drawable.quantum_ic_keyboard_grey600_24));
  }

  private static void insertHyperLinkToSubString(
      SpannableString spannableString, String subString) {
    Utils.formatSubstringAsUrl(
        spannableString,
        subString,
        "http://play.google.com/store/apps/details?id=" + Constants.GBOARD_PACKAGE_NAME);
  }

  private final ContentObserver imeSettingsContentObserver =
      new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
          return false;
        }

        @Override
        public void onChange(boolean selfChange) {
          this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
          if (BrailleImePreferencesActivity.this.isImeEnabled()) {
            finishActivity(REQUEST_CODE_IME_SETTINGS);
          }
        }
      };
}
