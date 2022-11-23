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

#define VBSIZE 40
#define LPTPORT 0x278
#define VBDELAY 3
#define VBCLOCK 5
#define VBREFRESHDELAY 40 // Min. time to wait before updating the display

// Don't touch the following definitions
#define VBLPTSTROBE 0x40
#define VBLPTCLOCK 0x20
#define VBLPTDATA 0x80

#define KEY_UP 0x1
#define KEY_LEFT 0x2
#define KEY_RIGHT 0x4
#define KEY_DOWN 0x8
#define KEY_ATTRIBUTES 0x10
#define KEY_CURSOR 0x20
#define KEY_HOME 0x40
#define KEY_MENU 0x80

typedef struct {
  unsigned char bigbuttons;
  char routingkey : 7;
  char keypressed : 1;
} vbButtons;

