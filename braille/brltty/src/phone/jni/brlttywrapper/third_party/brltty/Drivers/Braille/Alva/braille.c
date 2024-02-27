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

/* Alva/braille.cc - Braille display library for Alva braille displays
 * Copyright (C) 1995-2002 by Nicolas Pitre <nico@fluxnic.net>
 * See the GNU Lesser General Public License for details in the LICENSE-LGPL file
 *
 */

/* Changes:
 *    january 2004:
 *              - Added USB support.
 *              - Improved key bindings for Satellite models.
 *              - Moved autorepeat (typematic) support to the core.
 *    september 2002:
 *		- This pesky binary only parallel port library is just
 *		  causing trouble (not compatible with new compilers, etc).
 *		  It is also unclear if distribution of such closed source
 *		  library is allowed within a GPL'ed program archive.
 *		  Let's just nuke it until we can write an open source one.
 *		- Converted this file back to pure C source.
 *    may 21, 1999:
 *		- Added Alva Delphi 80 support.  Thanks to ???
*		  <cstrobel@crosslink.net>.
 *    mar 14, 1999:
 *		- Added LogPrint's (which is a good thing...)
 *		- Ugly ugly hack for parallel port support:  seems there
 *		  is a bug in the parallel port library so that the display
 *		  completely hang after an arbitrary period of time.
 *		  J. Lemmens didn't respond to my query yet... and since
 *		  the F***ing library isn't Open Source, I can't fix it.
 *    feb 05, 1999:
 *		- Added Alva Delphi support  (thanks to Terry Barnaby 
 *		  <terry@beam.demon.co.uk>).
 *		- Renamed Alva_ABT3 to Alva.
 *		- Some improvements to the autodetection stuff.
 *    dec 06, 1998:
 *		- added parallel port communication support using
 *		  J. lemmens <jlemmens@inter.nl.net> 's library.
 *		  This required brl.o to be sourced with C++ for the parallel 
 *		  stuff to link.  Now brl.o is a partial link of brlmain.o 
 *		  and the above library.
 *    jun 21, 1998:
 *		- replaced CMD_WINUP/DN with CMD_ATTRUP/DN wich seems
 *		  to be a more useful binding.  Modified help files 
 *		  acordingly.
 *    apr 23, 1998:
 *		- I finally had the chance to test with an ABT380... and
 *		  corrected the ABT380 model ID for autodetection.
 *		- Added a refresh delay to force redrawing the whole display
 *		  in order to minimize garbage due to noise on the 
 *		  serial line
 *    oct 02, 1996:
 *		- bound CMD_SAY_LINE and CMD_MUTE
 *    sep 22, 1996:
 *		- bound CMD_PRDIFLN and CMD_NXDIFLN.
 *    aug 15, 1996:
 *              - adeded automatic model detection for new firmware.
 *              - support for selectable help screen.
 *    feb 19, 1996: 
 *              - added small hack for automatic rewrite of display when
 *                the terminal is turned off and back on, replugged, etc.
 *      feb 15, 1996:
 *              - Modified writebrl() for lower bandwith
 *              - Joined the forced ReWrite function to the CURSOR key
 *      jan 31, 1996:
 *              - moved user configurable parameters into brlconf.h
 *              - added identbrl()
 *              - added overide parameter for serial device
 *              - added keybindings for BRLTTY preferences menu
 *      jan 23, 1996:
 *              - modifications to be compatible with the BRLTTY braille
 *                mapping standard.
 *      dec 27, 1995:
 *              - Added conditions to support all ABT3xx series
 *              - changed directory Alva_ABT40 to Alva_ABT3
 *      dec 02, 1995:
 *              - made changes to support latest Alva ABT3 firmware (new
 *                serial protocol).
 *      nov 05, 1995:
 *              - added typematic facility
 *              - added key bindings for Stephane Doyon's cut'n paste.
 *              - added cursor routing key block marking
 *              - fixed a bug in readbrl() about released keys
 *      sep 30' 1995:
 *              - initial Alva driver code, inspired from the
 *                (old) BrailleLite code.
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <errno.h>

#include "log.h"
#include "strfmt.h"
#include "parse.h"
#include "bitfield.h"
#include "timing.h"
#include "ascii.h"
#include "hidkeys.h"
#include "io_generic.h"
#include "io_usb.h"
#include "usb_hid.h"

typedef enum {
  PARM_ROTATED_CELLS,
  PARM_SECONDARY_ROUTING_KEY_EMULATION
} DriverParameter;
#define BRLPARMS "rotatedcells", "secondaryroutingkeyemulation"

#define BRL_STATUS_FIELDS sfAlphabeticCursorCoordinates, sfAlphabeticWindowCoordinates, sfStateLetter
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "brldefs-al.h"
#include "braille.h"

BEGIN_KEY_NAME_TABLE(routing1)
  KEY_GROUP_ENTRY(AL_GRP_RoutingKeys1, "RoutingKey1"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing2)
  KEY_GROUP_ENTRY(AL_GRP_RoutingKeys2, "RoutingKey2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(status1)
  KEY_NAME_ENTRY(AL_KEY_STATUS1+0, "Status1A"),
  KEY_NAME_ENTRY(AL_KEY_STATUS1+1, "Status1B"),
  KEY_NAME_ENTRY(AL_KEY_STATUS1+2, "Status1C"),
  KEY_NAME_ENTRY(AL_KEY_STATUS1+3, "Status1D"),
  KEY_NAME_ENTRY(AL_KEY_STATUS1+4, "Status1E"),
  KEY_NAME_ENTRY(AL_KEY_STATUS1+5, "Status1F"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(status2)
  KEY_NAME_ENTRY(AL_KEY_STATUS2+0, "Status2A"),
  KEY_NAME_ENTRY(AL_KEY_STATUS2+1, "Status2B"),
  KEY_NAME_ENTRY(AL_KEY_STATUS2+2, "Status2C"),
  KEY_NAME_ENTRY(AL_KEY_STATUS2+3, "Status2D"),
  KEY_NAME_ENTRY(AL_KEY_STATUS2+4, "Status2E"),
  KEY_NAME_ENTRY(AL_KEY_STATUS2+5, "Status2F"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(abt_basic)
  KEY_NAME_ENTRY(AL_KEY_Prog, "Prog"),
  KEY_NAME_ENTRY(AL_KEY_Home, "Home"),
  KEY_NAME_ENTRY(AL_KEY_Cursor, "Cursor"),

  KEY_NAME_ENTRY(AL_KEY_Up, "Up"),
  KEY_NAME_ENTRY(AL_KEY_Left, "Left"),
  KEY_NAME_ENTRY(AL_KEY_Right, "Right"),
  KEY_NAME_ENTRY(AL_KEY_Down, "Down"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(abt_extra)
  KEY_NAME_ENTRY(AL_KEY_Cursor2, "Cursor2"),
  KEY_NAME_ENTRY(AL_KEY_Home2, "Home2"),
  KEY_NAME_ENTRY(AL_KEY_Prog2, "Prog2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(sat_basic)
  KEY_NAME_ENTRY(AL_KEY_Home, "Home"),
  KEY_NAME_ENTRY(AL_KEY_Cursor, "Cursor"),

  KEY_NAME_ENTRY(AL_KEY_Up, "Up"),
  KEY_NAME_ENTRY(AL_KEY_Left, "Left"),
  KEY_NAME_ENTRY(AL_KEY_Right, "Right"),
  KEY_NAME_ENTRY(AL_KEY_Down, "Down"),

  KEY_NAME_ENTRY(AL_KEY_SpeechPadF1, "SpeechPadF1"),
  KEY_NAME_ENTRY(AL_KEY_SpeechPadUp, "SpeechPadUp"),
  KEY_NAME_ENTRY(AL_KEY_SpeechPadLeft, "SpeechPadLeft"),
  KEY_NAME_ENTRY(AL_KEY_SpeechPadDown, "SpeechPadDown"),
  KEY_NAME_ENTRY(AL_KEY_SpeechPadRight, "SpeechPadRight"),
  KEY_NAME_ENTRY(AL_KEY_SpeechPadF2, "SpeechPadF2"),

  KEY_NAME_ENTRY(AL_KEY_NavPadF1, "NavPadF1"),
  KEY_NAME_ENTRY(AL_KEY_NavPadUp, "NavPadUp"),
  KEY_NAME_ENTRY(AL_KEY_NavPadLeft, "NavPadLeft"),
  KEY_NAME_ENTRY(AL_KEY_NavPadDown, "NavPadDown"),
  KEY_NAME_ENTRY(AL_KEY_NavPadRight, "NavPadRight"),
  KEY_NAME_ENTRY(AL_KEY_NavPadF2, "NavPadF2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(sat_extra)
  KEY_NAME_ENTRY(AL_KEY_LeftTumblerLeft, "LeftTumblerLeft"),
  KEY_NAME_ENTRY(AL_KEY_LeftTumblerRight, "LeftTumblerRight"),
  KEY_NAME_ENTRY(AL_KEY_RightTumblerLeft, "RightTumblerLeft"),
  KEY_NAME_ENTRY(AL_KEY_RightTumblerRight, "RightTumblerRight"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(etouch)
  KEY_NAME_ENTRY(AL_KEY_ETouchLeftRear, "ETouchLeftRear"),
  KEY_NAME_ENTRY(AL_KEY_ETouchLeftFront, "ETouchLeftFront"),
  KEY_NAME_ENTRY(AL_KEY_ETouchRightRear, "ETouchRightRear"),
  KEY_NAME_ENTRY(AL_KEY_ETouchRightFront, "ETouchRightFront"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(smartpad)
  KEY_NAME_ENTRY(AL_KEY_SmartpadF1, "SmartpadF1"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadF2, "SmartpadF2"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadLeft, "SmartpadLeft"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadEnter, "SmartpadEnter"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadUp, "SmartpadUp"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadDown, "SmartpadDown"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadRight, "SmartpadRight"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadF3, "SmartpadF3"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadF4, "SmartpadF4"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(thumb)
  KEY_NAME_ENTRY(AL_KEY_THUMB+0, "ThumbLeft"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+1, "ThumbUp"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+2, "ThumbHome"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+3, "ThumbDown"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+4, "ThumbRight"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(featurepack)
  KEY_NAME_ENTRY(AL_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(AL_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(AL_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(AL_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(AL_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(AL_KEY_Dot6, "Dot6"),
  KEY_NAME_ENTRY(AL_KEY_Dot7, "Dot7"),
  KEY_NAME_ENTRY(AL_KEY_Dot8, "Dot8"),
  KEY_NAME_ENTRY(AL_KEY_Control, "Control"),
  KEY_NAME_ENTRY(AL_KEY_Windows, "Windows"),
  KEY_NAME_ENTRY(AL_KEY_Space, "Space"),
  KEY_NAME_ENTRY(AL_KEY_Alt, "Alt"),
  KEY_NAME_ENTRY(AL_KEY_Enter, "Enter"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(el)
  KEY_NAME_ENTRY(AL_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(AL_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(AL_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(AL_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(AL_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(AL_KEY_Dot6, "Dot6"),

  KEY_NAME_ENTRY(AL_KEY_Dot7, "Shift"),
  KEY_NAME_ENTRY(AL_KEY_Space, "Space"),
  KEY_NAME_ENTRY(AL_KEY_Dot8, "Control"),

  KEY_NAME_ENTRY(AL_KEY_SmartpadEnter, "JoystickEnter"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadLeft, "JoystickLeft"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadRight, "JoystickRight"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadUp, "JoystickUp"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadDown, "JoystickDown"),

  KEY_NAME_ENTRY(AL_KEY_THUMB+0, "ScrollLeft"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+4, "ScrollRight"),

  KEY_GROUP_ENTRY(AL_GRP_RoutingKeys1, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(voyager)
  KEY_NAME_ENTRY(AL_KEY_THUMB+0, "Thumb1"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+1, "Thumb2"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+3, "Thumb3"),
  KEY_NAME_ENTRY(AL_KEY_THUMB+4, "Thumb4"),

  KEY_NAME_ENTRY(AL_KEY_SmartpadLeft, "Left"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadUp, "Up"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadDown, "Down"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadRight, "Right"),

  KEY_NAME_ENTRY(AL_KEY_SmartpadF1, "Dot1"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadF2, "Dot2"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadF3, "Dot3"),
  KEY_NAME_ENTRY(AL_KEY_SmartpadF4, "Dot4"),

  KEY_NAME_ENTRY(AL_KEY_ETouchLeftRear, "Dot5"),
  KEY_NAME_ENTRY(AL_KEY_ETouchLeftFront, "Dot6"),
  KEY_NAME_ENTRY(AL_KEY_ETouchRightRear, "Dot7"),
  KEY_NAME_ENTRY(AL_KEY_ETouchRightFront, "Dot8"),

  KEY_GROUP_ENTRY(AL_GRP_RoutingKeys1, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(abt_small)
  KEY_NAME_TABLE(abt_basic),
  KEY_NAME_TABLE(status1),
  KEY_NAME_TABLE(routing1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(abt_large)
  KEY_NAME_TABLE(abt_basic),
  KEY_NAME_TABLE(abt_extra),
  KEY_NAME_TABLE(status1),
  KEY_NAME_TABLE(routing1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(sat_small)
  KEY_NAME_TABLE(sat_basic),
  KEY_NAME_TABLE(status1),
  KEY_NAME_TABLE(status2),
  KEY_NAME_TABLE(routing1),
  KEY_NAME_TABLE(routing2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(sat_large)
  KEY_NAME_TABLE(sat_basic),
  KEY_NAME_TABLE(sat_extra),
  KEY_NAME_TABLE(status1),
  KEY_NAME_TABLE(status2),
  KEY_NAME_TABLE(routing1),
  KEY_NAME_TABLE(routing2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bc640)
  KEY_NAME_TABLE(etouch),
  KEY_NAME_TABLE(smartpad),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(featurepack),
  KEY_NAME_TABLE(routing1),
  KEY_NAME_TABLE(routing2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bc680)
  KEY_NAME_TABLE(etouch),
  KEY_NAME_TABLE(smartpad),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(featurepack),
  KEY_NAME_TABLE(routing1),
  KEY_NAME_TABLE(routing2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el)
  KEY_NAME_TABLE(el),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(voyager)
  KEY_NAME_TABLE(voyager),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(abt_small)
DEFINE_KEY_TABLE(abt_large)
DEFINE_KEY_TABLE(sat_small)
DEFINE_KEY_TABLE(sat_large)
DEFINE_KEY_TABLE(bc640)
DEFINE_KEY_TABLE(bc680)
DEFINE_KEY_TABLE(el)
DEFINE_KEY_TABLE(voyager)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(abt_small),
  &KEY_TABLE_DEFINITION(abt_large),
  &KEY_TABLE_DEFINITION(sat_small),
  &KEY_TABLE_DEFINITION(sat_large),
  &KEY_TABLE_DEFINITION(bc640),
  &KEY_TABLE_DEFINITION(bc680),
  &KEY_TABLE_DEFINITION(el),
  &KEY_TABLE_DEFINITION(voyager),
END_KEY_TABLE_LIST

struct BrailleDataStruct {
  unsigned int rotatedCells;

  struct {
    unsigned char buffer[0X20];
    unsigned char *end;
  } restore;

  union {
    struct {
      unsigned int secondaryRoutingKeyEmulation;

      unsigned char splitOffset;
      HidKeyboardPacket hidKeyboardPacket;

      struct {
        uint32_t hardware;
        uint32_t firmware;
        uint32_t btBase;
        uint32_t btFP;
      } version;

      struct {
        uint64_t base;
        uint64_t featurePack;
      } macAddress;
    } bc;
  } protocol;
};

typedef struct {
  const char *name;
  const KeyTableDefinition *keyTableDefinition;
  unsigned char identifier;
  unsigned char columns;
  unsigned char statusCells;
  unsigned char flags;
} ModelEntry;
static const ModelEntry *model;		/* points to terminal model config struct */

#define MOD_FLAG_CAN_CONFIGURE   0X01
#define MOD_FLAG_FORCE_FROM_0    0X02

static const ModelEntry modelTable[] = {
  { .identifier = 0X00,
    .name = "ABT 320",
    .columns = 20,
    .statusCells = 3,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_small)
  }
  ,
  { .identifier = 0X01,
    .name = "ABT 340",
    .columns = 40,
    .statusCells = 3,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_small)
  }
  ,
  { .identifier = 0X02,
    .name = "ABT 340 Desktop",
    .columns = 40,
    .statusCells = 5,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_small)
  }
  ,
  { .identifier = 0X03,
    .name = "ABT 380",
    .columns = 80,
    .statusCells = 5,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_large)
  }
  ,
  { .identifier = 0X04,
    .name = "ABT 382 Twin Space",
    .columns = 80,
    .statusCells = 5,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_large)
  }
  ,
  { .identifier = 0X0A,
    .name = "Delphi 420",
    .columns = 20,
    .statusCells = 3,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_small)
  }
  ,
  { .identifier = 0X0B,
    .name = "Delphi 440",
    .columns = 40,
    .statusCells = 3,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_small)
  }
  ,
  { .identifier = 0X0C,
    .name = "Delphi 440 Desktop",
    .columns = 40,
    .statusCells = 5,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_small)
  }
  ,
  { .identifier = 0X0D,
    .name = "Delphi 480",
    .columns = 80,
    .statusCells = 5,
    .flags = 0,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(abt_large)
  }
  ,
  { .identifier = 0X0E,
    .name = "Satellite 544",
    .columns = 40,
    .statusCells = 3,
    .flags = MOD_FLAG_CAN_CONFIGURE,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(sat_small)
  }
  ,
  { .identifier = 0X0F,
    .name = "Satellite 570 Pro",
    .columns = 66,
    .statusCells = 3,
    .flags = MOD_FLAG_CAN_CONFIGURE,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(sat_large)
  }
  ,
  { .identifier = 0X10,
    .name = "Satellite 584 Pro",
    .columns = 80,
    .statusCells = 3,
    .flags = MOD_FLAG_CAN_CONFIGURE,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(sat_large)
  }
  ,
  { .identifier = 0X11,
    .name = "Satellite 544 Traveller",
    .columns = 40,
    .statusCells = 3,
    .flags = MOD_FLAG_CAN_CONFIGURE,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(sat_small)
  }
  ,
  { .identifier = 0X13,
    .name = "Braille System 40",
    .columns = 40,
    .statusCells = 0,
    .flags = MOD_FLAG_CAN_CONFIGURE,
    .keyTableDefinition = &KEY_TABLE_DEFINITION(sat_small)
  }
  ,
  { .name = NULL }
};

static const ModelEntry modelBC624 = {
  .identifier = 0X24,
  .name = "BC624",
  .columns = 24,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(bc640)
};

static const ModelEntry modelBC640 = {
  .identifier = 0X40,
  .name = "BC640",
  .columns = 40,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(bc640)
};

static const ModelEntry modelBC680 = {
  .identifier = 0X80,
  .name = "BC680",
  .columns = 80,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(bc680)
};

static const ModelEntry modelEL12 = {
  .identifier = 0X40,
  .name = "EasyLink 12 Touch",
  .columns = 12,
  .flags = MOD_FLAG_FORCE_FROM_0,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(el)
};

static const ModelEntry modelVoyager = {
  .identifier = 0X00,
  .name = "Voyager Protocol Converter",
  .columns = 70,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(voyager)
};

typedef struct {
  int (*test) (BrailleDisplay *brl);
  unsigned char feature;
  unsigned char offset;
  unsigned char disable;
  unsigned char enable;
} SettingsUpdateEntry;

typedef struct {
  void (*initializeVariables) (BrailleDisplay *brl, char **parameters);

  BraillePacketVerifier *verifyPacket;
  int (*readPacket) (BrailleDisplay *brl, unsigned char *packet, int size);

  const SettingsUpdateEntry *requiredSettings;
  int (*setFeature) (BrailleDisplay *brl, const unsigned char *data, size_t size);
  size_t (*getFeature) (BrailleDisplay *brl, unsigned char feature, unsigned char *buffer, size_t size);

  int (*updateConfiguration) (BrailleDisplay *brl, int autodetecting, const unsigned char *packet);
  int (*detectModel) (BrailleDisplay *brl);

  int (*readCommand) (BrailleDisplay *brl);
  int (*writeBraille) (BrailleDisplay *brl, const unsigned char *cells, int start, int count);
} ProtocolOperations;
static const ProtocolOperations *protocol;

typedef enum {
  STATUS_FIRST,
  STATUS_LEFT,
  STATUS_RIGHT
} StatusType;

static unsigned char *previousText = NULL;
static unsigned char *previousStatus = NULL;

static unsigned char actualColumns;
static unsigned char textOffset;
static unsigned char statusOffset;

static unsigned char textRewriteRequired = 0;
static unsigned char statusRewriteRequired;

typedef unsigned char FieldByteConverter (unsigned char byte);

static uint64_t
parseNumericField (
  const unsigned char **bytes, size_t *count,
  size_t size, size_t width,
  FieldByteConverter *convertByte
) {
  uint64_t result = 0;

  while (width > 0) {
    result <<= 8;

    if (size > 0) {
      if (*count > 0) {
        result |= convertByte(*(*bytes)++);
        *count -= 1;
      }

      size -= 1;
    }

    width -= 1;
  }

  return result;
}

static unsigned char
convertHexadecimalByte (unsigned char byte) {
  return byte;
}

static uint64_t
parseHexadecimalField (
  const unsigned char **bytes, size_t *count,
  size_t size, size_t width
) {
  return parseNumericField(bytes, count, size, width, convertHexadecimalByte);
}

static unsigned char
convertDecimalByte (unsigned char byte) {
  return byte - '0';
}

static uint64_t
parseDecimalField (
  const unsigned char **bytes, size_t *count,
  size_t size, size_t width
) {
  return parseNumericField(bytes, count, size, width, convertDecimalByte);
}

static int
readPacket (BrailleDisplay *brl, unsigned char *packet, int size) {
  return readBraillePacket(brl, NULL, packet, size, protocol->verifyPacket, NULL);
}

static int
flushSettingsUpdate (
  BrailleDisplay *brl, size_t length,
  const unsigned char *old, const unsigned char *new
) {
  if (length) {
    if (memcmp(old, new, length) != 0) {
      if (!protocol->setFeature(brl, new, length)) return 0;

      {
        unsigned char **const end = &brl->data->restore.end;

        if (length > UINT8_MAX) {
          logBytes(LOG_WARNING, "settings update too long", new, length);
        } else if ((*end + length + 1) > (brl->data->restore.buffer + sizeof(brl->data->restore.buffer))) {
          logBytes(LOG_WARNING, "settings update not saved", new, length);
        } else {
          *end = mempcpy(*end, old, length);
          *(*end)++ = length;
        }
      }
    }
  }

  return 1;
}

static int
updateSettings (BrailleDisplay *brl) {
  size_t length = 0;
  const size_t size = 0X20;
  unsigned char old[size];
  unsigned char new[size];
  const SettingsUpdateEntry *settings = protocol->requiredSettings;

  if (settings) {
    unsigned char previous = 0;

    while (settings->feature) {
      if (!settings->test || settings->test(brl)) {
        if (settings->feature != previous) {
          if (!flushSettingsUpdate(brl, length, old, new)) return 0;

          if (!(length = protocol->getFeature(brl, settings->feature, old, size))) {
            if (errno == EAGAIN) goto next;

#ifdef ETIMEDOUT
            if (errno == ETIMEDOUT) goto next;
#endif /* ETIMEDOUT */

            return 0;
          }

          memcpy(new, old, length);
          previous = settings->feature;
        }

        {
          unsigned char *byte = &new[settings->offset];

          *byte &= ~settings->disable;
          *byte |= settings->enable;
        }
      }

    next:
      settings += 1;
    }
  }

  return flushSettingsUpdate(brl, length, old, new);
}

static int
restoreSettings (BrailleDisplay *brl) {
  const unsigned char *request = brl->data->restore.end;

  while (request > brl->data->restore.buffer) {
    unsigned char length = *--request;

    request -= length;
    if (!protocol->setFeature(brl, request, length)) return 0;
  }

  return 1;
}

static int
reallocateBuffer (unsigned char **buffer, int size) {
  void *address = realloc(*buffer, size);
  if (size && !address) return 0;
  *buffer = address;
  return 1;
}

static int
reallocateBuffers (BrailleDisplay *brl) {
  if (reallocateBuffer(&previousText, brl->textColumns*brl->textRows))
    if (reallocateBuffer(&previousStatus, brl->statusColumns*brl->statusRows))
      return 1;

  logMessage(LOG_ERR, "cannot allocate braille buffers");
  return 0;
}

static int
setDefaultConfiguration (BrailleDisplay *brl) {
  logMessage(LOG_INFO, "detected Alva %s: %d columns, %d status cells",
             model->name, model->columns, model->statusCells);

  brl->textColumns = model->columns;
  brl->textRows = 1;
  brl->statusColumns = model->statusCells;
  brl->statusRows = 1;

  actualColumns = model->columns;
  statusOffset = 0;
  textOffset = statusOffset + model->statusCells;
  textRewriteRequired = 1;			/* To write whole display at first time */
  statusRewriteRequired = 1;
  return reallocateBuffers(brl);
}

static int
updateConfiguration (BrailleDisplay *brl, int autodetecting, int textColumns, int statusColumns, StatusType statusType) {
  int changed = 0;
  int separator = 0;

  actualColumns = textColumns;
  if (statusType == STATUS_FIRST) {
    statusOffset = 0;
    textOffset = statusOffset + statusColumns;
  } else if ((statusColumns = MIN(statusColumns, (actualColumns-1)/2))) {
    separator = 1;
    textColumns -= statusColumns + separator;

    switch (statusType) {
      case STATUS_LEFT:
        statusOffset = 0;
        textOffset = statusOffset + statusColumns + separator;
        break;

      case STATUS_RIGHT:
        textOffset = 0;
        statusOffset = textOffset + textColumns + separator;
        break;

      default:
        break;
    }
  } else {
    statusOffset = 0;
    textOffset = 0;
  }

  if (statusColumns != brl->statusColumns) {
    logMessage(LOG_INFO, "status cell count changed to %d", statusColumns);
    brl->statusColumns = statusColumns;
    changed = 1;
  }

  if (textColumns != brl->textColumns) {
    logMessage(LOG_INFO, "text column count changed to %d", textColumns);
    brl->textColumns = textColumns;
    if (!autodetecting) brl->resizeRequired = 1;
    changed = 1;
  }

  if (changed)
    if (!reallocateBuffers(brl))
      return 0;

  if (separator) {
    unsigned char cell = 0;
    if (!protocol->writeBraille(brl, &cell, MAX(textOffset, statusOffset)-1, 1)) return 0;
  }

  textRewriteRequired = 1;
  statusRewriteRequired = 1;
  return 1;
}

#define PACKET_SIZE(count) (((count) * 2) + 4)
#define MAXIMUM_PACKET_SIZE PACKET_SIZE(0XFF)
#define PACKET_BYTE(packet, index) ((packet)[PACKET_SIZE((index)) - 1])

static const unsigned char BRL_ID[] = {ASCII_ESC, 'I', 'D', '='};
#define BRL_ID_LENGTH (sizeof(BRL_ID))
#define BRL_ID_SIZE (BRL_ID_LENGTH + 1)

static int
writeFunction1 (BrailleDisplay *brl, unsigned char code) {
  unsigned char bytes[] = {ASCII_ESC, 'F', 'U', 'N', code, ASCII_CR};
  return writeBraillePacket(brl, NULL, bytes, sizeof(bytes));
}

static int
writeParameter1 (BrailleDisplay *brl, unsigned char parameter, unsigned char setting) {
  unsigned char bytes[] = {ASCII_ESC, 'P', 'A', 3, 0, parameter, setting, ASCII_CR};
  return writeBraillePacket(brl, NULL, bytes, sizeof(bytes));
}

static int
updateConfiguration1 (BrailleDisplay *brl, int autodetecting, const unsigned char *packet) {
  int textColumns = brl->textColumns;
  int statusColumns = brl->statusColumns;
  int count = PACKET_BYTE(packet, 0);

  if (count >= 3) statusColumns = PACKET_BYTE(packet, 3);
  if (count >= 4) textColumns = PACKET_BYTE(packet, 4);
  return updateConfiguration(brl, autodetecting, textColumns, statusColumns, STATUS_FIRST);
}

static int
setBrailleFirmness1 (BrailleDisplay *brl, BrailleFirmness setting) {
  return writeParameter1(brl, 3,
                         setting * 4 / BRL_FIRMNESS_MAXIMUM);
}

static int
identifyModel1 (BrailleDisplay *brl, unsigned char identifier) {
  /* Find out which model we are connected to... */
  for (
    model = modelTable;
    model->name && (model->identifier != identifier);
    model += 1
  );

  if (model->name) {
    if (setDefaultConfiguration(brl)) {
      if (model->flags & MOD_FLAG_CAN_CONFIGURE) {
        brl->setBrailleFirmness = setBrailleFirmness1;

        if (!writeFunction1(brl, 0X07)) return 0;

        while (awaitBrailleInput(brl, 200)) {
          unsigned char packet[MAXIMUM_PACKET_SIZE];
          int count = protocol->readPacket(brl, packet, sizeof(packet));

          if (count == -1) break;
          if (count == 0) continue;

          if ((packet[0] == 0X7F) && (packet[1] == 0X07)) {
            updateConfiguration1(brl, 1, packet);
            break;
          }
        }

        if (!writeFunction1(brl, 0X0B)) return 0;
      }

      return 1;
    }
  } else {
    logMessage(LOG_ERR, "detected unknown Alva model with ID %02X (hex)", identifier);
  }

  return 0;
}

static void
initializeVariables1 (BrailleDisplay *brl, char **parameters) {
}

static int
readPacket1 (BrailleDisplay *brl, unsigned char *packet, int size) {
  int offset = 0;
  int length = 0;

  while (1) {
    unsigned char byte;

    {
      int started = offset > 0;

      if (!gioReadByte(brl->gioEndpoint, &byte, started)) {
        int result = (errno == EAGAIN)? 0: -1;
        if (started) logPartialPacket(packet, offset);
        return result;
      }
    }

  gotByte:
    if (offset == 0) {
      if (byte == 0X7F) {
        length = PACKET_SIZE(0);
      } else if ((byte & 0XF0) == 0X70) {
        length = 2;
      } else if (byte == BRL_ID[0]) {
        length = BRL_ID_SIZE;
      } else if (!byte) {
        length = 2;
      } else {
        logIgnoredByte(byte);
        continue;
      }
    } else {
      int unexpected = 0;

      unsigned char type = packet[0];

      if (type == 0X7F) {
        if (offset == 3) length = PACKET_SIZE(byte);
        if (((offset % 2) == 0) && (byte != 0X7E)) unexpected = 1;
      } else if (type == BRL_ID[0]) {
        if ((offset < BRL_ID_LENGTH) && (byte != BRL_ID[offset])) unexpected = 1;
      } else if (!type) {
        if (byte) unexpected = 1;
      }

      if (unexpected) {
        logShortPacket(packet, offset);
        offset = 0;
        length = 0;
        goto gotByte;
      }
    }

    if (offset < size) {
      packet[offset] = byte;
    } else {
      if (offset == size) logTruncatedPacket(packet, offset);
      logDiscardedByte(byte);
    }

    if (++offset == length) {
      if ((offset > size) || !packet[0]) {
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
detectModel1 (BrailleDisplay *brl) {
  int probes = 0;

  while (writeFunction1(brl, 0X06)) {
    while (awaitBrailleInput(brl, 200)) {
      unsigned char packet[MAXIMUM_PACKET_SIZE];

      if (protocol->readPacket(brl, packet, sizeof(packet)) > 0) {
        if (memcmp(packet, BRL_ID, BRL_ID_LENGTH) == 0) {
          if (identifyModel1(brl, packet[BRL_ID_LENGTH])) {
            return 1;
          }
        }
      }
    }

    if (errno != EAGAIN) break;
    if (++probes == 3) break;
  }

  return 0;
}

static int
readCommand1 (BrailleDisplay *brl) {
  unsigned char packet[MAXIMUM_PACKET_SIZE];
  int length;

  while ((length = protocol->readPacket(brl, packet, sizeof(packet))) > 0) {
    unsigned char group = packet[0];
    unsigned char key = packet[1];
    int press = !(key & AL_KEY_RELEASE);
    key &= ~AL_KEY_RELEASE;

    switch (group) {
      case 0X71: /* operating keys and status keys */
        if (key <= 0X0D) {
          enqueueKeyEvent(brl, AL_GRP_NavigationKeys, key+AL_KEY_OPERATION, press);
          continue;
        }

        if ((key >= 0X20) && (key <= 0X25)) {
          enqueueKeyEvent(brl, AL_GRP_NavigationKeys, key-0X20+AL_KEY_STATUS1, press);
          continue;
        }

        if ((key >= 0X30) && (key <= 0X35)) {
          enqueueKeyEvent(brl, AL_GRP_NavigationKeys, key-0X30+AL_KEY_STATUS2, press);
          continue;
        }

        break;

      case 0X72: /* primary (lower) routing keys */
        if (key <= 0X5F) {			/* make */
          enqueueKeyEvent(brl, AL_GRP_RoutingKeys1, key, press);
          continue;
        }

        break;

      case 0X75: /* secondary (upper) routing keys */
        if (key <= 0X5F) {			/* make */
          enqueueKeyEvent(brl, AL_GRP_RoutingKeys2, key, press);
          continue;
        }

        break;

      case 0X77: /* satellite keypads */
        if (key <= 0X05) {
          enqueueKeyEvent(brl, AL_GRP_NavigationKeys, key+AL_KEY_SPEECH_PAD, press);
          continue;
        }

        if ((key >= 0X20) && (key <= 0X25)) {
          enqueueKeyEvent(brl, AL_GRP_NavigationKeys, key-0X20+AL_KEY_NAV_PAD, press);
          continue;
        }

        continue;

      case 0X7F:
        switch (packet[1]) {
          case 0X07: /* text/status cells reconfigured */
            if (!updateConfiguration1(brl, 0, packet)) return BRL_CMD_RESTARTBRL;
            continue;

          case 0X0B: { /* display parameters reconfigured */
            int count = PACKET_BYTE(packet, 0);

            if (count >= 8) {
              unsigned char frontKeys = PACKET_BYTE(packet, 8);
              const unsigned char progKey = 0X02;

              if (frontKeys & progKey) {
                unsigned char newSetting = frontKeys & ~progKey;

                logMessage(LOG_DEBUG, "Reconfiguring front keys: %02X -> %02X",
                           frontKeys, newSetting);
                writeParameter1(brl, 6, newSetting);
              }
            }

            continue;
          }
        }

        break;

      default:
        if (length >= BRL_ID_SIZE) {
          if (memcmp(packet, BRL_ID, BRL_ID_LENGTH) == 0) {
            /* The terminal has been turned off and back on. */
            if (!identifyModel1(brl, packet[BRL_ID_LENGTH])) return BRL_CMD_RESTARTBRL;
            brl->resizeRequired = 1;
            continue;
          }
        }

        break;
    }

    logUnexpectedPacket(packet, length);
  }

  return (length < 0)? BRL_CMD_RESTARTBRL: EOF;
}

static int
writeBraille1 (BrailleDisplay *brl, const unsigned char *cells, int start, int count) {
  static const unsigned char header[] = {ASCII_CR, ASCII_ESC, 'B'};	/* escape code to display braille */
  static const unsigned char trailer[] = {ASCII_CR};		/* to send after the braille sequence */

  unsigned char packet[sizeof(header) + 2 + count + sizeof(trailer)];
  unsigned char *byte = packet;

  byte = mempcpy(byte, header, sizeof(header));
  *byte++ = start;
  *byte++ = count;
  byte = mempcpy(byte, cells, count);
  byte = mempcpy(byte, trailer, sizeof(trailer));

  return writeBraillePacket(brl, NULL, packet, byte-packet);
}

static const ProtocolOperations protocol1Operations = {
  .initializeVariables = initializeVariables1,

  .readPacket = readPacket1,

  .updateConfiguration = updateConfiguration1,
  .detectModel = detectModel1,

  .readCommand = readCommand1,
  .writeBraille = writeBraille1
};

static void
initializeVariables2 (BrailleDisplay *brl, char **parameters) {
  brl->data->protocol.bc.secondaryRoutingKeyEmulation = 0;
  if (*parameters[PARM_SECONDARY_ROUTING_KEY_EMULATION]) {
    if (!validateYesNo(&brl->data->protocol.bc.secondaryRoutingKeyEmulation, parameters[PARM_SECONDARY_ROUTING_KEY_EMULATION])) {
      logMessage(LOG_WARNING, "%s: %s", "invalid secondary routing key emulation setting",
                 parameters[PARM_SECONDARY_ROUTING_KEY_EMULATION]);
    }
  }

  initializeHidKeyboardPacket(&brl->data->protocol.bc.hidKeyboardPacket);

  brl->data->protocol.bc.version.hardware = 0;
  brl->data->protocol.bc.version.firmware = 0;
  brl->data->protocol.bc.version.btBase = 0;
  brl->data->protocol.bc.version.btFP = 0;

  brl->data->protocol.bc.macAddress.base = 0;
  brl->data->protocol.bc.macAddress.featurePack = 0;
}

static int
testHaveFeaturePack2 (BrailleDisplay *brl) {
  return brl->data->protocol.bc.macAddress.featurePack != 0;
}

static int
testHaveRawKeyboard2 (BrailleDisplay *brl) {
  return testHaveFeaturePack2(brl) &&
         (brl->data->protocol.bc.version.firmware >= 0X020801);
}

static void
logVersion2 (uint32_t version, const char *label) {
  BytesOverlay overlay;

  unsigned char *byte = &overlay.bytes[2];
  char string[0X40];

  putLittleEndian32(&overlay.u32, version);
  STR_BEGIN(string, sizeof(string));

  while (1) {
    STR_PRINTF("%u", *byte);
    if (byte == overlay.bytes) break;

    *byte = 0;
    if (!overlay.u32) break;

    STR_PRINTF(".");
    byte -= 1;
  }

  STR_END;
  logMessage(LOG_DEBUG, "%s: %s", label, string);
}

static uint64_t
parseHardwareVersion2 (
  const unsigned char **bytes, size_t *count
) {
  return parseDecimalField(bytes, count, 2, 3);
}

static uint64_t
parseFirmwareVersion2 (
  const unsigned char **bytes, size_t *count
) {
  return parseHexadecimalField(bytes, count, 3, 3);
}

static void
setVersions2 (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  brl->data->protocol.bc.version.hardware = parseHardwareVersion2(&bytes, &count);
  logVersion2(brl->data->protocol.bc.version.hardware, "Hardware Version");

  brl->data->protocol.bc.version.firmware = parseFirmwareVersion2(&bytes, &count);
  logVersion2(brl->data->protocol.bc.version.firmware, "Firmware Version");

  brl->data->protocol.bc.version.btBase = parseFirmwareVersion2(&bytes, &count);
  logVersion2(brl->data->protocol.bc.version.btBase, "Base Bluetooth Module Version");

  brl->data->protocol.bc.version.btFP = parseFirmwareVersion2(&bytes, &count);
  logVersion2(brl->data->protocol.bc.version.btFP, "Feature Pack Bluetooth Module Version");
}

static void
logMacAddress2 (uint64_t address, const char *label) {
  BytesOverlay overlay;

  const unsigned char *byte = &overlay.bytes[5];
  char string[0X20];

  putLittleEndian64(&overlay.u64, address);
  STR_BEGIN(string, sizeof(string));

  while (1) {
    STR_PRINTF("%02X", *byte);
    if (byte == overlay.bytes) break;
    byte -= 1;
    STR_PRINTF("%c", ':');
  }

  STR_END;
  logMessage(LOG_DEBUG, "%s: %s", label, string);
}

static uint64_t
parseMacAddress2 (
  const unsigned char **bytes, size_t *count
) {
  BytesOverlay overlay;

  putLittleEndian64(&overlay.u64, parseHexadecimalField(bytes, count, 6, 6));
  swapBytes(&overlay.bytes[5], &overlay.bytes[4]);
  swapBytes(&overlay.bytes[2], &overlay.bytes[0]);
  return getLittleEndian64(overlay.u64);
}

static void
setMacAddresses2 (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  brl->data->protocol.bc.macAddress.base = parseMacAddress2(&bytes, &count);
  logMacAddress2(brl->data->protocol.bc.macAddress.base, "Base Mac Address");

  brl->data->protocol.bc.macAddress.featurePack = parseMacAddress2(&bytes, &count);
  logMacAddress2(brl->data->protocol.bc.macAddress.featurePack, "Feature Pack Mac Address");
}

static int
interpretKeyboardEvent2 (BrailleDisplay *brl, const unsigned char *packet) {
  const void *newPacket = packet;
  processHidKeyboardPacket(&brl->data->protocol.bc.hidKeyboardPacket, newPacket);
  return EOF;
}

static int
interpretKeyEvent2 (BrailleDisplay *brl, unsigned char group, unsigned char key) {
  unsigned char release = group & 0X80;
  int press = !release;
  group &= ~release;

  switch (group) {
    case 0X01:
      switch (key) {
        case 0X01:
          if (!protocol->updateConfiguration(brl, 0, NULL)) return BRL_CMD_RESTARTBRL;
          return EOF;

        default:
          break;
      }
      break;

    {
      unsigned int base;
      unsigned int count;
      int secondary;

    case 0X71: /* thumb key */
      base = AL_KEY_THUMB;
      count = AL_KEYS_THUMB;
      secondary = 1;
      goto doKey;

    case 0X72: /* etouch key */
      base = AL_KEY_ETOUCH;
      count = AL_KEYS_ETOUCH;
      secondary = 0;
      goto doKey;

    case 0X73: /* smartpad key */
      base = AL_KEY_SMARTPAD;
      count = AL_KEYS_SMARTPAD;
      secondary = 1;
      goto doKey;

    case 0X78: /* feature pack key */
      base = AL_KEY_FEATUREPACK;
      count = AL_KEYS_FEATUREPACK;
      secondary = 0;
      goto doKey;

    doKey:
      if (secondary) {
        if ((key / count) == 1) {
          key -= count;
        }
      }

      if (key < count) {
        enqueueKeyEvent(brl, AL_GRP_NavigationKeys, base+key, press);
        return EOF;
      }
      break;
    }

    case 0X74: { /* routing key */
      unsigned char secondary = key & 0X80;
      key &= ~secondary;

      /* 
       * The 6xx series don't have a second row of routing keys but
       * emulate them (in order to aid compatibility with the 5xx series)
       * using an annoying press delay.  It is adviseable to turn this
       * functionality off in the device's menu, but, in case it's left
       * on, we just interpret these keys as primary routing keys by
       * default, unless overriden by a driver parameter.
       */
      if (!brl->data->protocol.bc.secondaryRoutingKeyEmulation) secondary = 0;

      if (brl->data->protocol.bc.version.firmware < 0X011102) {
        if (key >= brl->data->protocol.bc.splitOffset) {
          key -= brl->data->protocol.bc.splitOffset;
        }
      }

      if (key >= textOffset) {
        if ((key -= textOffset) < brl->textColumns) {
          KeyGroup group = secondary? AL_GRP_RoutingKeys2: AL_GRP_RoutingKeys1;

          enqueueKeyEvent(brl, group, key, press);
          return EOF;
        }
      }
      break;
    }

    default:
      break;
  }

  logMessage(LOG_WARNING, "unknown key: group=%02X key=%02X", group, key);
  return EOF;
}

static BraillePacketVerifierResult
verifyPacket2s (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      switch (byte) {
        case ASCII_ESC:
          *length = 2;
          break;

        default:
          return BRL_PVR_INVALID;
      }
      break;

    case 2:
      switch (byte) {
        case 0X32: /* 2 */ *length =  5; break;
        case 0X3F: /* ? */ *length =  3; break;
        case 0X45: /* E */ *length =  3; break;
        case 0X4B: /* K */ *length =  4; break;
        case 0X4E: /* N */ *length = 14; break;
        case 0X50: /* P */ *length =  3; break;
        case 0X54: /* T */ *length =  4; break;
        case 0X56: /* V */ *length = 13; break;
        case 0X68: /* h */ *length = 10; break;
        case 0X72: /* r */ *length = 3; break;

        default:
          return BRL_PVR_INVALID;
      }
      break;

    default:
      break;
  }

  return BRL_PVR_INCLUDE;
}

static int
setFeature2s (BrailleDisplay *brl, const unsigned char *request, size_t size) {
  return writeBraillePacket(brl, NULL, request, size);
}

static size_t
getFeature2s (BrailleDisplay *brl, unsigned char feature, unsigned char *response, size_t size) {
  const unsigned char request[] = {ASCII_ESC, feature, 0X3F};

  if (protocol->setFeature(brl, request, sizeof(request))) {
    while (awaitBrailleInput(brl, 1000)) {
      int length = protocol->readPacket(brl, response, size);

      if (length <= 0) break;
      if ((response[0] == ASCII_ESC) && (response[1] == feature)) return length;
      logUnexpectedPacket(response, length);
    }
  }

  return 0;
}

static int
updateConfiguration2s (BrailleDisplay *brl, int autodetecting, const unsigned char *packet) {
  unsigned char response[0X20];

  if (protocol->getFeature(brl, 0X45, response, sizeof(response))) {
    unsigned char textColumns = response[2];

    if (autodetecting) {
      if (brl->data->protocol.bc.version.firmware < 0X010A00) {
        switch (textColumns) {
          case 12:
            if (model == &modelBC640) {
              model = &modelEL12;
              logMessage(LOG_INFO, "switched to model %s", model->name);
            }
            break;

          default:
            break;
        }
      }
    }

    if (protocol->getFeature(brl, 0X54, response, sizeof(response))) {
      unsigned char statusColumns = response[2];
      unsigned char statusSide = response[3];

      if (updateConfiguration(brl, autodetecting, textColumns, statusColumns,
                              (statusSide == 'R')? STATUS_RIGHT: STATUS_LEFT)) {
        brl->data->protocol.bc.splitOffset = (model->columns == actualColumns)? 0: actualColumns+1;
        return 1;
      }
    }
  }

  return 0;
}

static int
identifyModel2s (BrailleDisplay *brl, unsigned char identifier) {
  static const ModelEntry *const models[] = {
    &modelBC624, &modelBC640, &modelBC680,
    NULL
  };

  unsigned char response[0X20];
  const ModelEntry *const *modelEntry = models;

  while ((model = *modelEntry++)) {
    if (model->identifier == identifier) {
      if (protocol->getFeature(brl, 0X56, response, sizeof(response))) {
        setVersions2(brl, &response[2], sizeof(response)-2);

        if (protocol->getFeature(brl, 0X4E, response, sizeof(response))) {
          setMacAddresses2(brl, &response[2], sizeof(response)-2);

          if (setDefaultConfiguration(brl)) {
            if (updateConfiguration2s(brl, 1, NULL)) {
              return 1;
            }
          }
        }
      }

      return 0;
    }
  }

  logMessage(LOG_ERR, "detected unknown Alva model with ID %02X (hex)", identifier);
  return 0;
}

static int
detectModel2s (BrailleDisplay *brl) {
  int probes = 0;

  do {
    unsigned char response[0X20];

    if (protocol->getFeature(brl, 0X3F, response, sizeof(response))) {
      if (identifyModel2s(brl, response[2])) {
        return 1;
      }
    } else if (errno != EAGAIN) {
      break;
    }
  } while (++probes < 3);

  return 0;
}

static int
readCommand2s (BrailleDisplay *brl) {
  while (1) {
    unsigned char packet[MAXIMUM_PACKET_SIZE];
    int length = protocol->readPacket(brl, packet, sizeof(packet));

    if (!length) return EOF;
    if (length < 0) return BRL_CMD_RESTARTBRL;

    switch (packet[0]) {
      case ASCII_ESC:
        switch (packet[1]) {
          case 0X4B: /* K */ {
            int command = interpretKeyEvent2(brl, packet[2], packet[3]);
            if (command != EOF) return command;
            continue;
          }

          case 0X68: /* h */ {
            int command = interpretKeyboardEvent2(brl, &packet[2]);
            if (command != EOF) return command;
            continue;
          }

          default:
            break;
        }
        break;

      default:
        break;
    }

    logUnexpectedPacket(packet, length);
  }
}

static int
writeBraille2s (BrailleDisplay *brl, const unsigned char *cells, int start, int count) {
  unsigned char packet[4 + count];
  unsigned char *byte = packet;

  *byte++ = ASCII_ESC;
  *byte++ = 0X42;
  *byte++ = start;
  *byte++ = count;
  byte = mempcpy(byte, cells, count);

  return writeBraillePacket(brl, NULL, packet, byte-packet);
}

static const SettingsUpdateEntry requiredSettings2s[] = {
  { /* enable raw feature pack keys */
    .feature = 0X72 /* r */,
    .test = testHaveRawKeyboard2,
    .offset = 2,
    .disable = 0XFF,
    .enable = 0X01
  },

  { /* disable key repeat */
    .feature = 0X50 /* P */,
    .offset = 2,
    .disable = 0XFF
  },

  { /* disable second routing key row emulation */
    .feature = 0X32 /* 2 */,
    .offset = 2,
    .disable = 0XFF
  },

  { .feature = 0 }
};

static const ProtocolOperations protocol2sOperations = {
  .initializeVariables = initializeVariables2,

  .verifyPacket = verifyPacket2s,
  .readPacket = readPacket,

  .requiredSettings = requiredSettings2s,
  .setFeature = setFeature2s,
  .getFeature = getFeature2s,

  .updateConfiguration = updateConfiguration2s,
  .detectModel = detectModel2s,

  .readCommand = readCommand2s,
  .writeBraille = writeBraille2s
};

static BraillePacketVerifierResult
verifyPacket2u (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      switch (byte) {
        case 0X01: *length = 9; break;
        case 0X04: *length = 3; break;

        default:
          return BRL_PVR_INVALID;
      }
      break;

    default:
      break;
  }

  return BRL_PVR_INCLUDE;
}

static int
setFeature2u (BrailleDisplay *brl, const unsigned char *request, size_t size) {
  logOutputPacket(request, size);
  return gioWriteHidFeature(brl->gioEndpoint, request, size) != -1;
}

static size_t
getFeature2u (BrailleDisplay *brl, HidReportIdentifier identifier, unsigned char *response, size_t size) {
  ssize_t length = gioGetHidFeature(brl->gioEndpoint, identifier, response, size);

  if (length > 0) {
    logInputPacket(response, length);
    return length;
  }

  return 0;
}

static int
updateConfiguration2u (BrailleDisplay *brl, int autodetecting, const unsigned char *packet) {
  unsigned char buffer[0X20];
  size_t length = protocol->getFeature(brl, 0X05, buffer, sizeof(buffer));

  if (length > 0) {
    int textColumns = brl->textColumns;
    int statusColumns = brl->statusColumns;
    int statusSide = 0;

    if (length >= 2) statusColumns = buffer[1];
    if (length >= 3) statusSide = buffer[2];
    if (length >= 7) textColumns = buffer[6];

    if (updateConfiguration(brl, autodetecting, textColumns, statusColumns,
                            statusSide? STATUS_RIGHT: STATUS_LEFT)) {
      brl->data->protocol.bc.splitOffset = model->columns - actualColumns;
      return 1;
    }
  }

  return 0;
}

static int
detectModel2u (BrailleDisplay *brl) {
  {
    unsigned char buffer[0X20];
    size_t length = protocol->getFeature(brl, 0X09, buffer, sizeof(buffer));

    if (length > 3) setVersions2(brl, &buffer[3], length-3);
  }

  {
    unsigned char buffer[0X20];
    size_t length = protocol->getFeature(brl, 0X0D, buffer, sizeof(buffer));

    if (length > 1) setMacAddresses2(brl, &buffer[1], length-1);
  }

  if (setDefaultConfiguration(brl))
    if (updateConfiguration2u(brl, 1, NULL))
      return 1;

  return 0;
}

static int
readCommand2u (BrailleDisplay *brl) {
  while (1) {
    unsigned char packet[MAXIMUM_PACKET_SIZE];
    int length = protocol->readPacket(brl, packet, sizeof(packet));

    if (!length) return EOF;
    if (length < 0) return BRL_CMD_RESTARTBRL;

    switch (packet[0]) {
      case 0X01: {
        int command = interpretKeyboardEvent2(brl, &packet[1]);
        if (command != EOF) return command;
        continue;
      }

      case 0X04: {
        int command = interpretKeyEvent2(brl, packet[2], packet[1]);
        if (command != EOF) return command;
        continue;
      }

      default:
        break;
    }

    logUnexpectedPacket(packet, length);
  }
}

static int
writeBraille2u (BrailleDisplay *brl, const unsigned char *cells, int start, int count) {
  while (count > 0) {
    int length = MIN(count, 40);
    unsigned char packet[3 + length];
    unsigned char *byte = packet;

    *byte++ = 0X02;
    *byte++ = start;
    *byte++ = length;
    byte = mempcpy(byte, cells, length);

    if (!writeBraillePacket(brl, NULL, packet, byte-packet)) return 0;
    cells += length;
    start += length;
    count -= length;
  }

  return 1;
}

static ssize_t
writeData2u (
  UsbDevice *device, const UsbChannelDefinition *definition,
  const void *data, size_t size, int timeout
) {
  const unsigned char *bytes = data;

  return usbHidSetReport(device, definition->interface,
                         bytes[0], bytes, size, timeout);
}

static const SettingsUpdateEntry requiredSettings2u[] = {
  { /* enable raw feature pack keys */
    .feature = 6 /* Key Settings Report */,
    .test = testHaveRawKeyboard2,
    .offset = 1,
    .enable = 0X20
  },

  { /* disable key repeat */
    .feature = 6 /* Key Settings Report */,
    .offset = 1,
    .disable = 0X08
  },

  { /* disable second routing key row emulation */
    .feature = 7 /* CR Key Settings Report */,
    .offset = 1,
    .disable = 0X02
  },

  { .feature = 0 }
};

static const ProtocolOperations protocol2uOperations = {
  .initializeVariables = initializeVariables2,

  .verifyPacket = verifyPacket2u,
  .readPacket = readPacket,

  .requiredSettings = requiredSettings2u,
  .setFeature = setFeature2u,
  .getFeature = getFeature2u,

  .updateConfiguration = updateConfiguration2u,
  .detectModel = detectModel2u,

  .readCommand = readCommand2u,
  .writeBraille = writeBraille2u
};

static BrailleDisplay *brailleDisplay = NULL;

int
AL_writeData (unsigned char *data, int len ) {
  return writeBraillePacket(brailleDisplay, NULL, data, len);
}

static void
setUsbConnectionProperties (
  GioUsbConnectionProperties *properties,
  const UsbChannelDefinition *definition
) {
  model = properties->applicationData;

  if (definition->outputEndpoint) {
    properties->applicationData = &protocol1Operations;
  } else {
    properties->applicationData = &protocol2uOperations;
    properties->writeData = writeData2u;
  }
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 9600
  };

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* Satellite (5nn) */
      .vendor=0X06B0, .product=0X0001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2
    },

    { /* Voyager Protocol Converter */
      .vendor=0X0798, .product=0X0600,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=0,
      .data=&modelVoyager
    },

    { /* BC624 */
      .vendor=0X0798, .product=0X0624,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=0,
      .data=&modelBC624
    },

    { /* BC640 */
      .vendor=0X0798, .product=0X0640,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=0,
      .data=&modelBC640
    },

    { /* BC680 */
      .vendor=0X0798, .product=0X0680,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=0,
      .data=&modelBC680
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;
  descriptor.serial.options.applicationData = &protocol1Operations;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.setConnectionProperties = setUsbConnectionProperties;
  descriptor.usb.options.inputTimeout = 100;

  descriptor.bluetooth.channelNumber = 1;
  descriptor.bluetooth.discoverChannel = 1;
  descriptor.bluetooth.options.applicationData = &protocol2sOperations;
  descriptor.bluetooth.options.inputTimeout = 200;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    protocol = gioGetApplicationData(brl->gioEndpoint);
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));
    brl->data->restore.end = brl->data->restore.buffer;

    if (connectResource(brl, device)) {
      protocol->initializeVariables(brl, parameters);

      brl->data->rotatedCells = 0;
      if (*parameters[PARM_ROTATED_CELLS]) {
        if (!validateYesNo(&brl->data->rotatedCells, parameters[PARM_ROTATED_CELLS])) {
          logMessage(LOG_WARNING, "%s: %s", "invalid rotated cells setting",
                     parameters[PARM_ROTATED_CELLS]);
        }
      }

      if (protocol->detectModel(brl)) {
        if (updateSettings(brl)) {
          setBrailleKeyTable(brl, model->keyTableDefinition);

          if (brl->data->rotatedCells) {
            makeOutputTable(dotsTable_rotated);
          } else {
            makeOutputTable(dotsTable_ISO11548_1);
          }

          brailleDisplay = brl;
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
  brailleDisplay = NULL;
  restoreSettings(brl);
  disconnectBrailleResource(brl, NULL);
  free(brl->data);

  if (previousText) {
    free(previousText);
    previousText = NULL;
  }

  if (previousStatus) {
    free(previousStatus);
    previousStatus = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  unsigned int from;
  unsigned int to;

  if (cellsHaveChanged(previousText, brl->buffer, brl->textColumns, &from, &to, &textRewriteRequired)) {
    if (model->flags & MOD_FLAG_FORCE_FROM_0) from = 0;

    {
      size_t count = to - from;
      unsigned char cells[count];

      translateOutputCells(cells, &brl->buffer[from], count);
      if (!protocol->writeBraille(brl, cells, textOffset+from, count)) return 0;
    }
  }

  return 1;
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *status) {
  size_t cellCount = brl->statusColumns;

  if (cellsHaveChanged(previousStatus, status, cellCount, NULL, NULL, &statusRewriteRequired)) {
    unsigned char cells[cellCount];

    translateOutputCells(cells, status, cellCount);
    if (!protocol->writeBraille(brl, cells, statusOffset, cellCount)) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  return protocol->readCommand(brl);
}
