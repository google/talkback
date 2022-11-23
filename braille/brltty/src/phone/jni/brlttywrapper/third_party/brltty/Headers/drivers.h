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

#ifndef BRLTTY_INCLUDED_DRIVERS
#define BRLTTY_INCLUDED_DRIVERS

#include "driver.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const void *address;
  const DriverDefinition *definition;
} DriverEntry;

extern int isDriverAvailable (const char *code, const char *codes);
extern int isDriverIncluded (const char *code, const DriverEntry *table);
extern int haveDriver (const char *code, const char *codes, const DriverEntry *table);
extern const char *getDefaultDriver (const DriverEntry *table);

extern const void *loadDriver (
  const char *driverCode, void **driverObject,
  const char *driverDirectory, const DriverEntry *driverTable,
  const char *typeName, char typeLetter, const char *symbolPrefix,
  const void *nullAddress, const DriverDefinition *nullDefinition
);

extern void identifyDriver (
  const char *type,
  const DriverDefinition *definition,
  int full
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_DRIVERS */
