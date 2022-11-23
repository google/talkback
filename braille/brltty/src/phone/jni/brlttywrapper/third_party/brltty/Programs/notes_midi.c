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

#include <string.h>
#include <errno.h>

#include "prefs.h"
#include "log.h"
#include "midi.h"
#include "notes.h"

char *opt_midiDevice;

struct NoteDeviceStruct {
  MidiDevice *midi;
  int channelNumber;
};

static NoteDevice *
midiConstruct (int errorLevel) {
  NoteDevice *device;

  if ((device = malloc(sizeof(*device)))) {
    if ((device->midi = openMidiDevice(errorLevel, opt_midiDevice))) {
      device->channelNumber = 0;
      setMidiInstrument(device->midi, device->channelNumber, prefs.midiInstrument);

      logMessage(LOG_DEBUG, "MIDI enabled");
      return device;
    }

    free(device);
  } else {
    logMallocError();
  }

  logMessage(LOG_DEBUG, "MIDI not available");
  return NULL;
}

static void
midiDestruct (NoteDevice *device) {
  closeMidiDevice(device->midi);
  free(device);
  logMessage(LOG_DEBUG, "MIDI disabled");
}

static int
midiNote (NoteDevice *device, unsigned int duration, unsigned char note) {
  logMessage(LOG_DEBUG, "tone: MSecs:%u Note:%u", duration, note);
  beginMidiBlock(device->midi);

  if (note) {
    startMidiNote(device->midi, device->channelNumber, note, prefs.midiVolume);
    insertMidiWait(device->midi, duration);
    stopMidiNote(device->midi, device->channelNumber);
  } else {
    insertMidiWait(device->midi, duration);
  }

  endMidiBlock(device->midi);
  return 1;
}

static int
midiTone (NoteDevice *device, unsigned int duration, NoteFrequency frequency) {
  return midiNote(device, duration, getNearestNote(frequency));
}

static int
midiFlush (NoteDevice *device) {
  return flushMidiDevice(device->midi);
}

const NoteMethods midiNoteMethods = {
  .construct = midiConstruct,
  .destruct = midiDestruct,

  .tone = midiTone,
  .note = midiNote,
  .flush = midiFlush
};
