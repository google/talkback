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
#include <string.h>
#include <errno.h>

#include "log.h"

#include "mntpt_mnttab.h"
#include "mntpt_internal.h"

FILE *
openMountsTable (int update) {
  FILE *table = fopen(MNTTAB, (update? "a": "r"));
  if (!table)
    logMessage((errno == ENOENT)? LOG_WARNING: LOG_ERR,
               "mounted file systems table open erorr: %s: %s",
               MNTTAB, strerror(errno));
  return table;
}

void
closeMountsTable (FILE *table) {
  fclose(table);
}

MountEntry *
readMountsTable (FILE *table) {
  static struct mnttab entry;
  if (getmntent(table, &entry) == 0) return &entry;
  return NULL;
}

int
addMountEntry (FILE *table, MountEntry *entry) {
  errno = ENOSYS;
  if (!putmntent(table, entry)) return 1;
  logMessage(LOG_ERR, "mounts table entry add error: %s[%s] -> %s: %s",
             entry->mnt_fstype, entry->mnt_special, entry->mnt_mountp, strerror(errno));
  return 0;
}
