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
#include "io_generic.h"
#include "io_usb.h"
#include "ezusb.h"

#define BRL_STATUS_FIELDS sfCursorCoordinates2, sfWindowCoordinates2
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "brldefs-fa.h"

#define PROBE_RETRY_LIMIT 2
#define PROBE_INPUT_TIMEOUT 1000

#define MAXIMUM_RESPONSE_SIZE 0X20
#define WRITE_CELLS_LIMIT 62

#define TEXT_CELL_COUNT 80
#define STATUS_CELL_COUNT 4

BEGIN_KEY_NAME_TABLE(navigation)
  BRL_KEY_NAME_ENTRY(FA, NAV, K1, "K1"),
  BRL_KEY_NAME_ENTRY(FA, NAV, K2, "K2"),
  BRL_KEY_NAME_ENTRY(FA, NAV, K3, "K3"),

  BRL_KEY_NAME_ENTRY(FA, NAV, K4, "K4"),
  BRL_KEY_NAME_ENTRY(FA, NAV, K5, "K5"),
  BRL_KEY_NAME_ENTRY(FA, NAV, K6, "K6"),

  BRL_KEY_NAME_ENTRY(FA, NAV, K7, "K7"),
  BRL_KEY_NAME_ENTRY(FA, NAV, K8, "K8"),
  BRL_KEY_NAME_ENTRY(FA, NAV, K9, "K9"),

  BRL_KEY_NAME_ENTRY(FA, NAV, F1, "F1"),
  BRL_KEY_NAME_ENTRY(FA, NAV, F2, "F2"),
  BRL_KEY_NAME_ENTRY(FA, NAV, F3, "F3"),

  BRL_KEY_NAME_ENTRY(FA, NAV, F4, "F4"),
  BRL_KEY_NAME_ENTRY(FA, NAV, F5, "F5"),
  BRL_KEY_NAME_ENTRY(FA, NAV, F6, "F6"),

  BRL_KEY_GROUP_ENTRY(FA, ROUTE, "RoutingKey"),
  BRL_KEY_GROUP_ENTRY(FA, SLIDE, "Slider"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(all)
  KEY_NAME_TABLE(navigation),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(all)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(all),
END_KEY_TABLE_LIST

typedef struct {
  int (*prepare) (BrailleDisplay *brl);
} ProductEntry;

typedef struct {
  size_t length;
  unsigned char buffer[8];
} DeviceResponse;

struct BrailleDataStruct {
  const ProductEntry *product;

  struct {
    KeyNumberSet navigation;
    unsigned char routing[TEXT_CELL_COUNT / 8];
  } keys;

  struct {
    unsigned char rewrite;
    unsigned char cells[TEXT_CELL_COUNT];
  } text;

  struct {
    unsigned char rewrite;
    unsigned char cells[STATUS_CELL_COUNT];
  } status;

  struct {
    DeviceResponse response1;
    DeviceResponse serialNumber[2];
    DeviceResponse response4;
    DeviceResponse response5;
    DeviceResponse response6;
    DeviceResponse response7;
    DeviceResponse response8;
  } device;
};

static UsbDevice *
getDevice (BrailleDisplay *brl) {
  UsbChannel *channel = gioGetResourceObject(brl->gioEndpoint);
  return channel->device;
}

static int
installStage (UsbDevice *device, unsigned int stage, EzusbAction action) {
  char name[0X40];

  snprintf(
    name, sizeof(name), "%s-bfa-stage%u",
    PACKAGE_TARNAME, stage
  );

  return ezusbInstallBlob(device, name, action);
}

static int
installFirmware (BrailleDisplay *brl) {
  UsbDevice *device = getDevice(brl);

  if (!ezusbStopCPU(device)) return 0;
  if (!installStage(device, 1, EZUSB_ACTION_RW_INTERNAL)) return 0;

  if (!ezusbResetCPU(device)) return 0;
  if (!installStage(device, 2, EZUSB_ACTION_RW_MEMORY)) return 0;

  if (!ezusbStopCPU(device)) return 0;
  if (!installStage(device, 3, EZUSB_ACTION_RW_INTERNAL)) return 0;

  if (!ezusbResetCPU(device)) return 0;
  return 1;
}

static int
prepare1016 (BrailleDisplay *brl) {
  if (!installFirmware(brl)) return 0;
  return 0; // force a retry - the product ID should now be 0X1017
}

static const ProductEntry productEntry_1016 = {
  .prepare = prepare1016,
};

static int
askDevice (UsbDevice *device, uint16_t value, uint16_t index, DeviceResponse *values) {
  ssize_t result = usbControlRead(
    device, UsbControlRecipient_Device, UsbControlType_Vendor,
    0XC0, value, index,
    values->buffer, sizeof(values->buffer), 1000
  );

  if (result == -1) return 0;
  values->length = result;

  logBytes(LOG_CATEGORY(BRAILLE_DRIVER),
    "response: %04X %04X",
    values->buffer, values->length, value, index
  );

  return 1;
}

static int
prepare1017 (BrailleDisplay *brl) {
  UsbDevice *device = getDevice(brl);

  if (!askDevice(device, 0X0000, 0X0001, &brl->data->device.response1)) return 0;
  if (!askDevice(device, 0X0001, 0X0000, &brl->data->device.serialNumber[0])) return 0;
  if (!askDevice(device, 0X0001, 0X0001, &brl->data->device.serialNumber[1])) return 0;
  if (!askDevice(device, 0X0001, 0X0002, &brl->data->device.response4)) return 0;
  if (!askDevice(device, 0X0001, 0X0004, &brl->data->device.response5)) return 0;
  if (!askDevice(device, 0X0001, 0X0005, &brl->data->device.response6)) return 0;
  if (!askDevice(device, 0X0001, 0X0006, &brl->data->device.response7)) return 0;
  if (!askDevice(device, 0X0001, 0X0007, &brl->data->device.response8)) return 0;

  return 1;
}

static const ProductEntry productEntry_1017 = {
  .prepare = prepare1017,
};

static int
writeBytes (BrailleDisplay *brl, const unsigned char *bytes, size_t count) {
  return writeBraillePacket(brl, NULL, bytes, count);
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
        case FA_PKT_SLIDER:
          *length += 3;
          break;

        case FA_PKT_NAV:
          *length += 4;
          break;

        case FA_PKT_ROUTE:
          *length += ARRAY_COUNT(brl->data->keys.routing);
          break;

        default:
          return BRL_PVR_INVALID;
      }

      break;
    }

    default:
      break;
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
    { /* B2K84 (before firmware installation) */
      .vendor=0X0904, .product=0X1016,
      .configuration=1, .interface=0, .alternative=0,
      .data = &productEntry_1016
    },

    { /* B2K84 (after firmware installation) */
      .vendor=0X0904, .product=0X1017,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .data = &productEntry_1017
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.usb.channelDefinitions = usbChannelDefinitions;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    brl->data->product = gioGetApplicationData(brl->gioEndpoint);
    if (brl->data->product->prepare(brl)) return 1;
    disconnectBrailleResource(brl, NULL);
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      setBrailleKeyTable(brl, &KEY_TABLE_DEFINITION(all));
      makeOutputTable(dotsTable_ISO11548_1);

      brl->textColumns = TEXT_CELL_COUNT;
      brl->statusColumns = STATUS_CELL_COUNT;

      brl->data->keys.navigation = 0;
      brl->data->text.rewrite = 1;
      brl->data->status.rewrite = 1;

      return 1;
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
writeCells (BrailleDisplay *brl, const unsigned char *cells, unsigned int from, unsigned int to, unsigned int offset) {
  unsigned char packet[1 + (to - from)];

  while (from < to) {
    unsigned char *byte = packet;
    unsigned int count = to - from;
    count = MIN(count, WRITE_CELLS_LIMIT);

    *byte++ = from + offset;
    byte = mempcpy(byte, cells, count);
    if (!writeBytes(brl, packet, (byte - packet))) return 0;

    from += count;
    cells += count;
  }

  return 1;
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *cells) {
  unsigned int from, to;

  if (cellsHaveChanged(brl->data->status.cells, cells, brl->statusColumns,
                       &from, &to, &brl->data->status.rewrite)) {
    if (!writeCells(brl, &cells[from], from, to, 0)) {
      return 0;
    }
  }

  return 1;
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  unsigned int from, to;

  if (cellsHaveChanged(brl->data->text.cells, brl->buffer, brl->textColumns,
                       &from, &to, &brl->data->text.rewrite)) {
    size_t count = to - from;
    unsigned char cells[count];

    translateOutputCells(cells, &brl->data->text.cells[from], count);
    if (!writeCells(brl, cells, from, to, STATUS_CELL_COUNT)) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[MAXIMUM_RESPONSE_SIZE];
  size_t size;

  while ((size = readPacket(brl, packet, sizeof(packet)))) {
    switch (packet[0]) {
      case FA_PKT_NAV: {
        KeyNumberSet keys = (packet[1] <<  0)
                          | (packet[2] <<  8)
                          | (packet[3] << 16)
                          | (packet[4] << 24)
                          ;

        enqueueUpdatedKeys(brl, keys, &brl->data->keys.navigation, FA_GRP_NAV, 0);
        continue;
      }

      case FA_PKT_ROUTE: {
        enqueueUpdatedKeyGroup(
          brl, TEXT_CELL_COUNT,
          &packet[1], brl->data->keys.routing,
          FA_GRP_ROUTE
        );

        continue;
      }

      case FA_PKT_SLIDER: {
	int value = (packet[2] * 0XFF) / 0XF4;
        value = MIN(value, 0XFF);
        enqueueKey(brl, FA_GRP_SLIDE, value);
        continue;
      }

      default:
        break;
    }

    logUnexpectedPacket(packet, size);
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}
