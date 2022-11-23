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
#include <errno.h>
#include <ctype.h>

#include "prefs.h"
#include "prefs_internal.h"
#include "status_types.h"
#include "defaults.h"
#include "log.h"
#include "file.h"
#include "datafile.h"
#include "parse.h"

#define PREFS_COMMENT_CHARACTER '#'
#define PREFS_MAGIC_NUMBER 0x4005

Preferences prefs;                /* environment (i.e. global) parameters */
unsigned char statusFieldsSet;

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
    sfCursorAndWindowColumn, sfCursorAndWindowRow, sfStateDots, sfEnd
  };

  static const unsigned char stylePB80[] = {
    sfWindowRow, sfEnd
  };

  static const unsigned char styleConfigurable[] = {
    sfGeneric, sfEnd
  };

  static const unsigned char styleMDV[] = {
    sfWindowCoordinates, sfEnd
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

static void
resetPreference (const PreferenceEntry *pref) {
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
    const PreferenceEntry *pref = preferenceTable;
    const PreferenceEntry *end = pref + preferenceCount;

    while (pref < end) resetPreference(pref++);
  }
}

char *
makePreferencesFilePath (const char *name) {
  if (!name) name = PREFERENCES_FILE;
  return makeUpdatablePath(name);
}

static int
comparePreferenceNames (const char *name1, const char *name2) {
  return strcmp(name1, name2);
}

static int
sortPreferencesByName (const void *element1, const void *element2) {
  const PreferenceEntry *const *pref1 = element1;
  const PreferenceEntry *const *pref2 = element2;

  return comparePreferenceNames((*pref1)->name, (*pref2)->name);
}

static int
searchPreferenceByName (const void *target, const void *element) {
  const char *name = target;
  const PreferenceEntry *const *pref = element;
  return comparePreferenceNames(name, (*pref)->name);
}

const PreferenceEntry *
findPreferenceByName (const char *name) {
  static const PreferenceEntry **sortedPreferences = NULL;

  if (!sortedPreferences) {
    if (!(sortedPreferences = malloc(ARRAY_SIZE(sortedPreferences, preferenceCount)))) {
      logMallocError();
      return NULL;
    }

    {
      unsigned int index;

      for (index=0; index<preferenceCount; index+=1) {
        sortedPreferences[index] = &preferenceTable[index];
      }
    }

    qsort(sortedPreferences, preferenceCount, sizeof(*sortedPreferences), sortPreferencesByName);
  }

  {
    const PreferenceEntry *const *pref = bsearch(
      name, sortedPreferences,
      preferenceCount, sizeof(*sortedPreferences),
      searchPreferenceByName
    );

    if (pref) return *pref;
  }

  return NULL;
}

static int
sortAliasesByOldName (const void *element1, const void *element2) {
  const PreferenceAliasEntry *const *alias1 = element1;
  const PreferenceAliasEntry *const *alias2 = element2;

  return comparePreferenceNames((*alias1)->oldName, (*alias2)->oldName);
}

static int
searchAliasByOldName (const void *target, const void *element) {
  const char *name = target;
  const PreferenceAliasEntry *const *alias = element;

  return comparePreferenceNames(name, (*alias)->oldName);
}

const PreferenceEntry *
findPreferenceByAlias (const char *name) {
  static const PreferenceAliasEntry **sortedAliases = NULL;

  if (!sortedAliases) {
    if (!(sortedAliases = malloc(ARRAY_SIZE(sortedAliases, preferenceAliasCount)))) {
      logMallocError();
      return NULL;
    }

    {
      unsigned int index;

      for (index=0; index<preferenceAliasCount; index+=1) {
        sortedAliases[index] = &preferenceAliasTable[index];
      }
    }

    qsort(sortedAliases, preferenceAliasCount, sizeof(*sortedAliases), sortAliasesByOldName);
  }

  {
    const PreferenceAliasEntry *const *alias = bsearch(
      name, sortedAliases,
      preferenceAliasCount, sizeof(*sortedAliases),
      searchAliasByOldName
    );

    if (alias) return findPreferenceByName((*alias)->newName);
  }

  return NULL;
}

const PreferenceEntry *
findPreferenceEntry (const char *name) {
  const PreferenceEntry *pref;

  if ((pref = findPreferenceByName(name))) return pref;
  if ((pref = findPreferenceByAlias(name))) return pref;
  return NULL;
}

static int
changePreferenceSetting (
  const char *name, const char *operand,
  unsigned char *setting, const PreferenceStringTable *names
) {
  if (names) {
    for (unsigned int index=0; index<names->count; index+=1) {
      if (strcmp(operand, names->table[index]) == 0) {
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
    const PreferenceEntry *pref = findPreferenceEntry(name);

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
    } else {
      logMessage(LOG_WARNING, "unknown preference: %s", name);
    }
  } else {
    logMessage(LOG_WARNING, "missing preference name");
  }

  return 1;
}

static int
processPreferenceLine (char *line, void *data) {
  while (isspace(*line)) line += 1;
  if (!*line) return 1;
  if (*line == PREFS_COMMENT_CHARACTER) return 1;
  return setPreference(line);
}

int
loadPreferencesFile (const char *path) {
  int ok = 0;
  FILE *file = openDataFile(path, "rb", 1);

  if (file) {
    Preferences newPreferences;
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
        const PreferenceEntry *pref = preferenceTable;
        const PreferenceEntry *end = pref + preferenceCount;

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
putPreferenceComment (FILE *file, const PreferenceEntry *pref) {
  if (fprintf(file, "\n%c %s", PREFS_COMMENT_CHARACTER, pref->name) < 0) return 0;

  if (pref->settingCount) {
    if (fprintf(file, "[%u]", pref->settingCount) < 0) return 0;
  }

  if (fputs(": ", file) == EOF) return 0;

  if (pref->settingNames && (pref->defaultValue < pref->settingNames->count)) {
    if (fputs(pref->settingNames->table[pref->defaultValue], file) == EOF) return 0;
  } else {
    if (fprintf(file, "%u", pref->defaultValue) < 0) return 0;
  }

  if (pref->settingNames && (pref->settingNames->count > 0)) {
    unsigned int index;

    if (fputs(" {", file) == EOF) return 0;

    for (index=0; index<pref->settingNames->count; index+=1) {
      if (index > 0) {
        if (fputc(' ', file) == EOF) return 0;
      }

      if (fputs(pref->settingNames->table[index], file) == EOF) return 0;
    }

    if (fputc('}', file) == EOF) return 0;
  }

  if (fputc('\n', file) == EOF) return 0;
  return 1;
}

static int
putPreferenceSetting (FILE *file, unsigned char setting, const PreferenceStringTable *names) {
  if (fputc(' ', file) == EOF) return 0;

  if (names && (setting < names->count)) {
    if (fputs(names->table[setting], file) == EOF) return 0;
  } else {
    if (fprintf(file, "%u", setting) < 0) return 0;
  }

  return 1;
}

int
savePreferencesFile (const char *path) {
  int ok = 0;
  FILE *file = openDataFile(path, "w", 0);

  if (file) {
    const PreferenceEntry *pref = preferenceTable;
    const PreferenceEntry *const end = pref + preferenceCount;

    if (fprintf(file, "%c %s Preferences File\n", PREFS_COMMENT_CHARACTER, PACKAGE_NAME) < 0) goto done;

    while (pref < end) {
      if (!putPreferenceComment(file, pref)) break;
      if (fputs(pref->name, file) == EOF) break;

      if (pref->settingCount) {
        unsigned char count = pref->settingCount;
        unsigned char *setting = pref->setting;

        while (count-- && *setting) {
          if (!putPreferenceSetting(file, *setting++, pref->settingNames)) goto done;
        }
      } else if (!putPreferenceSetting(file, *pref->setting, pref->settingNames)) {
        goto done;
      }

      if (fputs("\n", file) == EOF) goto done;

      pref += 1;
    }

    ok = 1;
  done:
    if (!ok) {
      if (!ferror(file)) errno = EIO;
      logMessage(LOG_ERR, "%s: %s: %s",
                 gettext("cannot write to preferences file"), path, strerror(errno));
    }

    fclose(file);
  }

  return ok;
}
