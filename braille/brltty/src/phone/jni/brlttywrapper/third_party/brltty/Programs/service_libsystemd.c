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

#include <systemd/sd-daemon.h>

#include "log.h"
#include "service.h"

int
installService (const char *name, const char *description, const char *configurationFile) {
  logUnsupportedFeature("service installation");
  return 0;
}

int
removeService (const char *name) {
  logUnsupportedFeature("service removal");
  return 0;
}

int
notifyServiceReady (void) {
  if (sd_notify(1, "READY=1") < 0) {
    logSystemError("sd_notify");
  }

  return 1;
}
