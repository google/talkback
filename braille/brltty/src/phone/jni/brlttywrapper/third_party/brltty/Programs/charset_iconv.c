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

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <iconv.h>
#include <locale.h>

#ifdef HAVE_LANGINFO_H
#include <langinfo.h>
#endif /* HAVE_LANGINFO_H */

#include "log.h"
#include "charset_internal.h"
#include "program.h"

#define CHARSET_ICONV_NULL ((iconv_t)-1)
#define CHARSET_ICONV_HANDLE(name) iconv_t iconv##name = CHARSET_ICONV_NULL

static CHARSET_ICONV_HANDLE(CharToWchar);
static CHARSET_ICONV_HANDLE(WcharToChar);

#define CHARSET_CONVERT_TYPE_TO_TYPE(name, from, to, ret, eof) \
ret convert##name (from f) { \
  if (getCharset()) { \
    from *fp = &f; \
    size_t fs = sizeof(f); \
    to t; \
    to *tp = &t; \
    size_t ts = sizeof(t); \
    if (iconv(iconv##name, (void *)&fp, &fs, (void *)&tp, &ts) != (size_t)-1) return t; \
    logMessage(LOG_DEBUG, "iconv (" #from " -> " #to ") error: %s", strerror(errno)); \
  } \
  return eof; \
}
CHARSET_CONVERT_TYPE_TO_TYPE(CharToWchar, char, wchar_t, wint_t, WEOF)
CHARSET_CONVERT_TYPE_TO_TYPE(WcharToChar, wchar_t, unsigned char, int, EOF)
#undef CHARSET_CONVERT_TYPE_TO_TYPE

const char *
getLocaleCharset (void) {
  const char *locale = setlocale(LC_ALL, "");

  if (locale && !isPosixLocale(locale)) {
#ifdef HAVE_NL_LANGINFO
    /* some 8-bit locale is set, assume its charset is correct */
    return nl_langinfo(CODESET);
#endif /* HAVE_NL_LANGINFO */
  }

  return defaultCharset;
}

static void
exitCharsetIconv (void *data) {
  static iconv_t *const handles[] = {
    &iconvCharToWchar,
    &iconvWcharToChar
  };

  iconv_t *const *handle = handles;
  iconv_t *const *const end = handle + ARRAY_COUNT(handles);

  while (handle < end) {
    if (**handle != CHARSET_ICONV_NULL) {
      iconv_close(**handle);
      **handle = CHARSET_ICONV_NULL;
    }

    handle += 1;
  }
}

int
registerCharacterSet (const char *charset) {
  int firstTime = 0;
  const char *const wcharCharset = getWcharCharset();

  typedef struct {
    iconv_t *const handle;
    const char *const fromCharset;
    const char *const toCharset;

    iconv_t newHandle;
  } ConvEntry;

  ConvEntry convTable[] = {
    { .handle = &iconvCharToWchar,
      .fromCharset = charset,
      .toCharset = wcharCharset
    },

    { .handle = &iconvWcharToChar,
      .fromCharset = wcharCharset,
      .toCharset = charset
    },
  };

  ConvEntry *conv = convTable;
  const ConvEntry *convEnd = conv + ARRAY_COUNT(convTable);

  while (conv < convEnd) {
    if ((conv->newHandle = iconv_open(conv->toCharset, conv->fromCharset)) == CHARSET_ICONV_NULL) {
      logSystemError("iconv_open");

      while (conv > convTable) {
        conv -= 1;
        iconv_close(conv->newHandle);
      }

      return 0;
    }

    conv += 1;
  }

  while (conv > convTable) {
    conv -= 1;

    if (*conv->handle == CHARSET_ICONV_NULL) {
      firstTime = 1;
    } else {
      iconv_close(*conv->handle);
    }

    *conv->handle = conv->newHandle;
  }

  if (firstTime) onProgramExit("charset-iconv", exitCharsetIconv, NULL);
  return 1;
}
