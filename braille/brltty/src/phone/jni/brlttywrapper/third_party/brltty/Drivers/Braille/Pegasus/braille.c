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

#include <string.h>
#include <errno.h>

#include "parse.h"

//#define BRL_STATUS_FIELDS sf...
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "brldefs-pg.h"

static const char productPrefix[] = "PBC";
static const unsigned char productPrefixLength = sizeof(productPrefix) - 1;

static int rewriteRequired;
static unsigned char textCells[80];
static unsigned char statusCells[2];

BEGIN_KEY_NAME_TABLE(all)
  KEY_NAME_ENTRY(PG_KEY_LeftShift, "LeftShift"),
  KEY_NAME_ENTRY(PG_KEY_RightShift, "RightShift"),
  KEY_NAME_ENTRY(PG_KEY_LeftControl, "LeftControl"),
  KEY_NAME_ENTRY(PG_KEY_RighTControl, "RighTControl"),

  KEY_NAME_ENTRY(PG_KEY_Left, "Left"),
  KEY_NAME_ENTRY(PG_KEY_Right, "Right"),
  KEY_NAME_ENTRY(PG_KEY_Up, "Up"),
  KEY_NAME_ENTRY(PG_KEY_Down, "Down"),

  KEY_NAME_ENTRY(PG_KEY_Home, "Home"),
  KEY_NAME_ENTRY(PG_KEY_End, "End"),
  KEY_NAME_ENTRY(PG_KEY_Enter, "Enter"),
  KEY_NAME_ENTRY(PG_KEY_Escape, "Escape"),

  KEY_GROUP_ENTRY(PG_GRP_RoutingKeys, "RoutingKey"),
  KEY_NAME_ENTRY(PG_KEY_Status+0, "Status1"),
  KEY_NAME_ENTRY(PG_KEY_Status+1, "Status2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(all)
  KEY_NAME_TABLE(all),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(all)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(all),
END_KEY_TABLE_LIST

typedef struct {
  int (*identifyModel) (BrailleDisplay *brl);
  int (*writeCells) (BrailleDisplay *brl, const unsigned char *cells, unsigned int count);
} InputOutputMethods;

typedef struct {
  int (*openPort) (const char *device);
  void (*closePort) ();
  int (*awaitInput) (int milliseconds);
  int (*readBytes) (unsigned char *buffer, int length, int wait);
  int (*writeBytes) (const unsigned char *buffer, int length);
  const InputOutputMethods *methods;
} InputOutputOperations;
static const InputOutputOperations *io;

typedef enum {
  IPT_KEY_NAVIGATION = 0X13,
  IPT_KEY_SIMULATION = 0XFE,
  IPT_KEY_ROUTING    = 0XFF
} InputPacketType;

typedef union {
  unsigned char bytes[1];

  char product[44 + 1];

  struct {
    unsigned char type;

    union {
      struct {
        unsigned char type;
        unsigned char value;
        unsigned char release;
      } PACKED key;
    } fields;
  } PACKED data;
} PACKED InputPacket;

static void
setCellCounts (BrailleDisplay *brl, int size) {
  brl->statusColumns = ARRAY_COUNT(statusCells);
  brl->statusRows = 1;
  brl->textColumns = size - brl->statusColumns;
  brl->textRows = 1;

  setBrailleKeyTable(brl, &KEY_TABLE_DEFINITION(all));
}

static int
getCellCounts (BrailleDisplay *brl, char *product) {
  unsigned int length = strlen(product);

  {
    static const unsigned char indexes[] = {3, 42, 0};
    const unsigned char *index = indexes;

    while (*index) {
      if (*index < length) {
        unsigned char size = product[*index];
        static const unsigned char sizes[] = {22, 29, 42, 82};

        if (memchr(sizes, size, sizeof(sizes))) {
          setCellCounts(brl, size);
          return 1;
        }
      }

      index += 1;
    }
  }

  {
    static const char delimiters[] = " ";
    char *word;

    if ((word = strtok(product, delimiters))) {
      if (strncmp(word, productPrefix, productPrefixLength) == 0) {
        if ((word = strtok(NULL, delimiters))) {
          int size;

          if (!(*word && isInteger(&size, word))) size = 0;
          while (strtok(NULL, delimiters));

          if ((size > ARRAY_COUNT(statusCells)) &&
              (size <= (ARRAY_COUNT(statusCells) + ARRAY_COUNT(textCells)))) {
            setCellCounts(brl, size);
            return 1;
          }
        }
      }
    }
  }

  return 0;
}

static int
readByte (unsigned char *byte, int wait) {
  int count = io->readBytes(byte, 1, wait);
  if (count > 0) return 1;

  if (count == 0) errno = EAGAIN;
  return 0;
}

static int
readPacket (BrailleDisplay *brl, InputPacket *packet) {
  typedef enum {
    IPG_PRODUCT,
    IPG_KEY,
    IPG_DEFAULT
  } InputPacketGroup;
  InputPacketGroup group = IPG_DEFAULT;

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

  gotByte:
    if (!offset) {
      switch (byte) {
        case IPT_KEY_NAVIGATION:
        case IPT_KEY_SIMULATION:
        case IPT_KEY_ROUTING:
          group = IPG_KEY;
          length = 4;
          break;

        default:
          if (byte == productPrefix[0]) {
            group = IPG_PRODUCT;
            length = sizeof(packet->product) - 1;
          } else {
            logIgnoredByte(byte);
            continue;
          }
          break;
      }
    } else {
      int unexpected = 0;

      switch (group) {
        case IPG_PRODUCT:
          if (offset < productPrefixLength) {
            if (byte != productPrefix[offset]) unexpected = 1;
          } else if (byte == '@') {
            length = offset + 1;
          }
          break;

        case IPG_KEY:
          if (offset == 1) {
            if (byte != packet->bytes[0]) unexpected = 1;
          } else if (offset == 3) {
            if (byte != 0X19) unexpected = 1;
          }
          break;

        default:
          break;
      }

      if (unexpected) {
        logShortPacket(packet->bytes, offset);
        group = IPG_DEFAULT;
        offset = 0;
        length = 1;
        goto gotByte;
      }
    }

    packet->bytes[offset++] = byte;
    if (offset == length) {
      if (group == IPG_PRODUCT) {
        packet->bytes[length] = 0;
      }

      logInputPacket(packet->bytes, offset);
      return length;
    }
  }
}

static int
writeBytes (BrailleDisplay *brl, const unsigned char *buffer, int count) {
  logOutputPacket(buffer, count);
  if (io->writeBytes(buffer, count) != -1) return 1;
  return 0;
}

static int
writeCells (BrailleDisplay *brl) {
  unsigned int textCount = brl->textColumns;
  unsigned int statusCount = brl->statusColumns;
  unsigned char cells[textCount + statusCount];
  unsigned char *cell = cells;

  while (textCount) *cell++ = translateOutputCell(textCells[--textCount]);
  while (statusCount) *cell++ = translateOutputCell(statusCells[--statusCount]);

  return io->methods->writeCells(brl, cells, cell-cells);
}

static void
updateCells (unsigned char *target, const unsigned char *source, unsigned int count) {
  if (cellsHaveChanged(target, source, count, NULL, NULL, NULL)) {
    rewriteRequired = 1;
  }
}

/* Serial IO */
#include "io_serial.h"

static SerialDevice *serialDevice = NULL;
#define SERIAL_BAUD 9600

static int
openSerialPort (const char *device) {
  if ((serialDevice = serialOpenDevice(device))) {
    if (serialRestartDevice(serialDevice, SERIAL_BAUD))
      if (serialSetFlowControl(serialDevice, SERIAL_FLOW_HARDWARE))
        return 1;

    serialCloseDevice(serialDevice);
    serialDevice = NULL;
  }

  return 0;
}

static void
closeSerialPort (void) {
  if (serialDevice) {
    serialCloseDevice(serialDevice);
    serialDevice = NULL;
  }
}

static int
awaitSerialInput (int milliseconds) {
  return serialAwaitInput(serialDevice, milliseconds);
}

static int
readSerialBytes (unsigned char *buffer, int count, int wait) {
  const int timeout = 100;
  return serialReadData(serialDevice, buffer, count,
                        (wait? timeout: 0), timeout);
}

static int
writeSerialBytes (const unsigned char *buffer, int length) {
  return serialWriteData(serialDevice, buffer, length);
}

static int
identifySerialModel (BrailleDisplay *brl) {
  static const unsigned char request[] = {0X40, 0X50, 0X53};

  if (writeBytes(brl, request, sizeof(request))) {
    while (io->awaitInput(1000)) {
      InputPacket response;

      while (readPacket(brl, &response)) {
        if (response.data.type == productPrefix[0]) {
          if (getCellCounts(brl, response.product)) {
            return 1;
          }
        }
      }
    }
  }

  return 0;
}

static int
writeSerialCells (BrailleDisplay *brl, const unsigned char *cells, unsigned int count) {
  static const unsigned char header[] = {0X40, 0X50, 0X4F};
  static const unsigned char trailer[] = {0X18, 0X20, 0X20};

  unsigned char buffer[sizeof(header) + count + sizeof(trailer)];
  unsigned char *byte = buffer;

  byte = mempcpy(byte, header, sizeof(header));
  byte = mempcpy(byte, cells, count);
  byte = mempcpy(byte, trailer, sizeof(trailer));

  return writeBytes(brl, buffer, byte-buffer);
}

static const InputOutputMethods serialMethods = {
  identifySerialModel, writeSerialCells
};

static const InputOutputOperations serialOperations = {
  openSerialPort, closeSerialPort,
  awaitSerialInput, readSerialBytes, writeSerialBytes,
  &serialMethods
};

/* USB IO */
#include "io_usb.h"

static UsbChannel *usbChannel = NULL;

static int
openUsbPort (const char *device) {
  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* all models */
      .vendor=0X4242, .product=0X0001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2
    },
  END_USB_CHANNEL_DEFINITIONS

  if ((usbChannel = usbOpenChannel(usbChannelDefinitions, (void *)device))) {
    return 1;
  }
  return 0;
}

static void
closeUsbPort (void) {
  if (usbChannel) {
    usbCloseChannel(usbChannel);
    usbChannel = NULL;
  }
}

static int
awaitUsbInput (int milliseconds) {
  return usbAwaitInput(usbChannel->device,
                       usbChannel->definition->inputEndpoint,
                       milliseconds);
}

static int
readUsbBytes (unsigned char *buffer, int length, int wait) {
  const int timeout = 100;
  int count = usbReadData(usbChannel->device,
                          usbChannel->definition->inputEndpoint,
                          buffer, length,
                          (wait? timeout: 0), timeout);

  if (count != -1) return count;
  if (errno == EAGAIN) return 0;
  return -1;
}

static int
writeUsbBytes (const unsigned char *buffer, int length) {
  return usbWriteEndpoint(usbChannel->device,
                          usbChannel->definition->outputEndpoint,
                          buffer, length, 1000);
}

static int
identifyUsbModel (BrailleDisplay *brl) {
  int ok = 0;
  char *product;

  if ((product = usbGetProduct(usbChannel->device, 1000))) {
    if (getCellCounts(brl, product)) {
      ok = 1;
    }

    free(product);
  }

  return ok;
}

static int
writeUsbCells (BrailleDisplay *brl, const unsigned char *cells, unsigned int count) {
  unsigned char buffer[1 + count];
  unsigned char *byte = buffer;

  *byte++ = 0X43;
  byte = mempcpy(byte, cells, count);

  return writeBytes(brl, buffer, byte-buffer);
}

static const InputOutputMethods usbMethods = {
  identifyUsbModel, writeUsbCells
};

static const InputOutputOperations usbOperations = {
  openUsbPort, closeUsbPort,
  awaitUsbInput, readUsbBytes, writeUsbBytes,
  &usbMethods
};

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if (isSerialDeviceIdentifier(&device)) {
    io = &serialOperations;
  } else if (isUsbDeviceIdentifier(&device)) {
    io = &usbOperations;
  } else {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  if (io->openPort(device)) {
    if (io->methods->identifyModel(brl)) {
      makeOutputTable(dotsTable_ISO11548_1);
  
      rewriteRequired = 1;
      memset(textCells, 0, sizeof(textCells));
      memset(statusCells, 0, sizeof(statusCells));

      return 1;
    }

    io->closePort();
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  io->closePort();
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  updateCells(textCells, brl->buffer, brl->textColumns);

  if (rewriteRequired) {
    if (!writeCells(brl)) return 0;
    rewriteRequired = 0;
  }

  return 1;
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *cells) {
  updateCells(statusCells, cells, brl->statusColumns);
  return 1;
}

static int
enqueueNavigationKey (BrailleDisplay *brl, KeyNumber modifier, KeyNumber key) {
  const KeyGroup group = PG_GRP_NavigationKeys;
  const int modifierSpecified = modifier != PG_KEY_None;

  if (modifierSpecified && !enqueueKeyEvent(brl, group, modifier, 1)) return 0;
  if (!enqueueKey(brl, group, key)) return 0;
  if (modifierSpecified && !enqueueKeyEvent(brl, group, modifier, 0)) return 0;
  return 1;
}

static int
interpretNavigationKey (BrailleDisplay *brl, unsigned char key) {
#define KEY(code,modifier,key) case (code): return enqueueNavigationKey(brl, (modifier), (key))
  switch (key) {
    KEY(0X15, PG_KEY_None, PG_KEY_Left);
    KEY(0X4D, PG_KEY_None, PG_KEY_Right);
    KEY(0X3D, PG_KEY_None, PG_KEY_Up);
    KEY(0X54, PG_KEY_None, PG_KEY_Down);

    KEY(0X16, PG_KEY_None, PG_KEY_Home);
    KEY(0X1C, PG_KEY_None, PG_KEY_Enter);
    KEY(0X36, PG_KEY_None, PG_KEY_End);
    KEY(0X2C, PG_KEY_None, PG_KEY_Escape);

    KEY(0X27, PG_KEY_LeftControl, PG_KEY_Left);
    KEY(0X28, PG_KEY_LeftControl, PG_KEY_Right);
    KEY(0X21, PG_KEY_LeftControl, PG_KEY_Up);
    KEY(0X22, PG_KEY_LeftControl, PG_KEY_Down);

    KEY(0X3F, PG_KEY_LeftControl, PG_KEY_Enter);
    KEY(0X2F, PG_KEY_LeftControl, PG_KEY_End);
    KEY(0X56, PG_KEY_LeftControl, PG_KEY_Escape);

    KEY(0X1F, PG_KEY_LeftShift, PG_KEY_Left);
    KEY(0X20, PG_KEY_LeftShift, PG_KEY_Right);
    KEY(0X5B, PG_KEY_LeftShift, PG_KEY_Down);

    KEY(0X17, PG_KEY_LeftShift, PG_KEY_Home);
    KEY(0X3A, PG_KEY_LeftShift, PG_KEY_Enter);
    KEY(0X3B, PG_KEY_LeftShift, PG_KEY_End);
    KEY(0X18, PG_KEY_LeftShift, PG_KEY_Escape);

    KEY(0X37, PG_KEY_RightShift, PG_KEY_Left);
    KEY(0X33, PG_KEY_RightShift, PG_KEY_Right);
    KEY(0X38, PG_KEY_RightShift, PG_KEY_Down);

    KEY(0X2A, PG_KEY_RightShift, PG_KEY_Home);
    KEY(0X31, PG_KEY_RightShift, PG_KEY_Enter);
    KEY(0X32, PG_KEY_RightShift, PG_KEY_End);
    KEY(0X30, PG_KEY_RightShift, PG_KEY_Escape);

    default:
      break;
  }
#undef KEY

  return 0;
}

static int
interpretSimulationKey (BrailleDisplay *brl, unsigned char key) {
  switch (key) {
    default:
      break;
  }

  return interpretNavigationKey(brl, key);
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  InputPacket packet;
  int length;

  while ((length = readPacket(brl, &packet))) {
    switch (packet.data.type) {
      case IPT_KEY_NAVIGATION:
        if (interpretNavigationKey(brl, packet.data.fields.key.value)) continue;
        break;

      case IPT_KEY_SIMULATION:
        if (interpretSimulationKey(brl, packet.data.fields.key.value)) continue;
        break;

      case IPT_KEY_ROUTING: {
        unsigned char code = packet.data.fields.key.value;
        KeyGroup group;
        KeyNumber number;

        if ((code >= 81) && (code <= 82)) {
          group = PG_GRP_NavigationKeys;
          number = PG_KEY_Status + (code - 81);
        } else if ((code > 0) && (code <= brl->textColumns)) {
          group = PG_GRP_RoutingKeys;
          number = code - 1;
        } else {
          break;
        }

        enqueueKey(brl, group, number);
        continue;
      }

      default:
        break;
    }

    logUnexpectedPacket(packet.bytes, length);
  }
  if (errno != EAGAIN) return BRL_CMD_RESTARTBRL;

  return EOF;
}
