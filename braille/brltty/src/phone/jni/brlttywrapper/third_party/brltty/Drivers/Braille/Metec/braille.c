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
#include "parameters.h"
#include "async_alarm.h"

#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "brldefs-mt.h"

BEGIN_KEY_NAME_TABLE(3keys)
  KEY_NAME_ENTRY(MT_KEY_LeftUp, "Up"),
  KEY_NAME_ENTRY(MT_KEY_LeftSelect, "Select"),
  KEY_NAME_ENTRY(MT_KEY_LeftDown, "Down"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(6keys)
  KEY_NAME_ENTRY(MT_KEY_LeftUp, "LeftUp"),
  KEY_NAME_ENTRY(MT_KEY_LeftSelect, "LeftSelect"),
  KEY_NAME_ENTRY(MT_KEY_LeftDown, "LeftDown"),

  KEY_NAME_ENTRY(MT_KEY_RightUp, "RightUp"),
  KEY_NAME_ENTRY(MT_KEY_RightSelect, "RightSelect"),
  KEY_NAME_ENTRY(MT_KEY_RightDown, "RightDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(4keys)
  KEY_NAME_ENTRY(MT_KEY_LeftUp, "LeftUp"),
  KEY_NAME_ENTRY(MT_KEY_LeftDown, "LeftDown"),

  KEY_NAME_ENTRY(MT_KEY_RightUp, "RightUp"),
  KEY_NAME_ENTRY(MT_KEY_RightDown, "RightDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing1)
  KEY_GROUP_ENTRY(MT_GRP_RoutingKeys1, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(status1)
  KEY_GROUP_ENTRY(MT_GRP_StatusKeys1, "StatusKey"),
END_KEY_NAME_TABLE

/*
BEGIN_KEY_NAME_TABLE(front)
  KEY_NAME_ENTRY(MT_KEY_CursorLeft, "CursorLeft"),
  KEY_NAME_ENTRY(MT_KEY_CursorUp, "CursorUp"),
  KEY_NAME_ENTRY(MT_KEY_CursorRight, "CursorRight"),
  KEY_NAME_ENTRY(MT_KEY_CursorDown, "CursorDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing2)
  KEY_GROUP_ENTRY(MT_GRP_RoutingKeys2, "RoutingKey2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(status2)
  KEY_GROUP_ENTRY(MT_GRP_StatusKeys2, "StatusKey2"),
END_KEY_NAME_TABLE
*/

BEGIN_KEY_NAME_TABLES(bd1_3)
  KEY_NAME_TABLE(3keys),
  KEY_NAME_TABLE(routing1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bd1_3s)
  KEY_NAME_TABLE(3keys),
  KEY_NAME_TABLE(routing1),
  KEY_NAME_TABLE(status1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bd1_6)
  KEY_NAME_TABLE(6keys),
  KEY_NAME_TABLE(routing1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bd1_6s)
  KEY_NAME_TABLE(6keys),
  KEY_NAME_TABLE(routing1),
  KEY_NAME_TABLE(status1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(bd2)
  KEY_NAME_TABLE(4keys),
  KEY_NAME_TABLE(routing1),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(bd1_3)
DEFINE_KEY_TABLE(bd1_3s)
DEFINE_KEY_TABLE(bd1_6)
DEFINE_KEY_TABLE(bd1_6s)
DEFINE_KEY_TABLE(bd2)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(bd1_3),
  &KEY_TABLE_DEFINITION(bd1_3s),
  &KEY_TABLE_DEFINITION(bd1_6),
  &KEY_TABLE_DEFINITION(bd1_6s),
  &KEY_TABLE_DEFINITION(bd2),
END_KEY_TABLE_LIST

#define MT_IDENTITY_PACKET_SIZE 0X400
#define MT_STATUS_PACKET_SIZE 8

#define MT_ROUTING_KEYS_SECONDARY 100
#define MT_ROUTING_KEYS_NONE 0XFF

#define MT_MODULE_SIZE 8
#define MT_MODULES_MAXIMUM 10
#define MT_CELLS_MAXIMUM (MT_MODULES_MAXIMUM * MT_MODULE_SIZE)

typedef struct {
  int (*beginProtocol) (BrailleDisplay *brl);
  void (*endProtocol) (BrailleDisplay *brl);

  int (*setHighVoltage) (BrailleDisplay *brl, int on);
  int (*getDeviceIdentity) (BrailleDisplay *brl);

  int (*handleInput) (BrailleDisplay *brl);
} ProtocolOperations;

struct BrailleDataStruct {
  const ProtocolOperations *protocol;

  unsigned char oldCells[MT_CELLS_MAXIMUM];
  unsigned char newCells[MT_CELLS_MAXIMUM];

  unsigned char cellCount;
  unsigned char textCount;
  unsigned char statusCount;

  unsigned char moduleCount;
  unsigned char writeModule[MT_MODULES_MAXIMUM];

  KeyNumberSet allNavigationKeys;
  KeyNumberSet pressedNavigationKeys;
  unsigned char routingKey;

  union {
    struct {
      AsyncHandle statusAlarm;
    } usb;
  } proto;
};

static void
setCellCount (BrailleDisplay *brl, unsigned char count) {
  brl->data->moduleCount = (brl->data->cellCount = count) / MT_MODULE_SIZE;

  switch (count) {
    case 22:
    case 42:
      brl->data->statusCount = 2;
      break;

    default:
      brl->data->statusCount = 0;
      break;
  }

  brl->data->textCount = brl->data->cellCount - brl->data->statusCount;
  brl->textColumns = brl->data->textCount;
  brl->statusColumns = brl->data->statusCount;
}

static void
handleNavigationKeys (BrailleDisplay *brl, KeyNumberSet keys) {
  keys &= brl->data->allNavigationKeys;
  enqueueUpdatedKeys(brl, keys, &brl->data->pressedNavigationKeys, MT_GRP_NavigationKeys, 0);
}

static void
handleRoutingKeyEvent (BrailleDisplay *brl, unsigned char key, int press) {
  if (key != MT_ROUTING_KEYS_NONE) {
    KeyGroup group;

    {
      KeyGroup routing;
      KeyGroup status;

      if (key < MT_ROUTING_KEYS_SECONDARY) {
        routing = MT_GRP_RoutingKeys1;
        status = MT_GRP_StatusKeys1;
      } else {
        key -= MT_ROUTING_KEYS_SECONDARY;
        routing = MT_GRP_RoutingKeys2;
        status = MT_GRP_StatusKeys2;
      }

      if (key < brl->data->statusCount) {
        group = status;
      } else if ((key -= brl->data->statusCount) < brl->data->textCount) {
        group = routing;
      } else {
        return;
      }
    }

    enqueueKeyEvent(brl, group, key, press);
  }
}

static void
handleRoutingKey (BrailleDisplay *brl, unsigned char key) {
  if (key != brl->data->routingKey) {
    handleRoutingKeyEvent(brl, brl->data->routingKey, 0);
    handleRoutingKeyEvent(brl, key, 1);
    brl->data->routingKey = key;
  }
}

#include "io_usb.h"

#define MT_USB_CONTROL_RECIPIENT UsbControlRecipient_Device
#define MT_USB_CONTROL_TYPE UsbControlType_Vendor

static int setUsbStatusAlarm (BrailleDisplay *brl);

static ssize_t
tellUsbDevice (
  BrailleDisplay *brl, unsigned char request,
  const void *data, size_t length
) {
  return gioTellResource(brl->gioEndpoint,
                         MT_USB_CONTROL_RECIPIENT, MT_USB_CONTROL_TYPE,
                         request, 0, 0, data, length);
}

static ssize_t
askUsbDevice (
  BrailleDisplay *brl, unsigned char request,
  void *buffer, size_t size
) {
  return gioAskResource(brl->gioEndpoint,
                        MT_USB_CONTROL_RECIPIENT, MT_USB_CONTROL_TYPE,
                        request, 0, 0, buffer, size);
}

static ssize_t
getUsbStatusPacket (BrailleDisplay *brl, unsigned char *packet) {
  return askUsbDevice(brl, 0X80, packet, MT_STATUS_PACKET_SIZE);
}

ASYNC_ALARM_CALLBACK(handleUsbStatusAlarm) {
  BrailleDisplay *brl = parameters->data;
  unsigned char packet[MT_STATUS_PACKET_SIZE];

  asyncDiscardHandle(brl->data->proto.usb.statusAlarm);
  brl->data->proto.usb.statusAlarm = NULL;

  memset(packet, 0, sizeof(packet));

  if (getUsbStatusPacket(brl, packet))  {
    logInputPacket(packet, sizeof(packet));
    handleRoutingKey(brl, packet[0]);
    handleNavigationKeys(brl, (packet[2] | (packet[3] << 8)));
    setUsbStatusAlarm(brl);
  } else {
    enqueueCommand(BRL_CMD_RESTARTBRL);
  }
}

static int
setUsbStatusAlarm (BrailleDisplay *brl) {
  return asyncNewRelativeAlarm(&brl->data->proto.usb.statusAlarm,
                         BRAILLE_DRIVER_INPUT_POLL_INTERVAL,
                         handleUsbStatusAlarm, brl);
}

static int
beginUsbProtocol (BrailleDisplay *brl) {
  brl->data->proto.usb.statusAlarm = NULL;
  setUsbStatusAlarm(brl);

  return 1;
}

static void
endUsbProtocol (BrailleDisplay *brl) {
  if (brl->data->proto.usb.statusAlarm) {
    asyncCancelRequest(brl->data->proto.usb.statusAlarm);
    brl->data->proto.usb.statusAlarm = NULL;
  }
}

static int
setUsbHighVoltage (BrailleDisplay *brl, int on) {
  const unsigned char data[] = {
    (on? 0XEF: 0X00),
    0, 0, 0, 0, 0, 0, 0
  };

  return tellUsbDevice(brl, 0X01, data, sizeof(data)) != -1;
}

static int
getUsbDeviceIdentity (BrailleDisplay *brl) {
  UsbChannel *channel = gioGetResourceObject(brl->gioEndpoint);
  UsbDevice *device = channel->device;
  unsigned int counter = 2;

  do {
    static const unsigned char data[] = {0};

    if (tellUsbDevice(brl, 0X04, data, sizeof(data)) != -1) {
      unsigned char identity[MT_IDENTITY_PACKET_SIZE];
      ssize_t result = usbReadEndpoint(device, 1, identity, sizeof(identity), 1000);

      if (result != -1) return 1;
    }
  } while (--counter);

  return 0;
}

static int
handleUsbInput (BrailleDisplay *brl) {
  return 1;
}

static const ProtocolOperations usbProtocolOperations = {
  .beginProtocol = beginUsbProtocol,
  .endProtocol = endUsbProtocol,

  .setHighVoltage = setUsbHighVoltage,
  .getDeviceIdentity = getUsbDeviceIdentity,

  .handleInput = handleUsbInput
};

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* all models */
      .vendor=0X0452, .product=0X0100,
      .configuration=1, .interface=0, .alternative=0,
      .disableEndpointReset=1
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.options.applicationData = &usbProtocolOperations;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    brl->data->protocol = gioGetApplicationData(brl->gioEndpoint);
    return 1;
  }

  return 0;
}

static void
disconnectResource (BrailleDisplay *brl) {
  disconnectBrailleResource(brl, NULL);
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      if (brl->data->protocol->setHighVoltage(brl, 1)) {
        unsigned char statusPacket[MT_STATUS_PACKET_SIZE];

        brl->data->protocol->getDeviceIdentity(brl);

        if (getUsbStatusPacket(brl, statusPacket)) {
          setCellCount(brl, statusPacket[1]);

          {
            unsigned int moduleNumber;

            for (moduleNumber=0; moduleNumber<brl->data->moduleCount; moduleNumber+=1) {
              brl->data->writeModule[moduleNumber] = 1;
            }
          }

          MAKE_OUTPUT_TABLE(0X80, 0X40, 0X20, 0X10, 0X08, 0X04, 0X02, 0X01);

          {
            const KeyTableDefinition *ktd;

            if (statusPacket[2] & 0X80) {
              ktd = brl->data->statusCount? &KEY_TABLE_DEFINITION(bd1_3s):
                                            &KEY_TABLE_DEFINITION(bd1_3);
            } else {
              ktd = brl->data->statusCount? &KEY_TABLE_DEFINITION(bd1_6s):
                                            &KEY_TABLE_DEFINITION(bd1_6);
            }

            brl->data->allNavigationKeys = makeKeyNumberSet(ktd->names, MT_GRP_NavigationKeys);
            setBrailleKeyTable(brl, ktd);
          }

          brl->data->pressedNavigationKeys = 0;
          brl->data->routingKey = MT_ROUTING_KEYS_NONE;

          if (brl->data->protocol->beginProtocol(brl)) return 1;
        }

        brl->data->protocol->setHighVoltage(brl, 0);
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
  brl->data->protocol->endProtocol(brl);
  brl->data->protocol->setHighVoltage(brl, 0);
  disconnectResource(brl);
  free(brl->data);
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  const unsigned char *source = brl->data->newCells;
  unsigned char *target = brl->data->oldCells;
  unsigned int moduleNumber;

  memcpy(&brl->data->newCells[brl->data->statusCount], brl->buffer, brl->data->textCount);

  for (moduleNumber=0; moduleNumber<brl->data->moduleCount; moduleNumber+=1) {
    if (cellsHaveChanged(target, source, MT_MODULE_SIZE, NULL, NULL, &brl->data->writeModule[moduleNumber])) {
      unsigned char cells[MT_MODULE_SIZE];

      translateOutputCells(cells, source, MT_MODULE_SIZE);
      if (tellUsbDevice(brl, 0X0A+moduleNumber, cells, MT_MODULE_SIZE) == -1) return 0;
    }

    source += MT_MODULE_SIZE;
    target += MT_MODULE_SIZE;
  }

  return 1;
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *cells) {
  const unsigned int count = brl->data->statusCount;

  if (count) {
    unsigned char *target = &brl->data->newCells[0];
    const unsigned char *end = target + count;

    while (target < end) {
      unsigned char cell = *cells++;

      if (!cell) break;
      *target++ = cell;
    }

    while (target < end) *target++ = 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  return brl->data->protocol->handleInput(brl)? EOF: BRL_CMD_RESTARTBRL;
}
