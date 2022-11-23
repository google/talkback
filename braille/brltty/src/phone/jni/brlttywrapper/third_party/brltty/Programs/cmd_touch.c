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

#include "log.h"
#include "cmd_utils.h"
#include "cmd_queue.h"
#include "cmd_touch.h"
#include "brl_cmds.h"
#include "brl_utils.h"
#include "report.h"
#include "bitmask.h"
#include "prefs.h"

typedef struct {
  struct {
    ReportListenerInstance *brailleWindowUpdated;
  } reportListeners;

  BITMASK(touched, 88, int);
  unsigned char cells[88];
  unsigned int count;
  unsigned int activeCells;
  unsigned int lastActive;
  int lastTouched;
} TouchCommandData;

static void
resetTouched (TouchCommandData *tcd) {
  tcd->activeCells = 0;
  tcd->lastTouched = -1;
  BITMASK_ZERO(tcd->touched);

  for (int i = 0; i < tcd->count; ++i) {
    if (tcd->cells[i]) {
      BITMASK_SET(tcd->touched, i);
      tcd->lastActive = i;
      tcd->activeCells += 1;
    }
  }
}

static void
handleTouchAt (int offset, TouchCommandData *tcd) {
  tcd->lastTouched = offset;
  BITMASK_CLEAR(tcd->touched, offset);
}

static void
handleTouchOff (TouchCommandData *tcd) {
  int ok = 0;

  if (prefs.touchNavigation && (tcd->lastTouched > ((int)tcd->lastActive - 2))) {
    BITMASK_COUNT(tcd->touched, unread);

    if (tcd->activeCells && unread == 0) {
      ok = 1;
    }

    if (!ok && tcd->activeCells && unread) {
      float factor = (float)tcd->activeCells / unread;

      if (factor > 6) ok = 1;
    }
  }

  if (ok) {
    resetTouched(tcd);
    handleCommand(BRL_CMD_NXNBWIN);
  }
}

static void
handleBrailleWindowUpdated (
  const BrailleWindowUpdatedReport *report, TouchCommandData *tcd
) {
  if (cellsHaveChanged(&tcd->cells[0], report->cells, report->count, NULL, NULL, NULL)) {
    tcd->count = report->count;

    resetTouched(tcd);
  }
}

REPORT_LISTENER(brailleWindowUpdatedListener) {
  TouchCommandData *tcd = parameters->listenerData;
  const BrailleWindowUpdatedReport *report = parameters->reportData;

  handleBrailleWindowUpdated(report, tcd);
}

static TouchCommandData *
newTouchCommandData (void) {
  TouchCommandData *tcd;

  if ((tcd = malloc(sizeof(*tcd)))) {
    memset(tcd, 0, sizeof(*tcd));

    if ((tcd->reportListeners.brailleWindowUpdated = registerReportListener(REPORT_BRAILLE_WINDOW_UPDATED, brailleWindowUpdatedListener, tcd))) {
      return tcd;
    }

    free(tcd);
  } else {
    logMallocError();
  }

  return NULL;
}

static void
destroyTouchCommandData (TouchCommandData *tcd) {
  unregisterReportListener(tcd->reportListeners.brailleWindowUpdated);
  free(tcd);
}

static int
handleTouchCommands (int command, void *data) {
  switch (command & BRL_MSK_BLK) {
    case BRL_CMD_BLK(TOUCH_AT): {
      int arg = command & BRL_MSK_ARG;

      if (arg == BRL_MSK_ARG) {
        handleTouchOff(data);
      } else if (isTextOffset(&arg, 0, 0)) {
        handleTouchAt(arg, data);
      }

      break;
    }

    default:
      return 0;
  }

  return 1;
}

static void
destructTouchCommandData (void *data) {
  TouchCommandData *tcd = data;

  destroyTouchCommandData(tcd);
}

int
addTouchCommands (void) {
  TouchCommandData *tcd;

  if ((tcd = newTouchCommandData())) {
    if (pushCommandHandler("touch", KTB_CTX_DEFAULT, handleTouchCommands,
                           destructTouchCommandData, tcd)) {
      return 1;
    }

    destroyTouchCommandData(tcd);
  }

  return 0;
}
