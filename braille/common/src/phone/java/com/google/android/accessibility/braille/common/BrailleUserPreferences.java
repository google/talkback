/*
 * Copyright 2021 Google Inc.
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
package com.google.android.accessibility.braille.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.text.TextUtils;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.translate.TranslatorFactory;
import com.google.android.accessibility.braille.translate.liblouis.LibLouis;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Controls the braille common user preferences. */
public class BrailleUserPreferences {
  public static final String BRAILLE_SHARED_PREFS_FILENAME = "braille_keyboard";
  private static final String SHARED_PREFS_VERSION = "brailleime_shared_prefs_version";
  private static final int AAS12_0_SHARED_PREFS_VERSION = 1;
  private static final int AAS12_2_SHARED_PREFS_VERSION = 2;
  private static int currentVersion = -1;

  @VisibleForTesting
  static final ImmutableList<Code> PREFERRED_CODES_DEFAULT =
      ImmutableList.of(Code.UEB_1, Code.UEB_2);

  private static final Code CODE_DEFAULT = Code.UEB_2;
  // Braille keyboard constants.
  private static final boolean SHOW_SWITCH_INPUT_CODE_GESTURE_TIP = true;
  private static final boolean ACCUMULATE_MODE_DEFAULT = true;
  private static final boolean REVERSE_DOTS_MODE_DEFAULT = false;
  private static final boolean LAUNCH_TUTORIAL_DEFAULT = true;
  private static final int EXIT_KEYBOARD_DEFAULT = 0;
  private static final int SHOW_OPTION_DIALOG_DEFAULT = 0;
  // Braille display constants.
  private static final boolean WORD_WRAP_DEFAULT = true;
  private static final boolean SHOW_OVERLAY_DEFAULT = false;

  private BrailleUserPreferences() {}

  // Prefs that are common to BK and BD
  /** Writes current using input {@link Code}. */
  public static void writeCurrentActiveInputCode(Context context, Code code) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putString(context.getString(R.string.pref_brailleime_translator_code), code.name())
        .apply();
  }

  /** Retrieves a {@link List<Code>} contains all user preferred and available {@link Code}. */
  public static List<Code> readAvailablePreferredCodes(Context context) {
    return BrailleLanguages.extractAvailableCodes(context, readPreferredCodes(context));
  }

  /** Writes user preferred {@link List<Code>}. */
  public static void writePreferredCodes(Context context, List<Code> preferredCodes) {
    Set<String> selectedCodesInStringSet = new HashSet<>();
    for (Code code : preferredCodes) {
      selectedCodesInStringSet.add(code.name());
    }
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putStringSet(
            context.getString(R.string.pref_brailleime_translator_codes_preferred),
            selectedCodesInStringSet)
        .apply();
  }

  /**
   * Retrieves current active input {@link Code}. If it's not in the preferred codes, returns the
   * first preferred code.
   */
  public static Code readCurrentActiveInputCodeAndCorrect(Context context) {
    Code storedCode = readCurrentActiveInputCode(context);
    List<Code> selectedAvailableCodes = readAvailablePreferredCodes(context);
    if (selectedAvailableCodes.contains(storedCode)) {
      return storedCode;
    }
    Code firstPreferredCode = selectedAvailableCodes.get(0);
    // Update current active input code preference.
    writeCurrentActiveInputCode(context, firstPreferredCode);
    return firstPreferredCode;
  }

  /**
   * Gets the default preferred {@link Code} which are the {@link Code} with spoken language as same
   * as the system language. If there is no matched {@link Code}, return {@link
   * BrailleUserPreferences#PREFERRED_CODES_DEFAULT}.
   */
  public static List<Code> getDefaultPreferredCodes(Context context) {
    String systemLanguage = Locale.getDefault().getLanguage();
    List<Code> preferredCodes =
        Arrays.stream(Code.values())
            .filter(
                code ->
                    code.isAvailable(context)
                        && code.getCorrespondingPrintLanguage().equals(systemLanguage))
            .collect(Collectors.toList());
    if (preferredCodes.isEmpty()) {
      preferredCodes.addAll(PREFERRED_CODES_DEFAULT);
    }
    return preferredCodes;
  }

  /** Gets the next preferred output {@link Code}. */
  public static Code getNextOutputCode(Context context) {
    List<Code> preferredCodes = readAvailablePreferredCodes(context);
    Code currentCode = readCurrentActiveOutputCodeAndCorrect(context);
    int currentCodeIndex = preferredCodes.indexOf(currentCode) + 1;
    if (currentCodeIndex >= preferredCodes.size()) {
      currentCodeIndex = 0;
    }
    return preferredCodes.get(currentCodeIndex);
  }

  /** Gets the next preferred input {@link Code}. */
  public static Code getNextInputCode(Context context) {
    List<Code> preferredCodes = readAvailablePreferredCodes(context);
    Code currentCode = readCurrentActiveInputCodeAndCorrect(context);
    int currentCodeIndex = preferredCodes.indexOf(currentCode) + 1;
    if (currentCodeIndex >= preferredCodes.size()) {
      currentCodeIndex = 0;
    }
    return preferredCodes.get(currentCodeIndex);
  }

  /** Reads translator factory. */
  public static TranslatorFactory readTranslatorFactory() {
    Class<? extends TranslatorFactory> translatorFactorClass = LibLouis.class;
    return TranslatorFactory.forName(TranslatorFactory.getNameFromClass(translatorFactorClass));
  }

  /** Reads current using input {@link Code}. */
  private static Code readCurrentActiveInputCode(Context context) {
    String codeName =
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
            .getString(
                context.getString(R.string.pref_brailleime_translator_code), CODE_DEFAULT.name());
    return BrailleCommonUtils.valueOfSafe(codeName, CODE_DEFAULT);
  }

  /** Reads user preferred {@link List<Code>}. */
  private static List<Code> readPreferredCodes(Context context) {
    Set<String> preferredCodesInStringSet =
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
            .getStringSet(
                context.getString(R.string.pref_brailleime_translator_codes_preferred),
                ImmutableSet.of());
    if (preferredCodesInStringSet.isEmpty()) {
      return getDefaultPreferredCodes(context);
    }
    return extractValidCodes(preferredCodesInStringSet);
  }

  // Prefs that are BK-specific

  /** Reads should notify changing braille keyboard input code preference. */
  public static boolean readShowSwitchBrailleKeyboardInputCodeGestureTip(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_show_switch_input_code_gesture_tip),
            SHOW_SWITCH_INPUT_CODE_GESTURE_TIP);
  }

  /** Writes should notify changing braille keyboard input code preference. */
  public static void writeShowSwitchBrailleKeyboardInputCodeGestureTip(
      Context context, boolean showTip) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_show_switch_input_code_gesture_tip), showTip)
        .apply();
  }

  /** Reads accumulate mode status. */
  public static boolean readAccumulateMode(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_brailleime_accumulate_mode), ACCUMULATE_MODE_DEFAULT);
  }

  /** Writes accumulate mode status. */
  public static void writeAccumulateMode(Context context, boolean accumulateMode) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_brailleime_accumulate_mode), accumulateMode)
        .apply();
  }

  /** Reads reverse dots mode status. */
  public static boolean readReverseDotsMode(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_brailleime_reverse_dots_mode),
            REVERSE_DOTS_MODE_DEFAULT);
  }

  /** Writes reverse dots mode status. */
  public static void writeReverseDotsMode(Context context, boolean reverseDotsMode) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_brailleime_reverse_dots_mode), reverseDotsMode)
        .apply();
  }

  /** Reads layout mode. */
  public static TouchDots readLayoutMode(Context context) {
    return BrailleCommonUtils.valueOfSafe(
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
            .getString(
                context.getString(R.string.pref_brailleime_layout_mode),
                BrailleUtils.isPhoneSizedDevice(context.getResources())
                    ? TouchDots.AUTO_DETECT.name()
                    : TouchDots.TABLETOP.name()),
        TouchDots.AUTO_DETECT);
  }

  /** Writes layout mode. */
  public static void writeLayoutMode(Context context, TouchDots touchDotsMode) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putString(context.getString(R.string.pref_brailleime_layout_mode), touchDotsMode.name())
        .apply();
  }

  /** Reads calibration points for a phone device. */
  public static List<PointF> readCalibrationPointsPhone(
      Context context, boolean isTableTop, int orientation, Size screenSize) throws ParseException {
    return BrailleUserPreferencesTouchDots.readLayoutPointsPhone(
        context,
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME),
        isTableTop,
        orientation,
        screenSize);
  }

  /** Writes calibration points for a phone device. */
  public static void writeCalibrationPointsPhone(
      Context context, boolean isTableTop, int orientation, List<PointF> points, Size screenSize)
      throws ParseException {
    BrailleUserPreferencesTouchDots.writeLayoutPointsPhone(
        context,
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME),
        isTableTop,
        orientation,
        screenSize,
        points);
  }

  /** Reads calibration points for a tablet device. */
  public static List<PointF> readCalibrationPointsTablet(Context context, int orientation)
      throws ParseException {
    return BrailleUserPreferencesTouchDots.readLayoutPointsTablet(
        context, getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME), orientation);
  }

  /** Writes calibration points for a tablet device. */
  public static void writeCalibrationPointsTablet(
      Context context, int orientation, List<PointF> points, Size screenSize)
      throws ParseException {
    BrailleUserPreferencesTouchDots.writeLayoutPointsTablet(
        context,
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME),
        orientation,
        points,
        screenSize);
  }

  /** Sets that tutorial doesn't launch when opens Braille keyboard. */
  public static void setTutorialFinished(Context context) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_brailleime_auto_launch_tutorial), false)
        .apply();
  }

  /** Returns true if it's the first use of Braille keyboard. */
  public static boolean shouldLaunchTutorial(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_brailleime_auto_launch_tutorial),
            LAUNCH_TUTORIAL_DEFAULT);
  }

  /** Sets exit keyboard count. */
  public static void writeExitKeyboardCount(Context context, int count) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putInt(context.getString(R.string.pref_brailleime_exit_keyboard_count), count)
        .apply();
  }

  /** Sets show option dialog count. */
  public static void writeShowOptionDialogCount(Context context, int count) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putInt(context.getString(R.string.pref_brailleime_show_option_dialog_count), count)
        .apply();
  }

  /** Reads exit keyboard count. */
  public static int readExitKeyboardCount(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getInt(
            context.getString(R.string.pref_brailleime_exit_keyboard_count), EXIT_KEYBOARD_DEFAULT);
  }

  /** Reads show option dialog count. */
  public static int readShowOptionDialogCount(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getInt(
            context.getString(R.string.pref_brailleime_show_option_dialog_count),
            SHOW_OPTION_DIALOG_DEFAULT);
  }

  /** Extracts the valid {@link Code} in String Set. */
  public static List<Code> extractValidCodes(Set<String> codes) {
    return codes.stream()
        .map(code -> BrailleCommonUtils.valueOfSafe(code, Code.STUB))
        .filter(code -> !code.equals(Code.STUB))
        .collect(Collectors.toList());
  }

  public static SharedPreferences getSharedPreferences(Context context, String fileName) {
    migrateIfNecessary(context);
    return SharedPreferencesUtils.getSharedPreferences(context, fileName);
  }

  /** Migrates user preferences if necerssary.. */
  public static void migrateIfNecessary(Context context) {
    SharedPreferences brailleKeyboardSharedPreferences =
        SharedPreferencesUtils.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME);
    if (currentVersion == -1) {
      currentVersion = brailleKeyboardSharedPreferences.getInt(SHARED_PREFS_VERSION, 0);
    }
    SharedPreferences talkBackSharedPreferences =
        SharedPreferencesUtils.getSharedPreferences(context);
    SharedPreferences.Editor brailleKeyboardSharedPreferencesEditor =
        brailleKeyboardSharedPreferences.edit();
    // 1. Rename released key names to new names.
    // 2. copy the existing (old) prefs from TalkBack SharedPreference to braille keyboard
    // SharedPreference, and then remove the old TalkBack SharedPreference.
    // 3. Mark migrated.
    if (currentVersion < AAS12_0_SHARED_PREFS_VERSION) {
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

      String newContractedModeKey = "pref_brailleime_contracted_mode";
      String oldContractedModeKey = getOldKey(newContractedModeKey);
      brailleKeyboardSharedPreferencesEditor.putBoolean(
          newContractedModeKey, talkBackSharedPreferences.getBoolean(oldContractedModeKey, true));
      talkBackSharedPreferencesEditor.remove(oldContractedModeKey);

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
          SHARED_PREFS_VERSION, AAS12_0_SHARED_PREFS_VERSION);
      currentVersion = AAS12_0_SHARED_PREFS_VERSION;

      brailleKeyboardSharedPreferencesEditor.apply();
      talkBackSharedPreferencesEditor.apply();
    }
    // Replace Code.UEB with Code.UEB1 and Code.UEB2. Code.UEB is no longer exists.
    if (currentVersion < AAS12_2_SHARED_PREFS_VERSION) {
      // Update current Code.
      boolean contracted =
          brailleKeyboardSharedPreferences.getBoolean("pref_brailleime_contracted_mode", true);
      String codePrefKey = context.getString(R.string.pref_brailleime_translator_code);
      if (brailleKeyboardSharedPreferences.getString(codePrefKey, "UEB").equals("UEB")) {
        Code newCode = contracted ? Code.UEB_2 : Code.UEB_1;
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, newCode.name());
      }
      // Update selected Codes.
      String selectedCodePrefKey =
          context.getString(R.string.pref_brailleime_translator_codes_preferred);
      Set<String> selectedCodesInStringSet =
          new HashSet<>(
              brailleKeyboardSharedPreferences.getStringSet(
                  selectedCodePrefKey, ImmutableSet.of()));
      if (selectedCodesInStringSet.remove("UEB")) {
        selectedCodesInStringSet.add(Code.UEB_1.name());
        selectedCodesInStringSet.add(Code.UEB_2.name());
      }
      if (selectedCodesInStringSet.isEmpty()) {
        for (Code code : getDefaultPreferredCodes(context)) {
          selectedCodesInStringSet.add(code.name());
        }
      }
      brailleKeyboardSharedPreferencesEditor.putStringSet(
          selectedCodePrefKey, selectedCodesInStringSet);
      // Upgrade version.
      brailleKeyboardSharedPreferencesEditor.putInt(
          SHARED_PREFS_VERSION, AAS12_2_SHARED_PREFS_VERSION);
      currentVersion = AAS12_2_SHARED_PREFS_VERSION;

      brailleKeyboardSharedPreferencesEditor.apply();
    }
  }

  private static String getOldKey(String key) {
    return key.replace("_brailleime", "");
  }

  // Prefs that are BD-specific
  /**
   * Reads current active output {@link Code}. Use the first match of system language or the first
   * preferred code if empty.
   */
  public static Code readCurrentActiveOutputCodeAndCorrect(Context context) {
    Code returnCode;
    String codeName =
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
            .getString(context.getString(R.string.pref_bd_output_code), "");
    if (TextUtils.isEmpty(codeName)) {
      String systemLanguage = Locale.getDefault().getLanguage();
      Optional<Code> possibleCode =
          Arrays.stream(Code.values())
              .filter(
                  code ->
                      code.isAvailable(context)
                          && code.getCorrespondingPrintLanguage().equals(systemLanguage))
              .findFirst();
      codeName = possibleCode.isPresent() ? possibleCode.get().name() : Code.STUB.name();
    }
    returnCode = BrailleCommonUtils.valueOfSafe(codeName, Code.UEB_2);
    List<Code> availablePreferredCodes = readAvailablePreferredCodes(context);
    if (!availablePreferredCodes.contains(returnCode)) {
      returnCode = availablePreferredCodes.get(0);
    }
    // Update current active output code preference.
    writeCurrentActiveOutputCode(context, returnCode);
    return returnCode;
  }

  public static void writeCurrentActiveOutputCode(Context context, Code code) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putString(context.getString(R.string.pref_bd_output_code), code.name())
        .apply();
  }

  /** Reads word wrapping preference. */
  public static boolean readWordWrapping(Context context) {
    return SharedPreferencesUtils.getBooleanPref(
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME),
        context.getResources(),
        R.string.pref_braille_word_wrap_key,
        WORD_WRAP_DEFAULT);
  }

  /** Writes word wrapping preference. */
  public static void writeWordWrapping(Context context, boolean wordWrapping) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_braille_word_wrap_key), wordWrapping)
        .apply();
  }

  /** Reads whether on-screen overlay is enabled by user. */
  public static boolean readOnScreenOverlayEnabled(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(context.getString(R.string.pref_braille_overlay_key), SHOW_OVERLAY_DEFAULT);
  }

  /** Reads whether to show search tutorial again. */
  public static boolean readSearchTutorialNeverShow(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(context.getString(R.string.pref_search_tutorial_never_show), false);
  }

  /** Writes whether to search tutorial again. */
  public static void writeSearchTutorialNeverShow(Context context, boolean neverShowAgain) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_search_tutorial_never_show), neverShowAgain)
        .apply();
  }

  /** Reads should notify changing braille display input code preference. */
  public static boolean readShowBrailleDisplaySwitchInputCodeGestureTip(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_bd_show_switch_input_code_gesture_tip),
            SHOW_SWITCH_INPUT_CODE_GESTURE_TIP);
  }

  /** Writes should notify changing braille display input code preference. */
  public static void writeShowSwitchBrailleDisplayInputCodeGestureTip(
      Context context, boolean showTip) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_bd_show_switch_input_code_gesture_tip), showTip)
        .apply();
  }

  /** Reads should notify changing braille display input code preference. */
  public static boolean readShowBrailleDisplaySwitchOutputCodeGestureTip(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_bd_show_switch_output_code_gesture_tip),
            SHOW_SWITCH_INPUT_CODE_GESTURE_TIP);
  }

  /** Writes should notify changing braille display output code preference. */
  public static void writeShowSwitchBrailleDisplayOutputCodeGestureTip(
      Context context, boolean showTip) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(
            context.getString(R.string.pref_bd_show_switch_output_code_gesture_tip), showTip)
        .apply();
  }

  @VisibleForTesting
  static void testing_resetCurrentVersion(Context context) {
    currentVersion = -1;
    SharedPreferencesUtils.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .remove(SHARED_PREFS_VERSION)
        .apply();
  }
}
