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

#ifndef BRLTTY_INCLUDED_HD_BRLDEFS
#define BRLTTY_INCLUDED_HD_BRLDEFS

typedef enum {
  HD_REQ_WRITE_CELLS = 0X01
} HD_RequestCode;

typedef enum {
  HD_PFL_K1 = 0X04,
  HD_PFL_K2 = 0X03,
  HD_PFL_K3 = 0X08,

  HD_PFL_B1 = 0X03,
  HD_PFL_B2 = 0X07,
  HD_PFL_B3 = 0X0B,
  HD_PFL_B4 = 0X0F,
  HD_PFL_B5 = 0X13,
  HD_PFL_B6 = 0X17,
  HD_PFL_B7 = 0X1B,
  HD_PFL_B8 = 0X1F
} HD_KeyCode_ProfiLine;

typedef enum {
  HD_MBL_B1 =  0,
  HD_MBL_B2 =  1,
  HD_MBL_B3 =  2,

  HD_MBL_B4 =  4,
  HD_MBL_B5 =  5,
  HD_MBL_B6 =  6,

  HD_MBL_K1 =  8,
  HD_MBL_K2 =  9,
  HD_MBL_K3 = 10
} HD_KeyCode_MobilLine;

typedef enum {
  HD_GRP_NavigationKeys,
  HD_GRP_RoutingKeys
} HD_KeyGroup;

#endif /* BRLTTY_INCLUDED_HD_BRLDEFS */ 
