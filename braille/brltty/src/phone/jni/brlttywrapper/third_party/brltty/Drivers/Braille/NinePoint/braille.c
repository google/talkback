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
#include "brldefs-np.h"

#define PROBE_RETRY_LIMIT 3
#define PROBE_INPUT_TIMEOUT 1000
#define MAXIMUM_RESPONSE_SIZE 3
#define MAXIMUM_CELL_COUNT (NP_KEY_ROUTING_MAX - NP_KEY_ROUTING_MIN + 1)

BEGIN_KEY_NAME_TABLE(navigation)
  KEY_NAME_ENTRY(NP_KEY_Brl1, "Brl1"),
  KEY_NAME_ENTRY(NP_KEY_Brl2, "Brl2"),
  KEY_NAME_ENTRY(NP_KEY_Brl3, "Brl3"),
  KEY_NAME_ENTRY(NP_KEY_Brl4, "Brl4"),
  KEY_NAME_ENTRY(NP_KEY_Brl5, "Brl5"),
  KEY_NAME_ENTRY(NP_KEY_Brl6, "Brl6"),
  KEY_NAME_ENTRY(NP_KEY_Brl7, "Brl7"),
  KEY_NAME_ENTRY(NP_KEY_Brl8, "Brl8"),

  KEY_NAME_ENTRY(NP_KEY_Enter,     "Enter"),
  KEY_NAME_ENTRY(NP_KEY_Space,     "Space"),
  KEY_NAME_ENTRY(NP_KEY_PadCenter, "PadCenter"),
  KEY_NAME_ENTRY(NP_KEY_PadLeft,   "PadLeft"),
  KEY_NAME_ENTRY(NP_KEY_PadRight,  "PadRight"),
  KEY_NAME_ENTRY(NP_KEY_PadUp,     "PadUp"),
  KEY_NAME_ENTRY(NP_KEY_PadDown,   "PadDown"),
  KEY_NAME_ENTRY(NP_KEY_NavLeft,   "NavLeft"),
  KEY_NAME_ENTRY(NP_KEY_NavRight,  "NavRight"),

  KEY_GROUP_ENTRY(NP_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(all)
  KEY_NAME_TABLE(navigation),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(all)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(all),
END_KEY_TABLE_LIST

struct BrailleDataStruct {
  unsigned char forceRewrite;
  unsigned char textCells[MAXIMUM_CELL_COUNT];
};

static int
writeBytes (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  return writeBraillePacket(brl, NULL, bytes, count);
}

static size_t
readPacket (BrailleDisplay *brl, void *packet, size_t size) {
  unsigned char *bytes = packet;
  size_t offset = 0;
  size_t length = 0;

  while (1) {
    unsigned char byte;

    {
      int started = offset > 0;

      if (!gioReadByte(brl->gioEndpoint, &byte, started)) {
        if (started) logPartialPacket(bytes, offset);
        return 0;
      }
    }

  gotByte:
    if (offset == 0) {
      switch (byte) {
        case 0XFC:
          length = 2;
          break;

        case 0XFD:
          length = 2;
          break;

        default:
          logIgnoredByte(byte);
          continue;
      }
    } else {
      int unexpected = 0;

      if (offset == 1) {
        if (bytes[0] == 0XFD) {
          switch (byte) {
            case 0X2F:
              length = 3;
              break;

            default:
              unexpected = 1;
              break;
          }
        }
      }

      if (unexpected) {
        logShortPacket(bytes, offset);
        offset = 0;
        length = 0;
        goto gotByte;
      }
    }

    if (offset < size) {
      bytes[offset] = byte;

      if (offset == (length - 1)) {
        logInputPacket(bytes, length);
        return length;
      }
    } else {
      if (offset == size) logTruncatedPacket(bytes, offset);
      logDiscardedByte(byte);
    }

    offset += 1;
  }
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.bluetooth.channelNumber = 1;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
writeIdentifyRequest (BrailleDisplay *brl) {
  return 1;
}
  
static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const unsigned char *bytes = packet;
    
  return ((size == 3) && (bytes[0] == 0XFD) && (bytes[1] == 0X2F))?
         BRL_RSP_DONE:
         BRL_RSP_UNEXPECTED;
}
                        
static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      unsigned char response[MAXIMUM_RESPONSE_SIZE];

      if (probeBrailleDisplay(brl, PROBE_RETRY_LIMIT, NULL, PROBE_INPUT_TIMEOUT,
                              writeIdentifyRequest,
                              readPacket, &response, sizeof(response),
                              isIdentityResponse)) {
        setBrailleKeyTable(brl, &KEY_TABLE_DEFINITION(all));
        MAKE_OUTPUT_TABLE(0X01, 0X04, 0X10, 0X02, 0X08, 0X20, 0X40, 0X80);

        brl->textColumns = MAXIMUM_CELL_COUNT;
        brl->data->forceRewrite = 1;
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
    free(brl->data);
    brl->data = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (cellsHaveChanged(brl->data->textCells, brl->buffer, brl->textColumns, NULL, NULL, &brl->data->forceRewrite)) {
    unsigned char bytes[(brl->textColumns * 2) + 2];
    unsigned char *byte = bytes;

    {
      int i;

      for (i=brl->textColumns-1; i>=0; i-=1) {
        *byte++ = 0XFC;
        *byte++ = translateOutputCell(brl->data->textCells[i]);
      }
    }

    *byte++ = 0XFD;
    *byte++ = 0X10;

    if (!writeBytes(brl, bytes, byte-bytes)) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[MAXIMUM_RESPONSE_SIZE];
  size_t size;

  while ((size = readPacket(brl, packet, sizeof(packet)))) {
    switch (packet[0]) {
      case 0XFD:
        switch (packet[1]) {
          case 0X2F:
            continue;

          default:
            break;
        }
        break;

      case 0XFC: {
        unsigned int key = packet[1];
        if ((key >= NP_KEY_ROUTING_MIN) && (key <= NP_KEY_ROUTING_MAX)) {
          enqueueKey(brl, NP_GRP_RoutingKeys, (key - NP_KEY_ROUTING_MIN));
          continue;
        } else {
          int press = !!(key & NP_KEY_NAVIGATION_PRESS);
          if (press) key &= ~NP_KEY_NAVIGATION_PRESS;
          enqueueKeyEvent(brl, NP_GRP_NavigationKeys, key, press);
          continue;
        }
        break;
      }
    }

    logUnexpectedPacket(packet, size);
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
