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

#ifndef BRLTTY_INCLUDED_SCR_HELP
#define BRLTTY_INCLUDED_SCR_HELP

#include "scr_base.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  BaseScreen base;
  int (*construct) (void);
  void (*destruct) (void);

  unsigned int (*addPage) (void);
  unsigned int (*getPageCount) (void);
  unsigned int (*getPageNumber) (void);
  int (*setPageNumber) (unsigned int number);

  int (*clearPage) (void);
  int (*addLine) (const wchar_t *characters);
  unsigned int (*getLineCount) (void);
} HelpScreen;

extern void initializeHelpScreen (HelpScreen *help);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SCR_HELP */
