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

#ifndef BRLTTY_INCLUDED_API_TYPES
#define BRLTTY_INCLUDED_API_TYPES

#include "ktb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  void (*identify) (int full);
  const char *const * (*getParameters) (void);

  int (*start) (char **parameters);
  void (*stop) (void);
  int (*isStarted) (void);

  void (*link) (void);
  void (*unlink) (void);
  int (*isLinked) (void);

  void (*suspend) (void);
  int (*resume) (void);

  int (*claimDriver) (void);
  void (*releaseDriver) (void);

  int (*handleCommand) (int command);
  int (*handleKeyEvent) (KeyGroup group, KeyNumber number, int press);

  int (*flush) (void);
} ApiMethods;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_API_TYPES */
