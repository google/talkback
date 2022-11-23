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
#include "parameters.h"
#include "brl_cmds.h"
#include "ktb_types.h"
#include "cmd_queue.h"
#include "cmd_learn.h"
#include "learn.h"
#include "async_task.h"
#include "update.h"
#include "message.h"
#include "core.h"

typedef struct {
  int timeout;
} LearnModeParameters;

ASYNC_TASK_CALLBACK(presentLearnMode) {
  LearnModeParameters *lmp = data;

  suspendUpdates();
  learnMode(lmp->timeout);
  resumeUpdates(1);
  free(lmp);
}

static int
handleLearnCommands (int command, void *data) {
  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_LEARN: {
      LearnModeParameters *lmp;

      if ((lmp = malloc(sizeof(*lmp)))) {
        memset(lmp, 0, sizeof(*lmp));
        lmp->timeout = LEARN_MODE_TIMEOUT;

        if (asyncAddTask(NULL, presentLearnMode, lmp)) break;
        free(lmp);
      } else {
        logMallocError();
      }

      brl.hasFailed = 1;
      break;
    }

    default:
      return 0;
  }

  return 1;
}

int
addLearnCommands (void) {
  return pushCommandHandler("learn", KTB_CTX_DEFAULT,
                            handleLearnCommands, NULL, NULL);
}
