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
#include "drivers.h"
#include "brl.h"

#define BRLSYMBOL noBraille
#define DRIVER_NAME NoBraille
#define DRIVER_CODE no
#define DRIVER_COMMENT "no braille support"
#define DRIVER_VERSION ""
#define DRIVER_DEVELOPERS ""
#include "brl_driver.h"
#include "brl.auto.h"

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if (connectResource(brl, "null:")) {
    return 1;
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl UNUSED) {
  disconnectBrailleResource(brl, NULL);
}

static int
brl_readCommand (BrailleDisplay *brl UNUSED, KeyTableCommandContext context UNUSED) {
  return EOF;
}

static int
brl_writeWindow (BrailleDisplay *brl UNUSED, const wchar_t *characters UNUSED) {
  return 1;
}

const BrailleDriver *braille = &noBraille;

int
haveBrailleDriver (const char *code) {
  return haveDriver(code, BRAILLE_DRIVER_CODES, driverTable);
}

const char *
getDefaultBrailleDriver (void) {
  return getDefaultDriver(driverTable);
}

const BrailleDriver *
loadBrailleDriver (const char *code, void **driverObject, const char *driverDirectory) {
  return loadDriver(code, driverObject,
                    driverDirectory, driverTable,
                    "braille", 'b', "brl",
                    &noBraille, &noBraille.definition);
}

void
identifyBrailleDriver (const BrailleDriver *driver, int full) {
  identifyDriver("Braille", &driver->definition, full);
}

void
identifyBrailleDrivers (int full) {
  const DriverEntry *entry = driverTable;
  while (entry->address) {
    const BrailleDriver *driver = entry++->address;
    identifyBrailleDriver(driver, full);
  }
}
