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

/** EuroBraille/eu_esysiris.c 
 ** Implements the ESYS and IRIS rev >=1.71 protocol 
 ** Made by Yannick PLASSIARD <yan@mistigri.org>
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <errno.h>

#include "log.h"
#include "ascii.h"
#include "brldefs-eu.h"
#include "eu_protocol.h"
#include "eu_protocoldef.h"

#define MAXIMUM_DISPLAY_SIZE 80

#define COMMAND_KEY_ENTRY(k,n) KEY_ENTRY(CommandKeys, CMD, k, n)
#define BRAILLE_KEY_ENTRY(k,n) KEY_ENTRY(BrailleKeys, BRL, k, n)

BEGIN_KEY_NAME_TABLE(linear)
  COMMAND_KEY_ENTRY(L1, "L1"),
  COMMAND_KEY_ENTRY(L2, "L2"),
  COMMAND_KEY_ENTRY(L3, "L3"),
  COMMAND_KEY_ENTRY(L4, "L4"),
  COMMAND_KEY_ENTRY(L5, "L5"),
  COMMAND_KEY_ENTRY(L6, "L6"),
  COMMAND_KEY_ENTRY(L7, "L7"),
  COMMAND_KEY_ENTRY(L8, "L8"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(arrow)
  COMMAND_KEY_ENTRY(Left, "Left"),
  COMMAND_KEY_ENTRY(Right, "Right"),
  COMMAND_KEY_ENTRY(Up, "Up"),
  COMMAND_KEY_ENTRY(Down, "Down"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(switch1)
  COMMAND_KEY_ENTRY(Switch1Left, "Switch1Left"),
  COMMAND_KEY_ENTRY(Switch1Right, "Switch1Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(switch2)
  COMMAND_KEY_ENTRY(Switch2Left, "Switch2Left"),
  COMMAND_KEY_ENTRY(Switch2Right, "Switch2Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(switch3)
  COMMAND_KEY_ENTRY(Switch3Left, "Switch3Left"),
  COMMAND_KEY_ENTRY(Switch3Right, "Switch3Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(switch4)
  COMMAND_KEY_ENTRY(Switch4Left, "Switch4Left"),
  COMMAND_KEY_ENTRY(Switch4Right, "Switch4Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(switch5)
  COMMAND_KEY_ENTRY(Switch5Left, "Switch5Left"),
  COMMAND_KEY_ENTRY(Switch5Right, "Switch5Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(switch6)
  COMMAND_KEY_ENTRY(Switch6Left, "Switch6Left"),
  COMMAND_KEY_ENTRY(Switch6Right, "Switch6Right"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(joystick1)
  COMMAND_KEY_ENTRY(LeftJoystickLeft, "LeftJoystickLeft"),
  COMMAND_KEY_ENTRY(LeftJoystickRight, "LeftJoystickRight"),
  COMMAND_KEY_ENTRY(LeftJoystickUp, "LeftJoystickUp"),
  COMMAND_KEY_ENTRY(LeftJoystickDown, "LeftJoystickDown"),
  COMMAND_KEY_ENTRY(LeftJoystickPress, "LeftJoystickPress"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(joystick2)
  COMMAND_KEY_ENTRY(RightJoystickLeft, "RightJoystickLeft"),
  COMMAND_KEY_ENTRY(RightJoystickRight, "RightJoystickRight"),
  COMMAND_KEY_ENTRY(RightJoystickUp, "RightJoystickUp"),
  COMMAND_KEY_ENTRY(RightJoystickDown, "RightJoystickDown"),
  COMMAND_KEY_ENTRY(RightJoystickPress, "RightJoystickPress"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(keyboard)
  BRAILLE_KEY_ENTRY(Dot1, "Dot1"),
  BRAILLE_KEY_ENTRY(Dot2, "Dot2"),
  BRAILLE_KEY_ENTRY(Dot3, "Dot3"),
  BRAILLE_KEY_ENTRY(Dot4, "Dot4"),
  BRAILLE_KEY_ENTRY(Dot5, "Dot5"),
  BRAILLE_KEY_ENTRY(Dot6, "Dot6"),
  BRAILLE_KEY_ENTRY(Dot7, "Dot7"),
  BRAILLE_KEY_ENTRY(Dot8, "Dot8"),
  BRAILLE_KEY_ENTRY(Backspace, "Backspace"),
  BRAILLE_KEY_ENTRY(Space, "Space"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing)
  KEY_GROUP_ENTRY(EU_GRP_RoutingKeys1, "RoutingKey1"),
  KEY_GROUP_ENTRY(EU_GRP_RoutingKeys2, "RoutingKey2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(iris)
  KEY_NAME_TABLE(linear),
  KEY_NAME_TABLE(arrow),
  KEY_NAME_TABLE(keyboard),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(esys_small)
  KEY_NAME_TABLE(switch1),
  KEY_NAME_TABLE(switch2),
  KEY_NAME_TABLE(joystick1),
  KEY_NAME_TABLE(joystick2),
  KEY_NAME_TABLE(keyboard),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(esys_medium)
  KEY_NAME_TABLE(switch1),
  KEY_NAME_TABLE(switch2),
  KEY_NAME_TABLE(switch3),
  KEY_NAME_TABLE(switch4),
  KEY_NAME_TABLE(joystick1),
  KEY_NAME_TABLE(joystick2),
  KEY_NAME_TABLE(keyboard),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(esys_large)
  KEY_NAME_TABLE(switch1),
  KEY_NAME_TABLE(switch2),
  KEY_NAME_TABLE(switch3),
  KEY_NAME_TABLE(switch4),
  KEY_NAME_TABLE(switch5),
  KEY_NAME_TABLE(switch6),
  KEY_NAME_TABLE(joystick1),
  KEY_NAME_TABLE(joystick2),
  KEY_NAME_TABLE(keyboard),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(esytime)
  KEY_NAME_TABLE(joystick1),
  KEY_NAME_TABLE(joystick2),
  KEY_NAME_TABLE(linear),
  KEY_NAME_TABLE(keyboard),   // For braille keyboard when not in usb-hid mode.
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

PUBLIC_KEY_TABLE(iris)
PUBLIC_KEY_TABLE(esys_small)
PUBLIC_KEY_TABLE(esys_medium)
PUBLIC_KEY_TABLE(esys_large)
PUBLIC_KEY_TABLE(esytime)

typedef struct {
  const char *modelName;
  const KeyTableDefinition *keyTable;
  unsigned char modelIdentifier;
  unsigned char cellCount;
  unsigned hasBrailleKeyboard:1;
  unsigned hasAzertyKeyboard:1;
  unsigned hasVisualDisplay:1;
  unsigned hasOpticalBar:1;
  unsigned isIris:1;
  unsigned isEsys:1;
  unsigned isEsytime:1;
} ModelEntry;

static const ModelEntry modelTable[] = {
  { .modelIdentifier = EU_IRIS_20,
    .modelName = "Iris 20",
    .cellCount = 20,
    .hasBrailleKeyboard = 1,
    .hasVisualDisplay = 1,
    .isIris = 1,
    .keyTable = &KEY_TABLE_DEFINITION(iris)
  },

  { .modelIdentifier = EU_IRIS_40,
    .modelName = "Iris 40",
    .cellCount = 40,
    .hasBrailleKeyboard = 1,
    .hasVisualDisplay = 1,
    .isIris = 1,
    .keyTable = &KEY_TABLE_DEFINITION(iris)
  },

  { .modelIdentifier = EU_IRIS_S20,
    .modelName = "Iris S-20",
    .cellCount = 20,
    .hasBrailleKeyboard = 1,
    .isIris = 1,
    .keyTable = &KEY_TABLE_DEFINITION(iris)
  },

  { .modelIdentifier = EU_IRIS_S32,
    .modelName = "Iris S-32",
    .cellCount = 32,
    .hasBrailleKeyboard = 1,
    .isIris = 1,
    .keyTable = &KEY_TABLE_DEFINITION(iris)
  },

  { .modelIdentifier = EU_IRIS_KB20,
    .modelName = "Iris KB-20",
    .cellCount = 20,
    .hasAzertyKeyboard = 1,
    .isIris = 1,
    .keyTable = &KEY_TABLE_DEFINITION(iris)
  },

  { .modelIdentifier = EU_IRIS_KB40,
    .modelName = "Iris KB-40",
    .cellCount = 40,
    .hasAzertyKeyboard = 1,
    .isIris = 1,
    .keyTable = &KEY_TABLE_DEFINITION(iris)
  },

  { .modelIdentifier = EU_ESYS_12,
    .modelName = "Esys 12",
    .cellCount = 12,
    .hasBrailleKeyboard = 1,
    .isEsys = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esys_small)
  },

  { .modelIdentifier = EU_ESYS_40,
    .modelName = "Esys 40",
    .cellCount = 40,
    .hasBrailleKeyboard = 1,
    .isEsys = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esys_medium)
  },

  { .modelIdentifier = EU_ESYS_LIGHT_40,
    .modelName = "Esys Light 40",
    .cellCount = 40,
    .isEsys = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esys_medium)
  },

  { .modelIdentifier = EU_ESYS_24,
    .modelName = "Esys 24",
    .cellCount = 24,
    .hasBrailleKeyboard = 1,
    .isEsys = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esys_small)
  },

  { .modelIdentifier = EU_ESYS_64,
    .modelName = "Esys 64",
    .cellCount = 64,
    .hasBrailleKeyboard = 1,
    .isEsys = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esys_medium)
  },

  { .modelIdentifier = EU_ESYS_80,
    .modelName = "Esys 80",
    .cellCount = 80,
    .hasBrailleKeyboard = 1,
    .isEsys = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esys_large)
  },

  { .modelIdentifier = EU_ESYS_LIGHT_80,
    .modelName = "Esys Light 80",
    .cellCount = 80,
    .isEsys = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esys_large)
  },

  { .modelIdentifier = EU_ESYTIME_32,
    .modelName = "Esytime 32",
    .cellCount = 32,
    .hasBrailleKeyboard = 1,
    .hasOpticalBar = 1,
    .isEsytime = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esytime)
  },

  { .modelIdentifier = EU_ESYTIME_32_STANDARD,
    .modelName = "Esytime 32 Standard",
    .cellCount = 32,
    .hasBrailleKeyboard = 1,
    .isEsytime = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esytime)
  },

  { .modelIdentifier = EU_ESYTIME_EVO,
    .modelName = "Esytime Evolution",
    .cellCount = 32,
    .hasBrailleKeyboard = 1,
    .hasOpticalBar = 1,
    .isEsytime = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esytime)
  },

  { .modelIdentifier = EU_ESYTIME_EVO_STANDARD,
    .modelName = "Esytime Evolution Standard",
    .cellCount = 32,
    .hasBrailleKeyboard = 1,
    .isEsytime = 1,
    .keyTable = &KEY_TABLE_DEFINITION(esytime)
  },

  { .modelName = NULL }
};

static int haveSystemInformation;
static const ModelEntry *model;
static uint32_t firmwareVersion;
static uint32_t protocolVersion;
static uint32_t deviceOptions;
static uint16_t maximumFrameLength;

static unsigned char forceWindowRewrite;
static unsigned char forceVisualRewrite;
static unsigned char forceCursorRewrite;

static unsigned char sequenceCheck;
static unsigned char sequenceKnown;
static unsigned char sequenceNumber;

static KeyNumberSet commandKeys;

static inline void
forceRewrite (void) {
  forceWindowRewrite = 1;
  forceVisualRewrite = 1;
  forceCursorRewrite = 1;
}

static ssize_t
readPacket (BrailleDisplay *brl, void *packet, size_t size) {
  unsigned char *buffer = packet;
  const unsigned char pad = 0X55;
  unsigned int offset = 0;
  unsigned int length = 3;

  while (1)
    {
      int started = offset > 0;
      unsigned char byte;

      if (!io->readByte(brl, &byte, started))
        {
          if (started) logPartialPacket(buffer, offset);
          return (errno == EAGAIN)? 0: -1;
        }

      switch (offset)
        {
          case 0: {
            unsigned char sequence = sequenceCheck;
            sequenceCheck = 0;

            if (sequence && sequenceKnown) {
              if (byte == ++sequenceNumber) continue;
              logInputProblem("Unexpected Sequence Number", &byte, 1);
              sequenceKnown = 0;
            }

            if (byte == pad) continue;
            if (byte == ASCII_STX) break;

            if (sequence && !sequenceKnown) {
              sequenceNumber = byte;
              sequenceKnown = 1;
            } else {
              logIgnoredByte(byte);
            }

            continue;
          }

          case 1:
            if ((byte == pad) && !sequenceKnown) {
              sequenceNumber = buffer[0];
              sequenceKnown = 1;
              offset = 0;
              continue;
            }
            break;

          case 2:
            length = ((buffer[1] << 8) | byte) + 2;
            break;

          default:
            break;
        }

      if (offset < size)
        {
          buffer[offset] = byte;
        }
      else
        {
          if (offset == length) logTruncatedPacket(buffer, offset);
          logDiscardedByte(byte);
        }

      if (++offset == length)
        {
          if (byte != ASCII_ETX)
            {
              logCorruptPacket(buffer, offset);
              offset = 0;
              length = 3;
              continue;
            }

          sequenceCheck = 1;
          logInputPacket(buffer, offset);
          return offset;
        }
    }
}

static ssize_t
writePacket (BrailleDisplay *brl, const void *packet, size_t size) {
  int packetSize = size + 2;
  unsigned char buf[packetSize + 2];
  if (!io || !packet || !size)
    return (-1);
  buf[0] = ASCII_STX;
  buf[1] = (packetSize >> 8) & 0x00FF;
  buf[2] = packetSize & 0x00FF;
  memcpy(buf + 3, packet, size);
  buf[sizeof(buf)-1] = ASCII_ETX;
  logOutputPacket(buf, sizeof(buf));
  return io->writeData(brl, buf, sizeof(buf));
}

static const ModelEntry *
getModelEntry (unsigned char identifier) {
  const ModelEntry *mdl = modelTable;

  while (mdl->modelName) {
    if (mdl->modelIdentifier == identifier) return mdl;
    mdl += 1;
  }

  return NULL;
}

static int
handleSystemInformation (BrailleDisplay *brl, unsigned char *packet) {
  int logLevel = LOG_INFO;
  const char *infoDescription;
  enum {Unknown, End, String, Dec8, Dec16, Hex32} infoType;

  switch(packet[0]) {
    case LP_SYSTEM_SHORTNAME: 
      infoType = String;
      infoDescription = "Short Name";
      break;

    case LP_SYSTEM_IDENTITY: 
      infoType = End;
      break;

    case LP_SYSTEM_DISPLAY_LENGTH: 
      if (haveSystemInformation) brl->resizeRequired = 1;
      brl->textColumns = packet[1];

      infoType = Dec8;
      infoDescription = "Cell Count";
      break;

    case LP_SYSTEM_LANGUAGE: 
      infoType = String;
      infoDescription = "Country Code";
      break;

    case LP_SYSTEM_FRAME_LENGTH: 
      maximumFrameLength = (packet[1] << 8)
                         | (packet[2] << 0)
                         ;

      infoType = Dec16;
      infoDescription = "Maximum Frame Length";
      break;

    case LP_SYSTEM_NAME: 
      infoType = String;
      infoDescription = "Long Name";
      break;

    case LP_SYSTEM_OPTION: 
      deviceOptions = (packet[1] << 24)
                    | (packet[2] << 16)
                    | (packet[3] <<  8)
                    | (packet[4] <<  0)
                    ;

      infoType = Hex32;
      infoDescription = "Device Options";
      break;

    case LP_SYSTEM_PROTOCOL: 
      protocolVersion = ((packet[1] - '0') << 16)
                      | ((packet[3] - '0') <<  8)
                      | ((packet[4] - '0') <<  0)
                      ;

      infoType = String;
      infoDescription = "Protocol Version";
      break;

    case LP_SYSTEM_SERIAL: 
      infoType = String;
      infoDescription = "Serial Number";
      break;

    case LP_SYSTEM_TYPE:
      {
        unsigned char identifier = packet[1];

        if (!(model = getModelEntry(identifier))) {
          logMessage(LOG_WARNING, "unknown EuroBraille model: 0X%02X", identifier);
        }
      }

      infoType = Dec8;
      infoDescription = "Model Identifier";
      break;

    case LP_SYSTEM_SOFTWARE: 
      firmwareVersion = ((packet[1] - '0') << 16)
                      | ((packet[3] - '0') <<  8)
                      | ((packet[4] - '0') <<  0)
                      ;

      infoType = String;
      infoDescription = "Firmware Version";
      break;

    default:
      infoType = Unknown;
      break;
  }

  switch (infoType) {
    case Unknown:
      logMessage(LOG_WARNING, "unknown Esysiris system information subcode: 0X%02X", packet[0]);
      break;

    case End:
      logMessage(LOG_DEBUG, "end of Esysiris system information");
      return 1;

    case String:
      logMessage(logLevel, "Esysiris %s: %s", infoDescription, &packet[1]);
      break;

    case Dec8:
      logMessage(logLevel, "Esysiris %s: %u", infoDescription, packet[1]);
      break;

    case Dec16:
      logMessage(logLevel, "Esysiris %s: %u", infoDescription, (packet[1] << 8) | packet[2]);
      break;

    case Hex32:
      logMessage(logLevel, "Esysiris %s: 0X%02X%02X%02X%02X",
                 infoDescription, packet[1], packet[2], packet[3], packet[4]);
      break;

    default:
      logMessage(LOG_WARNING, "unimplemented Esysiris system information subcode type: 0X%02X", infoType);
      break;
  }

  return 0;
}

static int
makeKeyboardCommand (BrailleDisplay *brl, const unsigned char *packet) {
  unsigned char a = packet[1];
  unsigned char b = packet[2];
  unsigned char c = packet[3];
  unsigned char d = packet[4];
  int command = 0;

  switch (a) {
    case 0:
      switch (b) {
        case 0:
          command = BRL_CMD_BLK(PASSCHAR) | d;
          break;

        case ASCII_BS:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_BACKSPACE;
          break;

        case ASCII_HT:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_TAB;
          break;

        case ASCII_CR:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_ENTER;
          break;

        case ASCII_ESC:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_ESCAPE;
          break;

        case 0X20: // space
          command = BRL_CMD_BLK(PASSCHAR) | b;
          break;

        default:
          if ((b >= 0X70) && (b <= 0X7B)) {
            command = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + (b - 0X70));
          }
          break;
      }
      break;

    case 1:
      switch (b) {
        case 0X07:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_HOME;
          break;

        case 0X08:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_END;
          break;

        case 0X09:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_PAGE_UP;
          break;

        case 0X0A:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_PAGE_DOWN;
          break;

        case 0X0B:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_LEFT;
          break;

        case 0X0C:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_RIGHT;
          break;

        case 0X0D:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_UP;
          break;

        case 0X0E:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_DOWN;
          break;

        case 0X0F:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_INSERT;
          break;

        case 0X10:
          command = BRL_CMD_BLK(PASSKEY) | BRL_KEY_DELETE;
          break;

        default:
          break;
      }
      break;

    default:
      break;
  }

  if (!command) return BRL_CMD_NOOP;

  if (c & 0X02) command |= BRL_FLG_INPUT_CONTROL;
  if (c & 0X04) command |= BRL_FLG_INPUT_META;
  return command;
}

static int
handleKeyEvent (BrailleDisplay *brl, unsigned char *packet) {
  switch (packet[0]) {
    case LP_KEY_BRAILLE: {
      KeyNumberSet keys = ((packet[1] << 8) | packet[2]) & 0X3Ff;
      enqueueKeys(brl, keys, EU_GRP_BrailleKeys, 0);
      return 1;
    }

    case LP_KEY_INTERACTIVE: {
      unsigned char key = packet[2];

      if ((key > 0) && (key <= brl->textColumns)) {
        key -= 1;

        switch (packet[1]) {
          case INTERACTIVE_SINGLE_CLIC: // single click
            enqueueKey(brl, EU_GRP_RoutingKeys1, key);
          case INTERACTIVE_REPETITION: // repeat
            return 1;

          case INTERACTIVE_DOUBLE_CLIC: // double click
            enqueueKey(brl, EU_GRP_RoutingKeys2, key);
            return 1;

          default:
            break;
        }
      }

      break;
    }

    case LP_KEY_COMMAND: {
      KeyNumberSet keys;

      if (model->isIris) {
        keys = ((packet[1] << 8) | packet[2]) & 0XFFF;
      } else {
        keys = (packet[1] << 24) + (packet[2] << 16) + (packet[3] << 8) + packet[4];
      }

      if (model->isIris) {
        enqueueKeys(brl, keys, EU_GRP_CommandKeys, 0);
      } else {
        enqueueUpdatedKeys(brl, keys, &commandKeys, EU_GRP_CommandKeys, 0);
      }

      return 1;
    }

    case LP_KEY_PC: {
      int command = makeKeyboardCommand(brl, packet);

      enqueueCommand(command);
      if (command != BRL_CMD_NOOP) return 1;
      break;
    }

    default:
      break;
  }

  return 0;
}

static int
readCommand (BrailleDisplay *brl, KeyTableCommandContext ctx) {
  unsigned char	packet[2048];
  ssize_t length;

  while ((length = readPacket(brl, packet, sizeof(packet))) > 0) {
    switch (packet[3]) {
      case LP_SYSTEM:
        if (handleSystemInformation(brl, packet+4)) haveSystemInformation = 1;
        continue;

      case LP_KEY:
        if (handleKeyEvent(brl, packet+4)) continue;
        break;

      case LP_MODE:
        if (packet[4] == LP_MODE_PILOT) {
          /* return from internal menu */
          forceRewrite();
        }
        continue;

      case LP_VISU:
        /* ignore visualization */
        continue;

      default:
        break;
    }

    logUnexpectedPacket(packet, length);
  }

  return (length == -1)? BRL_CMD_RESTARTBRL: EOF;
}

static int
initializeDevice (BrailleDisplay *brl) {
  int retriesLeft = 2;
      
  haveSystemInformation = 0;
  model = NULL;
  firmwareVersion = 0;
  protocolVersion = 0;
  deviceOptions = 0;
  maximumFrameLength = 0;

  forceRewrite();
  sequenceCheck = 0;
  sequenceKnown = 0;

  commandKeys = 0;

  do {
    {
      static const unsigned char packet[] = {LP_SYSTEM, LP_SYSTEM_IDENTITY};
      if (writePacket(brl, packet, sizeof(packet)) == -1) return 0;
    }

    while (io->awaitInput(brl, 500)) {
      if (readCommand(brl, KTB_CTX_DEFAULT) == BRL_CMD_RESTARTBRL) return 0;

      if (haveSystemInformation) {
        if (!model) return 0;
        setBrailleKeyTable(brl, model->keyTable);

        if (!maximumFrameLength) {
          if (model->isIris) maximumFrameLength = 2048;
          if (model->isEsys) maximumFrameLength = 128;
          if (model->isEsytime) maximumFrameLength = 512;
        }

        logMessage(LOG_INFO, "Model Detected: %s (%u cells)",
                   model->modelName, brl->textColumns);
        return 1;
      }
    }
  } while (retriesLeft-- && (errno == EAGAIN));

  return 0;
}

static int
resetDevice (BrailleDisplay *brl) {
  return 0;
}

static int
writeWindow (BrailleDisplay *brl) {
  static unsigned char previousCells[MAXIMUM_DISPLAY_SIZE];
  unsigned int size = brl->textColumns * brl->textRows;
  
  if (cellsHaveChanged(previousCells, brl->buffer, size, NULL, NULL, &forceWindowRewrite)) {
    unsigned char data[size + 2];
    unsigned char *byte = data;

    *byte++ = LP_BRAILLE_DISPLAY;
    *byte++ = LP_BRAILLE_DISPLAY_STATIC;
    byte = translateOutputCells(byte, brl->buffer, size);

    if (writePacket(brl, data, byte-data) == -1) return 0;
  }

  return 1;
}

static int
hasVisualDisplay (BrailleDisplay *brl) {
  return model->hasVisualDisplay;
}

static int
writeVisual (BrailleDisplay *brl, const wchar_t *text) {
  if (model->hasVisualDisplay) {
    {
      static wchar_t previousText[MAXIMUM_DISPLAY_SIZE];
      unsigned int size = brl->textColumns * brl->textRows;
      
      if (textHasChanged(previousText, text, size, NULL, NULL, &forceVisualRewrite)) {
        unsigned char data[size + 2];
        unsigned char *byte = data;

        *byte++ = LP_LCD_DISPLAY;
        *byte++ = LP_LCD_DISPLAY_TEXT;

        {
          const wchar_t *character = text;
          const wchar_t *end = character + size;

          while (character < end) {
            *byte++ = iswLatin1(*character)? *character: '?';
            character += 1;
          }
        }

        if (writePacket(brl, data, byte-data) == -1) return 0;
      }
    }

    {
      static int previousCursor;

      if (cursorHasChanged(&previousCursor, brl->cursor, &forceCursorRewrite )) {
        const unsigned char packet[] = {
          LP_LCD_DISPLAY, LP_LCD_DISPLAY_CARET, ((brl->cursor != BRL_NO_CURSOR)? (brl->cursor + 1): 0)
        };

        if (writePacket(brl, packet, sizeof(packet)) == -1) return 0;
      }
    }
  }

  return 1;
}

const ProtocolOperations esysirisProtocolOperations = {
  .protocolName = "esysiris",

  .initializeDevice = initializeDevice,
  .resetDevice = resetDevice,

  .readPacket = readPacket,
  .writePacket = writePacket,

  .readCommand = readCommand,
  .writeWindow = writeWindow,

  .hasVisualDisplay = hasVisualDisplay,
  .writeVisual = writeVisual
};
