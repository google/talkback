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

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <errno.h>

#include "log.h"
#include "strfmt.h"
#include "parse.h"
#include "async_wait.h"
#include "ascii.h"

typedef enum {
  PARM_PROTOCOL,
  PARM_VARIOKEYS
} DriverParameter;
#define BRLPARMS "protocol", "variokeys"

#define BRLSTAT ST_TiemanStyle
#define BRL_HAVE_STATUS_CELLS
#define BRL_HAVE_PACKET_IO
#include "brl_driver.h"
#include "brldefs-bm.h"

BEGIN_KEY_NAME_TABLE(display)
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+7, "Display8"),
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+6, "Display7"),
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+5, "Display6"),
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+4, "Display5"),
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+3, "Display4"),
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+2, "Display3"),
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+1, "Display2"),
  KEY_NAME_ENTRY(BM_KEY_DISPLAY+0, "Display1"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(command)
  KEY_NAME_ENTRY(BM_KEY_COMMAND+6, "Command7"),
  KEY_NAME_ENTRY(BM_KEY_COMMAND+5, "Command6"),
  KEY_NAME_ENTRY(BM_KEY_COMMAND+4, "Command5"),
  KEY_NAME_ENTRY(BM_KEY_COMMAND+3, "Command4"),
  KEY_NAME_ENTRY(BM_KEY_COMMAND+2, "Command3"),
  KEY_NAME_ENTRY(BM_KEY_COMMAND+1, "Command2"),
  KEY_NAME_ENTRY(BM_KEY_COMMAND+0, "Command1"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(front)
  KEY_NAME_ENTRY(BM_KEY_FRONT+0, "Front1"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+1, "Front2"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+2, "Front3"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+3, "Front4"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+4, "Front5"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+5, "Front6"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+6, "Front7"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+7, "Front8"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+8, "Front9"),
  KEY_NAME_ENTRY(BM_KEY_FRONT+9, "Front10"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(back)
  KEY_NAME_ENTRY(BM_KEY_BACK+0, "Back1"),
  KEY_NAME_ENTRY(BM_KEY_BACK+1, "Back2"),
  KEY_NAME_ENTRY(BM_KEY_BACK+2, "Back3"),
  KEY_NAME_ENTRY(BM_KEY_BACK+3, "Back4"),
  KEY_NAME_ENTRY(BM_KEY_BACK+4, "Back5"),
  KEY_NAME_ENTRY(BM_KEY_BACK+5, "Back6"),
  KEY_NAME_ENTRY(BM_KEY_BACK+6, "Back7"),
  KEY_NAME_ENTRY(BM_KEY_BACK+7, "Back8"),
  KEY_NAME_ENTRY(BM_KEY_BACK+8, "Back9"),
  KEY_NAME_ENTRY(BM_KEY_BACK+9, "Back10"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(entry)
  KEY_NAME_ENTRY(BM_KEY_B9, "B9"),
  KEY_NAME_ENTRY(BM_KEY_B10, "B10"),
  KEY_NAME_ENTRY(BM_KEY_B11, "B11"),

  KEY_NAME_ENTRY(BM_KEY_F1, "F1"),
  KEY_NAME_ENTRY(BM_KEY_F2, "F2"),
  KEY_NAME_ENTRY(BM_KEY_F3, "F3"),
  KEY_NAME_ENTRY(BM_KEY_F4, "F4"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(dots)
  KEY_NAME_ENTRY(BM_KEY_DOT1, "Dot1"),
  KEY_NAME_ENTRY(BM_KEY_DOT2, "Dot2"),
  KEY_NAME_ENTRY(BM_KEY_DOT3, "Dot3"),
  KEY_NAME_ENTRY(BM_KEY_DOT4, "Dot4"),
  KEY_NAME_ENTRY(BM_KEY_DOT5, "Dot5"),
  KEY_NAME_ENTRY(BM_KEY_DOT6, "Dot6"),
  KEY_NAME_ENTRY(BM_KEY_DOT7, "Dot7"),
  KEY_NAME_ENTRY(BM_KEY_DOT8, "Dot8"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(joystick)
  KEY_NAME_ENTRY(BM_KEY_UP, "Up"),
  KEY_NAME_ENTRY(BM_KEY_LEFT, "Left"),
  KEY_NAME_ENTRY(BM_KEY_DOWN, "Down"),
  KEY_NAME_ENTRY(BM_KEY_RIGHT, "Right"),
  KEY_NAME_ENTRY(BM_KEY_PRESS, "Press"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(navpad)
  KEY_NAME_ENTRY(BM_KEY_UP, "Up"),
  KEY_NAME_ENTRY(BM_KEY_LEFT, "Left"),
  KEY_NAME_ENTRY(BM_KEY_DOWN, "Down"),
  KEY_NAME_ENTRY(BM_KEY_RIGHT, "Right"),
  KEY_NAME_ENTRY(BM_KEY_PRESS, "Select"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(wheels)
  KEY_NAME_ENTRY(BM_KEY_WHEEL_UP+0, "FirstWheelUp"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_DOWN+0, "FirstWheelDown"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_PRESS+0, "FirstWheelPress"),

  KEY_NAME_ENTRY(BM_KEY_WHEEL_UP+1, "SecondWheelUp"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_DOWN+1, "SecondWheelDown"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_PRESS+1, "SecondWheelPress"),

  KEY_NAME_ENTRY(BM_KEY_WHEEL_UP+2, "ThirdWheelUp"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_DOWN+2, "ThirdWheelDown"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_PRESS+2, "ThirdWheelPress"),

  KEY_NAME_ENTRY(BM_KEY_WHEEL_UP+3, "FourthWheelUp"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_DOWN+3, "FourthWheelDown"),
  KEY_NAME_ENTRY(BM_KEY_WHEEL_PRESS+3, "FourthWheelPress"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(status)
  KEY_NAME_ENTRY(BM_KEY_STATUS+0, "StatusButton1"),
  KEY_NAME_ENTRY(BM_KEY_STATUS+1, "StatusButton2"),
  KEY_NAME_ENTRY(BM_KEY_STATUS+2, "StatusButton3"),
  KEY_NAME_ENTRY(BM_KEY_STATUS+3, "StatusButton4"),

  KEY_NAME_ENTRY(BM_KEY_STATUS+4, "StatusKey1"),
  KEY_NAME_ENTRY(BM_KEY_STATUS+5, "StatusKey2"),
  KEY_NAME_ENTRY(BM_KEY_STATUS+6, "StatusKey3"),
  KEY_NAME_ENTRY(BM_KEY_STATUS+7, "StatusKey4"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing)
  KEY_GROUP_ENTRY(BM_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(horizontal)
  KEY_GROUP_ENTRY(BM_GRP_HorizontalSensors, "HorizontalSensor"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(vertical)
  KEY_GROUP_ENTRY(BM_GRP_LeftSensors, "LeftSensor"),
  KEY_GROUP_ENTRY(BM_GRP_RightSensors, "RightSensor"),
  KEY_GROUP_ENTRY(BM_GRP_ScaledLeftSensors, "ScaledLeftSensor"),
  KEY_GROUP_ENTRY(BM_GRP_ScaledRightSensors, "ScaledRightSensor"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(NLS_Zoomax)
  KEY_NAME_ENTRY(BM_KEY_B9, "BL"),
  KEY_NAME_ENTRY(BM_KEY_B10, "Space"),

  KEY_NAME_ENTRY(BM_KEY_F1, "S1"),
  KEY_NAME_ENTRY(BM_KEY_F2, "S2"),
  KEY_NAME_ENTRY(BM_KEY_F3, "S3"),
  KEY_NAME_ENTRY(BM_KEY_F4, "S4"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(orbit)
  KEY_NAME_ENTRY(BM_KEY_B9, "Space"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(default)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(joystick),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(rb)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(joystick),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(orbit)
  KEY_NAME_TABLE(orbit),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(navpad),
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(NLS_Zoomax)
  KEY_NAME_TABLE(NLS_Zoomax),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(navpad),
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(b2g)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(navpad),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(connect)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(joystick),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(conny)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(joystick),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(pronto)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(joystick),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(pv)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(joystick),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(sv)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(ultra)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(entry),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(joystick),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(inka)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(horizontal),
  KEY_NAME_TABLE(vertical),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(dm80p)
  KEY_NAME_SUBTABLE(display,7),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(v40)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(v80)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(command),
  KEY_NAME_TABLE(front),
  KEY_NAME_TABLE(back),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(pro)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(wheels),
  KEY_NAME_TABLE(status),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(vk)
  KEY_NAME_SUBTABLE(display,6),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(default)
DEFINE_KEY_TABLE(rb)
DEFINE_KEY_TABLE(orbit)
DEFINE_KEY_TABLE(NLS_Zoomax)
DEFINE_KEY_TABLE(b2g)
DEFINE_KEY_TABLE(connect)
DEFINE_KEY_TABLE(conny)
DEFINE_KEY_TABLE(pronto)
DEFINE_KEY_TABLE(pv)
DEFINE_KEY_TABLE(sv)
DEFINE_KEY_TABLE(ultra)
DEFINE_KEY_TABLE(inka)
DEFINE_KEY_TABLE(dm80p)
DEFINE_KEY_TABLE(v40)
DEFINE_KEY_TABLE(v80)
DEFINE_KEY_TABLE(pro)
DEFINE_KEY_TABLE(vk)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(default),
  &KEY_TABLE_DEFINITION(rb),
  &KEY_TABLE_DEFINITION(orbit),
  &KEY_TABLE_DEFINITION(NLS_Zoomax),
  &KEY_TABLE_DEFINITION(b2g),
  &KEY_TABLE_DEFINITION(connect),
  &KEY_TABLE_DEFINITION(conny),
  &KEY_TABLE_DEFINITION(pronto),
  &KEY_TABLE_DEFINITION(pv),
  &KEY_TABLE_DEFINITION(sv),
  &KEY_TABLE_DEFINITION(ultra),
  &KEY_TABLE_DEFINITION(inka),
  &KEY_TABLE_DEFINITION(dm80p),
  &KEY_TABLE_DEFINITION(v40),
  &KEY_TABLE_DEFINITION(v80),
  &KEY_TABLE_DEFINITION(pro),
  &KEY_TABLE_DEFINITION(vk),
END_KEY_TABLE_LIST

/* Global Definitions */

static const int probeLimit = 2;
static const int probeTimeout = 200;

#define KEY_GROUP_SIZE(count) (((count) + 7) / 8)
#define MAXIMUM_CELL_COUNT 84
#define VERTICAL_SENSOR_COUNT 27

static int cellCount;
static int cellsUpdated;
static unsigned char internalCells[MAXIMUM_CELL_COUNT];
static unsigned char externalCells[MAXIMUM_CELL_COUNT];

typedef struct {
  unsigned char navigationKeys[KEY_GROUP_SIZE(BM_KEY_COUNT)];
  unsigned char routingKeys[KEY_GROUP_SIZE(MAXIMUM_CELL_COUNT)];
  unsigned char horizontalSensors[KEY_GROUP_SIZE(MAXIMUM_CELL_COUNT)];
  unsigned char leftSensors[KEY_GROUP_SIZE(VERTICAL_SENSOR_COUNT)];
  unsigned char rightSensors[KEY_GROUP_SIZE(VERTICAL_SENSOR_COUNT)];
} KeysState;

static KeysState keysState;
static unsigned char switchSettings;

typedef struct {
  const char *name;
  const DotsTable *dotsTable;

  unsigned int serialBaud;
  SerialParity serialParity;

  int (*readPacket) (BrailleDisplay *brl, unsigned char *packet, int size);
  int (*writePacket) (BrailleDisplay *brl, const unsigned char *packet, int length);

  int (*probeDevice) (BrailleDisplay *brl);
  void (*processPackets) (BrailleDisplay *brl);

  int (*writeCells) (BrailleDisplay *brl);
  int (*writeCellRange) (BrailleDisplay *brl, unsigned int start, unsigned int count);
} ProtocolOperations;

struct BrailleDataStruct {
  const ProtocolOperations *protocol;

  struct {
    unsigned char routingKeys;
  } packetSize;
};

/* Internal Routines */

static void
logTextField (const char *name, const char *address, int size) {
  while (size > 0) {
    const char byte = address[size - 1];

    if (byte && (byte != ' ')) break;
    size -= 1;
  }

  logMessage(LOG_INFO, "%s: %.*s", name, size, address);
}

static int
setGroupedKey (unsigned char *set, KeyNumber number, int press) {
  unsigned char *byte = &set[number / 8];
  unsigned char bit = 1 << (number % 8);

  if (!(*byte & bit) == !press) return 0;

  if (press) {
    *byte |= bit;
  } else {
    *byte &= ~bit;
  }

  return 1;
}

static void
clearKeyGroup (unsigned char *set, unsigned char count) {
  memset(set, 0, KEY_GROUP_SIZE(count));
}

static void
resetKeyGroup (unsigned char *set, unsigned char count, KeyNumber key) {
  clearKeyGroup(set, count);
  if (key > 0) setGroupedKey(set, key-1, 1);
}

static void
updateKeyGroup (
  BrailleDisplay *brl,
  unsigned char *old, const unsigned char *new,
  KeyGroup group, KeyNumber base, unsigned char count, int scaled
) {
  KeyNumber pressTable[count];
  unsigned char pressCount = 0;
  unsigned char offset;

  for (offset=0; offset<count; offset+=1) {
    KeyNumber number = base + offset;
    int press = (new[offset / 8] & (1 << (offset % 8))) != 0;

    if (setGroupedKey(old, number, press)) {
      if (scaled) number = rescaleInteger(number, count-1, BRL_MSK_ARG);

      if (press) {
        pressTable[pressCount++] = number;
      } else {
        enqueueKeyEvent(brl, group, number, 0);
      }
    }
  }

  while (pressCount) enqueueKeyEvent(brl, group, pressTable[--pressCount], 1);
}

static void
updateNavigationKeys (
  BrailleDisplay *brl,
  const unsigned char *new, KeyNumber base, unsigned char count
) {
  updateKeyGroup(brl, keysState.navigationKeys, new, BM_GRP_NavigationKeys, base, count, 0);
}

static void
updateDisplayKeys (BrailleDisplay *brl, unsigned char new) {
  updateNavigationKeys(brl, &new, BM_KEY_DISPLAY, BM_KEYS_DISPLAY);
}

static void
updateEntryKeys (BrailleDisplay *brl, unsigned char *new) {
  updateNavigationKeys(brl, new, BM_KEY_ENTRY, BM_KEYS_ENTRY);
}

static void
updateJoystick (BrailleDisplay *brl, unsigned char *new) {
  updateNavigationKeys(brl, new, BM_KEY_JOYSTICK, BM_KEYS_JOYSTICK);
}

static void
updateRoutingKeys (BrailleDisplay *brl, const unsigned char *new, unsigned char count) {
  updateKeyGroup(brl, keysState.routingKeys, new, BM_GRP_RoutingKeys, 0, count, 0);
}

static int
updateCells (BrailleDisplay *brl) {
  if (cellsUpdated) {
    if (!brl->data->protocol->writeCells(brl)) return 0;
    cellsUpdated = 0;
  }
  return 1;
}

static int
updateCellRange (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  if (count) {
    translateOutputCells(&externalCells[start], &internalCells[start], count);
    cellsUpdated = 1;
    if (!brl->data->protocol->writeCellRange(brl, start, count)) return 0;
  }

  return 1;
}

static int
clearCellRange (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  memset(&internalCells[start], 0, count);
  return updateCellRange(brl, start, count);
}

static int
putCells (BrailleDisplay *brl, const unsigned char *cells, unsigned int start, unsigned int count) {
  unsigned int from;
  unsigned int to;

  if (cellsHaveChanged(&internalCells[start], cells, count, &from, &to, NULL)) {
    if (!updateCellRange(brl, start+from, to-from)) return 0;
  }

  return 1;
}

static int
isAcceptableCellCount (int count) {
  return (count > 0) && (count <= MAXIMUM_CELL_COUNT);
}

static void
logUnexpectedCellCount (int count) {
  logMessage(LOG_DEBUG, "unexpected cell count: %d", count);
}

static void
logCellCount (BrailleDisplay *brl) {
  switch ((brl->textColumns = cellCount)) {
    case 44:
    case 68:
    case 84:
      brl->textColumns -= 4;
      break;

    case 56:
      brl->textColumns -= 16;
      break;
  }
  brl->textRows = 1;
  brl->statusRows = (brl->statusColumns = cellCount - brl->textColumns)? 1: 0;

  logMessage(LOG_INFO, "Cell Count: %d (%d text, %d status)",
             cellCount, brl->textColumns, brl->statusColumns);
}

static int
changeCellCount (BrailleDisplay *brl, int count) {
  int ok = 1;

  if (count != cellCount) {
    if (count > cellCount) {
      if (!clearCellRange(brl, cellCount, count-cellCount)) ok = 0;

      {
        int number;
        for (number=cellCount; number<count; number+=1) {
          setGroupedKey(keysState.routingKeys, number, 0);
          setGroupedKey(keysState.horizontalSensors, number, 0);
        }
      }
    }

    cellCount = count;
    logCellCount(brl);
    brl->resizeRequired = 1;
  }

  return ok;
}

/* Baum Protocol */

typedef unsigned char BaumInteger[2];
#define MAKE_BAUM_INTEGER_FIRST(i) ((i) & 0XFF)
#define MAKE_BAUM_INTEGER_SECOND(i) (((i) >> 8) & 0XFF)
#define MAKE_BAUM_INTEGER(i) MAKE_BAUM_INTEGER_FIRST((i)), MAKE_BAUM_INTEGER_SECOND((i))

static inline uint16_t
getBaumInteger (const BaumInteger integer) {
  return (integer[1] << 8) | integer[0];
}

typedef enum {
  BAUM_REQ_DisplayData             = 0X01,
  BAUM_REQ_GetVersionNumber        = 0X05,
  BAUM_REQ_GetKeys                 = 0X08,
  BAUM_REQ_GetMode                 = 0X11,
  BAUM_REQ_SetMode                 = 0X12,
  BAUM_REQ_SetProtocolState        = 0X15,
  BAUM_REQ_SetCommunicationChannel = 0X16,
  BAUM_REQ_CausePowerdown          = 0X17,
  BAUM_REQ_ModuleRegistration      = 0X50,
  BAUM_REQ_DataRegisters           = 0X51,
  BAUM_REQ_ServiceRegisters        = 0X52,
  BAUM_REQ_GetDeviceIdentity       = 0X84,
  BAUM_REQ_GetSerialNumber         = 0X8A,
  BAUM_REQ_GetBluetoothName        = 0X8C,
  BAUM_REQ_SetBluetoothName        = 0X8D,
  BAUM_REQ_SetBluetoothPin         = 0X8E
} BaumRequestCode;

typedef enum {
  BAUM_RSP_CellCount            = 0X01,
  BAUM_RSP_VersionNumber        = 0X05,
  BAUM_RSP_ModeSetting          = 0X11,
  BAUM_RSP_CommunicationChannel = 0X16,
  BAUM_RSP_PowerdownSignal      = 0X17,
  BAUM_RSP_HorizontalSensors    = 0X20,
  BAUM_RSP_VerticalSensors      = 0X21,
  BAUM_RSP_RoutingKeys          = 0X22,
  BAUM_RSP_Switches             = 0X23,
  BAUM_RSP_DisplayKeys          = 0X24,
  BAUM_RSP_HorizontalSensor     = 0X25,
  BAUM_RSP_VerticalSensor       = 0X26,
  BAUM_RSP_RoutingKey           = 0X27,
  BAUM_RSP_Front6               = 0X28,
  BAUM_RSP_Back6                = 0X29,
  BAUM_RSP_CommandKeys          = 0X2B,
  BAUM_RSP_Front10              = 0X2C,
  BAUM_RSP_Back10               = 0X2D,
  BAUM_RSP_EntryKeys            = 0X33,
  BAUM_RSP_Joystick             = 0X34,
  BAUM_RSP_ErrorCode            = 0X40,
  BAUM_RSP_ModuleRegistration   = 0X50,
  BAUM_RSP_DataRegisters        = 0X51,
  BAUM_RSP_ServiceRegisters     = 0X52,
  BAUM_RSP_DeviceIdentity       = 0X84,
  BAUM_RSP_SerialNumber         = 0X8A,
  BAUM_RSP_BluetoothName        = 0X8C,
  BAUM_RSP_NLS_ZMX_BD           = 0XBD,
  BAUM_RSP_NLS_ZMX_BE           = 0XBE,
  BAUM_RSP_NLS_ZMX_BF           = 0XBF,
} BaumResponseCode;

typedef enum {
  BAUM_MODE_KeyGroupCompressed          = 0X01,
  BAUM_MODE_HorizontalSensorsEnabled    = 0X06,
  BAUM_MODE_LeftSensorsEnabled  = 0X07,
  BAUM_MODE_RoutingKeysEnabled          = 0X08,
  BAUM_MODE_RightSensorsEnabled = 0X09,
  BAUM_MODE_BackKeysEnabled             = 0X0A,
  BAUM_MODE_DisplayRotated              = 0X10,
  BAUM_MODE_DisplayEnabled              = 0X20,
  BAUM_MODE_PowerdownEnabled            = 0X21,
  BAUM_MODE_PowerdownTime               = 0X22,
  BAUM_MODE_BluetoothEnabled            = 0X23,
  BAUM_MODE_UsbCharge                   = 0X24
} BaumMode;

typedef enum {
  BAUM_PDT_5Minutes  = 1,
  BAUM_PDT_10Minutes = 2,
  BAUM_PDT_1Hour     = 3,
  BAUM_PDT_2Hours    = 4
} BaumPowerdownTime;

typedef enum {
  BAUM_PDR_ProtocolRequested = 0X01,
  BAUM_PDR_PowerSwitch       = 0X02,
  BAUM_PDR_AutoPowerOff      = 0X04,
  BAUM_PDR_BatteryLow        = 0X08,
  BAUM_PDR_Charging          = 0X80
} BaumPowerdownReason;

typedef enum {
  BAUM_SWT_DisableSensors  = 0X01,
  BAUM_SWT_ScaledVertical  = 0X02,
  BAUM_SWT_ShowSensor      = 0X40,
  BAUM_SWT_BrailleKeyboard = 0X80
} BaumSwitch;

typedef enum {
  BAUM_ERR_BluetoothSupport       = 0X0A,
  BAUM_ERR_TransmitOverrun        = 0X10,
  BAUM_ERR_ReceiveOverrun         = 0X11,
  BAUM_ERR_TransmitTimeout        = 0X12,
  BAUM_ERR_ReceiveTimeout         = 0X13,
  BAUM_ERR_PacketType             = 0X14,
  BAUM_ERR_PacketChecksum         = 0X15,
  BAUM_ERR_PacketData             = 0X16,
  BAUM_ERR_Test                   = 0X18,
  BAUM_ERR_FlashWrite             = 0X19,
  BAUM_ERR_CommunicationChannel   = 0X1F,
  BAUM_ERR_SerialNumber           = 0X20,
  BAUM_ERR_SerialParity           = 0X21,
  BAUM_ERR_SerialOverrun          = 0X22,
  BAUM_ERR_SerialFrame            = 0X24,
  BAUM_ERR_LocalizationIdentifier = 0X25,
  BAUM_ERR_LocalizationIndex      = 0X26,
  BAUM_ERR_LanguageIdentifier     = 0X27,
  BAUM_ERR_LanguageIndex          = 0X28,
  BAUM_ERR_BrailleTableIdentifier = 0X29,
  BAUM_ERR_BrailleTableIndex      = 0X2A
} BaumError;

#define BAUM_LENGTH_DeviceIdentity 18
#define BAUM_LENGTH_SerialNumber 8
#define BAUM_LENGTH_BluetoothName 14

typedef enum {
  BAUM_MRC_Acknowledge = 0X01,
  BAUM_MRC_Query       = 0X04
} BaumModuleRegistrationCommand;

typedef enum {
  BAUM_MRE_Addition  = 1,
  BAUM_MRE_Removal   = 2,
  BAUM_MRE_Rejection = 3
} BaumModuleRegistrationEvent;

typedef enum {
  BAUM_DRC_Write = 0X00,
  BAUM_DRC_Read  = 0X01,
  BAUM_DRC_Reset = 0X80
} BaumDataRegistersCommand;

typedef enum {
  BAUM_DRF_WheelsChanged  = 0X01,
  BAUM_DRF_ButtonsChanged = 0X02,
  BAUM_DRF_KeysChanged    = 0X04,
  BAUM_DRF_PotsChanged    = 0X04,
  BAUM_DRF_SensorsChanged = 0X08,
  BAUM_DRF_ErrorOccurred  = 0X80
} BaumDataRegistersFlag;

typedef enum {
  BAUM_DRE_WheelsNotConnected = 0X01,
  BAUM_DRE_WheelsNotAdjusted  = 0X02,
  BAUM_DRE_KeyBufferFull      = 0X04,
  BAUM_DRE_SerialError        = 0X80
} BaumDataRegistersError;

typedef enum {
  BAUM_SRC_Write = 0X00,
  BAUM_SRC_Read  = 0X01
} BaumServiceRegistersCommand;

typedef union {
  unsigned char bytes[2 + 0XFF];

  struct {
    unsigned char code;

    union {
      unsigned char cellCount;
      unsigned char versionNumber;

      struct {
        unsigned char identifier;
        unsigned char setting;
      } PACKED mode;

      unsigned char communicationChannel;
      unsigned char powerdownReason;
      unsigned char horizontalSensors[KEY_GROUP_SIZE(MAXIMUM_CELL_COUNT)];

      struct {
        unsigned char left[KEY_GROUP_SIZE(VERTICAL_SENSOR_COUNT)];
        unsigned char right[KEY_GROUP_SIZE(VERTICAL_SENSOR_COUNT)];
      } PACKED verticalSensors;

      unsigned char routingKeys[KEY_GROUP_SIZE(MAXIMUM_CELL_COUNT)];
      unsigned char switches;
      unsigned char displayKeys;
      unsigned char horizontalSensor;

      union {
        unsigned char left;
        unsigned char right;
      } PACKED verticalSensor;

      unsigned char routingKey;
      unsigned char front6[1];
      unsigned char back6[1];
      unsigned char commandKeys[1];
      unsigned char front10[2];
      unsigned char back10[2];
      unsigned char entryKeys[2];
      unsigned char joystick[1];
      unsigned char errorCode;

      struct {
        unsigned char length;
        BaumInteger moduleIdentifier;
        BaumInteger serialNumber;

        union {
          struct {
            BaumInteger hardwareVersion;
            BaumInteger firmwareVersion;
            unsigned char event;
          } PACKED registration;

          union {
            struct {
              unsigned char flags;
              unsigned char errors;
              signed char wheels[4];
              unsigned char buttons;
              unsigned char keys;
              unsigned char sensors[KEY_GROUP_SIZE(80)];
            } PACKED display80;

            struct {
              unsigned char flags;
              unsigned char errors;
              signed char wheels[3];
              unsigned char buttons;
              unsigned char keys;
              unsigned char sensors[KEY_GROUP_SIZE(64)];
            } PACKED display64;

            struct {
              unsigned char flags;
              unsigned char errors;
              unsigned char buttons;
            } PACKED status;

            struct {
              unsigned char flags;
              unsigned char errors;
              signed char wheel;
              unsigned char buttons;
              unsigned char keypad[2];
            } PACKED phone;

            struct {
              unsigned char flags;
              unsigned char errors;
              signed char wheel;
              unsigned char keys;
              unsigned char pots[6];
            } PACKED audio;

            struct {
              unsigned char flags;
              unsigned char errors;
              unsigned char buttons;
              unsigned char cursor;
              unsigned char keys;
              unsigned char pots[4];
            } PACKED voice;
          } registers;
        } data;
      } PACKED modular;

      char deviceIdentity[BAUM_LENGTH_DeviceIdentity];
      char serialNumber[BAUM_LENGTH_SerialNumber];
      char bluetoothName[BAUM_LENGTH_BluetoothName];
    } PACKED values;
  } PACKED data;
} PACKED BaumResponsePacket;

typedef enum {
  BAUM_DEVICE_Default = 0,

  BAUM_DEVICE_Refreshabraille,
  BAUM_DEVICE_Orbit,
  BAUM_DEVICE_NLS_Zoomax,
  BAUM_DEVICE_B2G,

  BAUM_DEVICE_Conny,
  BAUM_DEVICE_PocketVario,
  BAUM_DEVICE_Pronto,
  BAUM_DEVICE_SuperVario,
  BAUM_DEVICE_VarioConnect,
  BAUM_DEVICE_VarioUltra,

  BAUM_DEVICE_Inka,
  BAUM_DEVICE_DM80P,
  BAUM_DEVICE_Vario40,
  BAUM_DEVICE_Vario80,
  BAUM_DEVICE_Modular
} BaumDeviceType;

typedef struct {
  const char *string;
  BaumDeviceType type;
} BaumDeviceIdentityEntry;

static const BaumDeviceIdentityEntry baumDeviceIdentityTable[] = {
  { .string = "Refreshabraille",
    .type = BAUM_DEVICE_Refreshabraille
  },

  { .string = "Orbit",
    .type = BAUM_DEVICE_Orbit
  },

  { .string = "NLS eReader Zoomax",
    .type = BAUM_DEVICE_NLS_Zoomax
  },

  { .string = "Conny (NBP B2G)",
    .type = BAUM_DEVICE_B2G
  },

  { .string = "BrailleConnect",
    .type = BAUM_DEVICE_VarioConnect
  },

  { .string = "Brailliant",
    .type = BAUM_DEVICE_SuperVario
  },

  { .string = "Conny",
    .type = BAUM_DEVICE_Conny
  },

  { .string = "PocketVario",
    .type = BAUM_DEVICE_PocketVario
  },

  { .string = "Pronto",
    .type = BAUM_DEVICE_Pronto
  },

  { .string = "SuperVario",
    .type = BAUM_DEVICE_SuperVario
  },

  { .string = "SVario",
    .type = BAUM_DEVICE_SuperVario
  },

  { .string = "Vario 40",
    .type = BAUM_DEVICE_Vario40
  },

  { .string = "VarioConnect",
    .type = BAUM_DEVICE_VarioConnect
  },

  { .string = "VarioUltra",
    .type = BAUM_DEVICE_VarioUltra
  },
};

static const unsigned char baumDeviceIdentityCount = ARRAY_COUNT(baumDeviceIdentityTable);
static BaumDeviceType baumDeviceType;

static void
setBaumDeviceType (const char *identity, size_t size) {
  const BaumDeviceIdentityEntry *bdi = baumDeviceIdentityTable;
  const BaumDeviceIdentityEntry *end = bdi + baumDeviceIdentityCount;

  while (bdi < end) {
    size_t length = strlen(bdi->string);
    const char *from = identity;
    const char *to = from + size - length;

    while (from <= to) {
      if (*from == *bdi->string) {
        if (memcmp(from, bdi->string, length) == 0) {
          baumDeviceType = bdi->type;
          return;
        }
      }

      from += 1;
    }

    bdi += 1;
  }
}

typedef enum {
  BAUM_MODULE_Display80,
  BAUM_MODULE_Display64,
  BAUM_MODULE_Status,
  BAUM_MODULE_Phone,
  BAUM_MODULE_Audio,
  BAUM_MODULE_Voice
} BaumModuleType;

typedef struct {
  uint16_t identifier;
  unsigned char type;
  unsigned char cellCount;
  unsigned char keyCount;
  unsigned char buttonCount;
  unsigned char wheelCount;
  unsigned char potCount;
  unsigned isDisplay:1;
  unsigned hasCursorKeys:1;
  unsigned hasKeypad:1;
} BaumModuleDescription;

static const BaumModuleDescription baumModuleDescriptions[] = {
  { .identifier = 0X4180,
    .type = BAUM_MODULE_Display80,
    .cellCount = 80,
    .wheelCount = 4,
    .isDisplay = 1
  }
  ,
  { .identifier = 0X4181,
    .type = BAUM_MODULE_Display64,
    .cellCount = 64,
    .wheelCount = 3,
    .isDisplay = 1
  }
  ,
  { .identifier = 0X4190,
    .type = BAUM_MODULE_Status,
    .cellCount = 4,
    .buttonCount = 4
  }
  ,
  { .identifier = 0X4191,
    .type = BAUM_MODULE_Phone,
    .cellCount = 12,
    .buttonCount = 4,
    .wheelCount = 1,
    .hasKeypad = 1
  }
  ,
  { .identifier = 0X4192,
    .type = BAUM_MODULE_Audio,
    .keyCount = 5,
    .wheelCount = 1,
    .potCount = 6
  }
  ,
  { .identifier = 0X4193,
    .type = BAUM_MODULE_Voice,
    .keyCount = 4,
    .buttonCount = 3,
    .potCount = 4,
    .hasCursorKeys = 1
  }
  ,
  { .identifier = 0 }
};

static const BaumModuleDescription *
getBaumModuleDescription (uint16_t identifier) {
  const BaumModuleDescription *bmd = baumModuleDescriptions;

  while (bmd->identifier) {
    if (bmd->identifier == identifier) return bmd;
    bmd += 1;
  }

  logMessage(LOG_DEBUG, "unknown module identifier: %04X", identifier);
  return NULL;
}

typedef struct {
  const BaumModuleDescription *description;
  uint16_t serialNumber;
  uint16_t hardwareVersion;
  uint16_t firmwareVersion;
} BaumModuleRegistration;

static void
clearBaumModuleRegistration (BaumModuleRegistration *bmr) {
  bmr->description = NULL;
  bmr->serialNumber = 0;
  bmr->hardwareVersion = 0;
  bmr->firmwareVersion = 0;
}

static BaumModuleRegistration baumDisplayModule;
static BaumModuleRegistration baumStatusModule;

static BaumModuleRegistration *const baumModules[] = {
  &baumDisplayModule,
  &baumStatusModule,
  NULL
};

static BaumModuleRegistration *
getBaumModuleRegistration (const BaumModuleDescription *bmd, uint16_t serialNumber) {
  if (bmd) {
    BaumModuleRegistration *const *bmr = baumModules;

    while (*bmr) {
      if (((*bmr)->description == bmd) && ((*bmr)->serialNumber == serialNumber)) return *bmr;
      bmr += 1;
    }
  }

  return NULL;
}

static int
getBaumModuleCellCount (void) {
  int count = 0;

  {
    BaumModuleRegistration *const *bmr = baumModules;

    while (*bmr) {
      const BaumModuleDescription *bmd = (*bmr++)->description;
      if (bmd) count += bmd->cellCount;
    }
  }

  return count;
}

static void
assumeBaumDeviceIdentity (const char *identity) {
  logMessage(LOG_INFO, "Baum Device Identity: %s", identity);
}

static void
handleBaumDeviceIdentity (const BaumResponsePacket *packet, int probing) {
  const char *identity = packet->data.values.deviceIdentity;
  size_t size = sizeof(packet->data.values.deviceIdentity);

  logTextField("Baum Device Identity", identity, size);
  if (probing) setBaumDeviceType(identity, size);
}

static void
logBaumSerialNumber (const BaumResponsePacket *packet) {
  logTextField("Baum Serial Number",
               packet->data.values.serialNumber,
               sizeof(packet->data.values.serialNumber));
}

static int
logBaumPowerdownReason (BaumPowerdownReason reason) {
  typedef struct {
    BaumPowerdownReason bit;
    const char *explanation;
  } ReasonEntry;

  static const ReasonEntry reasonTable[] = {
    {BAUM_PDR_ProtocolRequested, strtext("driver request")},
    {BAUM_PDR_PowerSwitch      , strtext("power switch")},
    {BAUM_PDR_AutoPowerOff     , strtext("idle timeout")},
    {BAUM_PDR_BatteryLow       , strtext("battery low")},
    {0}
  };

  char buffer[0X100];
  char delimiter = ':';
  int length;

  STR_BEGIN(buffer, sizeof(buffer));
  STR_PRINTF("%s %s", STRINGIFY(DRIVER_NAME), gettext("Powerdown"));

  for (const ReasonEntry *entry=reasonTable; entry->bit; entry+=1) {
    if (reason & entry->bit) {
      STR_PRINTF("%c %s", delimiter, gettext(entry->explanation));
      delimiter = ',';
    }
  }

  length = STR_LENGTH;
  STR_END;

  logMessage(LOG_WARNING, "%.*s", length, buffer);
  return 1;
}

static void
adjustPacketLength (const unsigned char *bytes, size_t size, size_t *length) {
  switch (bytes[0]) {
    case BAUM_RSP_DeviceIdentity:
      if (size == 17) {
        if (memcmp(&bytes[1], "Refreshabraille ", (size - 1)) == 0) {
          *length += 2;
        } else if (memcmp(&bytes[1], "NLS eReader Zoom", (size - 1)) == 0) {
          *length += 2;
        }
      }
      break;

    default:
      break;
  }
}

typedef enum {
  BAUM_PVS_WAITING,
  BAUM_PVS_STARTED,
  BAUM_PVS_ESCAPED
} BaumPacketVerificationState;

typedef struct {
  BaumPacketVerificationState state;
} BaumPacketVerificationData;

static BraillePacketVerifierResult
verifyBaumPacket (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  BaumPacketVerificationData *pvd = data;
  unsigned char byte = bytes[size-1];
  int escape = byte == ASCII_ESC;

  switch (pvd->state) {
    case BAUM_PVS_WAITING:
      if (!escape) return BRL_PVR_INVALID;
      pvd->state = BAUM_PVS_STARTED;
      return BRL_PVR_EXCLUDE;

    case BAUM_PVS_STARTED:
      if (escape) {
        pvd->state = BAUM_PVS_ESCAPED;
        return BRL_PVR_EXCLUDE;
      }
      break;

    case BAUM_PVS_ESCAPED:
      pvd->state = BAUM_PVS_STARTED;
      break;

    default:
      logMessage(LOG_NOTICE, "unexpected %s packet verification state: %u",
                 brl->data->protocol->name, pvd->state);
      return BRL_PVR_INVALID;
  }

  if (size == 1) {
    switch (byte) {
      case BAUM_RSP_Switches:
        if (!cellCount) {
          assumeBaumDeviceIdentity("DM80P");
          baumDeviceType = BAUM_DEVICE_DM80P;
          cellCount = 84;
        }

      case BAUM_RSP_CellCount:
      case BAUM_RSP_VersionNumber:
      case BAUM_RSP_CommunicationChannel:
      case BAUM_RSP_PowerdownSignal:
      case BAUM_RSP_DisplayKeys:
      case BAUM_RSP_HorizontalSensor:
      case BAUM_RSP_RoutingKey:
      case BAUM_RSP_Front6:
      case BAUM_RSP_Back6:
      case BAUM_RSP_CommandKeys:
      case BAUM_RSP_Joystick:
      case BAUM_RSP_ErrorCode:
      case BAUM_RSP_ModuleRegistration:
      case BAUM_RSP_DataRegisters:
      case BAUM_RSP_ServiceRegisters:
        *length = 2;
        break;

      case BAUM_RSP_ModeSetting:
      case BAUM_RSP_Front10:
      case BAUM_RSP_Back10:
      case BAUM_RSP_EntryKeys:
        *length = 3;
        break;

      case BAUM_RSP_VerticalSensor:
        *length = (baumDeviceType == BAUM_DEVICE_Inka)? 2: 3;
        break;

      case BAUM_RSP_VerticalSensors:
      case BAUM_RSP_SerialNumber:
        *length = 9;
        break;

      case BAUM_RSP_BluetoothName:
        *length = 15;
        break;

      case BAUM_RSP_DeviceIdentity:
        *length = 17;
        break;

      case BAUM_RSP_RoutingKeys:
        if (!cellCount) {
          assumeBaumDeviceIdentity("Inka");
          baumDeviceType = BAUM_DEVICE_Inka;
          cellCount = 56;
        }

        if (baumDeviceType == BAUM_DEVICE_Inka) {
          *length = 2;
          break;
        }

        *length = brl->data->packetSize.routingKeys + 1;
        break;

      case BAUM_RSP_HorizontalSensors:
        *length = KEY_GROUP_SIZE(brl->textColumns) + 1;
        break;

      case BAUM_RSP_NLS_ZMX_BD:
      case BAUM_RSP_NLS_ZMX_BE:
        *length = 2;
        break;

      case BAUM_RSP_NLS_ZMX_BF:
        *length = 2;
        break;

      default:
        pvd->state = BAUM_PVS_WAITING;
        return BRL_PVR_INVALID;
    }
  } else if (size == 2) {
    switch (bytes[0]) {
      case BAUM_RSP_ModuleRegistration:
      case BAUM_RSP_DataRegisters:
      case BAUM_RSP_ServiceRegisters:
        if (byte < 4) return BRL_PVR_INVALID;
        *length += byte;
        break;

      case BAUM_RSP_NLS_ZMX_BD:
      case BAUM_RSP_NLS_ZMX_BE:
        if (byte != ASCII_CR) return BRL_PVR_EXCLUDE;
        break;

      default:
        break;
    }
  }

  adjustPacketLength(bytes, size, length);
  return BRL_PVR_INCLUDE;
}

static int
readBaumPacket (BrailleDisplay *brl, unsigned char *packet, int size) {
  BaumPacketVerificationData pvd = {
    .state = BAUM_PVS_WAITING
  };

  memset(packet, 0, size);
  return readBraillePacket(brl, NULL, packet, size, verifyBaumPacket, &pvd);
}

static int
getBaumPacket (BrailleDisplay *brl, BaumResponsePacket *packet) {
  return readBaumPacket(brl, packet->bytes, sizeof(*packet));
}

static int
writeBaumPacket (BrailleDisplay *brl, const unsigned char *packet, int length) {
  unsigned char buffer[1 + (length * 2)];
  unsigned char *byte = buffer;
  *byte++ = ASCII_ESC;

  {
    int index = 0;
    while (index < length)
      if ((*byte++ = packet[index++]) == ASCII_ESC)
        *byte++ = ASCII_ESC;
  }

  return writeBraillePacket(brl, NULL, buffer, (byte - buffer));
}

static int
writeBaumModuleRegistrationCommand (
  BrailleDisplay *brl,
  uint16_t moduleIdentifier, uint16_t serialNumber,
  BaumModuleRegistrationCommand command
) {
  const unsigned char request[] = {
    BAUM_REQ_ModuleRegistration,
    5, /* data length */
    MAKE_BAUM_INTEGER(moduleIdentifier),
    MAKE_BAUM_INTEGER(serialNumber),
    command
  };

  return writeBaumPacket(brl, request, sizeof(request));
}

static int
writeBaumDataRegisters (
  BrailleDisplay *brl,
  const BaumModuleRegistration *bmr,
  const unsigned char *registers,
  unsigned char start, unsigned char count
) {
  const BaumModuleDescription *bmd = bmr->description;

  if (bmd) {
    if (count < bmd->cellCount) count = bmd->cellCount;

    if (count) {
      unsigned char packet[2 + 7 + count];
      unsigned char *byte = packet;

      *byte++ = BAUM_REQ_DataRegisters;
      *byte++ = 7 + count;

      *byte++ = MAKE_BAUM_INTEGER_FIRST(bmd->identifier);
      *byte++ = MAKE_BAUM_INTEGER_SECOND(bmd->identifier);

      *byte++ = MAKE_BAUM_INTEGER_FIRST(bmr->serialNumber);
      *byte++ = MAKE_BAUM_INTEGER_SECOND(bmr->serialNumber);

      *byte++ = BAUM_DRC_Write;
      *byte++ = start;
      *byte++ = count;
      byte = mempcpy(byte, registers, count);

      if (!writeBaumPacket(brl, packet, byte-packet)) return 0;
    }
  }

  return 1;
}

typedef struct {
  const KeyTableDefinition *keyTableDefinition;
  int (*writeAllCells) (BrailleDisplay *brl);
  int (*writeCellRange) (BrailleDisplay *brl, unsigned int start, unsigned int count);
} BaumDeviceOperations;

static int
writeBaumCells_all (BrailleDisplay *brl) {
  unsigned char packet[1 + cellCount];
  unsigned char *byte = packet;

  *byte++ = BAUM_REQ_DisplayData;
  byte = mempcpy(byte, externalCells, cellCount);

  return writeBaumPacket(brl, packet, byte-packet);
}

static int
writeBaumCells_start (BrailleDisplay *brl) {
  unsigned char packet[1 + 1 + cellCount];
  unsigned char *byte = packet;

  *byte++ = BAUM_REQ_DisplayData;
  *byte++ = 0;
  byte = mempcpy(byte, externalCells, cellCount);

  return writeBaumPacket(brl, packet, byte-packet);
}

static int
writeBaumCells_modular (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  if (start < brl->textColumns) {
    unsigned int amount = MIN(count, brl->textColumns-start);

    if (amount > 0) {
      if (!writeBaumDataRegisters(brl, &baumDisplayModule, &externalCells[start], start, amount)) return 0;
      start += amount;
      count -= amount;
    }
  }

  if (count > 0) {
    if (!writeBaumDataRegisters(brl, &baumStatusModule, &externalCells[start], start-brl->textColumns, count)) return 0;
  }

  return 1;
}

static const BaumDeviceOperations baumDeviceOperations[] = {
  [BAUM_DEVICE_Default] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(default),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_Refreshabraille] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(rb),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_Orbit] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(orbit),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_NLS_Zoomax] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(NLS_Zoomax),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_B2G] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(b2g),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_Conny] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(conny),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_PocketVario] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(pv),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_Pronto] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(pronto),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_SuperVario] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(sv),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_VarioConnect] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(connect),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_VarioUltra] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(ultra),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_Inka] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(inka),
    .writeAllCells = writeBaumCells_start
  },

  [BAUM_DEVICE_DM80P] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(dm80p),
    .writeAllCells = writeBaumCells_start
  },

  [BAUM_DEVICE_Vario40] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(v40),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_Vario80] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(v80),
    .writeAllCells = writeBaumCells_all
  },

  [BAUM_DEVICE_Modular] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(pro),
    .writeCellRange = writeBaumCells_modular
  }
};

static int
setBaumMode (BrailleDisplay *brl, unsigned char mode, unsigned char setting) {
  const unsigned char request[] = {BAUM_REQ_SetMode, mode, setting};
  return writeBaumPacket(brl, request, sizeof(request));
}

static void
setBaumSwitches (BrailleDisplay *brl, unsigned char newSettings, int initialize) {
  unsigned char changedSettings = newSettings ^ switchSettings;
  switchSettings = newSettings;

  {
    typedef struct {
      unsigned char switchBit;
      unsigned char modeNumber;
      unsigned char offValue;
      unsigned char onValue;
    } SwitchEntry;

    static const SwitchEntry switchTable[] = {
      {BAUM_SWT_ShowSensor, 0X01, 0, 2},
      {BAUM_SWT_BrailleKeyboard, 0X03, 0, 3},
      {0}
    };
    const SwitchEntry *entry = switchTable;

    while (entry->switchBit) {
      if (initialize || (changedSettings & entry->switchBit))
        setBaumMode(brl, entry->modeNumber,
                    ((switchSettings & entry->switchBit)? entry->onValue:
                                                          entry->offValue));
      ++entry;
    }
  }
}

static void
setInkaSwitches (BrailleDisplay *brl, unsigned char newSettings, int initialize) {
  newSettings ^= 0X0F;
  setBaumSwitches(brl, ((newSettings & 0X03) | ((newSettings & 0X0C) << 4)), initialize);
}

static int
handleBaumModuleRegistrationEvent (BrailleDisplay *brl, const BaumResponsePacket *packet) {
  uint16_t moduleIdentifier = getBaumInteger(packet->data.values.modular.moduleIdentifier);
  uint16_t serialNumber = getBaumInteger(packet->data.values.modular.serialNumber);
  const BaumModuleDescription *bmd = getBaumModuleDescription(moduleIdentifier);

  if (packet->data.values.modular.data.registration.event == BAUM_MRE_Addition) {
    if (!writeBaumModuleRegistrationCommand(brl,
                                            moduleIdentifier, serialNumber,
                                            BAUM_MRC_Acknowledge)) {
      return 0;
    }

    if (bmd) {
      BaumModuleRegistration *bmr;

      if (bmd->isDisplay) {
        bmr = &baumDisplayModule;
      } else if (bmd->type == BAUM_MODULE_Status) {
        bmr = &baumStatusModule;
      } else {
        bmr = NULL;
      }

      if (bmr) {
        if (bmr->description) clearBaumModuleRegistration(bmr);

        bmr->description = bmd;
        bmr->serialNumber = serialNumber;
        bmr->hardwareVersion = getBaumInteger(packet->data.values.modular.data.registration.hardwareVersion);
        bmr->firmwareVersion = getBaumInteger(packet->data.values.modular.data.registration.firmwareVersion);
      }
    }
  } else {
    BaumModuleRegistration *bmr = getBaumModuleRegistration(bmd, serialNumber);
    if (bmr) clearBaumModuleRegistration(bmr);
  }

  return 1;
}

static void
handleBaumDataRegistersEvent (BrailleDisplay *brl, const BaumResponsePacket *packet) {
  const BaumModuleDescription *bmd = getBaumModuleDescription(getBaumInteger(packet->data.values.modular.moduleIdentifier));
  const BaumModuleRegistration *bmr = getBaumModuleRegistration(bmd, getBaumInteger(packet->data.values.modular.serialNumber));

  if (bmr) {
    switch (bmd->type) {
      {
        unsigned char flags;
        unsigned char UNUSED errors;
        const signed char *wheel;
        unsigned char wheels;
        unsigned char buttons;
        unsigned char keys;
        const unsigned char *sensors;

      case BAUM_MODULE_Display80:
        flags = packet->data.values.modular.data.registers.display80.flags;
        errors = packet->data.values.modular.data.registers.display80.errors;
        wheel = packet->data.values.modular.data.registers.display80.wheels;
        wheels = ARRAY_COUNT(packet->data.values.modular.data.registers.display80.wheels);
        buttons = packet->data.values.modular.data.registers.display80.buttons;
        keys = packet->data.values.modular.data.registers.display80.keys;
        sensors = packet->data.values.modular.data.registers.display80.sensors;
        goto doDisplay;

      case BAUM_MODULE_Display64:
        flags = packet->data.values.modular.data.registers.display64.flags;
        errors = packet->data.values.modular.data.registers.display64.errors;
        wheel = packet->data.values.modular.data.registers.display64.wheels;
        wheels = ARRAY_COUNT(packet->data.values.modular.data.registers.display64.wheels);
        buttons = packet->data.values.modular.data.registers.display64.buttons;
        keys = packet->data.values.modular.data.registers.display64.keys;
        sensors = packet->data.values.modular.data.registers.display64.sensors;
        goto doDisplay;

      doDisplay:
        if (flags & BAUM_DRF_WheelsChanged) {
          unsigned int index;

          for (index=0; index<wheels; index+=1) {
            signed char count = wheel[index];

            while (count > 0) {
              enqueueKey(brl, BM_GRP_NavigationKeys, (BM_KEY_WHEEL_UP + index));
              count -= 1;
            }

            while (count < 0) {
              enqueueKey(brl, BM_GRP_NavigationKeys, (BM_KEY_WHEEL_DOWN + index));
              count += 1;
            }
          }
        }

        if (flags & BAUM_DRF_ButtonsChanged) {
          updateNavigationKeys(brl, &buttons, BM_KEY_WHEEL_PRESS, wheels);
        }

        if (flags & BAUM_DRF_KeysChanged) {
          updateDisplayKeys(brl, keys);
        }

        if (flags & BAUM_DRF_SensorsChanged) {
          updateRoutingKeys(brl, sensors, brl->textColumns);
        }

        break;
      }

      case BAUM_MODULE_Status:
        if (packet->data.values.modular.data.registers.status.flags & BAUM_DRF_ButtonsChanged) {
          updateNavigationKeys(brl, &packet->data.values.modular.data.registers.status.buttons,
                               BM_KEY_STATUS, BM_KEYS_STATUS);
        }

        break;

      default:
        logMessage(LOG_WARNING, "unsupported data register configuration: %u", bmd->type);
        break;
    }
  }
}

static int
getIdentityCellCount (char* deviceIdentity, const int length) {
  char buffer[length+1];
  memcpy(buffer, deviceIdentity, length);
  buffer[length] = 0;

  char *digits = strpbrk(buffer, "123456789");

  if (digits) {
    int count = atoi(digits);
    if (isAcceptableCellCount(count)) return count;
  }

  return 0;
}

static int
probeBaumDevice (BrailleDisplay *brl) {
  int probes = 0;

  do {
    int identityCellCount = 0;

    baumDeviceType = BAUM_DEVICE_Default;
    cellCount = 0;

    {
      BaumModuleRegistration *const *bmr = baumModules;
      while (*bmr) clearBaumModuleRegistration(*bmr++);
    }

    /* get the serial number for the log */
    {
      static const unsigned char request[] = {BAUM_REQ_GetSerialNumber};
      if (!writeBaumPacket(brl, request, sizeof(request))) break;
    }

    /* newer models return an identity string which contains the cell count */
    {
      static const unsigned char request[] = {BAUM_REQ_GetDeviceIdentity};
      if (!writeBaumPacket(brl, request, sizeof(request))) break;
    }

    /* try explicitly asking for the cell count */
    {
      static const unsigned char request[] = {BAUM_REQ_DisplayData, 0};
      if (!writeBaumPacket(brl, request, sizeof(request))) break;
    }

    /* enqueue a request to get the initial key states */
    {
      static const unsigned char request[] = {BAUM_REQ_GetKeys};
      if (!writeBaumPacket(brl, request, sizeof(request))) break;
    }

    /* the modular models need to be probed with a general call */
    if (!writeBaumModuleRegistrationCommand(brl, 0, 0, BAUM_MRC_Query)) break;

    while (awaitBrailleInput(brl, probeTimeout)) {
      BaumResponsePacket response;
      int size = getBaumPacket(brl, &response);

      if (size) {
        switch (response.data.code) {
          case BAUM_RSP_VersionNumber:
            continue;

          case BAUM_RSP_RoutingKeys: /* Inka */
            setInkaSwitches(brl, response.data.values.switches, 1);
            return 1;

          case BAUM_RSP_Switches: /* DM80P */
            setBaumSwitches(brl, response.data.values.switches, 1);
            return 1;

          case BAUM_RSP_CellCount: { /* newer models */
            unsigned char count = response.data.values.cellCount;

            if (isAcceptableCellCount(count)) {
              cellCount = count;
              return 1;
            }

            logUnexpectedCellCount(count);
            continue;
          }

          case BAUM_RSP_ModuleRegistration: /* modular models */
            if (!handleBaumModuleRegistrationEvent(brl, &response)) return 0;
            if (!baumDisplayModule.description) continue;
            baumDeviceType = BAUM_DEVICE_Modular;
            cellCount = getBaumModuleCellCount();
            return 1;

          case BAUM_RSP_DeviceIdentity: {
            /* should contain fallback cell count */
            int count = getIdentityCellCount(response.data.values.deviceIdentity,
                                             sizeof(response.data.values.deviceIdentity));
            if (count) identityCellCount = count;
            handleBaumDeviceIdentity(&response, 1);
            continue;
          }

          case BAUM_RSP_SerialNumber:
            logBaumSerialNumber(&response);
            continue;

          case BAUM_RSP_ErrorCode:
            if (response.data.values.errorCode != BAUM_ERR_PacketType) goto unexpectedPacket;
            logMessage(LOG_DEBUG, "unsupported request");
            continue;

          default:
          unexpectedPacket:
            logUnexpectedPacket(response.bytes, size);
            continue;
        }
      } else if (errno != EAGAIN) {
        break;
      }
    }
    if (errno != EAGAIN) break;

    if (identityCellCount) {
      /* Older models don't provide the actual cell count
       * so it must be derived from the identity string.
       */
      switch ((cellCount = identityCellCount)) {
        case 80: /* probably a Vario 80 */
          baumDeviceType = BAUM_DEVICE_Vario80;
          cellCount += 4;
          break;
      }

      return 1;
    }
  } while (++probes < probeLimit);

  return 0;
}

static void
processBaumPackets (BrailleDisplay *brl) {
  BaumResponsePacket packet;
  int size;

  while ((size = getBaumPacket(brl, &packet))) {
    switch (packet.data.code) {
      case BAUM_RSP_CellCount:
        if (!changeCellCount(brl, packet.data.values.cellCount)) return;
        continue;

      case BAUM_RSP_DeviceIdentity:
        handleBaumDeviceIdentity(&packet, 0);
        continue;

      case BAUM_RSP_SerialNumber:
        logBaumSerialNumber(&packet);
        continue;

      case BAUM_RSP_CommunicationChannel:
        continue;

      case BAUM_RSP_PowerdownSignal:
        if (!logBaumPowerdownReason(packet.data.values.powerdownReason)) continue;
        errno = ENODEV;
        return;

      case BAUM_RSP_DisplayKeys: {
        unsigned char keys;
        unsigned char UNUSED count = 6;

        switch (baumDeviceType) {
          case BAUM_DEVICE_Inka:
            keys = 0;
#define KEY(inka,baum) if (!(packet.data.values.displayKeys & (inka))) keys |= (baum)
            KEY(004, 001);
            KEY(002, 002);
            KEY(001, 004);
            KEY(040, 010);
            KEY(020, 020);
            KEY(010, 040);
#undef KEY
            break;

          case BAUM_DEVICE_DM80P:
            keys = packet.data.values.displayKeys ^ 0X7F;
            count = 7;
            break;

          case BAUM_DEVICE_Orbit:
            count = 8;
            /* fall through */

          default:
            keys = packet.data.values.displayKeys;
            break;
        }

        updateDisplayKeys(brl, keys);
        continue;
      }

      case BAUM_RSP_CommandKeys:
        updateNavigationKeys(brl, packet.data.values.commandKeys,
                             BM_KEY_COMMAND, BM_KEYS_COMMAND);
        continue;

      case BAUM_RSP_Front6:
        updateNavigationKeys(brl, packet.data.values.front6,
                             BM_KEY_FRONT, 6);
        continue;

      case BAUM_RSP_Back6:
        updateNavigationKeys(brl, packet.data.values.back6,
                             BM_KEY_BACK, 6);
        continue;

      case BAUM_RSP_Front10: {
        unsigned char keys[2];
        keys[0] = packet.data.values.front10[1];
        keys[1] = packet.data.values.front10[0];
        updateNavigationKeys(brl, keys, BM_KEY_FRONT, 10);
        continue;
      }

      case BAUM_RSP_Back10: {
        unsigned char keys[2];
        keys[0] = packet.data.values.back10[1];
        keys[1] = packet.data.values.back10[0];
        updateNavigationKeys(brl, keys, BM_KEY_BACK, 10);
        continue;
      }

      case BAUM_RSP_EntryKeys:
        updateEntryKeys(brl, packet.data.values.entryKeys);
        continue;

      case BAUM_RSP_Joystick:
        updateJoystick(brl, packet.data.values.joystick);
        continue;

      case BAUM_RSP_HorizontalSensor:
        resetKeyGroup(packet.data.values.horizontalSensors, brl->textColumns, packet.data.values.horizontalSensor);
      case BAUM_RSP_HorizontalSensors:
        if (!(switchSettings & BAUM_SWT_DisableSensors)) {
          updateKeyGroup(brl, keysState.horizontalSensors, packet.data.values.horizontalSensors,
                         BM_GRP_HorizontalSensors, 0, brl->textColumns, 0);
        }
        continue;

      case BAUM_RSP_VerticalSensor: {
        unsigned char left = packet.data.values.verticalSensor.left;
        unsigned char right;

        if (baumDeviceType != BAUM_DEVICE_Inka) {
          right = packet.data.values.verticalSensor.right;
        } else if (left & 0X40) {
          left -= 0X40;
          right = 0;
        } else {
          right = left;
          left = 0;
        }

        resetKeyGroup(packet.data.values.verticalSensors.left, VERTICAL_SENSOR_COUNT, left);
        resetKeyGroup(packet.data.values.verticalSensors.right, VERTICAL_SENSOR_COUNT, right);
      }

      case BAUM_RSP_VerticalSensors:
        if (!(switchSettings & BAUM_SWT_DisableSensors)) {
          int scaled = (switchSettings & BAUM_SWT_ScaledVertical) != 0;

          updateKeyGroup(brl, keysState.leftSensors, packet.data.values.verticalSensors.left,
                         (scaled? BM_GRP_ScaledLeftSensors: BM_GRP_LeftSensors),
                         0, VERTICAL_SENSOR_COUNT, scaled);
          updateKeyGroup(brl, keysState.rightSensors, packet.data.values.verticalSensors.right,
                         (scaled? BM_GRP_ScaledRightSensors: BM_GRP_RightSensors),
                         0, VERTICAL_SENSOR_COUNT, scaled);
        }
        continue;

      case BAUM_RSP_RoutingKey:
        resetKeyGroup(packet.data.values.routingKeys, cellCount, packet.data.values.routingKey);
        goto doRoutingKeys;

      case BAUM_RSP_RoutingKeys:
        if (baumDeviceType == BAUM_DEVICE_Inka) {
          setInkaSwitches(brl, packet.data.values.switches, 0);
          continue;
        }

      doRoutingKeys:
        updateRoutingKeys(brl, packet.data.values.routingKeys, cellCount);
        continue;

      case BAUM_RSP_Switches:
        setBaumSwitches(brl, packet.data.values.switches, 0);
        continue;

      case BAUM_RSP_ModuleRegistration:
        if (handleBaumModuleRegistrationEvent(brl, &packet)) {
        }

        if (!changeCellCount(brl, getBaumModuleCellCount())) return;
        continue;

      case BAUM_RSP_DataRegisters:
        handleBaumDataRegistersEvent(brl, &packet);
        continue;

      case BAUM_RSP_ErrorCode:
        if (packet.data.values.errorCode != BAUM_ERR_PacketType) goto unexpectedPacket;
        logMessage(LOG_DEBUG, "unsupported request");
        continue;

      case BAUM_RSP_NLS_ZMX_BD:
      case BAUM_RSP_NLS_ZMX_BE:
      case BAUM_RSP_NLS_ZMX_BF:
        continue;

      default:
      unexpectedPacket:
        logUnexpectedPacket(packet.bytes, size);
        continue;
    }
  }
}

static int
writeBaumCells (BrailleDisplay *brl) {
  const BaumDeviceOperations *bdo = &baumDeviceOperations[baumDeviceType];
  if (!bdo->writeAllCells) return 1;
  return bdo->writeAllCells(brl);
}

static int
writeBaumCellRange (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  const BaumDeviceOperations *bdo = &baumDeviceOperations[baumDeviceType];
  if (!bdo->writeCellRange) return 1;
  return bdo->writeCellRange(brl, start, count);
}

static const ProtocolOperations baumEscapeOperations = {
  .name = "Baum Escape",
  .dotsTable = &dotsTable_ISO11548_1,

  .serialBaud = 19200,
  .serialParity = SERIAL_PARITY_NONE,

  .readPacket = readBaumPacket,
  .writePacket = writeBaumPacket,

  .probeDevice = probeBaumDevice,
  .processPackets = processBaumPackets,

  .writeCells = writeBaumCells,
  .writeCellRange = writeBaumCellRange
};

/* HID Protocol */

typedef union {
  unsigned char bytes[0];

  struct {
    unsigned char type;

    union {
      unsigned char cellCount[16];
      unsigned char routingKeys[16];
      unsigned char displayKeys[16];
      unsigned char routingKey[16];
      unsigned char entryKeys[16];
      unsigned char joystick[16];
      char deviceIdentity[BAUM_LENGTH_DeviceIdentity];
      char serialNumber[BAUM_LENGTH_SerialNumber];
    } data;
  } PACKED fields;
} HidResponsePacket;

typedef struct {
  struct {
    const unsigned char *table;
    unsigned char count;
  } const packetLengths;
} HidPacketVerificationData;

static BraillePacketVerifierResult
verifyHidPacket (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  HidPacketVerificationData *pvd = data;
  unsigned char byte = bytes[size-1];

  if (size == 1) {
    if (byte < pvd->packetLengths.count) {
      unsigned char l = pvd->packetLengths.table[byte];

      if (l) {
        *length = l;
        return BRL_PVR_INCLUDE;
      }
    }

    if (!cellCount) return BRL_PVR_INVALID;

    switch (byte) {
      case BAUM_RSP_RoutingKeys:
        *length = brl->data->packetSize.routingKeys + 1;
        break;

      default:
        return BRL_PVR_INVALID;
    }
  } else {
    adjustPacketLength(bytes, size, length);
  }

  return BRL_PVR_INCLUDE;
}

static int
readHid1Packet (BrailleDisplay *brl, unsigned char *packet, int size) {
  static const unsigned char packetLengths[] = {
    [BAUM_RSP_CellCount]  = 2,
    [BAUM_RSP_DisplayKeys]  = 2,
    [BAUM_RSP_RoutingKey]  = 2,
    [BAUM_RSP_EntryKeys]  = 3,
    [BAUM_RSP_Joystick]  = 2,
    [BAUM_RSP_DeviceIdentity]  = 17,
    [BAUM_RSP_SerialNumber]  = 9,
  };

  HidPacketVerificationData pvd = {
    .packetLengths = {
      .table = packetLengths,
      .count = ARRAY_COUNT(packetLengths)
    }
  };

  memset(packet, 0, size);
  return readBraillePacket(brl, NULL, packet, size, verifyHidPacket, &pvd);
}

static int
readHid2Packet (BrailleDisplay *brl, unsigned char *packet, int size) {
  static const unsigned char packetLengths[] = {
    [BAUM_RSP_CellCount]  = 17,
    [BAUM_RSP_DisplayKeys]  = 17,
    [BAUM_RSP_RoutingKey]  = 17,
    [BAUM_RSP_EntryKeys]  = 17,
    [BAUM_RSP_Joystick]  = 17,
    [BAUM_RSP_DeviceIdentity]  = 17,
    [BAUM_RSP_SerialNumber]  = 17,
  };

  HidPacketVerificationData pvd = {
    .packetLengths = {
      .table = packetLengths,
      .count = ARRAY_COUNT(packetLengths)
    }
  };

  memset(packet, 0, size);
  return readBraillePacket(brl, NULL, packet, size, verifyHidPacket, &pvd);
}

static int
getHidPacket (BrailleDisplay *brl, HidResponsePacket *packet) {
  return brl->data->protocol->readPacket(brl, packet->bytes, sizeof(*packet));
}

static int
writeHidPacket (BrailleDisplay *brl, const unsigned char *packet, int length) {
  return writeBraillePacket(brl, NULL, packet, length);
}

static void
handleHidDeviceIdentity (const HidResponsePacket *packet, int probing) {
  const char *identity = packet->fields.data.deviceIdentity;
  size_t size = sizeof(packet->fields.data.deviceIdentity);

  logTextField("Baum Device Identity", identity, size);
  if (probing) setBaumDeviceType(identity, size);
}

static void
logHidSerialNumber (const HidResponsePacket *packet) {
  logTextField("Baum Serial Number",
               packet->fields.data.serialNumber,
               sizeof(packet->fields.data.serialNumber));
}

static int
probeHidDevice (BrailleDisplay *brl) {
  static const unsigned char packet[] = {0X02, 0X00};

  if (writeBraillePacket(brl, NULL, packet, sizeof(packet))) {
    int haveCellCount = 0;
    int haveDeviceIdentity = 0;
    int identityCellCount = 0;

    baumDeviceType = BAUM_DEVICE_Default;
    cellCount = 0;

    while (awaitBrailleInput(brl, probeTimeout)) {
      HidResponsePacket packet;
      size_t size = getHidPacket(brl, &packet);
      if (!size) break;

      switch (packet.fields.type) {
        case BAUM_RSP_CellCount: {
          unsigned char count = packet.fields.data.cellCount[0];

          if (isAcceptableCellCount(count)) {
            cellCount = count;
            haveCellCount = 1;
          } else {
            logUnexpectedCellCount(count);
          }

          break;
        }

        case BAUM_RSP_DeviceIdentity: {
          int count = getIdentityCellCount(packet.fields.data.deviceIdentity,
                                           sizeof(packet.fields.data.deviceIdentity));
          if (count) identityCellCount = count;
          handleHidDeviceIdentity(&packet, 1);
          haveDeviceIdentity = 1;
          break;
        }

        case BAUM_RSP_SerialNumber:
          logHidSerialNumber(&packet);
          break;

        default:
          logUnexpectedPacket(packet.bytes, size);
          break;
      }

      if (haveCellCount && haveDeviceIdentity) return 1;
    }

    if (!cellCount && identityCellCount) {
      /* Older models don't provide the actual cell count
       * so it must be derived from the identity string.
       */
      cellCount = identityCellCount;
      return 1;
    }
  }

  return 0;
}

static void
processHidPackets (BrailleDisplay *brl) {
  HidResponsePacket packet;
  size_t size;

  while ((size = getHidPacket(brl, &packet))) {
    switch (packet.fields.type) {
      case BAUM_RSP_CellCount:
        if (!changeCellCount(brl, packet.fields.data.cellCount[0])) return;
        continue;

      case BAUM_RSP_RoutingKey:
        resetKeyGroup(packet.fields.data.routingKeys, cellCount, packet.fields.data.routingKey[0]);
      case BAUM_RSP_RoutingKeys:
        updateRoutingKeys(brl, packet.fields.data.routingKeys, cellCount);
        continue;

      case BAUM_RSP_DisplayKeys:
        updateDisplayKeys(brl, packet.fields.data.displayKeys[0]);
        continue;

      case BAUM_RSP_EntryKeys:
        updateEntryKeys(brl, packet.fields.data.entryKeys);
        continue;

      case BAUM_RSP_Joystick:
        updateJoystick(brl, packet.fields.data.joystick);
        continue;

      case BAUM_RSP_DeviceIdentity:
        handleHidDeviceIdentity(&packet, 0);
        continue;

      case BAUM_RSP_SerialNumber:
        logHidSerialNumber(&packet);
        continue;

      default:
        logUnexpectedPacket(packet.bytes, size);
        continue;
    }
  }
}

static int
writeHidCells (BrailleDisplay *brl) {
  unsigned char packet[1 + cellCount];
  unsigned char *byte = packet;

  *byte++ = BAUM_REQ_DisplayData;
  byte = mempcpy(byte, externalCells, cellCount);

  return writeHidPacket(brl, packet, byte-packet);
}

static int
writeHidCellRange (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  return 1;
}

static const ProtocolOperations baumHid1Operations = {
  .name = "Baum HID1",
  .dotsTable = &dotsTable_ISO11548_1,

  .readPacket = readHid1Packet,
  .writePacket = writeHidPacket,

  .probeDevice = probeHidDevice,
  .processPackets = processHidPackets,

  .writeCells = writeHidCells,
  .writeCellRange = writeHidCellRange
};

static const ProtocolOperations baumHid2Operations = {
  .name = "Baum HID2",
  .dotsTable = &dotsTable_ISO11548_1,

  .readPacket = readHid2Packet,
  .writePacket = writeHidPacket,

  .probeDevice = probeHidDevice,
  .processPackets = processHidPackets,

  .writeCells = writeHidCells,
  .writeCellRange = writeHidCellRange
};

/* HandyTech Protocol */

typedef enum {
  HT_REQ_WRITE = 0X01,
  HT_REQ_RESET = 0XFF
} HandyTechRequestCode;

typedef enum {
  HT_RSP_KEY_B1    = 0X03,
  HT_RSP_KEY_Up    = 0X04,
  HT_RSP_KEY_B2    = 0X07,
  HT_RSP_KEY_Dn    = 0X08,
  HT_RSP_KEY_B3    = 0X0B,
  HT_RSP_KEY_B4    = 0X0F,
  HT_RSP_KEY_CR1   = 0X20,
  HT_RSP_WRITE_ACK = 0X7E,
  HT_RSP_RELEASE   = 0X80,
  HT_RSP_IDENTITY  = 0XFE
} HandyTechResponseCode;
#define HT_IS_ROUTING_KEY(code) (((code) >= HT_RSP_KEY_CR1) && ((code) < (HT_RSP_KEY_CR1 + brl->textColumns)))

typedef union {
  unsigned char bytes[2];

  struct {
    unsigned char code;

    union {
      unsigned char identity;
    } PACKED values;
  } PACKED data;
} PACKED HandyTechResponsePacket;

typedef struct {
  const char *name;
  unsigned char identity;
  unsigned char textCount;
  unsigned char statusCount;
} HandyTechModelEntry;

static const HandyTechModelEntry handyTechModelTable[] = {
  { "Modular 80",
    0X88, 80, 4
  }
  ,
  { "Modular 40",
    0X89, 40, 4
  }
  ,
  {NULL}        
};
static const HandyTechModelEntry *ht;

static int
readHandyTechPacket (BrailleDisplay *brl, unsigned char *packet, int size) {
  int offset = 0;
  int length = 0;

  while (1) {
    unsigned char byte;

    if (!gioReadByte(brl->gioEndpoint, &byte, offset>0)) {
      if (offset > 0) logPartialPacket(packet, offset);
      return 0;
    }

    if (offset < size) {
      if (offset == 0) {
        switch (byte) {
          case HT_RSP_IDENTITY:
            length = 2;
            break;

          case HT_RSP_WRITE_ACK:
            length = 1;
            break;

          default: {
            unsigned char key = byte & ~HT_RSP_RELEASE;
            switch (key) {
              default:
                if (!HT_IS_ROUTING_KEY(key)) {
                  logUnknownPacket(byte);
                  continue;
                }

              case HT_RSP_KEY_Up:
              case HT_RSP_KEY_Dn:
              case HT_RSP_KEY_B1:
              case HT_RSP_KEY_B2:
              case HT_RSP_KEY_B3:
              case HT_RSP_KEY_B4:
                length = 1;
                break;
            }
            break;
          }
        }
      }

      packet[offset] = byte;
    } else {
      if (offset == size) logTruncatedPacket(packet, offset);
      logDiscardedByte(byte);
    }

    if (++offset == length) {
      if (offset > size) {
        offset = 0;
        length = 0;
        continue;
      }

      logInputPacket(packet, offset);
      return length;
    }
  }
}

static int
getHandyTechPacket (BrailleDisplay *brl, HandyTechResponsePacket *packet) {
  return readHandyTechPacket(brl, packet->bytes, sizeof(*packet));
}

static int
writeHandyTechPacket (BrailleDisplay *brl, const unsigned char *packet, int length) {
  return writeBraillePacket(brl, NULL, packet, length);
}

static const HandyTechModelEntry *
findHandyTechModel (unsigned char identity) {
  const HandyTechModelEntry *model;

  for (model=handyTechModelTable; model->name; ++model) {
    if (identity == model->identity) {
      logMessage(LOG_INFO, "Baum emulation: HandyTech Model: %02X -> %s", identity, model->name);
      return model;
    }
  }

  logMessage(LOG_WARNING, "Baum emulation: unknown HandyTech identity code: %02X", identity);
  return NULL;
}

static int
probeHandyTechDevice (BrailleDisplay *brl) {
  int probes = 0;
  static const unsigned char request[] = {HT_REQ_RESET};
  while (writeHandyTechPacket(brl, request, sizeof(request))) {
    while (awaitBrailleInput(brl, probeTimeout)) {
      HandyTechResponsePacket response;
      if (getHandyTechPacket(brl, &response)) {
        if (response.data.code == HT_RSP_IDENTITY) {
          if (!(ht = findHandyTechModel(response.data.values.identity))) return 0;
          cellCount = ht->textCount;
          return 1;
        }
      }
    }
    if (errno != EAGAIN) break;
    if (++probes == probeLimit) break;
  }

  return 0;
}

static void
processHandyTechPackets (BrailleDisplay *brl) {
  HandyTechResponsePacket packet;
  int size;

  while ((size = getHandyTechPacket(brl, &packet))) {
    unsigned char code = packet.data.code;

    switch (code) {
      case HT_RSP_IDENTITY: {
        const HandyTechModelEntry *model = findHandyTechModel(packet.data.values.identity);
        if (model && (model != ht)) {
          ht = model;
          if (!changeCellCount(brl, ht->textCount)) return;
        }
        continue;
      }

      case HT_RSP_WRITE_ACK:
        continue;
    }

    {
      unsigned char *set;
      KeyGroup group;
      unsigned char key = code & ~HT_RSP_RELEASE;
      int press = (code & HT_RSP_RELEASE) == 0;

      if (HT_IS_ROUTING_KEY(key)) {
        set = keysState.routingKeys;
        group = BM_GRP_RoutingKeys;
        key -= HT_RSP_KEY_CR1;
      } else {
        set = keysState.navigationKeys;
        group = BM_GRP_NavigationKeys;

        switch (key) {
#define KEY(ht,baum) case HT_RSP_KEY_##ht: key = BM_KEY_DISPLAY + baum; break
          KEY(Up, 0);
          KEY(B1, 1);
          KEY(Dn, 2);
          KEY(B2, 3);
          KEY(B3, 4);
          KEY(B4, 5);
#undef KEY

          default:
            logUnexpectedPacket(packet.bytes, size);
            continue;
        }
      }

      if (setGroupedKey(set, key, press)) enqueueKeyEvent(brl, group, key, press);
    }
  }
}

static int
writeHandyTechCells (BrailleDisplay *brl) {
  unsigned char packet[1 + ht->statusCount + ht->textCount];
  unsigned char *byte = packet;

  *byte++ = HT_REQ_WRITE;

  {
    int count = ht->statusCount;
    while (count-- > 0) *byte++ = 0;
  }

  byte = mempcpy(byte, externalCells, ht->textCount);

  return writeHandyTechPacket(brl, packet, byte-packet);
}

static int
writeHandyTechCellRange (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  return 1;
}

static const ProtocolOperations handyTechOperations = {
  .name = "HandyTech",
  .dotsTable = &dotsTable_ISO11548_1,

  .serialBaud = 19200,
  .serialParity = SERIAL_PARITY_ODD,

  .readPacket = readHandyTechPacket,
  .writePacket = writeHandyTechPacket,

  .probeDevice = probeHandyTechDevice,
  .processPackets = processHandyTechPackets,

  .writeCells = writeHandyTechCells,
  .writeCellRange = writeHandyTechCellRange
};

/* PowerBraille Protocol */

#define PB_BUTTONS0_MARKER    0X60
#define PB1_BUTTONS0_Display6 0X08
#define PB1_BUTTONS0_Display5 0X04
#define PB1_BUTTONS0_Display4 0X02
#define PB1_BUTTONS0_Display2 0X01
#define PB2_BUTTONS0_Display3 0X08
#define PB2_BUTTONS0_Display5 0X04
#define PB2_BUTTONS0_Display1 0X02
#define PB2_BUTTONS0_Display2 0X01

#define PB_BUTTONS1_MARKER    0XE0
#define PB1_BUTTONS1_Display3 0X08
#define PB1_BUTTONS1_Display1 0X02
#define PB2_BUTTONS1_Display6 0X08
#define PB2_BUTTONS1_Display4 0X02

typedef enum {
  PB_REQ_WRITE = 0X04,
  PB_REQ_RESET = 0X0A
} PowerBrailleRequestCode;

typedef enum {
  PB_RSP_IDENTITY = 0X05,
  PB_RSP_SENSORS  = 0X08
} PowerBrailleResponseCode;

typedef union {
  unsigned char bytes[11];

  unsigned char buttons[2];

  struct {
    unsigned char zero;
    unsigned char code;

    union {
      struct {
        unsigned char cells;
        unsigned char dots;
        unsigned char version[4];
        unsigned char checksum[4];
      } PACKED identity;

      struct {
        unsigned char count;
        unsigned char vertical[4];
        unsigned char horizontal[10];
      } PACKED sensors;
    } PACKED values;
  } PACKED data;
} PACKED PowerBrailleResponsePacket;

static int
readPowerBraillePacket (BrailleDisplay *brl, unsigned char *packet, int size) {
  int offset = 0;
  int length = 0;

  while (1) {
    unsigned char byte;

    if (!gioReadByte(brl->gioEndpoint, &byte, offset>0)) {
      if (offset > 0) logPartialPacket(packet, offset);
      return 0;
    }
  haveByte:

    if (offset == 0) {
      if (!byte) {
        length = 2;
      } else if ((byte & PB_BUTTONS0_MARKER) == PB_BUTTONS0_MARKER) {
        length = 2;
      } else {
        logIgnoredByte(byte);
        continue;
      }
    } else if (packet[0]) {
      if ((byte & PB_BUTTONS1_MARKER) != PB_BUTTONS1_MARKER) {
        logShortPacket(packet, offset);
        offset = 0;
        length = 0;
        goto haveByte;
      }
    } else {
      if (offset == 1) {
        switch (byte) {
          case PB_RSP_IDENTITY:
            length = 12;
            break;

          case PB_RSP_SENSORS:
            length = 3;
            break;

          default:
            logUnknownPacket(byte);
            offset = 0;
            length = 0;
            continue;
        }
      } else if ((offset == 2) && (packet[1] == PB_RSP_SENSORS)) {
        length += byte;
      }
    }

    if (offset < length) {
      packet[offset] = byte;
    } else {
      if (offset == size) logTruncatedPacket(packet, offset);
      logDiscardedByte(byte);
    }

    if (++offset == length) {
      if (offset > size) {
        offset = 0;
        length = 0;
        continue;
      }

      logInputPacket(packet, offset);
      return length;
    }
  }
}

static int
getPowerBraillePacket (BrailleDisplay *brl, PowerBrailleResponsePacket *packet) {
  return readPowerBraillePacket(brl, packet->bytes, sizeof(*packet));
}

static int
writePowerBraillePacket (BrailleDisplay *brl, const unsigned char *packet, int length) {
  unsigned char buffer[2 + length];
  unsigned char *byte = buffer;

  *byte++ = 0XFF;
  *byte++ = 0XFF;
  byte = mempcpy(byte, packet, length);

  return writeBraillePacket(brl, NULL, buffer, (byte - buffer));
}

static int
probePowerBrailleDevice (BrailleDisplay *brl) {
  int probes = 0;
  static const unsigned char request[] = {PB_REQ_RESET};
  while (writePowerBraillePacket(brl, request, sizeof(request))) {
    while (awaitBrailleInput(brl, probeTimeout)) {
      PowerBrailleResponsePacket response;
      if (getPowerBraillePacket(brl, &response)) {
        if (response.data.code == PB_RSP_IDENTITY) {
          const unsigned char *version = response.data.values.identity.version;
          logMessage(LOG_INFO, "Baum emulation: PowerBraille Version: %c%c%c%c",
                     version[0], version[1], version[2], version[3]);
          cellCount = response.data.values.identity.cells;
          return 1;
        }
      }
    }
    if (errno != EAGAIN) break;
    if (++probes == probeLimit) break;
  }

  return 0;
}

static void
processPowerBraillePackets (BrailleDisplay *brl) {
  PowerBrailleResponsePacket packet;
  int size;

  while ((size = getPowerBraillePacket(brl, &packet))) {
    if (!packet.data.zero) {
      switch (packet.data.code) {
        case PB_RSP_IDENTITY:
          if (!changeCellCount(brl, packet.data.values.identity.cells)) return;
          continue;

        case PB_RSP_SENSORS:
          updateKeyGroup(brl, keysState.routingKeys, packet.data.values.sensors.horizontal,
                         BM_GRP_RoutingKeys, 0, brl->textColumns, 0);
          continue;

        default:
          break;
      }
    } else {
      unsigned char keys = 0;

#define KEY(key,index) if (packet.buttons[index] & PB2_BUTTONS##index##_Display##key) keys |= 1 << (key - 1)
      KEY(1, 0);
      KEY(2, 0);
      KEY(3, 0);
      KEY(4, 1);
      KEY(5, 0);
      KEY(6, 1);
#undef KEY

      /*
       * The PB emulation is deficient as the protocol doesn't report any
       * key status when all keys are released.  The ability to act on
       * released keys as needed for multiple key combinations is,
       * therefore, an unsolvable problem.  The TSI driver works around
       * this limitation by guessing the "key held" state based on the fact
       * that native Navigator/PowerBraille displays send repeated key
       * status for as long as there is at least one key pressed. Baum's PB
       * emulation, however, doesn't do this.
       *
       * Let's treat each packet as a discrete set of press/release events.
       * The limited set of single key bindings will work just fine.
       * Multi-key combinations won't work very well at all, though,
       * because it's unlikely that the user will be able to press and/or
       * release all of the keys quickly enough, and because releasing one
       * key before the others will generate press events for the rest.
       *
       * This is far from perfect, but that's the best we can do. The PB
       * emulation modes (either PB1 or PB2) should simply be avoided
       * whenever possible, and BAUM or HT should be used instead.
       */

      {
        const KeyGroup group = BM_GRP_NavigationKeys;
        KeyNumber pressedKeys[BM_KEYS_DISPLAY];
        unsigned char pressedCount = 0;
        unsigned char offset;

        for (offset=0; offset<BM_KEYS_DISPLAY; offset+=1) {
          if (keys & (1 << offset)) {
            KeyNumber number = BM_KEY_DISPLAY + offset;

            enqueueKeyEvent(brl, group, number, 1);
            pressedKeys[pressedCount++] = number;
          }
        }

        while (pressedCount) enqueueKeyEvent(brl, group, pressedKeys[--pressedCount], 0);
      }

      continue;
    }

    logUnexpectedPacket(packet.bytes, size);
  }
}

static int
writePowerBrailleCells (BrailleDisplay *brl) {
  unsigned char packet[6 + (brl->textColumns * 2)];
  unsigned char *byte = packet;

  *byte++ = PB_REQ_WRITE;
  *byte++ = 0; /* cursor mode: disabled */
  *byte++ = 0; /* cursor position: nowhere */
  *byte++ = 1; /* cursor type: command */
  *byte++ = brl->textColumns * 2; /* attribute-data pairs */
  *byte++ = 0; /* start */

  {
    int i;
    for (i=0; i<brl->textColumns; ++i) {
      *byte++ = 0; /* attributes */
      *byte++ = externalCells[i]; /* data */
    }
  }

  return writePowerBraillePacket(brl, packet, byte-packet);
}

static int
writePowerBrailleCellRange (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  return 1;
}

static const ProtocolOperations powerBrailleOperations = {
  .name = "PowerBraille",
  .dotsTable = &dotsTable_ISO11548_1,

  .serialBaud = 9600,
  .serialParity = SERIAL_PARITY_NONE,

  .readPacket = readPowerBraillePacket,
  .writePacket = writePowerBraillePacket,

  .probeDevice = probePowerBrailleDevice,
  .processPackets = processPowerBraillePackets,

  .writeCells = writePowerBrailleCells,
  .writeCellRange = writePowerBrailleCellRange
};

/* Driver Handlers */

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS
  };

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* Vario 40 (40 cells) */
      .vendor=0X0403, .product=0XFE70,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* PocketVario (24 cells) */
      .vendor=0X0403, .product=0XFE71,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* SuperVario 40 (40 cells) */
      .vendor=0X0403, .product=0XFE72,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* SuperVario 32 (32 cells) */
      .vendor=0X0403, .product=0XFE73,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* SuperVario 64 (64 cells) */
      .vendor=0X0403, .product=0XFE74,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* SuperVario 80 (80 cells) */
      .vendor=0X0403, .product=0XFE75,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* VarioPro 80 (80 cells) */
      .vendor=0X0403, .product=0XFE76,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* VarioPro 64 (64 cells) */
      .vendor=0X0403, .product=0XFE77,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* Orbit Reader 20 (20 cells) */
      .vendor=0X0483, .product=0XA1D3,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&baumHid1Operations,
    },

    { /* Orbit Reader 40 (40 cells) */
      .vendor=0X0483, .product=0Xa366,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&baumHid1Operations,
    },

    { /* VarioPro 40 (40 cells) */
      .vendor=0X0904, .product=0X2000,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* EcoVario 24 (24 cells) */
      .vendor=0X0904, .product=0X2001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* EcoVario 40 (40 cells) */
      .vendor=0X0904, .product=0X2002,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* VarioConnect 40 (40 cells) */
      .vendor=0X0904, .product=0X2007,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* VarioConnect 32 (32 cells) */
      .vendor=0X0904, .product=0X2008,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* VarioConnect 24 (24 cells) */
      .vendor=0X0904, .product=0X2009,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* VarioConnect 64 (64 cells) */
      .vendor=0X0904, .product=0X2010,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* VarioConnect 80 (80 cells) */
      .vendor=0X0904, .product=0X2011,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* EcoVario 32 (32 cells) */
      .vendor=0X0904, .product=0X2014,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* EcoVario 64 (64 cells) */
      .vendor=0X0904, .product=0X2015,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* EcoVario 80 (80 cells) */
      .vendor=0X0904, .product=0X2016,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* Refreshabraille 18 (18 cells) */
      .vendor=0X0904, .product=0X3000,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&baumEscapeOperations
    },

    { /* Orbit in Refreshabraille Emulation Mode (18 cells) */
      .vendor=0X0904, .product=0X3001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .verifyInterface=1,
      .data=&baumHid1Operations
    },

    { /* Refreshabraille 18 (18 cells) */
      .vendor=0X0904, .product=0X3001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface=1,
      .data=&baumHid1Operations
    },

    { /* Pronto! V3 18 (18 cells) */
      .vendor=0X0904, .product=0X4004,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* Pronto! V3 40 (40 cells) */
      .vendor=0X0904, .product=0X4005,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* Pronto! V4 18 (18 cells) */
      .vendor=0X0904, .product=0X4007,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid2Operations
    },

    { /* Pronto! V4 40 (40 cells) */
      .vendor=0X0904, .product=0X4008,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid2Operations
    },

    { /* SuperVario2 40 (40 cells) */
      .vendor=0X0904, .product=0X6001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* PocketVario2 (24 cells) */
      .vendor=0X0904, .product=0X6002,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* SuperVario2 32 (32 cells) */
      .vendor=0X0904, .product=0X6003,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* SuperVario2 64 (64 cells) */
      .vendor=0X0904, .product=0X6004,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* SuperVario2 80 (80 cells) */
      .vendor=0X0904, .product=0X6005,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* Brailliant2 40 (40 cells) */
      .vendor=0X0904, .product=0X6006,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* Brailliant2 24 (24 cells) */
      .vendor=0X0904, .product=0X6007,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* Brailliant2 32 (32 cells) */
      .vendor=0X0904, .product=0X6008,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* Brailliant2 64 (64 cells) */
      .vendor=0X0904, .product=0X6009,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* Brailliant2 80 (80 cells) */
      .vendor=0X0904, .product=0X600A,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* VarioConnect 24 (24 cells) */
      .vendor=0X0904, .product=0X6011,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* VarioConnect 32 (32 cells) */
      .vendor=0X0904, .product=0X6012,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* VarioConnect 40 (40 cells) */
      .vendor=0X0904, .product=0X6013,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid1Operations
    },

    { /* VarioUltra 20 (20 cells) */
      .vendor=0X0904, .product=0X6101,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid2Operations
    },

    { /* VarioUltra 40 (40 cells) */
      .vendor=0X0904, .product=0X6102,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid2Operations
    },

    { /* VarioUltra 32 (32 cells) */
      .vendor=0X0904, .product=0X6103,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&baumHid2Operations
    },

    { /* NLS eReader Zoomax (20 cells) */
      .vendor=0X1A86, .product=0X7523,
      .parentVendor=0X1A40, .parentProduct=0X0101,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=2, .outputEndpoint=2,
      .data=&baumEscapeOperations
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;
  descriptor.serial.options.applicationData = &baumEscapeOperations;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.options.ignoreWriteTimeouts = 1;

  descriptor.bluetooth.channelNumber = 1;
  descriptor.bluetooth.discoverChannel = 1;
  descriptor.bluetooth.options.applicationData = &baumEscapeOperations;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  const ProtocolOperations *requestedProtocol = NULL;
  unsigned int useVarioKeys = 0;

  {
    static const ProtocolOperations *const values[] = {
      NULL,
      &baumEscapeOperations,
      &baumHid1Operations,
      &baumHid2Operations,
      &handyTechOperations,
      &powerBrailleOperations
    };

    static const char *choices[] = {"default", "escape", "hid1", "hid2", "ht","pb", NULL};
    unsigned int index = 0;

    if (validateChoice(&index, parameters[PARM_PROTOCOL], choices)) {
      requestedProtocol = values[index];
    } else {
      logMessage(LOG_WARNING, "%s: %s", "invalid protocol setting", parameters[PARM_PROTOCOL]);
    }
  }

  if (!validateYesNo(&useVarioKeys, parameters[PARM_VARIOKEYS])) {
    logMessage(LOG_WARNING, "%s: %s", "invalid vario keys setting", parameters[PARM_VARIOKEYS]);
  }

  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      unsigned int attempts = 0;

      while (1) {
        brl->data->protocol = requestedProtocol;
        if (!brl->data->protocol) brl->data->protocol = gioGetApplicationData(brl->gioEndpoint);
        logMessage(LOG_DEBUG, "probing with %s protocol", brl->data->protocol->name);

        if (brl->data->protocol->serialBaud) {
          const SerialParameters parameters = {
            SERIAL_DEFAULT_PARAMETERS,
            .baud = brl->data->protocol->serialBaud,
            .parity = brl->data->protocol->serialParity
          };

          if (!gioReconfigureResource(brl->gioEndpoint, &parameters)) goto failed;
        }

        if (!gioDiscardInput(brl->gioEndpoint)) goto failed;

        memset(&keysState, 0, sizeof(keysState));
        switchSettings = 0;

        if (brl->data->protocol->probeDevice(brl)) {
          logCellCount(brl);

          {
            unsigned char *size = &brl->data->packetSize.routingKeys;

            *size = KEY_GROUP_SIZE(cellCount);
            if ((*size > 2) && (*size < 5)) *size = 5;
          }

          if ((baumDeviceType == BAUM_DEVICE_VarioConnect) && (cellCount == 12)) {
            baumDeviceType = BAUM_DEVICE_Conny;
          }

          makeOutputTable(brl->data->protocol->dotsTable[0]);
          if (!clearCellRange(brl, 0, cellCount)) goto failed;
          if (!updateCells(brl)) goto failed;

          {
            const KeyTableDefinition *ktd;

            if (useVarioKeys) {
              ktd = &KEY_TABLE_DEFINITION(vk);
            } else {
              ktd = baumDeviceOperations[baumDeviceType].keyTableDefinition;
            }

            setBrailleKeyTable(brl, ktd);
          }

          return 1;
        }

        if (++attempts == 2) break;
        asyncWait(700);
      }

    failed:
      disconnectBrailleResource(brl, NULL);
    }

    free(brl->data);
  } else {
    logMallocError();
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  disconnectBrailleResource(brl, NULL);
  free(brl->data);
}

static ssize_t
brl_readPacket (BrailleDisplay *brl, void *buffer, size_t size) {
  int count = brl->data->protocol->readPacket(brl, buffer, size);
  if (!count) count = -1;
  return count;
}

static ssize_t
brl_writePacket (BrailleDisplay *brl, const void *packet, size_t length) {
  return brl->data->protocol->writePacket(brl, packet, length)? length: -1;
}

static int
brl_reset (BrailleDisplay *brl) {
  return 0;
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (!putCells(brl, brl->buffer, 0, brl->textColumns)) return 0;
  return updateCells(brl);
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *status) {
  return putCells(brl, status, brl->textColumns, brl->statusColumns);
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  brl->data->protocol->processPackets(brl);
  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
