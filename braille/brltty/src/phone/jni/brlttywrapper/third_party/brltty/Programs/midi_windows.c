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

#include "log.h"
#include "parse.h"
#include "timing.h"
#include "midi.h"

struct MidiDeviceStruct {
  HMIDIOUT handle;
  unsigned char note;
  int count;
  char buffer[0X80];
};

typedef enum {
  MIDI_NoteOff        = 0X80,
  MIDI_NoteOn         = 0X90,
  MIDI_KeyPressure    = 0XA0,
  MIDI_ControlChange  = 0XB0,
  MIDI_ProgramChange  = 0XC0,
  MIDI_ChannelPresure = 0XD0,
  MIDI_PitchBend      = 0XE0,
  MIDI_SystemPrefix   = 0XF0
} MidiEvent;

static void
logMidiOutError (MMRESULT error, int errorLevel, const char *action) {
  char text[MAXERRORLENGTH];
  midiOutGetErrorText(error, text, sizeof(text));
  logMessage(errorLevel, "%s error %d: %s", action, error, text);
}

static int
addMidiMessage (MidiDevice *midi, const unsigned char *message, int length) {
  if ((midi->count + length) > sizeof(midi->buffer))
    if (!flushMidiDevice(midi))
      return 0;

  memcpy(&midi->buffer[midi->count], message, length);
  midi->count += length;
  return 1;
}

static int
writeMidiMessage (MidiDevice *midi, const unsigned char *message, int length) {
  if (!addMidiMessage(midi, message, length)) return 0;
  if (!flushMidiDevice(midi)) return 0;
  return 1;
}

MidiDevice *
openMidiDevice (int errorLevel, const char *device) {
  MidiDevice *midi;
  MMRESULT error;
  int id = 0;
  static const char *const defaultDevice = "default";

  if (!*device) device = defaultDevice;

  if (strcmp(device, defaultDevice) == 0) {
    id = -1;
  } else if (!isInteger(&id, device) || (id < 0) || (id >= midiOutGetNumDevs())) {
    int count = midiOutGetNumDevs();
    for (id=0; id<count; ++id) {
      MIDIOUTCAPS cap;
      if (midiOutGetDevCaps(id, &cap, sizeof(cap)) == MMSYSERR_NOERROR)
        if (strncasecmp(device, cap.szPname, strlen(device)) == 0)
          break;
    }

    if (id == count) {
      logMessage(errorLevel, "invalid MIDI device number: %s", device);
      return NULL;
    }
  }

  if ((midi = malloc(sizeof(*midi)))) {
    if ((error = midiOutOpen(&midi->handle, id, 0, 0, CALLBACK_NULL)) == MMSYSERR_NOERROR) {
      midi->note = 0;
      midi->count = 0;
      return midi;
    } else {
      logMidiOutError(error, errorLevel, "MIDI device open");
    }

    free(midi);
  } else {
    logSystemError("MIDI device allocation");
  }
  return NULL;
}

void
closeMidiDevice (MidiDevice *midi) {
  flushMidiDevice(midi);
  midiOutClose(midi->handle);
  free(midi);
}

int
flushMidiDevice (MidiDevice *midi) {
  int ok = 1;

  if (midi->count > 0) {
    MMRESULT error;
    MIDIHDR header;
    
    header.lpData = midi->buffer;
    header.dwBufferLength = midi->count;
    header.dwFlags = 0;

    if ((error = midiOutPrepareHeader(midi->handle, &header, sizeof(header))) == MMSYSERR_NOERROR) {
      if ((error = midiOutLongMsg(midi->handle, &header, sizeof(header))) == MMSYSERR_NOERROR) {
        midi->count = 0;
      } else {
        logMidiOutError(error, LOG_ERR, "midiOutLongMsg");
        ok = 0;
      }

      while ((error = midiOutUnprepareHeader(midi->handle, &header, sizeof(header))) == MIDIERR_STILLPLAYING) {
        approximateDelay(1);
      }

      if (error != MMSYSERR_NOERROR) {
        logMidiOutError(error, LOG_ERR, "midiOutUnprepareHeader");
      }
    } else {
      logMidiOutError(error, LOG_ERR, "midiOutPrepareHeader");
      ok = 0;
    }
  }

  return ok;
}

int
setMidiInstrument (MidiDevice *midi, unsigned char channel, unsigned char instrument) {
  const unsigned char message[] = {
    MIDI_ProgramChange|channel, instrument
  };
  return writeMidiMessage(midi, message, sizeof(message));
}

int
beginMidiBlock (MidiDevice *midi) {
  return 1;
}

int
endMidiBlock (MidiDevice *midi) {
  return 1;
}

int
startMidiNote (MidiDevice *midi, unsigned char channel, unsigned char note, unsigned char volume) {
  const unsigned char message[] = {
    MIDI_NoteOn|channel, note, (0X7F * volume / 100)
  };
  int ok = writeMidiMessage(midi, message, sizeof(message));
  if (ok) midi->note = note;
  return ok;
}

int
stopMidiNote (MidiDevice *midi, unsigned char channel) {
  const unsigned char message[] = {
    MIDI_NoteOff|channel, midi->note, 0
  };
  int ok = writeMidiMessage(midi, message, sizeof(message));
  if (ok) midi->note = 0;
  return ok;
}

int
insertMidiWait (MidiDevice *midi, int duration) {
  approximateDelay(duration);
  return 1;
}
