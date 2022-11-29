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

package com.google.android.accessibility.braille.common.translate;

import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslatorFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the list of supported {@link Code} and provides an {@link EditBuffer} for given {@link
 * Code} and {@link TranslatorFactory}.
 */
public class BrailleLanguages {
  public static final String PRINT_LANGUAGE_EN = "en";
  public static final String PRINT_LANGUAGE_ES = "es";
  public static final String PRINT_LANGUAGE_AR = "ar";
  public static final String PRINT_LANGUAGE_FR = "fr";
  public static final String PRINT_LANGUAGE_PL = "pl";

  /**
   * Finds and produces a {@link EditBuffer} based on {@code code}, {@code translatorFactory}, and
   * {@code contractedMode}.
   */
  public static EditBuffer createEditBuffer(
      Context context, TalkBackSpeaker talkBack, Code code, TranslatorFactory translatorFactory) {
    BrailleTranslator translator = translatorFactory.create(context, code.name());
    return code.createEditBuffer(context, talkBack, translator);
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

  /** Extracts all available {@link List<Code>} within {@code codes}. */
  public static List<Code> extractAvailableCodes(Context context, List<Code> codes) {
    List<Code> availableCodes = getAvailableCodes(context);
    for (int i = availableCodes.size() - 1; i >= 0; i--) {
      if (!codes.contains(availableCodes.get(i))) {
        availableCodes.remove(i);
      }
    }
    return availableCodes;
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
    ARABIC(PRINT_LANGUAGE_AR) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_ar);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context, TalkBackSpeaker talkBack, BrailleTranslator translator) {
        return new EditBufferArabic(context, translator, talkBack);
      }
    },
    FRENCH(PRINT_LANGUAGE_FR) {
      @Nullable
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return null;
        // TODO: Reveal after user testing.
        // return resources.getString(R.string.code_user_facing_name_fr);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context, TalkBackSpeaker talkBack, BrailleTranslator translator) {
        return new EditBufferFrench(context, translator, talkBack);
      }
    },
    POLISH(PRINT_LANGUAGE_PL) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_pl);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context, TalkBackSpeaker talkBack, BrailleTranslator translator) {
        return new EditBufferPolish(context, translator, talkBack);
      }
    },
    SPANISH(PRINT_LANGUAGE_ES) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_es);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context, TalkBackSpeaker talkBack, BrailleTranslator translator) {
        return new EditBufferSpanish(context, translator, talkBack);
      }
    },
    STUB(PRINT_LANGUAGE_EN) {
      @Nullable
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return null;
      }

      @Override
      EditBuffer createEditBuffer(
          Context context, TalkBackSpeaker talkBack, BrailleTranslator translator) {
        return new EditBufferStub(context, translator, talkBack);
      }
    },
    UEB_1(PRINT_LANGUAGE_EN) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_ueb1);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context, TalkBackSpeaker talkBack, BrailleTranslator translator) {
        return new EditBufferUeb1(context, translator, talkBack);
      }
    },
    UEB_2(PRINT_LANGUAGE_EN) {
      @Override
      public CharSequence getUserFacingName(Resources resources) {
        return resources.getString(R.string.code_user_facing_name_ueb2);
      }

      @Override
      EditBuffer createEditBuffer(
          Context context, TalkBackSpeaker talkBack, BrailleTranslator translator) {
        return new EditBufferUeb2(context, translator, talkBack);
      }
    };
    // LINT.ThenChange(
    // //depot/google3/java/com/google/android/accessibility/braille/translate/\
    //     liblouis_tables.bzl:supported_languages,
    // //depot/google3/logs/proto/wireless/android/aas/brailleime/\
    //     brailleime_log.proto:supported_languages)

    /** Returns the user-facing name of the code, or null if the code is not user-facing. */
    public abstract CharSequence getUserFacingName(Resources resources);

    public boolean isAvailable(Context context) {
      return getUserFacingName(context.getResources()) != null;
    }

    abstract EditBuffer createEditBuffer(
        Context context, TalkBackSpeaker talkBack, BrailleTranslator translator);

    final String correspondingPrintLanguage;

    Code(String correspondingPrintLanguage) {
      this.correspondingPrintLanguage = correspondingPrintLanguage;
    }

    public String getCorrespondingPrintLanguage() {
      return correspondingPrintLanguage;
    }
  }

  private BrailleLanguages() {}
}
