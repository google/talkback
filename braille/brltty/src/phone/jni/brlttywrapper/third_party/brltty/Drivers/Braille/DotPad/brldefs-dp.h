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

#ifndef BRLTTY_INCLUDED_DP_BRLDEFS
#define BRLTTY_INCLUDED_DP_BRLDEFS

#define DP_MAXIMUM_TEXT_COLUMNS 80

typedef enum {
  DP_REQ_FIRMWARE_VERSION = 0X0000,
  DP_RSP_FIRMWARE_VERSION = 0X0001,

  DP_REQ_DEVICE_NAME = 0X0100,
  DP_RSP_DEVICE_NAME = 0X0101,

  DP_REQ_BOARD_INFORMATION = 0X0110,
  DP_RSP_BOARD_INFORMATION = 0X0111,

  DP_REQ_DISPLAY_LINE = 0X0200,
  DP_RSP_DISPLAY_LINE = 0X0201,
  DP_NTF_DISPLAY_LINE = 0X0202,

  DP_REQ_DISPLAY_CURSOR = 0X0210,
  DP_RSP_DISPLAY_CURSOR = 0X0211,
  DP_NTF_DISPLAY_CURSOR = 0X0212,

  DP_NTF_KEYS_SCROLL = 0X0302,
  DP_NTF_KEYS_PERKINS = 0X0312,
  DP_NTF_KEYS_ROUTING = 0X0322,
  DP_NTF_KEYS_FUNCTION = 0X0332,

  DP_NTF_ERROR = 0X9902,
} DP_Command;

typedef enum {
  DP_HAS_GRAPHIC_DISPLAY = 0X80,
  DP_HAS_TEXT_DISPLAY = 0X40,
  DP_HAS_PERKINS_KEYS = 0X20,
  DP_HAS_ROUTING_KEYS = 0X10,
  DP_HAS_NAVIGATION_KEYS = 0X08,
  DP_HAS_PANNING_KEYS = 0X04,
  DP_HAS_FUNCTION_KEYS = 0X02,
} DP_Features;

typedef enum {
  DP_DPC_6 = 0,
  DP_DPC_8 = 1,
} DP_DotsPerCell;

typedef struct {
  unsigned char rowCount;     // 1, 2
  unsigned char columnCount;  // 12, 14, 15, 16, 20, 24, 26, 28, 30, 32, 36, 40
  unsigned char dividedLine;
  unsigned char refreshTime;  // 100ms
} DP_DisplayDescriptor;

typedef struct {
  unsigned char features;
  unsigned char dotsPerCell;          // 0:6, 1:8
  unsigned char distanceBetweenPins;  // 0.1mm
  unsigned char functionKeyCount;

  DP_DisplayDescriptor text;
  DP_DisplayDescriptor graphic;
} DP_BoardInformation;

typedef enum {
  DP_DSP_DOT1 = 0X01,
  DP_DSP_DOT2 = 0X02,
  DP_DSP_DOT3 = 0X04,
  DP_DSP_DOT4 = 0X10,
  DP_DSP_DOT5 = 0X20,
  DP_DSP_DOT6 = 0X40,
  DP_DSP_DOT7 = 0X08,
  DP_DSP_DOT8 = 0X80,
} DP_DisplayDots;

typedef enum {
  DP_DRC_ACK = 0,
  DP_DRC_NACK = 1,
  DP_DRC_WAIT = 2,
  DP_DRC_CHECKSUM = 3,
} DP_DisplayResponseCode;

typedef enum {
  DP_SCL_LEFT_NEXT = 28,
  DP_SCL_LEFT_PREV = 29,
  DP_SCL_RIGHT_NEXT = 30,
  DP_SCL_RIGHT_PREV = 31,
} DP_ScrollKey;

typedef enum {
  DP_KBD_DOT7 = 0,
  DP_KBD_DOT3 = 1,
  DP_KBD_DOT2 = 2,
  DP_KBD_DOT1 = 3,
  DP_KBD_DOT4 = 4,
  DP_KBD_DOT5 = 5,
  DP_KBD_DOT6 = 6,
  DP_KBD_DOT8 = 7,

  DP_KBD_SPACE = 8,
  DP_KBD_SHIFT_LEFT = 9,
  DP_KBD_CONTROL_LEFT = 10,
  DP_KBD_SHIFT_RIGHT = 11,
  DP_KBD_CONTROL_RIGHT = 12,
  DP_PAN_LEFT = 13,
  DP_PAN_RIGHT = 14,

  DP_NAV_CENTER = 16,
  DP_NAV_UP = 17,
  DP_NAV_RIGHT = 18,
  DP_NAV_DOWN = 19,
  DP_NAV_LEFT = 20,
} DP_PerkinsKey;

typedef enum {
  DP_GRP_ScrollKeys,
  DP_GRP_PerkinsKeys,
  DP_GRP_FunctionKeys,
  DP_GRP_RoutingKeys,
} DP_KeyGroup;

typedef enum {
  DP_ERR_LENGTH = 1,
  DP_ERR_COMMAND = 2,
  DP_ERR_CHECKSUM = 3,
  DP_ERR_PARAMETER = 4,
  DP_ERR_TIMEOUT = 5,
} DP_ErrorCode;

typedef enum {
  DP_PSB_SYNC1 = 0XAA,
  DP_PSB_SYNC2 = 0X55,
} DP_PacketSyncByte;

typedef enum {
  DP_SEQ_TEXT = 0X80,
} DP_PacketSeqFlag;

typedef struct {
  unsigned char sync[2];
  unsigned char length[2];  // big endian
  unsigned char destination;
  unsigned char command[2];  // big endian
  unsigned char seq;

  unsigned char data[DP_MAXIMUM_TEXT_COLUMNS + 1];
  // includes one-byte trailing checksum
} DP_PacketFields;

typedef union {
  unsigned char bytes[sizeof(DP_PacketFields)];
  DP_PacketFields fields;
} DP_Packet;

#endif /* BRLTTY_INCLUDED_DP_BRLDEFS */
