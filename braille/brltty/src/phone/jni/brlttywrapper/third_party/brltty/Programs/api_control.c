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
#include "api_control.h"
#include "api_server.h"
#include "core.h"

#ifndef ENABLE_API
const char *const api_parameters[] = {NULL};

void
api_identify (int full) {
}

int
api_start (BrailleDisplay *brl, char **parameters) {
  return 0;
}

void
api_stop (BrailleDisplay *brl) {
}

void
api_link (BrailleDisplay *brl) {
}

void
api_unlink (BrailleDisplay *brl) {
}

void
api_suspend (BrailleDisplay *brl) {
}

int
api_resume (BrailleDisplay *brl) {
  return 0;
}

int
api_claimDriver (BrailleDisplay *brl) {
  return 0;
}

void
api_releaseDriver (BrailleDisplay *brl) {
}

int
api_handleCommand (int command) {
  return 0;
}

int
api_handleKeyEvent (KeyGroup group, KeyNumber number, int press) {
  return 0;
}

int
api_flush (BrailleDisplay *brl) {
  return 0;
}
#endif /* ENABLE_API */

static int apiStarted = 0;
static int apiLinked = 0;
static int driverClaimed = 0;

static void
apiIdentify (int full) {
  api_identify(full);
}

static const char *const *
apiGetParameters (void) {
  return api_parameters;
}

static int
apiStart (char **parameters) {
  if (api_start(&brl, parameters)) {
    apiStarted = 1;
    return 1;
  }

  return 0;
}

static void
apiStop (void) {
  api_stop(&brl);
  apiStarted = 0;
}

static int
apiIsStarted (void) {
  return apiStarted;
}

static void
apiLink (void) {
  if (apiStarted) {
    api_link(&brl);
    apiLinked = 1;
  }
}

static void
apiUnlink (void) {
  if (apiStarted) {
    api_unlink(&brl);
    apiLinked = 0;
  }
}

static int
apiIsLinked (void) {
  return apiLinked;
}

static void
apiSuspend (void) {
#ifdef ENABLE_API
  if (apiStarted) {
    api_suspend(&brl);
  } else
#endif /* ENABLE_API */

  {
    destructBrailleDriver();
  }
}

static int
apiResume (void) {
#ifdef ENABLE_API
  if (apiStarted) return api_resume(&brl);
#endif /* ENABLE_API */

  return constructBrailleDriver();
}

static int
apiClaimDriver (void) {
  if (!driverClaimed && apiStarted) {
    if (!api_claimDriver(&brl)) return 0;
    driverClaimed = 1;
  }

  return 1;
}

static void
apiReleaseDriver (void) {
  if (driverClaimed) {
    api_releaseDriver(&brl);
    driverClaimed = 0;
  }
}

static int
apiHandleCommand (int command) {
  if (!apiStarted) return 0;
  return api_handleCommand(command);
}

static int
apiHandleKeyEvent (KeyGroup group, KeyNumber number, int press) {
  if (!apiStarted) return 0;
  return api_handleKeyEvent(group, number, press);
}

static int
apiFlush (void) {
  if (!apiStarted) return 1;
  return api_flush(&brl);
}

const ApiMethods api = {
  .identify = apiIdentify,
  .getParameters = apiGetParameters,

  .start = apiStart,
  .stop = apiStop,
  .isStarted = apiIsStarted,

  .link = apiLink,
  .unlink = apiUnlink,
  .isLinked = apiIsLinked,

  .suspend = apiSuspend,
  .resume = apiResume,

  .claimDriver = apiClaimDriver,
  .releaseDriver = apiReleaseDriver,

  .handleCommand = apiHandleCommand,
  .handleKeyEvent = apiHandleKeyEvent,

  .flush = apiFlush
};
