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

#include <string.h>
#include <errno.h>

#include "log.h"
#include "pgmpath.h"

char *
getProgramPath (void) {
  char *path = NULL;
  size_t size = 0X80;
  char *buffer = NULL;

  while (1) {
    {
      char *newBuffer = realloc(buffer, size<<=1);

      if (!newBuffer) {
        logMallocError();
        break;
      }

      buffer = newBuffer;
    }

    {
      int length = readlink("/proc/self/path/a.out", buffer, size);

      if (length == -1) {
        if (errno != ENOENT) logSystemError("readlink");
        break;
      }

      if (length < size) {
        buffer[length] = 0;
        if (!(path = strdup(buffer))) logMallocError();
        break;
      }
    }
  }

  if (buffer) free(buffer);
  return path;
}
