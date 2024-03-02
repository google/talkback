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

#include "brl_driver.h"
#include "brldefs-hd.h"

#define PROBE_RETRY_LIMIT 2
#define PROBE_INPUT_TIMEOUT 1000

#define MAXIMUM_RESPONSE_SIZE 3
#define MAXIMUM_TEXT_CELL_COUNT 80
#define MAXIMUM_STATUS_CELL_COUNT 4

BEGIN_KEY_NAME_TABLE(pfl)
  KEY_NAME_ENTRY(HD_PFL_K1, "K1"),
  KEY_NAME_ENTRY(HD_PFL_K2, "K2"),
  KEY_NAME_ENTRY(HD_PFL_K3, "K3"),

  KEY_NAME_ENTRY(HD_PFL_B1, "B1"),
  KEY_NAME_ENTRY(HD_PFL_B2, "B2"),
  KEY_NAME_ENTRY(HD_PFL_B3, "B3"),
  KEY_NAME_ENTRY(HD_PFL_B4, "B4"),
  KEY_NAME_ENTRY(HD_PFL_B5, "B5"),
  KEY_NAME_ENTRY(HD_PFL_B6, "B6"),
  KEY_NAME_ENTRY(HD_PFL_B7, "B7"),
  KEY_NAME_ENTRY(HD_PFL_B8, "B8"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(mbl)
  KEY_NAME_ENTRY(HD_MBL_B1, "B1"),
  KEY_NAME_ENTRY(HD_MBL_B2, "B2"),
  KEY_NAME_ENTRY(HD_MBL_B3, "B3"),

  KEY_NAME_ENTRY(HD_MBL_B4, "B4"),
  KEY_NAME_ENTRY(HD_MBL_B5, "B5"),
  KEY_NAME_ENTRY(HD_MBL_B6, "B6"),

  KEY_NAME_ENTRY(HD_MBL_K1, "K1"),
  KEY_NAME_ENTRY(HD_MBL_K2, "K2"),
  KEY_NAME_ENTRY(HD_MBL_K3, "K3"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing)
  KEY_GROUP_ENTRY(HD_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(pfl)
  KEY_NAME_TABLE(pfl),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(mbl)
  KEY_NAME_TABLE(mbl),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(pfl)
DEFINE_KEY_TABLE(mbl)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(pfl),
  &KEY_TABLE_DEFINITION(mbl),
END_KEY_TABLE_LIST

typedef int KeysPacketInterpreter (BrailleDisplay *brl, const unsigned char *packet);

typedef struct {
  const char *modelName;
  const KeyTableDefinition *keyTableDefinition;

  BraillePacketVerifier *verifyPacket;
  KeysPacketInterpreter *interpretKeysPacket;

  unsigned char textCellCount;
  unsigned char statusCellCount;

  unsigned char firstRoutingKey;
  unsigned char acknowledgementResponse;
} ModelEntry;

struct BrailleDataStruct {
  const ModelEntry *model;

  unsigned char forceRewrite;
  unsigned char textCells[MAXIMUM_TEXT_CELL_COUNT];
  unsigned char statusCells[MAXIMUM_STATUS_CELL_COUNT];

  KeyNumberSet navigationKeys;
};

static BraillePacketVerifierResult
verifyPacket_ProfiLine (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  switch (size) {
    case 1:
      *length = 1;
      break;

    default:
      break;
  }

  return BRL_PVR_INCLUDE;
}

static int
interpretKeysPacket_ProfiLine (BrailleDisplay *brl, const unsigned char *packet) {
  const unsigned char code = packet[0];
  const unsigned char release = 0X80;
  const int press = !(code & release);
  unsigned char key = code & ~release;
  KeyGroup group;

  if (key < brl->data->model->firstRoutingKey) {
    group = HD_GRP_NavigationKeys;
  } else if (key < (brl->data->model->firstRoutingKey + brl->textColumns)) {
    group = HD_GRP_RoutingKeys;
    key -= brl->data->model->firstRoutingKey;
  } else {
    return 0;
  }

  enqueueKeyEvent(brl, group, key, press);
  return 1;
}

static const ModelEntry modelEntry_ProfiLine = {
  .modelName = "ProfiLine USB",
  .keyTableDefinition = &KEY_TABLE_DEFINITION(pfl),

  .verifyPacket = verifyPacket_ProfiLine,
  .interpretKeysPacket = interpretKeysPacket_ProfiLine,

  .textCellCount = 80,
  .statusCellCount = 4,

  .firstRoutingKey = 0X20,
  .acknowledgementResponse = 0X7E
};

static BraillePacketVerifierResult
verifyPacket_MobilLine (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  off_t index = size - 1;
  unsigned char byte = bytes[index];

  if ((byte >> 4) == index) {
    if (index == 0) *length = 3;
  } else if (size == 1) {
    *length = 1;
  } else {
    return BRL_PVR_INVALID;
  }

  return BRL_PVR_INCLUDE;
}

static int
interpretKeysPacket_MobilLine (BrailleDisplay *brl, const unsigned char *packet) {
  const unsigned char *byte = packet;

  if (!(*byte >> 4)) {
    const unsigned char *end = packet + 3;
    KeyNumberSet keys = 0;
    unsigned char shift = 0;

    while (byte < end) {
      keys |= (*byte++ & 0XF) << shift;
      shift += 4;
    }

    enqueueUpdatedKeys(brl, keys, &brl->data->navigationKeys,
                       HD_GRP_NavigationKeys, 0);
    return 1;
  }

  if (*byte >= brl->data->model->firstRoutingKey) {
    unsigned char key = *byte - brl->data->model->firstRoutingKey;

    if (key < brl->textColumns) {
      enqueueKey(brl, HD_GRP_RoutingKeys, key);
      return 1;
    }
  }

  return 0;
}

static const ModelEntry modelEntry_MobilLine = {
  .modelName = "MobilLine USB",
  .keyTableDefinition = &KEY_TABLE_DEFINITION(mbl),

  .verifyPacket = verifyPacket_MobilLine,
  .interpretKeysPacket = interpretKeysPacket_MobilLine,

  .textCellCount = 40,
  .statusCellCount = 2,

  .firstRoutingKey = 0X40,
  .acknowledgementResponse = 0X30
};

static size_t
readPacket (BrailleDisplay *brl, void *packet, size_t size) {
  return readBraillePacket(brl, NULL, packet, size, brl->data->model->verifyPacket, NULL);
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters_ProfiLine = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 19200,
    .parity = SERIAL_PARITY_ODD
  };

  static const SerialParameters serialParameters_MobilLine = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 9600,
    .parity = SERIAL_PARITY_ODD
  };

  BEGIN_USB_STRING_LIST(usbManufacturers_0403_6001)
    "Hedo Reha Technik GmbH",
  END_USB_STRING_LIST

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* ProfiLine */
      .vendor=0X0403, .product=0XDE59,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .serial = &serialParameters_ProfiLine,
      .data = &modelEntry_ProfiLine
    },

    { /* MobilLine */
      .vendor=0X0403, .product=0XDE58,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .serial = &serialParameters_MobilLine,
      .data = &modelEntry_MobilLine
    },

    { /* MobilLine */
      .vendor=0X0403, .product=0X6001,
      .manufacturers = usbManufacturers_0403_6001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .serial = &serialParameters_MobilLine,
      .data = &modelEntry_MobilLine
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.usb.channelDefinitions = usbChannelDefinitions;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static void
disconnectResource (BrailleDisplay *brl) {
  disconnectBrailleResource(brl, NULL);
}

static int
writeCells (BrailleDisplay *brl, int wait) {
  unsigned char packet[1 + brl->data->model->statusCellCount + brl->data->model->textCellCount];
  unsigned char *byte = packet;

  *byte++ = HD_REQ_WRITE_CELLS;
  byte = mempcpy(byte, brl->data->statusCells, brl->data->model->statusCellCount);
  byte = translateOutputCells(byte, brl->data->textCells, brl->data->model->textCellCount);

  {
    size_t count = byte - packet;

    if (wait) return writeBrailleMessage(brl, NULL, 0, packet, count);
    return writeBraillePacket(brl, NULL, packet, count);
  }
}

static int
writeIdentifyRequest (BrailleDisplay *brl) {
  memset(brl->data->textCells, 0, sizeof(brl->data->textCells));
  memset(brl->data->statusCells, 0, sizeof(brl->data->statusCells));
  return writeCells(brl, 0);
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const unsigned char *bytes = packet;

  return (bytes[0] == brl->data->model->acknowledgementResponse)? BRL_RSP_DONE: BRL_RSP_UNEXPECTED;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      unsigned char response[MAXIMUM_RESPONSE_SIZE];

      brl->data->model = gioGetApplicationData(brl->gioEndpoint);
      brl->textColumns = brl->data->model->textCellCount;
      makeOutputTable(dotsTable_ISO11548_1);

      if (probeBrailleDisplay(brl, PROBE_RETRY_LIMIT, NULL, PROBE_INPUT_TIMEOUT,
                              writeIdentifyRequest,
                              readPacket, &response, sizeof(response),
                              isIdentityResponse)) {
        setBrailleKeyTable(brl, brl->data->model->keyTableDefinition);

        brl->data->forceRewrite = 1;
        return 1;
      }

      disconnectResource(brl);
    }

    free(brl->data);
  } else {
    logMallocError();
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  disconnectResource(brl);

  if (brl->data) {
    free(brl->data);
    brl->data = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (cellsHaveChanged(brl->data->textCells, brl->buffer, brl->textColumns, NULL, NULL, &brl->data->forceRewrite)) {
    if (!writeCells(brl, 1)) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[MAXIMUM_RESPONSE_SIZE];
  size_t size;

  while ((size = readPacket(brl, packet, sizeof(packet)))) {
    if (packet[0] == brl->data->model->acknowledgementResponse) {
      acknowledgeBrailleMessage(brl);
    } else if (!brl->data->model->interpretKeysPacket(brl, packet)) {
      logUnexpectedPacket(packet, size);
    }
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
