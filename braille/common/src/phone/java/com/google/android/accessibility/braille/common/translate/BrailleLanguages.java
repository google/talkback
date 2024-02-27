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
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.accessibility.braille.common.FeatureFlagReader;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Holds the list of supported {@link Code} and provides an {@link EditBuffer} for given {@link
 * Code} and {@link TranslatorFactory}.
 */
public class BrailleLanguages {
  public static final Locale LOCALE_AR = Locale.forLanguageTag("ar");
  public static final Locale LOCALE_BG = Locale.forLanguageTag("bg");
  public static final Locale LOCALE_CA = Locale.forLanguageTag("ca");
  public static final Locale LOCALE_CKB = Locale.forLanguageTag("ckb");
  public static final Locale LOCALE_CS = Locale.forLanguageTag("cs");
  public static final Locale LOCALE_CY = Locale.forLanguageTag("cy");
  public static final Locale LOCALE_DA = Locale.forLanguageTag("da");
  public static final Locale LOCALE_DE = Locale.GERMAN;
  public static final Locale LOCALE_EL = Locale.forLanguageTag("el");
  public static final Locale LOCALE_EN = Locale.ENGLISH;
  public static final Locale LOCALE_EN_GB = Locale.UK;
  public static final Locale LOCALE_EN_IN = Locale.forLanguageTag("en-IN");
  public static final Locale LOCALE_EN_US = Locale.US;
  public static final Locale LOCALE_ES = Locale.forLanguageTag("es");
  public static final Locale LOCALE_ET = Locale.forLanguageTag("et");
  public static final Locale LOCALE_FI = Locale.forLanguageTag("fi");
  public static final Locale LOCALE_FR = Locale.FRENCH;
  public static final Locale LOCALE_GRC = Locale.forLanguageTag("grc");
  public static final Locale LOCALE_GU_IN = Locale.forLanguageTag("gu-IN");
  public static final Locale LOCALE_HI_IN = Locale.forLanguageTag("hi-IN");
  public static final Locale LOCALE_HE_IL = Locale.forLanguageTag("he-IL");
  public static final Locale LOCALE_HR = Locale.forLanguageTag("hr");
  public static final Locale LOCALE_HU = Locale.forLanguageTag("hu");
  public static final Locale LOCALE_IS = Locale.forLanguageTag("is");
  public static final Locale LOCALE_IT = Locale.ITALIAN;
  public static final Locale LOCALE_JP = Locale.JAPANESE;
  public static final Locale LOCALE_KM = Locale.forLanguageTag("km");
  public static final Locale LOCALE_KO = Locale.KOREAN;
  public static final Locale LOCALE_KN = Locale.forLanguageTag("kn");
  public static final Locale LOCALE_LT = Locale.forLanguageTag("lt");
  public static final Locale LOCALE_LV = Locale.forLanguageTag("lv");
  public static final Locale LOCALE_ML_IN = Locale.forLanguageTag("ml-IN");
  public static final Locale LOCALE_MR_IN = Locale.forLanguageTag("mr-IN");
  public static final Locale LOCALE_NE = Locale.forLanguageTag("ne");
  public static final Locale LOCALE_NE_IN = Locale.forLanguageTag("ne-IN");
  public static final Locale LOCALE_NL = Locale.forLanguageTag("nl");
  public static final Locale LOCALE_NL_NL = Locale.forLanguageTag("nl-NL");
  public static final Locale LOCALE_NO = Locale.forLanguageTag("no");
  public static final Locale LOCALE_PA = Locale.forLanguageTag("pa");
  public static final Locale LOCALE_PL = Locale.forLanguageTag("pl");
  public static final Locale LOCALE_PT = Locale.forLanguageTag("pt");
  public static final Locale LOCALE_RO = Locale.forLanguageTag("ro");
  public static final Locale LOCALE_RU = Locale.forLanguageTag("ru");
  public static final Locale LOCALE_SD_IN = Locale.forLanguageTag("sd-IN");
  public static final Locale LOCALE_SE = Locale.forLanguageTag("se");
  public static final Locale LOCALE_SI = Locale.forLanguageTag("si");
  public static final Locale LOCALE_SK = Locale.forLanguageTag("sk");
  public static final Locale LOCALE_SR = Locale.forLanguageTag("sr");
  public static final Locale LOCALE_SV = Locale.forLanguageTag("sv");
  public static final Locale LOCALE_TA = Locale.forLanguageTag("ta");
  public static final Locale LOCALE_TE_IN = Locale.forLanguageTag("te-IN");
  public static final Locale LOCALE_TR = Locale.forLanguageTag("tr");
  public static final Locale LOCALE_UK = Locale.forLanguageTag("uk");
  public static final Locale LOCALE_UR = Locale.forLanguageTag("ur");
  public static final Locale LOCALE_VI = Locale.forLanguageTag("vi");
  public static final Locale LOCALE_ZH_YUE = Locale.forLanguageTag("zh-yue");
  public static final Locale LOCALE_ZH_CN = Locale.SIMPLIFIED_CHINESE;
  public static final Locale LOCALE_ZH_TW = Locale.TRADITIONAL_CHINESE;

  /**
   * Finds and produces a {@link EditBuffer} based on {@code code}, {@code translatorFactory}, and
   * {@code contractedMode}.
   */
  public static EditBuffer createEditBuffer(
      Context context,
      TalkBackSpeaker talkBack,
      Code code,
      TranslatorFactory translatorFactory,
      boolean contractedMode) {
    BrailleTranslator translator = translatorFactory.create(context, code.name(), contractedMode);
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

  /** Gets the default {@link Code} based on the {@link Locale}. */
  public static Code getDefaultCode(Context context) {
    Locale systemLocale = Locale.getDefault();
    if (systemLocale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
      return Code.UEB;
    }
    List<Code> localeLanguageCodes =
        BrailleLanguages.getAvailableCodes(context).stream()
            .filter(code -> code.getLocale().getLanguage().equals(systemLocale.getLanguage()))
            .collect(Collectors.toList());
    Optional<Code> firstLocaleCountryCode =
        localeLanguageCodes.stream()
            .filter(code -> code.getLocale().getCountry().equals(systemLocale.getCountry()))
            .collect(Collectors.toList())
            .stream()
            .findFirst();
    if (firstLocaleCountryCode.isPresent()) {
      return firstLocaleCountryCode.get();
    } else if (!localeLanguageCodes.isEmpty()) {
      return localeLanguageCodes.get(0);
    }
    return Code.UEB;
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
    ARABIC(
        LOCALE_AR,
        R.string.code_user_facing_name_arabic,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferArabic2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    ARABIC_COMP8(
        LOCALE_AR,
        R.string.code_user_facing_name_arabic_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    BULGARIAN(
        LOCALE_BG,
        R.string.code_user_facing_name_bulgarian,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferBulgarian(context, translator, talkBack);
      }
    },
    CANTONESE(
        LOCALE_ZH_YUE,
        R.string.code_user_facing_name_catonese,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    CATALAN(
        LOCALE_CA,
        R.string.code_user_facing_name_ca_catalan,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferCatalan(context, translator, talkBack);
      }
    },
    CHINESE_CHINA_CURRENT_WITH_TONES(
        LOCALE_ZH_CN,
        R.string.code_user_facing_name_chinese_china_current_with_tones,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    CHINESE_CHINA_CURRENT_WITHOUT_TONES(
        LOCALE_ZH_CN,
        R.string.code_user_facing_name_chinese_china_current_without_tones,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    CHINESE_CHINA_TWO_CELLS(
        LOCALE_ZH_CN,
        R.string.code_user_facing_name_chinese_china_two_cells,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    CHINESE_CHINA_COMMON(
        LOCALE_ZH_CN,
        R.string.code_user_facing_name_chinese_china_common,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    CHINESE_TAIWAN(
        LOCALE_ZH_TW,
        R.string.code_user_facing_name_chinese_taiwan,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    CROATIAN(
        LOCALE_HR,
        R.string.code_user_facing_name_croatian,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferCroatian(context, translator, talkBack);
      }
    },
    CROATIAN_COMP8(
        LOCALE_HR,
        R.string.code_user_facing_name_croatian_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    CZECH(
        LOCALE_CS,
        R.string.code_user_facing_name_czech,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    CZECH_COMP8(
        LOCALE_CS,
        R.string.code_user_facing_name_czech_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    DANISH(
        LOCALE_DA,
        R.string.code_user_facing_name_danish,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferDanish2(context, translator, talkBack);
        }
        return new EditBufferDanish(context, translator, talkBack);
      }
    },
    DANISH_8(
        LOCALE_DA,
        R.string.code_user_facing_name_danish_eight,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    DANISH_COMP8(
        LOCALE_DA,
        R.string.code_user_facing_name_danish_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    DUTCH_NL(
        LOCALE_NL_NL,
        R.string.code_user_facing_name_dutch_nl,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferDutch(context, translator, talkBack);
      }
    },
    DUTCH_COMP8(
        LOCALE_NL,
        R.string.code_user_facing_name_dutch_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    EN_IN(
        LOCALE_EN_IN,
        R.string.code_user_facing_name_en_in,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    UEB(
        LOCALE_EN,
        R.string.code_user_facing_name_ueb1,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferUeb2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    EN_UK(
        LOCALE_EN_GB,
        R.string.code_user_facing_name_en_uk,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferBritish2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    EN_US_EBAE(
        LOCALE_EN_US,
        R.string.code_user_facing_name_en_us_ebae,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferEbae2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    EN_NABCC(
        LOCALE_EN,
        R.string.code_user_facing_name_en_nabcc_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    EN_COMP6(
        LOCALE_EN_US,
        R.string.code_user_facing_name_en_comp6,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferEnComp6(context, translator, talkBack);
      }
    },
    ESTONIAN(
        LOCALE_ET,
        R.string.code_user_facing_name_estonian,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    FINNISH(
        LOCALE_FI,
        R.string.code_user_facing_name_finnish,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    FINNISH_COMP8(
        LOCALE_FI,
        R.string.code_user_facing_name_finnish_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    FRENCH(
        LOCALE_FR,
        R.string.code_user_facing_name_french,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferFrench2(context, translator, talkBack);
        }
        return new EditBufferFrench(context, translator, talkBack);
      }
    },
    FRENCH_COMP8(
        LOCALE_FR,
        R.string.code_user_facing_name_french_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    GREEK(
        LOCALE_EL,
        R.string.code_user_facing_name_greek,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferGreek(context, translator, talkBack);
      }
    },
    GREEK_INTERNATIONAL(
        LOCALE_GRC,
        R.string.code_user_facing_name_greek_international,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    GERMAN(
        LOCALE_DE,
        R.string.code_user_facing_name_german,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferGerman2(context, translator, talkBack);
        }
        return new EditBufferGerman(context, translator, talkBack);
      }
    },
    GERMAN_COMP8(
        LOCALE_DE,
        R.string.code_user_facing_name_german_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    GUJARATI(
        LOCALE_GU_IN,
        R.string.code_user_facing_name_gujarati_india,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    HEBREW(
        LOCALE_HE_IL,
        R.string.code_user_facing_name_hebrew,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    HEBREW_COMP8(
        LOCALE_HE_IL,
        R.string.code_user_facing_name_hebrew_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    HINDI(
        LOCALE_HI_IN,
        R.string.code_user_facing_name_hindi_india,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    HUNGARIAN(
        LOCALE_HU,
        R.string.code_user_facing_name_hungarian,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferHungarian2(context, translator, talkBack);
        }
        return new EditBufferHungarian(context, translator, talkBack);
      }
    },
    HUNGARIAN_COMP8(
        LOCALE_HU,
        R.string.code_user_facing_name_hungarian_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    ICELANDIC(
        LOCALE_IS,
        R.string.code_user_facing_name_icelandic,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    ICELANDIC_COMP8(
        LOCALE_IS,
        R.string.code_user_facing_name_icelandic_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    ITALIAN_COMP6(
        LOCALE_IT,
        R.string.code_user_facing_name_italian_comp6,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferItalian(context, translator, talkBack);
      }
    },
    ITALIAN_COMP8(
        LOCALE_IT,
        R.string.code_user_facing_name_italian_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    JAPANESE(
        LOCALE_JP,
        R.string.code_user_facing_name_japanese,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    KANNADA(
        LOCALE_KN,
        R.string.code_user_facing_name_kannada,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    KHMER(
        LOCALE_KM,
        R.string.code_user_facing_name_khmer,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    KOREAN(
        LOCALE_KO,
        R.string.code_user_facing_name_korean,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferKorean2(context, translator, talkBack);
        }
        return new EditBufferKorean(context, translator, talkBack);
      }
    },
    KOREAN_2006(
        LOCALE_KO,
        R.string.code_user_facing_name_korean_2006,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferKorean2(context, translator, talkBack);
        }
        return new EditBufferKorean(context, translator, talkBack);
      }
    },
    KURDISH(
        LOCALE_CKB,
        R.string.code_user_facing_name_central_kurdish,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    LATVIAN(
        LOCALE_LV,
        R.string.code_user_facing_name_latvian,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferLatvian(context, translator, talkBack);
      }
    },
    LITHUANIAN(
        LOCALE_LT,
        R.string.code_user_facing_name_lithuanian,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferLithuanian(context, translator, talkBack);
      }
    },
    LITHUANIAN_8(
        LOCALE_LT,
        R.string.code_user_facing_name_lithuanian_8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    MALAYALAM_IN(
        LOCALE_ML_IN,
        R.string.code_user_facing_name_malayalam_indian,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    MARATHI_IN(
        LOCALE_MR_IN,
        R.string.code_user_facing_name_marathi_indian,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    NEPALI(
        LOCALE_NE,
        R.string.code_user_facing_name_nepali,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    NEPALI_IN(
        LOCALE_NE_IN,
        R.string.code_user_facing_name_nepali_indian,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    NORWEGIAN(
        LOCALE_NO,
        R.string.code_user_facing_name_norwegian,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferNorwegian2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    NORWEGIAN_8_NO(
        LOCALE_NO,
        R.string.code_user_facing_name_norwegian_8_no,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    NORWEGIAN_8(
        LOCALE_NO,
        R.string.code_user_facing_name_norwegian_8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    NORWEGIAN_8_6FALLBACK(
        LOCALE_NO,
        R.string.code_user_facing_name_norwegian_8_with_6_fallback,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    POLISH(
        LOCALE_PL,
        R.string.code_user_facing_name_polish,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferPolish(context, translator, talkBack);
      }
    },
    POLISH_COMP8(
        LOCALE_PL,
        R.string.code_user_facing_name_polish_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    PORTUGUESE(
        LOCALE_PT,
        R.string.code_user_facing_name_portuguese,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferPortuguese2(context, translator, talkBack);
        }
        return new EditBufferPortuguese(context, translator, talkBack);
      }
    },
    PORTUGUESE_COMP8(
        LOCALE_PT,
        R.string.code_user_facing_name_portuguese_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    PUNJABI(
        LOCALE_PA,
        R.string.code_user_facing_name_punjabi,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    ROMANIAN_8(
        LOCALE_RO,
        R.string.code_user_facing_name_romanian_no,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    RUSSIAN(
        LOCALE_RU,
        R.string.code_user_facing_name_russian,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferRussian(context, translator, talkBack);
      }
    },
    RUSSIAN_DETAILED(
        LOCALE_RU,
        R.string.code_user_facing_name_russian_detailed,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferRussian(context, translator, talkBack);
      }
    },
    RUSSIAN_COMP8(
        LOCALE_RU,
        R.string.code_user_facing_name_russian_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    SERBIAN(
        LOCALE_SR,
        R.string.code_user_facing_name_serbian,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferSerbian(context, translator, talkBack);
      }
    },
    SINDHI_IN(
        LOCALE_SD_IN,
        R.string.code_user_facing_name_sindhi_indian,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    SINHALA(
        LOCALE_SI,
        R.string.code_user_facing_name_sinhala,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    SLOVAK(
        LOCALE_SK,
        R.string.code_user_facing_name_slovak,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    SPANISH(
        LOCALE_ES,
        R.string.code_user_facing_name_spanish,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferSpanish2(context, translator, talkBack);
        }
        return new EditBufferSpanish(context, translator, talkBack);
      }
    },
    SPANISH_COMP8(
        LOCALE_ES,
        R.string.code_user_facing_name_spanish_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    SWEDEN(
        LOCALE_SV,
        R.string.code_user_facing_name_sweden,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    SWEDEN_8(
        LOCALE_SE,
        R.string.code_user_facing_name_sweden_8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    SWEDISH_COMP8_1996(
        LOCALE_SV,
        R.string.code_user_facing_name_swedish_comp8_1996,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    SWEDISH_COMP8_1989(
        LOCALE_SV,
        R.string.code_user_facing_name_swedish_comp8_1989,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    TAMIL(
        LOCALE_TA,
        R.string.code_user_facing_name_tamil,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    TELUGU_IN(
        LOCALE_TE_IN,
        R.string.code_user_facing_name_telugu_in,
        /* supportedContracted= */ false,
        /* eightDot= */ false),
    TURKISH(
        LOCALE_TR,
        R.string.code_user_facing_name_turkish,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferTurkish2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    TURKISH_8(
        LOCALE_TR,
        R.string.code_user_facing_name_turkish_8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    UKRAINIAN(
        LOCALE_UK,
        R.string.code_user_facing_name_ukrainian,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        // Based on Russian.
        return new EditBufferRussian(context, translator, talkBack);
      }
    },
    UKRAINIAN_COMP8(
        LOCALE_UK,
        R.string.code_user_facing_name_ukrainian_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    URDU(
        LOCALE_UR,
        R.string.code_user_facing_name_urdu,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferUrdu2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    VIETNAMESE(
        LOCALE_VI,
        R.string.code_user_facing_name_vietnamese,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferVietnamese2(context, translator, talkBack);
        }
        return new EditBufferVietnamese(context, translator, talkBack);
      }
    },
    VIETNAMESE_COMP8(
        LOCALE_VI,
        R.string.code_user_facing_name_vietnamese_comp8,
        /* supportedContracted= */ false,
        /* eightDot= */ true),
    WELSH(
        LOCALE_CY,
        R.string.code_user_facing_name_welsh,
        /* supportedContracted= */ true,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        if (contractedMode) {
          return new EditBufferWelsh2(context, translator, talkBack);
        }
        return new EditBufferUnContracted(context, translator, talkBack);
      }
    },
    STUB(
        LOCALE_EN,
        ResourcesCompat.ID_NULL,
        /* supportedContracted= */ false,
        /* eightDot= */ false) {
      @Override
      EditBuffer createEditBuffer(
          Context context,
          TalkBackSpeaker talkBack,
          BrailleTranslator translator,
          boolean contractedMode) {
        return new EditBufferStub(context, translator, talkBack);
      }
    };
    // LINT.ThenChange(
    // //depot/google3/java/com/google/android/accessibility/braille/translate/\
    //     liblouis_tables.bzl:supported_languages,
    // //depot/google3/logs/proto/wireless/android/aas/brailleime/\
    //     brailleime_log.proto:supported_languages)

    private final Locale locale;
    private final boolean supportsContracted;
    private final boolean eightDot;
    @StringRes private final int stringId;

    Code(
        Locale correspondingPrintLanguage,
        @StringRes int stringId,
        boolean supportsContracted,
        boolean eightDot) {
      this.locale = correspondingPrintLanguage;
      this.stringId = stringId;
      this.supportsContracted = supportsContracted;
      this.eightDot = eightDot;
    }

    /** Returns the user-facing name of the code, or null if the code is not user-facing. */
    public String getUserFacingName(Context context) {
      return isAvailable(context) ? context.getResources().getString(stringId) : "";
    }

    public boolean isAvailable(Context context) {
      if (stringId != ResourcesCompat.ID_NULL) {
        if (this == KOREAN || this == KOREAN_2006) {
          return FeatureFlagReader.useKorean(context);
        } else if (this == JAPANESE) {
          return FeatureFlagReader.useJapanese(context);
        } else if (this == CANTONESE) {
          return FeatureFlagReader.useCantonese(context);
        } else if (this == CHINESE_CHINA_COMMON) {
          return FeatureFlagReader.useChineseChinaCommon(context);
        } else if (this == CHINESE_CHINA_CURRENT_WITH_TONES) {
          return FeatureFlagReader.useChineseChinaCurrentWithTones(context);
        } else if (this == CHINESE_CHINA_CURRENT_WITHOUT_TONES) {
          return FeatureFlagReader.useChineseChinaCurrentWithoutTones(context);
        } else if (this == CHINESE_CHINA_TWO_CELLS) {
          return FeatureFlagReader.useChineseChinaTwoCells(context);
        } else if (this == CHINESE_TAIWAN) {
          return FeatureFlagReader.useChineseTaiwan(context);
        }
        return true;
      }
      return false;
    }

    EditBuffer createEditBuffer(
        Context context,
        TalkBackSpeaker talkBack,
        BrailleTranslator translator,
        boolean contractedMode) {
      return new EditBufferUnContracted(context, translator, talkBack);
    }

    public Locale getLocale() {
      return locale;
    }

    /** Returns whether BrailleIme supports contracted mode, depends on the feature flag. */
    public boolean isSupportsContracted(Context context) {
      switch (this) {
        case EN_US_EBAE:
          return FeatureFlagReader.useEBAEContracted(context);
        case EN_UK:
          return FeatureFlagReader.useBritishContracted(context);
        case WELSH:
          return FeatureFlagReader.useWelshContracted(context);
        case ARABIC:
          return FeatureFlagReader.useArabicContracted(context);
        case FRENCH:
          return FeatureFlagReader.useFrenchContracted(context);
        case SPANISH:
          return FeatureFlagReader.useSpanishContracted(context);
        case VIETNAMESE:
          return FeatureFlagReader.useVietnameseContracted(context);
        case GERMAN:
          return FeatureFlagReader.useGermanContracted(context);
        case NORWEGIAN:
          return FeatureFlagReader.useNorwegianContracted(context);
        case PORTUGUESE:
          return FeatureFlagReader.usePortugueseContracted(context);
        case HUNGARIAN:
          return FeatureFlagReader.useHungarianContracted(context);
        case DANISH:
          return FeatureFlagReader.useDanishContracted(context);
        case TURKISH:
          return FeatureFlagReader.useTurkishContracted(context);
        case URDU:
          return FeatureFlagReader.useUrduContracted(context);
        default:
          // fall through
      }
      return supportsContracted;
    }

    public boolean isEightDot() {
      return eightDot;
    }
  }

  private BrailleLanguages() {}
}
