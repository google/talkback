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

#include <errno.h>
#include <string.h>

#include "io_serial.h"

typedef enum {
  PARM_BAUD
} DriverParameter;
#define BRLPARMS "baud"

#define BRL_STATUS_FIELDS sfGeneric
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"

static const int showOutputMapping = 0;

static SerialDevice *serialDevice = NULL;
static unsigned int serialBaud;
static unsigned int charactersPerSecond;
static const int *initialCommand;

typedef enum {
  IPT_MINIMUM_LINE     =   1,
  IPT_MAXIMUM_LINE     =  25,
  IPT_SEARCH_ATTRIBUTE =  90,
  IPT_CURRENT_LINE     = 100,
  IPT_CURRENT_LOCATION = 101,
} InputPacketType;

typedef union {
  unsigned char bytes[4];

  struct {
    unsigned char type;

    union {
      struct {
        unsigned char line;
        unsigned char column;
        unsigned char attributes;
      } PACKED search;
    } fields;
  } PACKED data;
} InputPacket;

typedef int (*WriteFunction) (BrailleDisplay *brl);
static WriteFunction writeFunction;

static unsigned char statusCells[GSC_COUNT];

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  {
    static const unsigned int baudTable[] = {9600, 19200, 0};
    const char *baudParameter = parameters[PARM_BAUD];

    if (!*baudParameter ||
        !serialValidateBaud(&serialBaud, "baud", baudParameter, baudTable))
      serialBaud = baudTable[0];
  }

  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  if ((serialDevice = serialOpenDevice(device))) {
    if (serialRestartDevice(serialDevice, serialBaud)) {
      charactersPerSecond = serialBaud / 10;
      writeFunction = NULL;

      {
        static const TranslationTable outputTable = {
#define MAP(byte,cell) [cell] = byte
#include "brl-out.h"
#undef MAP
        };
        setOutputTable(outputTable);
      }

      {
        static const int initialCommands[] = {
          BRL_CMD_TUNES | BRL_FLG_TOGGLE_OFF,
          BRL_CMD_CSRTRK | BRL_FLG_TOGGLE_OFF,
          BRL_CMD_CSRVIS | BRL_FLG_TOGGLE_OFF,
          BRL_CMD_ATTRVIS | BRL_FLG_TOGGLE_OFF,
          EOF
        };

        initialCommand = initialCommands;
      }

      brl->textColumns = 80;
      return 1;
    }

    serialCloseDevice(serialDevice);
    serialDevice = NULL;
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  if (serialDevice) {
    serialCloseDevice(serialDevice);
    serialDevice = NULL;
  }
}

static int
writePacket (BrailleDisplay *brl, const unsigned char *packet, size_t size) {
  logOutputPacket(packet, size);
  brl->writeDelay += (size * 1000 / charactersPerSecond) + 1;
  return serialWriteData(serialDevice, packet, size) != -1;
}

static int
writeLine (BrailleDisplay *brl) {
  unsigned char packet[2 + (brl->textColumns * 2)];
  unsigned char *byte = packet;

  *byte++ = statusCells[gscScreenCursorRow];
  *byte++ = statusCells[gscScreenCursorColumn];

  {
    int i;

    for (i=0; i<brl->textColumns; i+=1) {
      *byte++ = translateOutputCell(brl->buffer[i]);
      *byte++ = 0X07;
    }
  }

  if (showOutputMapping) {
    int row = statusCells[gscBrailleWindowRow];

    if (--row < 0X10) {
      int column;

      for (column=0; column<80; column+=1) packet[2+(column*2)] = ' ';

      for (column=0; column<0X10; column+=1) {
        unsigned char *byte = &packet[2 + (column * 8)];
        static const unsigned char hex[] = "0123456789ABCDEF";

        *byte = hex[row];
        byte += 2;

        *byte = hex[column];
        byte += 2;

        *byte = (row << 4) | column;
      }
    }
  }

  return writePacket(brl, packet, byte-packet);
}

static int
writeLocation (BrailleDisplay *brl) {
  unsigned char packet[2];
  unsigned char *byte = packet;

  *byte++ = statusCells[gscScreenCursorRow];
  *byte++ = statusCells[gscScreenCursorColumn];

  return writePacket(brl, packet, byte-packet);
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (writeFunction) {
    int ok = writeFunction(brl);
    writeFunction = NULL;
    if (!ok) return 0;
  }

  return 1;
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *status) {
  memcpy(statusCells, status, GSC_COUNT);
  return 1;
}

static int
readByte (unsigned char *byte, int wait) {
  const int timeout = 100;
  ssize_t result = serialReadData(serialDevice,
                                  byte, sizeof(*byte),
                                  (wait? timeout: 0), timeout);

  if (result > 0) return 1;
  if (result == 0) errno = EAGAIN;
  return 0;
}

static int
readPacket (BrailleDisplay *brl, InputPacket *packet) {
  int length = 1;
  int offset = 0;

  while (1) {
    unsigned char byte;

    {
      int started = offset > 0;
      if (!readByte(&byte, started)) {
        if (started) logPartialPacket(packet->bytes, offset);
        return 0;
      }
    }

    if (!offset) {
      switch (byte) {
        case IPT_CURRENT_LINE:
        case IPT_CURRENT_LOCATION:
          length = 1;
          break;

        case IPT_SEARCH_ATTRIBUTE:
          length = 4;
          break;

        default:
          if ((byte >= IPT_MINIMUM_LINE) && (byte <= IPT_MAXIMUM_LINE)) {
            length = 1;
          } else {
            logIgnoredByte(byte);
            continue;
          }
          break;
      }
    }

    packet->bytes[offset++] = byte;
    if (offset == length) {
      logInputPacket(packet->bytes, offset);
      return length;
    }
  }
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  InputPacket packet;
  int length;

  if (context == KTB_CTX_WAITING) return BRL_CMD_NOOP;
  if (writeFunction) return EOF;
  while (*initialCommand != EOF) enqueueCommand(*initialCommand++);

  while ((length = readPacket(brl, &packet))) {
    if ((packet.data.type >= IPT_MINIMUM_LINE) &&
        (packet.data.type <= IPT_MAXIMUM_LINE)) {
      enqueueCommand(BRL_CMD_BLK(GOTOLINE) | BRL_FLG_MOTION_TOLEFT | (packet.data.type - IPT_MINIMUM_LINE));
      writeFunction = writeLine;
      return EOF;
    }

    switch (packet.data.type) {
      case IPT_SEARCH_ATTRIBUTE:
      case IPT_CURRENT_LINE:
        enqueueCommand(BRL_CMD_HOME);
        enqueueCommand(BRL_CMD_LNBEG);
        writeFunction = writeLine;
        return EOF;

      case IPT_CURRENT_LOCATION:
        writeFunction = writeLocation;
        return EOF;

      default:
        logUnexpectedPacket(&packet, length);
        break;
    }
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
