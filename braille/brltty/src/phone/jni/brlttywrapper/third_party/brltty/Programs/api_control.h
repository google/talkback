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

#ifndef BRLTTY_INCLUDED_API_CONTROL
#define BRLTTY_INCLUDED_API_CONTROL

#include "brlapi_param.h"
#include "ktb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  void (*logServerIdentity) (int full);
  const char *const * (*getServerParameters) (void);

  int (*startServer) (char **parameters);
  void (*stopServer) (void);
  int (*isServerRunning) (void);

  void (*linkServer) (void);
  void (*unlinkServer) (void);
  int (*isServerLinked) (void);

  void (*suspendDriver) (void);
  int (*resumeDriver) (void);

  int (*claimDriver) (void);
  void (*releaseDriver) (void);

  int (*handleCommand) (int command);
  int (*handleKeyEvent) (KeyGroup group, KeyNumber number, int press);

  int (*flushOutput) (void);
  void (*updateParameter) (brlapi_param_t parameter, brlapi_param_subparam_t subparam);
} ApiMethods;

extern const ApiMethods api;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_API_CONTROL */
