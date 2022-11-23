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

#include "cmd_queue.h"
#include "cmd_custom.h"
#include "brl_cmds.h"
#include "brl_custom.h"

static int
handleCustomCommands (int command, void *data) {
  switch (command & BRL_MSK_CMD) {
    default: {
      switch (command & BRL_MSK_BLK) {
        default:
          return 0;
      }

      break;
    }
  }

  return 1;
}

int
addCustomCommands (void) {
  return pushCommandHandler("custom", KTB_CTX_DEFAULT,
                            handleCustomCommands, NULL, NULL);
}
