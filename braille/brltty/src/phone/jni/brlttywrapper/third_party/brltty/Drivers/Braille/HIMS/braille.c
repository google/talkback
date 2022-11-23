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
#include "brldefs-hm.h"

#define MAXIMUM_CELL_COUNT 40

BEGIN_KEY_NAME_TABLE(common)
  KEY_GROUP_ENTRY(HM_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(braille)
  KEY_NAME_ENTRY(HM_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(HM_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(HM_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(HM_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(HM_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(HM_KEY_Dot6, "Dot6"),
  KEY_NAME_ENTRY(HM_KEY_Dot7, "Dot7"),
  KEY_NAME_ENTRY(HM_KEY_Dot8, "Dot8"),
  KEY_NAME_ENTRY(HM_KEY_Space, "Space"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(pan)
  KEY_NAME_ENTRY(HM_KEY_Backward, "Backward"),
  KEY_NAME_ENTRY(HM_KEY_Forward, "Forward"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(BS_scroll)
  KEY_NAME_ENTRY(HM_KEY_BS_LeftScrollUp, "LeftScrollUp"),
  KEY_NAME_ENTRY(HM_KEY_BS_LeftScrollDown, "LeftScrollDown"),
  KEY_NAME_ENTRY(HM_KEY_BS_RightScrollUp, "RightScrollUp"),
  KEY_NAME_ENTRY(HM_KEY_BS_RightScrollDown, "RightScrollDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(BE_scroll)
  KEY_NAME_ENTRY(HM_KEY_BE_LeftScrollUp, "LeftScrollUp"),
  KEY_NAME_ENTRY(HM_KEY_BE_LeftScrollDown, "LeftScrollDown"),
  KEY_NAME_ENTRY(HM_KEY_BE_RightScrollUp, "RightScrollUp"),
  KEY_NAME_ENTRY(HM_KEY_BE_RightScrollDown, "RightScrollDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(f14)
  KEY_NAME_ENTRY(HM_KEY_F1, "F1"),
  KEY_NAME_ENTRY(HM_KEY_F2, "F2"),
  KEY_NAME_ENTRY(HM_KEY_F3, "F3"),
  KEY_NAME_ENTRY(HM_KEY_F4, "F4"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(f58)
  KEY_NAME_ENTRY(HM_KEY_F5, "F5"),
  KEY_NAME_ENTRY(HM_KEY_F6, "F6"),
  KEY_NAME_ENTRY(HM_KEY_F7, "F7"),
  KEY_NAME_ENTRY(HM_KEY_F8, "F8"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(lp)
  KEY_NAME_ENTRY(HM_KEY_LeftPadUp, "LeftPadUp"),
  KEY_NAME_ENTRY(HM_KEY_LeftPadDown, "LeftPadDown"),
  KEY_NAME_ENTRY(HM_KEY_LeftPadLeft, "LeftPadLeft"),
  KEY_NAME_ENTRY(HM_KEY_LeftPadRight, "LeftPadRight"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(rp)
  KEY_NAME_ENTRY(HM_KEY_RightPadUp, "RightPadUp"),
  KEY_NAME_ENTRY(HM_KEY_RightPadDown, "RightPadDown"),
  KEY_NAME_ENTRY(HM_KEY_RightPadLeft, "RightPadLeft"),
  KEY_NAME_ENTRY(HM_KEY_RightPadRight, "RightPadRight"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(pan)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(f14),
  KEY_NAME_TABLE(pan),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(scroll)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(f14),
  KEY_NAME_TABLE(BS_scroll),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(qwerty)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(f14),
  KEY_NAME_TABLE(BS_scroll),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(edge)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(f14),
  KEY_NAME_TABLE(f58),
  KEY_NAME_TABLE(BE_scroll),
  KEY_NAME_TABLE(lp),
  KEY_NAME_TABLE(rp),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLE(SB_scroll)
  KEY_NAME_ENTRY(HM_KEY_SB_LeftScrollUp, "LeftScrollUp"),
  KEY_NAME_ENTRY(HM_KEY_SB_LeftScrollDown, "LeftScrollDown"),
  KEY_NAME_ENTRY(HM_KEY_SB_RightScrollUp, "RightScrollUp"),
  KEY_NAME_ENTRY(HM_KEY_SB_RightScrollDown, "RightScrollDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(sync)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(SB_scroll),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLE(beetle)
  KEY_NAME_ENTRY(HM_KEY_BS_RightScrollUp, "Backward"),
  KEY_NAME_ENTRY(HM_KEY_BS_RightScrollDown, "Forward"),

  KEY_NAME_ENTRY(HM_KEY_F1, "F1"),
  KEY_NAME_ENTRY(HM_KEY_F4, "F2"),
  KEY_NAME_ENTRY(HM_KEY_F3, "F3"),
  KEY_NAME_ENTRY(HM_KEY_F2, "F4"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(beetle)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(beetle),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(pan)
DEFINE_KEY_TABLE(scroll)
DEFINE_KEY_TABLE(qwerty)
DEFINE_KEY_TABLE(edge)
DEFINE_KEY_TABLE(sync)
DEFINE_KEY_TABLE(beetle)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(pan),
  &KEY_TABLE_DEFINITION(scroll),
  &KEY_TABLE_DEFINITION(qwerty),
  &KEY_TABLE_DEFINITION(edge),
  &KEY_TABLE_DEFINITION(sync),
  &KEY_TABLE_DEFINITION(beetle),
END_KEY_TABLE_LIST

typedef enum {
  IPT_CURSOR  = 0X00,
  IPT_KEYS    = 0X01,
  IPT_CELLS   = 0X02
} InputPacketType;

typedef union {
  unsigned char bytes[10];

  struct {
    unsigned char start;
    unsigned char type;
    unsigned char count;
    unsigned char data;
    unsigned char reserved[4];
    unsigned char checksum;
    unsigned char end;
  } PACKED data;
} PACKED InputPacket;

typedef struct {
  const KeyTableDefinition *keyTable;
  unsigned char id1;
  unsigned char id2;
} IdentityEntry;

static const IdentityEntry panIdentity = {
  .keyTable = &KEY_TABLE_DEFINITION(pan)
};

static const IdentityEntry scrollIdentity = {
  .id1 = 0X4C, .id2 = 0X58,
  .keyTable = &KEY_TABLE_DEFINITION(scroll)
};

static const IdentityEntry qwerty2Identity = {
  .id1 = 0X53, .id2 = 0X58,
  .keyTable = &KEY_TABLE_DEFINITION(qwerty)
};

static const IdentityEntry qwerty1Identity = {
  .id1 = 0X51, .id2 = 0X58,
  .keyTable = &KEY_TABLE_DEFINITION(qwerty)
};

static const IdentityEntry edgeIdentity = {
  .id1 = 0X42, .id2 = 0X45,
  .keyTable = &KEY_TABLE_DEFINITION(edge)
};

typedef struct {
  const char *modelName;
  const char *resourceNamePrefix;
  const KeyTableDefinition *keyTable;
  const KeyTableDefinition * (*testIdentities) (BrailleDisplay *brl);
  int (*getDefaultCellCount) (BrailleDisplay *brl, unsigned int *count);
} ProtocolEntry;

struct BrailleDataStruct {
  const ProtocolEntry *protocol;
  unsigned char previousCells[MAXIMUM_CELL_COUNT];
};

static BraillePacketVerifierResult
verifyPacket (
  BrailleDisplay *brl,
  const unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1: {
      switch (byte) {
        case 0X1C:
          *length = 4;
          break;

        case 0XFA:
          *length = 10;
          break;

        default:
          return BRL_PVR_INVALID;
      }

      break;
    }

    default:
      break;
  }

  if (size == *length) {
    switch (bytes[0]) {
      case 0X1C: {
        if (byte != 0X1F) return BRL_PVR_INVALID;
        break;
      }

      case 0XFA: {
        if (byte != 0XFB) return BRL_PVR_INVALID;

        const InputPacket *packet = (const void *)bytes;
        int checksum = -packet->data.checksum;
        for (size_t i=0; i<size; i+=1) checksum += packet->bytes[i];

        if ((checksum & 0XFF) != packet->data.checksum) {
          logInputProblem("incorrect input checksum", packet->bytes, size);
          return BRL_PVR_INVALID;
        }

        break;
      }

      default:
        break;
    }
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readPacket (BrailleDisplay *brl, InputPacket *packet) {
  return readBraillePacket(brl, NULL, packet, sizeof(*packet), verifyPacket, NULL);
}

static size_t
readBytes (BrailleDisplay *brl, void *packet, size_t size) {
  return readPacket(brl, packet);
}

static int
writeBytes (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  return writeBraillePacket(brl, NULL, bytes, count);
}

static int
testIdentity (BrailleDisplay *brl, unsigned char id1, unsigned char id2) {
  const unsigned char sequence[] = {0X1C, id1, id2, 0X1F};
  const size_t length = sizeof(sequence);

  if (writeBytes(brl, sequence, length)) {
    while (awaitBrailleInput(brl, 200)) {
      InputPacket response;
      size_t size = readPacket(brl, &response);
      if (!size) break;

      if (response.bytes[0] == sequence[0]) {
        return memcmp(response.bytes, sequence, length) == 0;
      }
    }
  }

  return 0;
}

static const KeyTableDefinition *
testIdentities (BrailleDisplay *brl, const IdentityEntry *const *identities) {
  while (*identities) {
    const IdentityEntry *identity = *identities;
    const char *name = identity->keyTable->bindings;

    if (!identity->id1 && !identity->id2) {
      logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "assuming identity: %s", name);
    } else {
      logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "testing identity: %s", name);

      if (!testIdentity(brl, identity->id1, identity->id2)) {
        identities += 1;
        continue;
      }
    }

    return identity->keyTable;
  }

  return NULL;
}

static const KeyTableDefinition *
testBrailleSenseIdentities (BrailleDisplay *brl) {
  static const IdentityEntry *const identities[] = {
    &qwerty2Identity,
    &qwerty1Identity,
    &scrollIdentity,
    &panIdentity,
    NULL
  };

  return testIdentities(brl, identities);
}

static const KeyTableDefinition *
testBrailleEdgeIdentities (BrailleDisplay *brl) {
  static const IdentityEntry *const identities[] = {
    &edgeIdentity,
    NULL
  };

  return testIdentities(brl, identities);
}

static int
writePacket (
  BrailleDisplay *brl,
  unsigned char type, unsigned char mode,
  const unsigned char *data1, size_t length1,
  const unsigned char *data2, size_t length2
) {
  unsigned char packet[2 + 1 + 1 + 2 + length1 + 1 + 1 + 2 + length2 + 1 + 4 + 1 + 2];
  unsigned char *byte = packet;
  unsigned char *checksum;

  /* DS */
  *byte++ = type;
  *byte++ = type;

  /* M */
  *byte++ = mode;

  /* DS1 */
  *byte++ = 0XF0;

  /* Cnt1 */
  *byte++ = (length1 >> 0) & 0XFF;
  *byte++ = (length1 >> 8) & 0XFF;

  /* D1 */
  byte = mempcpy(byte, data1, length1);

  /* DE1 */
  *byte++ = 0XF1;

  /* DS2 */
  *byte++ = 0XF2;

  /* Cnt2 */
  *byte++ = (length2 >> 0) & 0XFF;
  *byte++ = (length2 >> 8) & 0XFF;

  /* D2 */
  if (data2) byte = mempcpy(byte, data2, length2);

  /* DE2 */
  *byte++ = 0XF3;

  /* Reserved */
  {
    int count = 4;
    while (count--) *byte++ = 0;
  }

  /* Chk */
  *(checksum = byte++) = 0;

  /* DE */
  *byte++ = 0XFD;
  *byte++ = 0XFD;

  {
    unsigned char sum = 0;
    const unsigned char *ptr = packet;
    while (ptr != byte) sum += *ptr++;
    *checksum = sum;
  }

  return writeBytes(brl, packet, byte - packet);
}


static int
getBrailleSenseDefaultCellCount (BrailleDisplay *brl, unsigned int *count) {
  *count = 32;
  return 1;
}

static const ProtocolEntry brailleSenseProtocol = {
  .modelName = "Braille Sense",
  .keyTable = &KEY_TABLE_DEFINITION(pan),
  .testIdentities = testBrailleSenseIdentities,
  .getDefaultCellCount = getBrailleSenseDefaultCellCount
};


static int
getSyncBrailleDefaultCellCount (BrailleDisplay *brl, unsigned int *count) {
  return 0;
}

static const ProtocolEntry syncBrailleProtocol = {
  .modelName = "SyncBraille",
  .keyTable = &KEY_TABLE_DEFINITION(sync),
  .getDefaultCellCount = getSyncBrailleDefaultCellCount
};


static int
getBrailleEdgeDefaultCellCount (BrailleDisplay *brl, unsigned int *count) {
  *count = 40;
  return 1;
}

static const ProtocolEntry brailleEdgeProtocol = {
  .modelName = "Braille Edge",
  .resourceNamePrefix = "BrailleEDGE",
  .keyTable = &KEY_TABLE_DEFINITION(edge),
  .testIdentities = testBrailleEdgeIdentities,
  .getDefaultCellCount = getBrailleEdgeDefaultCellCount
};


static const ProtocolEntry *protocolTable[] = {
  &brailleSenseProtocol,
  &syncBrailleProtocol,
  &brailleEdgeProtocol,
  NULL
};


static int
writeCells (BrailleDisplay *brl) {
  const size_t count = MIN(brl->textColumns*brl->textRows, MAXIMUM_CELL_COUNT);
  unsigned char cells[count];

  translateOutputCells(cells, brl->data->previousCells, count);
  return writePacket(brl, 0XFC, 0X01, cells, count, NULL, 0);
}

static int
clearCells (BrailleDisplay *brl) {
  memset(brl->data->previousCells, 0, MIN(brl->textColumns*brl->textRows, MAXIMUM_CELL_COUNT));
  return writeCells(brl);
}

static int
writeCellCountRequest (BrailleDisplay *brl) {
  static const unsigned char data[] = {
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
  };

  return writePacket(brl, 0XFB, 0X01, data, sizeof(data), NULL, 0);
}

static BrailleResponseResult
isCellCountResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const InputPacket *response = packet;

  return (response->data.type == IPT_CELLS)? BRL_RSP_DONE: BRL_RSP_UNEXPECTED;
}

static int
getCellCount (BrailleDisplay *brl, unsigned int *count) {
  InputPacket response;

  if (probeBrailleDisplay(brl, 2, NULL, 1000,
                          writeCellCountRequest,
                          readBytes, &response, sizeof(response.bytes),
                          isCellCountResponse)) {
    *count = response.data.data;
    return 1;
  }

  return brl->data->protocol->getDefaultCellCount(brl, count);
}

static void
setKeyTable (BrailleDisplay *brl, const KeyTableDefinition *ktd) {
  if (!ktd) ktd = brl->data->protocol->keyTable;

  switch (brl->textColumns) {
    case 14:
      if (ktd == &KEY_TABLE_DEFINITION(scroll)) {
        ktd = &KEY_TABLE_DEFINITION(beetle);
      }
      break;
  }

  setBrailleKeyTable(brl, ktd);
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 115200
  };

  BEGIN_USB_STRING_LIST(usbManufacturers_0403_6001)
    "FTDI",
  END_USB_STRING_LIST

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* Braille Sense (USB 1.1) */
      .version = UsbSpecificationVersion_1_1,
      .vendor=0X045E, .product=0X930A,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&brailleSenseProtocol
    },

    { /* Braille Sense (USB 2.0) */
      .version = UsbSpecificationVersion_2_0,
      .vendor=0X045E, .product=0X930A,
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface=1,
      .disableAutosuspend=1,
      .data=&brailleSenseProtocol
    },

    { /* Braille Sense U2 (USB 2.0) */
      .version = UsbSpecificationVersion_2_0,
      .vendor=0X045E, .product=0X930A,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface=1,
      .disableAutosuspend=1,
      .data=&brailleSenseProtocol
    },

    { /* Sync Braille */
      .vendor=0X0403, .product=0X6001,
      .manufacturers = usbManufacturers_0403_6001,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data=&syncBrailleProtocol
    },

    { /* Braille Edge */
      .vendor=0X045E, .product=0X930B,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .disableAutosuspend=1,
      .data=&brailleEdgeProtocol
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;
  descriptor.serial.options.applicationData = &brailleSenseProtocol;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;

  descriptor.bluetooth.channelNumber = 4;
  descriptor.bluetooth.discoverChannel = 1;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      if (!(brl->data->protocol = gioGetApplicationData(brl->gioEndpoint))) {
        char *name = gioGetResourceName(brl->gioEndpoint);
        brl->data->protocol = &brailleSenseProtocol;

        if (name) {
          const ProtocolEntry *const *protocolAddress = protocolTable;

          while (*protocolAddress) {
            const ProtocolEntry *protocol = *protocolAddress;
            const char *prefix = protocol->resourceNamePrefix;

            if (prefix) {
              if (strncasecmp(name, prefix, strlen(prefix)) == 0) {
                brl->data->protocol = protocol;
                break;
              }
            }

            protocolAddress += 1;
          }

          free(name);
        }
      }

      logMessage(LOG_INFO, "detected: %s", brl->data->protocol->modelName);

      const KeyTableDefinition *ktd =
        brl->data->protocol->testIdentities?
          brl->data->protocol->testIdentities(brl):
          NULL;

      if (getCellCount(brl, &brl->textColumns)) {
        brl->textRows = 1;

        setKeyTable(brl, ktd);
        makeOutputTable(dotsTable_ISO11548_1);
  
        if (clearCells(brl)) return 1;
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
  size_t count = brl->textColumns * brl->textRows;

  if (cellsHaveChanged(brl->data->previousCells, brl->buffer, count, NULL, NULL, NULL)) {
    if (!writeCells(brl)) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  InputPacket packet;
  int length;

  while ((length = readPacket(brl, &packet))) {
    switch (packet.data.type) {
      case IPT_CURSOR: {
        unsigned char key = packet.data.data;

        enqueueKey(brl, HM_GRP_RoutingKeys, key);
        continue;
      }

      case IPT_KEYS: {
        KeyNumberSet bits = (packet.data.reserved[0] << 0X00)
                          | (packet.data.reserved[1] << 0X08)
                          | (packet.data.reserved[2] << 0X10)
                          | (packet.data.reserved[3] << 0X18);

        enqueueKeys(brl, bits, HM_GRP_NavigationKeys, 0);
        continue;
      }

      default:
        break;
    }

    logUnexpectedPacket(&packet, length);
  }
  if (errno != EAGAIN) return BRL_CMD_RESTARTBRL;

  return EOF;
}
