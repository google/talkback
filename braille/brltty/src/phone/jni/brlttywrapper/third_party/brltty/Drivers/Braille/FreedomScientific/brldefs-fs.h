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

#ifndef BRLTTY_INCLUDED_FS_BRLDEFS
#define BRLTTY_INCLUDED_FS_BRLDEFS

typedef enum {
  FS_PKT_QUERY = 0X00,  /* host->unit: request device information */
  FS_PKT_ACK = 0X01,    /* unit->host: acknowledge packet receipt */
  FS_PKT_NAK = 0X02,    /* unit->host: negative acknowledge, report error */
  FS_PKT_KEY = 0X03,    /* unit->host: key event */
  FS_PKT_BUTTON = 0X04, /* unit->host: routing button event */
  FS_PKT_WHEEL = 0X05,  /* unit->host: whiz wheel event */
  FS_PKT_HVADJ = 0X08,  /* host->unit: set braille display voltage */
  FS_PKT_BEEP = 0X09,   /* host->unit: sound short beep */
  FS_PKT_CONFIG = 0X0F, /* host->unit: configure device options */
  FS_PKT_INFO = 0X80,   /* unit->host: response to query packet */
  FS_PKT_WRITE = 0X81,  /* host->unit: write to braille display */
  FS_PKT_EXTKEY = 0X82  /* unit->host: extended keys event */
} FS_PacketType;

typedef enum {
  FS_EXT_HVADJ = 0X08,    /* error in varibraille packet */
  FS_EXT_BEEP = 0X09,     /* error in beep packet */
  FS_EXT_CLEAR = 0X31,    /* error in ClearMsgBuf function */
  FS_EXT_LOOP = 0X32,     /* timing loop in ParseCommands function */
  FS_EXT_TYPE = 0X33,     /* unknown packet type in ParseCommands function */
  FS_EXT_CMDWRITE = 0X34, /* error in CmdWrite function */
  FS_EXT_UPDATE = 0X7E,   /* error in update packet */
  FS_EXT_DIAG = 0X7F,     /* error in diag packet */
  FS_EXT_QUERY = 0X80,    /* error in query packet */
  FS_EXT_WRITE = 0X81     /* error in write packet */
} FS_ExtendedPacketType;

typedef enum {
  FS_ERR_TIMEOUT = 0X30,   /* no data received from host for a while */
  FS_ERR_CHECKSUM = 0X31,  /* incorrect checksum */
  FS_ERR_TYPE = 0X32,      /* unsupported packet type */
  FS_ERR_PARAMETER = 0X33, /* invalid parameter */
  FS_ERR_SIZE = 0X34,      /* write size too large */
  FS_ERR_POSITION = 0X35,  /* write position too large */
  FS_ERR_OVERRUN = 0X36,   /* message queue overflow */
  FS_ERR_POWER = 0X37,     /* insufficient USB power */
  FS_ERR_SPI = 0X38        /* timeout on SPI bus */
} FS_ErrorCode;

typedef enum {
  FS_CFG_EXTKEY = 0X02 /* send extended key events */
} FS_ConfigFlag;

typedef struct {
  unsigned char type;
  unsigned char arg1;
  unsigned char arg2;
  unsigned char arg3;
} FS_PacketHeader;

#define FS_INFO_MANUFACTURER_SIZE 24
#define FS_INFO_MODEL_SIZE 16
#define FS_INFO_FIRMWARE_SIZE 8

typedef struct {
  FS_PacketHeader header;

  union {
    unsigned char bytes[0X100];

    struct {
      char manufacturer[FS_INFO_MANUFACTURER_SIZE];
      char model[FS_INFO_MODEL_SIZE];
      char firmware[FS_INFO_FIRMWARE_SIZE];
    } info;

    struct {
      unsigned char bytes[4];
    } extkey;
  } payload;
} FS_Packet;

#define FS_KEYS_WHEEL 8
#define FS_KEYS_HOT 8

typedef enum {
  FS_KEY_Dot1 = 0,
  FS_KEY_Dot2 = 1,
  FS_KEY_Dot3 = 2,
  FS_KEY_Dot4 = 3,
  FS_KEY_Dot5 = 4,
  FS_KEY_Dot6 = 5,
  FS_KEY_Dot7 = 6,
  FS_KEY_Dot8 = 7,

  FS_KEY_LeftWheel = 8,
  FS_KEY_RightWheel = 9,
  FS_KEY_LeftShift = 10,
  FS_KEY_RightShift = 11,
  FS_KEY_PanLeft = 12,
  FS_KEY_PanRight = 13,
  FS_KEY_Space = 15,

  FS_KEY_LeftSelector = 16,
  FS_KEY_RightSelector = 17,
  FS_KEY_LeftBumperUp = 20,
  FS_KEY_LeftBumperDown = 21,
  FS_KEY_RightBumperUp = 22,
  FS_KEY_RightBumperDown = 23,

  FS_KEY_LeftRockerUp = 28,
  FS_KEY_LeftRockerDown = 29,
  FS_KEY_RightRockerUp = 30,
  FS_KEY_RightRockerDown = 31,

  FS_KEY_WHEEL,
  FS_KEY_HOT = FS_KEY_WHEEL + FS_KEYS_WHEEL
} FS_NavigationKey;

typedef enum {
  FS_GRP_NavigationKeys = 0,
  FS_GRP_RoutingKeys,
  FS_GRP_NavrowKeys
} FS_KeyGroup;

#endif /* BRLTTY_INCLUDED_FS_BRLDEFS */ 
