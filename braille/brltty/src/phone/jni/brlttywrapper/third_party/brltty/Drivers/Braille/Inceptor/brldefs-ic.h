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
  IC_KEY_Dot1          =  0,
  IC_KEY_Dot2          =  1,
  IC_KEY_Dot3          =  2,
  IC_KEY_Dot4          =  3,
  IC_KEY_Dot5          =  4,
  IC_KEY_Dot6          =  5,
  IC_KEY_Dot7          =  6,
  IC_KEY_Dot8          =  7,

  IC_KEY_Space         =  8,
  IC_KEY_LeftUp        =  9,
  IC_KEY_LeftDown      = 10,
  IC_KEY_RightUp       = 11,
  IC_KEY_RightDown     = 12,

  IC_KEY_Back          = 13,
  IC_KEY_Enter         = 14,
} IC_NavigationKey;

typedef enum {
  IC_GRP_NavigationKeys = 0,
  IC_GRP_RoutingKeys
} IC_KeyGroup;

#endif /* BRLTTY_INCLUDED_IC_BRLDEFS */ 
