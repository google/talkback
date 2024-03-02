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

#include <stdio.h>
#include <errno.h>

#include "log.h"
#include "mntpt_none.h"
#include "mntpt_internal.h"

FILE *
openMountsTable (int update) {
  logUnsupportedFunction();
  return NULL;
}

void
closeMountsTable (FILE *table) {
}

MountEntry *
readMountsTable (FILE *table) {
  return NULL;
}

int
addMountEntry (FILE *table, MountEntry *entry) {
  return 0;
}
