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

#include "log.h"
#include "embed.h"
#include "parameters.h"
#include "brl_input.h"
#include "brl_utils.h"
#include "brl_cmds.h"
#include "cmd_queue.h"
#include "cmd_enqueue.h"
#include "io_generic.h"
#include "core.h"

static int
processInput (void) {
  int command = readBrailleCommand(&brl, getCurrentCommandContext());

  if (command != EOF) {
    switch (command & BRL_MSK_CMD) {
      case BRL_CMD_OFFLINE:
        setBrailleOffline(&brl);
        return 0;

      default:
        break;
    }
  }

  setBrailleOnline(&brl);
  if (command == EOF) return 0;
  enqueueCommand(command);
  return 1;
}

static
GIO_INPUT_HANDLER(handleBrailleInput) {
  int processed = 0;

  {
    int error = parameters->error;

    if (error) {
      logActionError(error, "braille input monitor");
      brl.hasFailed = 1;
      return 0;
    }
  }

  suspendCommandQueue();

  if (!brl.isSuspended) {
    if (brl.api) brl.api->claimDriver();
    if (processInput()) processed = 1;
    if (brl.api) brl.api->releaseDriver();
  }

#ifdef ENABLE_API
  else if (brl.api && brl.api->isStarted()) {
    switch (readBrailleCommand(&brl, KTB_CTX_DEFAULT)) {
      case BRL_CMD_RESTARTBRL:
        brl.hasFailed = 1;
        break;

      default:
        processed = 1;
      case EOF:
        break;
    }
  }
#endif /* ENABLE_API */

  resumeCommandQueue();
  return processed;
}

static GioHandleInputObject *handleBrailleInputObject = NULL;

void
stopBrailleInput (void) {
  if (handleBrailleInputObject) {
    gioDestroyHandleInputObject(handleBrailleInputObject);
    handleBrailleInputObject = NULL;
  }
}

void
startBrailleInput (void) {
  stopBrailleInput();
  handleBrailleInputObject = gioNewHandleInputObject(brl.gioEndpoint, BRAILLE_DRIVER_INPUT_POLL_INTERVAL, handleBrailleInput, &brl);
}
