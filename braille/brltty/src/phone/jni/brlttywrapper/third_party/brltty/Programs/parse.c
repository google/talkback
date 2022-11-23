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

#include <stdio.h>
#include <string.h>
#include <ctype.h>

#include "parse.h"
#include "log.h"

char *
joinStrings (const char *const *strings, int count) {
  char *string;
  size_t length = 0;
  size_t lengths[count];
  int index;

  for (index=0; index<count; index+=1) {
    length += lengths[index] = strlen(strings[index]);
  }

  if ((string = malloc(length+1))) {
    char *target = string;

    for (index=0; index<count; index+=1) {
      length = lengths[index];
      memcpy(target, strings[index], length);
      target += length;
    }

    *target = 0;
  }

  return string;
}

int
changeStringSetting (char **setting, const char *value) {
  char *string;

  if (!value) {
    string = NULL;
  } else if (!(string = strdup(value))) {
    logMallocError();
    return 0;
  }

  if (*setting) free(*setting);
  *setting = string;
  return 1;
}

int
extendStringSetting (char **setting, const char *value, int prepend) {
  if (value && *value) {
    if (*setting) {
      size_t newSize = strlen(*setting) + 1 + strlen(value) + 1;
      char newSetting[newSize];

      if (prepend) {
        snprintf(newSetting, newSize, "%s%c%s", value, PARAMETER_SEPARATOR_CHARACTER, *setting);
      } else {
        snprintf(newSetting, newSize, "%s%c%s", *setting, PARAMETER_SEPARATOR_CHARACTER, value);
      }

      if (!changeStringSetting(setting, newSetting)) return 0;
    } else if (!changeStringSetting(setting, value)) {
      return 0;
    }
  }

  return 1;
}

void
deallocateStrings (char **array) {
  char **element = array;
  while (*element) free(*element++);
  free(array);
}

char **
splitString (const char *string, char delimiter, int *count) {
  char **array = NULL;

  if (!string) string = "";

  if (string) {
    while (1) {
      const char *start = string;
      int index = 0;

      if (*start) {
        while (1) {
          const char *end = strchr(start, delimiter);
          size_t length = end? (size_t)(end-start): strlen(start);

          if (array) {
            char *element = malloc(length+1);

            if (!(array[index] = element)) {
              logMallocError();
              deallocateStrings(array);
              array = NULL;
              goto done;
            }

            memcpy(element, start, length);
            element[length] = 0;
          }
          index += 1;

          if (!end) break;
          start = end + 1;
        }
      }

      if (array) {
        array[index] = NULL;
        if (count) *count = index;
        break;
      }

      if (!(array = malloc((index + 1) * sizeof(*array)))) {
        logMallocError();
        break;
      }
    }
  }

done:
  if (!array && count) *count = 0;
  return array;
}

int
rescaleInteger (int value, int from, int to) {
  return (to * (value + (from / (to * 2)))) / from;
}

int
isInteger (int *value, const char *string) {
  if (*string) {
    char *end;
    long l = strtol(string, &end, 0);

    if (!*end) {
      *value = l;
      return 1;
    }
  }

  return 0;
}

int
isUnsignedInteger (unsigned int *value, const char *string) {
  if (*string) {
    char *end;
    unsigned long l = strtoul(string, &end, 0);

    if (!*end) {
      *value = l;
      return 1;
    }
  }

  return 0;
}

int
isLogLevel (unsigned int *level, const char *string) {
  {
    size_t length = strlen(string);
    unsigned int index;

    for (index=0; index<logLevelCount; index+=1) {
      if (strncasecmp(string, logLevelNames[index], length) == 0) {
        *level = index;
        return 1;
      }
    }
  }

  {
    unsigned int value;

    if (isUnsignedInteger(&value, string) && (value < logLevelCount)) {
      *level = value;
      return 1;
    }
  }

  return 0;
}

int
isAbbreviation (const char *actual, const char *supplied) {
  return strncasecmp(actual, supplied, strlen(supplied)) == 0;
}

int
isAbbreviatedPhrase (const char *actual, const char *supplied) {
  while (1) {
    if (!*supplied) return 1;

    if (*supplied == '-') {
      while (*actual != '-') {
        if (!*actual) return 0;
        actual += 1;
      }
    } else if (tolower(*supplied) != tolower(*actual)) {
      return 0;
    }

    actual += 1;
    supplied += 1;
  }
}

int
validateInteger (int *value, const char *string, const int *minimum, const int *maximum) {
  if (*string) {
    int i;

    if (!isInteger(&i, string)) return 0;
    if (minimum && (i < *minimum)) return 0;
    if (maximum && (i > *maximum)) return 0;

    *value = i;
  }

  return 1;
}

int
validateChoiceEx (unsigned int *value, const char *string, const void *choices, size_t size) {
  *value = 0;
  if (!*string) return 1;
  const void *choice = choices;

  while (1) {
    typedef struct {
      const char *name;
    } Entry;

    const Entry *entry = choice;
    const char *name = entry->name;
    if (!name) break;

    if (isAbbreviatedPhrase(name, string)) {
      *value = (choice - choices) / size;
      return 1;
    }

    choice += size;
  }

  return 0;
}

int
validateChoice (unsigned int *value, const char *string, const char *const *choices) {
  return validateChoiceEx(value, string, choices, sizeof(*choices));
}

FlagKeywordPair fkpOnOff     = {.true="on"  , .false="off"  };
FlagKeywordPair fkpTrueFalse = {.true="true", .false="false"};
FlagKeywordPair fkpYesNo     = {.true="yes" , .false="no"   };
FlagKeywordPair fkp10        = {.true="1"   , .false="0"    };

const FlagKeywordPair *const flagKeywordPairs[] = {
  &fkpOnOff, &fkpTrueFalse, &fkpYesNo, &fkp10
};

int
validateFlagKeyword (unsigned int *value, const char *string) {
  static const char **choices = NULL;

  if (!choices) {
    unsigned int count = ARRAY_COUNT(flagKeywordPairs);
    size_t size = ARRAY_SIZE(choices, ((count * 2) + 1));

    if (!(choices = malloc(size))) {
      logMallocError();
      return 0;
    }

    const FlagKeywordPair *const *fkp = flagKeywordPairs;
    const FlagKeywordPair *const *end = fkp + count;
    const char **choice = choices;

    while (fkp < end) {
      *choice++ = (*fkp)->false;
      *choice++ = (*fkp)->true;
      fkp += 1;
    }

    *choice = NULL;
  }

  if (!validateChoice(value, string, choices)) return 0;
  *value %= 2;
  return 1;
}

int
validateFlag (unsigned int *value, const char *string, const FlagKeywordPair *fkp) {
  const char *choices[] = {fkp->false, fkp->true, NULL};
  return validateChoice(value, string, choices);
}

int
validateOnOff (unsigned int *value, const char *string) {
  return validateFlag(value, string, &fkpOnOff);
}

int
validateYesNo (unsigned int *value, const char *string) {
  return validateFlag(value, string, &fkpYesNo);
}

#ifndef NO_FLOAT
int
isFloat (float *value, const char *string) {
  if (*string) {
    char *end;
    double d = strtod(string, &end);

    if (!*end) {
      *value = d;
      return 1;
    }
  }

  return 0;
}

int
validateFloat (float *value, const char *string, const float *minimum, const float *maximum) {
  if (*string) {
    float f;

    if (!isFloat(&f, string)) return 0;
    if (minimum && (f < *minimum)) return 0;
    if (maximum && (f > *maximum)) return 0;

    *value = f;
  }

  return 1;
}
#endif /* NO_FLOAT */

int
hasQualifier (const char **identifier, const char *qualifier) {
  const char *delimiter = strchr(*identifier, PARAMETER_QUALIFIER_CHARACTER);
  if (!delimiter) return 0;

  size_t count = delimiter - *identifier;
  if (memchr(*identifier, FILE_PATH_DELIMITER, count)) return 0;

  if (qualifier) {
    if (count != strlen(qualifier)) return 0;
    if (strncasecmp(*identifier, qualifier, count) != 0) return 0;
  }

  *identifier += count + 1;
  return 1;
}

int
hasNoQualifier (const char *identifier) {
  return !hasQualifier(&identifier, NULL);
}

static int
parseParameters (
  char **values,
  const char *const *names,
  const char *qualifier,
  const char *parameters
) {
  if (parameters && *parameters) {
    const char *parameter = parameters;

    while (1) {
      const char *parameterEnd = strchr(parameter, PARAMETER_SEPARATOR_CHARACTER);
      int done = !parameterEnd;
      int parameterLength;

      if (done) parameterEnd = parameter + strlen(parameter);
      parameterLength = parameterEnd - parameter;

      if (parameterLength > 0) {
        const char *value = memchr(parameter, PARAMETER_ASSIGNMENT_CHARACTER, parameterLength);

        if (!value) {
          logMessage(LOG_ERR, "%s: %.*s",
                     gettext("missing parameter value"),
                     parameterLength, parameter);
          return 0;
        }

        {
          const char *name = parameter;
          size_t nameLength = value++ - name;
          size_t valueLength = parameterEnd - value;
          int isEligible = 1;

          if (qualifier) {
            const char *delimiter = memchr(name, PARAMETER_QUALIFIER_CHARACTER, nameLength);

            if (delimiter) {
              size_t qualifierLength = delimiter - name;
              size_t nameAdjustment = qualifierLength + 1;

              name += nameAdjustment;
              nameLength -= nameAdjustment;
              isEligible = 0;

              if (!qualifierLength) {
                logMessage(LOG_ERR, "%s: %.*s",
                           gettext("missing parameter qualifier"),
                           parameterLength, parameter);
                return 0;
              }

              if ((qualifierLength == strlen(qualifier)) &&
                  (memcmp(parameter, qualifier, qualifierLength) == 0)) {
                isEligible = 1;
              }
            }
          }

          if (!nameLength) {
            logMessage(LOG_ERR, "%s: %.*s",
                       gettext("missing parameter name"),
                       parameterLength, parameter);
            return 0;
          }

          if (isEligible) {
            unsigned int index = 0;

            while (names[index]) {
              if (strncasecmp(name, names[index], nameLength) == 0) {
                char *newValue = malloc(valueLength + 1);

                if (!newValue) {
                  logMallocError();
                  return 0;
                }

                memcpy(newValue, value, valueLength);
                newValue[valueLength] = 0;

                free(values[index]);
                values[index] = newValue;
                goto parameterDone;
              }

              index += 1;
            }

            logMessage(LOG_ERR, "%s: %.*s",
                       gettext("unsupported parameter"),
                       parameterLength, parameter);
            return 0;
          }
        }
      }

    parameterDone:
      if (done) break;
      parameter = parameterEnd + 1;
    }
  }

  return 1;
}

char **
getParameters (const char *const *names, const char *qualifier, const char *parameters) {
  if (!names) {
    static const char *const noNames[] = {NULL};
    names = noNames;
  }

  {
    char **values;
    unsigned int count = 0;
    while (names[count]) count += 1;

    if ((values = malloc((count + 1) * sizeof(*values)))) {
      unsigned int index = 0;

      while (index < count) {
        if (!(values[index] = strdup(""))) {
          logMallocError();
          break;
        }

        index += 1;
      }

      if (index == count) {
        values[index] = NULL;
        if (parseParameters(values, names, qualifier, parameters)) return values;
      }

      deallocateStrings(values);
    } else {
      logMallocError();
    }
  }

  return NULL;
}

void
logParameters (const char *const *names, char **values, const char *description) {
  if (names && values) {
    while (*names) {
      logMessage(LOG_INFO, "%s: %s=%s", description, *names, *values);
      ++names;
      ++values;
    }
  }
}
