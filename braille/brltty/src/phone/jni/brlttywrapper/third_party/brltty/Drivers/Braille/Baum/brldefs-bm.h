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

#ifndef BRLTTY_INCLUDED_BM_BRLDEFS
#define BRLTTY_INCLUDED_BM_BRLDEFS

#define BM_KEYS_DISPLAY 8
#define BM_KEYS_COMMAND 7
#define BM_KEYS_FRONT 10
#define BM_KEYS_ENTRY 16
#define BM_KEYS_JOYSTICK 5
#define BM_KEYS_WHEEL 4
#define BM_KEYS_STATUS 8

typedef enum {
  BM_KEY_DISPLAY = 0,
  BM_KEY_COMMAND = BM_KEY_DISPLAY + BM_KEYS_DISPLAY,
  BM_KEY_FRONT = BM_KEY_COMMAND + BM_KEYS_COMMAND,
  BM_KEY_BACK = BM_KEY_FRONT + BM_KEYS_FRONT,
  BM_KEY_ENTRY = BM_KEY_BACK + BM_KEYS_FRONT,
  BM_KEY_JOYSTICK = BM_KEY_ENTRY + BM_KEYS_ENTRY,
  BM_KEY_WHEEL_UP = BM_KEY_JOYSTICK + BM_KEYS_JOYSTICK,
  BM_KEY_WHEEL_DOWN = BM_KEY_WHEEL_UP + BM_KEYS_WHEEL,
  BM_KEY_WHEEL_PRESS = BM_KEY_WHEEL_DOWN + BM_KEYS_WHEEL,
  BM_KEY_STATUS = BM_KEY_WHEEL_PRESS + BM_KEYS_WHEEL,
  BM_KEY_COUNT = BM_KEY_STATUS + BM_KEYS_STATUS
} BM_NavigationKey;

typedef enum {
  BM_GRP_NavigationKeys = 0,
  BM_GRP_RoutingKeys,
  BM_GRP_HorizontalSensors,
  BM_GRP_LeftSensors,
  BM_GRP_RightSensors,
  BM_GRP_ScaledLeftSensors,
  BM_GRP_ScaledRightSensors
} BM_KeyGroup;

#endif /* BRLTTY_INCLUDED_BM_BRLDEFS */ 
