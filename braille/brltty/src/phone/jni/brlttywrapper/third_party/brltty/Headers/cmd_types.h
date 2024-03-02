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

#ifndef BRLTTY_INCLUDED_CMD_TYPES
#define BRLTTY_INCLUDED_CMD_TYPES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const char *name;
  const char *description;
  int code;

  unsigned char isToggle : 1;
  unsigned char isMotion : 1;
  unsigned char isRow : 1;
  unsigned char isVertical : 1;
  unsigned char isHorizontal : 1;
  unsigned char isPanning : 1;

  unsigned char isInput : 1;
  unsigned char isCharacter : 1;
  unsigned char isBraille : 1;

  unsigned char isKeyboard : 1;

  unsigned char isRouting : 1;
  unsigned char isColumn : 1;
  unsigned char isOffset : 1;
  unsigned char isRange : 1;
} CommandEntry;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CMD_TYPES */
