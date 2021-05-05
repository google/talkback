/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.brailleime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.PointF;
import android.util.Size;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.brailleime.BrailleLanguages.Code;
import com.google.android.accessibility.brailleime.translate.TranslatorFactory;
import com.google.android.accessibility.brailleime.translate.liblouis.LibLouis;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.common.collect.ImmutableList;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads and writes user preferences. */
public class UserPreferences {

  public static final String SHARED_PRES_FILENAME = "braille_keyboard";
  private static final String SHARED_PREFS_VERSION = "brailleime_shared_prefs_version";
  private static final int BRAILLE_KEYBOARD_SHARED_PREFS_VERSION = 1;
  private static int currentVersion = -1;
  private static final boolean ACCUMULATE_MODE_DEFAULT = true;
  private static final boolean REVERSE_DOTS_MODE_DEFAULT = false;
  private static final boolean CONTRACTED_MODE_DEFAULT = true;
  private static final Code CODE_DEFAULT = Code.UEB;
  private static final TypingEchoMode TYPING_ECHO_MODE_DEFAULT = TypingEchoMode.CHARACTERS;
  private static final boolean LAUNCH_TUTORIAL_DEFAULT = true;
  private static final int EXIT_KEYBOARD_DEFAULT = 0;
  private static final int SHOW_OPTION_DIALOG_DEFAULT = 0;

  @VisibleForTesting
  static final ImmutableList<Code> SELECTED_CODES_DEFAULT = ImmutableList.of(Code.UEB);

  private UserPreferences() {}

  /** Reads accumulate mode status. */
  public static boolean readAccumulateMode(Context context) {
    return getSharedPreferences(context)
        .getBoolean(
            context.getString(R.string.pref_brailleime_accumulate_mode), ACCUMULATE_MODE_DEFAULT);
  }

  /** Writes accumulate mode status. */
  public static void writeAccumulateMode(Context context, boolean accumulateMode) {
    getSharedPreferences(context)
        .edit()
        .putBoolean(context.getString(R.string.pref_brailleime_accumulate_mode), accumulateMode)
        .apply();
  }

  /** Reads reverse dots mode status. */
  public static boolean readReverseDotsMode(Context context) {
    return getSharedPreferences(context)
        .getBoolean(
            context.getString(R.string.pref_brailleime_reverse_dots_mode),
            REVERSE_DOTS_MODE_DEFAULT);
  }

  /** Writes reverse dots mode status. */
  public static void writeReverseDotsMode(Context context, boolean reverseDotsMode) {
    getSharedPreferences(context)
        .edit()
        .putBoolean(context.getString(R.string.pref_brailleime_reverse_dots_mode), reverseDotsMode)
        .apply();
  }

  /** Reads contracted mode status. */
  public static boolean readContractedMode(Context context) {
    return getSharedPreferences(context)
        .getBoolean(
            context.getString(R.string.pref_brailleime_contracted_mode), CONTRACTED_MODE_DEFAULT);
  }

  /** Writes contracted mode status. */
  public static void writeContractedMode(Context context, boolean contractedMode) {
    getSharedPreferences(context)
        .edit()
        .putBoolean(context.getString(R.string.pref_brailleime_contracted_mode), contractedMode)
        .apply();
  }

  /** Reads layout mode. */
  public static DotsLayout readLayoutMode(Context context) {
    return Utils.valueOfSafe(
        getSharedPreferences(context)
            .getString(
                context.getString(R.string.pref_brailleime_layout_mode),
                Utils.isPhoneSizedDevice(context.getResources())
                    ? DotsLayout.AUTO_DETECT.name()
                    : DotsLayout.TABLETOP.name()),
        DotsLayout.AUTO_DETECT);
  }

  /** Writes layout mode. */
  public static void writeLayoutMode(Context context, DotsLayout dotsLayoutMode) {
    getSharedPreferences(context)
        .edit()
        .putString(context.getString(R.string.pref_brailleime_layout_mode), dotsLayoutMode.name())
        .apply();
  }

  /** Reads calibration points for a phone device. */
  public static List<PointF> readCalibrationPointsPhone(
      Context context, boolean isTableTop, int orientation, Size screenSize) throws ParseException {
    return UserPreferencesForDots.readLayoutPointsPhone(
        context, getSharedPreferences(context), isTableTop, orientation, screenSize);
  }

  /** Writes calibration points for a phone device. */
  public static void writeCalibrationPointsPhone(
      Context context, boolean isTableTop, int orientation, List<PointF> points, Size screenSize)
      throws ParseException {
    UserPreferencesForDots.writeLayoutPointsPhone(
        context, getSharedPreferences(context), isTableTop, orientation, screenSize, points);
  }

  /** Reads calibration points for a tablet device. */
  public static List<PointF> readCalibrationPointsTablet(Context context, int orientation)
      throws ParseException {
    return UserPreferencesForDots.readLayoutPointsTablet(
        context, getSharedPreferences(context), orientation);
  }

  /** Writes calibration points for a tablet device. */
  public static void writeCalibrationPointsTablet(
      Context context, int orientation, List<PointF> points, Size screenSize)
      throws ParseException {
    UserPreferencesForDots.writeLayoutPointsTablet(
        context, getSharedPreferences(context), orientation, points, screenSize);
  }

  /** Reads translator code. */
  public static Code readTranslateCode(Context context) {
    String codeName =
        getSharedPreferences(context)
            .getString(
                context.getString(R.string.pref_brailleime_translator_code), CODE_DEFAULT.name());
    return Utils.valueOfSafe(codeName, Code.UEB);
  }

  /** Writes translator code. */
  public static void writeTranslateCode(Context context, Code code) {
    getSharedPreferences(context)
        .edit()
        .putString(context.getString(R.string.pref_brailleime_translator_code), code.name())
        .apply();
  }

  /** Reads the typing echo mode. */
  public static TypingEchoMode readTypingEchoMode(Context context) {
    String typingEchoModeName =
        getSharedPreferences(context)
            .getString(
                context.getString(R.string.pref_brailleime_typing_echo),
                TYPING_ECHO_MODE_DEFAULT.name());
    return Utils.valueOfSafe(typingEchoModeName, TYPING_ECHO_MODE_DEFAULT);
  }

  /** Writes the typing echo mode. */
  public static void writeTypingEchoMode(Context context, TypingEchoMode typingEchoMode) {
    getSharedPreferences(context)
        .edit()
        .putString(context.getString(R.string.pref_brailleime_typing_echo), typingEchoMode.name())
        .apply();
  }

  /** Sets that tutorial doesn't launch when opens Braille keyboard. */
  public static void setTutorialFinished(Context context) {
    getSharedPreferences(context)
        .edit()
        .putBoolean(context.getString(R.string.pref_brailleime_auto_launch_tutorial), false)
        .apply();
  }

  /** Returns true if it's the first use of Braille keyboard. */
  public static boolean shouldLaunchTutorial(Context context) {
    return getSharedPreferences(context)
        .getBoolean(
            context.getString(R.string.pref_brailleime_auto_launch_tutorial),
            LAUNCH_TUTORIAL_DEFAULT);
  }

  /** Reads translator factory. */
  public static TranslatorFactory readTranslatorFactory() {
    Class<? extends TranslatorFactory> translatorFactorClass = LibLouis.class;
    return TranslatorFactory.forName(TranslatorFactory.getNameFromClass(translatorFactorClass));
  }

  /** Sets exit keyboard count. */
  public static void writeExitKeyboardCount(Context context, int count) {
    getSharedPreferences(context)
        .edit()
        .putInt(context.getString(R.string.pref_brailleime_exit_keyboard_count), count)
        .apply();
  }

  /** Sets show option dialog count. */
  public static void writeShowOptionDialogCount(Context context, int count) {
    getSharedPreferences(context)
        .edit()
        .putInt(context.getString(R.string.pref_brailleime_show_option_dialog_count), count)
        .apply();
  }

  /** Sets selected {@link Code}. */
  public static void writeSelectedCodes(Context context, List<Code> selectedCodes) {
    Set<String> selectedCodesInStringSet = new HashSet<>();
    for (Code code : selectedCodes) {
      selectedCodesInStringSet.add(code.name());
    }
    getSharedPreferences(context)
        .edit()
        .putStringSet(
            context.getString(R.string.pref_brailleime_translator_codes_preferred),
            selectedCodesInStringSet)
        .apply();
  }

  /** Reads exit keyboard count. */
  public static int readExitKeyboardCount(Context context) {
    return getSharedPreferences(context)
        .getInt(
            context.getString(R.string.pref_brailleime_exit_keyboard_count), EXIT_KEYBOARD_DEFAULT);
  }

  /** Reads show option dialog count. */
  public static int readShowOptionDialogCount(Context context) {
    return getSharedPreferences(context)
        .getInt(
            context.getString(R.string.pref_brailleime_show_option_dialog_count),
            SHOW_OPTION_DIALOG_DEFAULT);
  }

  /** Reads selected {@link Code}. */
  public static List<Code> readSelectedCodes(Context context) {
    Set<String> selectedCodesInStringSet =
        getSharedPreferences(context)
            .getStringSet(
                context.getString(R.string.pref_brailleime_translator_codes_preferred),
                Collections.emptySet());
    if (selectedCodesInStringSet.isEmpty()) {
      return getDefaultSelectedCodes(context);
    }
    return extractValidCodes(selectedCodesInStringSet);
  }

  /** Extracts the valid {@link Code} in String Set. */
  public static List<Code> extractValidCodes(Set<String> codes) {
    return codes.stream()
        .map(code -> Utils.valueOfSafe(code, Code.STUB))
        .filter(code -> !code.equals(Code.STUB))
        .collect(Collectors.toList());
  }

  /**
   * Gets the default selected language codes which are the {@link Code} with spoken language as
   * same as the system language. If there is no matched {@link Code}, return {@link
   * UserPreferences#SELECTED_CODES_DEFAULT}.
   */
  public static List<Code> getDefaultSelectedCodes(Context context) {
    String systemLanguage = Locale.getDefault().getLanguage();
    List<Code> selectedCodes =
        Arrays.stream(Code.values())
            .filter(
                code ->
                    code.isAvailable(context) && code.getSpokenLanguage().equals(systemLanguage))
            .collect(Collectors.toList());
    if (selectedCodes.isEmpty()) {
      selectedCodes.addAll(SELECTED_CODES_DEFAULT);
    }
    return selectedCodes;
  }

  @VisibleForTesting
  static SharedPreferences getSharedPreferences(Context context) {
    migrateIfNecessary(context);
    return getBrailleKeyboardSharedPreferences(context);
  }

  /** Migrates user preferences if necerssary.. */
  public static void migrateIfNecessary(Context context) {
    SharedPreferences brailleKeyboardSharedPreferences =
        getBrailleKeyboardSharedPreferences(context);
    if (currentVersion == -1) {
      currentVersion = brailleKeyboardSharedPreferences.getInt(SHARED_PREFS_VERSION, 0);
    }
    // 1. Rename released key names to new names.
    // 2. copy the existing (old) prefs from TalkBack SharedPreference to braille keyboard
    // SharedPreference, and then remove the old TalkBack SharedPreference.
    // 3. Mark migrated.
    if (currentVersion < 1) {
      SharedPreferences talkBackSharedPreferences =
          SharedPreferencesUtils.getSharedPreferences(context);
      SharedPreferences.Editor brailleKeyboardSharedPreferencesEditor =
          brailleKeyboardSharedPreferences.edit();
      SharedPreferences.Editor talkBackSharedPreferencesEditor = talkBackSharedPreferences.edit();
      // Start migrating.
      String newAccumulateModeKey = context.getString(R.string.pref_brailleime_accumulate_mode);
      String oldAccumulatedModeKey = getOldKey(newAccumulateModeKey);
      brailleKeyboardSharedPreferencesEditor.putBoolean(
          newAccumulateModeKey,
          talkBackSharedPreferences.getBoolean(oldAccumulatedModeKey, ACCUMULATE_MODE_DEFAULT));
      talkBackSharedPreferencesEditor.remove(oldAccumulatedModeKey);

      String newReverseDotsModeKey = context.getString(R.string.pref_brailleime_reverse_dots_mode);
      String oldReverseDotsModeKey = getOldKey(newReverseDotsModeKey);
      brailleKeyboardSharedPreferencesEditor.putBoolean(
          newReverseDotsModeKey,
          talkBackSharedPreferences.getBoolean(oldReverseDotsModeKey, REVERSE_DOTS_MODE_DEFAULT));
      talkBackSharedPreferencesEditor.remove(oldReverseDotsModeKey);

      String newContractedModeKey = context.getString(R.string.pref_brailleime_contracted_mode);
      String oldContractedModeKey = getOldKey(newContractedModeKey);
      brailleKeyboardSharedPreferencesEditor.putBoolean(
          newContractedModeKey,
          talkBackSharedPreferences.getBoolean(oldContractedModeKey, CONTRACTED_MODE_DEFAULT));
      talkBackSharedPreferencesEditor.remove(oldContractedModeKey);

      String newTypingEchoKey = context.getString(R.string.pref_brailleime_typing_echo);
      String oldTypingEchoKey = getOldKey(newTypingEchoKey);
      brailleKeyboardSharedPreferencesEditor.putString(
          newTypingEchoKey,
          talkBackSharedPreferences.getString(oldTypingEchoKey, TYPING_ECHO_MODE_DEFAULT.name()));
      talkBackSharedPreferencesEditor.remove(oldTypingEchoKey);

      String newAutoLaunchKey = context.getString(R.string.pref_brailleime_auto_launch_tutorial);
      String oldAutoLaunchKey = getOldKey(newAutoLaunchKey);
      brailleKeyboardSharedPreferencesEditor.putBoolean(
          newAutoLaunchKey,
          talkBackSharedPreferences.getBoolean(oldAutoLaunchKey, LAUNCH_TUTORIAL_DEFAULT));
      talkBackSharedPreferencesEditor.remove(oldAutoLaunchKey);

      String newExitCountKey = context.getString(R.string.pref_brailleime_exit_keyboard_count);
      String oldExitCountKey = getOldKey(newExitCountKey);
      brailleKeyboardSharedPreferencesEditor.putInt(
          newExitCountKey,
          talkBackSharedPreferences.getInt(oldExitCountKey, EXIT_KEYBOARD_DEFAULT));
      talkBackSharedPreferencesEditor.remove(oldExitCountKey);

      String newShowOptionDialogCountKey =
          context.getString(R.string.pref_brailleime_show_option_dialog_count);
      String oldShowOptionDialogCountKey = getOldKey(newShowOptionDialogCountKey);
      brailleKeyboardSharedPreferencesEditor.putInt(
          newShowOptionDialogCountKey,
          talkBackSharedPreferences.getInt(
              oldShowOptionDialogCountKey, SHOW_OPTION_DIALOG_DEFAULT));
      talkBackSharedPreferencesEditor.remove(oldShowOptionDialogCountKey);

      // Upgrade version.
      brailleKeyboardSharedPreferencesEditor.putInt(
          SHARED_PREFS_VERSION, BRAILLE_KEYBOARD_SHARED_PREFS_VERSION);
      currentVersion = BRAILLE_KEYBOARD_SHARED_PREFS_VERSION;

      brailleKeyboardSharedPreferencesEditor.apply();
      talkBackSharedPreferencesEditor.apply();
    }
  }

  private static SharedPreferences getBrailleKeyboardSharedPreferences(Context context) {
    return SharedPreferencesUtils.getSharedPreferences(context, SHARED_PRES_FILENAME);
  }

  private static String getOldKey(String key) {
    return key.replace("_brailleime", "");
  }
  /** Enum for the possible values of the typing echo mode user option. */
  public enum TypingEchoMode {
    CHARACTERS(R.string.typing_echo_preference_label_characters),
    WORDS(R.string.typing_echo_preference_label_words),
    ;
    final int userFacingNameStringId;

    TypingEchoMode(@StringRes int userFacingNameStringId) {
      this.userFacingNameStringId = userFacingNameStringId;
    }

    public String getUserFacingName(Resources resources) {
      return resources.getString(userFacingNameStringId);
    }
  }

  @VisibleForTesting
  static void testing_resetCurrentVersion(Context context) {
    currentVersion = -1;
    getBrailleKeyboardSharedPreferences(context).edit().remove(SHARED_PREFS_VERSION).apply();
  }
}
