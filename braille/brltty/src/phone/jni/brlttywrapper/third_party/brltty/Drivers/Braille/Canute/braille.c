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

#include <string.h>
#include <errno.h>

#include "log.h"
#include "crc_generate.h"
#include "async_handle.h"
#include "async_alarm.h"
#include "timing.h"

#include "brl_driver.h"
#include "brldefs-cn.h"

#define PROBE_RETRY_LIMIT 0
#define PROBE_RESPONSE_TIMEOUT 1000
#define COMMAND_RESPONSE_TIMEOUT 10000
#define MAXIMUM_RESPONSE_SIZE 0X100

#define KEYS_POLL_INTERVAL 100
#define MOTORS_POLL_INTERVAL 400
#define ROW_UPDATE_TIME 1200
#define CELLS_RESET_TIME 14000
#define UPDATE_RETRY_DELAY 5000

BEGIN_KEY_NAME_TABLE(navigation)
  KEY_NAME_ENTRY(CN_KEY_Help, "Help"),
  KEY_NAME_ENTRY(CN_KEY_Refresh, "Refresh"),

  KEY_NAME_ENTRY(CN_KEY_Line1, "Line1"),
  KEY_NAME_ENTRY(CN_KEY_Line2, "Line2"),
  KEY_NAME_ENTRY(CN_KEY_Line3, "Line3"),
  KEY_NAME_ENTRY(CN_KEY_Line4, "Line4"),
  KEY_NAME_ENTRY(CN_KEY_Line5, "Line5"),
  KEY_NAME_ENTRY(CN_KEY_Line6, "Line6"),
  KEY_NAME_ENTRY(CN_KEY_Line7, "Line7"),
  KEY_NAME_ENTRY(CN_KEY_Line8, "Line8"),
  KEY_NAME_ENTRY(CN_KEY_Line9, "Line9"),

  KEY_NAME_ENTRY(CN_KEY_Back, "Back"),
  KEY_NAME_ENTRY(CN_KEY_Menu, "Menu"),
  KEY_NAME_ENTRY(CN_KEY_Forward, "Forward"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(all)
  KEY_NAME_TABLE(navigation),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(all)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(all),
END_KEY_TABLE_LIST

typedef struct {
  unsigned char force;
  unsigned char haveOldCells:1;
  unsigned char haveNewCells:1;

  unsigned char *oldCells;
  unsigned char newCells[];
} RowEntry;

typedef BrailleResponseResult ProbeResponseHandler (
  BrailleDisplay *brl,
  const unsigned char *response, size_t size
);

struct BrailleDataStruct {
  CRCGenerator *crcGenerator;
  AsyncHandle keysPollerAlarm;

  struct {
    ProbeResponseHandler *responseHandler;
    unsigned int protocolVersion;
  } probe;

  struct {
    TimePeriod timeout;
    unsigned char command;
    unsigned char waiting:1;
  } response;

  struct {
    TimePeriod retryDelay;
    RowEntry **rowEntries;
    unsigned int firstChangedRow;
    unsigned int lastRowSent;
    unsigned char resetCells:1;
  } window;

  struct {
    TimePeriod delay;
    CN_PacketInteger flags;
  } status;

  struct {
    KeyNumberSet pressed;
  } keys;
};

static crc_t
makePacketChecksum (BrailleDisplay *brl, const void *packet, size_t size) {
  CRCGenerator *crc = brl->data->crcGenerator;
  crcResetGenerator(crc);
  crcAddData(crc, packet, size);
  return crcGetChecksum(crc);
}

typedef enum {
  PVS_WAITING,
  PVS_STARTED,
  PVS_DONE
} PacketVerificationState;

typedef struct {
  PacketVerificationState state;
  unsigned escaped:1;
} PacketVerificationData;

static BraillePacketVerifierResult
verifyPacket (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  PacketVerificationData *pvd = data;
  unsigned char *byte = &bytes[size-1];

  if (*byte == CN_PACKET_FRAMING_BYTE) {
    if ((pvd->state += 1) == PVS_DONE) {
      if (pvd->escaped) return BRL_PVR_INVALID;
      *length = size - 1;
    } else {
      *length = MAXIMUM_RESPONSE_SIZE;
    }

    return BRL_PVR_EXCLUDE;
  }

  if (pvd->state == PVS_WAITING) {
    return BRL_PVR_INVALID;
  }

  if (*byte == CN_PACKET_ESCAPE_BYTE) {
    if (pvd->escaped) return BRL_PVR_INVALID;
    pvd->escaped = 1;
    return BRL_PVR_EXCLUDE;
  }

  if (pvd->escaped) {
    pvd->escaped = 0;
    *byte ^= CN_PACKET_ESCAPE_BIT;
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readPacket (BrailleDisplay *brl, void *packet, size_t size) {
  while (1) {
    PacketVerificationData pvd = {
      .state = PVS_WAITING
    };

    size_t length = readBraillePacket(brl, NULL, packet, size, verifyPacket, &pvd);

    if (length > 0) {
      if (length < 3) {
        logShortPacket(packet, length);
        continue;
      }

      {
        crc_t expected = CN_getResponseInteger(packet, (length -= 2));
        crc_t actual = makePacketChecksum(brl, packet, length);

        if (actual != expected) {
          logBytes(LOG_WARNING,
            "input packet checksum mismatch:"
            " Actual:%"PRIcrc " Expected:%"PRIcrc,
            packet, length,
            actual, expected
          );

          continue;
        }
      }

      {
        const unsigned char *bytes = packet;
        size_t expected = 0;

        switch (bytes[0]) {
          case CN_CMD_COLUMN_COUNT:
          case CN_CMD_ROW_COUNT:
          case CN_CMD_PROTOCOL_VERSION:
          case CN_CMD_FIRMWARE_VERSION:
          case CN_CMD_DEVICE_STATUS:
          case CN_CMD_PRESSED_KEYS:
          case CN_CMD_SEND_ROW:
          case CN_CMD_RESET_CELLS:
            expected = 3;
            break;

          default:
            logUnexpectedPacket(packet, length);
            continue;
        }

        if (length < expected) {
          logTruncatedPacket(packet, length);
          continue;
        }
      }
    }

    return length;
  }
}

static inline void
addByteToPacket (unsigned char **target, unsigned char byte) {
  if ((byte == CN_PACKET_ESCAPE_BYTE) || (byte == CN_PACKET_FRAMING_BYTE)) {
    *(*target)++ = CN_PACKET_ESCAPE_BYTE;
    byte ^= CN_PACKET_ESCAPE_BIT;
  }

  *(*target)++ = byte;
}

static int
writePacket (BrailleDisplay *brl, const unsigned char *packet, size_t size) {
  logBytes(LOG_CATEGORY(OUTPUT_PACKETS), "raw", packet, size);

  unsigned char buffer[1 + ((size + 2) * 2) + 1];
  unsigned char *target = buffer;
  *target++ = CN_PACKET_FRAMING_BYTE;

  {
    const unsigned char *source = packet;
    const unsigned char *end = source + size;
    while (source < end) addByteToPacket(&target, *source++);
  }

  {
    uint16_t checksum = makePacketChecksum(brl, packet, size);
    addByteToPacket(&target, (checksum & UINT8_MAX));
    addByteToPacket(&target, (checksum >> 8));
  }

  *target++ = CN_PACKET_FRAMING_BYTE;
  size = target - buffer;
  int ok = writeBraillePacket(brl, NULL, buffer, size);

  if (ok) {
    brl->data->response.waiting = 1;
    startTimePeriod(&brl->data->response.timeout, COMMAND_RESPONSE_TIMEOUT);
    brl->data->response.command = packet[0];
  } else {
    brl->hasFailed = 1;
  }

  return ok;
}

static int
writeSimpleCommand (BrailleDisplay *brl, unsigned char command) {
  const unsigned char packet[] = {command};
  return writePacket(brl, packet, sizeof(packet));
}

static RowEntry *
getRowEntry (BrailleDisplay *brl, unsigned int index) {
  return brl->data->window.rowEntries[index];
}

static void
deallocateRowEntries (BrailleDisplay *brl, unsigned int count) {
  RowEntry ***rowEntries = &brl->data->window.rowEntries;

  if (*rowEntries) {
    while (count > 0) free(getRowEntry(brl, --count));
    free(*rowEntries);
    *rowEntries = NULL;
  }
}

static int
allocateRowEntries (BrailleDisplay *brl) {
  RowEntry ***rowEntries = &brl->data->window.rowEntries;

  if (!(*rowEntries = malloc(ARRAY_SIZE(*rowEntries, brl->textRows)))) {
    logMallocError();
    return 0;
  }

  for (unsigned int index=0; index<brl->textRows; index+=1) {
    RowEntry **row = &(*rowEntries)[index];
    size_t rowLength = brl->textColumns;
    size_t size = sizeof(**row) + (rowLength * 2);

    if (!(*row = malloc(size))) {
      logMallocError();
      deallocateRowEntries(brl, (index + 1));
      return 0;
    }

    memset(*row, 0, size);
    (*row)->force = 1;
    (*row)->oldCells = (*row)->newCells + rowLength;
  }

  return 1;
}

static void
setRowHasChanged (BrailleDisplay *brl, unsigned int index) {
  getRowEntry(brl, index)->haveNewCells = 1;
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "row has changed: %u", index);

  if (index < brl->data->window.firstChangedRow) {
    logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "first changed row: %u", index);
    brl->data->window.firstChangedRow = index;
  }
}

static void
resendRow (BrailleDisplay *brl) {
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "resending row: %u", brl->data->window.lastRowSent);
  setRowHasChanged(brl, brl->data->window.lastRowSent);
}

static int
refreshAllRows (BrailleDisplay *brl) {
  brl->data->window.resetCells = 1;
  return 1;
}

static int
refreshRow (BrailleDisplay *brl, int row) {
  return refreshAllRows(brl); // for now
}

ASYNC_ALARM_CALLBACK(CN_keysPoller) {
  BrailleDisplay *brl = parameters->data;

  if (!brl->data->response.waiting) {
    writeSimpleCommand(brl, CN_CMD_PRESSED_KEYS);
  } else if (afterTimePeriod(&brl->data->response.timeout, NULL)) {
    unsigned char command = brl->data->response.command;
    logMessage(LOG_WARNING, "command response timeout: Cmd:0X%02X", command);

    switch (command) {
      case CN_CMD_SEND_ROW:
        resendRow(brl);
        break;

      case CN_CMD_RESET_CELLS:
        brl->data->window.resetCells = 1;;
        break;

      default:
        break;
    }

    writeSimpleCommand(brl, CN_CMD_DEVICE_STATUS);
  }
}

static void
stopKeysPoller (BrailleDisplay *brl) {
  AsyncHandle *alarm = &brl->data->keysPollerAlarm;

  if (*alarm) {
    asyncCancelRequest(*alarm);
    *alarm = NULL;
  }
}

static int
startKeysPoller (BrailleDisplay *brl) {
  AsyncHandle alarm = brl->data->keysPollerAlarm;
  if (alarm) return 1;

  if (asyncNewRelativeAlarm(&alarm, 0, CN_keysPoller, brl)) {
    if (asyncResetAlarmInterval(alarm, KEYS_POLL_INTERVAL)) {
      brl->data->keysPollerAlarm = alarm;
      return 1;
    }

    asyncCancelRequest(alarm);
  }

  return 0;
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  brl->data->response.waiting = 0;
  ProbeResponseHandler *handler = brl->data->probe.responseHandler;
  brl->data->probe.responseHandler = NULL;
  return handler(brl, packet, size);
}

static BrailleResponseResult
writeProbeCommand (BrailleDisplay *brl, unsigned char command, ProbeResponseHandler *handler) {
  if (!writeSimpleCommand(brl, command)) return 0;
  brl->data->probe.responseHandler = handler;
  return 1;
}

static BrailleResponseResult
writeNextProbeCommand (BrailleDisplay *brl, unsigned char command, ProbeResponseHandler *handler) {
  return writeProbeCommand(brl, command, handler)? BRL_RSP_CONTINUE: BRL_RSP_FAIL;
}

static BrailleResponseResult
handleDeviceStatus (BrailleDisplay *brl, const unsigned char *response, size_t size) {
  if (response[0] != CN_CMD_DEVICE_STATUS) return BRL_RSP_UNEXPECTED;
  brl->data->status.flags = CN_getResponseResult(response);
  return BRL_RSP_DONE;
}

static BrailleResponseResult
handleFirmwareVersion (BrailleDisplay *brl, const unsigned char *response, size_t size) {
  if (response[0] != CN_CMD_FIRMWARE_VERSION) return BRL_RSP_UNEXPECTED;

  response += 1;
  size -= 1;
  logMessage(LOG_INFO, "Firmware Version: %.*s", (int)size, response);

  return writeNextProbeCommand(brl, CN_CMD_DEVICE_STATUS, handleDeviceStatus);
}

static BrailleResponseResult
handleProtocolVersion (BrailleDisplay *brl, const unsigned char *response, size_t size) {
  if (response[0] != CN_CMD_PROTOCOL_VERSION) return BRL_RSP_UNEXPECTED;
  brl->data->probe.protocolVersion = CN_getResponseResult(response);
  logMessage(LOG_INFO, "Protocol Version: %u", brl->data->probe.protocolVersion);
  return writeNextProbeCommand(brl, CN_CMD_FIRMWARE_VERSION, handleFirmwareVersion);
}

static BrailleResponseResult
handleRowCount (BrailleDisplay *brl, const unsigned char *response, size_t size) {
  if (response[0] != CN_CMD_ROW_COUNT) return BRL_RSP_UNEXPECTED;
  brl->textRows = CN_getResponseResult(response);
  return writeNextProbeCommand(brl, CN_CMD_PROTOCOL_VERSION, handleProtocolVersion);
}

static BrailleResponseResult
handleColumnCount (BrailleDisplay *brl, const unsigned char *response, size_t size) {
  if (response[0] != CN_CMD_COLUMN_COUNT) return BRL_RSP_UNEXPECTED;
  brl->textColumns = CN_getResponseResult(response);
  return writeNextProbeCommand(brl, CN_CMD_ROW_COUNT, handleRowCount);
}

static int
writeIdentifyRequest (BrailleDisplay *brl) {
  return writeProbeCommand(brl, CN_CMD_COLUMN_COUNT, handleColumnCount);
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 9600
  };

  BEGIN_USB_STRING_LIST(usbManufacturers_16C0_05E1)
    "bristolbraille.co.uk",
  END_USB_STRING_LIST

  BEGIN_USB_STRING_LIST(usbProducts_16C0_05E1)
    "Canute 360",
  END_USB_STRING_LIST

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* all models */
      .vendor=0X16C0, .product=0X05E1,
      .manufacturers = usbManufacturers_16C0_05E1,
      .products = usbProducts_16C0_05E1,
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=3, .outputEndpoint=2,
      .serial = &serialParameters,
      .resetDevice = 1
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    brl->data->crcGenerator = NULL;
    brl->data->keysPollerAlarm = NULL;

    brl->data->probe.responseHandler = NULL;
    brl->data->probe.protocolVersion = 0;

    brl->data->response.waiting = 0;

    startTimePeriod(&brl->data->window.retryDelay, 0);
    brl->data->window.rowEntries = NULL;
    brl->data->window.resetCells = 0;

    brl->data->keys.pressed = 0;

    {
      static const CRCAlgorithm algorithm = {
        .primaryName = CN_CRC_ALGORITHM_NAME,
        .checksumWidth = CN_CRC_CHECKSUM_WIDTH,
        .reflectData = CN_CRC_REFLECT_DATA,
        .reflectResult = CN_CRC_REFLECT_RESULT,
        .generatorPolynomial = UINT16_C(CN_CRC_GENERATOR_POLYNOMIAL),
        .initialValue = UINT16_C(CN_CRC_INITIAL_VALUE),
        .xorMask = UINT16_C(CN_CRC_XOR_MASK),
        .checkValue = UINT16_C(CN_CRC_CHECK_VALUE),
        .residue = UINT16_C(CN_CRC_RESIDUE),
      };

      brl->data->crcGenerator = crcNewGenerator(&algorithm);
    }

    if (brl->data->crcGenerator) {
      if (connectResource(brl, device)) {
        unsigned char response[MAXIMUM_RESPONSE_SIZE];

        if (probeBrailleDisplay(brl, PROBE_RETRY_LIMIT,
                                NULL, PROBE_RESPONSE_TIMEOUT,
                                writeIdentifyRequest,
                                readPacket, &response, sizeof(response),
                                isIdentityResponse)) {
          if (allocateRowEntries(brl)) {
            brl->refreshBrailleDisplay = refreshAllRows;
            brl->refreshBrailleRow = refreshRow;
            brl->cellSize = 6;

            setBrailleKeyTable(brl, &KEY_TABLE_DEFINITION(all));
            makeOutputTable(dotsTable_ISO11548_1);

            if (startKeysPoller(brl)) {
              return 1;
            }

            deallocateRowEntries(brl, brl->textRows);
          }
        }

        disconnectBrailleResource(brl, NULL);
      }

      crcDestroyGenerator(brl->data->crcGenerator);
    }

    free(brl->data);
  } else {
    logMallocError();
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  stopKeysPoller(brl);
  disconnectBrailleResource(brl, NULL);

  deallocateRowEntries(brl, brl->textRows);
  crcDestroyGenerator(brl->data->crcGenerator);

  free(brl->data);
  brl->data = NULL;
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  unsigned int length = brl->textColumns;
  const unsigned char *cells = brl->buffer;

  for (unsigned int index=0; index<brl->textRows; index+=1) {
    RowEntry *row = getRowEntry(brl, index);

    if (cellsHaveChanged(row->newCells, cells, length, NULL, NULL, &row->force)) {
      setRowHasChanged(brl, index);
    }

    cells += length;
  }

  return 1;
}

static int
startUpdate (BrailleDisplay *brl) {
  if (!afterTimePeriod(&brl->data->window.retryDelay, NULL)) return 0;

  if (brl->data->window.resetCells) {
    brl->data->window.resetCells = 0;
    brl->data->window.firstChangedRow = 0;

    for (unsigned int index=0; index<brl->textRows; index+=1) {
      RowEntry *row = getRowEntry(brl, index);
      row->haveNewCells = 1;
      row->haveOldCells = 0;
    }

    writeSimpleCommand(brl, CN_CMD_RESET_CELLS);
    return 1;
  }

  while (brl->data->window.firstChangedRow < brl->textRows) {
    RowEntry *row = getRowEntry(brl, brl->data->window.firstChangedRow);

    if (row->haveNewCells) {
      unsigned int length = brl->textColumns;

      if (row->haveOldCells) {
        if (memcmp(row->newCells, row->oldCells, length) == 0) {
          row->haveNewCells = 0;
        }
      }

      if (row->haveNewCells) {
        unsigned char packet[2 + length];
        unsigned char *byte = packet;

        *byte++ = CN_CMD_SEND_ROW;
        *byte++ = brl->data->window.firstChangedRow;
        byte = translateOutputCells(byte, row->newCells, length);

        size_t size = byte - packet;
        logBytes(LOG_CATEGORY(BRAILLE_DRIVER), "sending row: %u", packet, size, brl->data->window.firstChangedRow);

        if (writePacket(brl, packet, size)) {
          row->haveNewCells = 0;
          brl->data->window.lastRowSent = brl->data->window.firstChangedRow++;
          memcpy(row->oldCells, row->newCells, length);
        }

        return 1;
      }
    }

    brl->data->window.firstChangedRow += 1;
  }

  return 0;
}

static void
startNextCommand (BrailleDisplay *brl) {
  if (!(brl->data->status.flags & CN_STATUS_MOTORS_ACTIVE)) {
    startUpdate(brl);
  } else if (afterTimePeriod(&brl->data->status.delay, NULL)) {
    startTimePeriod(&brl->data->status.delay, MOTORS_POLL_INTERVAL);
    writeSimpleCommand(brl, CN_CMD_DEVICE_STATUS);
  }
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[MAXIMUM_RESPONSE_SIZE];
  size_t size;

  while ((size = readPacket(brl, packet, sizeof(packet)))) {
    brl->data->response.waiting = 0;
    brl->writeDelay = 0;

    unsigned char command = packet[0];
    CN_PacketInteger result = CN_getResponseResult(packet);

    unsigned int motorsTime = 0;

    switch (command) {
      case CN_CMD_PRESSED_KEYS:
        enqueueUpdatedKeys(brl, result, &brl->data->keys.pressed, CN_GRP_NavigationKeys, 0);
        startNextCommand(brl);
        continue;

      case CN_CMD_DEVICE_STATUS:
        brl->data->status.flags = result;
        continue;

      case CN_CMD_SEND_ROW: {
        RowEntry *row = getRowEntry(brl, brl->data->window.lastRowSent);

        if (row->haveOldCells) {
          motorsTime = ROW_UPDATE_TIME;
        } else {
          row->haveOldCells = 1;
        }

        break;
      }

      case CN_CMD_RESET_CELLS:
        motorsTime = CELLS_RESET_TIME;
        break;

      default:
        logUnexpectedPacket(packet, size);
        continue;
    }

    if (result) {
      logMessage(LOG_WARNING,
        "command failed: Cmd:0X%02X Err:0X%02X",
        command, result
      );

      switch (command) {
        case CN_CMD_SEND_ROW:
          resendRow(brl);
          goto UPDATE_FAILED;

        case CN_CMD_RESET_CELLS:
          brl->data->window.resetCells = 1;
          goto UPDATE_FAILED;

        UPDATE_FAILED:
          startTimePeriod(&brl->data->window.retryDelay, UPDATE_RETRY_DELAY);
          continue;

        default:
          continue;;
      }
    } else if (motorsTime) {
      brl->data->status.flags |= CN_STATUS_MOTORS_ACTIVE;
      startTimePeriod(&brl->data->status.delay, motorsTime);
    }
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
