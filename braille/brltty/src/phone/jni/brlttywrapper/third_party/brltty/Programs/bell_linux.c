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

#include "bell.h"

#ifdef HAVE_LINUX_INPUT_H
#include <linux/input.h>

#include "system_linux.h"

static InputEventMonitor *inputEventMonitor = NULL;

static int
prepareUinputObject (UinputObject *uinput) {
  if (!enableUinputEventType(uinput, EV_SND)) return 0;
  if (!enableUinputSound(uinput, SND_BELL)) return 0;
  return 1;
}

static void
handleInputEvent (const InputEvent *event) {
  switch (event->type) {
    case EV_SND: {
      int value = event->value;

      switch (event->code) {
        case SND_BELL:
          if (value) alertConsoleBell();
          break;

        default:
          break;
      }

      break;
    }

    default:
      break;
  }
}

int
canMonitorConsoleBell (void) {
  return 1;
}

int
startMonitoringConsoleBell (void) {
  if (!inputEventMonitor) {
    InputEventMonitor *monitor = newInputEventMonitor(
      "Console Bell Monitor", prepareUinputObject, handleInputEvent
    );

    if (!monitor) return 0;
    inputEventMonitor = monitor;
  }

  return 1;
}

void
stopMonitoringConsoleBell (void) {
  if (inputEventMonitor) {
    destroyInputEventMonitor(inputEventMonitor);
    inputEventMonitor = NULL;
  }
}

#else /* HAVE_LINUX_INPUT_H */
int
canMonitorConsoleBell (void) {
  return 0;
}

int
startMonitoringConsoleBell (void) {
  return 0;
}

void
stopMonitoringConsoleBell (void) {
}
#endif /* HAVE_LINUX_INPUT_H */
