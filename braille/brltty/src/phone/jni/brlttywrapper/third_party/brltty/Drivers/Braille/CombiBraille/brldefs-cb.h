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

#ifndef BRLTTY_INCLUDED_CB_BRLDEFS
#define BRLTTY_INCLUDED_CB_BRLDEFS

#define CB_SERIAL_BAUD 38400 /* baud rate for Braille display */

typedef enum {
  CB_PKT_KeepAlive = 0,
  CB_PKT_DeviceIdentity = '?',
  CB_PKT_WriteCells = 'B',
  CB_PKT_RoutingKey = 'C',
  CB_PKT_NavigationKeys = 'K',
} CB_PacketType;

typedef enum {
  CB_KEY_Dot6 = 0,
  CB_KEY_Dot5,
  CB_KEY_Dot4,
  CB_KEY_Dot1,
  CB_KEY_Dot2,
  CB_KEY_Dot3,

  CB_KEY_Thumb1 = 8,
  CB_KEY_Thumb2,
  CB_KEY_Thumb3,
  CB_KEY_Thumb4,
  CB_KEY_Thumb5,

  CB_KEY_Status1 = 16,
  CB_KEY_Status2,
  CB_KEY_Status3,
  CB_KEY_Status4,
  CB_KEY_Status5,
  CB_KEY_Status6,
} CB_NavigationKey;

typedef enum {
  CB_GRP_NavigationKeys = 0,
  CB_GRP_RoutingKeys,
} CB_KeyGroup;

#endif /* BRLTTY_INCLUDED_CB_BRLDEFS */ 
