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
#include "scr.h"
#include "scr_real.h"
#include "routing.h"

static int
routeCursor_RealScreen (int column, int row, int screen) {
  return startRouting(column, row, screen);
}

void
initializeRealScreen (MainScreen *main) {
  initializeMainScreen(main);
  main->base.routeCursor = routeCursor_RealScreen;
}
