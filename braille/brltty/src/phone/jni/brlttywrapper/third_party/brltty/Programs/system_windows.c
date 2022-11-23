/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2019 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#include "prologue.h"

#include <fcntl.h>
#include <errno.h>

#include "log.h"
#include "timing.h"
#include "system.h"
#include "system_windows.h"

/* ntdll.dll */
WIN_PROC_STUB(NtSetInformationProcess);


/* kernel32.dll: console */
WIN_PROC_STUB(AttachConsole);
WIN_PROC_STUB(GetLocaleInfoEx);


/* user32.dll */
WIN_PROC_STUB(GetAltTabInfoA);
WIN_PROC_STUB(SendInput);


#ifdef __MINGW32__
/* ws2_32.dll */
WIN_PROC_STUB(getaddrinfo);
WIN_PROC_STUB(freeaddrinfo);
#endif /* __MINGW32__ */


static void *
loadLibrary (const char *name) {
  HMODULE module = LoadLibrary(name);
  if (!module) logMessage(LOG_DEBUG, "%s: %s", gettext("cannot load library"), name);
  return module;
}

static void *
getProcedure (HMODULE module, const char *name) {
  void *address = module? GetProcAddress(module, name): NULL;
  if (!address) logMessage(LOG_DEBUG, "%s: %s", gettext("cannot find procedure"), name);
  return address;
}

static int
addWindowsCommandLineCharacter (char **buffer, int *size, int *length, char character) {
  if (*length == *size) {
    char *newBuffer = realloc(*buffer, (*size = *size? *size<<1: 0X80));
    if (!newBuffer) {
      logSystemError("realloc");
      return 0;
    }
    *buffer = newBuffer;
  }

  (*buffer)[(*length)++] = character;
  return 1;
}

char *
makeWindowsCommandLine (const char *const *arguments) {
  const char backslash = '\\';
  const char quote = '"';
  char *buffer = NULL;
  int size = 0;
  int length = 0;

#define ADD(c) if (!addWindowsCommandLineCharacter(&buffer, &size, &length, (c))) goto error
  while (*arguments) {
    const char *character = *arguments;
    int backslashCount = 0;
    int needQuotes = 0;
    int start = length;

    while (*character) {
      if (*character == backslash) {
        ++backslashCount;
      } else {
        if (*character == quote) {
          needQuotes = 1;
          backslashCount = (backslashCount * 2) + 1;
        } else if ((*character == ' ') || (*character == '\t')) {
          needQuotes = 1;
        }

        while (backslashCount > 0) {
          ADD(backslash);
          --backslashCount;
        }

        ADD(*character);
      }

      ++character;
    }

    if (needQuotes) backslashCount *= 2;
    while (backslashCount > 0) {
      ADD(backslash);
      --backslashCount;
    }

    if (needQuotes) {
      ADD(quote);
      ADD(quote);
      memmove(&buffer[start+1], &buffer[start], length-start-1);
      buffer[start] = quote;
    }

    ADD(' ');
    ++arguments;
  }
#undef ADD

  buffer[length-1] = 0;
  {
    char *line = realloc(buffer, length);
    if (line) return line;
    logSystemError("realloc");
  }

error:
  if (buffer) free(buffer);
  return NULL;
}

void
initializeSystemObject (void) {
  HMODULE library;

#define LOAD_LIBRARY(name) (library = loadLibrary(name))
#define GET_PROC(name) (name##Proc = getProcedure(library, #name))

  if (LOAD_LIBRARY("ntdll.dll")) {
    GET_PROC(NtSetInformationProcess);
  }

  if (LOAD_LIBRARY("kernel32.dll")) {
    GET_PROC(AttachConsole);
    GET_PROC(GetLocaleInfoEx);
  }

  if (LOAD_LIBRARY("user32.dll")) {
    GET_PROC(GetAltTabInfoA);
    GET_PROC(SendInput);
  }

#ifdef __MINGW32__
  if (LOAD_LIBRARY("ws2_32.dll")) {
    GET_PROC(getaddrinfo);
    GET_PROC(freeaddrinfo);
  }
#endif /* __MINGW32__ */
}

#ifdef __MINGW32__
#include "win_errno.h"

#ifndef SUBLANG_DUTCH_NETHERLANDS
#define SUBLANG_DUTCH_NETHERLANDS SUBLANG_DUTCH
#endif /* SUBLANG_DUTCH_NETHERLANDS */

#ifndef SUBLANG_ENGLISH_IRELAND
#define SUBLANG_ENGLISH_IRELAND SUBLANG_ENGLISH_EIRE
#endif /* SUBLANG_ENGLISH_IRELAND */

#ifndef SUBLANG_FRENCH_FRANCE
#define SUBLANG_FRENCH_FRANCE SUBLANG_FRENCH
#endif /* SUBLANG_FRENCH_FRANCE */

#ifndef SUBLANG_GERMAN_GERMANY
#define SUBLANG_GERMAN_GERMANY SUBLANG_GERMAN
#endif /* SUBLANG_GERMAN_GERMANY */

#ifndef SUBLANG_ITALIAN_ITALY
#define SUBLANG_ITALIAN_ITALY SUBLANG_ITALIAN
#endif /* SUBLANG_ITALIAN_ITALY */

#ifndef SUBLANG_KOREAN_KOREA
#define SUBLANG_KOREAN_KOREA SUBLANG_KOREAN
#endif /* SUBLANG_KOREAN_KOREA */

#ifndef SUBLANG_LITHUANIAN_LITHUANIA
#define SUBLANG_LITHUANIAN_LITHUANIA SUBLANG_LITHUANIAN
#endif /* SUBLANG_LITHUANIAN_LITHUANIA */

#ifndef SUBLANG_PORTUGUESE_PORTUGAL
#define SUBLANG_PORTUGUESE_PORTUGAL SUBLANG_PORTUGUESE
#endif /* SUBLANG_PORTUGUESE_PORTUGAL */

#ifndef SUBLANG_SPANISH_SPAIN
#define SUBLANG_SPANISH_SPAIN SUBLANG_SPANISH
#endif /* SUBLANG_SPANISH_SPAIN */

#ifndef SUBLANG_SWEDISH_SWEDEN
#define SUBLANG_SWEDISH_SWEDEN SUBLANG_SWEDISH
#endif /* SUBLANG_SWEDISH_SWEDEN */

#ifndef SUBLANG_SYRIAC_TURKEY
#define SUBLANG_SYRIAC_TURKEY SUBLANG_SYRIAC
#endif /* SUBLANG_SYRIAC_TURKEY */

char *
getWindowsLocaleName (void) {
  if (GetLocaleInfoExProc) {
#define WIN_LOCALE_SIZE 85
    WCHAR buffer[WIN_LOCALE_SIZE];
    int result = GetLocaleInfoExProc(LOCALE_NAME_USER_DEFAULT, LOCALE_SNAME, buffer, WIN_LOCALE_SIZE);

    if (result > 0) {
      char locale[WIN_LOCALE_SIZE];
      const WCHAR *source = buffer;
      char *target = locale;

      do {
        WCHAR c = *source;

        if (c == '-') c = '_';
        *target++ = c;
      } while (*source++);

      {
        char *name = strdup(locale);

        if (name) {
          return name;
        } else {
          logMallocError();
        }
      }
    } else {
      logWindowsSystemError("GetLocaleInfoEx");
    }
  }

  {
    DWORD langid;
    int result = GetLocaleInfo(LOCALE_USER_DEFAULT,
                               LOCALE_ILANGUAGE | LOCALE_RETURN_NUMBER,
                               (char *)&langid, sizeof(langid)/sizeof(TCHAR));

    if (result > 0) {
      char *name;

      switch (langid) {
#define DIALECT(primary,secondary,locale) case MAKELANGID(LANG_##primary, SUBLANG_##primary##_##secondary): name = (locale); break;
        DIALECT(AFRIKAANS, SOUTH_AFRICA, "af_ZA");
        DIALECT(ALBANIAN, ALBANIA, "sq_AL");
        DIALECT(ALSATIAN, FRANCE, "gsw_FR");
        DIALECT(AMHARIC, ETHIOPIA, "am_ET");
        DIALECT(ARABIC, ALGERIA, "ar_DZ");
        DIALECT(ARABIC, BAHRAIN, "ar_BH");
        DIALECT(ARABIC, EGYPT, "ar_EG");
        DIALECT(ARABIC, IRAQ, "ar_IQ");
        DIALECT(ARABIC, JORDAN, "ar_JO");
        DIALECT(ARABIC, KUWAIT, "ar_QW");
        DIALECT(ARABIC, LEBANON, "ar_LB");
        DIALECT(ARABIC, LIBYA, "ar_LY");
        DIALECT(ARABIC, MOROCCO, "ar_MA");
        DIALECT(ARABIC, OMAN, "ar_OM");
        DIALECT(ARABIC, QATAR, "ar_QA");
        DIALECT(ARABIC, SAUDI_ARABIA, "ar_SA");
        DIALECT(ARABIC, SYRIA, "ar_SY");
        DIALECT(ARABIC, TUNISIA, "ar_TN");
        DIALECT(ARABIC, UAE, "ar_AE");
        DIALECT(ARABIC, YEMEN, "ar_YE");
        DIALECT(ARMENIAN, ARMENIA, "hy_AM");
        DIALECT(ASSAMESE, INDIA, "as_IN");
        DIALECT(AZERI, CYRILLIC, "az@cyrillic");
        DIALECT(AZERI, LATIN, "az@latin");
        DIALECT(BASHKIR, RUSSIA, "ba_RU");
        DIALECT(BASQUE, BASQUE, "eu_XX");
        DIALECT(BELARUSIAN, BELARUS, "be_BY");
        DIALECT(BENGALI, BANGLADESH, "bn_HD");
        DIALECT(BENGALI, INDIA, "bn_IN");
        DIALECT(BOSNIAN, BOSNIA_HERZEGOVINA_CYRILLIC, "bs_BA@cyrillic");
        DIALECT(BOSNIAN, BOSNIA_HERZEGOVINA_LATIN, "bs_BA@latin");
        DIALECT(BRETON, FRANCE, "br_FR");
        DIALECT(BULGARIAN, BULGARIA, "bg_BG");
        DIALECT(CATALAN, CATALAN, "ca_XX");
        DIALECT(CHINESE, HONGKONG, "zh_HK");
        DIALECT(CHINESE, MACAU, "zh_MO");
        DIALECT(CHINESE, SIMPLIFIED, "zh_CN");
        DIALECT(CHINESE, SINGAPORE, "zh_SG");
        DIALECT(CHINESE, TRADITIONAL, "zh_TW");
        DIALECT(CORSICAN, FRANCE, "co_FR");
        DIALECT(CROATIAN, BOSNIA_HERZEGOVINA_LATIN, "hr_BA@latin");
        DIALECT(CROATIAN, CROATIA, "hr_HR");
        DIALECT(CZECH, CZECH_REPUBLIC, "cs_CZ");
        DIALECT(DANISH, DENMARK, "da_DK");
        DIALECT(DIVEHI, MALDIVES, "dv_MV");
        DIALECT(DUTCH, BELGIAN, "nl_BE");
        DIALECT(DUTCH, NETHERLANDS, "nl_NL");
        DIALECT(ENGLISH, AUS, "en_AU");
        DIALECT(ENGLISH, BELIZE, "en_BZ");
        DIALECT(ENGLISH, CAN, "en_CA");
        DIALECT(ENGLISH, CARIBBEAN, "en_XX");
        DIALECT(ENGLISH, INDIA, "en_IN");
        DIALECT(ENGLISH, IRELAND, "en_IE");
        DIALECT(ENGLISH, JAMAICA, "en_JM");
        DIALECT(ENGLISH, MALAYSIA, "en_MY");
        DIALECT(ENGLISH, NZ, "en_NZ");
        DIALECT(ENGLISH, PHILIPPINES, "en_PH");
        DIALECT(ENGLISH, SINGAPORE, "en_SG");
        DIALECT(ENGLISH, SOUTH_AFRICA, "en_ZA");
        DIALECT(ENGLISH, TRINIDAD, "en_TT");
        DIALECT(ENGLISH, UK, "en_GB");
        DIALECT(ENGLISH, US, "en_US");
        DIALECT(ENGLISH, ZIMBABWE, "en_ZW");
        DIALECT(ESTONIAN, ESTONIA, "et_EE");
        DIALECT(FAEROESE, FAROE_ISLANDS, "fo_FO");
        DIALECT(FILIPINO, PHILIPPINES, "fil_PH");
        DIALECT(FINNISH, FINLAND, "fi_FI");
        DIALECT(FRENCH, BELGIAN, "fr_BE");
        DIALECT(FRENCH, CANADIAN, "fr_CA");
        DIALECT(FRENCH, FRANCE, "fr_FR");
        DIALECT(FRENCH, LUXEMBOURG, "fr_LU");
        DIALECT(FRENCH, MONACO, "fr_MC");
        DIALECT(FRENCH, SWISS, "fr_CH");
        DIALECT(FRISIAN, NETHERLANDS, "fy_NL");
        DIALECT(GALICIAN, GALICIAN, "gl_ES");
        DIALECT(GEORGIAN, GEORGIA, "ka_GE");
        DIALECT(GERMAN, AUSTRIAN, "de_AT");
        DIALECT(GERMAN, GERMANY, "de_DE");
        DIALECT(GERMAN, LIECHTENSTEIN, "de_LI");
        DIALECT(GERMAN, LUXEMBOURG, "de_LU");
        DIALECT(GERMAN, SWISS, "de_CH");
        DIALECT(GREEK, GREECE, "el_GR");
        DIALECT(GREENLANDIC, GREENLAND, "kl_GL");
        DIALECT(GUJARATI, INDIA, "gu_IN");
        DIALECT(HAUSA, NIGERIA, "ha_NG");
        DIALECT(HEBREW, ISRAEL, "he_IL");
        DIALECT(HINDI, INDIA, "hi_IN");
        DIALECT(HUNGARIAN, HUNGARY, "hu_HU");
        DIALECT(ICELANDIC, ICELAND, "is_IS");
        DIALECT(IGBO, NIGERIA, "ig_NG");
        DIALECT(INDONESIAN, INDONESIA, "id_ID");
        DIALECT(INUKTITUT, CANADA, "iu_CA");
        DIALECT(IRISH, IRELAND, "ga_IE");
        DIALECT(ITALIAN, ITALY, "it_IT");
        DIALECT(ITALIAN, SWISS, "it_CH");
        DIALECT(JAPANESE, JAPAN, "ja_JP");
        DIALECT(KASHMIRI, INDIA, "ks_IN");
        DIALECT(KAZAK, KAZAKHSTAN, "kk_KZ");
        DIALECT(KHMER, CAMBODIA, "km_KH");
        DIALECT(KICHE, GUATEMALA, "quc_GT");
        DIALECT(KINYARWANDA, RWANDA, "rw_RW");
        DIALECT(KONKANI, INDIA, "kok_IN");
        DIALECT(KOREAN, KOREA, "ko_KR");
        DIALECT(KYRGYZ, KYRGYZSTAN, "ky_KG");
        DIALECT(LAO, LAO_PDR, "lo_LA");
        DIALECT(LATVIAN, LATVIA, "lv_LV");
        DIALECT(LITHUANIAN, LITHUANIA, "lt_LT");
        DIALECT(LOWER_SORBIAN, GERMANY, "dsb_DE");
        DIALECT(LUXEMBOURGISH, LUXEMBOURG, "lb_LU");
        DIALECT(MACEDONIAN, MACEDONIA, "mk_MK");
        DIALECT(MALAY, BRUNEI_DARUSSALAM, "ms_BN");
        DIALECT(MALAY, MALAYSIA, "ms_MY");
        DIALECT(MALAYALAM, INDIA, "ml_IN");
        DIALECT(MALTESE, MALTA, "mt_MT");
        DIALECT(MAORI, NEW_ZEALAND, "mi_NZ");
        DIALECT(MAPUDUNGUN, CHILE, "arn_CL");
        DIALECT(MARATHI, INDIA, "mr_IN");
        DIALECT(MOHAWK, MOHAWK, "moh");
        DIALECT(MONGOLIAN, CYRILLIC_MONGOLIA, "mn_MN@cyrillic");
        DIALECT(MONGOLIAN, PRC, "mn_CN");
        DIALECT(NEPALI, INDIA, "ne_IN");
        DIALECT(NEPALI, NEPAL, "ne_NP");
        DIALECT(NORWEGIAN, BOKMAL, "nb_NO");
        DIALECT(NORWEGIAN, NYNORSK, "nn_NO");
        DIALECT(OCCITAN, FRANCE, "oc_FR");
        DIALECT(ORIYA, INDIA, "or_IN");
        DIALECT(PASHTO, AFGHANISTAN, "ps_AF");
        DIALECT(PERSIAN, IRAN, "fa_IR");
        DIALECT(POLISH, POLAND, "pl_PL");
        DIALECT(PORTUGUESE, BRAZILIAN, "pt_BR");
        DIALECT(PORTUGUESE, PORTUGAL, "pt_PT");
        DIALECT(PUNJABI, INDIA, "pa_IN");
#ifdef SUBLANG_PUNJABI_PAKISTAN
        DIALECT(PUNJABI, PAKISTAN, "pa_PK");
#endif /* SUBLANG_PUNJABI_PAKISTAN */
        DIALECT(QUECHUA, BOLIVIA, "qu_BO");
        DIALECT(QUECHUA, ECUADOR, "qu_EC");
        DIALECT(QUECHUA, PERU, "qu_PE");
#ifdef SUBLANG_ROMANIAN_MOLDOVA
        DIALECT(ROMANIAN, MOLDOVA, "ro_MD");
#endif /* SUBLANG_ROMANIAN_MOLDOVA */
        DIALECT(ROMANIAN, ROMANIA, "ro_RO");
        DIALECT(RUSSIAN, RUSSIA, "ru_RU");
        DIALECT(SAMI, LULE_NORWAY, "smj_NO");
        DIALECT(SAMI, LULE_SWEDEN, "smj_SE");
        DIALECT(SAMI, NORTHERN_FINLAND, "sme_FI");
        DIALECT(SAMI, NORTHERN_NORWAY, "sme_NO");
        DIALECT(SAMI, NORTHERN_SWEDEN, "sme_SE");
        DIALECT(SAMI, SOUTHERN_NORWAY, "sma_NO");
        DIALECT(SAMI, SOUTHERN_SWEDEN, "sma_SE");
        DIALECT(SANSKRIT, INDIA, "sa_IN");
        DIALECT(SERBIAN, BOSNIA_HERZEGOVINA_CYRILLIC, "sr_BA@cyrillic");
        DIALECT(SERBIAN, BOSNIA_HERZEGOVINA_LATIN, "sr_BA@latin");
        DIALECT(SERBIAN, CYRILLIC, "sr@cyrillic");
        DIALECT(SERBIAN, LATIN, "sr@latin");
        DIALECT(SINDHI, AFGHANISTAN, "sd_AF");
        DIALECT(SINHALESE, SRI_LANKA, "si_LK");
        DIALECT(SLOVAK, SLOVAKIA, "sk_SK");
        DIALECT(SLOVENIAN, SLOVENIA, "sl_SI");
        DIALECT(SOTHO, NORTHERN_SOUTH_AFRICA, "st_XX");
        DIALECT(SPANISH, ARGENTINA, "es_AR");
        DIALECT(SPANISH, BOLIVIA, "es_BO");
        DIALECT(SPANISH, CHILE, "es_CL");
        DIALECT(SPANISH, COLOMBIA, "es_CO");
        DIALECT(SPANISH, COSTA_RICA, "es_CR");
        DIALECT(SPANISH, DOMINICAN_REPUBLIC, "es_DO");
        DIALECT(SPANISH, ECUADOR, "es_EC");
        DIALECT(SPANISH, EL_SALVADOR, "es_SV");
        DIALECT(SPANISH, GUATEMALA, "es_GT");
        DIALECT(SPANISH, HONDURAS, "es_HN");
        DIALECT(SPANISH, MEXICAN, "es_MX");
        DIALECT(SPANISH, MODERN, "es_XX");
        DIALECT(SPANISH, NICARAGUA, "es_NI");
        DIALECT(SPANISH, PANAMA, "es_PA");
        DIALECT(SPANISH, PARAGUAY, "es_PY");
        DIALECT(SPANISH, PERU, "es_PE");
        DIALECT(SPANISH, PUERTO_RICO, "es_PR");
        DIALECT(SPANISH, SPAIN, "es_ES");
        DIALECT(SPANISH, URUGUAY, "es_UY");
        DIALECT(SPANISH, US, "es_US");
        DIALECT(SPANISH, VENEZUELA, "es_VE");
        DIALECT(SWEDISH, FINLAND, "sv_FI");
        DIALECT(SWEDISH, SWEDEN, "sv_SE");
        DIALECT(SYRIAC, TURKEY, "syr_TR");
        DIALECT(TAMAZIGHT, ALGERIA_LATIN, "ber_DZ@latin");
        DIALECT(TAMIL, INDIA, "ta_IN");
        DIALECT(TATAR, RUSSIA, "tt_RU");
        DIALECT(TELUGU, INDIA, "te_IN");
        DIALECT(THAI, THAILAND, "th_TH");
        DIALECT(TIBETAN, BHUTAN, "bo_BT");
        DIALECT(TIBETAN, PRC, "bo_CN");
        DIALECT(TIGRIGNA, ERITREA, "ti_ER");
        DIALECT(TSWANA, SOUTH_AFRICA, "tn_ZA");
        DIALECT(TURKISH, TURKEY, "tr_TR");
        DIALECT(UIGHUR, PRC, "ug_CN");
        DIALECT(UKRAINIAN, UKRAINE, "uk_UA");
      //DIALECT(UPPER_SORBIAN, GERMANY, "hsb_DE");
        DIALECT(URDU, INDIA, "ur_IN");
        DIALECT(URDU, PAKISTAN, "ur_PK");
        DIALECT(UZBEK, CYRILLIC, "uz@cyrillic");
        DIALECT(UZBEK, LATIN, "uz@latin");
        DIALECT(VIETNAMESE, VIETNAM, "vi_VN");
        DIALECT(WELSH, UNITED_KINGDOM, "cy_GB");
        DIALECT(WOLOF, SENEGAL, "fy_SN");
        DIALECT(XHOSA, SOUTH_AFRICA, "xh_ZA");
        DIALECT(YAKUT, RUSSIA, "sah_RU");
        DIALECT(YI, PRC, "ii_CN");
        DIALECT(YORUBA, NIGERIA, "yo_NG");
        DIALECT(ZULU, SOUTH_AFRICA, "zu_ZA");
#undef DIALECTo

        default:
          switch (PRIMARYLANGID(langid)) {
#define LANGUAGE(primary,locale) case LANG_##primary: name = (locale); break;
            LANGUAGE(AFRIKAANS, "af");
            LANGUAGE(ALBANIAN, "sq");
            LANGUAGE(ALSATIAN, "gsw");
            LANGUAGE(AMHARIC, "am");
            LANGUAGE(ARABIC, "ar");
            LANGUAGE(ARMENIAN, "hy");
            LANGUAGE(ASSAMESE, "as");
            LANGUAGE(AZERI, "az");
            LANGUAGE(BASHKIR, "ba");
            LANGUAGE(BASQUE, "eu");
            LANGUAGE(BELARUSIAN, "be");
            LANGUAGE(BENGALI, "bn");
            LANGUAGE(BOSNIAN, "bs");
            LANGUAGE(BOSNIAN_NEUTRAL, "bs");
            LANGUAGE(BRETON, "br");
            LANGUAGE(BULGARIAN, "bg");
            LANGUAGE(CATALAN, "ca");
            LANGUAGE(CHINESE, "zh");
            LANGUAGE(CORSICAN, "co");
          //LANGUAGE(CROATIAN, "hr");
            LANGUAGE(CZECH, "cs");
            LANGUAGE(DANISH, "da");
            LANGUAGE(DARI, "gbz");
            LANGUAGE(DIVEHI, "dv");
            LANGUAGE(DUTCH, "nl");
            LANGUAGE(ENGLISH, "en");
            LANGUAGE(ESTONIAN, "et");
            LANGUAGE(FAEROESE, "fo");
            LANGUAGE(FILIPINO, "fil");
            LANGUAGE(FINNISH, "fi");
            LANGUAGE(FRENCH, "fr");
            LANGUAGE(FRISIAN, "fy");
            LANGUAGE(GALICIAN, "gl");
            LANGUAGE(GEORGIAN, "ka");
            LANGUAGE(GERMAN, "de");
            LANGUAGE(GREEK, "el");
            LANGUAGE(GREENLANDIC, "kl");
            LANGUAGE(GUJARATI, "gu");
            LANGUAGE(HAUSA, "ha");
            LANGUAGE(HEBREW, "he");
            LANGUAGE(HINDI, "hi");
            LANGUAGE(HUNGARIAN, "hu");
            LANGUAGE(ICELANDIC, "is");
            LANGUAGE(IGBO, "ig");
            LANGUAGE(INDONESIAN, "id");
            LANGUAGE(INUKTITUT, "iu");
            LANGUAGE(IRISH, "ga");
            LANGUAGE(ITALIAN, "it");
            LANGUAGE(JAPANESE, "ja");
            LANGUAGE(KANNADA, "kn");
            LANGUAGE(KASHMIRI, "ks");
            LANGUAGE(KAZAK, "kk");
            LANGUAGE(KHMER, "km");
            LANGUAGE(KICHE, "quc");
            LANGUAGE(KINYARWANDA, "rw");
            LANGUAGE(KONKANI, "kok");
            LANGUAGE(KOREAN, "ko");
            LANGUAGE(KYRGYZ, "ky");
            LANGUAGE(LAO, "lo");
            LANGUAGE(LATVIAN, "lv");
            LANGUAGE(LITHUANIAN, "lt");
            LANGUAGE(LOWER_SORBIAN, "dsb");
            LANGUAGE(LUXEMBOURGISH, "lb");
            LANGUAGE(MACEDONIAN, "mk");
#ifndef __MINGW64_VERSION_MAJOR
            LANGUAGE(MALAGASY, "mg");
#endif
            LANGUAGE(MALAY, "ms");
            LANGUAGE(MALAYALAM, "ml");
            LANGUAGE(MALTESE, "mt");
            LANGUAGE(MANIPURI, "mni");
            LANGUAGE(MAORI, "mi");
            LANGUAGE(MAPUDUNGUN, "arn");
            LANGUAGE(MARATHI, "mr");
            LANGUAGE(MOHAWK, "moh");
            LANGUAGE(MONGOLIAN, "mn");
            LANGUAGE(NEPALI, "ne");
            LANGUAGE(NORWEGIAN, "no");
            LANGUAGE(OCCITAN, "oc");
            LANGUAGE(ORIYA, "or");
            LANGUAGE(PASHTO, "ps");
            LANGUAGE(PERSIAN, "fa");
            LANGUAGE(POLISH, "pl");
            LANGUAGE(PORTUGUESE, "pt");
            LANGUAGE(PUNJABI, "pa");
            LANGUAGE(QUECHUA, "qu");
            LANGUAGE(ROMANIAN, "ro");
            LANGUAGE(RUSSIAN, "ru");
            LANGUAGE(SAMI, "se");
            LANGUAGE(SANSKRIT, "sa");
          //LANGUAGE(SERBIAN, "sr");
            LANGUAGE(SERBIAN_NEUTRAL, "sr");
            LANGUAGE(SINDHI, "sd");
            LANGUAGE(SINHALESE, "si");
            LANGUAGE(SLOVAK, "sk");
            LANGUAGE(SLOVENIAN, "sl");
            LANGUAGE(SOTHO, "st");
            LANGUAGE(SPANISH, "es");
            LANGUAGE(SWAHILI, "sw");
            LANGUAGE(SWEDISH, "sv");
            LANGUAGE(SYRIAC, "syr");
            LANGUAGE(TAMAZIGHT, "ber");
            LANGUAGE(TAMIL, "ta");
            LANGUAGE(TATAR, "tt");
            LANGUAGE(TELUGU, "te");
            LANGUAGE(THAI, "th");
            LANGUAGE(TIBETAN, "bo");
            LANGUAGE(TIGRIGNA, "ti");
            LANGUAGE(TSWANA, "tn");
            LANGUAGE(TURKISH, "tr");
            LANGUAGE(UIGHUR, "ug");
            LANGUAGE(UKRAINIAN, "uk");
          //LANGUAGE(UPPER_SORBIAN, "hsb");
            LANGUAGE(URDU, "ur");
            LANGUAGE(UZBEK, "uz");
            LANGUAGE(VIETNAMESE, "vi");
            LANGUAGE(WELSH, "cy");
            LANGUAGE(WOLOF, "fy");
            LANGUAGE(XHOSA, "xh");
            LANGUAGE(YAKUT, "sah");
            LANGUAGE(YI, "ii");
            LANGUAGE(YORUBA, "yo");
            LANGUAGE(ZULU, "zu");
#undef LANGUAGE

            default:
              name = NULL;
              break;
          }
          break;
      }

      if (name) {
        if ((name = strdup(name))) {
          return name;
        } else {
          logMallocError();
        }
      }
    } else {
      logWindowsSystemError("GetLocaleInfo");
    }
  }

  return NULL;
}

#if (__MINGW32_MAJOR_VERSION < 3) || ((__MINGW32_MAJOR_VERSION == 3) && (__MINGW32_MINOR_VERSION < 10))
int
gettimeofday (struct timeval *tvp, void *tzp) {
  DWORD time = GetTickCount();
  /* this is not 49.7 days-proof ! */
  tvp->tv_sec = time / 1000;
  tvp->tv_usec = (time % 1000) * 1000;
  return 0;
}
#endif /* gettimeofday() */

#if !defined(__MINGW64_VERSION_MAJOR) && ((__MINGW32_MAJOR_VERSION < 3) || ((__MINGW32_MAJOR_VERSION == 3) && (__MINGW32_MINOR_VERSION < 15)))
void
usleep (int usec) {
  if (usec > 0) {
    approximateDelay((usec + (USECS_PER_MSEC - 1)) / USECS_PER_MSEC);
  }
}
#endif /* usleep() */
#endif /* __MINGW32__ */
