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

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <ctype.h>

#include "prefs.h"
#include "pref_tables.h"
#include "status_types.h"
#include "defaults.h"
#include "log.h"
#include "file.h"
#include "datafile.h"
#include "parse.h"

#define PREFS_COMMENT_CHARACTER '#'
#define PREFS_MAGIC_NUMBER 0x4005

void
setStatusFields (const unsigned char *fields) {
  if (!statusFieldsSet) {
    if (fields) {
      unsigned int index = 0;

      while (index < (ARRAY_COUNT(prefs.statusFields) - 1)) {
        unsigned char field = fields[index];
        if (field == sfEnd) break;
        prefs.statusFields[index++] = field;
      }

      statusFieldsSet = 1;
    }
  }
}

static void
setStatusStyle (unsigned char style) {
  static const unsigned char styleNone[] = {
    sfEnd
  };

  static const unsigned char styleAlva[] = {
    sfAlphabeticCursorCoordinates, sfAlphabeticWindowCoordinates, sfStateLetter, sfEnd
  };

  static const unsigned char styleTieman[] = {
    sfCursorAndWindowColumn2, sfCursorAndWindowRow2, sfStateDots, sfEnd
  };

  static const unsigned char stylePB80[] = {
    sfWindowRow, sfEnd
  };

  static const unsigned char styleConfigurable[] = {
    sfGeneric, sfEnd
  };

  static const unsigned char styleMDV[] = {
    sfWindowCoordinates2, sfEnd
  };

  static const unsigned char styleVoyager[] = {
    sfWindowRow, sfCursorRow, sfCursorColumn, sfEnd
  };

  static const unsigned char styleTime[] = {
    sfTime, sfEnd
  };

  static const unsigned char *const styleTable[] = {
    styleNone, styleAlva, styleTieman, stylePB80,
    styleConfigurable, styleMDV, styleVoyager, styleTime
  };
  static const unsigned char styleCount = ARRAY_COUNT(styleTable);

  if (style < styleCount) {
    const unsigned char *fields = styleTable[style];
    if (*fields != sfEnd) setStatusFields(fields);
  }
}

static int
comparePreferenceNames (const char *name1, const char *name2) {
  return strcmp(name1, name2);
}

static int
sortPreferenceDefinitions (const void *element1, const void *element2) {
  const PreferenceDefinition *const *pref1 = element1;
  const PreferenceDefinition *const *pref2 = element2;
  return comparePreferenceNames((*pref1)->name, (*pref2)->name);
}

static int
searchPreferenceDefinition (const void *target, const void *element) {
  const char *name = target;
  const PreferenceDefinition *const *pref = element;
  return comparePreferenceNames(name, (*pref)->name);
}

static const PreferenceDefinition *
findPreferenceDefinition (const char *name) {
  static const PreferenceDefinition **sortedDefinitions = NULL;

  if (!sortedDefinitions) {
    if (!(sortedDefinitions = malloc(ARRAY_SIZE(sortedDefinitions, preferenceDefinitionCount)))) {
      logMallocError();
      return NULL;
    }

    for (unsigned int index=0; index<preferenceDefinitionCount; index+=1) {
      sortedDefinitions[index] = &preferenceDefinitionTable[index];
    }

    qsort(
      sortedDefinitions, preferenceDefinitionCount,
      sizeof(*sortedDefinitions), sortPreferenceDefinitions
    );
  }

  {
    const PreferenceDefinition *const *pref = bsearch(
      name, sortedDefinitions, preferenceDefinitionCount,
      sizeof(*sortedDefinitions), searchPreferenceDefinition
    );

    if (pref) return *pref;
  }

  return NULL;
}

static int
sortPreferenceAliases (const void *element1, const void *element2) {
  const PreferenceAlias *const *alias1 = element1;
  const PreferenceAlias *const *alias2 = element2;
  return comparePreferenceNames((*alias1)->oldName, (*alias2)->oldName);
}

static int
searchPreferenceAlias (const void *target, const void *element) {
  const char *name = target;
  const PreferenceAlias *const *alias = element;
  return comparePreferenceNames(name, (*alias)->oldName);
}

static const PreferenceAlias *
findPreferenceAlias (const char *name) {
  static const PreferenceAlias **sortedAliases = NULL;

  if (!sortedAliases) {
    if (!(sortedAliases = malloc(ARRAY_SIZE(sortedAliases, preferenceAliasCount)))) {
      logMallocError();
      return NULL;
    }

    for (unsigned int index=0; index<preferenceAliasCount; index+=1) {
      sortedAliases[index] = &preferenceAliasTable[index];
    }

    qsort(
      sortedAliases, preferenceAliasCount,
      sizeof(*sortedAliases), sortPreferenceAliases
    );
  }

  {
    const PreferenceAlias *const *alias = bsearch(
      name, sortedAliases, preferenceAliasCount,
      sizeof(*sortedAliases), searchPreferenceAlias
    );

    if (alias) return *alias;
  }

  return NULL;
}

const PreferenceDefinition *
findPreference (const char *name) {
  while (name) {
    {
      const PreferenceDefinition *pref = findPreferenceDefinition(name);
      if (pref) return pref;
    }

    {
      const PreferenceAlias *alias = findPreferenceAlias(name);
      if (!alias) break;
      name = alias->newName;
    }
  }

  if (name) logMessage(LOG_WARNING, "unknown preference: %s", name);
  return NULL;
}

static void
resetPreference (const PreferenceDefinition *pref) {
  if (pref->settingCount) {
    memset(pref->setting, pref->defaultValue, pref->settingCount);
  } else {
    *pref->setting = pref->defaultValue;
  }

  if (pref->encountered) *pref->encountered = 0;
}

void
resetPreferences (void) {
  memset(&prefs, 0, sizeof(prefs));

  prefs.magic[0] = PREFS_MAGIC_NUMBER & 0XFF;
  prefs.magic[1] = PREFS_MAGIC_NUMBER >> 8;
  prefs.version = 6;

  {
    const PreferenceDefinition *pref = preferenceDefinitionTable;
    const PreferenceDefinition *end = pref + preferenceDefinitionCount;

    while (pref < end) resetPreference(pref++);
  }
}

static const char *
getSettingName (const PreferenceDefinition *pref, unsigned char index) {
  const PreferenceStringTable *names = pref->settingNames;
  if (!names) return NULL;
  if (index >= names->count) return NULL;
  return names->table[index];
}

static int
changePreferenceSetting (
  const char *name, const char *operand,
  unsigned char *setting, const PreferenceStringTable *names
) {
  if (names) {
    for (unsigned int index=0; index<names->count; index+=1) {
      const char *name = names->table[index];
      if (!name) continue;

      if (strcmp(operand, name) == 0) {
        *setting = index;
        return 1;
      }
    }
  }

  {
    int value;

    if (isInteger(&value, operand)) {
      unsigned char maximum = names? (names->count - 1): 0XFF;

      if ((value >= 0) && (value <= maximum)) {
        *setting = value;
        return 1;
      }
    }
  }

  logMessage(LOG_WARNING, "invalid preference setting: %s %s", name, operand);
  return 0;
}

int
setPreference (char *string) {
  const char *name;

  {
    static const char delimiters[] = {
      ' ', '\t', PARAMETER_ASSIGNMENT_CHARACTER, 0
    };

    name = strtok(string, delimiters);
  }

  if (name) {
    const PreferenceDefinition *pref = findPreference(name);

    if (pref) {
      if (pref->encountered) *pref->encountered = 1;

      static const char delimiters[] = " \t";
      const char *operand;

      if (pref->settingCount) {
        unsigned char count = pref->settingCount;
        unsigned char *setting = pref->setting;

        while (count) {
          if ((operand = strtok(NULL, delimiters))) {
            if (changePreferenceSetting(name, operand, setting, pref->settingNames)) {
              setting += 1;
              count -= 1;
              continue;
            }
          }

          *setting = 0;
          break;
        }
      } else if (!(operand = strtok(NULL, delimiters))) {
        logMessage(LOG_WARNING, "missing preference setting: %s", name);
      } else if (!changePreferenceSetting(name, operand, pref->setting, pref->settingNames)) {
      }
    }
  } else {
    logMessage(LOG_WARNING, "missing preference name");
  }

  return 1;
}

char *
makePreferencesFilePath (const char *name) {
  if (!name) name = PREFERENCES_FILE;
  return makeUpdatablePath(name);
}

static int
processPreferenceLine (const LineHandlerParameters *parameters) {
  char *line = parameters->line.text;
  while (isspace(*line)) line += 1;
  if (!*line) return 1;
  if (*line == PREFS_COMMENT_CHARACTER) return 1;
  return setPreference(line);
}

int
loadPreferencesFile (const char *path) {
  int ok = 0;

  logMessage(LOG_DEBUG, "loading preferences file: %s", path);
  FILE *file = openDataFile(path, "rb", 1);

  if (file) {
    PreferenceSettings newPreferences;
    size_t length = fread(&newPreferences, 1, sizeof(newPreferences), file);

    if (ferror(file)) {
      logMessage(LOG_ERR, "%s: %s: %s",
                 gettext("cannot read preferences file"), path, strerror(errno));
    } else if ((length < 40) ||
               (newPreferences.magic[0] != (PREFS_MAGIC_NUMBER & 0XFF)) ||
               (newPreferences.magic[1] != (PREFS_MAGIC_NUMBER >> 8))) {
      fclose(file);

      if ((file = openDataFile(path, "r", 1))) {
        resetPreferences();
        if (processLines(file, processPreferenceLine, NULL)) ok = 1;
      }
    } else {
      prefs = newPreferences;
      ok = 1;

      {
        const PreferenceDefinition *pref = preferenceDefinitionTable;
        const PreferenceDefinition *end = pref + preferenceDefinitionCount;

        const unsigned char *from = prefs.magic;
        const unsigned char *to = from + length;

        while (pref < end) {
          unsigned char count = pref->settingCount;
          if (!count) count = 1;

          if ((pref->setting < from) || ((pref->setting + count) > to)) {
            resetPreference(pref);
          }

          pref += 1;
        }
      }

      if (length < (prefs.statusFields + sizeof(prefs.statusFields) - prefs.magic)) {
        setStatusStyle(prefs.expandCurrentWord);
      } else {
        statusFieldsSet = 1;
      }

      if (prefs.version == 0) {
        prefs.version += 1;
        prefs.pcmVolume = DEFAULT_PCM_VOLUME;
        prefs.midiVolume = DEFAULT_MIDI_VOLUME;
        prefs.fmVolume = DEFAULT_FM_VOLUME;
      }

      if (prefs.version == 1) {
        prefs.version += 1;
        prefs.sayLineMode = DEFAULT_SAY_LINE_MODE;
        prefs.autospeak = DEFAULT_AUTOSPEAK;
      }

      if (prefs.version == 2) {
        prefs.version += 1;
        prefs.autorepeatEnabled = DEFAULT_AUTOREPEAT_ENABLED;
        prefs.longPressTime = DEFAULT_LONG_PRESS_TIME;
        prefs.autorepeatInterval = DEFAULT_AUTOREPEAT_INTERVAL;
        prefs.screenCursorVisibleTime *= 4;
        prefs.screenCursorInvisibleTime *= 4;
        prefs.attributesVisibleTime *= 4;
        prefs.attributesInvisibleTime *= 4;
        prefs.capitalsVisibleTime *= 4;
        prefs.capitalsInvisibleTime *= 4;
      }

      if (prefs.version == 3) {
        prefs.version += 1;
        prefs.autorepeatPanning = DEFAULT_AUTOREPEAT_PANNING;
      }

      if (prefs.version == 4) {
        prefs.version += 1;
        prefs.touchSensitivity = DEFAULT_TOUCH_SENSITIVITY;
      }

      if (prefs.version == 5) {
        prefs.version += 1;
        prefs.expandCurrentWord = DEFAULT_EXPAND_CURRENT_WORD;
      }
    }

    if (file) {
      fclose(file);
      file = NULL;
    }
  }

  return ok;
}

static int
putPreferenceComment (FILE *file, const PreferenceDefinition *pref) {
  if (fprintf(file, "\n%c %s", PREFS_COMMENT_CHARACTER, pref->name) < 0) return 0;

  if (pref->settingCount) {
    if (fprintf(file, "[%u]", pref->settingCount) < 0) return 0;
  }

  if (fputs(": ", file) == EOF) return 0;
  const char *name = getSettingName(pref, pref->defaultValue);

  if (name) {
    if (fputs(name, file) == EOF) return 0;
  } else {
    if (fprintf(file, "%u", pref->defaultValue) < 0) return 0;
  }

  if (pref->settingNames) {
    if (fputs(" {", file) == EOF) return 0;

    unsigned char count = pref->settingNames->count;
    int first = 1;

    for (unsigned char index=0; index<count; index+=1) {
      const char *name = getSettingName(pref, index);
      if (name) {
        if (first) {
          first = 0;
        } else if (fputc(' ', file) == EOF) {
          return 0;
        }

        if (fputs(name, file) == EOF) return 0;
      } else {
        logMessage(LOG_WARNING,
          "unnamed preference setting: %s: %u",
          pref->name, index
        );
      }
    }

    if (fputc('}', file) == EOF) return 0;
  }

  if (fputc('\n', file) == EOF) return 0;
  return 1;
}

static int
putSetting (FILE *file, const PreferenceDefinition *pref, unsigned char setting) {
  if (fputc(' ', file) == EOF) return 0;
  const char *name = getSettingName(pref, setting);

  if (name) {
    if (fputs(name, file) == EOF) return 0;
  } else {
    if (fprintf(file, "%u", setting) < 0) return 0;
  }

  return 1;
}

static int
putPreference (FILE *file, const PreferenceDefinition *pref) {
  if (pref->dontSave) return 1;

  if (!putPreferenceComment(file, pref)) return 0;
  if (fputs(pref->name, file) == EOF) return 0;

  if (pref->settingCount) {
    unsigned char count = pref->settingCount;
    unsigned char *setting = pref->setting;

    while (count-- && *setting) {
      if (!putSetting(file, pref, *setting++)) return 0;
    }
  } else if (!putSetting(file, pref, *pref->setting)) {
    return 0;
  }

  if (fputs("\n", file) == EOF) return 0;
  return 1;
}

static int
putPreferences (FILE *file) {
  const PreferenceDefinition *pref = preferenceDefinitionTable;
  const PreferenceDefinition *const end = pref + preferenceDefinitionCount;

  while (pref < end) {
    if (!putPreference(file, pref)) return 0;
    pref += 1;
  }

  return 1;
}

static int
putHeader (FILE *file) {
  fprintf(file,
    "%c %s Preferences File\n",
    PREFS_COMMENT_CHARACTER, PACKAGE_NAME
  );

  return !ferror(file);
}

int
savePreferencesFile (const char *path) {
  int ok = 0;
  FILE *file = openDataFile(path, "w", 0);

  if (file) {
    if (putHeader(file)) {
      if (putPreferences(file)) {
        ok = 1;
      }
    }

    if (!ok) {
      if (!ferror(file)) errno = EIO;
      logMessage(LOG_ERR,
        "%s: %s: %s",
        gettext("cannot write to preferences file"),
        path, strerror(errno)
      );
    }

    fclose(file);
  }

  return ok;
}
