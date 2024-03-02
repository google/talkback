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

#ifndef BRLTTY_INCLUDED_RGX
#define BRLTTY_INCLUDED_RGX

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct RGX_ObjectStruct RGX_Object;
typedef struct RGX_MatcherStruct RGX_Matcher;

extern RGX_Object *rgxNewObject (void *data);
extern void rgxDestroyObject (RGX_Object *rgx);

typedef struct {
  const RGX_Matcher *matcher;

  struct {
    const wchar_t *characters;
    size_t length;
  } pattern;

  struct {
    void *internal;
    const wchar_t *characters;
    size_t length;
  } text;

  struct {
    size_t count;
  } capture;

  struct {
    void *object;
    void *pattern;
    void *match;
  } data;
} RGX_Match;

#define RGX_MATCH_HANDLER(name) int name (const RGX_Match *match)
typedef RGX_MATCH_HANDLER(RGX_MatchHandler);

extern RGX_Matcher *rgxAddPatternCharacters (
  RGX_Object *rgx,
  const wchar_t *characters, size_t length,
  RGX_MatchHandler *handler, void *data
);

extern RGX_Matcher *rgxAddPatternString (
  RGX_Object *rgx,
  const wchar_t *string,
  RGX_MatchHandler *handler, void *data
);

extern RGX_Matcher *rgxAddPatternUTF8 (
  RGX_Object *rgx,
  const char *string,
  RGX_MatchHandler *handler, void *data
);

extern RGX_Matcher *rgxMatchTextCharacters (
  RGX_Object *rgx,
  const wchar_t *characters, size_t length,
  RGX_Match **result, void *data
);

extern RGX_Matcher *rgxMatchTextString (
  RGX_Object *rgx,
  const wchar_t *string,
  RGX_Match **result, void *data
);

extern RGX_Matcher *rgxMatchTextUTF8 (
  RGX_Object *rgx,
  const char *string,
  RGX_Match **result, void *data
);

extern int rgxGetNameNumberCharacters (
  const RGX_Matcher *matcher,
  const wchar_t *characters, size_t length,
  size_t *number
);

extern int rgxGetNameNumberString (
  const RGX_Matcher *matcher,
  const wchar_t *string,
  size_t *number
);

extern int rgxGetNameNumberUTF8 (
  const RGX_Matcher *matcher,
  const char *string,
  size_t *number
);

extern size_t rgxGetCaptureCount (
  const RGX_Match *match
);

extern int rgxGetCaptureBounds (
  const RGX_Match *match,
  size_t number, size_t *from, size_t *to
);

extern int rgxGetCaptureText (
  const RGX_Match *match,
  size_t number, const wchar_t **characters, size_t *length
);

typedef enum {
  RGX_OPTION_CLEAR,
  RGX_OPTION_SET,
  RGX_OPTION_TOGGLE,
  RGX_OPTION_TEST
} RGX_OptionAction;

typedef enum {
  RGX_COMPILE_ANCHOR_START,

  RGX_COMPILE_IGNORE_CASE,
  RGX_COMPILE_UNICODE_PROPERTIES,
} RGX_CompileOption;

extern int rgxCompileOption (
  RGX_Object *rgx,
  RGX_OptionAction action,
  RGX_CompileOption option
);

typedef enum {
  RGX_MATCH_ANCHOR_START,
} RGX_MatchOption;

extern int rgxMatchOption (
  RGX_Matcher *matcher,
  RGX_OptionAction action,
  RGX_MatchOption option
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_RGX */
