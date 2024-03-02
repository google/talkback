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

#ifndef BRLTTY_INCLUDED_STRFMT
#define BRLTTY_INCLUDED_STRFMT

#include <stdio.h>

#include "strfmth.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define STR_BEGIN(buffer, size) { \
char *strNext = (buffer); \
const char *const strStart = strNext; \
const char *const strEnd = strStart + (size); \
const char *const strLast = strEnd - 1; \
*strNext = 0;

#define STR_END }

#define STR_LENGTH (size_t)(strNext - strStart)

#define STR_NEXT strNext

#define STR_LEFT (size_t)(strEnd - strNext)

#define STR_POP() ((strNext > strStart)? --strNext: NULL)

#define STR_ADJUST(length) \
do { if ((strNext += (length)) > strLast) strNext = (char *)strLast; } while (0)

#define STR_BEGIN_FORMATTER(name, ...) \
STR_DECLARE_FORMATTER(name, __VA_ARGS__) { \
  size_t strFormatterResult; \
  STR_BEGIN(strFormatterBuffer, strFormatterSize);

#define STR_END_FORMATTER \
  strFormatterResult = STR_LENGTH; \
  STR_END; \
  return strFormatterResult; \
}

#define STR_FORMAT(formatter, ...) \
STR_ADJUST(formatter(STR_NEXT, STR_LEFT, __VA_ARGS__))

#define STR_PRINTF(...) \
STR_FORMAT(snprintf, __VA_ARGS__)

#define STR_VPRINTF(format, arguments) \
STR_FORMAT(vsnprintf, format, arguments)

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_STRFMT */
