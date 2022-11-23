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

#ifndef BRLTTY_INCLUDED_MD_BRLDEFS
#define BRLTTY_INCLUDED_MD_BRLDEFS

typedef union {
  unsigned char bytes[1];

  struct {
    unsigned char soh;
    unsigned char stx;
    unsigned char code;
    unsigned char length;
    unsigned char etx;

    union {
      unsigned char bytes[0XFF];

      struct {
        unsigned char key;
      } navigationKey;

      struct {
        unsigned char key;
      } routingPress;

      struct {
        unsigned char key;
      } routingRelease;

      struct {
        unsigned char isChord;
        unsigned char dots;
        unsigned char ascii;
      } brailleKey;

      struct {
        unsigned char textCellCount;
        unsigned char statusCellCount;
        unsigned char dotsPerCell;
        unsigned char haveRoutingKeys;
        unsigned char majorVersion;
        unsigned char minorVersion;
      } identity;
    } data;

    /* Declare the checksum bytes here to ensure that the size is correct
     * even though the actual checksum is just after the last data byte.
     */
    unsigned char checksum[2];
  } PACKED fields;
} MD_Packet;

typedef enum {
  MD_CODE_WRITE_ALL       =   0,
  MD_CODE_WRITE_STATUS    =   1,
  MD_CODE_WRITE_TEXT      =   2,
  MD_CODE_WRITE_LCD       =   5,
  MD_CODE_NAVIGATION_KEY  =  16,
  MD_CODE_ROUTING_PRESS   =  17,
  MD_CODE_ROUTING_RELEASE =  18,
  MD_CODE_BRAILLE_KEY     =  21,
  MD_CODE_IDENTIFY        =  36,
  MD_CODE_IDENTITY        =  37,
  MD_CODE_ACKNOWLEDGE     = 127,
} MD_PacketCode;

typedef enum {
  MD_NAV_F1            = 0X01,
  MD_NAV_F2            = 0X02,
  MD_NAV_F3            = 0X03,
  MD_NAV_F4            = 0X04,
  MD_NAV_F5            = 0X05,
  MD_NAV_F6            = 0X06,
  MD_NAV_F7            = 0X07,
  MD_NAV_F8            = 0X08,
  MD_NAV_F9            = 0X09,
  MD_NAV_F10           = 0X0A,
  MD_NAV_LEFT          = 0X0B,
  MD_NAV_UP            = 0X0C,
  MD_NAV_RIGHT         = 0X0D,
  MD_NAV_DOWN          = 0X0E,
  MD_NAV_MASK_KEY      = 0X0F,

  MD_NAV_SHIFT         = 0X10,
  MD_NAV_LONG          = 0X20,
  MD_NAV_MASK_MOD      = 0X30,

  MD_NAV_SHIFT_PRESS   = 0X3F,
  MD_NAV_SHIFT_RELEASE = 0X40,
} MD_NavigationKey;

typedef enum {
  MD_BRL_DOT1  = 0,
  MD_BRL_DOT2  = 1,
  MD_BRL_DOT3  = 2,
  MD_BRL_DOT4  = 3,
  MD_BRL_DOT5  = 4,
  MD_BRL_DOT6  = 5,
  MD_BRL_DOT7  = 6,
  MD_BRL_DOT8  = 7,
  MD_BRL_SPACE = 8,
} MD_BrailleKey;

typedef enum {
  MD_ROUTING_FIRST = 0X01,
  MD_ROUTING_MASK  = 0X7F,
  MD_ROUTING_SHIFT = 0X80,
} MD_RoutingKey;

typedef enum {
  MD_GRP_NAV,
  MD_GRP_BRL,
  MD_GRP_RK,
  MD_GRP_SK,
} MD_KeyGroup;

#endif /* BRLTTY_INCLUDED_MD_BRLDEFS */ 
