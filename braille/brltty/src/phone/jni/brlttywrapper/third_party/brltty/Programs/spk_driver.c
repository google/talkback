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

#include "drivers.h"
#include "spk.h"
#include "spk.auto.h"

#define SPKSYMBOL noSpeech
#define DRIVER_NAME NoSpeech
#define DRIVER_CODE no
#define DRIVER_COMMENT "no speech support"
#define DRIVER_VERSION ""
#define DRIVER_DEVELOPERS ""
#include "spk_driver.h"

static int
spk_construct (volatile SpeechSynthesizer *spk, char **parameters) {
  return 1;
}

static void
spk_destruct (volatile SpeechSynthesizer *spk) {
}

static void
spk_say (volatile SpeechSynthesizer *spk, const unsigned char *text, size_t length, size_t count, const unsigned char *attributes) {
}

static void
spk_mute (volatile SpeechSynthesizer *spk) {
}

const SpeechDriver *speech = &noSpeech;

int
haveSpeechDriver (const char *code) {
  return haveDriver(code, SPEECH_DRIVER_CODES, driverTable);
}

const char *
getDefaultSpeechDriver (void) {
  return getDefaultDriver(driverTable);
}

const SpeechDriver *
loadSpeechDriver (const char *code, void **driverObject, const char *driverDirectory) {
  return loadDriver(code, driverObject,
                    driverDirectory, driverTable,
                    "speech", 's', "spk",
                    &noSpeech, &noSpeech.definition);
}

void
identifySpeechDriver (const SpeechDriver *driver, int full) {
  identifyDriver("Speech", &driver->definition, full);
}

void
identifySpeechDrivers (int full) {
  const DriverEntry *entry = driverTable;
  while (entry->address) {
    const SpeechDriver *driver = entry++->address;
    identifySpeechDriver(driver, full);
  }
}
