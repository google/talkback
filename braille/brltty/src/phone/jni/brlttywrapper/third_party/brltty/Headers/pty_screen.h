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

#ifndef BRLTTY_INCLUDED_PTY_SCREEN
#define BRLTTY_INCLUDED_PTY_SCREEN

#include "get_curses.h"
#include "pty_object.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int ptyBeginScreen(PtyObject *pty, int driverDirectives);
extern void ptyEndScreen(void);
extern void ptyRefreshScreen(void);

extern void ptySetCursorPosition(unsigned int row, unsigned int column);
extern void ptySetCursorRow(unsigned int row);
extern void ptySetCursorColumn(unsigned int column);

extern void ptySaveCursorPosition(void);
extern void ptyRestoreCursorPosition(void);

extern void ptySetScrollRegion(unsigned int top, unsigned int bottom);
extern int ptyAmWithinScrollRegion(void);
extern void ptyScrollDown(unsigned int count);
extern void ptyScrollUp(unsigned int count);

extern void ptyMoveCursorUp(unsigned int amount);
extern void ptyMoveCursorDown(unsigned int amount);
extern void ptyMoveCursorLeft(unsigned int amount);
extern void ptyMoveCursorRight(unsigned int amount);

extern void ptyMoveUp1(void);
extern void ptyMoveDown1(void);

extern void ptyTabBackward(void);
extern void ptyTabForward(void);

extern void ptyInsertLines(unsigned int count);
extern void ptyDeleteLines(unsigned int count);

extern void ptyInsertCharacters(unsigned int count);
extern void ptyDeleteCharacters(unsigned int count);
extern void ptyAddCharacter(unsigned char character);

extern void ptySetCursorVisibility(unsigned int visibility);
extern void ptySetAttributes(attr_t attributes);
extern void ptyAddAttributes(attr_t attributes);
extern void ptyRemoveAttributes(attr_t attributes);
extern void ptySetForegroundColor(int color);
extern void ptySetBackgroundColor(int color);

extern void ptyClearToEndOfLine(void);
extern void ptyClearToBeginningOfLine(void);
extern void ptyClearToEndOfDisplay(void);

extern void ptySetScreenLogLevel(unsigned char level);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PTY_SCREEN */
