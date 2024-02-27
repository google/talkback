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

#ifndef BRLTTY_INCLUDED_CMDLINE
#define BRLTTY_INCLUDED_CMDLINE

#include "cmdline_types.h"
#include "datafile.h"
#include "program.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern ProgramExitStatus processOptions(const CommandLineDescriptor *descriptor,
                                        int *argumentCount,
                                        char ***argumentVector);

#define PROCESS_OPTIONS(descriptorVariable, argcVariable, argvVariable)    \
  {                                                                        \
    ProgramExitStatus exitStatus =                                         \
        processOptions(&descriptorVariable, &argcVariable, &argvVariable); \
    if (exitStatus == PROG_EXIT_FORCE) return PROG_EXIT_SUCCESS;           \
    if (exitStatus != PROG_EXIT_SUCCESS) return exitStatus;                \
  }

extern void resetOptions(const CommandLineOptions *options);

typedef struct {
  void (*beginStream)(const char *name, void *data);
  void (*endStream)(int incomplete, void *data);
  DataFileParameters dataFileParameters;
} InputFilesProcessingParameters;

extern ProgramExitStatus processInputFiles(
    char **paths, int count, const InputFilesProcessingParameters *parameters);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CMDLINE */
