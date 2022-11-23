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

#ifndef BRLTTY_INCLUDED_PARSE
#define BRLTTY_INCLUDED_PARSE

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern char *joinStrings (const char *const *strings, int count);
extern int changeStringSetting (char **setting, const char *value);
extern int extendStringSetting (char **setting, const char *value, int prepend);
extern void deallocateStrings (char **array);
extern char **splitString (const char *string, char delimiter, int *count);

extern int rescaleInteger (int value, int from, int to);
extern int isInteger (int *value, const char *string);
extern int isUnsignedInteger (unsigned int *value, const char *string);
extern int isLogLevel (unsigned int *level, const char *string);

extern int isAbbreviation (const char *actual, const char *supplied);
extern int isAbbreviatedPhrase (const char *actual, const char *supplied);

extern int validateInteger (int *value, const char *string, const int *minimum, const int *maximum);
extern int validateChoice (unsigned int *value, const char *string, const char *const *choices);
extern int validateChoiceEx (unsigned int *value, const char *string, const void *choices, size_t size);

typedef struct {
  const char *true;
  const char *false;
} FlagKeywordPair;

static inline const char *
getFlagKeyword (const FlagKeywordPair *fkp, int state) {
  return state? fkp->true: fkp->false;
}

extern FlagKeywordPair fkpOnOff;
extern FlagKeywordPair fkpTrueFalse;
extern FlagKeywordPair fkpYesNo;
extern FlagKeywordPair fkp10;

static inline const char *
getFlagKeywordOnOff (int state) {
  return getFlagKeyword(&fkpOnOff, state);
}

static inline const char *
getFlagKeywordTrueFalse (int state) {
  return getFlagKeyword(&fkpTrueFalse, state);
}

static inline const char *
getFlagKeywordYesNo (int state) {
  return getFlagKeyword(&fkpYesNo, state);
}

static inline const char *
getFlagKeyword10 (int state) {
  return getFlagKeyword(&fkp10, state);
}

extern int validateFlagKeyword (unsigned int *value, const char *string);
extern int validateFlag (unsigned int *value, const char *string, const FlagKeywordPair *fkp);
extern int validateOnOff (unsigned int *value, const char *string);
extern int validateYesNo (unsigned int *value, const char *string);

#ifndef NO_FLOAT
extern int isFloat (float *value, const char *string);
extern int validateFloat (float *value, const char *string, const float *minimum, const float *maximum);
#endif /* NO_FLOAT */

#define FILE_PATH_DELIMITER  '/'
#define PARAMETER_SEPARATOR_CHARACTER  ','
#define PARAMETER_ASSIGNMENT_CHARACTER '='
#define PARAMETER_QUALIFIER_CHARACTER  ':'

extern int hasQualifier (const char **identifier, const char *qualifier);
extern int hasNoQualifier (const char *identifier);

extern char **getParameters (const char *const *names, const char *qualifier, const char *parameters);
extern void logParameters (const char *const *names, char **values, const char *description);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PARSE */
