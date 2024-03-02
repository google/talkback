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
#include "lock.h"
#include "file.h"
#include "atb.h"
#include "atb_internal.h"

static const unsigned char internalAttributesTableBytes[] = {
#include "atb.auto.h"
};

static AttributesTable internalAttributesTable = {
  .header.bytes = internalAttributesTableBytes,
  .size = 0
};

AttributesTable *attributesTable = &internalAttributesTable;

static LockDescriptor *
getAttributesTableLock (void) {
  static LockDescriptor *lock = NULL;
  return getLockDescriptor(&lock, "attributes-table");
}

void
lockAttributesTable (void) {
  obtainExclusiveLock(getAttributesTableLock());
}

void
unlockAttributesTable (void) {
  releaseLock(getAttributesTableLock());
}

unsigned char
convertAttributesToDots (AttributesTable *table, unsigned char attributes) {
  return table->header.fields->attributesToDots[attributes];
}

int
replaceAttributesTable (const char *directory, const char *name) {
  AttributesTable *newTable = NULL;

  if (*name) {
    char *path;

    if ((path = makeAttributesTablePath(directory, name))) {
      logMessage(LOG_DEBUG, "compiling attributes table: %s", path);

      if (!(newTable = compileAttributesTable(path))) {
        logMessage(LOG_ERR, "%s: %s", gettext("cannot compile attributes table"), path);
      }

      free(path);
    }
  } else {
    newTable = &internalAttributesTable;
  }

  if (newTable) {
    AttributesTable *oldTable = attributesTable;

    lockAttributesTable();
      attributesTable = newTable;
    unlockAttributesTable();

    destroyAttributesTable(oldTable);
    return 1;
  }

  logMessage(LOG_ERR, "%s: %s", gettext("cannot load attributes table"), name);
  return 0;
}
