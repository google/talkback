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

#ifndef BRLTTY_INCLUDED_SCR
#define BRLTTY_INCLUDED_SCR

#include "scr_types.h"
#include "ktb_types.h"
#include "driver.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int isMainScreen (void);

/* Routines which apply to the current screen. */
extern int pollScreen (void);
extern int refreshScreen (void);
extern void describeScreen (ScreenDescription *);		/* get screen status */
extern int readScreen (short left, short top, short width, short height, ScreenCharacter *buffer);
extern int readScreenText (short left, short top, short width, short height, wchar_t *buffer);
extern int insertScreenKey (ScreenKey key);
extern int routeScreenCursor (int column, int row, int screen);
extern int highlightScreenRegion (int left, int right, int top, int bottom);
extern int unhighlightScreenRegion (void);
extern int getScreenPointer (int *column, int *row);
extern int clearScreenTextSelection (void);
extern int setScreenTextSelection (int startColumn, int startRow, int endColumn, int endRow);
extern int currentVirtualTerminal (void);
extern int selectScreenVirtualTerminal (int vt);
extern int switchScreenVirtualTerminal (int vt);
extern int nextScreenVirtualTerminal (void);
extern int previousScreenVirtualTerminal (void);
extern int userVirtualTerminal (int number);
extern int handleScreenCommands (int command, void *data);
extern KeyTableCommandContext getScreenCommandContext (void);

static inline int
readScreenRows (int row, int width, int height, ScreenCharacter *buffer) {
  return readScreen(0, row, width, height, buffer);
}

static inline int
readScreenRow (int row, int width, ScreenCharacter *buffer) {
  return readScreenRows(row, width, 1, buffer);
}

/* Routines which apply to the routing screen.
 * An extra `thread' for the cursor routing subprocess.
 * This is needed because the forked subprocess shares its parent's
 * file descriptors.  A readScreen equivalent is not needed.
 */
extern int constructRoutingScreen (void);
extern void destructRoutingScreen (void);

extern const ScreenDriver *screen;
extern const ScreenDriver noScreen;
extern void setNoScreen (void);

extern const char *const *getScreenParameters (const ScreenDriver *driver);

extern int haveScreenDriver (const char *code);
extern const char *getDefaultScreenDriver (void);

extern const ScreenDriver *loadScreenDriver (const char *code, void **driverObject, const char *driverDirectory);
extern int constructScreenDriver (char **parameters);
extern void destructScreenDriver (void);

extern void identifyScreenDriver (const ScreenDriver *driver, int full);
extern void identifyScreenDrivers (int full);

extern void setNoScreenDriverReason (const char *reason);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SCR */
