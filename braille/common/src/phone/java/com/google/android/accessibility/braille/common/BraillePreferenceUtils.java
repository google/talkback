package com.google.android.accessibility.braille.common;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Util class for braille preferences. */
public class BraillePreferenceUtils {
  private BraillePreferenceUtils() {}

  /**
   * Configures the preferred code {@link Preference} object with appropriate listeners and summary
   * providers.
   */
  public static void setupPreferredCodePreference(
      Context context,
      Preference preferredCodesPref,
      @Nullable OnPreferenceChangeListener onPreferenceChangeListener) {
    if (BrailleUserPreferences.readAvailablePreferredCodes(context).isEmpty()) {
      setDefaultCodesAsPreferredCodes(context);
    }
    preferredCodesPref.setSummaryProvider(
        preference -> {
          List<Code> selectedCodes = BrailleUserPreferences.readAvailablePreferredCodes(context);
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < selectedCodes.size(); i++) {
            String lang = selectedCodes.get(i).getUserFacingName(context);
            if (i == 0) {
              sb.append(lang);
            } else {
              sb.append(context.getResources().getString(R.string.split_comma, lang));
            }
          }
          return sb;
        });
    preferredCodesPref.setOnPreferenceChangeListener(
        (preference, newValue) -> {
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
            .map(value -> value.getUserFacingName(context))
            .toArray(CharSequence[]::new));
    listPreference.setValue(readCode.apply(context).name());
    listPreference.setSummaryProvider(
        preference -> readCode.apply(context).getUserFacingName(context));
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
            android.R.string.ok,
            (dialog, which) ->
                checkboxConsumer.accept(context, !dontShowAgainCheckBox.isChecked()));
    return builder.create();
  }

  private static void setDefaultCodesAsPreferredCodes(Context context) {
    BrailleUserPreferences.writePreferredCodes(
        context, Lists.newArrayList(BrailleLanguages.getDefaultCode(context)));
  }
}
