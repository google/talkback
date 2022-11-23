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

#include "log.h"
#include "strfmt.h"
#include "file.h"
#include "dynld.h"
#include "drivers.h"

int
isDriverAvailable (const char *code, const char *codes) {
  int length = strlen(code);
  const char *string = codes;

  while ((string = strstr(string, code))) {
    if (((string == codes) || (string[-1] == ' ')) &&
        (!string[length] || (string[length] == ' '))) {
      return 1;
    }

    string += length;
  }

  return 0;
}

int
isDriverIncluded (const char *code, const DriverEntry *table) {
  while (table->address) {
    if (strcmp(code, table->definition->code) == 0) return 1;
    ++table;
  }
  return 0;
}

int
haveDriver (const char *code, const char *codes, const DriverEntry *table) {
  return (table && table->address)? isDriverIncluded(code, table):
                                    isDriverAvailable(code, codes);
}

const char *
getDefaultDriver (const DriverEntry *table) {
  return (table && table[0].address && !table[1].address)? table[0].definition->code: NULL;
}

static int
isDriverCode (const char *code, const DriverDefinition *definition) {
  if (strcmp(code, definition->code) == 0) return 1;
  return 0;
}

const void *
loadDriver (
  const char *driverCode, void **driverObject,
  const char *driverDirectory, const DriverEntry *driverTable,
  const char *typeName, char typeLetter, const char *symbolPrefix,
  const void *nullAddress, const DriverDefinition *nullDefinition
) {
  const void *driverAddress = NULL;
  *driverObject = NULL;

  if (!driverCode || !*driverCode) {
    if (driverTable)
      if (driverTable->address)
        return driverTable->address;
    return nullAddress;
  }

  if (isDriverCode(driverCode, nullDefinition)) return nullAddress;

  if (driverTable) {
    const DriverEntry *driverEntry = driverTable;
    while (driverEntry->address) {
      if (isDriverCode(driverCode, driverEntry->definition)) return driverEntry->address;
      ++driverEntry;
    }
  }

#ifdef ENABLE_SHARED_OBJECTS
  {
    char *libraryPath;
    const int libraryNameLength = strlen(MODULE_NAME) + strlen(driverCode) + strlen(MODULE_EXTENSION) + 3;
    char libraryName[libraryNameLength];
    snprintf(libraryName, libraryNameLength, "%s%c%s.%s",
             MODULE_NAME, typeLetter, driverCode, MODULE_EXTENSION);

    if ((libraryPath = makePath(driverDirectory, libraryName))) {
      void *libraryHandle = loadSharedObject(libraryPath);

      if (libraryHandle) {
        const int driverSymbolLength = strlen(symbolPrefix) + 8 + strlen(driverCode) + 1;
        char driverSymbol[driverSymbolLength];
        snprintf(driverSymbol, driverSymbolLength, "%s_driver_%s",
                 symbolPrefix, driverCode);

        if (findSharedSymbol(libraryHandle, driverSymbol, &driverAddress)) {
          *driverObject = libraryHandle;

          {
            const void *versionAddress = NULL;
            const int versionSymbolLength = strlen(symbolPrefix) + 9 + strlen(driverCode) + 1;
            char versionSymbol[versionSymbolLength];
            snprintf(versionSymbol, versionSymbolLength, "%s_version_%s",
                     symbolPrefix, driverCode);

            if (findSharedSymbol(libraryHandle, versionSymbol, &versionAddress)) {
              const char *actualVersion = versionAddress;
              static const char *expectedVersion = DRIVER_VERSION_STRING;

              if (strcmp(actualVersion, expectedVersion) != 0) {
                logMessage(LOG_WARNING, "%s %s driver version %s does not match expected version %s",
                           driverCode, typeName, actualVersion, expectedVersion);
              }
            } else {
              logMessage(LOG_WARNING, "cannot find %s %s driver version symbol: %s",
                         driverCode, typeName, versionSymbol);
            }
          }
        } else {
          logMessage(LOG_ERR, "cannot find %s driver symbol: %s", typeName, driverSymbol);
          unloadSharedObject(libraryHandle);
          driverAddress = NULL;
        }
      } else {
        logMessage(LOG_ERR, "cannot load %s driver: %s", typeName, libraryPath);
      }

      free(libraryPath);
    }
  }
#endif /* ENABLE_SHARED_OBJECTS */

  return driverAddress;
}

void
identifyDriver (
  const char *type,
  const DriverDefinition *definition,
  int full
) {
  {
    char buffer[0X100];

    STR_BEGIN(buffer, sizeof(buffer));
    STR_PRINTF("%s %s Driver:", definition->name, type);

    if (definition->version && *definition->version) {
      STR_PRINTF(" version %s", definition->version);
    }

    if (full) {
      STR_PRINTF(" [compiled on %s at %s]", definition->date, definition->time);
    }

    STR_END;
    logMessage(LOG_NOTICE, "%s", buffer);
  }

  if (full) {
    if (definition->developers && *definition->developers)
      logMessage(LOG_INFO, "   Developed by %s", definition->developers);
  }
}
