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

import static java.lang.Math.min;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.common.Constants.BrailleType;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.common.translate.GoogleTranslationResultCustomizer;
import com.google.android.accessibility.braille.translate.GoogleBrailleTranslatorFactory;
import com.google.android.accessibility.braille.translate.TranslatorFactory;
import com.google.android.accessibility.braille.translate.liblouis.LibLouis;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Controls the braille common user preferences. */
public class BrailleUserPreferences {
  public static final String BRAILLE_SHARED_PREFS_FILENAME = "braille_keyboard";
  private static final String SHARED_PREFS_VERSION = "brailleime_shared_prefs_version";
  private static final int AAS12_0_SHARED_PREFS_VERSION = 1;
  private static final int AAS12_2_SHARED_PREFS_VERSION = 2;
  private static final int AAS13_1_SHARED_PREFS_VERSION = 3;
  private static final int AAS14_1_SHARED_PREFS_VERSION = 4;
  private static int currentVersion = -1;

  private static final boolean CONTRACTED_MODE_DEFAULT = false;
  // Braille keyboard constants.
  private static final boolean SHOW_SWITCH_INPUT_CODE_GESTURE_TIP = true;
  private static final boolean ACCUMULATE_MODE_DEFAULT = true;
  private static final boolean REVERSE_DOTS_MODE_DEFAULT = false;
  private static final boolean LAUNCH_TUTORIAL_DEFAULT = true;
  private static final int EXIT_KEYBOARD_DEFAULT = 0;
  private static final int SHOW_OPTION_DIALOG_DEFAULT = 0;
  // Braille display constants.
  private static final boolean SHOW_SWITCH_OUTPUT_CODE_GESTURE_TIP = true;
  private static final boolean SHOW_NAVIGATION_COMMAND_UNAVAILABLE_TIP = true;
  private static final boolean SHOW_USB_CONNECT_DIALOG = true;
  private static final boolean WORD_WRAP_DEFAULT = true;
  private static final boolean SHOW_OVERLAY_DEFAULT = false;
  private static final boolean REVERSE_PANNING_BUTTONS = false;
  private static final String BLINKING_INTERVAL_MS_DEFAULT = "750";
  private static final String TIMED_MESSAGE_DURATION_FRACTION_DEFAULT = "1";

  private static final int MAX_SWITCH_BRAILLE_GRADE_COUNT = 5;

  private static final int AUTO_SCROLL_DURATION_MS_DEFAULT = 3000;

  /** Interval of adjust auto scroll duration. Unit is millisecond. */
  public static final int AUTO_SCROLL_DURATION_INTERVAL_MS = 500;

  /** Maximum auto scroll duration. Unit is millisecond. */
  public static final int MAXIMUM_AUTO_SCROLL_DURATION_MS = 20000;

  /** Minimum auto scroll duration. Unit is millisecond. */
  public static final int MINIMUM_AUTO_SCROLL_DURATION_MS = 500;

  private static final boolean AUTO_ADJUST_DURATION_ENABLE_DEFAULT = true;

  private static final int READ_CHARACTER_PER_SECOND = 5;
  private static final int MILLIS_PER_SECOND = 1000;
  private static final int MINIMUM_TIMED_MESSAGE_DURATION_MILLISECOND = 3000;

  private static final String DEPRECATED_UEB_1 = "UEB_1";
  private static final String DEPRECATED_UEB_2 = "UEB_2";
  private static final String DEPRECATED_SINHALA_IN = "SINHALA_IN";

  private BrailleUserPreferences() {}

  // Prefs that are common to BK and BD
  /** Writes current using input {@link Code}. */
  public static void writeCurrentActiveInputCode(Context context, Code code) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putString(context.getString(R.string.pref_brailleime_translator_code), code.name())
        .apply();
  }

  /** Whether current active typing language is eight dot braille. */
  public static boolean isCurrentActiveInputCodeEightDot(Context context) {
    return readCurrentActiveInputCodeAndCorrect(context).isEightDot();
  }

  /** Whether contracted enabled. */
  public static boolean readContractedMode(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_braille_contracted_mode), CONTRACTED_MODE_DEFAULT);
  }

  /** Writes contracted enabled status. */
  public static void writeContractedMode(Context context, boolean contractedMode) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_braille_contracted_mode), contractedMode)
        .apply();
  }

  /** Returns braille type of current typing language. */
  public static BrailleType getCurrentTypingLanguageType(Context context) {
    return BrailleUserPreferences.isCurrentActiveInputCodeEightDot(context)
        ? BrailleType.EIGHT_DOT
        : BrailleType.SIX_DOT;
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

  /** Removes {@link Code} from preferred list. */
  public static void removePreferredCode(Context context, Code code) {
    List<Code> preferredCodes = new ArrayList<>(readPreferredCodes(context));
    if (preferredCodes.remove(code)) {
      writePreferredCodes(context, preferredCodes);
    }
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
  public static TranslatorFactory readTranslatorFactory(Context context) {
    Class<? extends TranslatorFactory> translatorFactorClass = LibLouis.class;
    return new GoogleBrailleTranslatorFactory(
        TranslatorFactory.forName(TranslatorFactory.getNameFromClass(translatorFactorClass)),
        new GoogleTranslationResultCustomizer(context));
  }

  /** Reads current using input {@link Code}. */
  private static Code readCurrentActiveInputCode(Context context) {
    String codeName =
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
            .getString(
                context.getString(R.string.pref_brailleime_translator_code),
                BrailleLanguages.getDefaultCode(context).name());
    return BrailleCommonUtils.valueOfSafe(codeName, BrailleLanguages.getDefaultCode(context));
  }

  /** Reads user preferred {@link List<Code>}. */
  private static List<Code> readPreferredCodes(Context context) {
    Set<String> preferredCodesInStringSet =
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
            .getStringSet(
                context.getString(R.string.pref_brailleime_translator_codes_preferred),
                ImmutableSet.of());
    if (preferredCodesInStringSet.isEmpty()) {
      return ImmutableList.of(BrailleLanguages.getDefaultCode(context));
    }
    List<Code> preferredCodesInList = extractValidCodes(preferredCodesInStringSet);
    return preferredCodesInList.isEmpty()
        ? ImmutableList.of(BrailleLanguages.getDefaultCode(context))
        : preferredCodesInList;
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
          newContractedModeKey, talkBackSharedPreferences.getBoolean(oldContractedModeKey, false));
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
          brailleKeyboardSharedPreferences.getBoolean("pref_brailleime_contracted_mode", false);
      String codePrefKey = context.getString(R.string.pref_brailleime_translator_code);
      if (brailleKeyboardSharedPreferences.getString(codePrefKey, "UEB").equals("UEB")) {
        String newCode = contracted ? DEPRECATED_UEB_2 : DEPRECATED_UEB_1;
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, newCode);
      }
      // Update selected Codes.
      String selectedCodePrefKey =
          context.getString(R.string.pref_brailleime_translator_codes_preferred);
      Set<String> selectedCodesInStringSet =
          new HashSet<>(
              brailleKeyboardSharedPreferences.getStringSet(
                  selectedCodePrefKey, ImmutableSet.of()));
      if (selectedCodesInStringSet.remove(Code.UEB.name())) {
        selectedCodesInStringSet.add(DEPRECATED_UEB_1);
        selectedCodesInStringSet.add(DEPRECATED_UEB_2);
      }
      if (selectedCodesInStringSet.isEmpty()) {
        selectedCodesInStringSet.add(BrailleLanguages.getDefaultCode(context).name());
      }
      brailleKeyboardSharedPreferencesEditor.putStringSet(
          selectedCodePrefKey, selectedCodesInStringSet);
      // Upgrade version.
      brailleKeyboardSharedPreferencesEditor.putInt(
          SHARED_PREFS_VERSION, AAS12_2_SHARED_PREFS_VERSION);
      currentVersion = AAS12_2_SHARED_PREFS_VERSION;

      brailleKeyboardSharedPreferencesEditor.apply();
    }
    // Replace UEB_1 and UEB_2 to UEB.
    if (currentVersion < AAS13_1_SHARED_PREFS_VERSION) {
      boolean contracted = false;
      // Update input active code.
      String codePrefKey = context.getString(R.string.pref_brailleime_translator_code);
      String inputCode = brailleKeyboardSharedPreferences.getString(codePrefKey, Code.UEB.name());
      if (inputCode.equals(DEPRECATED_UEB_1)) {
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, Code.UEB.name());
      } else if (inputCode.equals(DEPRECATED_UEB_2)) {
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, Code.UEB.name());
        contracted = true;
      }
      // Update output active code.
      codePrefKey = context.getString(R.string.pref_bd_output_code);
      String outputCode = brailleKeyboardSharedPreferences.getString(codePrefKey, Code.UEB.name());
      if (outputCode.equals(DEPRECATED_UEB_1)) {
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, Code.UEB.name());
      } else if (outputCode.equals(DEPRECATED_UEB_2)) {
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, Code.UEB.name());
        contracted = true;
      }
      // Update contracted.
      String contractedModePrefKey = context.getString(R.string.pref_braille_contracted_mode);
      brailleKeyboardSharedPreferencesEditor.putBoolean(contractedModePrefKey, contracted);

      // Update selected Codes.
      boolean hasUeb = false;
      String selectedCodePrefKey =
          context.getString(R.string.pref_brailleime_translator_codes_preferred);
      Set<String> selectedCodesInStringSet =
          new HashSet<>(
              brailleKeyboardSharedPreferences.getStringSet(
                  selectedCodePrefKey, ImmutableSet.of()));
      if (selectedCodesInStringSet.remove(DEPRECATED_UEB_1)) {
        hasUeb = true;
      }
      if (selectedCodesInStringSet.remove(DEPRECATED_UEB_2)) {
        hasUeb = true;
      }
      if (hasUeb) {
        selectedCodesInStringSet.add(Code.UEB.name());
      }
      if (selectedCodesInStringSet.isEmpty()) {
        selectedCodesInStringSet.add(BrailleLanguages.getDefaultCode(context).name());
      }
      brailleKeyboardSharedPreferencesEditor.putStringSet(
          selectedCodePrefKey, selectedCodesInStringSet);

      // Upgrade version.
      brailleKeyboardSharedPreferencesEditor.putInt(
          SHARED_PREFS_VERSION, AAS13_1_SHARED_PREFS_VERSION);
      currentVersion = AAS13_1_SHARED_PREFS_VERSION;

      brailleKeyboardSharedPreferencesEditor.apply();
    }
    // Replace SINHALA_IN with SINHALA or SINDHI_IN.
    if (currentVersion < AAS14_1_SHARED_PREFS_VERSION) {
      Locale locale = Locale.getDefault();
      String newCode;
      if (locale.getLanguage().equals(Code.SINDHI_IN.getLocale().getLanguage())
          && locale.getCountry().equals(Code.SINDHI_IN.getLocale().getCountry())) {
        newCode = Code.SINDHI_IN.name();
      } else {
        newCode = Code.SINHALA.name();
      }
      // Update selected Codes.
      String selectedCodePrefKey =
          context.getString(R.string.pref_brailleime_translator_codes_preferred);
      Set<String> selectedCodesInStringSet =
          new HashSet<>(
              brailleKeyboardSharedPreferences.getStringSet(
                  selectedCodePrefKey, ImmutableSet.of()));
      if (selectedCodesInStringSet.remove(DEPRECATED_SINHALA_IN)) {
        selectedCodesInStringSet.add(newCode);
      }
      brailleKeyboardSharedPreferencesEditor.putStringSet(
          selectedCodePrefKey, selectedCodesInStringSet);
      // Update input active code.
      String codePrefKey = context.getString(R.string.pref_brailleime_translator_code);
      String inputCode = brailleKeyboardSharedPreferences.getString(codePrefKey, Code.UEB.name());
      if (inputCode.equals(DEPRECATED_SINHALA_IN)) {
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, newCode);
      }
      // Update output active code.
      codePrefKey = context.getString(R.string.pref_bd_output_code);
      String outputCode = brailleKeyboardSharedPreferences.getString(codePrefKey, Code.UEB.name());
      if (outputCode.equals(DEPRECATED_SINHALA_IN)) {
        brailleKeyboardSharedPreferencesEditor.putString(codePrefKey, newCode);
      }
      // Upgrade version.
      brailleKeyboardSharedPreferencesEditor.putInt(
          SHARED_PREFS_VERSION, AAS14_1_SHARED_PREFS_VERSION);
      currentVersion = AAS14_1_SHARED_PREFS_VERSION;

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
            .getString(
                context.getString(R.string.pref_bd_output_code),
                BrailleLanguages.getDefaultCode(context).name());
    returnCode = BrailleCommonUtils.valueOfSafe(codeName, BrailleLanguages.getDefaultCode(context));
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
  public static boolean readShowSwitchBrailleDisplayInputCodeGestureTip(Context context) {
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
  public static boolean readShowSwitchBrailleDisplayOutputCodeGestureTip(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_bd_show_switch_output_code_gesture_tip),
            SHOW_SWITCH_OUTPUT_CODE_GESTURE_TIP);
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

  /** Writes should notify changing braille display output code preference. */
  public static boolean readShowNavigationCommandUnavailableTip(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_bd_show_navigation_command_unavailable_tip),
            SHOW_NAVIGATION_COMMAND_UNAVAILABLE_TIP);
  }

  /** Writes should notify navigation commands are unavailable tip. */
  public static void writeShowNavigationCommandUnavailableTip(Context context, boolean showTip) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(
            context.getString(R.string.pref_bd_show_navigation_command_unavailable_tip), showTip)
        .apply();
  }

  /** Reads whether to show usb connection dialog. */
  public static boolean readShowUsbConnectDialog(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_bd_show_usb_connect_dialog), SHOW_USB_CONNECT_DIALOG);
  }

  /** Write whether to show usb connection dialog. */
  public static void writeShowUsbConnectDialog(Context context, boolean showAgain) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_bd_show_usb_connect_dialog), showAgain)
        .apply();
  }

  /** Reads whether to do reverse panning buttons preference. */
  public static boolean readReversePanningButtons(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_bd_reverse_panning_buttons), REVERSE_PANNING_BUTTONS);
  }

  /** Writes whether to do reverse panning buttons preference. */
  public static void writeReversePanningButtons(Context context, boolean reversePanningButtons) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(
            context.getString(R.string.pref_bd_reverse_panning_buttons), reversePanningButtons)
        .apply();
  }

  /** Reads the timed message duration fraction user preference. */
  public static String readTimedMessageDurationFraction(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getString(
            context.getString(R.string.pref_bd_timed_message_duration_fraction_key),
            TIMED_MESSAGE_DURATION_FRACTION_DEFAULT);
  }

  /**
   * Calculates the timed message duration in millisecond. Minimum is 3 seconds.
   *
   * <p>Equation: (amount of characters) / 5 * fraction seconds.
   */
  public static int getTimedMessageDurationInMillisecond(Context context, int amountOfCharacter) {
    float fraction = Float.parseFloat(readTimedMessageDurationFraction(context));
    return Math.max(
        Math.round(
            amountOfCharacter / ((float) READ_CHARACTER_PER_SECOND) * fraction * MILLIS_PER_SECOND),
        MINIMUM_TIMED_MESSAGE_DURATION_MILLISECOND);
  }

  /** Gets intervals to blink the cursor. */
  public static String[] getAvailableBlinkingIntervalsMs(Context context) {
    return context.getResources().getStringArray(R.array.blinking_interval_entries_values);
  }

  /** Reads the interval to blink the cursor. */
  public static int readBlinkingIntervalMs(Context context) {
    final String interval =
        getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
            .getString(
                context.getString(R.string.pref_bd_blinking_interval_key),
                BLINKING_INTERVAL_MS_DEFAULT);
    if (Arrays.stream(getAvailableBlinkingIntervalsMs(context)).anyMatch(s -> s.equals(interval))) {
      return Integer.parseInt(interval);
    }
    return Integer.parseInt(BLINKING_INTERVAL_MS_DEFAULT);
  }

  /** Adds count when switching braille grade. */
  public static void writeSwitchContactedCount(Context context) {
    int count = readSwitchContractedCount(context) + 1;
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putInt(
            context.getString(R.string.pref_bd_switch_contracted_count_key),
            min(MAX_SWITCH_BRAILLE_GRADE_COUNT + 1, count))
        .apply();
  }

  /** Reads whether to announce tips when switching braille grade. */
  public static boolean readAnnounceSwitchContracted(Context context) {
    return readSwitchContractedCount(context) <= MAX_SWITCH_BRAILLE_GRADE_COUNT;
  }

  private static int readSwitchContractedCount(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getInt(context.getString(R.string.pref_bd_switch_contracted_count_key), 0);
  }

  /** Writes the interval to blink the cursor. */
  public static void writeBlinkingIntervalMs(Context context, int interval) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putString(
            context.getString(R.string.pref_bd_blinking_interval_key), String.valueOf(interval))
        .apply();
  }

  /** Reads the auto scroll duration. */
  public static int readAutoScrollDuration(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getInt(
            context.getString(R.string.pref_bd_auto_scroll_duration_key),
            AUTO_SCROLL_DURATION_MS_DEFAULT);
  }

  /** Writes the auto scroll duration. */
  public static void writeAutoScrollDuration(Context context, int durationMs) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putInt(
            context.getString(R.string.pref_bd_auto_scroll_duration_key),
            Ints.constrainToRange(
                durationMs, MINIMUM_AUTO_SCROLL_DURATION_MS, MAXIMUM_AUTO_SCROLL_DURATION_MS))
        .apply();
  }

  /** Increases the auto scroll duration. */
  public static void increaseAutoScrollDuration(Context context) {
    writeAutoScrollDuration(
        context, readAutoScrollDuration(context) + AUTO_SCROLL_DURATION_INTERVAL_MS);
  }

  /** Increases the auto scroll duration. */
  public static void decreaseAutoScrollDuration(Context context) {
    writeAutoScrollDuration(
        context, readAutoScrollDuration(context) - AUTO_SCROLL_DURATION_INTERVAL_MS);
  }

  /** Reads whether to auto adjust duration. */
  public static boolean readAutoAdjustDurationEnable(Context context) {
    return getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .getBoolean(
            context.getString(R.string.pref_bd_auto_adjust_duration_enable_key),
            AUTO_ADJUST_DURATION_ENABLE_DEFAULT);
  }

  /** Writes the enable state of auto adjust duration. */
  public static void writeAutoAdjustDurationEnable(Context context, boolean enable) {
    getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .edit()
        .putBoolean(context.getString(R.string.pref_bd_auto_adjust_duration_enable_key), enable)
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
