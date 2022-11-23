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

#ifndef BRLTTY_INCLUDED_BN_BRLDEFS
#define BRLTTY_INCLUDED_BN_BRLDEFS

#include "ascii.h"

typedef enum {
  BN_REQ_BEGIN    = ESC,
  BN_REQ_DESCRIBE = '?',
  BN_REQ_WRITE    = 'B'
} BN_RequestType;

typedef enum {
  BN_RSP_CHARACTER   = 0X80,
  BN_RSP_SPACE       = 0X81,
  BN_RSP_BACKSPACE   = 0X82,
  BN_RSP_ENTER       = 0X83,
  BN_RSP_THUMB       = 0X84,
  BN_RSP_ROUTE       = 0X85,
  BN_RSP_DESCRIBE    = 0X86,
  BN_RSP_INPUT_CHAR  = 0X88,
  BN_RSP_INPUT_VKEY  = 0X89,
  BN_RSP_INPUT_RESET = 0X8A,
  BN_RSP_QWERTY_KEY  = 0X8C,
  BN_RSP_QWERTY_MODS = 0X8D,
  BN_RSP_DISPLAY     = ESC
} BN_ResponseType;

typedef enum {
  BN_KEY_Dot1,
  BN_KEY_Dot2,
  BN_KEY_Dot3,
  BN_KEY_Dot4,
  BN_KEY_Dot5,
  BN_KEY_Dot6,

  BN_KEY_Space,
  BN_KEY_Backspace,
  BN_KEY_Enter,

  BN_KEY_Previous,
  BN_KEY_Back,
  BN_KEY_Advance,
  BN_KEY_Next
} BN_NavigationKey;

typedef enum {
  BN_GRP_NavigationKeys,
  BN_GRP_RoutingKeys
} SK_KeyGroup;

typedef enum {
  BN_MOD_FUNCTION = 0X01,
  BN_MOD_SHIFT    = 0X02,
  BN_MOD_cONTROL  = 0X04,
  BN_MOD_rEAD     = 0X08
} BN_QwertyModifier;

#endif /* BRLTTY_INCLUDED_BN_BRLDEFS */ 
