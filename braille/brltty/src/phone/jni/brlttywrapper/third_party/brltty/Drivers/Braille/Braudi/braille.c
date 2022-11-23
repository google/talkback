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
#include "ascii.h"

#include "brl_driver.h"
#include "io_serial.h"

static SerialDevice *serialDevice = NULL;
static unsigned int charactersPerSecond;
static unsigned char *outputBuffer = NULL;

static int
readBytes (unsigned char *buffer, int size, size_t *length) {
  *length = 0;

  while (*length < size) {
    unsigned char byte;

    if (!serialReadChunk(serialDevice, buffer, length, 1, 0, 100)) {
      return 0;
    }
    byte = buffer[*length - 1];

    if ((*length == 1) && (byte == ACK)) {
      *length = 0;
      continue;
    }

    if (byte == CR) {
      logBytes(LOG_DEBUG, "Read", buffer, *length);
      return 1;
    }
  }

  return 0;
}

static int
writeBytes (BrailleDisplay *brl, const unsigned char *bytes, int count) {
  logBytes(LOG_DEBUG, "Write", bytes, count);
  if (serialWriteData(serialDevice, bytes, count) == -1) return 0;
  brl->writeDelay += (count * 1000 / charactersPerSecond) + 1;
  return 1;
}

static int
writeAcknowledgement (BrailleDisplay *brl) {
  static const unsigned char acknowledgement[] = {ACK};
  return writeBytes(brl, acknowledgement, sizeof(acknowledgement));
}

static int
writeCells (BrailleDisplay *brl) {
  static const unsigned char header[] = {'D'};
  static const unsigned char trailer[] = {CR};
  unsigned char buffer[sizeof(header) + brl->textColumns + sizeof(trailer)];
  unsigned char *byte = buffer;

  byte = mempcpy(byte, header, sizeof(header));
  byte = translateOutputCells(byte, outputBuffer, brl->textColumns);
  byte = mempcpy(byte, trailer, sizeof(trailer));

  return writeBytes(brl, buffer, byte-buffer);
}

static int
writeString (BrailleDisplay *brl, const char *string) {
  return writeBytes(brl, (const unsigned char *)string, strlen(string));
}

static int
skipCharacter (unsigned char character, const unsigned char **bytes, int *count) {
  int found = 0;

  while (*count) {
    if (**bytes != character) break;
    found = 1;
    ++*bytes, --*count;
  }

  return found;
}

static int
interpretNumber (int *number, const unsigned char **bytes, int *count) {
  int ok = skipCharacter('0', bytes, count);
  *number = 0;

  while (*count) {
    static unsigned char digits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    const unsigned char *digit = memchr(digits, **bytes, sizeof(digits));
    if (!digit) break;

    *number = (*number * 10) + (digit - digits);
    ok = 1;
    ++*bytes, --*count;
  }

  return ok;
}

static int
identifyDisplay (BrailleDisplay *brl) {
  static const unsigned char identify[] = {'I', CR};

  if (writeBytes(brl, identify, sizeof(identify))) {
    if (serialAwaitInput(serialDevice, 1000)) {
      unsigned char identity[0X100];
      size_t length;

      if (readBytes(identity, sizeof(identity), &length)) {
        static const unsigned char prefix[] = {'b', 'r', 'a', 'u', 'd', 'i', ' '};
        if ((length >= sizeof(prefix)) &&
            (memcmp(identity, prefix, sizeof(prefix)) == 0)) {
          const unsigned char *bytes = memchr(identity, ',', length);
          if (bytes) {
            int count = length - (bytes - identity);
            int cells;

            ++bytes, --count;
            skipCharacter(' ', &bytes, &count);
            if (interpretNumber(&cells, &bytes, &count)) {
              if (!count) {
                logMessage(LOG_INFO, "Detected: %.*s", (int)length, identity);

                brl->textColumns = cells;
                brl->textRows = 1;

                return 1;
              }
            }
          }
        }

        logUnexpectedPacket(identity, length);
      }
    }
  }
  return 0;
}

static int
setTable (BrailleDisplay *brl, int table) {
  char buffer[0X10];
  snprintf(buffer, sizeof(buffer), "L%d\r", table);
  return writeString(brl, buffer);
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  if ((serialDevice = serialOpenDevice(device))) {
    static const unsigned int baud = 9600;
    charactersPerSecond = baud / 10;
    if (serialRestartDevice(serialDevice, baud)) {
      if (identifyDisplay(brl)) {
        MAKE_OUTPUT_TABLE(0X01, 0X02, 0X04, 0X10, 0X20, 0X40, 0X08, 0X80);
  
        if ((outputBuffer = malloc(brl->textColumns))) {
          if (setTable(brl, 0)) {
            memset(outputBuffer, 0, brl->textColumns);
            writeCells(brl);

            return 1;
          }

          free(outputBuffer);
          outputBuffer = NULL;
        } else {
          logSystemError("Output buffer allocation");
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
  if (outputBuffer) {
    free(outputBuffer);
    outputBuffer = NULL;
  }

  if (serialDevice) {
    serialCloseDevice(serialDevice);
    serialDevice = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (cellsHaveChanged(outputBuffer, brl->buffer, brl->textColumns, NULL, NULL, NULL)) {
    writeCells(brl);
  }
  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char buffer[0X100];
  size_t length;

  while (readBytes(buffer, sizeof(buffer), &length)) {
    const unsigned char *bytes = buffer;
    int count = length;

    if (count > 0) {
      unsigned char category = *bytes++;
      --count;

      switch (category) {
        case 'F': {
          int keys;
          writeAcknowledgement(brl);

          if (interpretNumber(&keys, &bytes, &count)) {
            if (!count) {
              switch (keys) {
                case  1: return BRL_CMD_TOP_LEFT;
                case  2: return BRL_CMD_FWINLT;
                case  3: return BRL_CMD_LNDN;
                case  4: return BRL_CMD_LNUP;
                case  5: return BRL_CMD_FWINRT;
                case  6: return BRL_CMD_BOT_LEFT;
                case 23: return BRL_CMD_LNBEG;
                case 56: return BRL_CMD_LNEND;
                case 14: return BRL_CMD_CSRVIS;
                case 25: return BRL_CMD_DISPMD;
                case 26: return BRL_CMD_INFO;
                case 36: return BRL_CMD_HOME;
              }
            }
          }

          break;
        }

        case 'K': {
          int key;
          writeAcknowledgement(brl);

          if (interpretNumber(&key, &bytes, &count)) {
            if (!count) {
              if ((key > 0) && (key <= brl->textColumns)) return BRL_CMD_BLK(ROUTE) + (key - 1);
            }
          }

          break;
        }
      }
    }

    logUnexpectedPacket(buffer, length);
  }

  if (errno == EAGAIN) return EOF;
  return BRL_CMD_RESTARTBRL;
}
