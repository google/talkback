package com.google.android.accessibility.braille.common;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.utils.MaterialComponentUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Util class for braille preferences. */
public class BraillePreferenceUtils {
  private BraillePreferenceUtils() {}

  /**
   * Configures the preferred code {@link MultiSelectListPreference} object with appropriate
   * listeners and value providers.
   */
  public static void setupPreferredCodePreference(
      Context context,
      MultiSelectListPreference preferredCodesPref,
      @Nullable OnPreferenceChangeListener onPreferenceChangeListener) {
    preferredCodesPref
        .getPreferenceManager()
        .setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
    preferredCodesPref.setPositiveButtonText(android.R.string.ok);
    preferredCodesPref.setNegativeButtonText(android.R.string.cancel);
    List<Code> storedCodes = BrailleUserPreferences.readAvailablePreferredCodes(context);
    if (storedCodes.isEmpty()) {
      setDefaultCodesAsPreferredCodes(context, preferredCodesPref);
    }
    preferredCodesPref.setSummaryProvider(
        preference -> {
          List<Code> selectedCodes = BrailleUserPreferences.readAvailablePreferredCodes(context);
          return TextUtils.join(
              ", ",
              selectedCodes.stream()
                  .map(code -> code.getUserFacingName(context.getResources()))
                  .toArray(CharSequence[]::new));
        });
    List<Code> availableCodes = BrailleLanguages.getAvailableCodes(context);
    preferredCodesPref.setEntries(
        availableCodes.stream()
            .map(code -> code.getUserFacingName(context.getResources()))
            .toArray(CharSequence[]::new));
    preferredCodesPref.setEntryValues(
        availableCodes.stream().map(Enum::name).toArray(CharSequence[]::new));
    preferredCodesPref.setValues(
        Sets.newHashSet(storedCodes.stream().map(Enum::name).collect(Collectors.toSet())));
    preferredCodesPref.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          @SuppressWarnings({"unchecked"})
          Set<String> selectedLanguages = (Set<String>) newValue;
          if (selectedLanguages.isEmpty()) {
            setDefaultCodesAsPreferredCodes(context, preferredCodesPref);
          } else {
            BrailleUserPreferences.writePreferredCodes(
                context, BrailleUserPreferences.extractValidCodes(selectedLanguages));
            preferredCodesPref.setValues(Sets.newHashSet(selectedLanguages));
          }
          if (onPreferenceChangeListener != null) {
            onPreferenceChangeListener.onPreferenceChange(preference, newValue);
          }
          return false;
        });
  }

  /**
   * Configures the {@link Code} for {@link ListPreference} object with appropriate listeners and
   * value providers.
   */
  public static void setupLanguageListPreference(
      Context context,
      ListPreference listPreference,
      Function<Context, Code> readCode,
      BiConsumer<Context, Code> writeCode,
      @Nullable OnPreferenceChangeListener onPreferenceChangeListener) {
    List<Code> preferredCodes = BrailleUserPreferences.readAvailablePreferredCodes(context);
    listPreference.setEntryValues(
        preferredCodes.stream().map(Enum::name).toArray(CharSequence[]::new));
    listPreference.setEntries(
        preferredCodes.stream()
            .map(value -> value.getUserFacingName(context.getResources()))
            .toArray(CharSequence[]::new));
    listPreference.setValue(readCode.apply(context).name());
    listPreference.setSummaryProvider(
        preference -> readCode.apply(context).getUserFacingName(context.getResources()));
    listPreference.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          writeCode.accept(context, Code.valueOf(newValue.toString()));
          if (onPreferenceChangeListener != null) {
            onPreferenceChangeListener.onPreferenceChange(preference, newValue);
          }
          return true;
        });
  }

  /** Creates the tip dialog. */
  public static AlertDialog createTipAlertDialog(
      Context context,
      String title,
      String message,
      BiConsumer<Context, Boolean> checkboxConsumer) {
    AlertDialog.Builder builder = MaterialComponentUtils.alertDialogBuilder(context);
    View view =
        LayoutInflater.from(context).inflate(R.layout.dialog_dont_show_again_checkbox, null);
    CheckBox dontShowAgainCheckBox = view.findViewById(R.id.dont_show_again);
    builder
        .setTitle(title)
        .setMessage(message)
        .setView(view)
        .setPositiveButton(
            R.string.done,
            (dialog, which) ->
                checkboxConsumer.accept(context, !dontShowAgainCheckBox.isChecked()));
    return builder.create();
  }

  private static void setDefaultCodesAsPreferredCodes(
      Context context, MultiSelectListPreference preferredCodesPreference) {
    List<Code> defaultSelectedCodes = BrailleUserPreferences.getDefaultPreferredCodes(context);
    BrailleUserPreferences.writePreferredCodes(context, Lists.newArrayList(defaultSelectedCodes));
    preferredCodesPreference.setValues(
        Sets.newHashSet(defaultSelectedCodes.stream().map(Enum::name).collect(Collectors.toSet())));
  }
}
