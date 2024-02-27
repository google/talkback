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

#ifndef BRLTTY_INCLUDED_HOSTCMD_INTERNAL
#define BRLTTY_INCLUDED_HOSTCMD_INTERNAL

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  FILE **const *const streamVariable;
  const int fileDescriptor;
  const unsigned isInput:1;

  HostCommandPackageData package;
} HostCommandStream;

typedef int HostCommandStreamProcessor (HostCommandStream *hcs, void *data);

extern int processHostCommandStreams (
  HostCommandStream *hcs,
  HostCommandStreamProcessor *processStream,
  void *data
);

extern int finishHostCommandStream (HostCommandStream *hcs, int fileDescriptor);

extern int constructHostCommandPackageData (HostCommandPackageData *pkg);
extern void destructHostCommandPackageData (HostCommandPackageData *pkg);
extern int prepareHostCommandStream (HostCommandStream *hcs, void *data);

extern int runCommand (
  int *result,
  const char *const *command,
  HostCommandStream *streams,
  int asynchronous
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_HOSTCMD_INTERNAL */
