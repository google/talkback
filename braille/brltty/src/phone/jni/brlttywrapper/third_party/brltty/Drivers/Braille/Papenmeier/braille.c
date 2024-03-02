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

/* This Driver was written as a project in the
 *   HTL W1, Abteilung Elektrotechnik, Wien - Österreich
 *   (Technical High School, Department for electrical engineering,
 *     Vienna, Austria)  http://www.ee.htlw16.ac.at
 *  by
 *   Tibor Becker
 *   Michael Burger
 *   Herbert Gruber
 *   Heimo Schön
 * Teacher:
 *   August Hörandl <august.hoerandl@gmx.at>
 */
/*
 * Support for all Papenmeier Terminal + config file
 *   Heimo.Schön <heimo.schoen@gmx.at>
 *   August Hörandl <august.hoerandl@gmx.at>
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <errno.h>

#include "log.h"
#include "bitfield.h"
#include "async_wait.h"
#include "ascii.h"
#include "ktb.h"

#define BRL_STATUS_FIELDS sfGeneric
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "brldefs-pm.h"
#include "models.h"
 
/*--- Input/Output Operations ---*/

typedef struct {
  const unsigned int *baudList;
  const SerialFlowControl flowControl;
  unsigned char protocol1;
  unsigned char protocol2;
} InputOutputOperations;

/*--- Serial Operations ---*/

static const unsigned int serialBauds[] = {19200, 38400, 0};
static const InputOutputOperations serialOperations = {
  .baudList = serialBauds,
  .flowControl = SERIAL_FLOW_HARDWARE,
  .protocol1 = 1,
  .protocol2 = 1
};

/*--- USB Operations ---*/

static const unsigned int usbBauds[] = {115200, 57600, 0};
static const InputOutputOperations usbOperations = {
  .baudList = usbBauds,
  .flowControl = SERIAL_FLOW_NONE,
  .protocol1 = 0,
  .protocol2 = 3
};

/*--- Bluetooth Operations ---*/

static const InputOutputOperations bluetoothOperations = {
  .baudList = NULL,
  .flowControl = SERIAL_FLOW_NONE,
  .protocol1 = 0,
  .protocol2 = 3
};

/*--- Protocol Operation Utilities ---*/

typedef struct {
  void (*initializeTerminal) (BrailleDisplay *brl);
  void (*releaseResources) (BrailleDisplay *brl);
  int (*readCommand) (BrailleDisplay *brl, KeyTableCommandContext context);
  void (*writeText) (BrailleDisplay *brl, unsigned int start, unsigned int count);
  void (*writeStatus) (BrailleDisplay *brl, unsigned int start, unsigned int count);
  void (*flushCells) (BrailleDisplay *brl);
  int (*setBrailleFirmness) (BrailleDisplay *brl, BrailleFirmness setting);
} ProtocolOperations;

typedef enum {
  PM_GSC_DOTS = 0,
  PM_GSC_FLAG,
  PM_GSC_NUMBER,
  PM_GSC_POSITION
} PM_GenericStatusFormat;

typedef struct {
  unsigned char format;
  unsigned char value;
} PM_GenericStatusCode;

typedef struct {
  int first;
  int last;
} PM_KeyRange1;

typedef struct {
  KeyGroup group;
  KeyNumber number;
} PM_InputMapping2;

struct BrailleDataStruct {
  const InputOutputOperations *io;
  const ModelEntry *model;
  const ProtocolOperations *protocol;

  unsigned char textCells[PM_MAXIMUM_TEXT_CELLS];
  unsigned char statusCells[PM_MAXIMUM_STATUS_CELLS];

  struct {
    MakeNumberFunction *makeNumber;
    MakeFlagFunction *makeFlag;

    PM_GenericStatusCode codes[PM_MAXIMUM_STATUS_CELLS];
    unsigned char initialized;
  } gsc;

  union {
    struct {
      struct {
        PM_KeyRange1 front;
        PM_KeyRange1 bar;
        PM_KeyRange1 switches;
        PM_KeyRange1 status;
        PM_KeyRange1 cursor;
        unsigned char switchState;
      } rcv;

      struct {
        unsigned char textOffset;
        unsigned char statusOffset;
      } xmt;
    } p1;

    struct {
      PM_InputMapping2 *inputMap;
      unsigned char *inputState;

      int inputKeySize;
      int inputBytes;
      int inputBits;

      int refreshRequired;
    } p2;
  } prot;
};

static int
writePacket (BrailleDisplay *brl, const void *packet, size_t size) {
  return writeBraillePacket(brl, NULL, packet, size);
}

static int
interpretIdentity (BrailleDisplay *brl, unsigned char id, int major, int minor) {
  unsigned int modelIndex;

  logMessage(LOG_INFO, "Papenmeier ID: %d  Version: %d.%02d", id, major, minor);

  for (modelIndex=0; modelIndex<modelCount; modelIndex+=1) {
    if (modelTable[modelIndex].modelIdentifier == id) {
      brl->data->model = &modelTable[modelIndex];
      logMessage(LOG_INFO, "%s  Size: %d",
                 brl->data->model->modelName,
                 brl->data->model->textColumns);

      brl->textColumns = brl->data->model->textColumns;
      brl->textRows = 1;
      brl->statusRows = (brl->statusColumns = brl->data->model->statusCount)? 1: 0;

      setBrailleKeyTable(brl, brl->data->model->keyTableDefinition);

      return 1;
    }
  }

  logMessage(LOG_WARNING, "unknown Papenmeier ID: %d", id);
  return 0;
}

/*--- Protocol 1 Operations ---*/

static BraillePacketVerifierResult
verifyPacket1 (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      *length = 2;
      if (byte != ASCII_STX) return BRL_PVR_INVALID;
      break;

    case 2:
      switch (byte) {
        case PM_P1_PKT_IDENTITY:
          *length = 10;
          break;

        case PM_P1_PKT_RECEIVE:
          *length = 6;
          break;

        case 0X03:
        case 0X04:
        case 0X05:
        case 0X06:
        case 0X07:
          *length = 3;
          break;

        default:
          return BRL_PVR_INVALID;
      }
      break;

    case 6:
      switch (bytes[1]) {
        case PM_P1_PKT_RECEIVE:
          *length = (bytes[4] << 8) | byte;
          if (*length != 10) return BRL_PVR_INVALID;
          break;

        default:
          break;
      }
      break;

    default:
      break;
  }

  if (size == *length) {
    if (byte != ASCII_ETX) {
      return BRL_PVR_INVALID;
    }
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readPacket1 (BrailleDisplay *brl, void *packet, size_t size) {
  return readBraillePacket(brl, NULL, packet, size, verifyPacket1, NULL);
}

static int
writePacket1 (BrailleDisplay *brl, unsigned int xmtAddress, unsigned int count, const unsigned char *data) {
  if (count) {
    unsigned char header[] = {
      ASCII_STX,
      PM_P1_PKT_SEND,
      0, 0, /* big endian data offset */
      0, 0  /* big endian packet length */
    };
    static const unsigned char trailer[] = {ASCII_ETX};

    unsigned int size = sizeof(header) + count + sizeof(trailer);
    unsigned char buffer[size];
    unsigned char *byte = buffer;

    header[2] = xmtAddress >> 8;
    header[3] = xmtAddress & 0XFF;

    header[4] = size >> 8;
    header[5] = size & 0XFF;

    byte = mempcpy(byte, header, sizeof(header));
    byte = mempcpy(byte, data, count);
    byte = mempcpy(byte, trailer, sizeof(trailer));

    if (!writePacket(brl, buffer, byte-buffer)) return 0;
  }
  return 1;
}

static int
interpretIdentity1 (BrailleDisplay *brl, const unsigned char *identity) {
  {
    unsigned char id = identity[2];
    unsigned char major = identity[3];
    unsigned char minor = ((identity[4] * 10) + identity[5]);
    if (!interpretIdentity(brl, id, major, minor)) return 0;
  }

  /* routing key codes: 0X300 -> status -> cursor */
  brl->data->prot.p1.rcv.status.first = PM_P1_RCV_KEYROUTE;
  brl->data->prot.p1.rcv.status.last  = brl->data->prot.p1.rcv.status.first + 3 * (brl->data->model->statusCount - 1);
  brl->data->prot.p1.rcv.cursor.first = brl->data->prot.p1.rcv.status.last + 3;
  brl->data->prot.p1.rcv.cursor.last  = brl->data->prot.p1.rcv.cursor.first + 3 * (brl->data->model->textColumns - 1);
  logMessage(LOG_DEBUG, "Routing Keys: status=%03X-%03X cursor=%03X-%03X",
             brl->data->prot.p1.rcv.status.first, brl->data->prot.p1.rcv.status.last,
             brl->data->prot.p1.rcv.cursor.first, brl->data->prot.p1.rcv.cursor.last);

  /* function key codes: 0X000 -> front -> bar -> switches */
  brl->data->prot.p1.rcv.front.first = PM_P1_RCV_KEYFUNC + 3;
  brl->data->prot.p1.rcv.front.last  = brl->data->prot.p1.rcv.front.first + 3 * (brl->data->model->frontKeys - 1);
  brl->data->prot.p1.rcv.bar.first = brl->data->prot.p1.rcv.front.last + 3;
  brl->data->prot.p1.rcv.bar.last  = brl->data->prot.p1.rcv.bar.first + 3 * ((brl->data->model->hasBar? 8: 0) - 1);
  brl->data->prot.p1.rcv.switches.first = brl->data->prot.p1.rcv.bar.last + 3;
  brl->data->prot.p1.rcv.switches.last  = brl->data->prot.p1.rcv.switches.first + 3 * ((brl->data->model->hasBar? 8: 0) - 1);
  logMessage(LOG_DEBUG, "Function Keys: front=%03X-%03X bar=%03X-%03X switches=%03X-%03X",
             brl->data->prot.p1.rcv.front.first, brl->data->prot.p1.rcv.front.last,
             brl->data->prot.p1.rcv.bar.first, brl->data->prot.p1.rcv.bar.last,
             brl->data->prot.p1.rcv.switches.first, brl->data->prot.p1.rcv.switches.last);

  /* cell offsets: 0X00 -> status -> text */
  brl->data->prot.p1.xmt.statusOffset = 0;
  brl->data->prot.p1.xmt.textOffset = brl->data->prot.p1.xmt.statusOffset + brl->data->model->statusCount;
  logMessage(LOG_DEBUG, "Cell Offsets: status=%02X text=%02X",
             brl->data->prot.p1.xmt.statusOffset, brl->data->prot.p1.xmt.textOffset);

  return 1;
}

static int
handleSwitches1 (BrailleDisplay *brl, uint16_t time) {
  unsigned char state = time & 0XFF;
  KeyNumber pressStack[8];
  unsigned char pressCount = 0;
  const KeyGroup group = PM_GRP_SWT;
  KeyNumber number = 0;
  unsigned char bit = 0X1;

  while (brl->data->prot.p1.rcv.switchState != state) {
    if ((state & bit) && !(brl->data->prot.p1.rcv.switchState & bit)) {
      pressStack[pressCount++] = number;
      brl->data->prot.p1.rcv.switchState |= bit;
    } else if (!(state & bit) && (brl->data->prot.p1.rcv.switchState & bit)) {
      if (!enqueueKeyEvent(brl, group, number, 0)) return 0;
      brl->data->prot.p1.rcv.switchState &= ~bit;
    }

    number += 1;
    bit <<= 1;
  }

  while (pressCount) {
    if (!enqueueKeyEvent(brl, group, pressStack[--pressCount], 1)) {
      return 0;
    }
  }

  return 1;
}

static int
handleKey1 (BrailleDisplay *brl, uint16_t code, int press, uint16_t time) {
  int key;

  if (brl->data->prot.p1.rcv.front.first <= code && 
      code <= brl->data->prot.p1.rcv.front.last) { /* front key */
    key = (code - brl->data->prot.p1.rcv.front.first) / 3;
    return enqueueKeyEvent(brl, PM_GRP_FK1, key, press);
  }

  if (brl->data->prot.p1.rcv.status.first <= code && 
      code <= brl->data->prot.p1.rcv.status.last) { /* status key */
    key = (code - brl->data->prot.p1.rcv.status.first) / 3;
    return enqueueKeyEvent(brl, PM_GRP_SK1, key, press);
  }

  if (brl->data->prot.p1.rcv.bar.first <= code && 
      code <= brl->data->prot.p1.rcv.bar.last) { /* easy access bar */
    if (!handleSwitches1(brl, time)) return 0;

    key = (code - brl->data->prot.p1.rcv.bar.first) / 3;
    return enqueueKeyEvent(brl, PM_GRP_BAR, key, press);
  }

  if (brl->data->prot.p1.rcv.switches.first <= code && 
      code <= brl->data->prot.p1.rcv.switches.last) { /* easy access bar */
    return handleSwitches1(brl, time);
  //key = (code - brl->data->prot.p1.rcv.switches.first) / 3;
  //return enqueueKeyEvent(brl, PM_GRP_SWT, key, press);
  }

  if (brl->data->prot.p1.rcv.cursor.first <= code && 
      code <= brl->data->prot.p1.rcv.cursor.last) { /* Routing Keys */ 
    key = (code - brl->data->prot.p1.rcv.cursor.first) / 3;
    return enqueueKeyEvent(brl, PM_GRP_RK1, key, press);
  }

  logMessage(LOG_WARNING, "unexpected key: %04X", code);
  return 1;
}

static int
disableOutputTranslation1 (BrailleDisplay *brl, unsigned char xmtOffset, int count) {
  unsigned char buffer[count];
  memset(buffer, 1, sizeof(buffer));
  return writePacket1(brl, PM_P1_XMT_BRLWRITE+xmtOffset,
                      sizeof(buffer), buffer);
}

static void
initializeTable1 (BrailleDisplay *brl) {
  disableOutputTranslation1(brl, brl->data->prot.p1.xmt.statusOffset, brl->data->model->statusCount);
  disableOutputTranslation1(brl, brl->data->prot.p1.xmt.textOffset, brl->data->model->textColumns);
}

static void
writeText1 (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  unsigned char buffer[count];
  translateOutputCells(buffer, brl->data->textCells+start, count);
  writePacket1(brl, PM_P1_XMT_BRLDATA+brl->data->prot.p1.xmt.textOffset+start, count, buffer);
}

static void
writeStatus1 (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  unsigned char buffer[count];
  translateOutputCells(buffer, brl->data->statusCells+start, count);
  writePacket1(brl, PM_P1_XMT_BRLDATA+brl->data->prot.p1.xmt.statusOffset+start, count, buffer);
}

static void
flushCells1 (BrailleDisplay *brl) {
}

static void
initializeTerminal1 (BrailleDisplay *brl) {
  initializeTable1(brl);
  drainBrailleOutput(brl, 0);

  writeStatus1(brl, 0, brl->data->model->statusCount);
  drainBrailleOutput(brl, 0);

  writeText1(brl, 0, brl->data->model->textColumns);
  drainBrailleOutput(brl, 0);
}

static int
readCommand1 (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[PM_P1_MAXIMUM_PACKET_SIZE];
  size_t length;

  while ((length = readPacket1(brl, packet, sizeof(packet)))) {
    switch (packet[1]) {
      case PM_P1_PKT_IDENTITY:
        if (interpretIdentity1(brl, packet)) brl->resizeRequired = 1;
        asyncWait(200);
        initializeTerminal1(brl);
        break;

      case PM_P1_PKT_RECEIVE:
        handleKey1(brl, ((packet[2] << 8) | packet[3]),
                   (packet[6] == PM_P1_KEY_PRESSED),
                   ((packet[7] << 8) | packet[8]));
        continue;

      {
        const char *message;

      case 0X03:
        message = "missing identification byte";
        goto logError;

      case 0X04:
        message = "data too long";
        goto logError;

      case 0X05:
        message = "data starts beyond end of structure";
        goto logError;

      case 0X06:
        message = "data extends beyond end of structure";
        goto logError;

      case 0X07:
        message = "data framing error";
        goto logError;

      logError:
        logMessage(LOG_WARNING, "Output packet error: %02X: %s", packet[1], message);
        initializeTerminal1(brl);
        break;
      }

      default:
        logUnexpectedPacket(packet, length);
        break;
    }
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}

static void
releaseResources1 (BrailleDisplay *brl) {
}

static const ProtocolOperations protocolOperations1 = {
  initializeTerminal1, releaseResources1,
  readCommand1,
  writeText1, writeStatus1, flushCells1,
  NULL
};

static int
writeIdentifyRequest1 (BrailleDisplay *brl) {
  static const unsigned char badPacket[] = {
    ASCII_STX,
    PM_P1_PKT_SEND,
    0, 0,			/* position */
    0, 0,			/* wrong number of bytes */
    ASCII_ETX
  };

  return writePacket(brl, badPacket, sizeof(badPacket));
}

static BrailleResponseResult
isIdentityResponse1 (BrailleDisplay *brl, const void *packet, size_t size) {
  const unsigned char *packet1 = packet;

  return (packet1[1] == PM_P1_PKT_IDENTITY)? BRL_RSP_DONE: BRL_RSP_UNEXPECTED;
}

static int
identifyTerminal1 (BrailleDisplay *brl) {
  unsigned char response[PM_P1_MAXIMUM_PACKET_SIZE];			/* answer has 10 chars */
  int detected = probeBrailleDisplay(brl, 0, NULL, 1000,
                                     writeIdentifyRequest1,
                                     readPacket1, response, sizeof(response),
                                     isIdentityResponse1);

  if (detected) {
    if (interpretIdentity1(brl, response)) {
      brl->data->protocol = &protocolOperations1;
      brl->data->prot.p1.rcv.switchState = 0;

      makeOutputTable(dotsTable_ISO11548_1);
      return 1;
    }
  }

  return 0;
}

/*--- Protocol 2 Operations ---*/

#define PM2_MAX_PACKET_SIZE 0X203
#define PM2_MAKE_BYTE(high, low) ((LOW_NIBBLE((high)) << 4) | LOW_NIBBLE((low)))
#define PM2_MAKE_INTEGER2(tens,ones) ((LOW_NIBBLE((tens)) * 10) + LOW_NIBBLE((ones)))

typedef struct {
  unsigned char bytes[PM2_MAX_PACKET_SIZE];
  unsigned char type;
  unsigned char length;

  union {
    unsigned char bytes[0XFF];
  } data;
} Packet2;

static BraillePacketVerifierResult
verifyPacket2 (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  Packet2 *packet = data;
  unsigned char byte = bytes[size-1];

  switch (byte) {
    case ASCII_STX:
      if (size != 1) break;
      *length = 5;
      return BRL_PVR_INCLUDE;

    case ASCII_ETX:
      if (size != *length) break;
      return BRL_PVR_INCLUDE;

    default: {
      unsigned char type = HIGH_NIBBLE(byte);
      unsigned char value = LOW_NIBBLE(byte);
      int isIdentityPacket = packet->type == 0X0A;

      switch (size) {
        case 1:
          break;

        case 2:
          if (type != 0X40) break;
          packet->type = value;
          return BRL_PVR_INCLUDE;

        case 3:
          if (type != 0X50) break;
          packet->length = value << 4;
          return BRL_PVR_INCLUDE;

        case 4:
          if (type != 0X50) break;
          packet->length |= value;

          {
            size_t increment = packet->length;

            if (!isIdentityPacket) increment *= 2;
            *length += increment;

            return BRL_PVR_INCLUDE;
          }

        default: {
          size_t index;

          if (type != 0X30) break;
          if (size == *length) break;
          index = size - 5;

          if (isIdentityPacket) {
            packet->data.bytes[index] = byte;
          } else {
            int high = !(index % 2);
            index /= 2;

            if (high) {
              packet->data.bytes[index] = value << 4;
            } else {
              packet->data.bytes[index] |= value;
            }
          }

          return BRL_PVR_INCLUDE;
        }
      }

      break;
    }
  }

  return BRL_PVR_INVALID;
}

static size_t
readPacket2 (BrailleDisplay *brl, void *packet, size_t size) {
  Packet2 *packet2 = packet;

  return readBraillePacket(brl, NULL, packet2->bytes, size, verifyPacket2, packet);
}

static int
writePacket2 (BrailleDisplay *brl, unsigned char command, unsigned char count, const unsigned char *data) {
  unsigned char buffer[(count * 2) + 5];
  unsigned char *byte = buffer;

  *byte++ = ASCII_STX;
  *byte++ = 0X40 | command;
  *byte++ = 0X50 | (count >> 4);
  *byte++ = 0X50 | (count & 0XF);

  while (count-- > 0) {
    *byte++ = 0X30 | (*data >> 4);
    *byte++ = 0X30 | (*data & 0XF);
    data++;
  }

  *byte++ = ASCII_ETX;
  return writePacket(brl, buffer, byte-buffer);
}

static int
interpretIdentity2 (BrailleDisplay *brl, const unsigned char *identity) {
  {
    unsigned char id = PM2_MAKE_BYTE(identity[0], identity[1]);
    unsigned char major = LOW_NIBBLE(identity[2]);
    unsigned char minor = PM2_MAKE_INTEGER2(identity[3], identity[4]);
    if (!interpretIdentity(brl, id, major, minor)) return 0;
  }

  return 1;
}

static void
writeCells2 (BrailleDisplay *brl, unsigned int start, unsigned int count) {
  brl->data->prot.p2.refreshRequired = 1;
}

static void
flushCells2 (BrailleDisplay *brl) {
  if (brl->data->prot.p2.refreshRequired) {
    unsigned char buffer[0XFF];
    unsigned char *byte = buffer;

    /* The status cells. */
    byte = translateOutputCells(byte, brl->data->statusCells, brl->data->model->statusCount);

    /* Two dummy cells for each key on the left side. */
    if (brl->data->model->protocolRevision < 2) {
      int count = brl->data->model->leftKeys;
      while (count-- > 0) {
        *byte++ = 0;
        *byte++ = 0;
      }
    }

    /* The text cells. */
    byte = translateOutputCells(byte, brl->data->textCells, brl->data->model->textColumns);

    /* Two dummy cells for each key on the right side. */
    if (brl->data->model->protocolRevision < 2) {
      int count = brl->data->model->rightKeys;
      while (count-- > 0) {
        *byte++ = 0;
        *byte++ = 0;
      }
    }

    writePacket2(brl, 3, byte-buffer, buffer);
    brl->data->prot.p2.refreshRequired = 0;
  }
}

static void
initializeTerminal2 (BrailleDisplay *brl) {
  memset(brl->data->prot.p2.inputState, 0, brl->data->prot.p2.inputBytes);
  brl->data->prot.p2.refreshRequired = 1;

  /* Don't send the init packet by default as that was done at the factory
   * and shouldn't need to be done again. We'll keep the code, though,
   * just in case it's ever needed. Perhaps there should be a driver
   * parameter to control it.
   */
  if (0) {
    unsigned char data[13];
    unsigned char size = 0;

    data[size++] = brl->data->model->modelIdentifier; /* device identification code */

    /* serial baud (bcd-encoded, six digits, one per nibble) */
    /* set to zero for default (57,600) */
    data[size++] = 0;
    data[size++] = 0;
    data[size++] = 0;

    data[size++] = brl->data->model->statusCount; /* number of vertical braille cells */
    data[size++] = brl->data->model->leftKeys; /* number of left keys and switches */
    data[size++] = brl->data->model->textColumns; /* number of horizontal braille cells */
    data[size++] = brl->data->model->rightKeys; /* number of right keys and switches */

    data[size++] = 2; /* number of routing keys per braille cell */
    data[size++] = 0; /* size of LCD */

    data[size++] = 1; /* keys and switches mixed into braille data stream */
    data[size++] = 0; /* easy access bar mixed into braille data stream */
    data[size++] = 1; /* routing keys mixed into braille data stream */

    logBytes(LOG_DEBUG, "Init Packet", data, size);
    writePacket2(brl, 1, size, data);
  }
}

static int 
readCommand2 (BrailleDisplay *brl, KeyTableCommandContext context) {
  Packet2 packet;

  while (readPacket2(brl, &packet, PM2_MAX_PACKET_SIZE)) {
    switch (packet.type) {
      default:
        logMessage(LOG_DEBUG, "Packet ignored: %02X", packet.type);
        break;

      case 0X0B: {
        int bytes = MIN(packet.length, brl->data->prot.p2.inputBytes);
        int byte;

        /* Find out which keys have been released. */
        for (byte=0; byte<bytes; byte+=1) {
          unsigned char old = brl->data->prot.p2.inputState[byte];
          unsigned char new = packet.data.bytes[byte];

          if (new != old) {
            PM_InputMapping2 *mapping = &brl->data->prot.p2.inputMap[byte * 8];
            unsigned char bit = 0X01;

            while (bit) {
              if (!(new & bit) && (old & bit)) {
                enqueueKeyEvent(brl, mapping->group, mapping->number, 0);
                if ((brl->data->prot.p2.inputState[byte] &= ~bit) == new) break;
              }

              mapping += 1;
              bit <<= 1;
            }
          }
        }

        /* Find out which keys have been pressed. */
        for (byte=0; byte<bytes; byte+=1) {
          unsigned char old = brl->data->prot.p2.inputState[byte];
          unsigned char new = packet.data.bytes[byte];

          if (new != old) {
            PM_InputMapping2 *mapping = &brl->data->prot.p2.inputMap[byte * 8];
            unsigned char bit = 0X01;

            while (bit) {
              if ((new & bit) && !(old & bit)) {
                enqueueKeyEvent(brl, mapping->group, mapping->number, 1);
                if ((brl->data->prot.p2.inputState[byte] |= bit) == new) break;
              }

              mapping += 1;
              bit <<= 1;
            }
          }
        }

        continue;
      }

      case 0X0C: {
        unsigned char modifiers = packet.data.bytes[0];
        unsigned char code = packet.data.bytes[1];

        if (modifiers & 0X80) {
          int command = BRL_CMD_BLK(PASSXT) | code;

          if (modifiers & 0X01) command |= BRL_FLG_KBD_RELEASE;
          if (modifiers & 0X02) command |= BRL_FLG_KBD_EMUL0;
          if (modifiers & 0X04) command |= BRL_FLG_KBD_EMUL1;

          enqueueCommand(command);
        } else {
          KeyNumberSet keys = (modifiers << 8) | code;

#define BIT(key) (1 << (key))
          if (keys & (BIT(PM_KBD_LeftSpace) | BIT(PM_KBD_RightSpace))) {
            keys &= ~BIT(PM_KBD_Space);
          }
#undef BIT

          enqueueKeys(brl, keys, PM_GRP_KBD, 0);
        }

        continue;
      }
    }
  }

  if (errno != EAGAIN) return BRL_CMD_RESTARTBRL;
  return EOF;
}

static void
releaseResources2 (BrailleDisplay *brl) {
  if (brl->data->prot.p2.inputState) {
    free(brl->data->prot.p2.inputState);
    brl->data->prot.p2.inputState = NULL;
  }

  if (brl->data->prot.p2.inputMap) {
    free(brl->data->prot.p2.inputMap);
    brl->data->prot.p2.inputMap = NULL;
  }
}

static int
setBrailleFirmness2 (BrailleDisplay *brl, BrailleFirmness setting) {
  unsigned char data[] = {(setting * 98 / BRL_FIRMNESS_MAXIMUM) + 2, 0X99};
  return writePacket2(brl, 6, sizeof(data), data);
}

static const ProtocolOperations protocolOperations2 = {
  initializeTerminal2, releaseResources2,
  readCommand2,
  writeCells2, writeCells2, flushCells2,
  setBrailleFirmness2
};

typedef struct {
  unsigned char byte;
  unsigned char bit;
  unsigned char size;
} InputModule2;

static void
addInputMapping2 (BrailleDisplay *brl, const InputModule2 *module, unsigned char bit, KeyGroup group, KeyNumber number) {
  if (brl->data->model->protocolRevision < 2) {
    bit += module->bit;
  } else {
    bit += 8 - module->bit - module->size;
  }

  {
    PM_InputMapping2 *mapping = &brl->data->prot.p2.inputMap[(module->byte * 8) + bit];
    mapping->group = group;
    mapping->number = number;
  }
}

static int
nextInputModule2 (InputModule2 *module, unsigned char size) {
  if (!module->bit) {
    if (!module->byte) return 0;
    module->byte -= 1;
    module->bit = 8;
  }
  module->bit -= module->size = size;
  return 1;
}

static void
mapInputKey2 (BrailleDisplay *brl, int count, InputModule2 *module, KeyGroup group, KeyNumber rear, KeyNumber front) {
  while (count--) {
    nextInputModule2(module, brl->data->prot.p2.inputKeySize);
    addInputMapping2(brl, module, 0, group, rear);
    addInputMapping2(brl, module, 1, group, front);
  }
}

static void
mapInputModules2 (BrailleDisplay *brl) {
  InputModule2 module;
  module.byte = brl->data->prot.p2.inputBytes;
  module.bit = 0;

  {
    int i;
    for (i=0; i<brl->data->prot.p2.inputBits; ++i) {
      PM_InputMapping2 *mapping = &brl->data->prot.p2.inputMap[i];
      mapping->group = 0;
      mapping->number = 0;
    }
  }

  mapInputKey2(brl, brl->data->model->rightKeys, &module, PM_GRP_SWT, PM_SWT_RightKeyRear, PM_SWT_RightKeyFront);

  {
    unsigned char column = brl->data->model->textColumns;
    while (column) {
      nextInputModule2(&module, 1);
      addInputMapping2(brl, &module, 0, PM_GRP_RK2, --column);

      nextInputModule2(&module, 1);
      addInputMapping2(brl, &module, 0, PM_GRP_RK1, column);
    }
  }

  mapInputKey2(brl, brl->data->model->leftKeys, &module, PM_GRP_SWT, PM_SWT_LeftKeyRear, PM_SWT_LeftKeyFront);

  {
    unsigned char cell = brl->data->model->statusCount;
    while (cell) {
      nextInputModule2(&module, 1);
      addInputMapping2(brl, &module, 0, PM_GRP_SK2, cell-1);

      nextInputModule2(&module, 1);
      addInputMapping2(brl, &module, 0, PM_GRP_SK1, cell--);
    }
  }

  module.bit = 0;
  nextInputModule2(&module, 8);
  addInputMapping2(brl, &module, 0, PM_GRP_BAR, PM_BAR_Up2);
  addInputMapping2(brl, &module, 1, PM_GRP_BAR, PM_BAR_Up1);
  addInputMapping2(brl, &module, 2, PM_GRP_BAR, PM_BAR_Down1);
  addInputMapping2(brl, &module, 3, PM_GRP_BAR, PM_BAR_Down2);
  addInputMapping2(brl, &module, 4, PM_GRP_BAR, PM_BAR_Right1);
  addInputMapping2(brl, &module, 5, PM_GRP_BAR, PM_BAR_Left1);
  addInputMapping2(brl, &module, 6, PM_GRP_BAR, PM_BAR_Right2);
  addInputMapping2(brl, &module, 7, PM_GRP_BAR, PM_BAR_Left2);
}

static int
writeIdentifyRequest2 (BrailleDisplay *brl) {
  return writePacket2(brl, 2, 0, NULL);
}

static BrailleResponseResult
isIdentityResponse2 (BrailleDisplay *brl, const void *packet, size_t size) {
  const Packet2 *packet2 = packet;

  if (packet2->type == 0X0A) return BRL_RSP_DONE;
  logUnexpectedPacket(packet2->bytes, size);
  return BRL_RSP_CONTINUE;
}

static int
identifyTerminal2 (BrailleDisplay *brl) {
  Packet2 packet;			/* answer has 10 chars */
  int detected = probeBrailleDisplay(brl, brl->data->io->protocol2-1, NULL, 100,
                                     writeIdentifyRequest2,
                                     readPacket2, &packet, PM2_MAX_PACKET_SIZE,
                                     isIdentityResponse2);

  if (detected) {
    if (interpretIdentity2(brl, packet.data.bytes)) {
      brl->data->protocol = &protocolOperations2;

      MAKE_OUTPUT_TABLE(0X80, 0X40, 0X20, 0X10, 0X08, 0X04, 0X02, 0X01);

      brl->data->prot.p2.inputKeySize = (brl->data->model->protocolRevision < 2)? 4: 8;
      {
        int keyCount = brl->data->model->leftKeys + brl->data->model->rightKeys;

        brl->data->prot.p2.inputBytes = keyCount + 1 +
                                        ((((keyCount * brl->data->prot.p2.inputKeySize) +
                                           ((brl->data->model->textColumns + brl->data->model->statusCount) * 2)
                                          ) + 7) / 8);
      }
      brl->data->prot.p2.inputBits = brl->data->prot.p2.inputBytes * 8;

      if ((brl->data->prot.p2.inputMap = malloc(brl->data->prot.p2.inputBits * sizeof(*brl->data->prot.p2.inputMap)))) {
        mapInputModules2(brl);

        if ((brl->data->prot.p2.inputState = malloc(brl->data->prot.p2.inputBytes))) {
          return 1;
        }

        free(brl->data->prot.p2.inputMap);
        brl->data->prot.p2.inputMap = NULL;
      }
    }
  }

  return 0;
}

/*--- Driver Operations ---*/

static int
identifyTerminal (BrailleDisplay *brl) {
  if (brl->data->io->protocol1 && identifyTerminal1(brl)) return 1;
  if (brl->data->io->protocol2 && identifyTerminal2(brl)) return 1;
  return 0;
}

static int
startTerminal (BrailleDisplay *brl) {
  if (gioDiscardInput(brl->gioEndpoint)) {
    if (identifyTerminal(brl)) {
      brl->setBrailleFirmness = brl->data->protocol->setBrailleFirmness;

      memset(brl->data->textCells, 0, brl->data->model->textColumns);
      memset(brl->data->statusCells, 0, brl->data->model->statusCount);

      brl->data->protocol->initializeTerminal(brl);
      return 1;
    }
  }

  return 0;
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS
  };

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* all models */
      .vendor=0X0403, .product=0XF208,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .serial = &serialParameters
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;
  descriptor.serial.options.applicationData = &serialOperations;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.options.applicationData = &usbOperations;

  descriptor.bluetooth.channelNumber = 1;
  descriptor.bluetooth.options.applicationData = &bluetoothOperations;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    brl->data->io = gioGetApplicationData(brl->gioEndpoint);
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));
    brl->data->io = NULL;
    brl->data->model = NULL;
    brl->data->protocol = NULL;
    brl->data->gsc.initialized = 0;

    if (connectResource(brl, device)) {
      const unsigned int *baud = brl->data->io->baudList;

      if (baud) {
        while (*baud) {
          SerialParameters serialParameters;

          gioInitializeSerialParameters(&serialParameters);
          serialParameters.baud = *baud;
          serialParameters.flowControl = brl->data->io->flowControl;
          logMessage(LOG_DEBUG, "probing Papenmeier display at %u baud", *baud);

          if (gioReconfigureResource(brl->gioEndpoint, &serialParameters)) {
            if (startTerminal(brl)) {
               return 1;
            }
          }

          baud += 1;
        }
      } else if (startTerminal(brl)) {
        return 1;
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
  disconnectBrailleResource(brl, NULL);
  brl->data->protocol->releaseResources(brl);
  free(brl->data);
}

static void
updateCells (
  BrailleDisplay *brl,
  unsigned int count, const unsigned char *data, unsigned char *cells,
  void (*writeCells) (BrailleDisplay *brl, unsigned int start, unsigned int count)
) {
  unsigned int from, to;

  if (cellsHaveChanged(cells, data, count, &from, &to, NULL)) {
    writeCells(brl, from, to-from);
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  updateCells(brl, brl->data->model->textColumns, brl->buffer, brl->data->textCells, brl->data->protocol->writeText);
  brl->data->protocol->flushCells(brl);
  return 1;
}

static void
initializeGenericStatusCodes (BrailleDisplay *brl) {
  const size_t count = ARRAY_COUNT(brl->data->gsc.codes);
  int commands[count];

  getKeyGroupCommands(brl->keyTable, PM_GRP_SK1, commands, count);

  {
    unsigned int i;

    for (i=0; i<count; i+=1) {
      PM_GenericStatusCode *code = &brl->data->gsc.codes[i];

#define SET(COMMAND,FORMAT,VALUE) case (COMMAND): code->format = (FORMAT); code->value = (VALUE); break;
      switch (commands[i] & BRL_MSK_CMD) {
        default:
        SET(BRL_CMD_NOOP, PM_GSC_DOTS, 0);

        SET(BRL_CMD_HELP, PM_GSC_NUMBER, gscBrailleWindowRow);
        SET(BRL_CMD_LEARN, PM_GSC_POSITION, gscBrailleWindowColumn);
        SET(BRL_CMD_CSRJMP_VERT, PM_GSC_NUMBER, gscScreenCursorRow);
        SET(BRL_CMD_INFO, PM_GSC_NUMBER, gscScreenCursorColumn);
        SET(BRL_CMD_PREFMENU, PM_GSC_NUMBER, gscScreenNumber);

        SET(BRL_CMD_FREEZE, PM_GSC_FLAG, gscFrozenScreen);
        SET(BRL_CMD_DISPMD, PM_GSC_FLAG, gscDisplayMode);
        SET(BRL_CMD_SIXDOTS, PM_GSC_FLAG, gscSixDotComputerBraille);
        SET(BRL_CMD_SLIDEWIN, PM_GSC_FLAG, gscSlidingBrailleWindow);
        SET(BRL_CMD_SKPIDLNS, PM_GSC_FLAG, gscSkipIdenticalLines);
        SET(BRL_CMD_SKPBLNKWINS, PM_GSC_FLAG, gscSkipBlankBrailleWindows);
        SET(BRL_CMD_CSRVIS, PM_GSC_FLAG, gscShowScreenCursor);
        SET(BRL_CMD_CSRHIDE, PM_GSC_FLAG, gscHideScreenCursor);
        SET(BRL_CMD_CSRTRK, PM_GSC_FLAG, gscTrackScreenCursor);
        SET(BRL_CMD_CSRSIZE, PM_GSC_FLAG, gscScreenCursorStyle);
        SET(BRL_CMD_CSRBLINK, PM_GSC_FLAG, gscBlinkingScreenCursor);
        SET(BRL_CMD_ATTRVIS, PM_GSC_FLAG, gscShowAttributes);
        SET(BRL_CMD_ATTRBLINK, PM_GSC_FLAG, gscBlinkingAttributes);
        SET(BRL_CMD_CAPBLINK, PM_GSC_FLAG, gscBlinkingCapitals);
        SET(BRL_CMD_TUNES, PM_GSC_FLAG, gscAlertTunes);
        SET(BRL_CMD_AUTOREPEAT, PM_GSC_FLAG, gscAutorepeat);
        SET(BRL_CMD_AUTOSPEAK, PM_GSC_FLAG, gscAutospeak);
        SET(BRL_CMD_BRLUCDOTS, PM_GSC_FLAG, gscBrailleTypingMode);
      }
#undef SET
    }
  }
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *s) {
  if (brl->data->model->statusCount) {
    unsigned char cells[brl->data->model->statusCount];

    if (s[GSC_FIRST] == GSC_MARKER) {
      unsigned int i;

      if (!brl->data->gsc.initialized) {
        if (brl->data->model->statusCount < 13) {
          brl->data->gsc.makeNumber = makePortraitNumber;
          brl->data->gsc.makeFlag = makePortraitFlag;
        } else {
          brl->data->gsc.makeNumber = makeSeascapeNumber;
          brl->data->gsc.makeFlag = makeSeascapeFlag;
        }

        initializeGenericStatusCodes(brl);
        brl->data->gsc.initialized = 1;
      }

      for (i=0; i<brl->data->model->statusCount; i+=1) {
        unsigned char *cell = &cells[i];

        if (i < ARRAY_COUNT(brl->data->gsc.codes)) {
          const PM_GenericStatusCode *code = &brl->data->gsc.codes[i];

          switch (code->format) {
            case PM_GSC_DOTS:
              *cell = code->value;
              continue;

            case PM_GSC_FLAG:
              *cell = brl->data->gsc.makeFlag(i+1, s[code->value]);
              continue;

            case PM_GSC_POSITION:
              if (s[code->value] == 1) break;
            case PM_GSC_NUMBER:
              *cell = brl->data->gsc.makeNumber(s[code->value]);
              continue;

            default:
              break;
          }
        }

        *cell = 0;
      }
    } else {
      unsigned int i = 0;

      while (i < brl->data->model->statusCount) {
        unsigned char dots = s[i];

        if (!dots) break;
        cells[i++] = dots;
      }

      while (i < brl->data->model->statusCount) cells[i++] = 0;
    }

    updateCells(brl, brl->data->model->statusCount, cells, brl->data->statusCells, brl->data->protocol->writeStatus);
  }

  return 1;
}

static int 
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  return brl->data->protocol->readCommand(brl, context);
}
