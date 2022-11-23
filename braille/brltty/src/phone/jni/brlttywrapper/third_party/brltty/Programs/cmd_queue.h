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

#ifndef BRLTTY_INCLUDED_CMD_QUEUE
#define BRLTTY_INCLUDED_CMD_QUEUE

#include "ktb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int beginCommandQueue (void);
extern void endCommandQueue (void);

extern void suspendCommandQueue (void);
extern void resumeCommandQueue (void);

typedef void *CommandPreprocessor (void);
typedef void CommandPostprocessor (void *state, int command, int handled);

extern int pushCommandEnvironment (
  const char *name,
  CommandPreprocessor *preprocessCommand,
  CommandPostprocessor *postprocessCommand
);

extern int popCommandEnvironment (void);

typedef int CommandHandler (int command, void *data);
typedef void CommandDataDestructor (void *data);

extern int pushCommandHandler (
  const char *name,
  KeyTableCommandContext context,
  CommandHandler *handler,
  CommandDataDestructor *destructor,
  void *data
);

extern int popCommandHandler (void);

extern int handleCommand (int command);
extern KeyTableCommandContext getCurrentCommandContext (void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CMD_QUEUE */
