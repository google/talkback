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

#ifndef BRLTTY_INCLUDED_FS_BRLDEFS
#define BRLTTY_INCLUDED_FS_BRLDEFS

#define FS_KEYS_WHEEL 8
#define FS_KEYS_HOT 8

typedef enum {
  FS_KEY_Dot1 = 0,
  FS_KEY_Dot2 = 1,
  FS_KEY_Dot3 = 2,
  FS_KEY_Dot4 = 3,
  FS_KEY_Dot5 = 4,
  FS_KEY_Dot6 = 5,
  FS_KEY_Dot7 = 6,
  FS_KEY_Dot8 = 7,

  FS_KEY_LeftWheel = 8,
  FS_KEY_RightWheel = 9,
  FS_KEY_LeftShift = 10,
  FS_KEY_RightShift = 11,
  FS_KEY_LeftAdvance = 12,
  FS_KEY_RightAdvance = 13,
  FS_KEY_Space = 15,

  FS_KEY_LeftGdf = 16,
  FS_KEY_RightGdf = 17,
  FS_KEY_LeftBumperUp = 20,
  FS_KEY_LeftBumperDown = 21,
  FS_KEY_RightBumperUp = 22,
  FS_KEY_RightBumperDown = 23,

  FS_KEY_LeftRockerUp = 28,
  FS_KEY_LeftRockerDown = 29,
  FS_KEY_RightRockerUp = 30,
  FS_KEY_RightRockerDown = 31,

  FS_KEY_WHEEL,
  FS_KEY_HOT = FS_KEY_WHEEL + FS_KEYS_WHEEL
} FS_NavigationKey;

typedef enum {
  FS_GRP_NavigationKeys = 0,
  FS_GRP_RoutingKeys,
  FS_GRP_NavrowKeys
} FS_KeyGroup;

#endif /* BRLTTY_INCLUDED_FS_BRLDEFS */ 
