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

#include <string.h>

#include "log.h"
#include "rgx.h"
#include "rgx_internal.h"
#include "strfmt.h"

static int savedErrorCode = 0;
static const char *savedErrorMessage = NULL;

static void
saveErrorMessage (int error, const char *message) {
  if (message && *message) {
    savedErrorCode = error;
    savedErrorMessage = message;
  }
}

static const char *
getErrorMessage (int error) {
  if (error == savedErrorCode) return savedErrorMessage;
  return NULL;
}

RGX_CodeType *
rgxCompilePattern (
  const RGX_CharacterType *characters, size_t length,
  RGX_OptionsType options, RGX_OffsetType *offset,
  int *error
) {
  const char *message;

  RGX_CodeType *code = pcre32_compile2(
    characters, options, error, &message, offset, NULL
  );

  if (!code) saveErrorMessage(*error, message);
  return code;
}

void
rgxDeallocateCode (RGX_CodeType *code) {
  pcre32_free(code);
}

RGX_DataType *
rgxAllocateData (RGX_CodeType *code) {
  RGX_DataType *data;
  size_t size = sizeof(*data);

  size_t matches = 10;
  size_t count = matches * 3;
  size += count * sizeof(data->offsets[0]);

  if (!(data = malloc(size))) return NULL;
  memset(data, 0, size);

  data->matches = matches;
  data->count = count;

  {
    const char *message = NULL;
    data->study = pcre32_study(code, 0, &message);

    if (message) {
      logMessage(LOG_WARNING, "pcre study error: %s", message);

      if (data->study) {
        pcre32_free_study(data->study);
        data->study = NULL;
      }
    }
  }

  return data;
}

void
rgxDeallocateData (RGX_DataType *data) {
  if (data->study) pcre32_free_study(data->study);
  free(data);
}

int
rgxMatchText (
  const RGX_CharacterType *characters, size_t length,
  RGX_CodeType *code, RGX_DataType *data,
  RGX_OptionsType options, size_t *count, int *error
) {
  int result = pcre32_exec(
    code, data->study,
    characters, length,
    0, options,
    data->offsets, data->count
  );

  if (result < 0) {
    *error = result;
    return 0;
  }

  if (!result) result = data->matches;
  *count = result - 1;
  return 1;
}

int
rgxNameNumber (
  RGX_CodeType *code, const RGX_CharacterType *name,
  size_t *number, int *error
) {
  int result = pcre32_get_stringnumber(code, name);

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
  const RGX_OffsetType *offsets = data->offsets;
  offsets += number * 2;

  if (offsets[0] == -1) return 0;
  if (offsets[1] == -1) return 0;

  *from = offsets[0];
  *to = offsets[1];
  return 1;
}

static const char *const rgxNegativeErrors[] = {
  [0] = "no error",
   [-PCRE_ERROR_NOMATCH] = "no match",
   [-PCRE_ERROR_NULL] = "required pointer argument is null",
   [-PCRE_ERROR_BADOPTION] = "unrecognized option",
   [-PCRE_ERROR_BADMAGIC] = "magic number not found",
   [-PCRE_ERROR_UNKNOWN_OPCODE] = "invalid item in compiled pattern",
   [-PCRE_ERROR_NOMEMORY] = "insufficient memory",
   [-PCRE_ERROR_NOSUBSTRING] = "no capture with specified number or name",
   [-PCRE_ERROR_MATCHLIMIT] = "match limit exceeded",
   [-PCRE_ERROR_CALLOUT] = "error in callout",
   [-PCRE_ERROR_BADUTF32] = "invalid UTF-32 character",
   [-PCRE_ERROR_BADUTF16_OFFSET] = "start offset is within a multibyte character",
   [-PCRE_ERROR_PARTIAL] = "partial match",
   [-PCRE_ERROR_BADPARTIAL] = "pattern contains item not supported for partial match",
   [-PCRE_ERROR_INTERNAL] = "internal error",
   [-PCRE_ERROR_BADCOUNT] = "size of offsets vector is negative",
   [-PCRE_ERROR_DFA_UITEM] = "pattern contains item not supported for DFA match",
   [-PCRE_ERROR_DFA_UCOND] = "DFA match uses back reference for condition or test for recursion in specific group",
   [-PCRE_ERROR_DFA_UMLIMIT] = "match or recursion limit specified for DFA match",
   [-PCRE_ERROR_DFA_WSSIZE] = "DFA workspace overflow",
   [-PCRE_ERROR_DFA_RECURSE] = "DFA recursion offsets vector too small",
   [-PCRE_ERROR_RECURSIONLIMIT] = "recursion limit exceeded",
   [-PCRE_ERROR_NULLWSLIMIT] = "",
   [-PCRE_ERROR_BADNEWLINE] = "invalid newline option combination",
   [-PCRE_ERROR_BADOFFSET] = "start offset out of bounds",
   [-PCRE_ERROR_SHORTUTF16] = "truncated multibyte character",
   [-PCRE_ERROR_RECURSELOOP] = "recursion loop detected",
   [-PCRE_ERROR_JIT_STACKLIMIT] = "JIT stack too small",
   [-PCRE_ERROR_BADMODE] = "pattern compiled for different character size",
   [-PCRE_ERROR_BADENDIANNESS] = "pattern compiled for different host endianness",
   [-PCRE_ERROR_DFA_BADRESTART] = "unable to resume partial DFA match",
   [-PCRE_ERROR_JIT_BADOPTION] = "invalid JIT option",
   [-PCRE_ERROR_BADLENGTH] = "text length is negative",
   [-PCRE_ERROR_UNSET] = "required value not set",
};

STR_BEGIN_FORMATTER(rgxFormatErrorMessage, int error)
  const char *message = getErrorMessage(error);

  if (!message) {
    if (error <= 0) {
      if ((error = -error) < ARRAY_COUNT(rgxNegativeErrors)) {
        message = rgxNegativeErrors[error];
      }
    }
  }

  if (message && *message) STR_PRINTF("%s", message);
STR_END_FORMATTER

RGX_BEGIN_OPTION_MAP(rgxCompileOptions)
  [RGX_COMPILE_ANCHOR_START] = PCRE_ANCHORED,

  [RGX_COMPILE_IGNORE_CASE] = PCRE_CASELESS,
  [RGX_COMPILE_UNICODE_PROPERTIES] = PCRE_UCP,
RGX_END_OPTION_MAP(rgxCompileOptions)

RGX_BEGIN_OPTION_MAP(rgxMatchOptions)
  [RGX_MATCH_ANCHOR_START] = PCRE_ANCHORED,
RGX_END_OPTION_MAP(rgxMatchOptions)
