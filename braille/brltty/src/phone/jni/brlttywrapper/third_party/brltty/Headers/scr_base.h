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

#ifndef BRLTTY_INCLUDED_SCR_BASE
#define BRLTTY_INCLUDED_SCR_BASE

#include "scr_types.h"
#include "ktb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int isSpecialKey (ScreenKey key);
extern void setScreenKeyModifiers (ScreenKey *key, ScreenKey which);
extern void mapScreenKey (ScreenKey *key);

typedef struct {
  const char * (*getTitle) (void);

  void (*onForeground) (void);
  void (*onBackground) (void);

  int (*poll) (void);
  int (*refresh) (void);
  void (*describe) (ScreenDescription *);

  int (*readCharacters) (const ScreenBox *box, ScreenCharacter *buffer);
  int (*insertKey) (ScreenKey key);
  int (*routeCursor) (int column, int row, int screen);

  int (*highlightRegion) (int left, int right, int top, int bottom);
  int (*unhighlightRegion) (void);
  int (*getPointer) (int *column, int *row);

  int (*clearSelection) (void);
  int (*setSelection) (int startColumn, int startRow, int endColumn, int endRow);

  int (*currentVirtualTerminal) (void);
  int (*selectVirtualTerminal) (int vt);
  int (*switchVirtualTerminal) (int vt);
  int (*nextVirtualTerminal) (void);
  int (*previousVirtualTerminal) (void);

  int (*handleCommand) (int command);
  KeyTableCommandContext (*getCommandContext) (void);
} BaseScreen;

extern void initializeBaseScreen (BaseScreen *);
extern void describeBaseScreen (BaseScreen *, ScreenDescription *);

extern int validateScreenBox (const ScreenBox *box, int columns, int rows);
extern void setScreenMessage (const ScreenBox *box, ScreenCharacter *buffer, const char *message);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SCR_BASE */
