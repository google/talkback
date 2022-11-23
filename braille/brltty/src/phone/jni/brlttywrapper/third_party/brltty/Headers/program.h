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

#ifndef BRLTTY_INCLUDED_PROGRAM
#define BRLTTY_INCLUDED_PROGRAM

#include "pid.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  PROG_EXIT_SUCCESS  = 0,
  PROG_EXIT_FORCE    = 1,
  PROG_EXIT_SYNTAX   = 2,
  PROG_EXIT_SEMANTIC = 3,
  PROG_EXIT_FATAL    = 4
} ProgramExitStatus;

extern const char *programPath;
extern const char *programName;

extern const char standardStreamArgument[];
extern const char standardInputName[];
extern const char standardOutputName[];
extern const char standardErrorName[];

extern void beginProgram (int argumentCount, char **argumentVector);
extern void endProgram (void);

typedef void ProgramExitHandler (void *data);
extern void onProgramExit (const char *name, ProgramExitHandler *handler, void *data);
extern void registerProgramMemory (const char *name, void *pointer);

extern int fixInstallPath (char **path);

extern int createPidFile (const char *path, ProcessIdentifier pid);
extern int cancelProgram (const char *pidFile);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PROGRAM */
