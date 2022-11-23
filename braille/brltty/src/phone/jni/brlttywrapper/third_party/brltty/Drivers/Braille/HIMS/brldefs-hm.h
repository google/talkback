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

#ifndef BRLTTY_INCLUDED_HM_BRLDEFS
#define BRLTTY_INCLUDED_HM_BRLDEFS

typedef enum {
  /* braille keyboard keys */
  HM_KEY_Dot1  = 0,
  HM_KEY_Dot2  = 1,
  HM_KEY_Dot3  = 2,
  HM_KEY_Dot4  = 3,
  HM_KEY_Dot5  = 4,
  HM_KEY_Dot6  = 5,
  HM_KEY_Dot7  = 6,
  HM_KEY_Dot8  = 7,
  HM_KEY_Space = 8,

  /* Braille Sense/Edge function keys */
  HM_KEY_F1 =  9,
  HM_KEY_F2 = 10,
  HM_KEY_F3 = 11,
  HM_KEY_F4 = 12,

  /* Braille Sense panning keys */
  HM_KEY_Backward = 13,
  HM_KEY_Forward  = 14,

  /* SyncBraille scroll keys */
  HM_KEY_SB_LeftScrollUp    = 12,
  HM_KEY_SB_RightScrollUp   = 13,
  HM_KEY_SB_RightScrollDown = 14,
  HM_KEY_SB_LeftScrollDown  = 15,

  /* Braille Sense scroll keys */
  HM_KEY_BS_LeftScrollUp    = 16,
  HM_KEY_BS_LeftScrollDown  = 17,
  HM_KEY_BS_RightScrollUp   = 18,
  HM_KEY_BS_RightScrollDown = 19,

  /* Braille Edge scroll keys */
  HM_KEY_BE_LeftScrollUp    = 16,
  HM_KEY_BE_RightScrollUp   = 17,
  HM_KEY_BE_RightScrollDown = 18,
  HM_KEY_BE_LeftScrollDown  = 19,

  /* Braille Edge function keys */
  HM_KEY_F5 = 20,
  HM_KEY_F6 = 21,
  HM_KEY_F7 = 22,
  HM_KEY_F8 = 23,

  /* Braille Edge left pad */
  HM_KEY_LeftPadUp     = 24,
  HM_KEY_LeftPadDown   = 25,
  HM_KEY_LeftPadLeft   = 26,
  HM_KEY_LeftPadRight  = 27,

  /* Braille Edge right pad */
  HM_KEY_RightPadUp    = 28,
  HM_KEY_RightPadDown  = 29,
  HM_KEY_RightPadLeft  = 30,
  HM_KEY_RightPadRight = 31
} HM_NavigationKey;

typedef enum {
  HM_GRP_NavigationKeys = 0,
  HM_GRP_RoutingKeys
} HM_KeyGroup;

#endif /* BRLTTY_INCLUDED_HM_BRLDEFS */ 
