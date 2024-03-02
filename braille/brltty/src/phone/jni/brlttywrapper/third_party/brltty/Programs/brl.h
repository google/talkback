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

#ifndef BRLTTY_INCLUDED_BRL
#define BRLTTY_INCLUDED_BRL

#include "prologue.h"

#include "brl_types.h"
#include "ktb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern void constructBrailleDisplay (BrailleDisplay *brl);
extern int ensureBrailleBuffer (BrailleDisplay *brl, int infoLevel);
extern void destructBrailleDisplay (BrailleDisplay *brl);

extern void fillTextRegion (
  wchar_t *text, unsigned char *dots,
  unsigned int start, unsigned int count,
  unsigned int columns, unsigned int rows,
  const wchar_t *characters, size_t length
);

extern void fillDotsRegion (
  wchar_t *text, unsigned char *dots,
  unsigned int start, unsigned int count,
  unsigned int columns, unsigned int rows,
  const unsigned char *cells, size_t length
);

extern int clearStatusCells (BrailleDisplay *brl);
extern int setStatusText (BrailleDisplay *brl, const char *text);

extern int readBrailleCommand (BrailleDisplay *, KeyTableCommandContext);

extern int canRefreshBrailleDisplay (BrailleDisplay *brl);
extern int refreshBrailleDisplay (BrailleDisplay *brl);

extern int canRefreshBrailleRow (BrailleDisplay *brl);
extern int refreshBrailleRow (BrailleDisplay *brl, int row);

extern int canSetBrailleFirmness (BrailleDisplay *brl);
extern int setBrailleFirmness (BrailleDisplay *brl, BrailleFirmness setting);

extern int canSetTouchSensitivity (BrailleDisplay *brl);
extern int setTouchSensitivity (BrailleDisplay *brl, TouchSensitivity setting);

extern int canSetAutorepeatProperties (BrailleDisplay *brl);
extern int setAutorepeatProperties (BrailleDisplay *brl, int on, int delay, int interval);

extern int haveBrailleDriver (const char *code);
extern const char *getDefaultBrailleDriver (void);
extern const BrailleDriver *loadBrailleDriver (const char *code, void **driverObject, const char *driverDirectory);
extern void identifyBrailleDriver (const BrailleDriver *driver, int full);
extern void identifyBrailleDrivers (int full);
extern const BrailleDriver *braille;
extern const BrailleDriver noBraille;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BRL */
