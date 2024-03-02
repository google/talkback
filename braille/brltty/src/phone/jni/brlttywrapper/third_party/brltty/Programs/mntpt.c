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
#include <string.h>
#include <errno.h>

#include "log.h"
#include "parameters.h"
#include "mntfs.h"
#include "async_alarm.h"
#include "mntpt.h"

#if defined(USE_PKG_MNTPT_NONE)
#include "mntpt_none.h"
#elif defined(USE_PKG_MNTPT_MNTENT)
#include "mntpt_mntent.h"
#elif defined(USE_PKG_MNTPT_MNTTAB)
#include "mntpt_mnttab.h"
#else /* mount point package */
#error mount point package not selected
#include "mntpt_none.h"
#endif /* mount point package */

#include "mntpt_internal.h"

#if defined(MNTOPT_RW)
#define MOUNT_OPTION_RW MNTOPT_RW
#else /* MOUNT_OPTION_RW */
#define MOUNT_OPTION_RW "rw"
#endif /* MOUNT_OPTION_RW */

char *
findMountPoint (MountPointTester test) {
  char *path = NULL;
  FILE *table;

  if ((table = openMountsTable(0))) {
    MountEntry *entry;

    while ((entry = readMountsTable(table))) {
      if (test(entry->mountPath, entry->mountType)) {
        if (!(path = strdup(entry->mountPath))) logMallocError();
        break;
      }
    }

    closeMountsTable(table);
  }

  return path;
}

static void updateMountsTable (MountEntry *entry);

ASYNC_ALARM_CALLBACK(retryMountsTableUpdate) {
  MountEntry *entry = parameters->data;
  updateMountsTable(entry);
}

static void
updateMountsTable (MountEntry *entry) {
  int retry = 0;

  {
    FILE *table;

    if ((table = openMountsTable(1))) {
      addMountEntry(table, entry);
      closeMountsTable(table);
    } else if ((errno == EROFS) || (errno == EACCES)) {
      retry = 1;
    }
  }

  if (retry) {
    asyncNewRelativeAlarm(NULL, MOUNT_TABLE_UPDATE_RETRY_INTERVAL, retryMountsTableUpdate, entry);
  } else {
    if (entry->mountPath) free(entry->mountPath);
    if (entry->mountReference) free(entry->mountReference);
    if (entry->mountType) free(entry->mountType);
    if (entry->mountOptions) free(entry->mountOptions);
    free(entry);
  }
}

int
makeMountPoint (const char *path, const char *reference, const char *type) {
  if (mountFileSystem(path, reference, type)) {
    MountEntry *entry;

    logMessage(LOG_NOTICE, "file system mounted: %s[%s] -> %s",
               type, reference, path);

    if ((entry = malloc(sizeof(*entry)))) {
      memset(entry, 0, sizeof(*entry));

      if ((entry->mountPath = strdup(path))) {
        if ((entry->mountReference = strdup(reference))) {
          if ((entry->mountType = strdup(type))) {
            if ((entry->mountOptions = strdup(MOUNT_OPTION_RW))) {
              updateMountsTable(entry);
              return 1;
            } else {
              logMallocError();
            }

            free(entry->mountType);
          } else {
            logMallocError();
          }

          free(entry->mountReference);
        } else {
          logMallocError();
        }

        free(entry->mountPath);
      } else {
        logMallocError();
      }

      free(entry);
    } else {
      logMallocError();
    }
  } else {
    logMessage(LOG_ERR, "file system mount error: %s[%s] -> %s: %s",
               type, reference, path, strerror(errno));
  }

  return 0;
}
