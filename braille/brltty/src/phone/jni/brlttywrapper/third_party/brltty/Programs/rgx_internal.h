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

#ifndef BRLTTY_INCLUDED_RGX_INTERNAL
#define BRLTTY_INCLUDED_RGX_INTERNAL

#include "strfmth.h"

#if defined(USE_PKG_RGX_NONE)
#define RGX_NO_MATCH 1
#define RGX_NO_NAME 2

typedef wchar_t RGX_CharacterType;
typedef size_t RGX_OffsetType;
typedef int RGX_OptionsType;
typedef uint8_t RGX_CodeType;
typedef uint8_t RGX_DataType;

#elif defined(USE_PKG_RGX_LIBPCRE32)
#include <pcre.h>

#define RGX_NO_MATCH PCRE_ERROR_NOMATCH
#define RGX_NO_NAME PCRE_ERROR_NOSUBSTRING

typedef PCRE_UCHAR32 RGX_CharacterType;
typedef int RGX_OffsetType;
typedef int RGX_OptionsType;
typedef pcre32 RGX_CodeType;

typedef struct {
  pcre32_extra *study;
  size_t matches;
  size_t count;
  RGX_OffsetType offsets[];
} RGX_DataType;

#elif defined(USE_PKG_RGX_LIBPCRE2_32)
#define PCRE2_CODE_UNIT_WIDTH 32
#include <pcre2.h>

#define RGX_NO_MATCH PCRE2_ERROR_NOMATCH
#define RGX_NO_NAME PCRE2_ERROR_NOSUBSTRING

typedef PCRE2_UCHAR RGX_CharacterType;
typedef PCRE2_SIZE RGX_OffsetType;
typedef uint32_t RGX_OptionsType;
typedef pcre2_code RGX_CodeType;
typedef pcre2_match_data RGX_DataType;

#else /* regular expression package */
#error regular expression package not selected
#endif /* regular expression package */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern RGX_CodeType *rgxCompilePattern (
  const RGX_CharacterType *characters, size_t length,
  RGX_OptionsType options, RGX_OffsetType *offset,
  int *error
);

extern void rgxDeallocateCode (RGX_CodeType *code);
extern RGX_DataType *rgxAllocateData (RGX_CodeType *code);
extern void rgxDeallocateData (RGX_DataType *data);

extern int rgxMatchText (
  const RGX_CharacterType *characters, size_t length,
  RGX_CodeType *code, RGX_DataType *data,
  RGX_OptionsType options, size_t *count, int *error
);

extern int rgxNameNumber (
  RGX_CodeType *code, const RGX_CharacterType *name,
  size_t *number, int *error
);

extern int rgxCaptureBounds (
  RGX_DataType *data, size_t number, size_t *from, size_t *to
);

extern STR_DECLARE_FORMATTER(rgxFormatErrorMessage, int error);

typedef struct {
  const RGX_OptionsType *array;
  uint8_t count;
} RGX_OptionMap;

extern const RGX_OptionMap rgxCompileOptionsMap;
extern const RGX_OptionMap rgxMatchOptionsMap;

#define RGX_BEGIN_OPTION_MAP(name) static const RGX_OptionsType name##Array[] = {
#define RGX_END_OPTION_MAP(name) }; \
  const RGX_OptionMap name##Map = { \
  .array = name##Array, \
  .count = ARRAY_COUNT(name##Array) \
};

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_RGX_INTERNAL */
