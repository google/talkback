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
#include "brldefs-mm.h"

#define PROBE_RETRY_LIMIT 2
#define PROBE_INPUT_TIMEOUT 1000
#define START_INPUT_TIMEOUT 1000

#define MM_KEY_GROUP_ENTRY(s,n) BRL_KEY_GROUP_ENTRY(MM, s, n)
#define MM_KEY_NAME_ENTRY(s,k,n) BRL_KEY_NAME_ENTRY(MM, s, k, n)

#define MM_SHIFT_KEY_ENTRY(k,n) MM_KEY_NAME_ENTRY(SHIFT, k, n)
#define MM_DOT_KEY_ENTRY(k) MM_KEY_NAME_ENTRY(DOT, k, "dot" #k)
#define MM_EDIT_KEY_ENTRY(k,n) MM_KEY_NAME_ENTRY(EDIT, k, n)
#define MM_ARROW_KEY_ENTRY(k,n) MM_KEY_NAME_ENTRY(ARROW, k, n)
#define MM_DISPLAY_KEY_ENTRY(k,n) MM_KEY_NAME_ENTRY(DISPLAY, k, n)

BEGIN_KEY_NAME_TABLE(shift)
  MM_SHIFT_KEY_ENTRY(F1, "PanLeft"),
  MM_SHIFT_KEY_ENTRY(F3, "Extension"),
  MM_SHIFT_KEY_ENTRY(F4, "PanRight"),

  MM_SHIFT_KEY_ENTRY(F1, "F1"),
  MM_SHIFT_KEY_ENTRY(F2, "F2"),
  MM_SHIFT_KEY_ENTRY(F3, "F3"),
  MM_SHIFT_KEY_ENTRY(F4, "F4"),

  MM_SHIFT_KEY_ENTRY(CONTROL, "Control"),
  MM_SHIFT_KEY_ENTRY(ALT, "Alt"),
  MM_SHIFT_KEY_ENTRY(SELECT, "Select"),
  MM_SHIFT_KEY_ENTRY(READ, "Read"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(dot)
  MM_DOT_KEY_ENTRY(1),
  MM_DOT_KEY_ENTRY(2),
  MM_DOT_KEY_ENTRY(3),
  MM_DOT_KEY_ENTRY(4),
  MM_DOT_KEY_ENTRY(5),
  MM_DOT_KEY_ENTRY(6),
  MM_DOT_KEY_ENTRY(7),
  MM_DOT_KEY_ENTRY(8),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(edit)
  MM_EDIT_KEY_ENTRY(ESC, "Escape"),
  MM_EDIT_KEY_ENTRY(INF, "Info"),

  MM_EDIT_KEY_ENTRY(BS, "Backspace"),
  MM_EDIT_KEY_ENTRY(DEL, "Delete"),
  MM_EDIT_KEY_ENTRY(INS, "Insert"),

  MM_EDIT_KEY_ENTRY(CHANGE, "Change"),
  MM_EDIT_KEY_ENTRY(OK, "OK"),
  MM_EDIT_KEY_ENTRY(SET, "Set"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(arrow)
  MM_ARROW_KEY_ENTRY(UP, "ArrowUp"),
  MM_ARROW_KEY_ENTRY(DOWN, "ArrowDown"),
  MM_ARROW_KEY_ENTRY(LEFT, "ArrowLeft"),
  MM_ARROW_KEY_ENTRY(RIGHT, "ArrowRight"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(route)
  MM_KEY_GROUP_ENTRY(ROUTE, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(display)
  MM_DISPLAY_KEY_ENTRY(BACKWARD, "Backward"),
  MM_DISPLAY_KEY_ENTRY(FORWARD, "Forward"),

  MM_DISPLAY_KEY_ENTRY(LSCROLL, "ScrollLeft"),
  MM_DISPLAY_KEY_ENTRY(RSCROLL, "ScrollRight"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(pocket)
  KEY_NAME_TABLE(shift),
  KEY_NAME_TABLE(dot),
  KEY_NAME_TABLE(edit),
  KEY_NAME_TABLE(arrow),
  KEY_NAME_TABLE(route),
  KEY_NAME_TABLE(display),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(smart)
  KEY_NAME_TABLE(shift),
  KEY_NAME_TABLE(dot),
  KEY_NAME_TABLE(edit),
  KEY_NAME_TABLE(arrow),
  KEY_NAME_TABLE(route),
  KEY_NAME_TABLE(display),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(pocket)
DEFINE_KEY_TABLE(smart)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(pocket),
  &KEY_TABLE_DEFINITION(smart),
END_KEY_TABLE_LIST

typedef struct {
  const char *identityPrefix;
  const char *modelName;
  const KeyTableDefinition *keyTableDefinition;
} ModelEntry;

static const ModelEntry modelEntry_pocket = {
  .identityPrefix = "BMpk",
  .modelName = "Braille Memo Pocket",
  .keyTableDefinition = &KEY_TABLE_DEFINITION(pocket)
};

static const ModelEntry modelEntry_smart = {
  .identityPrefix = "BMsmart",
  .modelName = "Braille Memo Smart",
  .keyTableDefinition = &KEY_TABLE_DEFINITION(smart)
};

static const ModelEntry *const modelEntries[] = {
  &modelEntry_pocket,
  &modelEntry_smart,
  NULL
};

struct BrailleDataStruct {
  const ModelEntry *model;

  unsigned char forceRewrite;
  unsigned char textCells[MM_MAXIMUM_LINE_LENGTH];
};

static const unsigned char sizeTable[] = {16, 24, 32, 40, 46};
static const unsigned char sizeCount = ARRAY_COUNT(sizeTable);

static int
isValidSize (unsigned char size) {
  return memchr(sizeTable, size, sizeCount) != NULL;
}

static int
writeBytes (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  return writeBraillePacket(brl, NULL, bytes, count);
}

static int
writePacket (
  BrailleDisplay *brl,
  unsigned char code, unsigned char subcode,
  const unsigned char *data, size_t length
) {
  unsigned char bytes[sizeof(MM_CommandHeader) + length];
  unsigned char *byte = bytes;

  *byte++ = MM_HEADER_ID1;
  *byte++ = MM_HEADER_ID2;

  *byte++ = code;
  *byte++ = subcode;

  *byte++ = (length >> 0) & 0XFF;
  *byte++ = (length >> 8) & 0XFF;

  if (data) byte = mempcpy(byte, data, length);

  return writeBytes(brl, bytes, byte-bytes);
}

static BraillePacketVerifierResult
verifyPacket (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      switch (byte) {
        case MM_HEADER_ACK:
        case MM_HEADER_NAK:
          *length = 1;
          break;

        case MM_HEADER_ID1:
          *length = sizeof(MM_CommandHeader);
          break;

        default:
          if (isValidSize(byte)) {
            *length = 1;
            break;
          }

          return BRL_PVR_INVALID;
      }
      break;

    case 2:
      if (byte != MM_HEADER_ID2) return BRL_PVR_INVALID;
      break;

    case 5:
      *length += byte;
      break;

    case 6:
      *length += byte << 8;
      break;

    default:
      break;
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readBytes (BrailleDisplay *brl, void *packet, size_t size) {
  return readBraillePacket(brl, NULL, packet, size, verifyPacket, NULL);
}

static size_t
readPacket (BrailleDisplay *brl, MM_CommandPacket *packet) {
  return readBytes(brl, packet, sizeof(*packet));
}

static int
startDisplayMode (BrailleDisplay *brl) {
  static const unsigned char data[] = {MM_BLINK_NO, 0};

  if (writePacket(brl, MM_CMD_StartDisplayMode, 0, data, sizeof(data))) {
    if (awaitBrailleInput(brl, START_INPUT_TIMEOUT)) {
      MM_CommandPacket response;
      size_t size = readPacket(brl, &response);

      if (size) {
        if (response.fields.header.id1 == MM_HEADER_ACK) return 1;
        logUnexpectedPacket(response.bytes, size);
      }
    }
  }

  return 0;
}

static int
endDisplayMode (BrailleDisplay *brl) {
  return writePacket(brl, MM_CMD_EndDisplayMode, 0, NULL, 0);
}

static int
sendBrailleData (BrailleDisplay *brl, const unsigned char *cells, size_t count) {
  return writePacket(brl, MM_CMD_SendBrailleData, 0, cells, count);
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 9600
  };

  BEGIN_USB_STRING_LIST(usbManufacturers_10C4_EA60)
    "Silicon Labs",
  END_USB_STRING_LIST

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* Pocket */
      .vendor=0X10C4, .product=0XEA60,
      .manufacturers = usbManufacturers_10C4_EA60,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .serial=&serialParameters
    },

    { /* Smart */
      .vendor=0X1148, .product=0X0301,
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=3, .outputEndpoint=2,
      .serial=&serialParameters
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;

  descriptor.bluetooth.channelNumber = 1;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
detectModel (BrailleDisplay *brl, const MM_IdentityPacket *identity) {
  const ModelEntry *const *model = modelEntries;

  while (*model) {
    const char *prefix = (*model)->identityPrefix;

    if (strncmp(identity->hardwareName, prefix, strlen(prefix)) == 0) {
      brl->data->model = *model;
      logMessage(LOG_INFO, "detected model: %s", brl->data->model->modelName);
      return 1;
    }

    model += 1;
  }

  logMessage(LOG_WARNING, "unrecognized model: %s", identity->hardwareName);
  brl->data->model = &modelEntry_pocket;
  logMessage(LOG_INFO, "assumed model: %s", brl->data->model->modelName);
  return 0;
}

static int
writeIdentifyRequest (BrailleDisplay *brl) {
  return writePacket(brl, MM_CMD_QueryIdentity, 0, NULL, 0);
}

static BraillePacketVerifierResult
verifyIdentityResponse (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      switch (byte) {
        case 0X01:
          *length = sizeof(MM_IdentityPacket);
          break;

        default:
          return BRL_PVR_INVALID;
      }
      break;

    default:
      break;
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readIdentityResponse (BrailleDisplay *brl, void *packet, size_t size) {
  return readBraillePacket(brl, NULL, packet, size, verifyIdentityResponse, NULL);
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const MM_IdentityPacket *identity = packet;

  if ((identity->lineLength == 0) || (identity->lineLength > MM_MAXIMUM_LINE_LENGTH)) return BRL_RSP_UNEXPECTED;
  if ((identity->lineCount == 0) || (identity->lineCount > MM_MAXIMUM_LINE_COUNT)) return BRL_RSP_UNEXPECTED;

  {
    const char *byte = identity->hardwareName;
    const char *end = byte + sizeof(identity->hardwareName);

    while (byte < end) {
      if (!*byte) break;
      if (!iswprint(*byte)) return BRL_RSP_UNEXPECTED;
      byte += 1;
    }
  }

  return BRL_RSP_DONE;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      MM_IdentityPacket identity;

      if (probeBrailleDisplay(brl, PROBE_RETRY_LIMIT, NULL, PROBE_INPUT_TIMEOUT,
                              writeIdentifyRequest,
                              readIdentityResponse, &identity, sizeof(identity),
                              isIdentityResponse)) {
        detectModel(brl, &identity);
        brl->textColumns = identity.lineLength;

        if (startDisplayMode(brl)) {
          setBrailleKeyTable(brl, brl->data->model->keyTableDefinition);
          MAKE_OUTPUT_TABLE(0X80, 0X40, 0X20, 0X08, 0X04, 0X02, 0X10, 0X01);

          brl->data->forceRewrite = 1;
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
  disconnectBrailleResource(brl, endDisplayMode);

  if (brl->data) {
    free(brl->data);
    brl->data = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (cellsHaveChanged(brl->data->textCells, brl->buffer, brl->textColumns, NULL, NULL, &brl->data->forceRewrite)) {
    unsigned char cells[brl->textColumns];

    translateOutputCells(cells, brl->data->textCells, brl->textColumns);
    if (!sendBrailleData(brl, cells, sizeof(cells))) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  MM_CommandPacket packet;
  size_t size;

  while ((size = readPacket(brl, &packet))) {
    if ((packet.fields.header.id1 == MM_HEADER_ID1) &&
        (packet.fields.header.id2 == MM_HEADER_ID2)) {
      switch (packet.fields.header.code) {
        case MM_CMD_KeyCombination:
          switch (packet.fields.data.keys.group) {
            case MM_GRP_SHIFT:
              if (!packet.fields.data.keys.value) {
                enqueueKeys(brl, packet.fields.data.keys.shift, MM_GRP_SHIFT, 0);
                continue;
              }
              break;

            case MM_GRP_DOT:
            case MM_GRP_EDIT:
            case MM_GRP_ARROW:
            case MM_GRP_DISPLAY:
            {
              KeyNumberSet shift = 0;

              enqueueUpdatedKeys(brl, packet.fields.data.keys.shift, &shift, MM_GRP_SHIFT, 0);
              enqueueKeys(brl, packet.fields.data.keys.value, packet.fields.data.keys.group, 0);
              enqueueUpdatedKeys(brl, 0, &shift, MM_GRP_SHIFT, 0);
              continue;
            }

            case MM_GRP_ROUTE:
            {
              unsigned char key = packet.fields.data.keys.value;

              if ((key > 0) && (key <= brl->textColumns)) {
                KeyNumberSet shift = 0;

                enqueueUpdatedKeys(brl, packet.fields.data.keys.shift, &shift, MM_GRP_SHIFT, 0);
                enqueueKey(brl, packet.fields.data.keys.group, key-1);
                enqueueUpdatedKeys(brl, 0, &shift, MM_GRP_SHIFT, 0);
                continue;
              }

              break;
            }

            default:
              break;
          }
          break;

        case MM_CMD_ShiftPress:
        case MM_CMD_ShiftRelease:
          continue;

        default:
          break;
      }
    }

    logUnexpectedPacket(packet.bytes, size);
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
