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

#ifndef BRLTTY_INCLUDED_HW_BRLDEFS
#define BRLTTY_INCLUDED_HW_BRLDEFS

typedef enum {
  HW_MSG_INIT                  = 0X00,
  HW_MSG_INIT_RESP             = 0X01,
  HW_MSG_DISPLAY               = 0X02,
  HW_MSG_GET_KEYS              = 0X03,
  HW_MSG_KEYS                  = 0X04,
  HW_MSG_KEY_DOWN              = 0X05,
  HW_MSG_KEY_UP                = 0X06,
  HW_MSG_FIRMWARE_UPDATE       = 0X07,
  HW_MSG_FIRMWARE_RESP         = 0X08,
  HW_MSG_CONFIGURATION_UPDATE  = 0X09,
  HW_MSG_CONFIGURATION_RESP    = 0X0A,
  HW_MSG_GET_CONFIGURATION     = 0X0B,
  HW_MSG_GET_FIRMWARE_VERSION  = 0X0C,
  HW_MSG_FIRMWARE_VERSION_RESP = 0X0D,
  HW_MSG_KEEP_AWAKE            = 0X0E,
  HW_MSG_KEEP_AWAKE_RESP       = 0X0F,
  HW_MSG_POWERING_OFF          = 0X10
} HW_MessageType;

typedef enum {
  HW_MODEL_BrailleNoteTouch = 0X10
} HW_ModelIdentifier;

typedef union {
  unsigned char bytes[3 + 0XFF];

  struct {
    unsigned char header;
    unsigned char type;
    unsigned char length;

    union {
      unsigned char bytes[0XFF];

      struct {
        unsigned char stillInitializing;
        unsigned char modelIdentifier;
        unsigned char cellCount;
      } PACKED init;

      struct {
        unsigned char id;
      } PACKED key;

      struct {
        unsigned char have;
        unsigned char major;
        unsigned char minor;
        unsigned char build;
      } PACKED firmwareVersion;
    } data;
  } PACKED fields;
} HW_Packet;

typedef enum {
  HW_REP_FTR_Capabilities  = 1,
  HW_REP_FTR_Settings      = 2,
  HW_REP_FTR_Configuration = 3,
  HW_REP_IN_PressedKeys    = 4,
  HW_REP_OUT_WriteCells    = 5,
  HW_REP_FTR_KeepAwake     = 6,
  HW_REP_IN_PoweringOff    = 7
} HW_ReportIdentifier;

typedef struct {
  unsigned char reportIdentifier;
  char systemLanguage[2];

  struct {
    char major;
    char minor;
    char build[2];
  } version;

  char serialNumber[16];
  unsigned char zero;
  unsigned char cellCount;
  unsigned char cellType;
  unsigned char pad[13];
} HW_CapabilitiesReport;

typedef struct {
  unsigned char reportIdentifier;
  unsigned char dotPressure;
} HW_SettingsReport;

typedef struct {
  unsigned char reportIdentifier;
  unsigned char fill1;
  unsigned char fill2;
  unsigned char cellCount;

  struct {
    unsigned char firstIndex;
    unsigned char lastIndex;
  } primaryRoutingKeys;

  struct {
    unsigned char firstIndex;
    unsigned char lastIndex;
  } secondaryRoutingKeys;
} HW_ConfigurationReport;

typedef struct {
  unsigned char reportIdentifier;
  unsigned char fill;
} HW_KeepAwakeReport;

typedef struct {
  unsigned char reportIdentifier;
  unsigned char fill;
} HW_PoweringOffReport;

typedef enum {
  HW_KEY_Reset     =  1,

  HW_KEY_Dot1      =  2,
  HW_KEY_Dot2      =  3,
  HW_KEY_Dot3      =  4,
  HW_KEY_Dot4      =  5,
  HW_KEY_Dot5      =  6,
  HW_KEY_Dot6      =  7,
  HW_KEY_Dot7      =  8,
  HW_KEY_Dot8      =  9,
  HW_KEY_Space     = 10,

  HW_KEY_Command1  = 11,
  HW_KEY_Command2  = 12,
  HW_KEY_Command3  = 13,
  HW_KEY_Command4  = 14,
  HW_KEY_Command5  = 15,
  HW_KEY_Command6  = 16,

  HW_KEY_Thumb1    = 17,
  HW_KEY_Thumb2    = 18,
  HW_KEY_Thumb3    = 19,
  HW_KEY_Thumb4    = 20,

  HW_KEY_Up        = 21,
  HW_KEY_Down      = 22,
  HW_KEY_Left      = 23,
  HW_KEY_Right     = 24,
  HW_KEY_Action    = 25,

  HW_KEY_CAL_OK    = 30,
  HW_KEY_CAL_FAIL  = 31,
  HW_KEY_CAL_EMPTY = 32,
  HW_KEY_CAL_RESET = 34,

  HW_KEY_ROUTING   = 80
} HW_NavigationKey;

typedef enum {
  HW_GRP_NavigationKeys = 0,
  HW_GRP_RoutingKeys
} HW_KeyGroup;

#endif /* BRLTTY_INCLUDED_HW_BRLDEFS */ 
