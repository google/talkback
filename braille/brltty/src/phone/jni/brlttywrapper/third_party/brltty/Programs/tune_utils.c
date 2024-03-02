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

#include <string.h>

#include "log.h"
#include "tune.h"
#include "tune_utils.h"
#include "tune_types.h"
#include "midi.h"
#include "parse.h"
#include "prefs.h"

static const char *tuneDeviceNames[] = {
  "beeper",
  "pcm",
  "midi",
  "fm",
  NULL
};

const char *
getTuneDeviceName (TuneDevice device) {
  return tuneDeviceNames[device];
}

int
parseTuneDevice (const char *setting) {
  if (setting && *setting) {
    unsigned int device;

    if (!validateChoice(&device, setting, tuneDeviceNames)) {
      logMessage(LOG_ERR, "%s: %s", "invalid tune device", setting);
      return 0;
    }

    prefs.tuneDevice = device;
  }

  return 1;
}

int
setTuneDevice (void) {
  unsigned char device = prefs.tuneDevice;

  if (!tuneSetDevice(device)) {
    logMessage(LOG_ERR, "unsupported tune device: %s", getTuneDeviceName(device));
    return 0;
  }

  return 1;
}

int
parseTuneVolume (const char *setting) {
  if (setting && *setting) {
    static const int minimum = 0;
    static const int maximum = 100;
    int volume;

    if (!validateInteger(&volume, setting, &minimum, &maximum)) {
      logMessage(LOG_ERR, "%s: %s", "invalid volume percentage", setting);
      return 0;
    }

    switch (prefs.tuneDevice) {
      case tdPcm:
        prefs.pcmVolume = volume;
        break;

      case tdMidi:
        prefs.midiVolume = volume;
        break;

      case tdFm:
        prefs.fmVolume = volume;
        break;

      default:
        break;
    }
  }

  return 1;
}

#ifdef HAVE_MIDI_SUPPORT
static int
validateMidiInstrument (unsigned char *value, const char *string) {
  size_t stringLength = strlen(string);
  unsigned char instrument;
  for (instrument=0; instrument<midiInstrumentCount; ++instrument) {
    const char *component = midiInstrumentTable[instrument];
    size_t componentLeft = strlen(component);
    const char *word = string;
    size_t wordLeft = stringLength;
    {
      const char *delimiter = memchr(component, '(', componentLeft);
      if (delimiter) componentLeft = delimiter - component;
    }
    while (1) {
      while (*component == ' ') component++, componentLeft--;
      if ((componentLeft == 0) != (wordLeft == 0)) break; 
      if (!componentLeft) {
        *value = instrument;
        return 1;
      }
      {
        size_t wordLength = wordLeft;
        size_t componentLength = componentLeft;
        const char *delimiter;
        if ((delimiter = memchr(word, '-', wordLeft))) wordLength = delimiter - word;
        if ((delimiter = memchr(component, ' ', componentLeft))) componentLength = delimiter - component;
        if (strncasecmp(word, component, wordLength) != 0) break;
        word += wordLength; wordLeft -= wordLength;
        if (*word) word++, wordLeft--;
        component += componentLength; componentLeft -= componentLength;
      }
    }
  }
  return 0;
}

int
parseTuneInstrument (const char *setting) {
  if (setting && *setting) {
    unsigned char instrument;

    if (!validateMidiInstrument(&instrument, setting)) {
      logMessage(LOG_ERR, "%s: %s", "invalid musical instrument", setting);
      return 0;
    }

    prefs.midiInstrument = instrument;
  }

  return 1;
}
#endif /* HAVE_MIDI_SUPPORT */
