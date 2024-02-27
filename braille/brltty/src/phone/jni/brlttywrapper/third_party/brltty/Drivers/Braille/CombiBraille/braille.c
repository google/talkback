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
#include "ascii.h"

#define BRL_STATUS_FIELDS sfCursorAndWindowColumn2, sfCursorAndWindowRow2, sfStateDots
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"

#include "brldefs-cb.h"
#include "braille.h"

BEGIN_KEY_NAME_TABLE(dot)
  KEY_NAME_ENTRY(CB_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(CB_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(CB_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(CB_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(CB_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(CB_KEY_Dot6, "Dot6"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(thumb)
  KEY_NAME_ENTRY(CB_KEY_Thumb1, "Thumb1"),
  KEY_NAME_ENTRY(CB_KEY_Thumb2, "Thumb2"),
  KEY_NAME_ENTRY(CB_KEY_Thumb3, "Thumb3"),
  KEY_NAME_ENTRY(CB_KEY_Thumb4, "Thumb4"),
  KEY_NAME_ENTRY(CB_KEY_Thumb5, "Thumb5"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(status)
  KEY_NAME_ENTRY(CB_KEY_Status1, "Status1"),
  KEY_NAME_ENTRY(CB_KEY_Status2, "Status2"),
  KEY_NAME_ENTRY(CB_KEY_Status3, "Status3"),
  KEY_NAME_ENTRY(CB_KEY_Status4, "Status4"),
  KEY_NAME_ENTRY(CB_KEY_Status5, "Status5"),
  KEY_NAME_ENTRY(CB_KEY_Status6, "Status6"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing)
  KEY_GROUP_ENTRY(CB_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(all)
  KEY_NAME_TABLE(dot),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(status),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(all)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(all),
END_KEY_TABLE_LIST

#define CONNECTION_TIMEOUT 1000
#define CONNECTION_RETRIES 0
#define MAX_INPUT_PACKET_SIZE 4
#define MAX_TEXT_CELLS 80
#define STATUS_CELLS 5

BrailleDisplay *cbBrailleDisplay = NULL;

typedef struct {
  char identifier;
  char textColumns;
} ModelEntry;

static const ModelEntry modelTable[] = {
  { .identifier = 0,
    .textColumns = 20,
  },

  { .identifier = 1,
    .textColumns = 40,
  },

  { .identifier = 2,
    .textColumns = 80,
  },

  { .identifier = 7,
    .textColumns = 20,
  },

  { .identifier = 8,
    .textColumns = 40,
  },

  { .identifier = 9,
    .textColumns = 80,
  },

  { .textColumns = 0 }
};

static const ModelEntry *
findModelEntry (unsigned char identifier) {
  const ModelEntry *model = modelTable;

  while (model->textColumns) {
    if (identifier == model->identifier) return model;
    model += 1;
  }
          
  return NULL;
}

struct BrailleDataStruct {
  const ModelEntry *model;

  struct {
    unsigned char refresh;
    unsigned char previous[MAX_TEXT_CELLS];
  } text;

  struct {
    unsigned char refresh;
    unsigned char current[STATUS_CELLS];
    unsigned char previous[STATUS_CELLS];
  } status;
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
      if (byte != ASCII_ESC) return BRL_PVR_INVALID;
      *length = 2;
      return BRL_PVR_INCLUDE;

    case 2:
      switch (byte) {
        case CB_PKT_KeepAlive:
          *length = 2;
          return BRL_PVR_INCLUDE;

        case CB_PKT_DeviceIdentity:
        case CB_PKT_RoutingKey:
          *length = 3;
          return BRL_PVR_INCLUDE;

        case CB_PKT_NavigationKeys:
          *length = 4;
          return BRL_PVR_INCLUDE;

        default:
          return BRL_PVR_INVALID;
      }

    default:
      return BRL_PVR_INCLUDE;
  }
}

static size_t
readPacket (BrailleDisplay *brl, void *bytes, size_t size) {
  return readBraillePacket(brl, NULL, bytes, size, verifyPacket, NULL);
}

static int
writePacket (BrailleDisplay *brl, const void *bytes, size_t size) {
  return writeBraillePacket(brl, NULL, bytes, size);
}

static int
writeIdentifyRequest (BrailleDisplay *brl) {
  static const unsigned char packet[] = {
    ASCII_ESC, CB_PKT_DeviceIdentity
  };

  return writePacket(brl, packet, sizeof(packet));
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const unsigned char *bytes = packet;
  return (bytes[1] == CB_PKT_DeviceIdentity)? BRL_RSP_DONE: BRL_RSP_UNEXPECTED;
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = CB_SERIAL_BAUD,
    .flowControl = SERIAL_FLOW_HARDWARE,
  };

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));
    brl->data->text.refresh = 1;
    brl->data->status.refresh = 1;

    if (connectResource(brl, device)) {
      unsigned char response[MAX_INPUT_PACKET_SIZE];

      int detected = probeBrailleDisplay(
        brl, CONNECTION_RETRIES, NULL, CONNECTION_TIMEOUT,
        writeIdentifyRequest,
        readPacket, response, sizeof(response),
        isIdentityResponse
      );

      if (detected) {
        unsigned char identifier = response[2];

        if ((brl->data->model = findModelEntry(identifier))) {
          brl->textColumns = brl->data->model->textColumns;
          brl->textRows = 1;

          brl->statusColumns = STATUS_CELLS;
          brl->statusRows = 1;

          setBrailleKeyTable(brl, &KEY_TABLE_DEFINITION(all));
          MAKE_OUTPUT_TABLE(0X01, 0X02, 0X04, 0X80, 0X40, 0X20, 0X08, 0X10);

          cbBrailleDisplay = brl;
          return 1;
        } else {
          logMessage(LOG_ERR, "detected unknown CombiBraille model with ID %02X", identifier);
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
  cbBrailleDisplay = NULL;
  disconnectBrailleResource(brl, NULL);
  free(brl->data);
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *s) {
  memcpy(brl->data->status.current, s, brl->statusColumns);
  return 1;
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  int textChanged = cellsHaveChanged(
    brl->data->text.previous, brl->buffer, brl->textColumns,
    NULL, NULL, &brl->data->text.refresh
  );

  int statusChanged = cellsHaveChanged(
    brl->data->status.previous,
    brl->data->status.current,
    brl->statusColumns,
    NULL, NULL, &brl->data->status.refresh
  );

  /* Only refresh display if the data has changed: */
  if (textChanged || statusChanged) {
    static const unsigned char header[] = {
      ASCII_ESC, CB_PKT_WriteCells
    };

    unsigned char buffer[sizeof(header) + ((brl->statusColumns + brl->textColumns) * 2)];

    unsigned char *byte = buffer;
    byte = mempcpy(byte, header, sizeof(header));

    for (int i=0; i<brl->statusColumns; i+=1) {
      const unsigned char c = translateOutputCell(brl->data->status.current[i]);
      if (c == ASCII_ESC) *byte++ = c;
      *byte++ = c;
    }

    for (int i=0; i<brl->textColumns; i+=1) {
      const unsigned char c = translateOutputCell(brl->buffer[i]);
      if (c == ASCII_ESC) *byte++ = c;
      *byte++ = c;
    }

    {
      const size_t size = byte - buffer;
      if (!writePacket(brl, buffer, size)) return 0;
    }
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[MAX_INPUT_PACKET_SIZE];
  size_t length;

  while ((length = readPacket(brl, packet, sizeof(packet)))) {
    switch (packet[1]) {
      case CB_PKT_KeepAlive:
        continue;

      case CB_PKT_RoutingKey: {
        char key = packet[2];

        if (key < 6) {
          enqueueKey(brl, CB_GRP_NavigationKeys, (CB_KEY_Status1 + key));
        } else {
          enqueueKey(brl, CB_GRP_RoutingKeys, (key - 6));
        }

        continue;
      }

      case CB_PKT_NavigationKeys: {
        KeyNumberSet keys = packet[2] | (packet[3] << 8);
        enqueueKeys(brl, keys, CB_GRP_NavigationKeys, CB_KEY_Dot6);
        continue;
      }
    }

    logUnexpectedPacket(packet, length);
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
