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
import android.content.res.Resources;
import com.google.android.accessibility.brailleime.translate.EditBuffer;
import com.google.android.accessibility.brailleime.translate.EditBufferArabic;
import com.google.android.accessibility.brailleime.translate.EditBufferFrench;
import com.google.android.accessibility.brailleime.translate.EditBufferSpanish;
import com.google.android.accessibility.brailleime.translate.EditBufferStub;
import com.google.android.accessibility.brailleime.translate.EditBufferUeb1;
import com.google.android.accessibility.brailleime.translate.EditBufferUeb2;
import com.google.android.accessibility.brailleime.translate.Translator;
import com.google.android.accessibility.brailleime.translate.TranslatorFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the list of supported {@link Code} and provides an {@link EditBuffer} for given {@link
 * Code} and {@link TranslatorFactory}.
 */
public class BrailleLanguages {
  public static final String SPOKEN_LANGUAGE_EN = "en";
  public static final String SPOKEN_LANGUAGE_ES = "es";
  public static final String SPOKEN_LANGUAGE_AR = "ar";
  public static final String SPOKEN_LANGUAGE_FR = "fr";

  /**
   * Finds and produces a {@link EditBuffer} based on {@code code}, {@code translatorFactory}, and
   * {@code contractedMode}.
   */
  public static EditBuffer createEditBuffer(
      Context context,
      TalkBackForBrailleImeInternal talkBack,
      Code code,
      TranslatorFactory translatorFactory,
      boolean contractedMode) {
    Translator translator = translatorFactory.create(context, code, contractedMode);
    return code.createEditBuffer(context, talkBack, translator, contractedMode);
  }

  /** Retrieves a {@link List<Code>} contains all available translator {@link Code}. */
  public static List<Code> getAvailableCodes(Context context) {
    List<Code> availableCodes = new ArrayList<>();
    for (Code code : Code.values()) {
      if (code.isAvailable(context)) {
        availableCodes.add(code);
      }
    }
    return availableCodes;
  }

  /** Retrieves a {@link List<Code>} contains all user selected trabslater {@link Code}. */
  public static List<Code> getSelectedCodes(Context context) {
    List<Code> selectedCodes = UserPreferences.readSelectedCodes(context);
    List<Code> availableCodes = getAvailableCodes(context);
    for (int i = availableCodes.size() - 1; i >= 0; i--) {
      if (!selectedCodes.contains(availableCodes.get(i))) {
        availableCodes.remove(i);
      }
    }
    return availableCodes;
  }

  /**
   * Retrieves currently active {@link Code}. If it's not in the selected codes, return the first
   * selected code.
   */
  public static Code getCurrentCodeAndCorrect(Context context) {
    Code storedCode = UserPreferences.readTranslateCode(context);
    List<Code> selectedCodes = getSelectedCodes(context);
    if (selectedCodes.contains(storedCode)) {
      return storedCode;
    }
    Code firstSelectedCode = selectedCodes.get(0);
    // Update current using code preference.
    UserPreferences.writeTranslateCode(context, firstSelectedCode);
    return firstSelectedCode;
  }

  /**
   * An enum for holding Braille language family instances, such as UEB or ComputerBraille.
   *
   * <p>Each instance has a spoken language with which it is associated, which may later become a
   * collection of spoken languages (or a collection of locales) as we add support for more spoken
   * languages. Multiple instances can be associated with the same spoken language; for example both
   * UEB and Computer Braille can be associated with English.
   *
   * <p>Each instance has the important tasks of furnishing an {@link EditBuffer} via the {@link
   * Code#createEditBuffer} method and provides a user facing name if it is available.
   */
  // LINT.IfChange(supported_languages)
  public enum Code {
    ARABIC(SPOKEN_LANGUAGE_AR, false) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_ar);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackForBrailleImeInternal talkBack,
          Translator translator,
          boolean contractedMode) {
        return new EditBufferArabic(context, translator, talkBack);
      }
    },
    FRENCH(SPOKEN_LANGUAGE_FR, false) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return null;
        // TODO: Reveal after user testing.
        // return resources.getString(R.string.code_user_facing_name_fr);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackForBrailleImeInternal talkBack,
          Translator translator,
          boolean contractedMode) {
        return new EditBufferFrench(context, translator, talkBack);
      }
    },
    SPANISH(SPOKEN_LANGUAGE_ES, false) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_es);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackForBrailleImeInternal talkBack,
          Translator translator,
          boolean contractedMode) {
        return new EditBufferSpanish(context, translator, talkBack);
      }
    },
    STUB(SPOKEN_LANGUAGE_EN, false) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return null;
      }

      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackForBrailleImeInternal talkBack,
          Translator translator,
          boolean contractedMode) {
        return new EditBufferStub(context, translator, talkBack);
      }
    },
    UEB(SPOKEN_LANGUAGE_EN, true) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_ueb);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackForBrailleImeInternal talkBack,
          Translator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferUeb2(context, translator, talkBack);
        }
        return new EditBufferUeb1(context, translator, talkBack);
      }
    };
    // LINT.ThenChange(
    // //depot/google3/java/com/google/android/accessibility/braille/service/\
    //     liblouis_tables.bzl:supported_languages,
    // //depot/google3/logs/proto/wireless/android/aas/brailleime/\
    //     brailleime_log.proto:supported_languages)

    /** Returns the user-facing name of the code, or null if the code is not user-facing. */
    public abstract CharSequence getUserFacingName(Resources resources);

    public boolean isAvailable(Context context) {
      return getUserFacingName(context.getResources()) != null;
    }

    abstract EditBuffer createEditBuffer(
        Context context,
        TalkBackForBrailleImeInternal talkBack,
        Translator translator,
        boolean contractedMode);

    final String spokenLanguage;
    final boolean supportsContracted;

    Code(String spokenLanguage, boolean supportsContracted) {
      this.spokenLanguage = spokenLanguage;
      this.supportsContracted = supportsContracted;
    }

    public String getSpokenLanguage() {
      return spokenLanguage;
    }

    public boolean isSupportsContracted() {
      return supportsContracted;
    }
  }

  private BrailleLanguages() {}
}
