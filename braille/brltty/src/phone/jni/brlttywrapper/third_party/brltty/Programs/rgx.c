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
#include "charset.h"
#include "queue.h"
#include "strfmt.h"

#define RGX_UTF8_TO_CHARACTERS \
  size_t size = strlen(string) + 1; \
  wchar_t characters[size]; \
  size_t count; \
  { \
    const char *from = string; \
    wchar_t *to = characters; \
    convertUtf8ToWchars(&from, &to, size); \
    count = to - characters; \
  }

#define RGX_CHARACTERS_TO_INTERNAL \
  RGX_CharacterType internal[length + 1]; \
  internal[length] = 0; \
  for (unsigned int index=0; index<length; index+=1) { \
    internal[index] = characters[index]; \
  }

struct RGX_ObjectStruct {
  void *data;
  Queue *matchers;
  RGX_OptionsType options;
};

struct RGX_MatcherStruct {
  void *data;
  RGX_MatchHandler *handler;
  RGX_OptionsType options;

  struct {
    wchar_t *characters;
    size_t length;
  } pattern;

  struct {
    RGX_CodeType *code;
    RGX_DataType *data;
  } compiled;
};

static void
rgxLogError (int error, const RGX_Matcher *matcher, RGX_OffsetType *offset) {
  char log[0X100];
  STR_BEGIN(log, sizeof(log));

  STR_PRINTF("regular expression error");
  if (offset) STR_PRINTF(" at offset %"PRIu32, (uint32_t)*offset);
  STR_PRINTF(": ");

  {
    size_t left = STR_LEFT;
    STR_FORMAT(rgxFormatErrorMessage, error);
    if (STR_LEFT == left) STR_PRINTF("unrecognized error %d", error);
  }

  if (matcher) {
    STR_PRINTF(
      ": %.*"PRIws, (int)matcher->pattern.length, matcher->pattern.characters
    );
  }

  STR_END;
  logMessage(LOG_WARNING, "%s", log);
}

static void
rgxDeallocateMatcher (void *item, void *data) {
  RGX_Matcher *matcher = item;

  rgxDeallocateData(matcher->compiled.data);
  rgxDeallocateCode(matcher->compiled.code);
  free(matcher->pattern.characters);
  free(matcher);
}

RGX_Matcher *
rgxAddPatternCharacters (
  RGX_Object *rgx,
  const wchar_t *characters, size_t length,
  RGX_MatchHandler *handler, void *data
) {
  RGX_Matcher *matcher;

  if ((matcher = malloc(sizeof(*matcher)))) {
    memset(matcher, 0, sizeof(*matcher));
    matcher->data = data;
    matcher->handler = handler;
    matcher->options = 0;

    matcher->pattern.characters = calloc(
      (matcher->pattern.length = length) + 1,
      sizeof(*matcher->pattern.characters)
    );

    if (matcher->pattern.characters) {
      wmemcpy(matcher->pattern.characters, characters, length);
      matcher->pattern.characters[length] = 0;

      RGX_CHARACTERS_TO_INTERNAL;
      int error;
      RGX_OffsetType offset;

      matcher->compiled.code = rgxCompilePattern(
        internal, length, rgx->options, &offset, &error
      );

      if (matcher->compiled.code) {
        matcher->compiled.data = rgxAllocateData(matcher->compiled.code);

        if (matcher->compiled.data) {
          if (enqueueItem(rgx->matchers, matcher)) {
            return matcher;
          }

          rgxDeallocateData(matcher->compiled.data);
        } else {
          logMallocError();
        }

        rgxDeallocateCode(matcher->compiled.code);
      } else {
        rgxLogError(error, matcher, &offset);
      }

      free(matcher->pattern.characters);
    } else {
      logMallocError();
    }

    free(matcher);
  } else {
    logMallocError();
  }

  return NULL;
}

RGX_Matcher *
rgxAddPatternString (
  RGX_Object *rgx,
  const wchar_t *string,
  RGX_MatchHandler *handler, void *data
) {
  return rgxAddPatternCharacters(rgx, string, wcslen(string), handler, data);
}

RGX_Matcher *
rgxAddPatternUTF8 (
  RGX_Object *rgx,
  const char *string,
  RGX_MatchHandler *handler, void *data
) {
  RGX_UTF8_TO_CHARACTERS;
  return rgxAddPatternCharacters(rgx, characters, count, handler, data);
}

static int
rgxTestMatcher (const void *item, void *data) {
  const RGX_Matcher *matcher = item;
  RGX_Match *match = data;

  int error;
  int matched = rgxMatchText(
    match->text.internal, match->text.length,
    matcher->compiled.code, matcher->compiled.data,
    matcher->options, &match->capture.count, &error
  );

  if (!matched) {
    if (error != RGX_NO_MATCH) rgxLogError(error, matcher, NULL);
    return 0;
  }

  RGX_MatchHandler *handler = matcher->handler;
  if (!handler) return 1;

  match->matcher = matcher;
  match->data.pattern = matcher->data;
  match->pattern.characters = matcher->pattern.characters;
  match->pattern.length = matcher->pattern.length;
  return handler(match);
}

RGX_Matcher *
rgxMatchTextCharacters (
  RGX_Object *rgx,
  const wchar_t *characters, size_t length,
  RGX_Match **result, void *data
) {
  RGX_CHARACTERS_TO_INTERNAL;

  RGX_Match match = {
    .text = {
      .internal = internal,
      .characters = characters,
      .length = length
    },

    .data = {
      .object = rgx->data,
      .match = data
    }
  };

  Element *element = findElement(rgx->matchers, rgxTestMatcher, &match);
  if (!element) return NULL;

  if (result) {
    typedef struct {
      RGX_Match match;
      wchar_t text[];
    } Block;

    Block *block;
    size_t size = sizeof(*block);
    size += (length + 1) * sizeof(block->text[0]);

    if (!(block = malloc(size))) {
      logMallocError();
      return NULL;
    }

    block->match = match;
    block->match.text.internal = NULL;

    wmemcpy(block->text, match.text.characters, length);
    block->text[length] = 0;
    block->match.text.characters = block->text;

    *result = &block->match;
  }

  return getElementItem(element);
}

RGX_Matcher *
rgxMatchTextString (
  RGX_Object *rgx,
  const wchar_t *string,
  RGX_Match **result, void *data
) {
  return rgxMatchTextCharacters(rgx, string, wcslen(string), result, data);
}

RGX_Matcher *
rgxMatchTextUTF8 (
  RGX_Object *rgx,
  const char *string,
  RGX_Match **result, void *data
) {
  RGX_UTF8_TO_CHARACTERS;
  return rgxMatchTextCharacters(rgx, characters, count, result, data);
}

int
rgxGetNameNumberCharacters (
  const RGX_Matcher *matcher,
  const wchar_t *characters, size_t length,
  size_t *number
) {
  RGX_CHARACTERS_TO_INTERNAL;

  int error;
  if (rgxNameNumber(matcher->compiled.code, internal, number, &error)) return 1;

  if (error != RGX_NO_NAME) rgxLogError(error, matcher, NULL);
  return 0;
}

int
rgxGetNameNumberString (
  const RGX_Matcher *matcher,
  const wchar_t *string,
  size_t *number
) {
  return rgxGetNameNumberCharacters(matcher, string, wcslen(string), number);
}

int
rgxGetNameNumberUTF8 (
  const RGX_Matcher *matcher,
  const char *string,
  size_t *number
) {
  RGX_UTF8_TO_CHARACTERS;
  return rgxGetNameNumberCharacters(matcher, characters, count, number);
}

size_t
rgxGetCaptureCount (
  const RGX_Match *match
) {
  return match->capture.count;
}

int
rgxGetCaptureBounds (
  const RGX_Match *match,
  size_t number, size_t *from, size_t *to
) {
  if (number > match->capture.count) return 0;
  return rgxCaptureBounds(match->matcher->compiled.data, number, from, to);
}

int
rgxGetCaptureText (
  const RGX_Match *match,
  size_t number, const wchar_t **characters, size_t *length
) {
  size_t from, to;
  if (!rgxGetCaptureBounds(match, number, &from, &to)) return 0;

  *characters = &match->text.characters[from];
  *length = to - from;
  return 1;
}

RGX_Object *
rgxNewObject (void *data) {
  RGX_Object *rgx;

  if ((rgx = malloc(sizeof(*rgx)))) {
    memset(rgx, 0, sizeof(*rgx));
    rgx->data = data;
    rgx->options = 0;

    if ((rgx->matchers = newQueue(rgxDeallocateMatcher, NULL))) {
      return rgx;
    }

    free(rgx);
  } else {
    logMallocError();
  }

  return NULL;
}

void
rgxDestroyObject (RGX_Object *rgx) {
  deallocateQueue(rgx->matchers);
  free(rgx);
}

static int
rgxOption (
  RGX_OptionAction action, RGX_CompileOption option,
  RGX_OptionsType *bits, const RGX_OptionMap *map
) {
  RGX_OptionsType bit = ((option >= 0) && (option < map->count))? map->array[option]: 0;
  int wasSet = !!(*bits & bit);

  if (action == RGX_OPTION_TOGGLE) {
    action = wasSet? RGX_OPTION_CLEAR: RGX_OPTION_SET;
  }

  switch (action) {
    case RGX_OPTION_SET:
      *bits |= bit;
      break;

    case RGX_OPTION_CLEAR:
      *bits &= ~bit;
      break;

    default:
      logMessage(LOG_WARNING, "unimplemented regular expression option action: %d", action);
      /* fall through */
    case RGX_OPTION_TEST:
      break;
  }

  return wasSet;
}

int
rgxCompileOption (
  RGX_Object *rgx,
  RGX_OptionAction action,
  RGX_CompileOption option
) {
  return rgxOption(action, option, &rgx->options, &rgxCompileOptionsMap);
}

int
rgxMatchOption (
  RGX_Matcher *matcher,
  RGX_OptionAction action,
  RGX_MatchOption option
) {
  return rgxOption(action, option, &matcher->options, &rgxMatchOptionsMap);
}
