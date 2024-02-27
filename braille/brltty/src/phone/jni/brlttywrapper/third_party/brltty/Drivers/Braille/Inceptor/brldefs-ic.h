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

#ifndef BRLTTY_INCLUDED_IC_BRLDEFS
#define BRLTTY_INCLUDED_IC_BRLDEFS

typedef union {
  unsigned char bytes[10];

  struct {
    unsigned char start;
    unsigned char type;
    unsigned char count;
    unsigned char data;
    unsigned char reserved[4];
    unsigned char checksum;
    unsigned char end;
  } PACKED fields;
} InputPacket;

typedef enum {
  IC_KEY_Dot1 = 0,
  IC_KEY_Dot2 = 1,
  IC_KEY_Dot3 = 2,
  IC_KEY_Dot4 = 3,
  IC_KEY_Dot5 = 4,
  IC_KEY_Dot6 = 5,
  IC_KEY_Dot7 = 6,
  IC_KEY_Dot8 = 7,

  IC_KEY_Space = 8,
  IC_KEY_MoveUp = 9,
  IC_KEY_MoveDown = 10,
  IC_KEY_PanLeft = 11,
  IC_KEY_PanRight = 12,
  IC_KEY_Back = 13,
  IC_KEY_Enter = 14,

  IC_KEY_RoutingKey1 = 16,
  IC_KEY_RoutingKey2 = 17,
  IC_KEY_RoutingKey3 = 18,
  IC_KEY_RoutingKey4 = 19,
  IC_KEY_RoutingKey5 = 20,
  IC_KEY_RoutingKey6 = 21,
  IC_KEY_RoutingKey7 = 22,
  IC_KEY_RoutingKey8 = 23,

  IC_KEY_RoutingKey9 = 24,
  IC_KEY_RoutingKey10 = 25,
  IC_KEY_RoutingKey11 = 26,
  IC_KEY_RoutingKey12 = 27,
  IC_KEY_RoutingKey17 = 28,
  IC_KEY_RoutingKey18 = 29,
  IC_KEY_RoutingKey19 = 30,
  IC_KEY_RoutingKey20 = 31,
} IC_NavigationKey;

typedef enum {
  IC_GRP_NavigationKeys = 0,
  IC_GRP_RoutingKeys
} IC_KeyGroup;

#endif /* BRLTTY_INCLUDED_IC_BRLDEFS */ 
