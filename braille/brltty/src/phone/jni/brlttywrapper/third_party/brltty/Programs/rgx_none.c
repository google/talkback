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
  return NULL;
}

void
rgxDeallocateCode (RGX_CodeType *code) {
}

RGX_DataType *
rgxAllocateData (RGX_CodeType *code) {
  return NULL;
}

void
rgxDeallocateData (RGX_DataType *data) {
}

int
rgxMatchText (
  const RGX_CharacterType *characters, size_t length,
  RGX_CodeType *code, RGX_DataType *data,
  RGX_OptionsType options, size_t *count, int *error
) {
  *error = RGX_NO_MATCH;
  return 0;
}

int
rgxNameNumber (
  RGX_CodeType *code, const RGX_CharacterType *name,
  size_t *number, int *error
) {
  *error = RGX_NO_NAME;
  return 0;
}

int
rgxCaptureBounds (
  RGX_DataType *data, size_t number, size_t *from, size_t *to
) {
  return 0;
}

STR_BEGIN_FORMATTER(rgxFormatErrorMessage, int error)
  switch (error) {
    case RGX_NO_MATCH:
      STR_PRINTF("no match");
      break;
  }
STR_END_FORMATTER

RGX_BEGIN_OPTION_MAP(rgxCompileOptions)
RGX_END_OPTION_MAP(rgxCompileOptions)

RGX_BEGIN_OPTION_MAP(rgxMatchOptions)
RGX_END_OPTION_MAP(rgxMatchOptions)
