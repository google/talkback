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

#include <stdio.h>

#include "parameters.h"
#include "update.h"
#include "scr.h"
#include "scr_main.h"

static int
poll_MainScreen (void) {
  return 1;
}

static int
processParameters_MainScreen (char **parameters) {
  return 1;
}

static void
releaseParameters_MainScreen (void) {
}

static int
construct_MainScreen (void) {
  return 1;
}

static void
destruct_MainScreen (void) {
}

static int
userVirtualTerminal_MainScreen (int number) {
  return 1 + number;
}

void
initializeMainScreen (MainScreen *main) {
  initializeBaseScreen(&main->base);
  main->base.poll = poll_MainScreen;

  main->processParameters = processParameters_MainScreen;
  main->releaseParameters = releaseParameters_MainScreen;

  main->construct = construct_MainScreen;
  main->destruct = destruct_MainScreen;

  main->userVirtualTerminal = userVirtualTerminal_MainScreen;
}

void
mainScreenUpdated (void) {
  if (isMainScreen()) {
    scheduleUpdateIn("main screen updated", SCREEN_UPDATE_SCHEDULE_DELAY);
  }
}
