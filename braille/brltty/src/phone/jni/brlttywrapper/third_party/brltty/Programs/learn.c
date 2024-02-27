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
#include "learn.h"
#include "message.h"
#include "async_wait.h"
#include "cmd.h"
#include "cmd_queue.h"
#include "brl.h"
#include "brl_cmds.h"
#include "core.h"

typedef enum {
  LMS_CONTINUE,
  LMS_TIMEOUT,
  LMS_EXIT,
  LMS_ERROR
} LearnModeState;

typedef struct {
  const char *mode;
  LearnModeState state;
} LearnModeData;

ASYNC_CONDITION_TESTER(testEndLearnWait) {
  LearnModeData *lmd = data;

  return lmd->state != LMS_TIMEOUT;
}

static int
handleLearnModeCommands (int command, void *data) {
  LearnModeData *lmd = data;

  logMessage(LOG_DEBUG, "learn: command=%06X", command);
  lmd->state = LMS_CONTINUE;

  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_LEARN:
      lmd->state = LMS_EXIT;
      return 1;

    case BRL_CMD_NOOP:
      return 1;

    default:
      switch (command & BRL_MSK_BLK) {
        case BRL_CMD_BLK(TOUCH_AT):
          return 1;

        default:
          break;
      }
      break;
  }

  {
    char buffer[0X100];

    describeCommand(buffer, sizeof(buffer), command,
                    (CDO_IncludeName | CDO_IncludeOperand));

    logMessage(LOG_DEBUG, "learn: %s", buffer);
    if (!message(lmd->mode, buffer, MSG_SYNC|MSG_NODELAY)) lmd->state = LMS_ERROR;
  }

  return 1;
}

int
learnMode (int timeout) {
  LearnModeData lmd = {
    .mode = "lrn"
  };

  pushCommandEnvironment("learnMode", NULL, NULL);
  pushCommandHandler("learnMode", KTB_CTX_DEFAULT,
                     handleLearnModeCommands, NULL, &lmd);

  if (setStatusText(&brl, lmd.mode)) {
    if (message(lmd.mode, gettext("Learn Mode"), MSG_SYNC|MSG_NODELAY)) {
      do {
        lmd.state = LMS_TIMEOUT;
        if (!asyncAwaitCondition(timeout, testEndLearnWait, &lmd)) break;
      } while (lmd.state == LMS_CONTINUE);

      if (lmd.state == LMS_TIMEOUT) {
        if (!message(lmd.mode, gettext("done"), MSG_SYNC)) {
          lmd.state = LMS_ERROR;
        }
      }
    }
  }

  popCommandEnvironment();
  return lmd.state != LMS_ERROR;
}
