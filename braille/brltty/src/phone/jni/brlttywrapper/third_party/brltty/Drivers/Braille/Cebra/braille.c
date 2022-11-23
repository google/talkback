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

#include "log.h"

#include "brl_driver.h"
#include "brldefs-ce.h"

#define PROBE_RETRY_LIMIT 2
#define PROBE_INPUT_TIMEOUT 1000
#define MAXIMUM_RESPONSE_SIZE (0XFF + 4)
#define MAXIMUM_CELL_COUNT 140

BEGIN_KEY_NAME_TABLE(navigation)
  KEY_NAME_ENTRY(CE_KEY_PadLeft1, "PadLeft1"),
  KEY_NAME_ENTRY(CE_KEY_PadUp1, "PadUp1"),
  KEY_NAME_ENTRY(CE_KEY_PadCenter1, "PadCenter1"),
  KEY_NAME_ENTRY(CE_KEY_PadDown1, "PadDown1"),
  KEY_NAME_ENTRY(CE_KEY_PadRight1, "PadRight1"),

  KEY_NAME_ENTRY(CE_KEY_LeftUpper1, "LeftUpper1"),
  KEY_NAME_ENTRY(CE_KEY_LeftMiddle1, "LeftMiddle1"),
  KEY_NAME_ENTRY(CE_KEY_LeftLower1, "LeftLower1"),
  KEY_NAME_ENTRY(CE_KEY_RightUpper1, "RightUpper1"),
  KEY_NAME_ENTRY(CE_KEY_RightMiddle1, "RightMiddle1"),
  KEY_NAME_ENTRY(CE_KEY_RightLower1, "RightLower1"),

  KEY_NAME_ENTRY(CE_KEY_PadLeft2, "PadLeft2"),
  KEY_NAME_ENTRY(CE_KEY_PadUp2, "PadUp2"),
  KEY_NAME_ENTRY(CE_KEY_PadCenter2, "PadCenter2"),
  KEY_NAME_ENTRY(CE_KEY_PadDown2, "PadDown2"),
  KEY_NAME_ENTRY(CE_KEY_PadRight2, "PadRight2"),

  KEY_NAME_ENTRY(CE_KEY_LeftUpper2, "LeftUpper2"),
  KEY_NAME_ENTRY(CE_KEY_LeftMiddle2, "LeftMiddle2"),
  KEY_NAME_ENTRY(CE_KEY_LeftLower2, "LeftLower2"),
  KEY_NAME_ENTRY(CE_KEY_RightUpper2, "RightUpper2"),
  KEY_NAME_ENTRY(CE_KEY_RightMiddle2, "RightMiddle2"),
  KEY_NAME_ENTRY(CE_KEY_RightLower2, "RightLower2"),

  KEY_GROUP_ENTRY(CE_GRP_RoutingKey, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(novem)
  KEY_NAME_ENTRY(0X03, "Dot7"),
  KEY_NAME_ENTRY(0X07, "Dot3"),
  KEY_NAME_ENTRY(0X0B, "Dot2"),
  KEY_NAME_ENTRY(0X0F, "Dot1"),
  KEY_NAME_ENTRY(0X13, "Dot4"),
  KEY_NAME_ENTRY(0X17, "Dot5"),
  KEY_NAME_ENTRY(0X1B, "Dot6"),
  KEY_NAME_ENTRY(0X1F, "Dot8"),

  KEY_NAME_ENTRY(0X10, "LeftSpace"),
  KEY_NAME_ENTRY(0X18, "RightSpace"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(all)
  KEY_NAME_TABLE(navigation),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(novem)
  KEY_NAME_TABLE(novem),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(all)
DEFINE_KEY_TABLE(novem)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(all),
  &KEY_TABLE_DEFINITION(novem),
END_KEY_TABLE_LIST

typedef struct {
  unsigned char identifier;
  unsigned char cellCount;
  const KeyTableDefinition *ktd;
} ModelEntry;

static const ModelEntry modelTable[] = {
  { .identifier = 0X68,
    .cellCount = 0,
    .ktd = &KEY_TABLE_DEFINITION(novem)
  },

  { .identifier = 0X70,
    .cellCount = 0,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0X72,
    .cellCount = 20,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0X74,
    .cellCount = 40,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0X76,
    .cellCount = 60,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0X78,
    .cellCount = 80,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0X7A,
    .cellCount = 100,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0X7C,
    .cellCount = 120,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0X7E,
    .cellCount = 140,
    .ktd = &KEY_TABLE_DEFINITION(all)
  },

  { .identifier = 0 }
};

struct BrailleDataStruct {
  const ModelEntry *model;
  unsigned char forceRewrite;
  unsigned char acknowledgementPending;
  unsigned char textCells[MAXIMUM_CELL_COUNT];
};

static const ModelEntry *
getModelEntry (unsigned char identifier) {
  const ModelEntry *model = modelTable;

  while (model->identifier) {
    if (identifier == model->identifier) return model;
    model += 1;
  }

  logMessage(LOG_WARNING, "unknown %s model: 0X%02X",
             STRINGIFY(DRIVER_NAME), identifier);
  return NULL;
}

static int
setModel (BrailleDisplay *brl, unsigned char identifier) {
  const ModelEntry *model = getModelEntry(identifier);

  if (model) {
    logMessage(LOG_NOTICE, "%s Model: 0X%02X, %u cells",
               STRINGIFY(DRIVER_NAME), model->identifier, model->cellCount);

    brl->data->model = model;
    brl->textColumns = model->cellCount;
    return 1;
  }

  return 0;
}

static int
writeBytes (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  return writeBraillePacket(brl, NULL, bytes, count);
}

static int
writePacket (BrailleDisplay *brl, unsigned char type, size_t size, const unsigned char *data) {
  unsigned char bytes[size + 5];
  unsigned char *byte = bytes;

  *byte++ = CE_PKT_BEGIN;
  *byte++ = brl->data->model->identifier;
  *byte++ = size + 1;
  *byte++ = type;
  byte = mempcpy(byte, data, size);
  *byte++ = CE_PKT_END;

  return writeBytes(brl, bytes, byte-bytes);
}

static BraillePacketVerifierResult
verifyPacket (
  BrailleDisplay *brl,
  const unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  const unsigned char byte = bytes[size-1];

  if (size == 1) {
    switch (byte) {
      case CE_RSP_Identity:
        *length = 2;
        break;

      case CE_PKT_BEGIN:
        *length = 3;
        break;

      default:
        return BRL_PVR_INVALID;
    }
  } else {
    switch (bytes[0]) {
      case CE_PKT_BEGIN:
        if (size == 2) {
          if (byte != brl->data->model->identifier) {
            if (!setModel(brl, byte)) return BRL_PVR_INVALID;
            brl->resizeRequired = 1;
          }
        } else if (size == 3) {
          *length += byte + 1;
        } else if (size == *length) {
          if (byte != CE_PKT_END) return BRL_PVR_INVALID;
        }
        break;

      default:
        break;
    }
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readPacket (BrailleDisplay *brl, void *packet, size_t size) {
  return readBraillePacket(brl, NULL, packet, size, verifyPacket, NULL);
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 19200,
    .parity = SERIAL_PARITY_ODD
  };

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* all models */
      .vendor=0X0403, .product=0X6001, 
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .serial = &serialParameters
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.usb.channelDefinitions = usbChannelDefinitions;

  descriptor.bluetooth.channelNumber = 1;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
writeIdentityRequest (BrailleDisplay *brl) {
  static const unsigned char bytes[] = {CE_REQ_Identify};
  return writeBytes(brl, bytes, sizeof(bytes));
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const unsigned char *bytes = packet;

  return (bytes[0] == CE_RSP_Identity)? BRL_RSP_DONE: BRL_RSP_UNEXPECTED;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      unsigned char response[MAXIMUM_RESPONSE_SIZE];

      if (probeBrailleDisplay(brl, PROBE_RETRY_LIMIT, NULL, PROBE_INPUT_TIMEOUT,
                              writeIdentityRequest,
                              readPacket, &response, sizeof(response),
                              isIdentityResponse)) {
        if (setModel(brl, response[1])) {
          setBrailleKeyTable(brl, brl->data->model->ktd);
          makeOutputTable(dotsTable_ISO11548_1);

          brl->data->forceRewrite = 1;
          brl->data->acknowledgementPending = 0;

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
  disconnectBrailleResource(brl, NULL);

  if (brl->data) {
    free(brl->data);
    brl->data = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (!brl->data->acknowledgementPending) {
    if (cellsHaveChanged(brl->data->textCells, brl->buffer, brl->textColumns, NULL, NULL, &brl->data->forceRewrite)) {
      unsigned char cells[brl->textColumns];

      translateOutputCells(cells, brl->data->textCells, brl->textColumns);
      if (!writePacket(brl, CE_PKT_REQ_Write, brl->textColumns, cells)) return 0;
      brl->data->acknowledgementPending = 1;
    }
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[MAXIMUM_RESPONSE_SIZE];
  size_t size;

  while ((size = readPacket(brl, packet, sizeof(packet)))) {
    switch (packet[0]) {
      case CE_PKT_BEGIN: {
        const unsigned char *bytes = &packet[4];
        size_t count = packet[2] - 1;

        switch (packet[3]) {
          case CE_PKT_RSP_NavigationKey:
            if (count == 1) {
              KeyGroup group;
              unsigned char key = bytes[0];
              int press = !(key & CE_KEY_RELEASE);
              key &= ~CE_KEY_RELEASE;

              if ((key >= CE_KEY_ROUTING_MIN) && (key <= CE_KEY_ROUTING_MAX)) {
                group = CE_GRP_RoutingKey;
                key -= CE_KEY_ROUTING_MIN;
              } else {
                group = CE_GRP_NavigationKey;
              }

              enqueueKeyEvent(brl, group, key, press);
              continue;
            }
            break;

          case CE_PKT_RSP_Confirmation:
            if (count > 0) {
              switch (bytes[0]) {
                case 0X7D:
                  brl->data->forceRewrite = 1;
                case 0X7E:
                  brl->data->acknowledgementPending = 0;
                  continue;

                default:
                  break;
              }
            }
            break;

          case CE_PKT_RSP_KeyboardKey:
            while (count--) enqueueCommand(BRL_CMD_BLK(PASSAT) | BRL_ARG_PUT(*bytes++));
            continue;

          default:
            break;
        }

        break;
      }

      default:
        break;
    }

    logUnexpectedPacket(packet, size);
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
