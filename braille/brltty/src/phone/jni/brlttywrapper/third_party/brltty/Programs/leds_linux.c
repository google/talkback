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
#include "leds.h"

#ifdef HAVE_LINUX_INPUT_H
#include <linux/input.h>

#include "system_linux.h"

static InputEventMonitor *inputEventMonitor = NULL;

static int
prepareUinputObject (UinputObject *uinput) {
  if (!enableUinputEventType(uinput, EV_LED)) return 0;
  if (!enableUinputLed(uinput, LED_NUML)) return 0;
  if (!enableUinputLed(uinput, LED_CAPSL)) return 0;
  if (!enableUinputLed(uinput, LED_SCROLLL)) return 0;
  return 1;
}

static void
handleInputEvent (const InputEvent *event) {
  switch (event->type) {
    case EV_LED: {
      switch (event->code) {
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
canMonitorLeds (void) {
  return 1;
}

int
startMonitoringLeds (void) {
  if (!inputEventMonitor) {
    InputEventMonitor *monitor = newInputEventMonitor(
      "Keyboard LED Monitor", prepareUinputObject, handleInputEvent
    );

    if (!monitor) return 0;
    inputEventMonitor = monitor;
  }

  return 1;
}

void
stopMonitoringLeds (void) {
  if (inputEventMonitor) {
    destroyInputEventMonitor(inputEventMonitor);
    inputEventMonitor = NULL;
  }
}

#else /* HAVE_LINUX_INPUT_H */
int
canMonitorLeds (void) {
  return 0;
}

int
startMonitoringLeds (void) {
  return 0;
}

void
stopMonitoringLeds (void) {
}
#endif /* HAVE_LINUX_INPUT_H */
