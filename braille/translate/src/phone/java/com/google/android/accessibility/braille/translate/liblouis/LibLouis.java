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

package com.google.android.accessibility.braille.translate.liblouis;

import android.content.Context;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslatorFactory;

/** Creates Translator instances that delegate to the LibLouis braille translation library. */
public class LibLouis implements TranslatorFactory {

  @Override
  public BrailleTranslator create(Context context, String codeName, boolean contractedMode) {
    switch (codeName) {
      case "ARABIC":
        LibLouisTranslator uncontracted = new LibLouisTranslatorArabic(context);
        LibLouisTranslator contracted = new LibLouisTranslator(context, "ar-ar-g2.ctb");
        if (contractedMode) {
          return new ExpandableContractedTranslator(uncontracted, contracted);
        }
        return uncontracted;
      case "ARABIC_COMP8":
        return new LibLouisTranslator(context, "ar-ar-comp8.utb");
      case "BULGARIAN":
        return new LibLouisTranslator(context, "bg.utb");
      case "CANTONESE":
        return new LibLouisTranslator(context, "zh_HK.tbl");
      case "CHINESE_TAIWAN":
        return new LibLouisTranslator(context, "zh-tw.ctb");
      case "CHINESE_CHINA_CURRENT_WITH_TONES":
        return new LibLouisTranslator(context, "zhcn-g1.ctb");
      case "CHINESE_CHINA_CURRENT_WITHOUT_TONES":
        return new LibLouisTranslator(context, "zh_CHN.tbl");
      case "CHINESE_CHINA_TWO_CELLS":
        return new LibLouisTranslator(context, "zhcn-g2.ctb");
      case "CHINESE_CHINA_COMMON":
        return new LibLouisTranslator(context, "zhcn-cbs.ctb");
      case "CATALAN":
        return new LibLouisTranslator(context, "ca.tbl");
      case "CROATIAN":
        return new LibLouisTranslator(context, "hr-g1.tbl");
      case "CROATIAN_COMP8":
        return new LibLouisTranslator(context, "hr-comp8.tbl");
      case "CZECH":
        return new LibLouisTranslator(context, "cs-g1.ctb");
      case "CZECH_COMP8":
        return new LibLouisTranslator(context, "cs-comp8.utb");
      case "DANISH":
        return getBrailleTranslator(context, contractedMode, "da-dk-g16.ctb", "da-dk-g26.ctb");
      case "DANISH_8":
        return new LibLouisTranslator(context, "da-dk-g18.ctb");
      case "DANISH_COMP8":
        return new LibLouisTranslator(context, "da-dk-g08.ctb");
      case "DUTCH_COMP8":
        return new LibLouisTranslator(context, "nl-comp8.utb");
      case "DUTCH_NL":
        return new LibLouisTranslator(context, "nl-NL-g0.utb");
      case "EN_UK":
        return getBrailleTranslator(context, contractedMode, "en-gb-g1.utb", "en-GB-g2.ctb");
      case "EN_IN":
        return new LibLouisTranslator(context, "en-in-g1.ctb");
      case "EN_NABCC":
        return new LibLouisTranslator(context, "en-nabcc.utb");
      case "EN_COMP6":
        return new LibLouisTranslator(context, "en-us-comp6.ctb");
      case "UEB":
        return getBrailleTranslator(
            contractedMode,
            new LibLouisTranslatorUeb1(context),
            new LibLouisTranslatorUeb2(context));
      case "EN_US_EBAE":
        return getBrailleTranslator(context, contractedMode, "en-us-g1.ctb", "en-us-g2.ctb");
      case "ESTONIAN":
        return new LibLouisTranslator(context, "et-g0.utb");
      case "FINNISH":
        return new LibLouisTranslator(context, "fi.utb");
      case "FINNISH_COMP8":
        return new LibLouisTranslator(context, "fi-fi-8dot.ctb");
      case "FRENCH":
        return getBrailleTranslator(
            contractedMode,
            new LibLouisTranslatorFrench(context),
            new LibLouisTranslator(context, "fr-bfu-g2.ctb"));
      case "FRENCH_COMP8":
        return new LibLouisTranslator(context, "fr-bfu-comp8.utb");
      case "GERMAN":
        return getBrailleTranslator(context, contractedMode, "de-g0.utb", "de-g2.ctb");
      case "GERMAN_COMP8":
        return new LibLouisTranslator(context, "de-de-comp8.ctb");
      case "GREEK":
        return new LibLouisTranslator(context, "el.ctb");
      case "GREEK_INTERNATIONAL":
        return new LibLouisTranslator(context, "grc-international-en.utb");
      case "GUJARATI":
        return new LibLouisTranslator(context, "gu.tbl");
      case "HEBREW":
        return new LibLouisTranslator(context, "he-IL.utb");
      case "HEBREW_COMP8":
        return new LibLouisTranslator(context, "he-IL-comp8.utb");
      case "HINDI":
        return new LibLouisTranslator(context, "hi.tbl");
      case "HUNGARIAN":
        return getBrailleTranslator(context, contractedMode, "hu-hu-g1.ctb", "hu-hu-g2.ctb");
      case "HUNGARIAN_COMP8":
        return new LibLouisTranslator(context, "hu-hu-comp8.ctb");
      case "ICELANDIC":
        return new LibLouisTranslator(context, "is.tbl");
      case "ICELANDIC_COMP8":
        return new LibLouisTranslator(context, "is.ctb");
      case "ITALIAN_COMP6":
        return new LibLouisTranslator(context, "it.tbl");
      case "ITALIAN_COMP8":
        return new LibLouisTranslator(context, "it-it-comp8.utb");
      case "JAPANESE":
        return new LibLouisTranslator(context, "ja-kantenji.utb");
      case "KANNADA":
        return new LibLouisTranslator(context, "kn.tbl");
      case "KHMER":
        return new LibLouisTranslator(context, "km-g1.utb");
      case "KOREAN":
        return getBrailleTranslator(context, contractedMode, "ko-g1.ctb", "ko-g2.ctb");
      case "KOREAN_2006":
        return getBrailleTranslator(context, contractedMode, "ko-2006-g1.ctb", "ko-2006-g2.ctb");
      case "KURDISH":
        return new LibLouisTranslator(context, "ckb-g1.ctb");
      case "LITHUANIAN":
        return new LibLouisTranslator(context, "lt-6dot.tbl");
      case "LITHUANIAN_8":
        return new LibLouisTranslator(context, "lt.tbl");
      case "LATVIAN":
        return new LibLouisTranslator(context, "lv.tbl");
      case "MALAYALAM_IN":
        return new LibLouisTranslator(context, "ml.tbl");
      case "MARATHI_IN":
        return new LibLouisTranslator(context, "mr.tbl");
      case "NEPALI":
        return new LibLouisTranslator(context, "ne.ctb");
      case "NEPALI_IN":
        return new LibLouisTranslator(context, "ne.tbl");
      case "NORWEGIAN":
        return getBrailleTranslator(context, contractedMode, "no-no-g0.utb", "no-no-g3.ctb");
      case "NORWEGIAN_8":
        return new LibLouisTranslator(context, "no-no-8dot.utb");
      case "NORWEGIAN_8_6FALLBACK":
        return new LibLouisTranslator(context, "no-no-8dot-fallback-6dot-g0.utb");
      case "NORWEGIAN_8_NO":
        return new LibLouisTranslator(context, "no-no-generic.ctb");
      case "POLISH":
        return new LibLouisTranslatorPolish(context);
      case "POLISH_COMP8":
        return new LibLouisTranslator(context, "pl-pl-comp8.ctb");
      case "PORTUGUESE":
        return getBrailleTranslator(context, contractedMode, "pt-pt-g1.utb", "pt.tbl");
      case "PORTUGUESE_COMP8":
        return new LibLouisTranslator(context, "pt-pt-comp8.ctb");
      case "PUNJABI":
        return new LibLouisTranslator(context, "pa.tbl");
      case "ROMANIAN_8":
        return new LibLouisTranslator(context, "ro.tbl");
      case "RUSSIAN":
        return new LibLouisTranslator(context, "ru-litbrl.ctb");
      case "RUSSIAN_COMP8":
        return new LibLouisTranslator(context, "ru.ctb");
      case "RUSSIAN_DETAILED":
        return new LibLouisTranslator(context, "ru-litbrl-detailed.utb");
      case "SERBIAN":
        return new LibLouisTranslator(context, "sr-g1.ctb");
      case "SINDHI_IN":
        return new LibLouisTranslator(context, "si-in-g1.utb");
      case "SINHALA":
        return new LibLouisTranslator(context, "sin.utb");
      case "SLOVAK":
        return new LibLouisTranslator(context, "sk-g1.ctb");
      case "SPANISH":
        return getBrailleTranslator(
            contractedMode,
            new LibLouisTranslatorSpanish(context),
            new LibLouisTranslator(context, "es-g2.ctb"));
      case "SPANISH_COMP8":
        return new LibLouisTranslator(context, "Es-Es-G0.utb");
      case "SWEDEN":
        return new LibLouisTranslator(context, "sv-g1.ctb");
      case "SWEDEN_8":
        return new LibLouisTranslator(context, "se-se.ctb");
      case "SWEDISH_COMP8_1989":
        return new LibLouisTranslator(context, "sv-1989.ctb");
      case "SWEDISH_COMP8_1996":
        return new LibLouisTranslator(context, "sv-1996.ctb");
      case "TAMIL":
        return new LibLouisTranslator(context, "ta-ta-g1.ctb");
      case "TELUGU_IN":
        return new LibLouisTranslator(context, "te-in-g1.utb");
      case "TURKISH_8":
        return new LibLouisTranslator(context, "tr.ctb");
      case "TURKISH":
        return getBrailleTranslator(context, contractedMode, "tr-g1.ctb", "tr-g2.ctb");
      case "UKRAINIAN":
        return new LibLouisTranslator(context, "uk.utb");
      case "UKRAINIAN_COMP8":
        return new LibLouisTranslator(context, "uk-comp.utb");
      case "URDU":
        return getBrailleTranslator(context, contractedMode, "ur-pk-g1.utb", "ur-pk-g2.ctb");
      case "VIETNAMESE":
        return getBrailleTranslator(context, contractedMode, "vi-vn-g0.utb", "vi-vn-g2.ctb");
      case "VIETNAMESE_COMP8":
        return new LibLouisTranslator(context, "vi-cb8.utb");
      case "WELSH":
        return getBrailleTranslator(context, contractedMode, "cy-cy-g1.utb", "cy.tbl");
      default: // fall out
    }
    throw new IllegalArgumentException("unrecognized code " + codeName);
  }

  @Override
  public String getLibraryName() {
    return this.getClass().getSimpleName();
  }

  private BrailleTranslator getBrailleTranslator(
      Context context, boolean contractedMode, String grade1Table, String grade2Table) {
    return getBrailleTranslator(
        contractedMode,
        new LibLouisTranslator(context, grade1Table),
        new LibLouisTranslator(context, grade2Table));
  }

  private BrailleTranslator getBrailleTranslator(
      boolean contractedMode, LibLouisTranslator uncontracted, LibLouisTranslator contracted) {
    if (contractedMode) {
      return new ExpandableContractedTranslator(uncontracted, contracted);
    }
    return uncontracted;
  }
}
