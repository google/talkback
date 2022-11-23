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

#ifndef BRLTTY_INCLUDED_MT_BRLDEFS
#define BRLTTY_INCLUDED_MT_BRLDEFS

typedef enum {
  /* status[2] */
  MT_KEY_LeftUp      =  6,
  MT_KEY_LeftSelect  =  4,
  MT_KEY_LeftDown    =  2,
  MT_KEY_RightUp     =  3,
  MT_KEY_RightSelect =  1,
  MT_KEY_RightDown   =  0,

  /* status[3] (front keys from left to right) */
  MT_KEY_CursorLeft  = 10,
  MT_KEY_CursorDown  = 14,
  MT_KEY_CursorUp    = 11,
  MT_KEY_CursorRight = 12
} MT_NavigationKey;

typedef enum {
  MT_GRP_NavigationKeys = 0,

  MT_GRP_RoutingKeys1,
  MT_GRP_StatusKeys1,

  MT_GRP_RoutingKeys2,
  MT_GRP_StatusKeys2
} MT_KeyGroup;

#endif /* BRLTTY_INCLUDED_MT_BRLDEFS */ 
