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

#ifndef BRLTTY_INCLUDED_AT_BRLDEFS
#define BRLTTY_INCLUDED_AT_BRLDEFS

typedef enum {
  /* front left keys */
  AT_KEY_Home1        =  91, /* front left first upper */
  AT_KEY_End1         =  92, /* front left first lower */
  AT_KEY_ExtraCursor1 =  93, /* front left second upper */
  AT_KEY_Cursor1      =  94, /* front left second lower */
  AT_KEY_Up1          =  95, /* front left third upper */
  AT_KEY_Down1        =  96, /* front left third lower */
  AT_KEY_Left         =  97, /* front left fourth */

  /* front right keys */
  AT_KEY_Home2        = 201, /* front right first upper */
  AT_KEY_End2         = 202, /* front right first lower */
  AT_KEY_ExtraCursor2 = 203, /* front right second upper */
  AT_KEY_Cursor2      = 204, /* front right second lower */
  AT_KEY_Up2          = 205, /* front right third upper */
  AT_KEY_Down2        = 206, /* front right third lower */
  AT_KEY_Right        = 207, /* front right fourth */

  /* front middle keys */
  AT_KEY_Up3          =  98, /* front middle upper */
  AT_KEY_Down3        = 208, /* front middle lower */

  /* top left keys */
  AT_KEY_F1           =  83, /* top left first front */
  AT_KEY_F2           =  84, /* top left first rear */
  AT_KEY_F3           =  85, /* top left third rear */
  AT_KEY_F4           =  86, /* top left third front */
  AT_KEY_F5           =  87, /* top left second */
  AT_KEY_F6           =  88, /* top left fourth */
  AT_KEY_F7           =  89, /* top left fifth rear */
  AT_KEY_F8           =  90, /* top left fifth front */

  /* top right keys */
  AT_KEY_F9           = 193, /* top right first front */
  AT_KEY_F10          = 194, /* top right first rear */
  AT_KEY_F11          = 195, /* top right third rear */
  AT_KEY_F12          = 196, /* top right third front */
  AT_KEY_F13          = 197, /* top right second */
  AT_KEY_F14          = 198, /* top right fourth */
  AT_KEY_F15          = 199, /* top right fifth rear */
  AT_KEY_F16          = 200, /* top right fifth front */

  /* attribute keys */
  AT_KEY_Attribute1   =   1, /* attribute left front */
  AT_KEY_Attribute2   =  42, /* attribute left rear */
  AT_KEY_Attribute3   = 151, /* attribute right front */
  AT_KEY_Attribute4   = 192, /* attribute right rear */

  /* wheels */
  AT_KEY_LeftWheelRight  = 103, /* wheel left horizontal right */
  AT_KEY_LeftWheelLeft   = 104, /* wheel left horizontal left */
  AT_KEY_LeftWheelUp     = 105, /* wheel left vertical up */
  AT_KEY_LeftWheelDown   = 106, /* wheel left vertical down */
  AT_KEY_RightWheelRight = 213, /* wheel right horizontal right */
  AT_KEY_RightWheelLeft  = 214, /* wheel right horizontal left */
  AT_KEY_RightWheelUp    = 215, /* wheel right vertical up */
  AT_KEY_RightWheelDown  = 216, /* wheel right vertical down */
} AT_NavigationKey;

typedef enum {
  AT_GRP_NavigationKeys = 0,
  AT_GRP_RoutingKeys1,
  AT_GRP_RoutingKeys2
} AT_KeyGroup;

#endif /* BRLTTY_INCLUDED_AT_BRLDEFS */ 
