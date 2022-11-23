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

#ifndef BRLTTY_INCLUDED_CE_BRLDEFS
#define BRLTTY_INCLUDED_CE_BRLDEFS

#define CE_REQ_Identify 0XF8
#define CE_RSP_Identity 0XFE

#define CE_PKT_BEGIN 0X79
#define CE_PKT_END 0X16
#define CE_PKT_REQ_Write 0X01
#define CE_PKT_RSP_NavigationKey 0X04
#define CE_PKT_RSP_Confirmation 0X07
#define CE_PKT_RSP_KeyboardKey 0X09

typedef enum {
  CE_KEY_PadLeft1     = 0X0C,
  CE_KEY_PadUp1       = 0X0F,
  CE_KEY_PadCenter1   = 0X10,
  CE_KEY_PadDown1     = 0X13,
  CE_KEY_PadRight1    = 0X14,

  CE_KEY_LeftUpper1   = 0X07,
  CE_KEY_LeftMiddle1  = 0X0B,
  CE_KEY_LeftLower1   = 0X1B,
  CE_KEY_RightUpper1  = 0X03,
  CE_KEY_RightMiddle1 = 0X17,
  CE_KEY_RightLower1  = 0X1F,

  CE_KEY_PadLeft2     = 0X06,
  CE_KEY_PadUp2       = 0X1A,
  CE_KEY_PadCenter2   = 0X0A,
  CE_KEY_PadDown2     = 0X19,
  CE_KEY_PadRight2    = 0X0E,

  CE_KEY_LeftUpper2   = 0X16,
  CE_KEY_LeftMiddle2  = 0X12,
  CE_KEY_LeftLower2   = 0X15,
  CE_KEY_RightUpper2  = 0X1E,
  CE_KEY_RightMiddle2 = 0X0D,
  CE_KEY_RightLower2  = 0X1D,

  CE_KEY_ROUTING_MIN = 0X20,
  CE_KEY_ROUTING_MAX = 0X6F,

  CE_KEY_RELEASE     = 0X80
} CE_NavigationKey;

typedef enum {
  CE_GRP_NavigationKey,
  CE_GRP_RoutingKey
} CE_KeyGroup;

#endif /* BRLTTY_INCLUDED_CE_BRLDEFS */ 
