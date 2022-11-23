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
 * Web Page: http://mielke.cc/brltty/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#ifndef BRLTTY_INCLUDED_BG_BRLDEFS
#define BRLTTY_INCLUDED_BG_BRLDEFS

typedef enum {
  BG_NAV_Dot1,
  BG_NAV_Dot2,
  BG_NAV_Dot3,
  BG_NAV_Dot4,
  BG_NAV_Dot5,
  BG_NAV_Dot6,
  BG_NAV_Dot7,
  BG_NAV_Dot8,

  BG_NAV_Space,
  BG_NAV_Backward,
  BG_NAV_Forward,

  BG_NAV_Center,
  BG_NAV_Left,
  BG_NAV_Right,
  BG_NAV_Up,
  BG_NAV_Down,

  BG_NAV_Louder,
  BG_NAV_Softer,
} BG_NavigationKey;

typedef enum {
  BG_GRP_NavigationKeys,
  BG_GRP_RoutingKeys
} BG_KeyGroup;

#endif /* BRLTTY_INCLUDED_BG_BRLDEFS */ 
