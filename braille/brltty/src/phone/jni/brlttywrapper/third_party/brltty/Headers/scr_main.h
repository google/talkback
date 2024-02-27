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

#ifndef BRLTTY_INCLUDED_SCR_MAIN
#define BRLTTY_INCLUDED_SCR_MAIN

#include "scr_base.h"
#include "driver.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  BaseScreen base;

  int (*processParameters) (char **parameters);
  void (*releaseParameters) (void);

  int (*construct) (void);
  void (*destruct) (void);

  int (*userVirtualTerminal) (int number);
} MainScreen;

extern void initializeMainScreen (MainScreen *);

struct ScreenDriverStruct {
  DRIVER_DEFINITION_DECLARATION;
  const char *const *parameters;

  void (*initialize) (MainScreen *main);		/* initialize speech device */
};

extern void mainScreenUpdated (void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SCR_MAIN */
