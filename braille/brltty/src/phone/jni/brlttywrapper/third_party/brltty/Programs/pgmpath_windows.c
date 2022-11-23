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
#include "pgmpath.h"
#include "system_windows.h"

char *
getProgramPath (void) {
  char *path = NULL;
  HMODULE handle;

  if ((handle = GetModuleHandle(NULL))) {
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
        DWORD length = GetModuleFileName(handle, buffer, size);

        if (!length) {
          logWindowsSystemError("GetModuleFileName");
          break;
        }

        if (length < size) {
          buffer[length] = 0;
          if ((path = strdup(buffer))) {
            while (length > 0)
              if (path[--length] == '\\')
                path[length] = '/';
          } else {
            logMallocError();
          }

          break;
        }
      }
    }

    free(buffer);
  } else {
    logWindowsSystemError("GetModuleHandle");
  }

  return path;
}
