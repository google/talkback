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

#ifndef BRLTTY_INCLUDED_PG_BRLDEFS
#define BRLTTY_INCLUDED_PG_BRLDEFS

typedef enum {
  PG_KEY_None = 0,

  PG_KEY_LeftShift,
  PG_KEY_RightShift,
  PG_KEY_LeftControl,
  PG_KEY_RighTControl,

  PG_KEY_Left,
  PG_KEY_Right,
  PG_KEY_Up,
  PG_KEY_Down,

  PG_KEY_Home,
  PG_KEY_End,
  PG_KEY_Enter,
  PG_KEY_Escape,

  PG_KEY_Status
} PG_NavigationKey;

typedef enum {
  PG_GRP_NavigationKeys = 0,
  PG_GRP_RoutingKeys
} PG_KeyGroup;

#endif /* BRLTTY_INCLUDED_PG_BRLDEFS */ 
