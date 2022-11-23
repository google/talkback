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

#ifndef BRLTTY_INCLUDED_NP_BRLDEFS
#define BRLTTY_INCLUDED_NP_BRLDEFS

#define NP_KEY_NAVIGATION_PRESS 0X20
#define NP_KEY_ROUTING_MIN 0X80
#define NP_KEY_ROUTING_MAX 0X87

typedef enum {
  NP_KEY_Brl1      = 0X41,
  NP_KEY_Brl2      = 0X42,
  NP_KEY_Brl3      = 0X43,
  NP_KEY_Brl4      = 0X44,
  NP_KEY_Brl5      = 0X45,
  NP_KEY_Brl6      = 0X46,
  NP_KEY_Brl7      = 0X47,
  NP_KEY_Brl8      = 0X48,
  NP_KEY_Enter     = 0X49,
  NP_KEY_Space     = 0X4A,
  NP_KEY_PadCenter = 0X4B,
  NP_KEY_PadLeft   = 0X4C,
  NP_KEY_PadRight  = 0X4D,
  NP_KEY_PadUp     = 0X51,
  NP_KEY_PadDown   = 0X53,
  NP_KEY_NavLeft   = 0X55,
  NP_KEY_NavRight  = 0X52
} NP_NavigationKey;

typedef enum {
  NP_GRP_NavigationKeys,
  NP_GRP_RoutingKeys
} NP_KeyGroup;

#endif /* BRLTTY_INCLUDED_NP_BRLDEFS */ 
