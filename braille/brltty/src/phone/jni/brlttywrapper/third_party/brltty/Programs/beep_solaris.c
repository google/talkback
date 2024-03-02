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

#include <fcntl.h>
#include <sys/kbio.h>
#include <sys/kbd.h>

#include "log.h"
#include "beep.h"

static int
getKeyboard (void) {
  static int keyboard = -1;
  if (keyboard == -1) {
    if ((keyboard = open("/dev/kbd", O_WRONLY)) != -1) {
      logMessage(LOG_DEBUG, "keyboard opened: fd=%d", keyboard);
    } else {
      logSystemError("keyboard open");
    }
  }
  return keyboard;
}

int
canBeep (void) {
  return getKeyboard() != -1;
}

int
asynchronousBeep (BeepFrequency frequency, BeepDuration duration) {
  return 0;
}

int
synchronousBeep (BeepFrequency frequency, BeepDuration duration) {
  return 0;
}

int
startBeep (BeepFrequency frequency) {
  int keyboard = getKeyboard();
  if (keyboard != -1) {
    int command = KBD_CMD_BELL;
    if (ioctl(keyboard, KIOCCMD, &command) != -1) return 1;
    logSystemError("ioctl KIOCCMD KBD_CMD_BELL");
  }
  return 0;
}

int
stopBeep (void) {
  int keyboard = getKeyboard();
  if (keyboard != -1) {
    int command = KBD_CMD_NOBELL;
    if (ioctl(keyboard, KIOCCMD, &command) != -1) return 1;
    logSystemError("ioctl KIOCCMD KBD_CMD_NOBELL");
  }
  return 0;
}

void
endBeep (void) {
}
