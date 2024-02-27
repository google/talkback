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

#ifndef BRLTTY_INCLUDED_AL_BRLDEFS
#define BRLTTY_INCLUDED_AL_BRLDEFS

#define AL_KEYS_OPERATION 14
#define AL_KEYS_STATUS 6
#define AL_KEYS_SATELLITE 6
#define AL_KEYS_ETOUCH 4
#define AL_KEYS_SMARTPAD 9
#define AL_KEYS_THUMB 5
#define AL_KEYS_FEATUREPACK 14

typedef enum {
  AL_KEY_OPERATION = 1,
  AL_KEY_STATUS1 = AL_KEY_OPERATION + AL_KEYS_OPERATION,
  AL_KEY_STATUS2 = AL_KEY_STATUS1 + AL_KEYS_STATUS,
  AL_KEY_SPEECH_PAD = AL_KEY_STATUS2 + AL_KEYS_STATUS,
  AL_KEY_NAV_PAD = AL_KEY_SPEECH_PAD + AL_KEYS_SATELLITE,
  AL_KEY_ETOUCH = AL_KEY_NAV_PAD + AL_KEYS_SATELLITE,
  AL_KEY_SMARTPAD = AL_KEY_ETOUCH + AL_KEYS_ETOUCH,
  AL_KEY_THUMB = AL_KEY_SMARTPAD + AL_KEYS_SMARTPAD,
  AL_KEY_FEATUREPACK = AL_KEY_THUMB + AL_KEYS_THUMB,

  AL_KEY_Prog = AL_KEY_OPERATION,
  AL_KEY_Home,
  AL_KEY_Cursor,
  AL_KEY_Up,
  AL_KEY_Left,
  AL_KEY_Right,
  AL_KEY_Down,
  AL_KEY_Cursor2,
  AL_KEY_Home2,
  AL_KEY_Prog2,
  AL_KEY_LeftTumblerLeft,
  AL_KEY_LeftTumblerRight,
  AL_KEY_RightTumblerLeft,
  AL_KEY_RightTumblerRight,

  AL_KEY_SpeechPadF1 = AL_KEY_SPEECH_PAD,
  AL_KEY_SpeechPadUp,
  AL_KEY_SpeechPadLeft,
  AL_KEY_SpeechPadDown,
  AL_KEY_SpeechPadRight,
  AL_KEY_SpeechPadF2,

  AL_KEY_NavPadF1 = AL_KEY_NAV_PAD,
  AL_KEY_NavPadUp,
  AL_KEY_NavPadLeft,
  AL_KEY_NavPadDown,
  AL_KEY_NavPadRight,
  AL_KEY_NavPadF2,

  AL_KEY_ETouchLeftRear = AL_KEY_ETOUCH,
  AL_KEY_ETouchLeftFront,
  AL_KEY_ETouchRightRear,
  AL_KEY_ETouchRightFront,

  AL_KEY_SmartpadF1 = AL_KEY_SMARTPAD,
  AL_KEY_SmartpadF2,
  AL_KEY_SmartpadLeft,
  AL_KEY_SmartpadEnter,
  AL_KEY_SmartpadUp,
  AL_KEY_SmartpadDown,
  AL_KEY_SmartpadRight,
  AL_KEY_SmartpadF3,
  AL_KEY_SmartpadF4,

  AL_KEY_Dot1 = AL_KEY_FEATUREPACK + 1,
  AL_KEY_Dot2,
  AL_KEY_Dot3,
  AL_KEY_Dot4,
  AL_KEY_Dot5,
  AL_KEY_Dot6,
  AL_KEY_Dot7,
  AL_KEY_Dot8,
  AL_KEY_Control,
  AL_KEY_Windows,
  AL_KEY_Space,
  AL_KEY_Alt,
  AL_KEY_Enter,

  AL_KEY_RELEASE = 0X80
} AL_NavigationKey;

typedef enum {
  AL_GRP_NavigationKeys = 0,
  AL_GRP_RoutingKeys1,
  AL_GRP_RoutingKeys2
} AL_KeyGroup;

#endif /* BRLTTY_INCLUDED_AL_BRLDEFS */ 
