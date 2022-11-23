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
#include "parameters.h"
#include "device.h"
#include "get_select.h"
#include "async_alarm.h"
#include "scr.h"
#include "scr_gpm.h"

#ifdef HAVE_LIBGPM
#include <gpm.h>
extern int gpm_tried;

typedef enum {
  GCS_CLOSED,
  GCS_FAILED,
  GCS_OPENED
} GpmConnectionState;

static int gpmConnectionState = GCS_CLOSED;

ASYNC_ALARM_CALLBACK(gpmResetConnection) {
  gpmConnectionState = GCS_CLOSED;
}

static int
gpmOpenConnection (void) {
  switch (gpmConnectionState) {
    case  GCS_CLOSED: {
      Gpm_Connect options = {
        .eventMask = GPM_MOVE,
        .defaultMask = ~0,
        .minMod = 0,
        .maxMod = ~0
      };

      gpm_tried = 0;
      gpm_zerobased = 1;

      if (Gpm_Open(&options, -1) == -1) {
        logMessage(LOG_DEBUG, "GPM open error: %s", strerror(errno));
        asyncNewRelativeAlarm(NULL, GPM_CONNECTION_RESET_DELAY, gpmResetConnection, NULL);
        gpmConnectionState = GCS_FAILED;
        return 0;
      }

      logMessage(LOG_DEBUG, "GPM opened: fd=%d con=%d", gpm_fd, gpm_consolefd);
      gpmConnectionState = GCS_OPENED;
    }

    case GCS_OPENED:
      return 1;
  }

  return 0;
}

static void
gpmCloseConnection (int alreadyClosed) {
  if (gpmConnectionState == GCS_OPENED) {
    if (!alreadyClosed) Gpm_Close();
    logMessage(LOG_DEBUG, "GPM closed");
  }
  gpmConnectionState = GCS_CLOSED;
}
#endif /* HAVE_LIBGPM */

static int
highlightRegion_GpmScreen (int left, int right, int top, int bottom) {
#ifdef HAVE_LIBGPM
  FILE *console = getConsole();

  if (console) {
    if (gpmOpenConnection() && (gpm_fd >= 0)) {
      if (Gpm_DrawPointer(left, top, fileno(console)) != -1) return 1;

      if (errno != EINVAL) {
        logMessage(LOG_DEBUG, "Gpm_DrawPointer error: %s", strerror(errno));
        gpmCloseConnection(0);
        return 0;
      }
    }
  }
#endif /* HAVE_LIBGPM */

  return 0;
}

static int
getPointer_GpmScreen (int *column, int *row) {
  int ok = 0;

#ifdef HAVE_LIBGPM
  if (gpmOpenConnection()) {
    if (gpm_fd >= 0) {
      int error = 0;

      while (1) {
        fd_set mask;
        struct timeval timeout;
        Gpm_Event event;
        int result;

        FD_ZERO(&mask);
        FD_SET(gpm_fd, &mask);
        memset(&timeout, 0, sizeof(timeout));

        if ((result = select(gpm_fd+1, &mask, NULL, NULL, &timeout)) == 0) break;
        error = 1;

        if (result == -1) {
          if (errno == EINTR) continue;
          logSystemError("select");
          break;
        }

        if (!FD_ISSET(gpm_fd, &mask)) {
          logMessage(LOG_DEBUG, "GPM file descriptor not set: %d", gpm_fd);
          break;
        }

        if ((result = Gpm_GetEvent(&event)) == -1) {
          if (errno == EINTR) continue;
          logSystemError("Gpm_GetEvent");
          break;
        }

        error = 0;
        if (result == 0) {
          gpmCloseConnection(1);
          break;
        }

        *column = event.x;
        *row = event.y;
        ok = 1;
      }

      if (error) gpmCloseConnection(0);
    }
  }
#endif /* HAVE_LIBGPM */

  return ok;
}

void
includeGpmScreen (MainScreen *main) {
  main->base.highlightRegion = highlightRegion_GpmScreen;
  main->base.getPointer = getPointer_GpmScreen;
}
