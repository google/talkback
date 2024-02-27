/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2023 by The BRLTTY Developers.
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

#include <stdio.h>
#include <string.h>
#include <locale.h>

#include "log.h"
#include "charset_internal.h"

#if defined(CP_THREAD_ACP)
#define CHARSET_WINDOWS_CODEPAGE CP_THREAD_ACP
#else /* Windows codepage */
#define CHARSET_WINDOWS_CODEPAGE CP_ACP
#endif /* Windows codepage */

wint_t
convertCharToWchar (char c) {
  wchar_t wc;
  int result = MultiByteToWideChar(CHARSET_WINDOWS_CODEPAGE, MB_ERR_INVALID_CHARS,
                                   &c, 1,  &wc, 1);
  if (result) return wc;
  logWindowsSystemError("MultiByteToWideChar[" STRINGIFY(CHARSET_WINDOWS_CODEPAGE) "]");
  return WEOF;
}

int
convertWcharToChar (wchar_t wc) {
  char c;
  int result = WideCharToMultiByte(CHARSET_WINDOWS_CODEPAGE, WC_NO_BEST_FIT_CHARS /* WC_ERR_INVALID_CHARS */,
                                   &wc, 1, &c, 1,
                                   NULL, NULL);
  if (result) return c & 0XFF;
  logWindowsSystemError("WideCharToMultiByte[" STRINGIFY(CHARSET_WINDOWS_CODEPAGE) "]");
  return EOF;
}

const char *
getLocaleCharset (void) {
  const char *locale = setlocale(LC_ALL, "");

  if (locale && !isPosixLocale(locale)) {
    /* some 8-bit locale is set, assume its charset is correct */
    static char codepage[8] = {'C', 'P'};

    GetLocaleInfo(GetThreadLocale(), LOCALE_IDEFAULTANSICODEPAGE, codepage+2, sizeof(codepage)-2);
    return codepage;
  }

  return defaultCharset;
}

int
registerCharacterSet (const char *charset) {
  return 1;
}
