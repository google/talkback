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
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/soundcard.h>

#include "log.h"
#include "io_misc.h"
#include "midi.h"

#define MIDI_OSS_DEVICE_PATH "/dev/sequencer";

struct MidiDeviceStruct {
  int fileDescriptor;
  int deviceNumber;
/*SEQ_DEFINEBUF(0X80);*/
  unsigned char buffer[0X80];
  int bufferLength;
  int bufferUsed;
};

#ifndef SAMPLE_TYPE_AWE
#define SAMPLE_TYPE_AWE 0x20
#endif /* SAMPLE_TYPE_AWE */

static MidiDevice *midiDevice = NULL;
#define _seqbuf midiDevice->buffer
#define _seqbuflen midiDevice->bufferLength
#define _seqbufptr midiDevice->bufferUsed

void
seqbuf_dump (void) {
  if (_seqbufptr)
    if (writeFile(midiDevice->fileDescriptor, _seqbuf, _seqbufptr) == -1)
      logSystemError("MIDI write");
  _seqbufptr = 0;
}

MidiDevice *
openMidiDevice (int errorLevel, const char *device) {
  MidiDevice *midi;
  if ((midi = malloc(sizeof(*midi)))) {
    if (!*device) device = MIDI_OSS_DEVICE_PATH;
    if ((midi->fileDescriptor = open(device, O_WRONLY)) != -1) {
      {
        int count;
        int awe = -1;
        int fm = -1;
        int gus = -1;
        int ext = -1;

        if (ioctl(midi->fileDescriptor, SNDCTL_SEQ_NRSYNTHS, &count) != -1) {
          int index;
          for (index=0; index<count; ++index) {
            struct synth_info info;
            info.device = index;
            if (ioctl(midi->fileDescriptor, SNDCTL_SYNTH_INFO, &info) != -1) {
              switch (info.synth_type) {
                case SYNTH_TYPE_SAMPLE:
                  switch (info.synth_subtype) {
                    case SAMPLE_TYPE_AWE:
                      awe = index;
                      continue;

                    case SAMPLE_TYPE_GUS:
                      gus = index;
                      continue;
                  }
                  break;

                case SYNTH_TYPE_FM:
                  fm = index;
                  continue;
              }

              logMessage(LOG_DEBUG, "Unknown synthesizer: %d[%d]: %s",
                         info.synth_type, info.synth_subtype, info.name);
            } else {
              logMessage(errorLevel, "Cannot get description for synthesizer %d: %s",
                         index, strerror(errno));
            }
          }

          if (gus >= 0)
            if (ioctl(midi->fileDescriptor, SNDCTL_SEQ_RESETSAMPLES, &gus) == -1)
              logMessage(errorLevel, "Cannot reset samples for gus synthesizer %d: %s",
                         gus, strerror(errno));
        } else {
          logMessage(errorLevel, "Cannot get MIDI synthesizer count: %s",
                     strerror(errno));
        }

        if (ioctl(midi->fileDescriptor, SNDCTL_SEQ_NRMIDIS, &count) != -1) {
          if (count > 0) ext = count - 1;
        } else {
          logMessage(errorLevel, "Cannot get MIDI device count: %s",
                     strerror(errno));
        }

        midi->deviceNumber = (awe >= 0)? awe:
                             (gus >= 0)? gus:
                             (fm >= 0)? fm:
                             (ext >= 0)? ext:
                             0;
      }

      midi->bufferLength = sizeof(midi->buffer);
      midi->bufferUsed = 0;

      return midi;
    } else {
      logMessage(errorLevel, "Cannot open MIDI device: %s: %s", device, strerror(errno));
    }

    free(midi);
  } else {
    logSystemError("MIDI device allocation");
  }
  return NULL;
}

void
closeMidiDevice (MidiDevice *midi) {
  close(midi->fileDescriptor);
  free(midi);
}

static void
beginMidiOperation (MidiDevice *midi) {
  midiDevice = midi;
}

static int
endMidiOperation (MidiDevice *midi) {
  midiDevice = NULL;
  return 1;
}

int
flushMidiDevice (MidiDevice *midi) {
  beginMidiOperation(midi);
  seqbuf_dump();
  return endMidiOperation(midi);
}

int
setMidiInstrument (MidiDevice *midi, unsigned char channel, unsigned char instrument) {
  beginMidiOperation(midi);
  SEQ_SET_PATCH(midi->deviceNumber, channel, instrument);
  return endMidiOperation(midi);
}

int
beginMidiBlock (MidiDevice *midi) {
  beginMidiOperation(midi);
  SEQ_START_TIMER();
  return endMidiOperation(midi);
}

int
endMidiBlock (MidiDevice *midi) {
  beginMidiOperation(midi);
  SEQ_STOP_TIMER();
  seqbuf_dump();
  ioctl(midi->fileDescriptor, SNDCTL_SEQ_SYNC);
  return endMidiOperation(midi);
}

int
startMidiNote (MidiDevice *midi, unsigned char channel, unsigned char note, unsigned char volume) {
  beginMidiOperation(midi);
  SEQ_START_NOTE(midi->deviceNumber, channel, note, 0X7F*volume/100);
  return endMidiOperation(midi);
}

int
stopMidiNote (MidiDevice *midi, unsigned char channel) {
  beginMidiOperation(midi);
  SEQ_STOP_NOTE(midi->deviceNumber, channel, 0, 0);
  return endMidiOperation(midi);
}

int
insertMidiWait (MidiDevice *midi, int duration) {
  beginMidiOperation(midi);
  SEQ_DELTA_TIME((duration + 9) / 10);
  return endMidiOperation(midi);
}
