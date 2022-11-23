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

#ifndef BRLTTY_INCLUDED_TS_BRLDEFS
#define BRLTTY_INCLUDED_TS_BRLDEFS

#define TS_BAUD_LOW     4800
#define TS_BAUD_NORMAL  9600
#define TS_BAUD_HIGH   19200

typedef enum {
  /* Navigator - byte 1
   * Nav20/40: right side
   * Nav80: navigation keys, right thumb key
   * PB40: right rocker up/down, concave, forward/backward (on top)
   */
  TS_KEY_NavLeft    = 0,
  TS_KEY_NavUp      = 1,
  TS_KEY_NavRight   = 2,
  TS_KEY_NavDown    = 3,
  TS_KEY_ThumbRight = 4,

  /* Navigator - byte 2
   * Nav20/40: left side
   * nav80: cursor keys, left thumb key
   * PB40: left rocker up/down, convex
   */
  TS_KEY_CursorLeft  = 5,
  TS_KEY_CursorUp    = 6,
  TS_KEY_CursorRight = 7,
  TS_KEY_CursorDown  = 8,
  TS_KEY_ThumbLeft   = 9,

  // Power Braille - byte 6
  TS_KEY_Button1        = 5,
  TS_KEY_LeftRockerUp   = 6,
  TS_KEY_Button2        = 7,
  TS_KEY_LeftRockerDown = 8,
  TS_KEY_Convex         = 9,

  // Power Braille - byte 1
  TS_KEY_Switch1Up   = 10,
  TS_KEY_Switch1Down = 11,
  TS_KEY_Switch2Up   = 12,
  TS_KEY_Switch2Down = 13,

  // Power Braille - byte 2
  TS_KEY_Switch3Up   = 14,
  TS_KEY_Switch3Down = 15,
  TS_KEY_Switch4Up   = 16,
  TS_KEY_Switch4Down = 17,

  // Power Braille - byte 3
  TS_KEY_Bar3 = 18,
  TS_KEY_Bar4 = 20,

  // Power Braille - byte 4
  TS_KEY_Button3 = 21,
  TS_KEY_Button4 = 23,

  // Power Braille - byte 5
  TS_KEY_Bar1            = 24,
  TS_KEY_RightRockerUp   = 25,
  TS_KEY_Bar2            = 26,
  TS_KEY_RightRockerDown = 27,
  TS_KEY_Concave         = 28
} TS_NavigationKey;

typedef enum {
  TS_GRP_NavigationKeys = 0,
  TS_GRP_RoutingKeys
} TS_KeyGroup;

#endif /* BRLTTY_INCLUDED_TS_BRLDEFS */ 
