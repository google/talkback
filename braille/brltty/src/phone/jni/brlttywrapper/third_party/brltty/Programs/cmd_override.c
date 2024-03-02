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

#include <string.h>

#include "log.h"
#include "alert.h"
#include "cmd_queue.h"
#include "cmd_override.h"
#include "cmd_utils.h"
#include "brl_cmds.h"
#include "scr.h"
#include "prefs.h"
#include "core.h"

typedef struct {
  int row;

  struct {
    int first;
    int last;
  } column;
} SelectionEndpoint;

typedef struct {
  struct {
    SelectionEndpoint start;
    SelectionEndpoint end;
    unsigned char started:1;
  } selection;
} OverrideCommandData;

static int
getSelectionEndpoint (int arg, SelectionEndpoint *endpoint) {
  return getCharacterCoordinates(arg, &endpoint->row, &endpoint->column.first, &endpoint->column.last, 0);
}

static int
compareSelectionEndpoints (const SelectionEndpoint *endpoint1, const SelectionEndpoint *endpoint2) {
  if (endpoint1->row < endpoint2->row) return -1;
  if (endpoint1->row > endpoint2->row) return 1;

  if (endpoint1->column.first < endpoint2->column.first) return -1;
  if (endpoint1->column.first > endpoint2->column.first) return 1;

  return 0;
}

static int
setSelection (const SelectionEndpoint *start, const SelectionEndpoint *end, OverrideCommandData *ocd) {
  {
    const SelectionEndpoint *from = start;
    const SelectionEndpoint *to = end;

    if (compareSelectionEndpoints(from, to) > 0) {
      const SelectionEndpoint *temp = from;
      from = to;
      to = temp;
    }

    if (!setScreenTextSelection(from->column.first, from->row, to->column.last, to->row)) {
      return 0;
    }
  }

  ocd->selection.start = *start;
  ocd->selection.end = *end;
  return 1;
}

static int
startSelection (const SelectionEndpoint *start, OverrideCommandData *ocd) {
  return setSelection(start, start, ocd);
}

static int
handleOverrideCommands (int command, void *data) {
  OverrideCommandData *ocd = data;
  if (!scr.hasSelection) ocd->selection.started = 0;

  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_TXTSEL_CLEAR: {
      if (clearScreenTextSelection()) {
        ocd->selection.started = 0;
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }

      break;
    }

    default: {
      int blk = command & BRL_MSK_BLK;
      int arg = command & BRL_MSK_ARG;
      int ext = BRL_CODE_GET(EXT, command);

      switch (blk) {
        case BRL_CMD_BLK(TXTSEL_SET): {
          if (ext > arg) {
            SelectionEndpoint start;

            if (getSelectionEndpoint(arg, &start)) {
              SelectionEndpoint end;

              if (getSelectionEndpoint(ext, &end)) {
                if (setSelection(&start, &end, ocd)) {
                  ocd->selection.started = 0;
                  break;
                }
              }
            }
          }

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        case BRL_CMD_BLK(TXTSEL_START): {
          SelectionEndpoint start;

          if (getSelectionEndpoint(arg, &start)) {
            if (startSelection(&start, ocd)) {
              ocd->selection.started = 1;
              break;
            }
          }

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        case BRL_CMD_BLK(ROUTE): {
          if (!ocd->selection.started && !prefs.startSelectionWithRoutingKey) return 0;
          SelectionEndpoint endpoint;

          if (getSelectionEndpoint(arg, &endpoint)) {
            if (ocd->selection.started) {;
              if (setSelection(&ocd->selection.start, &endpoint, ocd)) break;
            } else if ((endpoint.row != scr.posy) || !((endpoint.column.first <= scr.posx) && (scr.posx <= endpoint.column.last))) {
              return 0;
            } else if (startSelection(&endpoint, ocd)) {
              ocd->selection.started = 1;
              break;
            }
          }

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        default:
          return 0;
      }

      break;
    }
  }

  return 1;
}

static void
destroyOverrideCommandData (void *data) {
  OverrideCommandData *ocd = data;
  free(ocd);
}

int
addOverrideCommands (void) {
  OverrideCommandData *ocd;

  if ((ocd = malloc(sizeof(*ocd)))) {
    memset(ocd, 0, sizeof(*ocd));

    ocd->selection.started = 0;

    if (pushCommandHandler("override", KTB_CTX_DEFAULT,
                           handleOverrideCommands, destroyOverrideCommandData, ocd)) {
      return 1;
    }

    free(ocd);
  } else {
    logMallocError();
  }

  return 0;
}
