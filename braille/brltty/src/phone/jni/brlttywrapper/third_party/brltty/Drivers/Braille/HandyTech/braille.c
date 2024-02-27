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
#include <time.h>
#include <errno.h>

#include "log.h"
#include "parameters.h"
#include "bitfield.h"
#include "parse.h"
#include "timing.h"
#include "async_wait.h"
#include "ascii.h"

typedef enum {
  PARM_SETTIME
} DriverParameter;
#define BRLPARMS "settime"

#define BRLSTAT ST_AlvaStyle
#define BRL_HAVE_STATUS_CELLS
#define BRL_HAVE_PACKET_IO
#include "brl_driver.h"
#include "brldefs-ht.h"

BEGIN_KEY_NAME_TABLE(routing)
  KEY_GROUP_ENTRY(HT_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(dots)
  KEY_NAME_ENTRY(HT_KEY_B1, "B1"),
  KEY_NAME_ENTRY(HT_KEY_B2, "B2"),
  KEY_NAME_ENTRY(HT_KEY_B3, "B3"),
  KEY_NAME_ENTRY(HT_KEY_B4, "B4"),

  KEY_NAME_ENTRY(HT_KEY_B5, "B5"),
  KEY_NAME_ENTRY(HT_KEY_B6, "B6"),
  KEY_NAME_ENTRY(HT_KEY_B7, "B7"),
  KEY_NAME_ENTRY(HT_KEY_B8, "B8"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(keypad)
  KEY_NAME_ENTRY(HT_KEY_B12, "B12"),
  KEY_NAME_ENTRY(HT_KEY_Zero, "Zero"),
  KEY_NAME_ENTRY(HT_KEY_B13, "B13"),
  KEY_NAME_ENTRY(HT_KEY_B14, "B14"),

  KEY_NAME_ENTRY(HT_KEY_B11, "B11"),
  KEY_NAME_ENTRY(HT_KEY_One, "One"),
  KEY_NAME_ENTRY(HT_KEY_Two, "Two"),
  KEY_NAME_ENTRY(HT_KEY_Three, "Three"),

  KEY_NAME_ENTRY(HT_KEY_B10, "B10"),
  KEY_NAME_ENTRY(HT_KEY_Four, "Four"),
  KEY_NAME_ENTRY(HT_KEY_Five, "Five"),
  KEY_NAME_ENTRY(HT_KEY_Six, "Six"),

  KEY_NAME_ENTRY(HT_KEY_B9, "B9"),
  KEY_NAME_ENTRY(HT_KEY_Seven, "Seven"),
  KEY_NAME_ENTRY(HT_KEY_Eight, "Eight"),
  KEY_NAME_ENTRY(HT_KEY_Nine, "Nine"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(rockers)
  KEY_NAME_ENTRY(HT_KEY_Escape, "LeftRockerTop"),
  KEY_NAME_ENTRY(HT_KEY_Return, "LeftRockerBottom"),

  KEY_NAME_ENTRY(HT_KEY_Up, "RightRockerTop"),
  KEY_NAME_ENTRY(HT_KEY_Down, "RightRockerBottom"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(navigation)
  KEY_NAME_ENTRY(HT_KEY_Escape, "Display1"),
  KEY_NAME_ENTRY(HT_KEY_LeftCenter, "Display2"),
  KEY_NAME_ENTRY(HT_KEY_Return, "Display3"),

  KEY_NAME_ENTRY(HT_KEY_Up, "Display4"),
  KEY_NAME_ENTRY(HT_KEY_RightCenter, "Display5"),
  KEY_NAME_ENTRY(HT_KEY_Down, "Display6"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(joystick)
  KEY_NAME_ENTRY(HT_KEY_JoystickLeft, "Left"),
  KEY_NAME_ENTRY(HT_KEY_JoystickRight, "Right"),
  KEY_NAME_ENTRY(HT_KEY_JoystickUp, "Up"),
  KEY_NAME_ENTRY(HT_KEY_JoystickDown, "Down"),
  KEY_NAME_ENTRY(HT_KEY_JoystickAction, "Action"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(modular)
  KEY_NAME_ENTRY(HT_KEY_Up, "Left"),
  KEY_NAME_ENTRY(HT_KEY_Down, "Right"),

  KEY_NAME_ENTRY(HT_KEY_STATUS+0, "Status1"),
  KEY_NAME_ENTRY(HT_KEY_STATUS+1, "Status2"),
  KEY_NAME_ENTRY(HT_KEY_STATUS+2, "Status3"),
  KEY_NAME_ENTRY(HT_KEY_STATUS+3, "Status4"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(mdlr)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(keypad),
  KEY_NAME_TABLE(modular),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLE(modularEvolution)
  KEY_NAME_ENTRY(HT_KEY_Space, "Left"),
  KEY_NAME_ENTRY(HT_KEY_SpaceRight, "Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(me64)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(modularEvolution),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(me88)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(keypad),
  KEY_NAME_TABLE(modularEvolution),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(mc88)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(keypad),
  KEY_NAME_TABLE(modularEvolution),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLE(brailleStar)
  KEY_NAME_ENTRY(HT_KEY_Space, "SpaceLeft"),
  KEY_NAME_ENTRY(HT_KEY_SpaceRight, "SpaceRight"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(bs40)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bs80)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(keypad),
  KEY_NAME_TABLE(brailleStar),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(brln)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(as40)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(ab)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(ab_s)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
  KEY_NAME_TABLE(joystick),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(cb40)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLE(brailleWave)
  KEY_NAME_ENTRY(HT_KEY_Up, "Left"),
  KEY_NAME_ENTRY(HT_KEY_Down, "Right"),

  KEY_NAME_ENTRY(HT_KEY_Escape, "Escape"),
  KEY_NAME_ENTRY(HT_KEY_Space, "Space"),
  KEY_NAME_ENTRY(HT_KEY_Return, "Return"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(wave)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(brailleWave),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLE(easyBraille)
  KEY_NAME_ENTRY(HT_KEY_Up, "Left"),
  KEY_NAME_ENTRY(HT_KEY_Down, "Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(easy)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(easyBraille),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLE(basicBraille)
  KEY_NAME_ENTRY(HT_KEY_B2, "Display3"),
  KEY_NAME_ENTRY(HT_KEY_B3, "Display2"),
  KEY_NAME_ENTRY(HT_KEY_B4, "Display1"),
  KEY_NAME_ENTRY(HT_KEY_B5, "Display4"),
  KEY_NAME_ENTRY(HT_KEY_B6, "Display5"),
  KEY_NAME_ENTRY(HT_KEY_B7, "Display6"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(bb)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(basicBraille),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bbp)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(alo)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(rockers),
  KEY_NAME_TABLE(brailleStar),
  KEY_NAME_TABLE(joystick),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(ac4)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(dots),
  KEY_NAME_TABLE(navigation),
  KEY_NAME_TABLE(brailleStar),
  KEY_NAME_TABLE(joystick),
END_KEY_NAME_TABLES

typedef enum {
  HT_BWK_Backward = 0X01,
  HT_BWK_Forward = 0X08,

  HT_BWK_Escape = 0X02,
  HT_BWK_Enter = 0X04
} HT_BookwormKey;

BEGIN_KEY_NAME_TABLE(bookworm)
  KEY_NAME_ENTRY(HT_BWK_Backward, "Backward"),
  KEY_NAME_ENTRY(HT_BWK_Forward, "Forward"),

  KEY_NAME_ENTRY(HT_BWK_Escape, "Escape"),
  KEY_NAME_ENTRY(HT_BWK_Enter, "Enter"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(bkwm)
  KEY_NAME_TABLE(bookworm),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(mdlr)
DEFINE_KEY_TABLE(me64)
DEFINE_KEY_TABLE(me88)
DEFINE_KEY_TABLE(mc88)
DEFINE_KEY_TABLE(bs40)
DEFINE_KEY_TABLE(bs80)
DEFINE_KEY_TABLE(brln)
DEFINE_KEY_TABLE(as40)
DEFINE_KEY_TABLE(ab)
DEFINE_KEY_TABLE(ab_s)
DEFINE_KEY_TABLE(cb40)
DEFINE_KEY_TABLE(wave)
DEFINE_KEY_TABLE(easy)
DEFINE_KEY_TABLE(bb)
DEFINE_KEY_TABLE(bbp)
DEFINE_KEY_TABLE(alo)
DEFINE_KEY_TABLE(ac4)
DEFINE_KEY_TABLE(bkwm)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(mdlr),
  &KEY_TABLE_DEFINITION(me64),
  &KEY_TABLE_DEFINITION(me88),
  &KEY_TABLE_DEFINITION(mc88),
  &KEY_TABLE_DEFINITION(bs40),
  &KEY_TABLE_DEFINITION(bs80),
  &KEY_TABLE_DEFINITION(brln),
  &KEY_TABLE_DEFINITION(as40),
  &KEY_TABLE_DEFINITION(ab),
  &KEY_TABLE_DEFINITION(ab_s),
  &KEY_TABLE_DEFINITION(cb40),
  &KEY_TABLE_DEFINITION(wave),
  &KEY_TABLE_DEFINITION(easy),
  &KEY_TABLE_DEFINITION(bb),
  &KEY_TABLE_DEFINITION(bbp),
  &KEY_TABLE_DEFINITION(alo),
  &KEY_TABLE_DEFINITION(ac4),
  &KEY_TABLE_DEFINITION(bkwm),
END_KEY_TABLE_LIST

static int
endSession_Bookworm (BrailleDisplay *brl) {
  static const unsigned char sessionEnd[] = {0X05, 0X07};
  return writeBrailleMessage(brl, NULL, 0, sessionEnd, sizeof(sessionEnd));
}

typedef int ByteInterpreter (BrailleDisplay *brl, unsigned char byte);
static ByteInterpreter interpretByte_key;
static ByteInterpreter interpretByte_Bookworm;

typedef int (CellWriter) (BrailleDisplay *brl);
static CellWriter writeCells_statusAndText;
static CellWriter writeCells_Bookworm;
static CellWriter writeCells_Evolution;

static SetBrailleFirmnessMethod setBrailleFirmness;

static SetTouchSensitivityMethod setTouchSensitivity_Evolution;
static SetTouchSensitivityMethod setTouchSensitivity_ActiveBraille;

typedef struct {
  const char *name;
  const KeyTableDefinition *keyTableDefinition;

  ByteInterpreter *interpretByte;
  CellWriter *writeCells;
  SetBrailleFirmnessMethod *setBrailleFirmness;
  SetTouchSensitivityMethod *setTouchSensitivity;

  BrailleSessionEnder *sessionEnder;

  HT_ModelIdentifier identifier:8;
  unsigned char textCells;
  unsigned char statusCells;

  unsigned hasATC:1; /* Active Tactile Control */
  unsigned hasTime:1;
} ModelEntry;

static const ModelEntry modelTable[] = {
  { .identifier = HT_MODEL_Modular20,
    .name = "Modular 20+4",
    .textCells = 20,
    .statusCells = 4,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(mdlr),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_statusAndText
  },

  { .identifier = HT_MODEL_Modular40,
    .name = "Modular 40+4",
    .textCells = 40,
    .statusCells = 4,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(mdlr),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_statusAndText
  },

  { .identifier = HT_MODEL_Modular80,
    .name = "Modular 80+4",
    .textCells = 80,
    .statusCells = 4,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(mdlr),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_statusAndText
  },

  { .identifier = HT_MODEL_ModularEvolution64,
    .name = "Modular Evolution 64",
    .textCells = 64,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(me64),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
    .setTouchSensitivity = setTouchSensitivity_Evolution,
    .hasATC = 1
  },

  { .identifier = HT_MODEL_ModularEvolution88,
    .name = "Modular Evolution 88",
    .textCells = 88,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(me88),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
    .setTouchSensitivity = setTouchSensitivity_Evolution,
    .hasATC = 1
  },

  { .identifier = HT_MODEL_BrailleWave,
    .name = "Braille Wave",
    .textCells = 40,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(wave),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_statusAndText
  },

  { .identifier = HT_MODEL_Bookworm,
    .name = "Bookworm",
    .textCells = 8,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(bkwm),
    .interpretByte = interpretByte_Bookworm,
    .writeCells = writeCells_Bookworm,
    .sessionEnder = endSession_Bookworm
  },

  { .identifier = HT_MODEL_Braillino,
    .name = "Braillino",
    .textCells = 20,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(brln),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_statusAndText
  },

  { .identifier = HT_MODEL_BrailleStar40,
    .name = "Braille Star 40",
    .textCells = 40,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(bs40),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_statusAndText
  },

  { .identifier = HT_MODEL_BrailleStar80,
    .name = "Braille Star 80",
    .textCells = 80,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(bs80),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_statusAndText
  },

  { .identifier = HT_MODEL_EasyBraille,
    .name = "Easy Braille",
    .textCells = 40,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(easy),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution
  },

  { .identifier = HT_MODEL_ActiveBraille,
    .name = "Active Braille",
    .textCells = 40,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(ab),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
    .setBrailleFirmness = setBrailleFirmness,
    .setTouchSensitivity = setTouchSensitivity_ActiveBraille,
    .hasATC = 1,
    .hasTime = 1
  },

#define HT_BASIC_BRAILLE(cells)                     \
  { .identifier = HT_MODEL_BasicBraille##cells,     \
    .name = "Basic Braille " STRINGIFY(cells),      \
    .textCells = cells,                             \
    .statusCells = 0,                               \
    .keyTableDefinition = &KEY_TABLE_DEFINITION(bb),\
    .interpretByte = interpretByte_key,             \
    .writeCells = writeCells_Evolution              \
  }
  HT_BASIC_BRAILLE(16),
  HT_BASIC_BRAILLE(20),
  HT_BASIC_BRAILLE(32),
  HT_BASIC_BRAILLE(40),
  HT_BASIC_BRAILLE(48),
  HT_BASIC_BRAILLE(64),
  HT_BASIC_BRAILLE(80),
  HT_BASIC_BRAILLE(160),
#undef HT_BASIC_BRAILLE

#define HT_BASIC_BRAILLE_PLUS(cells)                 \
  { .identifier = HT_MODEL_BasicBraillePlus##cells,  \
    .name = "Basic Braille Plus " STRINGIFY(cells),  \
    .textCells = cells,                              \
    .statusCells = 0,                                \
    .keyTableDefinition = &KEY_TABLE_DEFINITION(bbp),\
    .interpretByte = interpretByte_key,              \
    .writeCells = writeCells_Evolution               \
  }
  HT_BASIC_BRAILLE_PLUS(20),
  HT_BASIC_BRAILLE_PLUS(32),
  HT_BASIC_BRAILLE_PLUS(40),
  HT_BASIC_BRAILLE_PLUS(48),
  HT_BASIC_BRAILLE_PLUS(64),
  HT_BASIC_BRAILLE_PLUS(80),
  HT_BASIC_BRAILLE_PLUS(84),
#undef HT_BASIC_BRAILLE_PLUS

  { .identifier = HT_MODEL_Actilino,
    .name = "Actilino",
    .textCells = 16,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(alo),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
    .setBrailleFirmness = setBrailleFirmness,
    .setTouchSensitivity = setTouchSensitivity_ActiveBraille,
    .hasATC = 1,
    .hasTime = 1
  },

  { .identifier = HT_MODEL_Activator,
    .name = "Activator",
    .textCells = 40,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(ac4),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
    .setBrailleFirmness = setBrailleFirmness,
    .setTouchSensitivity = setTouchSensitivity_ActiveBraille,
    .hasATC = 1,
    .hasTime = 1
  },

  { .identifier = HT_MODEL_ActiveStar40,
    .name = "Active Star 40",
    .textCells = 40,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(as40),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
    .setBrailleFirmness = setBrailleFirmness,
    .setTouchSensitivity = setTouchSensitivity_ActiveBraille,
    .hasATC = 1,
    .hasTime = 1
  },

  { .identifier = HT_MODEL_ModularConnect88,
    .name = "Modular Connect 88",
    .textCells = 88,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(mc88),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
  },

  { .identifier = HT_MODEL_ConnectBraille40,
    .name = "Connect Braille 40",
    .textCells = 40,
    .statusCells = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(cb40),
    .interpretByte = interpretByte_key,
    .writeCells = writeCells_Evolution,
    .setBrailleFirmness = setBrailleFirmness,
    .hasTime = 1
  },

  { /* end of table */
    .name = NULL
  }
};

static const ModelEntry modelEntry_ab_s = {
  .identifier = HT_MODEL_ActiveBraille,
  .name = "Active Braille S",
  .textCells = 40,
  .statusCells = 0,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(ab_s),
  .interpretByte = interpretByte_key,
  .writeCells = writeCells_Evolution,
  .setBrailleFirmness = setBrailleFirmness,
  .setTouchSensitivity = setTouchSensitivity_ActiveBraille,
  .hasATC = 1,
  .hasTime = 1
};

#define MAXIMUM_TEXT_CELLS   160
#define MAXIMUM_STATUS_CELLS 4

typedef enum {
  BDS_OFF,
  BDS_READY
} BrailleDisplayState;

struct BrailleDataStruct {
  const ModelEntry *model;              /* points to terminal model config struct */

  unsigned char rawData[MAXIMUM_TEXT_CELLS];            /* translated data to send to Braille */
  unsigned char prevData[MAXIMUM_TEXT_CELLS];   /* previously sent raw data */

  unsigned char rawStatus[MAXIMUM_STATUS_CELLS];         /* to hold status info */
  unsigned char prevStatus[MAXIMUM_STATUS_CELLS];        /* to hold previous status */

  BrailleDisplayState currentState;
  TimePeriod statePeriod;

  unsigned int retryCount;
  unsigned char updateRequired;
};

/* USB IO */
#include "io_usb.h"
#include "usb_hid.h"

#define HT_HID_REPORT_TIMEOUT 100

typedef enum {
  HT_HID_RPT_OutData    = 0X01, /* receive data from device */
  HT_HID_RPT_InData     = 0X02, /* send data to device */
  HT_HID_RPT_InCommand  = 0XFB, /* run USB-HID firmware command */
  HT_HID_RPT_OutVersion = 0XFC, /* get version of USB-HID firmware */
  HT_HID_RPT_OutBaud    = 0XFD, /* get baud rate of serial connection */
  HT_HID_RPT_InBaud     = 0XFE, /* set baud rate of serial connection */
} HT_HidReportNumber;

typedef enum {
  HT_HID_CMD_FlushBuffers = 0X01, /* flush input and output buffers */
} HtHidCommand;

static size_t hidOutDataSize = 0;
static size_t hidInDataSize = 0;
static size_t hidInCommandSize = 0;
static size_t hidOutVersionSize = 0;
static size_t hidOutBaudSize = 0;
static size_t hidInBaudSize = 0;

static uint16_t hidFirmwareVersion;
static unsigned char *hidInputReport = NULL;
#define hidInputLength (hidInputReport[1])
#define hidInputBuffer (&hidInputReport[2])
static unsigned char hidInputOffset;

static ssize_t
getHidReport (
  UsbDevice *device, const UsbChannelDefinition *definition,
  unsigned char number, unsigned char *buffer, uint16_t size
) {
  ssize_t result = usbHidGetReport(device, definition->interface,
                                   number, buffer, size, HT_HID_REPORT_TIMEOUT);
  if (result > 0 && buffer[0] != number) {
    logMessage(LOG_WARNING, "unexpected HID report number: expected %02X, received %02X",
               number, buffer[0]);
    errno = EIO;
    result = -1;
  }

  return result;
}

static int
allocateHidInputBuffer (void) {
  if (hidOutDataSize) {
    if ((hidInputReport = malloc(hidOutDataSize))) {
      hidInputLength = 0;
      hidInputOffset = 0;
      return 1;
    } else {
      logMallocError();
    }
  }

  return 0;
}

static void
deallocateHidInputBuffer (void) {
  if (hidInputReport) {
    free(hidInputReport);
    hidInputReport = NULL;
  }
}

static int
getHidFirmwareVersion (BrailleDisplay *brl) {
  hidFirmwareVersion = 0;

  if (hidOutVersionSize) {
    unsigned char report[hidOutVersionSize];
    ssize_t result = gioGetHidReport(brl->gioEndpoint,
                                     HT_HID_RPT_OutVersion, report, sizeof(report));

    if (result > 0) {
      hidFirmwareVersion = (report[1] << 8) | report[2];
      logMessage(LOG_INFO, "USB-HID Firmware Version: %u.%u", report[1], report[2]);
      return 1;
    }
  }

  return 0;
}

static int
executeHidFirmwareCommand (BrailleDisplay *brl, HtHidCommand command) {
  if (hidInCommandSize) {
    unsigned char report[hidInCommandSize];

    report[0] = HT_HID_RPT_InCommand;
    report[1] = command;

    if (gioWriteHidReport(brl->gioEndpoint, report, sizeof(report)) != -1) {
      return 1;
    }
  }

  return 0;
}

typedef struct {
  int (*initializeSession) (BrailleDisplay *brl);
} GeneralOperations;

typedef struct {
  const GeneralOperations *general;
  GioUsbAwaitInputMethod *awaitInput;
  GioUsbReadDataMethod *readData;
  GioUsbWriteDataMethod *writeData;
  UsbInputFilter *inputFilter;
} UsbOperations;

static int
initializeUsbSession2 (BrailleDisplay *brl) {
  static const BrailleReportSizeEntry reportTable[] = {
    {.identifier=HT_HID_RPT_OutData, .input=&hidOutDataSize},
    {.identifier=HT_HID_RPT_InData, .output=&hidInDataSize},
    {.identifier=HT_HID_RPT_InCommand, .output=&hidInCommandSize},
    {.identifier=HT_HID_RPT_OutVersion, .input=&hidOutVersionSize},
    {.identifier=HT_HID_RPT_OutBaud, .input=&hidOutBaudSize},
    {.identifier=HT_HID_RPT_InBaud, .output=&hidInBaudSize},
    {.identifier=0}
  };

  if (getBrailleReportSizes(brl, reportTable)) {
    if (allocateHidInputBuffer()) {
      if (getHidFirmwareVersion(brl)) {
        if (executeHidFirmwareCommand(brl, HT_HID_CMD_FlushBuffers)) {
          return 1;
        }
      }

      deallocateHidInputBuffer();
    }
  }

  return 0;
}

static int
awaitUsbInput2 (
  UsbDevice *device, const UsbChannelDefinition *definition, int milliseconds
) {
  if (hidOutDataSize) {
    if (hidInputOffset < hidInputLength) return 1;

    TimePeriod period;
    startTimePeriod(&period, milliseconds);

    while (1) {
      ssize_t result = getHidReport(device, definition, HT_HID_RPT_OutData,
                                    hidInputReport, hidOutDataSize);

      if (result == -1) return 0;
      hidInputOffset = 0;
      if (hidInputLength > 0) return 1;

      if (afterTimePeriod(&period, NULL)) break;
      asyncWait(BRAILLE_DRIVER_INPUT_POLL_INTERVAL);
    }
  }

  errno = EAGAIN;
  return 0;
}

static ssize_t
readUsbData2 (
  UsbDevice *device, const UsbChannelDefinition *definition,
  void *data, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  unsigned char *buffer = data;
  int count = 0;

  while (count < size) {
    if (!awaitUsbInput2(device, definition,
                        count? subsequentTimeout: initialTimeout)) {
      if (errno != EAGAIN) count = -1;
      break;
    }

    {
      size_t amount = MIN(size-count, hidInputLength-hidInputOffset);

      memcpy(&buffer[count], &hidInputBuffer[hidInputOffset], amount);
      hidInputOffset += amount;
      count += amount;
    }
  }

  return count;
}

static ssize_t
writeUsbData2 (
  UsbDevice *device, const UsbChannelDefinition *definition,
  const void *data, size_t size, int timeout
) {
  const unsigned char *buffer = data;
  int index = 0;

  if (hidInDataSize) {
    while (size) {
      unsigned char report[hidInDataSize];
      unsigned char count = MIN(size, (sizeof(report) - 2));
      int result;

      report[0] = HT_HID_RPT_InData;
      report[1] = count;
      memcpy(report+2, &buffer[index], count);
      memset(&report[count+2], 0, sizeof(report)-count-2);

      result = usbHidSetReport(device, definition->interface,
                               report[0], report, sizeof(report),
                               HT_HID_REPORT_TIMEOUT);
      if (result == -1) return -1;

      index += count;
      size -= count;
    }
  }

  return index;
}

static const GeneralOperations generalOperations2 = {
  .initializeSession = initializeUsbSession2
};

static const UsbOperations usbOperations2 = {
  .general = &generalOperations2,
  .awaitInput = awaitUsbInput2,
  .readData = readUsbData2,
  .writeData = writeUsbData2
};

static int
initializeUsbSession3 (BrailleDisplay *brl) {
  static const BrailleReportSizeEntry reportTable[] = {
    {.identifier=HT_HID_RPT_OutData, .input=&hidOutDataSize},
    {.identifier=HT_HID_RPT_InData, .output=&hidInDataSize},
    {.identifier=0}
  };

  return getBrailleReportSizes(brl, reportTable);
}

static ssize_t
writeUsbData3 (
  UsbDevice *device, const UsbChannelDefinition *definition,
  const void *data, size_t size, int timeout
) {
  const unsigned char *buffer = data;
  int index = 0;

  if (hidInDataSize) {
    while (size) {
      unsigned char report[hidInDataSize];
      const unsigned char count = MIN(size, (sizeof(report) - 2));
      int result;

      report[0] = HT_HID_RPT_InData;
      report[1] = count;
      memset(mempcpy(report+2, &buffer[index], count), 0, sizeof(report)-count-2);

      result = usbWriteEndpoint(device, definition->outputEndpoint,
                                report, sizeof(report), 1000);
      if (result == -1) return -1;

      index += count;
      size -= count;
    }
  }

  return index;
}

static int
filterUsbInput3 (UsbInputFilterData *data) {
  unsigned char *buffer = data->buffer;

  if ((data->length >= 2) &&
      (data->length == hidOutDataSize) && 
      (buffer[0] == HT_HID_RPT_OutData) &&
      (buffer[1] <= (data->length - 2))) {
    data->length = buffer[1];
    memmove(data->buffer, data->buffer+2, data->length);
  }

  return 1;
}

static const GeneralOperations generalOperations3 = {
  .initializeSession = initializeUsbSession3
};

static const UsbOperations usbOperations3 = {
  .general = &generalOperations3,
  .writeData = writeUsbData3,
  .inputFilter = filterUsbInput3
};

static BraillePacketVerifierResult
verifyPacket (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      switch (byte) {
        default:
          *length = 1;
          break;

        case HT_PKT_OK:
          *length = 2;
          break;

        case HT_PKT_Extended:
          *length = 4;
          break;
      }
      break;

    case 3:
      if (bytes[0] == HT_PKT_Extended) *length += byte;
      break;

    case 5:
      if ((bytes[0] == HT_PKT_Extended) &&
          (bytes[1] == HT_MODEL_ActiveBraille) &&
          (bytes[2] == 2) &&
          (bytes[3] == HT_EXTPKT_Confirmation) &&
          (byte == 0X15))
        *length += 1;
      break;

    default:
      break;
  }

  if ((size == *length) && (bytes[0] == HT_PKT_Extended) && (byte != ASCII_SYN)) {
    return BRL_PVR_INVALID;
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readPacket (BrailleDisplay *brl, void *buffer, size_t size) {
  return readBraillePacket(brl, NULL, buffer, size, verifyPacket, NULL);
}

static ssize_t
brl_readPacket (BrailleDisplay *brl, void *buffer, size_t size) {
  const size_t length = readPacket(brl, buffer, size);

  if (length == 0 && errno != EAGAIN) return -1;
  return length;
}

static ssize_t
brl_writePacket (BrailleDisplay *brl, const void *packet, size_t length) {
  return writeBrailleMessage(brl, NULL, 0, packet, length)? length: -1;
}

static void
setState (BrailleDisplay *brl, BrailleDisplayState state) {
  if (state == brl->data->currentState) {
    ++brl->data->retryCount;
  } else {
    brl->data->retryCount = 0;
    brl->data->currentState = state;
  }

  startTimePeriod(&brl->data->statePeriod, 1000);
  // logMessage(LOG_DEBUG, "State: %d+%d", brl->data->currentState, brl->data->retryCount);
}

static int
brl_reset (BrailleDisplay *brl) {
  static const unsigned char packet[] = {HT_PKT_Reset};
  return writeBraillePacket(brl, NULL, packet, sizeof(packet));
}

static int
identifyModel (BrailleDisplay *brl, unsigned char identifier) {
  for (
    brl->data->model = modelTable;
    brl->data->model->name && (brl->data->model->identifier != identifier);
    brl->data->model++
  );

  if (!brl->data->model->name) {
    logMessage(LOG_ERR, "Detected unknown HandyTech model with ID %02X.",
               identifier);
    return 0;
  }

  if (brl->data->model->identifier == HT_MODEL_ActiveBraille) {
    GioEndpoint *endpoint = brl->gioEndpoint;
    char *serialNumber = NULL;

    switch (gioGetResourceType(endpoint)) {
      case GIO_TYPE_USB: {
        UsbChannel *channel = gioGetResourceObject(endpoint);
        serialNumber = usbGetSerialNumber(channel->device, 1000);
        break;
      }

      default: {
        serialNumber = gioGetResourceName(endpoint);
        break;
      }
    }

    if (serialNumber) {
      const char *slash = strchr(serialNumber, '/');

      if (slash) {
        if (slash[1] == 'S') brl->data->model = &modelEntry_ab_s;
      }

      free(serialNumber);
    }
  }

  logMessage(LOG_INFO, "Detected %s: %d data %s, %d status %s.",
             brl->data->model->name,
             brl->data->model->textCells, (brl->data->model->textCells == 1)? "cell": "cells",
             brl->data->model->statusCells, (brl->data->model->statusCells == 1)? "cell": "cells");

  brl->textColumns = brl->data->model->textCells;                       /* initialise size of display */
  brl->textRows = 1;
  brl->statusColumns = brl->data->model->statusCells;
  brl->statusRows = 1;

  setBrailleKeyTable(brl, brl->data->model->keyTableDefinition);
  brl->setBrailleFirmness = brl->data->model->setBrailleFirmness;
  brl->setTouchSensitivity = brl->data->model->setTouchSensitivity;

  memset(brl->data->rawStatus, 0, brl->data->model->statusCells);
  memset(brl->data->rawData, 0, brl->data->model->textCells);

  brl->data->retryCount = 0;
  brl->data->updateRequired = 0;
  brl->data->currentState = BDS_OFF;
  setState(brl, BDS_READY);

  return 1;
}

static int
writeExtendedPacket (
  BrailleDisplay *brl, HT_ExtendedPacketType type,
  const unsigned char *data, unsigned char size
) {
  HT_Packet packet;
  packet.fields.type = HT_PKT_Extended;
  packet.fields.data.extended.model = brl->data->model->identifier;
  packet.fields.data.extended.length = size + 1; /* type byte is included */
  packet.fields.data.extended.type = type;
  if (data) memcpy(packet.fields.data.extended.data.bytes, data, size);
  packet.fields.data.extended.data.bytes[size] = ASCII_SYN;
  size += 5; /* EXT, ID, LEN, TYPE, ..., SYN */
  return writeBrailleMessage(brl, NULL, type, &packet, size);
}

static int
setAtcMode (BrailleDisplay *brl, unsigned char value) {
  const unsigned char data[] = {value};
  return writeExtendedPacket(brl, HT_EXTPKT_SetAtcMode, data, sizeof(data));
}

static int
setBrailleFirmness (BrailleDisplay *brl, BrailleFirmness setting) {
  const unsigned char data[] = {setting * 2 / BRL_FIRMNESS_MAXIMUM};
  return writeExtendedPacket(brl, HT_EXTPKT_SetFirmness, data, sizeof(data));
}

static int
setTouchSensitivity_Evolution (BrailleDisplay *brl, TouchSensitivity setting) {
  const unsigned char data[] = {0XFF - (setting * 0XF0 / BRL_SENSITIVITY_MAXIMUM)};
  return writeExtendedPacket(brl, HT_EXTPKT_SetAtcSensitivity, data, sizeof(data));
}

static int
setTouchSensitivity_ActiveBraille (BrailleDisplay *brl, TouchSensitivity setting) {
  const unsigned char data[] = {setting * 6 / BRL_SENSITIVITY_MAXIMUM};
  return writeExtendedPacket(brl, HT_EXTPKT_SetAtcSensitivity2, data, sizeof(data));
}

typedef int (DateTimeProcessor) (BrailleDisplay *brl, const HT_DateTime *dateTime);
static DateTimeProcessor *dateTimeProcessor = NULL;

static int
requestDateTime (BrailleDisplay *brl, DateTimeProcessor *processor) {
  int result = writeExtendedPacket(brl, HT_EXTPKT_GetRTC, NULL, 0);

  if (result) {
    dateTimeProcessor = processor;
  }

  return result;
}

static int
logDateTime (BrailleDisplay *brl, const HT_DateTime *dateTime) {
  logMessage(LOG_INFO,
             "date and time of %s:"
             " %04" PRIu16 "-%02" PRIu8 "-%02" PRIu8
             " %02" PRIu8 ":%02" PRIu8 ":%02" PRIu8,
             brl->data->model->name,
             getBigEndian16(dateTime->year), dateTime->month, dateTime->day,
             dateTime->hour, dateTime->minute, dateTime->second);

  return 1;
}

static int
synchronizeDateTime (BrailleDisplay *brl, const HT_DateTime *dateTime) {
  long int delta;
  TimeValue hostTime;
  getCurrentTime(&hostTime);

  {
    TimeValue deviceTime;

    {
      TimeComponents components = {
        .year = getBigEndian16(dateTime->year),
        .month = dateTime->month - 1,
        .day = dateTime->day - 1,
        .hour = dateTime->hour,
        .minute = dateTime->minute,
        .second = dateTime->second
      };

      makeTimeValue(&deviceTime, &components);
    }

    delta = millisecondsBetween(&hostTime, &deviceTime);
    if (delta < 0) delta = -delta;
  }

  if (delta > 1000) {
    TimeComponents components;
    HT_DateTime payload;

    expandTimeValue(&hostTime, &components);
    putLittleEndian16(&payload.year, components.year);
    payload.month = components.month + 1;
    payload.day = components.day + 1;
    payload.hour = components.hour;
    payload.minute = components.minute;
    payload.second = components.second;

    logMessage(LOG_DEBUG, "Time difference between host and device: %ld.%03ld",
               (delta / MSECS_PER_SEC), (delta % MSECS_PER_SEC));

    if (writeExtendedPacket(brl, HT_EXTPKT_SetRTC,
                            (unsigned char *)&payload, sizeof(payload))) {
      return requestDateTime(brl, logDateTime);
    }
  }

  return 1;
}

static int
initializeSession (BrailleDisplay *brl) {
  const GeneralOperations *ops = gioGetApplicationData(brl->gioEndpoint);

  if (ops) {
    if (ops->initializeSession) {
      if (!ops->initializeSession(brl)) {
        return 0;
      }
    }
  }

  return 1;
}

static void
setUsbConnectionProperties (
  GioUsbConnectionProperties *properties,
  const UsbChannelDefinition *definition
) {
  if (definition->data) {
    const UsbOperations *usbOps = definition->data;

    properties->applicationData = usbOps->general;
    properties->writeData = usbOps->writeData;
    properties->readData = usbOps->readData;
    properties->awaitInput = usbOps->awaitInput;
    properties->inputFilter = usbOps->inputFilter;
  }
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 19200,
    .parity = SERIAL_PARITY_ODD
  };

  BEGIN_USB_STRING_LIST(usbManufacturers_0403_6001)
    "FTDI",
  END_USB_STRING_LIST

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* GoHubs chip */
      .vendor=0X0921, .product=0X1200,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .serial = &serialParameters
    },

    { /* FTDI chip */
      .vendor=0X0403, .product=0X6001,
      .manufacturers = usbManufacturers_0403_6001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .serial = &serialParameters
    },

    { /* Easy Braille (HID) */
      .vendor=0X1FE4, .product=0X0044,
      .configuration=1, .interface=0, .alternative=0,
      .data=&usbOperations2
    },

    { /* Braille Star 40 (HID) */
      .vendor=0X1FE4, .product=0X0074,
      .configuration=1, .interface=0, .alternative=0,
      .data=&usbOperations2
    },

    { /* USB-HID adapter */
      .vendor=0X1FE4, .product=0X0003,
      .configuration=1, .interface=0, .alternative=0,
      .data=&usbOperations2
    },

    { /* Active Braille */
      .vendor=0X1FE4, .product=0X0054,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 16 */
      .vendor=0X1FE4, .product=0X0081,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 20 */
      .vendor=0X1FE4, .product=0X0082,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 32 */
      .vendor=0X1FE4, .product=0X0083,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 40 */
      .vendor=0X1FE4, .product=0X0084,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 48 */
      .vendor=0X1FE4, .product=0X008A,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 64 */
      .vendor=0X1FE4, .product=0X0086,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 80 */
      .vendor=0X1FE4, .product=0X0087,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Basic Braille 160 */
      .vendor=0X1FE4, .product=0X008B,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Actilino */
      .vendor=0X1FE4, .product=0X0061,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Activator */
      .vendor=0X1FE4, .product=0X00A4,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Active Star 40 */
      .vendor=0X1FE4, .product=0X0064,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },

    { /* Connect Braille 40 */
      .vendor=0X1FE4, .product=0X0055,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .data=&usbOperations3
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.setConnectionProperties = setUsbConnectionProperties;
  descriptor.usb.options.inputTimeout = 100;
  descriptor.usb.options.requestTimeout = 100;

  descriptor.bluetooth.channelNumber = 1;
  descriptor.bluetooth.discoverChannel = 1;

  if (connectBrailleResource(brl, identifier, &descriptor, initializeSession)) {
    return 1;
  }

  return 0;
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const HT_Packet *response = packet;

  return (response->fields.type == HT_PKT_OK)? BRL_RSP_DONE: BRL_RSP_UNEXPECTED;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      unsigned int setTime = 0;
      HT_Packet response;

      if (*parameters[PARM_SETTIME]) {
        if (!validateYesNo(&setTime, parameters[PARM_SETTIME])) {
          logMessage(LOG_WARNING, "%s: %s", "invalid set time setting",
                     parameters[PARM_SETTIME]);
        }
      }

      setTime = !!setTime;

      if (probeBrailleDisplay(brl, 3, NULL, 100,
                              brl_reset,
                              readPacket, &response, sizeof(response),
                              isIdentityResponse)) {
        if (identifyModel(brl, response.fields.data.ok.model)) {
          makeOutputTable(dotsTable_ISO11548_1);

          if (brl->data->model->hasATC) {
            setAtcMode(brl, 1);
          }

          if (setTime) {
            if (brl->data->model->hasTime) {
              requestDateTime(brl, synchronizeDateTime);
            } else {
              logMessage(LOG_INFO, "%s does not support setting the clock",
                         brl->data->model->name);
            }
          }

          return 1;
        }
      }

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
  if (brl->data) {
    disconnectBrailleResource(brl, brl->data->model->sessionEnder);

    free(brl->data);
    brl->data = NULL;
  }

  deallocateHidInputBuffer();
}

static int
writeCells (BrailleDisplay *brl) {
  return brl->data->model->writeCells(brl);
}

static int
writeCells_statusAndText (BrailleDisplay *brl) {
  unsigned char buffer[1 + brl->data->model->statusCells + brl->data->model->textCells];
  unsigned char *byte = buffer;

  *byte++ = HT_PKT_Braille;
  byte = mempcpy(byte, brl->data->rawStatus, brl->data->model->statusCells);
  byte = mempcpy(byte, brl->data->rawData, brl->data->model->textCells);

  return writeBrailleMessage(brl, NULL, HT_PKT_Braille, buffer, byte-buffer);
}

static int
writeCells_Bookworm (BrailleDisplay *brl) {
  unsigned char buffer[1 + brl->data->model->statusCells + brl->data->model->textCells + 1];

  buffer[0] = 0X01;
  memcpy(buffer+1, brl->data->rawData, brl->data->model->textCells);
  buffer[sizeof(buffer)-1] = ASCII_SYN;
  return writeBrailleMessage(brl, NULL, 0X01, buffer, sizeof(buffer));
}

static int
writeCells_Evolution (BrailleDisplay *brl) {
  return writeExtendedPacket(brl, HT_EXTPKT_Braille,
                             brl->data->rawData, brl->data->model->textCells);
}

static int
updateCells (BrailleDisplay *brl) {
  if (!brl->data->updateRequired) return 1;
  if (brl->data->currentState != BDS_READY) return 1;

  if (!writeCells(brl)) return 0;
  brl->data->updateRequired = 0;
  return 1;
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  const size_t cellCount = brl->data->model->textCells;

  if (cellsHaveChanged(brl->data->prevData, brl->buffer, cellCount, NULL, NULL, NULL)) {
    translateOutputCells(brl->data->rawData, brl->data->prevData, cellCount);
    brl->data->updateRequired = 1;
  }

  return updateCells(brl);
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *st) {
  const size_t cellCount = brl->data->model->statusCells;

  if (cellsHaveChanged(brl->data->prevStatus, st, cellCount, NULL, NULL, NULL)) {
    translateOutputCells(brl->data->rawStatus, brl->data->prevStatus, cellCount);
    brl->data->updateRequired = 1;
  }

  return 1;
}

static int
interpretByte_key (BrailleDisplay *brl, unsigned char byte) {
  int release = (byte & HT_KEY_RELEASE) != 0;
  if (release) byte ^= HT_KEY_RELEASE;

  if ((byte >= HT_KEY_ROUTING) &&
      (byte < (HT_KEY_ROUTING + brl->data->model->textCells))) {
    return enqueueKeyEvent(brl, HT_GRP_RoutingKeys, byte - HT_KEY_ROUTING, !release);
  }

  if ((byte >= HT_KEY_STATUS) &&
      (byte < (HT_KEY_STATUS + brl->data->model->statusCells))) {
    return enqueueKeyEvent(brl, HT_GRP_NavigationKeys, byte, !release);
  }

  if (byte > 0) {
    return enqueueKeyEvent(brl, HT_GRP_NavigationKeys, byte, !release);
  }

  return 0;
}

static int
interpretByte_Bookworm (BrailleDisplay *brl, unsigned char byte) {
  static const KeyNumber keys[] = {
    HT_BWK_Backward,
    HT_BWK_Forward,
    HT_BWK_Escape,
    HT_BWK_Enter,
    0
  };

  const KeyNumber *key = keys;
  const KeyGroup group = HT_GRP_NavigationKeys;

  if (!byte) return 0;
  {
    unsigned char bits = byte;

    while (*key) bits &= ~*key++;
    if (bits) return 0;
    key = keys;
  }

  while (*key) {
    if ((byte & *key) && !enqueueKeyEvent(brl, group, *key, 1)) return 0;
    key += 1;
  }

  do {
    key -= 1;
    if ((byte & *key) && !enqueueKeyEvent(brl, group, *key, 0)) return 0;
  } while (key != keys);

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  while (1) {
    HT_Packet packet;
    size_t size = readPacket(brl, &packet, sizeof(packet));

    if (size == 0) {
      if (errno != EAGAIN) return BRL_CMD_RESTARTBRL;
      break;
    }

    /* a kludge to handle the Bookworm going offline */
    if (brl->data->model->identifier == HT_MODEL_Bookworm) {
      if (packet.fields.type == 0X06) {
        if (brl->data->currentState != BDS_OFF) {
          /* if we get another byte right away then the device
           * has gone offline and is echoing its display
           */
          if (awaitBrailleInput(brl, 10)) {
            setState(brl, BDS_OFF);
            continue;
          }

          /* if an input error occurred then restart the driver */
          if (errno != EAGAIN) return BRL_CMD_RESTARTBRL;

          /* no additional input so fall through and interpret the packet as keys */
        }
      }
    }

    switch (packet.fields.type) {
      case HT_PKT_OK:
        if (packet.fields.data.ok.model == brl->data->model->identifier) {
          releaseBrailleKeys(brl);
          brl->data->updateRequired = 1;
          continue;
        }
        break;

      default:
        switch (brl->data->currentState) {
          case BDS_OFF:
            continue;

          case BDS_READY:
            switch (packet.fields.type) {
              case HT_PKT_NAK:
                brl->data->updateRequired = 1;
              case HT_PKT_ACK:
                acknowledgeBrailleMessage(brl);
                continue;

              case HT_PKT_Extended: {
                unsigned char length = packet.fields.data.extended.length - 1;
                const unsigned char *bytes = &packet.fields.data.extended.data.bytes[0];

                switch (packet.fields.data.extended.type) {
                  case HT_EXTPKT_Confirmation:
                    switch (bytes[0]) {
                      case HT_PKT_NAK:
                        brl->data->updateRequired = 1;
                      case HT_PKT_ACK:
                        acknowledgeBrailleMessage(brl);
                        continue;

                      default:
                        break;
                    }
                    break;

                  case HT_EXTPKT_Key:
                    if (brl->data->model->interpretByte(brl, bytes[0])) {
                      updateCells(brl);
                      return EOF;
                    }
                    break;

                  case HT_EXTPKT_Scancode: {
                    while (length--)
                      enqueueCommand(BRL_CMD_BLK(PASSAT) | BRL_ARG_PUT(*bytes++));
                    continue;
                  }

                  case HT_EXTPKT_GetRTC: {
                    const HT_DateTime *const payload = (HT_DateTime *)bytes;
                    DateTimeProcessor *processor = dateTimeProcessor;
                    dateTimeProcessor = NULL;

                    if (processor) {
                      if (!processor(brl, payload)) {
                        break;
                      }
                    }

                    continue;
                  }

                  case HT_EXTPKT_AtcInfo: {
                    unsigned int readingPosition = BRL_MSK_ARG;
                    unsigned int highestPressure = 0;

                    if (bytes[0]) {
                      const unsigned int cellCount = brl->data->model->textCells + brl->data->model->statusCells;
                      unsigned int cellIndex = bytes[0] - 1;
                      unsigned int dataIndex;

                      for (dataIndex=1; dataIndex<length; dataIndex+=1) {
                        const unsigned char byte = bytes[dataIndex];

                        const unsigned char pressures[] = {
                          HIGH_NIBBLE(byte) >> 4,
                          LOW_NIBBLE(byte)
                        };

                        const unsigned int pressureCount = ARRAY_COUNT(pressures);
                        unsigned int pressureIndex;

                        for (pressureIndex=0; pressureIndex<pressureCount; pressureIndex+=1) {
                          const unsigned char pressure = pressures[pressureIndex];

                          if (pressure > highestPressure) {
                            highestPressure = pressure;
                            readingPosition = cellIndex;
                          }

                          cellIndex += 1;
                        }
                      }

                      if (readingPosition >= cellCount) readingPosition = BRL_MSK_ARG;
                    }

                    enqueueCommand(BRL_CMD_BLK(TOUCH_AT) | readingPosition);
                    continue;
                  }

                  case HT_EXTPKT_ReadingPosition: {
                    const unsigned int cellCount = brl->data->model->textCells + brl->data->model->statusCells;
                    unsigned int readingPosition = bytes[0];

                    if ((readingPosition == 0XFF) || (readingPosition >= cellCount)) {
                      readingPosition = BRL_MSK_ARG;
                    }

                    enqueueCommand(BRL_CMD_BLK(TOUCH_AT) | readingPosition);
                    continue;
                  }

                  default:
                    break;
                }
                break;
              }

              default:
                if (brl->data->model->interpretByte(brl, packet.fields.type)) {
                  updateCells(brl);
                  return EOF;
                }
                break;
            }
            break;
        }
        break;
    }

    logUnexpectedPacket(packet.bytes, size);
    logMessage(LOG_WARNING, "state %d", brl->data->currentState);
  }

  updateCells(brl);

  return EOF;
}
