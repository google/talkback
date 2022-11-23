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

#ifndef BRLTTY_INCLUDED_SCR_SPECIAL
#define BRLTTY_INCLUDED_SCR_SPECIAL

#include "scr_internal.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern void beginSpecialScreens (void);
extern void endSpecialScreens (void);

typedef enum {
  SCR_FROZEN,
  SCR_MENU,
  SCR_HELP,
} SpecialScreenType;

extern int activateSpecialScreen (SpecialScreenType type);
extern void deactivateSpecialScreen (SpecialScreenType type);
extern int haveSpecialScreen (SpecialScreenType type);
extern int isSpecialScreen (SpecialScreenType type);

extern int constructHelpScreen (void);
extern int addHelpPage (void);
extern unsigned int getHelpPageCount (void);
extern unsigned int getHelpPageNumber (void);
extern int setHelpPageNumber (unsigned int number);
extern int clearHelpPage (void);
extern int addHelpLine (const wchar_t *characters);
extern unsigned int getHelpLineCount (void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SCR_SPECIAL */
