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

#ifndef BRLTTY_INCLUDED_CHARSET
#define BRLTTY_INCLUDED_CHARSET

#include "utf8.h"
#include "lock.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int isCharsetLatin1 (const char *name);

extern char *getLocaleName (void);
extern int isPosixLocale (const char *locale);

extern const char *setCharset (const char *name);
extern const char *getCharset (void);

extern const char *getLocaleCharset (void);
extern const char *getWcharCharset (void);

extern size_t convertCharToUtf8 (char c, Utf8Buffer utf8);
extern int convertUtf8ToChar (const char **utf8, size_t *utfs);

extern wint_t convertCharToWchar (char c);
extern int convertWcharToChar (wchar_t wc);

extern int lockCharset (LockOptions options);
extern void unlockCharset (void);

typedef char *PathMaker (const char *directory, const char *name);
extern char *getFileForLocale (const char *directory, PathMaker *pathMaker);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CHARSET */
