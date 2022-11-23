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
#include "async_wait.h"
#include "beep.h"
#include "notes.h"

struct NoteDeviceStruct {
  char mustHaveAtLeastOneField;
};

static NoteDevice *
beepConstruct (int errorLevel) {
  NoteDevice *device;

  if ((device = malloc(sizeof(*device)))) {
    if (canBeep()) {
      logMessage(LOG_DEBUG, "beeper enabled");
      return device;
    }

    free(device);
  } else {
    logMallocError();
  }

  logMessage(LOG_DEBUG, "beeper not available");
  return NULL;
}

static void
beepDestruct (NoteDevice *device) {
  endBeep();
  free(device);
  logMessage(LOG_DEBUG, "beeper disabled");
}

static int
beepTone (NoteDevice *device, unsigned int duration, NoteFrequency frequency) {
  uint32_t pitch = frequency;
  logMessage(LOG_DEBUG, "tone: MSecs:%u Freq:%"PRIu32, duration, pitch);

  if (!pitch) {
    asyncWait(duration);
    return 1;
  }

  return playBeep(pitch, duration);
}

static int
beepNote (NoteDevice *device, unsigned int duration, unsigned char note) {
  return beepTone(device, duration, getNoteFrequency(note));
}

static int
beepFlush (NoteDevice *device) {
  return 1;
}

const NoteMethods beepNoteMethods = {
  .construct = beepConstruct,
  .destruct = beepDestruct,

  .tone = beepTone,
  .note = beepNote,
  .flush = beepFlush
};
