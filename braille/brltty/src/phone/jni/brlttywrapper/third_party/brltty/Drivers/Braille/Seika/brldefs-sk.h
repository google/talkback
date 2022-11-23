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

#ifndef BRLTTY_INCLUDED_SK_BRLDEFS
#define BRLTTY_INCLUDED_SK_BRLDEFS

typedef enum {
  SK_BDP_K1 =  0,
  SK_BDP_K7 =  1,
  SK_BDP_K8 =  2,
  SK_BDP_K6 =  3,
  SK_BDP_K5 =  4,
  SK_BDP_K2 =  9,
  SK_BDP_K3 = 11,
  SK_BDP_K4 = 12
} SK_BrailleDisplayKey;

typedef enum {
  SK_NTK_Dot1               =  0,
  SK_NTK_Dot2               =  1,
  SK_NTK_Dot3               =  2,
  SK_NTK_Dot4               =  3,
  SK_NTK_Dot5               =  4,
  SK_NTK_Dot6               =  5,
  SK_NTK_Dot7               =  6,
  SK_NTK_Dot8               =  7,

  SK_NTK_Backspace          =  8,
  SK_NTK_Space              =  9,

  SK_NTK_LeftButton         = 10,
  SK_NTK_RightButton        = 11,

  SK_NTK_LeftJoystickPress  = 12,
  SK_NTK_LeftJoystickLeft   = 13,
  SK_NTK_LeftJoystickRight  = 14,
  SK_NTK_LeftJoystickUp     = 15,
  SK_NTK_LeftJoystickDown   = 16,

  SK_NTK_RightJoystickPress = 17,
  SK_NTK_RightJoystickLeft  = 18,
  SK_NTK_RightJoystickRight = 19,
  SK_NTK_RightJoystickUp    = 20,
  SK_NTK_RightJoystickDown  = 21
} SK_NoteTakerKey;

typedef enum {
  SK_GRP_NavigationKeys = 0,
  SK_GRP_RoutingKeys
} SK_KeyGroup;

#endif /* BRLTTY_INCLUDED_SK_BRLDEFS */ 
