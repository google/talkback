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

#ifndef BRLTTY_INCLUDED_HT_BRLDEFS
#define BRLTTY_INCLUDED_HT_BRLDEFS

#define HT_USB_VENDOR 0X1FE4

typedef enum {
  HT_MODEL_UsbHidAdapter       = 0X03,
  HT_MODEL_BrailleWave         = 0X05,
  HT_MODEL_ModularEvolution64  = 0X36,
  HT_MODEL_ModularEvolution88  = 0X38,
  HT_MODEL_ModularConnect88    = 0X3A,
  HT_MODEL_EasyBraille         = 0X44,
  HT_MODEL_ActiveBraille       = 0X54,
  HT_MODEL_ConnectBraille40    = 0X55,
  HT_MODEL_Actilino            = 0X61,
  HT_MODEL_ActiveStar40        = 0X64,
  HT_MODEL_BasicBraille16      = 0X81,
  HT_MODEL_BasicBraille20      = 0X82,
  HT_MODEL_BasicBraille32      = 0X83,
  HT_MODEL_BasicBraille40      = 0X84,
  HT_MODEL_BasicBraille48      = 0X8A,
  HT_MODEL_BasicBraille64      = 0X86,
  HT_MODEL_BasicBraille80      = 0X87,
  HT_MODEL_BasicBraille160     = 0X8B,
  HT_MODEL_Braillino           = 0X72,
  HT_MODEL_BrailleStar40       = 0X74,
  HT_MODEL_BrailleStar80       = 0X78,
  HT_MODEL_Modular20           = 0X80,
  HT_MODEL_Modular80           = 0X88,
  HT_MODEL_Modular40           = 0X89,
  HT_MODEL_Bookworm            = 0X90,
  HT_MODEL_Activator           = 0XA4
} HT_ModelIdentifier;

/* Packet definition */
typedef enum {
  HT_PKT_Braille  = 0X01,
  HT_PKT_Extended = 0X79,
  HT_PKT_NAK      = 0X7D,
  HT_PKT_ACK      = 0X7E,
  HT_PKT_OK       = 0XFE,
  HT_PKT_Reset    = 0XFF
} HT_PacketType;

typedef enum {
  HT_EXTPKT_Braille               = HT_PKT_Braille,
  HT_EXTPKT_Key                   = 0X04,
  HT_EXTPKT_Confirmation          = 0X07,
  HT_EXTPKT_Scancode              = 0X09,
  HT_EXTPKT_Ping                  = 0X19,
  HT_EXTPKT_GetSerialNumber       = 0X41,
  HT_EXTPKT_SetRTC                = 0X44,
  HT_EXTPKT_GetRTC                = 0X45,
  HT_EXTPKT_GetBluetoothPIN       = 0X47,
  HT_EXTPKT_SetAtcMode            = 0X50,
  HT_EXTPKT_SetAtcSensitivity     = 0X51,
  HT_EXTPKT_AtcInfo               = 0X52,
  HT_EXTPKT_SetAtcSensitivity2    = 0X53,
  HT_EXTPKT_GetAtcSensitivity2    = 0X54,
  HT_EXTPKT_ReadingPosition       = 0X55,
  HT_EXTPKT_SetFirmness           = 0X60,
  HT_EXTPKT_GetFirmness           = 0X61,
  HT_EXTPKT_GetProtocolProperties = 0XC1,
  HT_EXTPKT_GetFirmwareVersion    = 0XC2
} HT_ExtendedPacketType;

typedef struct {
  uint16_t year;
  uint8_t month;
  uint8_t day;
  uint8_t hour;
  uint8_t minute;
  uint8_t second;
} PACKED HT_DateTime;

typedef enum {
  HT_MCAP_reqBatteryManagementInformation  = 0X001,
  HT_MCAP_BatteryCalibrationAndTestMode    = 0X002,
  HT_MCAP_getRealTimeClock                 = 0X004,
  HT_MCAP_setRealTimeClock                 = 0X008,
  HT_MCAP_getSerialNumber                  = 0X010,
  HT_MCAP_setSerialNumber                  = 0X020,
  HT_MCAP_getBluetoothPIN                  = 0X040,
  HT_MCAP_setBluetoothPIN                  = 0X080,
  HT_MCAP_setServiceInformation            = 0X100,
  HT_MCAP_getServiceInformation            = 0X200
} HT_MaintainanceCapabilities;

typedef enum {
  HT_ICAP_hasInternalMode               = 0X001,
  HT_ICAP_updNormalModeFirmware         = 0X002,
  HT_ICAP_updBrailleProcessorFirmware   = 0X004,
  HT_ICAP_updUsbProcessorFirmware       = 0X008,
  HT_ICAP_updBluetoothModuleFirmware    = 0X010,
  HT_ICAP_getBrailleSystemConfiguration = 0X020,
  HT_ICAP_setBrailleSystemConfiguration = 0X040
} HT_InternalModeCapabilities;

typedef struct {
  unsigned char majorVersion;
  unsigned char minorVersion;
  unsigned char cellCount;
  unsigned char hasSensitivity;
  unsigned char maximumSensitivity;
  unsigned char hasFirmness;
  unsigned char maximumFirmness;
  HT_MaintainanceCapabilities maintainanceCapabilities:16;
  HT_InternalModeCapabilities internalModeCapabilities:16;
} PACKED HT_ProtocolProperties;

typedef union {
  unsigned char bytes[4 + 0XFF];

  struct {
    unsigned char type;

    union {
      struct {
        unsigned char model;
      } PACKED ok;

      struct {
        unsigned char model;
        unsigned char length;
        unsigned char type;

        union {
          HT_DateTime dateTime;
          HT_ProtocolProperties protocolProperties;
          unsigned char bytes[0XFF];
        } data;
      } PACKED extended;
    } data;
  } PACKED fields;
} HT_Packet;

typedef enum {
  HT_KEY_None = 0,

  HT_KEY_B1 = 0X03,
  HT_KEY_B2 = 0X07,
  HT_KEY_B3 = 0X0B,
  HT_KEY_B4 = 0X0F,

  HT_KEY_B5 = 0X13,
  HT_KEY_B6 = 0X17,
  HT_KEY_B7 = 0X1B,
  HT_KEY_B8 = 0X1F,

  HT_KEY_Up = 0X04,
  HT_KEY_Down = 0X08,

  /* Keypad keys (star80 and modular) */
  HT_KEY_B12 = 0X01,
  HT_KEY_Zero = 0X05,
  HT_KEY_B13 = 0X09,
  HT_KEY_B14 = 0X0D,

  HT_KEY_B11 = 0X11,
  HT_KEY_One = 0X15,
  HT_KEY_Two = 0X19,
  HT_KEY_Three = 0X1D,

  HT_KEY_B10 = 0X02,
  HT_KEY_Four = 0X06,
  HT_KEY_Five = 0X0A,
  HT_KEY_Six = 0X0E,

  HT_KEY_B9 = 0X12,
  HT_KEY_Seven = 0X16,
  HT_KEY_Eight = 0X1A,
  HT_KEY_Nine = 0X1E,

  /* Braille wave/star keys */
  HT_KEY_Escape = 0X0C,
  HT_KEY_Space = 0X10,
  HT_KEY_Return = 0X14,

  /* Braille star keys */
  HT_KEY_SpaceRight = 0X18,

  /* Actilino keys */
  HT_KEY_JoystickLeft       = 0X74,
  HT_KEY_JoystickRight      = 0X75,
  HT_KEY_JoystickUp         = 0X76,
  HT_KEY_JoystickDown       = 0X77,
  HT_KEY_JoystickAction     = 0X78,

  /* ranges and flags */
  HT_KEY_ROUTING = 0X20,
  HT_KEY_STATUS = 0X70,
  HT_KEY_RELEASE = 0X80
} HT_NavigationKey;

typedef enum {
  HT_GRP_NavigationKeys = 0,
  HT_GRP_RoutingKeys
} HT_KeyGroup;

#endif /* BRLTTY_INCLUDED_HT_BRLDEFS */ 
