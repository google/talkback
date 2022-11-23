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

#ifndef BRLTTY_INCLUDED_CHARSET
#define BRLTTY_INCLUDED_CHARSET

#include <stdio.h>

#include "lock.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern char *getLocaleName (void);
extern int isPosixLocale (const char *locale);

extern const char *setCharset (const char *name);
extern const char *getCharset (void);

extern const char *getLocaleCharset (void);
extern const char *getWcharCharset (void);

#define UTF8_SIZE(bits) (((bits) < 8)? 1: (((bits) + 3) / 5))
#define UTF8_LEN_MAX UTF8_SIZE(32)
typedef char Utf8Buffer[UTF8_LEN_MAX + 1];

extern size_t convertCharToUtf8 (char c, Utf8Buffer utf8);
extern int convertUtf8ToChar (const char **utf8, size_t *utfs);

extern size_t convertWcharToUtf8 (wchar_t wc, Utf8Buffer utf8);
extern wint_t convertUtf8ToWchar (const char **utf8, size_t *utfs);

extern size_t getUtf8Length (const char *utf8);
extern void convertUtf8ToWchars (const char **utf8, wchar_t **characters, size_t count);
extern char *makeUtf8FromWchars (const wchar_t *characters, unsigned int count, size_t *length);

extern wint_t convertCharToWchar (char c);
extern int convertWcharToChar (wchar_t wc);

extern size_t convertTextToWchars (wchar_t *characters, const char *text, size_t size);
extern size_t getTextLength (const char *text);

extern int lockCharset (LockOptions options);
extern void unlockCharset (void);

extern int writeUtf8Character (FILE *stream, wchar_t character);
extern int writeUtf8Characters (FILE *stream, const wchar_t *characters, size_t count);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CHARSET */
