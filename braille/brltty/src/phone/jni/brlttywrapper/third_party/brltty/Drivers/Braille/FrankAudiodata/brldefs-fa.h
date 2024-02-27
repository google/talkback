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

#ifndef BRLTTY_INCLUDED_FA_BRLDEFS
#define BRLTTY_INCLUDED_FA_BRLDEFS

typedef enum {
  FA_PKT_SLIDER = 2,
  FA_PKT_NAV = 8,
  FA_PKT_ROUTE = 9,
} FA_PacketType;

typedef enum {
  FA_NAV_K1 = 2,
  FA_NAV_K2 = 1,
  FA_NAV_K3 = 0,

  FA_NAV_K4 = 8,
  FA_NAV_K5 = 9,
  FA_NAV_K6 = 10,

  FA_NAV_K7 = 3,
  FA_NAV_K8 = 4,
  FA_NAV_K9 = 5,

  FA_NAV_F1 = 18,
  FA_NAV_F2 = 17,
  FA_NAV_F3 = 16,

  FA_NAV_F4 = 19,
  FA_NAV_F5 = 20,
  FA_NAV_F6 = 21,
} FA_NavigationKey;

typedef enum {
  FA_GRP_NAV,
  FA_GRP_ROUTE,
  FA_GRP_SLIDE,
} FA_KeyGroup;

#endif /* BRLTTY_INCLUDED_FA_BRLDEFS */
