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
import static com.google.android.accessibility.brailleime.Constants.SETTINGS_ACTIVITY;

import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.core.text.HtmlCompat;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.brailleime.BrailleLanguages;
import com.google.android.accessibility.brailleime.BrailleLanguages.Code;
import com.google.android.accessibility.brailleime.Constants;
import com.google.android.accessibility.brailleime.Dialogs;
import com.google.android.accessibility.brailleime.DotsLayout;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.UserPreferences;
import com.google.android.accessibility.brailleime.UserPreferences.TypingEchoMode;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.dialog.SeeAllActionsDialog;
import com.google.android.accessibility.utils.BasePreferencesActivity;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.keyboard.KeyboardUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Activity used to set BrailleIme's user options. */
public class BrailleImePreferencesActivity extends BasePreferencesActivity {

  private static final String TAG = "BrailleImePreferencesActivity";

  private static final int REQUEST_CODE_IME_SETTINGS = 100;
  private static final String KEYBOARD_ICON_TOKEN = "KEYBOARD_ICON";
  private PreferenceFragmentCompat preferenceFragmentCompat;

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      startActivity(new Intent().setComponent(SETTINGS_ACTIVITY));
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

  @VisibleForTesting
  PreferenceFragmentCompat getPreferenceFragment() {
    return preferenceFragmentCompat;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getContentResolver()
        .registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
            /*notifyForDescendants=*/ false,
            imeSettingsContentObserver);
  }

  @Override
  protected void onDestroy() {
    getContentResolver().unregisterContentObserver(imeSettingsContentObserver);
    super.onDestroy();
  }

  /** Panel holding a set of developer preferences. */
  public static class BrailleImePrefFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      getPreferenceManager().setSharedPreferencesName(UserPreferences.SHARED_PRES_FILENAME);
      PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.brailleime_preferences);
    }

    @Override
    public void onResume() {
      super.onResume();
      configurePrefs();
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
        // Selected languages preference.
        MultiSelectListPreference selectedLanguagesPref =
            findPreference(getString(R.string.pref_brailleime_translator_codes_preferred));
        List<Code> storedLanguages =
            UserPreferences.extractValidCodes(selectedLanguagesPref.getValues());
        if (storedLanguages.isEmpty()) {
          ((BrailleImePreferencesActivity) getActivity()).setDefaultCodesAsSelectedLanguage();
        } else {
          UserPreferences.writeSelectedCodes(getContext(), storedLanguages);
        }
        selectedLanguagesPref.setSummaryProvider(
            preference -> {
              List<Code> selectedLanguages = BrailleLanguages.getSelectedCodes(getContext());
              return TextUtils.join(
                  ", ",
                  selectedLanguages.stream()
                      .map(code -> code.getUserFacingName(getResources()))
                      .toArray(CharSequence[]::new));
            });
        List<Code> availableCodes = BrailleLanguages.getAvailableCodes(getContext());
        selectedLanguagesPref.setEntries(
            availableCodes.stream()
                .map(code -> code.getUserFacingName(getResources()))
                .toArray(CharSequence[]::new));
        selectedLanguagesPref.setEntryValues(
            availableCodes.stream().map(Enum::name).toArray(CharSequence[]::new));
        selectedLanguagesPref.setOnPreferenceChangeListener(
            (preference, newValue) -> {
              @SuppressWarnings({"unchecked"})
              Set<String> selectedLanguages = (Set<String>) newValue;
              if (selectedLanguages.isEmpty()) {
                ((BrailleImePreferencesActivity) getActivity()).setDefaultCodesAsSelectedLanguage();
                return false;
              }
              return true;
            });
      }

      {
        // See all actions preference.
        Preference seeAllActionPref =
            findPreference(getString(R.string.pref_brailleime_see_all_actions));
        seeAllActionPref.setOnPreferenceClickListener(
            preference -> {
              new SeeAllActionsDialog(getContext()).show();
              return true;
            });
      }

      {
        // Accumulate mode preference.
        SwitchPreference accumulateModePref =
            findPreference(getString(R.string.pref_brailleime_accumulate_mode));
        accumulateModePref.setChecked(UserPreferences.readAccumulateMode(getContext()));
        accumulateModePref.setOnPreferenceClickListener(
            preference -> {
              UserPreferences.writeAccumulateMode(
                  getContext(), ((SwitchPreference) preference).isChecked());
              return true;
            });
        accumulateModePref.setChecked(UserPreferences.readAccumulateMode(getContext()));
      }

      {
        // Reverse dots mode preference.
        SwitchPreference reverseDotsModePref =
            findPreference(getString(R.string.pref_brailleime_reverse_dots_mode));
        reverseDotsModePref.setChecked(UserPreferences.readReverseDotsMode(getContext()));
        reverseDotsModePref.setOnPreferenceClickListener(
            preference -> {
              UserPreferences.writeReverseDotsMode(
                  getContext(), ((SwitchPreference) preference).isChecked());
              return true;
            });
        reverseDotsModePref.setChecked(UserPreferences.readReverseDotsMode(getContext()));
      }

      {
        // Typing echo mode preference.
        ListPreference typingEchoModePref =
            findPreference(getString(R.string.pref_brailleime_typing_echo));
        if (typingEchoModePref != null) {
          TypingEchoMode[] typingEchoModeValues = TypingEchoMode.values();
          typingEchoModePref.setEntryValues(
              Arrays.stream(typingEchoModeValues).map(Enum::name).toArray(CharSequence[]::new));
          typingEchoModePref.setEntries(
              Arrays.stream(typingEchoModeValues)
                  .map(value -> value.getUserFacingName(getResources()))
                  .toArray(CharSequence[]::new));
          typingEchoModePref.setValue(UserPreferences.readTypingEchoMode(getContext()).name());
          typingEchoModePref.setOnPreferenceChangeListener(
              (preference, newValue) -> {
                TypingEchoMode typingEchoModeValue = TypingEchoMode.valueOf(newValue.toString());
                UserPreferences.writeTypingEchoMode(getContext(), typingEchoModeValue);
                return true;
              });
        }
      }

      {
        // Layout settings preference.
        ListPreference layoutModePref =
            findPreference(getString(R.string.pref_brailleime_layout_mode));
        if (layoutModePref != null) {
          if (Utils.isPhoneSizedDevice(getResources())) {
            layoutModePref.setEntryValues(
                Arrays.stream(DotsLayout.values()).map(Enum::name).toArray(CharSequence[]::new));
            layoutModePref.setEntries(
                Arrays.stream(DotsLayout.values())
                    .map(value -> value.getLayoutDescription(getResources()))
                    .toArray(CharSequence[]::new));
            layoutModePref.setValue(UserPreferences.readLayoutMode(getContext()).name());
            layoutModePref.setSummaryProvider(
                preference ->
                    UserPreferences.readLayoutMode(getContext()).getLayoutName(getResources()));
            layoutModePref.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                  UserPreferences.writeLayoutMode(
                      getContext(), DotsLayout.valueOf(newValue.toString()));
                  return true;
                });
          } else {
            layoutModePref.getParent().removePreference(layoutModePref);
          }
        }
      }
    }
  }

  private void setDefaultCodesAsSelectedLanguage() {
    MultiSelectListPreference selectedLanguagesPref =
        (MultiSelectListPreference)
            findPreference(getString(R.string.pref_brailleime_translator_codes_preferred));

    List<Code> defaultSelectedCodes = UserPreferences.getDefaultSelectedCodes(this);
    UserPreferences.writeSelectedCodes(this, Lists.newArrayList(defaultSelectedCodes));
    selectedLanguagesPref.setValues(
        Sets.newHashSet(defaultSelectedCodes.stream().map(Enum::name).collect(Collectors.toSet())));
  }

  private void showTurnOnKeyboardDialog() {
    AlertDialog.Builder builder = Dialogs.getAlertDialogBuilder(this);
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
              R.string.use_brailleime_pref_button_case_ime_disabled,
              (dialogInterface, i) ->
                  startActivityForResult(
                      new Intent(ACTION_INPUT_METHOD_SETTINGS), REQUEST_CODE_IME_SETTINGS))
          .setNegativeButton(
              android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss());
    }
    AlertDialog dialog = builder.create();
    dialog.show();

    // Set movement method to url link.
    View message = dialog.findViewById(android.R.id.message);
    if (message instanceof TextView) {
      ((TextView) message).setMovementMethod(LinkMovementMethod.getInstance());
    }
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
    replaceKeyboardIconTokenToIconDrawable(spannableMessageString);
    insertHyperLinkToSubString(spannableMessageString, gboardName);

    return spannableMessageString;
  }

  @VisibleForTesting
  Spanned getDialogMessageForImeDisabled() {
    String gboardName = getString(R.string.gboard_name);
    String message =
        this.getString(
            R.string.use_brailleime_pref_dialog_case_ime_disabled,
            getString(R.string.braille_ime_service_name),
            KEYBOARD_ICON_TOKEN,
            gboardName);

    SpannableString spannableMessageString =
        SpannableString.valueOf(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY));
    replaceKeyboardIconTokenToIconDrawable(spannableMessageString);
    insertHyperLinkToSubString(spannableMessageString, gboardName);

    return spannableMessageString;
  }

  private void replaceKeyboardIconTokenToIconDrawable(SpannableString spannableString) {
    Utils.formatSubstringAsDrawable(
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
