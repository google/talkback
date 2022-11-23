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
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/kd.h>

#include "log.h"
#include "beep.h"
#include "system_linux.h"

#define BEEP_DEVICE_PATH "/dev/tty0"
#define TICKS_PER_SECOND 1193180

static int beepDevice = INVALID_FILE_DESCRIPTOR;

static inline BeepFrequency
getTicksPerWave (BeepFrequency frequency) {
  return frequency? (TICKS_PER_SECOND / frequency): 0;
}

static void
enableBeeps (void) {
  static unsigned char status = 0;

  installKernelModule("pcspkr", &status);
}

int
canBeep (void) {
  if (beepDevice == INVALID_FILE_DESCRIPTOR) {
    const char *path = BEEP_DEVICE_PATH;
    int device = open(path, O_WRONLY);

    if (device == -1) {
      logMessage(LOG_WARNING, "can't open beep device: %s: %s", path, strerror(errno));
      return 0;
    }

    beepDevice = device;
    enableBeeps();
  }

  return 1;
}

int
synchronousBeep (BeepFrequency frequency, BeepDuration duration) {
  return 0;
}

int
asynchronousBeep (BeepFrequency frequency, BeepDuration duration) {
  if (beepDevice != INVALID_FILE_DESCRIPTOR) {
    if (ioctl(beepDevice, KDMKTONE, ((duration << 0X10) | getTicksPerWave(frequency))) != -1) return 1;
    logSystemError("ioctl[KDMKTONE]");
  }

  return 0;
}

int
startBeep (BeepFrequency frequency) {
  if (beepDevice != INVALID_FILE_DESCRIPTOR) {
    if (ioctl(beepDevice, KIOCSOUND, getTicksPerWave(frequency)) != -1) return 1;
    logSystemError("ioctl[KIOCSOUND]");
  }

  return 0;
}

int
stopBeep (void) {
  return startBeep(0);
}

void
endBeep (void) {
  if (beepDevice != INVALID_FILE_DESCRIPTOR) {
    close(beepDevice);
    beepDevice = INVALID_FILE_DESCRIPTOR;
  }
}
