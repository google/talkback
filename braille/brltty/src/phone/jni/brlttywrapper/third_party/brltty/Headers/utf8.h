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

#ifndef BRLTTY_INCLUDED_UTF8
#define BRLTTY_INCLUDED_UTF8

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

wchar_t *allocateCharacters (size_t count);

#define UTF8_SIZE(bits) (((bits) < 8)? 1: (((bits) + 3) / 5))
#define UTF8_LEN_MAX UTF8_SIZE(32)
typedef char Utf8Buffer[UTF8_LEN_MAX + 1];

extern size_t convertCodepointToUtf8 (uint32_t codepoint, Utf8Buffer utf8);
extern int convertUtf8ToCodepoint (uint32_t *codepoint, const char **utf8, size_t *utfs);

extern size_t convertWcharToUtf8 (wchar_t wc, Utf8Buffer utf8);
extern wint_t convertUtf8ToWchar (const char **utf8, size_t *utfs);

extern void convertUtf8ToWchars (const char **utf8, wchar_t **characters, size_t count);

extern size_t makeUtf8FromWchars (const wchar_t *characters, unsigned int count, char *buffer, size_t size);
extern char *getUtf8FromWchars (const wchar_t *characters, unsigned int count, size_t *length);

extern size_t makeWcharsFromUtf8 (const char *text, wchar_t *characters, size_t size);
extern size_t countUtf8Characters (const char *text);

extern int writeUtf8Character (FILE *stream, wchar_t character);
extern int writeUtf8Characters (FILE *stream, const wchar_t *characters, size_t count);
extern int writeUtf8ByteOrderMark (FILE *stream);

extern int isCharsetUTF8 (const char *name);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_UTF8 */
