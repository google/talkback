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

#include "rgx.h"
#include "rgx_internal.h"
#include "strfmt.h"

RGX_CodeType *
rgxCompilePattern (
  const RGX_CharacterType *characters, size_t length,
  RGX_OptionsType options, RGX_OffsetType *offset,
  int *error
) {
  return pcre2_compile(
    characters, length, options, error, offset, NULL
  );
}

void
rgxDeallocateCode (RGX_CodeType *code) {
  pcre2_code_free(code);
}

RGX_DataType *
rgxAllocateData (RGX_CodeType *code) {
  return pcre2_match_data_create_from_pattern(code, NULL);
}

void
rgxDeallocateData (RGX_DataType *data) {
  pcre2_match_data_free(data);
}

int
rgxMatchText (
  const RGX_CharacterType *characters, size_t length,
  RGX_CodeType *code, RGX_DataType *data,
  RGX_OptionsType options, size_t *count, int *error
) {
  int result = pcre2_match(
    code, characters, length, 0, options, data, NULL
  );

  if (result < 0) {
    *error = result;
    return 0;
  }

  if (!result) result = pcre2_get_ovector_count(data);
  *count = result - 1;
  return 1;
}

int
rgxNameNumber (
  RGX_CodeType *code, const RGX_CharacterType *name,
  size_t *number, int *error
) {
  int result = pcre2_substring_number_from_name(code, name);

  if (result > 0) {
    *number = result;
    return 1;
  } else {
    *error = result;
    return 0;
  }
}

int
rgxCaptureBounds (
  RGX_DataType *data, size_t number, size_t *from, size_t *to
) {
  const RGX_OffsetType *offsets = pcre2_get_ovector_pointer(data);
  offsets += number * 2;

  if (offsets[0] == PCRE2_UNSET) return 0;
  if (offsets[1] == PCRE2_UNSET) return 0;

  *from = offsets[0];
  *to = offsets[1];
  return 1;
}

STR_BEGIN_FORMATTER(rgxFormatErrorMessage, int error)
  size_t size = STR_LEFT;
  RGX_CharacterType message[size];
  int length = pcre2_get_error_message(error, message, size);

  if (length > 0) {
    for (unsigned int index=0; index<length; index+=1) {
      STR_PRINTF("%"PRIwc, message[index]);
    }
  }
STR_END_FORMATTER

RGX_BEGIN_OPTION_MAP(rgxCompileOptions)
  [RGX_COMPILE_ANCHOR_START] = PCRE2_ANCHORED,

  [RGX_COMPILE_IGNORE_CASE] = PCRE2_CASELESS,
  [RGX_COMPILE_UNICODE_PROPERTIES] = PCRE2_UCP,
RGX_END_OPTION_MAP(rgxCompileOptions)

RGX_BEGIN_OPTION_MAP(rgxMatchOptions)
  [RGX_MATCH_ANCHOR_START] = PCRE2_ANCHORED,
RGX_END_OPTION_MAP(rgxMatchOptions)
