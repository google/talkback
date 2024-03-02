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
#include "brldefs-ic.h"

#define PROBE_RETRY_LIMIT 2
#define PROBE_INPUT_TIMEOUT 1000
#define MAXIMUM_TEXT_CELLS 0XFF

BEGIN_KEY_NAME_TABLE(common)
  KEY_NAME_ENTRY(IC_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(IC_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(IC_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(IC_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(IC_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(IC_KEY_Dot6, "Dot6"),
  KEY_NAME_ENTRY(IC_KEY_Dot7, "Dot7"),
  KEY_NAME_ENTRY(IC_KEY_Dot8, "Dot8"),

  KEY_NAME_ENTRY(IC_KEY_Space, "Space"),
  KEY_NAME_ENTRY(IC_KEY_MoveUp, "MoveUp"),
  KEY_NAME_ENTRY(IC_KEY_MoveDown, "MoveDown"),
  KEY_NAME_ENTRY(IC_KEY_PanLeft, "PanLeft"),
  KEY_NAME_ENTRY(IC_KEY_PanRight, "PanRight"),

  KEY_GROUP_ENTRY(IC_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(bb)
  KEY_NAME_TABLE(common),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(nvda)
  KEY_NAME_TABLE(common),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(bb)
DEFINE_KEY_TABLE(nvda)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(bb),
  &KEY_TABLE_DEFINITION(nvda),
END_KEY_TABLE_LIST

typedef struct {
  const KeyTableDefinition *keyTableDefinition;
  void (*remapKeyNumbers) (KeyNumberSet *keys);
  unsigned adjustRoutingKey:1;

  struct {
    const KeyNumberSetMapEntry *entries;
    size_t count;
  } keyNumberSetMap;
} InputOutputData;

struct BrailleDataStruct {
  const InputOutputData *io;
  KeyNumberSetMap *keyNumberSetMap;

  struct {
    unsigned char rewrite;
    unsigned char cells[MAXIMUM_TEXT_CELLS];
  } braille;

  struct {
    unsigned char rewrite;
    wchar_t characters[MAXIMUM_TEXT_CELLS];
  } text;

  struct {
    unsigned char rewrite;
    int position;
  } cursor;
};

#define KEY_BIT_Dot1 KEY_NUMBER_BIT(IC_KEY_Dot1)
#define KEY_BIT_Dot2 KEY_NUMBER_BIT(IC_KEY_Dot2)
#define KEY_BIT_Dot3 KEY_NUMBER_BIT(IC_KEY_Dot3)
#define KEY_BIT_Dot4 KEY_NUMBER_BIT(IC_KEY_Dot4)
#define KEY_BIT_Dot5 KEY_NUMBER_BIT(IC_KEY_Dot5)
#define KEY_BIT_Dot6 KEY_NUMBER_BIT(IC_KEY_Dot6)
#define KEY_BIT_Dot7 KEY_NUMBER_BIT(IC_KEY_Dot7)
#define KEY_BIT_Dot8 KEY_NUMBER_BIT(IC_KEY_Dot8)

#define KEY_BIT_Space KEY_NUMBER_BIT(IC_KEY_Space)
#define KEY_BIT_MoveUp KEY_NUMBER_BIT(IC_KEY_MoveUp)
#define KEY_BIT_MoveDown KEY_NUMBER_BIT(IC_KEY_MoveDown)
#define KEY_BIT_PanLeft KEY_NUMBER_BIT(IC_KEY_PanLeft)
#define KEY_BIT_PanRight KEY_NUMBER_BIT(IC_KEY_PanRight)
#define KEY_BIT_Back KEY_NUMBER_BIT(IC_KEY_Back)
#define KEY_BIT_Enter KEY_NUMBER_BIT(IC_KEY_Enter)

static void
remapKeyNumbers_BrailleBack (KeyNumberSet *keys) {
  static const KeyNumberMapEntry map[] = {
    {.to=IC_KEY_MoveUp  , .from=IC_KEY_MoveDown},
    {.to=IC_KEY_MoveDown, .from=IC_KEY_PanLeft },
    {.to=IC_KEY_PanLeft , .from=IC_KEY_Back    },
    {.to=IC_KEY_PanRight, .from=IC_KEY_Enter   },
    {.to=IC_KEY_Back    , .from=KTB_KEY_ANY    },
    {.to=IC_KEY_Enter   , .from=KTB_KEY_ANY    },
  };

  remapKeyNumbers(keys, map, ARRAY_COUNT(map));
}

static const KeyNumberSetMapEntry keyNumberSetMap_BrailleBack[] = {
  { .to = KEY_BIT_Dot7,
    .from = KEY_BIT_Space | KEY_BIT_Dot7
  },

  { .to = KEY_BIT_Dot8,
    .from = KEY_BIT_Space | KEY_BIT_Dot8
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot4,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot4 | KEY_BIT_Dot5
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot4 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot3 | KEY_BIT_Dot4 | KEY_BIT_Dot5
  },

  { .to = KEY_BIT_Dot7 | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot4,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot4 | KEY_BIT_Dot7
  },

  { .to = KEY_BIT_Dot8 | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot4,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot4
  },

  { .to = KEY_BIT_Dot7 | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot3,
    .from = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot4 | KEY_BIT_Dot7
  },

  { .to = KEY_BIT_Dot8 | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot3,
    .from = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot4
  },

  { .to = KEY_BIT_Dot7 | KEY_BIT_Dot2 | KEY_BIT_Dot3 | KEY_BIT_Dot4,
    .from = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot3 | KEY_BIT_Dot4 | KEY_BIT_Dot7
  },

  { .to = KEY_BIT_Dot8 | KEY_BIT_Dot2 | KEY_BIT_Dot3 | KEY_BIT_Dot4,
    .from = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot3 | KEY_BIT_Dot4
  },
};

static const InputOutputData ioData_BrailleBack = {
  .keyTableDefinition = &KEY_TABLE_DEFINITION(bb),
  .remapKeyNumbers = remapKeyNumbers_BrailleBack,

  .keyNumberSetMap = {
    .entries = keyNumberSetMap_BrailleBack,
    .count = ARRAY_COUNT(keyNumberSetMap_BrailleBack)
  }
};

static void
remapKeyNumbers_NVDA (KeyNumberSet *keys) {
}

static const KeyNumberSetMapEntry keyNumberSetMap_NVDA[] = {
  { .to = KEY_BIT_Space | KEY_BIT_MoveUp,
    .from = KEY_BIT_Space | KEY_BIT_MoveUp | KEY_BIT_Dot2
  },

  { .to = KEY_BIT_Space | KEY_BIT_MoveDown,
    .from = KEY_BIT_Space | KEY_BIT_MoveDown | KEY_BIT_Dot2
  },

  { .to = KEY_BIT_Space | KEY_BIT_PanLeft,
    .from = KEY_BIT_Space | KEY_BIT_PanLeft | KEY_BIT_Dot2
  },

  { .to = KEY_BIT_Space | KEY_BIT_PanRight,
    .from = KEY_BIT_Space | KEY_BIT_PanRight | KEY_BIT_Dot2
  },

  { .to = KEY_BIT_Dot8 | KEY_BIT_MoveUp,
    .from = KEY_BIT_Space | KEY_BIT_MoveUp | KEY_BIT_Dot4
  },

  { .to = KEY_BIT_Dot8 | KEY_BIT_MoveDown,
    .from = KEY_BIT_Space | KEY_BIT_MoveDown | KEY_BIT_Dot4
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot3,
    .from = KEY_BIT_Space | KEY_BIT_PanLeft
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_PanRight
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot3,
    .from = KEY_BIT_Space | KEY_BIT_MoveUp
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot5 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_MoveDown
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot3,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot3 | KEY_BIT_Dot8
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot4 | KEY_BIT_Dot5 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot6 | KEY_BIT_Dot8
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot4 | KEY_BIT_Dot5 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_Dot6
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot3 | KEY_BIT_Dot4 | KEY_BIT_Dot5 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_Dot3
  },

  { .to = KEY_BIT_Dot7 | KEY_BIT_Dot1,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_MoveUp
  },

  { .to = KEY_BIT_Dot8 | KEY_BIT_Dot4,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_MoveDown
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot3 | KEY_BIT_Dot4,
    .from = KEY_BIT_Dot8 | KEY_BIT_Dot2 | KEY_BIT_Dot3 | KEY_BIT_Dot4,
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot2 | KEY_BIT_Dot3 | KEY_BIT_Dot4 | KEY_BIT_Dot5,
    .from = KEY_BIT_Space | KEY_BIT_Dot5
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot5 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_Dot4 | KEY_BIT_Dot5
  },

  { .to = KEY_BIT_Space | KEY_BIT_Dot4 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot2 | KEY_BIT_Dot5 | KEY_BIT_Dot6
  },

  { .to = KEY_BIT_Dot7 | KEY_BIT_Dot6,
    .from = KEY_BIT_Space | KEY_BIT_Dot1 | KEY_BIT_Dot4
  },

  { .from = KEY_BIT_Dot7 | KEY_BIT_Dot8 },
};

static const InputOutputData ioData_NVDA = {
  .keyTableDefinition = &KEY_TABLE_DEFINITION(nvda),
  .remapKeyNumbers = remapKeyNumbers_NVDA,
  .adjustRoutingKey = 1,

  .keyNumberSetMap = {
    .entries = keyNumberSetMap_NVDA,
    .count = ARRAY_COUNT(keyNumberSetMap_NVDA)
  }
};

static int
writeBytes (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  return writeBraillePacket(brl, NULL, bytes, count);
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
  if (data1) byte = mempcpy(byte, data1, length1);

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
  unsigned char *checksum = byte++;
  *checksum = 0;

  /* DE */
  *byte++ = 0XFD;
  *byte++ = 0XFD;

  {
    unsigned char sum = 0;
    const unsigned char *ptr = packet;

    while (ptr != byte) sum += *ptr++;
    *checksum = sum;
  }

  return writeBytes(brl, packet, (byte - packet));
}

static BraillePacketVerifierResult
verifyPacket (
  BrailleDisplay *brl,
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1: {
      switch (byte) {
        case 0XFA:
          *length = 10;
          break;

        // an ASCII LF is being sent after each Bluetooth packet
        case 0X0A:
          return BRL_PVR_IGNORE;

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
      case 0XFA: {
        if (byte != 0XFB) return BRL_PVR_INVALID;

        const InputPacket *packet = (const void *)bytes;
        int checksum = -packet->fields.checksum;
        for (size_t i=0; i<size; i+=1) checksum += packet->bytes[i];

        if ((checksum & 0XFF) != packet->fields.checksum) {
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
readPacket (BrailleDisplay *brl, void *packet, size_t size) {
  return readBraillePacket(brl, NULL, packet, size, verifyPacket, NULL);
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* all models */
      .vendor=0X1209, .product=0XABC0,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.options.applicationData = &ioData_NVDA;

  descriptor.bluetooth.channelNumber = 1;
  descriptor.bluetooth.discoverChannel = 1;
  descriptor.bluetooth.options.applicationData = &ioData_BrailleBack;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    brl->data->io = gioGetApplicationData(brl->gioEndpoint);

    brl->data->keyNumberSetMap = newKeyNumberSetMap(
      brl->data->io->keyNumberSetMap.entries,
      brl->data->io->keyNumberSetMap.count
    );

    return 1;
  }

  return 0;
}

static int
writeIdentifyRequest (BrailleDisplay *brl) {
  static const unsigned char data1[20] = {0};
  return writePacket(brl, 0XFB, 0X01, data1, sizeof(data1), NULL, 0);
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *bytes, size_t size) {
  const InputPacket *packet = bytes;
  if (packet->fields.type != 0X02) return BRL_RSP_UNEXPECTED;

  brl->textColumns = packet->fields.data;
  return BRL_RSP_DONE;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));
    brl->data->io = NULL;
    brl->data->keyNumberSetMap = NULL;

    if (connectResource(brl, device)) {
      InputPacket response;

      if (probeBrailleDisplay(brl, PROBE_RETRY_LIMIT, NULL, PROBE_INPUT_TIMEOUT,
                              writeIdentifyRequest,
                              readPacket, &response, sizeof(response),
                              isIdentityResponse)) {
        setBrailleKeyTable(brl, brl->data->io->keyTableDefinition);
        makeOutputTable(dotsTable_ISO11548_1);
        brl->cellSize = 6;

        brl->data->braille.rewrite = 1;
        brl->data->text.rewrite = 1;
        brl->data->cursor.rewrite = 1;
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

  if (brl->data) {
    {
      KeyNumberSetMap *map = brl->data->keyNumberSetMap;
      if (map) destroyKeyNumberSetMap(map);
    }

    free(brl->data);
    brl->data = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  int cellCount = brl->textColumns;

  int newBraille =
      cellsHaveChanged(brl->data->braille.cells, brl->buffer, cellCount,
                       NULL, NULL, &brl->data->braille.rewrite);

  int newText =
      textHasChanged(brl->data->text.characters, text, cellCount,
                     NULL, NULL, &brl->data->text.rewrite);

  if (newBraille || newText) {
    unsigned char cells[cellCount];
    translateOutputCells(cells, brl->data->braille.cells, cellCount);

    unsigned char attributes[cellCount];
    memset(attributes, 0, sizeof(attributes));

    for (int i=0; i<cellCount; i+=1) {
      unsigned char *byte = &attributes[i];

      if (text) {
        wchar_t character = text[i];

        if (iswupper(character)) *byte |= 0X01;
      }
    }

    if (!writePacket(brl, 0XFC, 0X01,
                     cells, sizeof(cells),
                     attributes, sizeof(attributes))) return 0;
  }

  return 1;
}

static void
splitKeys (KeyNumberSet keys, KeyNumberSet *navigation, KeyNumberSet *routing) {
  {
    const KeyNumber routingShift = IC_KEY_RoutingKey1;
    const KeyNumberSet navigationMask = KEY_NUMBER_BIT(routingShift) - 1;

    *navigation = keys & navigationMask;
    *routing = keys >> routingShift;
  }

  if (*routing) {
    KeyNumber shift = IC_KEY_RoutingKey17 - IC_KEY_RoutingKey1;
    KeyNumberSet mask = KEY_NUMBER_BIT(shift) - 1;

    KeyNumberSet left = *routing & mask;
    *routing &= ~mask;

    *routing <<= 4;
    *routing |= left;
  }
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  InputPacket packet;
  size_t size;

  while ((size = readPacket(brl, &packet, sizeof(packet)))) {
    switch (packet.fields.type) {
      case 0X00: {
        unsigned char key = packet.fields.data;
        if (brl->data->io->adjustRoutingKey) key -= 1;
        if (key < brl->textColumns) enqueueKey(brl, IC_GRP_RoutingKeys, key);
        continue;
      }

      case 0X01: {
        KeyNumberSet navigation;
        KeyNumberSet routing;

        {
          KeyNumberSet keys = (packet.fields.reserved[0] << 0X00)
                            | (packet.fields.reserved[1] << 0X08)
                            | (packet.fields.reserved[2] << 0X10)
                            | (packet.fields.reserved[3] << 0X18)
                            ;

          brl->data->io->remapKeyNumbers(&keys);
          splitKeys(keys, &navigation, &routing);
        }

        if (navigation) {
          remapKeyNumberSet(&navigation, brl->data->keyNumberSetMap);

          if (navigation) {
            enqueueKeyEvents(brl, navigation, IC_GRP_NavigationKeys, 0, 1);
            enqueueKeys(brl, routing, IC_GRP_RoutingKeys, 0);
            enqueueKeyEvents(brl, navigation, IC_GRP_NavigationKeys, 0, 0);
          }
        }

        continue;
      }

      default:
        break;
    }

    logUnexpectedPacket(&packet, size);
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
