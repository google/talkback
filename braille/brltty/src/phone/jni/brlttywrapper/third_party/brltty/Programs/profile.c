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

#include "log.h"
#include "profile.h"
#include "datafile.h"
#include "file.h"
#include "utf8.h"

typedef struct {
  const ProfileDescriptor *profile;
  char **values;
} ProfileActivationData;

static int
processPropertyAssignment (
  DataFile *file,
  const DataString *name,
  char **value,
  const ProfileActivationData *pad
) {
  unsigned int index;

  for (index=0; index<pad->profile->properties.count; index+=1) {
    if (isKeyword(pad->profile->properties.array[index].name, name->characters, name->length)) {
      char **v = &pad->values[index];

      if (*v) {
        reportDataError(file, "property assigned more than once: %s:%.*"PRIws,
                        pad->profile->category, name->length, name->characters);
        free(*v);
      }

      *v = *value;
      *value = NULL;
      return 1;
    }
  }

  reportDataError(file, "unknown property: %s:%.*"PRIws,
                  pad->profile->category, name->length, name->characters);
  return 1;
}

static DATA_OPERANDS_PROCESSOR(processPropertyOperands) {
  const ProfileActivationData *pad = data;
  int ok = 1;
  DataString name;

  if (getDataString(file, &name, 0, "property name")) {
    DataString value;

    if (getDataString(file, &value, 0, "property value")) {
      char *v = getUtf8FromWchars(value.characters, value.length, NULL);

      if (v) {
        if (!processPropertyAssignment(file, &name, &v, pad)) ok = 0;
        if (v) free(v);
      } else {
        ok = 0;
      }
    }
  }

  return ok;
}

static DATA_OPERANDS_PROCESSOR(processProfileOperands) {
  BEGIN_DATA_DIRECTIVE_TABLE
    DATA_NESTING_DIRECTIVES,
    DATA_VARIABLE_DIRECTIVES,
    DATA_CONDITION_DIRECTIVES,
    {.name=NULL, .processor=processPropertyOperands},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "profile directive", data);
}

char *
makeProfilePath (const ProfileDescriptor *profile, const char *directory, const char *name) {
  char *subdirectory = makePath(directory, PROFILES_SUBDIRECTORY);

  if (subdirectory) {
    char *file = makeFilePath(subdirectory, name, profile->extension);

    free(subdirectory);
    if (file) return file;
  }

  return NULL;
}

static int
changeProperty (const ProfileProperty *property, const char *value) {
  if (property->change) {
    if (!value) value = *property->defaultValue;

    if (property->change(value)) {
      return 1;
    }
  }

  return 0;
}

static int
changeProperties (const ProfileDescriptor *profile, char **values) {
  int ok = !profile->begin || profile->begin();

  if (ok) {
    const ProfileProperty *const start = profile->properties.array;
    const ProfileProperty *const end = start + profile->properties.count;
    const ProfileProperty *property = start;

    while (property < end) {
      char *value = values? values[property - start]: NULL;

      if (!changeProperty(property, value)) ok = 0;
      if (value) free(value);
      property += 1;
    }

    if (profile->end && !profile->end()) ok = 0;
  }

  return ok;
}

int
activateProfile (const ProfileDescriptor *profile, const char *directory, const char *name) {
  int ok = 0;

  if (setBaseDataVariables(NULL)) {
    char *path;

    if ((path = makeProfilePath(profile, directory, name))) {
      ProfileActivationData pad = {
        .profile = profile
      };

      if ((pad.values = malloc(ARRAY_SIZE(pad.values, profile->properties.count)))) {
        for (unsigned int index=0; index<profile->properties.count; index+=1) {
          pad.values[index] = NULL;
        }

        const DataFileParameters parameters = {
          .processOperands = processProfileOperands,
          .data = &pad
        };

        if (processDataFile(path, &parameters)) {
          if (changeProperties(profile, pad.values)) {
            ok = 1;
          }
        }

        free(pad.values);
      } else {
        logMallocError();
      }

      free(path);
    }
  }

  return ok;
}

int
deactivateProfile (const ProfileDescriptor *profile) {
  return changeProperties(profile, NULL);
}
