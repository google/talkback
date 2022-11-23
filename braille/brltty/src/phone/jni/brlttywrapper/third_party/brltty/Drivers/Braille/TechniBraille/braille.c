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

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <errno.h>

#include "log.h"

#include "brl_driver.h"

static unsigned char brailleCells[0XFF];
static wchar_t visualText[0XFF];

#include "io_serial.h"
static SerialDevice *serialDevice = NULL;
static unsigned int charactersPerSecond;

static int
readPacket (BrailleDisplay *brl, unsigned char *packet, int length) {
  size_t offset = 0;
  int size = -1;

  while (offset < length) {
    const unsigned char *byte = &packet[offset];

    if (!serialReadChunk(serialDevice, packet, &offset, 1, 0, 1000)) {
      if (errno == EAGAIN) {
        if (!offset) return 0;
        logPartialPacket(packet, offset);
      }
      return -1;
    }

    if (offset == 1) {
      if (*byte) {
        logDiscardedByte(packet[0]);
        offset = 0;
      }
    } else {
      if (offset == 2) {
        switch (*byte) {
          default:
            size = 1;
            break;
        }
        size += offset;
      }

      if (offset == size) {
        logInputPacket(packet, offset);
        return offset;
      }
    }
  }

  logTruncatedPacket(packet, offset);
  return 0;
}

static int
writePacket (BrailleDisplay *brl, unsigned char function, unsigned char *data, unsigned char count) {
  unsigned char buffer[count + 4];
  unsigned char *byte = buffer;

  *byte++ = 0;
  *byte++ = function;
  *byte++ = count;
  byte = mempcpy(byte, data, count);

  {
    unsigned char checksum = 0;
    const unsigned char *ptr = buffer;
    while (ptr < byte) checksum ^= *ptr++;
    *byte++ = checksum;
  }

  {
    int size = byte - buffer;
    logOutputPacket(buffer, size);
    brl->writeDelay += (count * 1000 / charactersPerSecond) + 1;
    if (serialWriteData(serialDevice, buffer, size) != -1) return 1;
  }

  logSystemError("serial write");
  return 0;
}

static int
writeBrailleCells (BrailleDisplay *brl) {
  size_t count = brl->textColumns;
  unsigned char cells[count];

  translateOutputCells(cells, brailleCells, count);
  return writePacket(brl, 1, cells, count);
}

static int
clearBrailleCells (BrailleDisplay *brl) {
  memset(brailleCells, 0, brl->textColumns);
  return writeBrailleCells(brl);
}

static int
writeVisualText (BrailleDisplay *brl) {
  unsigned char bytes[brl->textColumns];
  int i;

  for (i=0; i<brl->textColumns; ++i) {
    wchar_t character = visualText[i];
    bytes[i] = iswLatin1(character)? character: '?';
  }

  return writePacket(brl, 2, bytes, brl->textColumns);
}

static int
clearVisualText (BrailleDisplay *brl) {
  wmemset(visualText, WC_C(' '), brl->textColumns);
  return writeVisualText(brl);
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  if ((serialDevice = serialOpenDevice(device))) {
    unsigned int baud = 19200;
    charactersPerSecond = baud / 11;

    if (serialRestartDevice(serialDevice, baud)) {
      if (serialSetParity(serialDevice, SERIAL_PARITY_EVEN)) {
        if (writePacket(brl, 4, NULL, 0)) {
          while (serialAwaitInput(serialDevice, 500)) {
            unsigned char response[3];
            int size = readPacket(brl, response, sizeof(response));
            if (size <= 0) break;

            if (response[1] == 4) {
              brl->textColumns = response[2];
              brl->textRows = 1;

              makeOutputTable(dotsTable_ISO11548_1);
              makeInputTable();

              if (!clearBrailleCells(brl)) break;
              if (!clearVisualText(brl)) break;
              if (!writeBrailleCells(brl)) break;

              return 1;
            }
          }
        }
      }
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
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (text) {
    if (wmemcmp(text, visualText, brl->textColumns) != 0) {
      wmemcpy(visualText, text, brl->textColumns);
      if (!writeVisualText(brl)) return 0;
    }
  }

  if (cellsHaveChanged(brailleCells, brl->buffer, brl->textColumns, NULL, NULL, NULL)) {
    if (!writeBrailleCells(brl)) return 0;
  }
  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  while (1) {
    unsigned char packet[3];
    int size = readPacket(brl, packet, sizeof(packet));
    if (size == 0) break;
    if (size < 0) return BRL_CMD_RESTARTBRL;

    switch (packet[1]) {
      default:
        break;

      case 1:
        return BRL_CMD_BLK(PASSDOTS) | translateInputCell(packet[2]);

      case 2: {
        unsigned char column = packet[2];
        if (column && (column <= brl->textColumns)) return BRL_CMD_BLK(ROUTE) + (column - 1);
        break;
      }

      case 3:
        switch (packet[2]) {
          default:
            break;

          // left rear: two columns, one row
          case 0X02: // ESC
            return BRL_CMD_LEARN;
          case 0X01: // M
            return BRL_CMD_PREFMENU;

          // left middle: cross
          case 0X06: // up
            return BRL_CMD_LNUP;
          case 0X03: // left
            return BRL_CMD_FWINLT;
          case 0X05: // right
            return BRL_CMD_FWINRT;
          case 0X04: // down
            return BRL_CMD_LNDN;

          // left front: two columns, three rows
          case 0X09: // ins
            return BRL_CMD_RETURN;
          case 0X0A: // E
            return BRL_CMD_TOP;
          case 0X0B: // supp
            return BRL_CMD_CSRTRK;
          case 0X0C: // L
            return BRL_CMD_BOT;
          case 0X07: // extra 1 (40s only)
            return BRL_CMD_CHRLT;
          case 0X08: // extra 2 (40s only)
            return BRL_CMD_CHRRT;

          case 0x0E: // left thumb
            return BRL_CMD_KEY(BACKSPACE);
          case 0x0F: // right thumb
            return BRL_CMD_BLK(PASSDOTS);
          case 0x3F: // both thumbs
            return BRL_CMD_KEY(ENTER);

          case 0X29: // key under dot 7
            return BRL_CMD_KEY(ESCAPE);
          case 0X2A: // key under dot 8
            return BRL_CMD_KEY(TAB);

          // right rear: one column, one row
          case 0X19: // extra 3 (40s only)
            return BRL_CMD_INFO;

          // right middle: one column, two rows
          case 0X1B: // extra 4 (40s only)
            return BRL_CMD_PRDIFLN;
          case 0X1A: // extra 5 (40s only)
            return BRL_CMD_NXDIFLN;

          // right front: one column, four rows
          case 0X2B: // slash (40s only)
            return BRL_CMD_FREEZE;
          case 0X2C: // asterisk (40s only)
            return BRL_CMD_DISPMD;
          case 0X2D: // minus (40s only)
            return BRL_CMD_ATTRVIS;
          case 0X2E: // plus (40s only)
            return BRL_CMD_CSRVIS;

          // first (top) row of numeric pad
          case 0X37: // seven (40s only)
            return BRL_CMD_KEY(HOME);
          case 0X38: // eight (40s only)
            return BRL_CMD_KEY(CURSOR_UP);
          case 0X39: // nine (40s only)
            return BRL_CMD_KEY(PAGE_UP);

          // second row of numeric pad
          case 0X34: // four (40s only)
            return BRL_CMD_KEY(CURSOR_LEFT);
          case 0X35: // five (40s only)
            return BRL_CMD_CSRJMP_VERT;
          case 0X36: // six (40s only)
            return BRL_CMD_KEY(CURSOR_RIGHT);

          // third row of numeric pad
          case 0X31: // one (40s only)
            return BRL_CMD_KEY(END);
          case 0X32: // two (40s only)
            return BRL_CMD_KEY(CURSOR_DOWN);
          case 0X33: // three (40s only)
            return BRL_CMD_KEY(PAGE_DOWN);

          // fourth (bottom) row of numeric pad
          case 0X28: // verr num (40s only)
            return BRL_CMD_SIXDOTS;
          case 0X30: // zero (40s only)
            return BRL_CMD_KEY(INSERT);
          case 0X2F: // supp (40s only)
            return BRL_CMD_KEY(DELETE);
        }
        break;

      /* When data is written to the display it acknowledges with:
       * 0X00 0X04 0Xxx
       * where xx is the number of bytes written.
       */
      case 4:
        continue;
    }

    logUnexpectedPacket(packet, size);
  }

  return EOF;
}
