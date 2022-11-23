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
#include "ascii.h"
#include "bitmask.h"
#include "async_wait.h"

#include "brl_driver.h"
#include "brldefs-hw.h"

BEGIN_KEY_NAME_TABLE(routing)
  KEY_GROUP_ENTRY(HW_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(braille)
  KEY_NAME_ENTRY(HW_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(HW_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(HW_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(HW_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(HW_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(HW_KEY_Dot6, "Dot6"),
  KEY_NAME_ENTRY(HW_KEY_Dot7, "Dot7"),
  KEY_NAME_ENTRY(HW_KEY_Dot8, "Dot8"),
  KEY_NAME_ENTRY(HW_KEY_Space, "Space"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(command)
  KEY_NAME_ENTRY(HW_KEY_Command1, "Display1"),
  KEY_NAME_ENTRY(HW_KEY_Command2, "Display2"),
  KEY_NAME_ENTRY(HW_KEY_Command3, "Display3"),
  KEY_NAME_ENTRY(HW_KEY_Command4, "Display4"),
  KEY_NAME_ENTRY(HW_KEY_Command5, "Display5"),
  KEY_NAME_ENTRY(HW_KEY_Command6, "Display6"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(joystick)
  KEY_NAME_ENTRY(HW_KEY_Up, "Up"),
  KEY_NAME_ENTRY(HW_KEY_Down, "Down"),
  KEY_NAME_ENTRY(HW_KEY_Left, "Left"),
  KEY_NAME_ENTRY(HW_KEY_Right, "Right"),
  KEY_NAME_ENTRY(HW_KEY_Action, "Action"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(thumb)
  KEY_NAME_ENTRY(HW_KEY_Thumb1, "Previous"),
  KEY_NAME_ENTRY(HW_KEY_Thumb2, "Back"),
  KEY_NAME_ENTRY(HW_KEY_Thumb3, "Advance"),
  KEY_NAME_ENTRY(HW_KEY_Thumb4, "Next"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(BI14)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(joystick),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(BI32)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(command),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(BI40)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(command),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(B80)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(command),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(touch)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(BI14)
DEFINE_KEY_TABLE(BI32)
DEFINE_KEY_TABLE(BI40)
DEFINE_KEY_TABLE(B80)
DEFINE_KEY_TABLE(touch)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(BI14),
  &KEY_TABLE_DEFINITION(BI32),
  &KEY_TABLE_DEFINITION(BI40),
  &KEY_TABLE_DEFINITION(B80),
  &KEY_TABLE_DEFINITION(touch),
END_KEY_TABLE_LIST

typedef struct {
  const KeyTableDefinition *keyTableDefinition;
  unsigned hasBrailleKeys:1;
  unsigned hasCommandKeys:1;
  unsigned hasJoystick:1;
  unsigned hasSecondThumbKeys:1;
} ModelEntry;

static const ModelEntry modelEntry_BI14 = {
  .hasBrailleKeys = 1,
  .hasJoystick = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI14)
};

static const ModelEntry modelEntry_BI32 = {
  .hasBrailleKeys = 1,
  .hasCommandKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI32)
};

static const ModelEntry modelEntry_BI40 = {
  .hasBrailleKeys = 1,
  .hasCommandKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI40)
};

static const ModelEntry modelEntry_B80 = {
  .hasCommandKeys = 1,
  .hasSecondThumbKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(B80)
};

static const ModelEntry modelEntry_touch = {
  .hasBrailleKeys = 1,
  .hasCommandKeys = 0,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(touch)
};

#define OPEN_READY_DELAY 100

#define SERIAL_PROBE_RESPONSE_TIMEOUT 1000
#define SERIAL_PROBE_RETRY_LIMIT 0

#define SERIAL_INIT_RESEND_DELAY 100
#define SERIAL_INIT_RESEND_LIMIT 10

#define MAXIMUM_TEXT_CELL_COUNT 0XFF

#define MAXIMUM_KEY_VALUE 0XFF
#define KEYS_BITMASK(name) BITMASK(name, (MAXIMUM_KEY_VALUE + 1), int)

#define BRAILLE_KEY_COUNT (8 + 1)
#define COMMAND_KEY_COUNT 6
#define THUMB_KEY_COUNT 4
#define JOYSTICK_KEY_COUNT 5

typedef struct {
  const char *name;
  int (*probeDisplay) (BrailleDisplay *brl);
  int (*writeCells) (BrailleDisplay *brl, const unsigned char *cells, unsigned char count);
  int (*processInputPacket) (BrailleDisplay *brl);
  int (*keepAwake) (BrailleDisplay *brl);
} ProtocolEntry;

struct BrailleDataStruct {
  const ProtocolEntry *protocol;
  const ModelEntry *model;

  uint32_t firmwareVersion;
  unsigned isBrailleNoteTouch:1;
  unsigned isOffline:1;

  struct {
    unsigned char count;
    KEYS_BITMASK(mask);
  } pressedKeys;

  struct {
    unsigned char rewrite;
    unsigned char cells[MAXIMUM_TEXT_CELL_COUNT];
  } text;

  struct {
    struct {
      unsigned char resendCount;
    } init;
  } serial;

  struct {
    struct {
      unsigned char reportSize;
    } pressedKeys;
  } hid;
};

static const ModelEntry *
getModelEntry (BrailleDisplay *brl) {
  if (brl->data->isBrailleNoteTouch) return &modelEntry_touch;

  switch (brl->textColumns) {
    case 14: return &modelEntry_BI14;
    case 32: return &modelEntry_BI32;
    case 40: return &modelEntry_BI40;
    case 80: return &modelEntry_B80;
    default: return NULL;
  }
}

static void
setModel (BrailleDisplay *brl) {
  brl->data->model = getModelEntry(brl);
}

static int
getDecimalValue (const char *digits, unsigned int count) {
  const char *end = digits + count;
  unsigned int result = 0;
  const char zero = '0';

  while (digits < end) {
    char digit = *digits++;
    if (digit >= zero) digit -= zero;

    if (digit < 0) return 0;
    if (digit > 9) return 0;

    result *= 10;
    result += digit;
  }

  return result;
}

static void
setFirmwareVersion (BrailleDisplay *brl, unsigned char major, unsigned char minor, unsigned char build) {
  logMessage(LOG_INFO, "Firmware Version: %u.%u.%u", major, minor, build);
  brl->data->firmwareVersion = (major << 16) | (minor << 8) << (build << 0);
}

static int
handleKeyEvent (BrailleDisplay *brl, unsigned char key, int press) {
  KeyGroup group;

  if (key < HW_KEY_ROUTING) {
    group = HW_GRP_NavigationKeys;
  } else {
    group = HW_GRP_RoutingKeys;
    key -= HW_KEY_ROUTING;
  }

  return enqueueKeyEvent(brl, group, key, press);
}

static int
isCalibrationKey (BrailleDisplay *brl, unsigned char key) {
  switch (key) {
    default:
      return 0;

    case HW_KEY_CAL_OK:
    case HW_KEY_CAL_FAIL:
    case HW_KEY_CAL_EMPTY:
    case HW_KEY_CAL_RESET:
      break;
  }

  releaseBrailleKeys(brl);
  BITMASK_ZERO(brl->data->pressedKeys.mask);
  brl->data->pressedKeys.count = 0;
  return 1;
}

static int
handleKeyPress (BrailleDisplay *brl, unsigned char key) {
  if (BITMASK_TEST(brl->data->pressedKeys.mask, key)) return 0;

  BITMASK_SET(brl->data->pressedKeys.mask, key);
  brl->data->pressedKeys.count += 1;

  handleKeyEvent(brl, key, 1);
  return 1;
}

static int
handleKeyRelease (BrailleDisplay *brl, unsigned char key) {
  if (!BITMASK_TEST(brl->data->pressedKeys.mask, key)) return 0;

  BITMASK_CLEAR(brl->data->pressedKeys.mask, key);
  brl->data->pressedKeys.count -= 1;

  handleKeyEvent(brl, key, 0);
  return 1;
}

static void
handlePressedKeysArray (BrailleDisplay *brl, unsigned char *keys, size_t count) {
  KEYS_BITMASK(pressedMask);
  BITMASK_ZERO(pressedMask);
  unsigned int pressedCount = 0;

  {
    const unsigned char *key = keys;
    const unsigned char *end = keys + count;

    while (key < end) {
      if (!*key) break;

      if (!BITMASK_TEST(pressedMask, *key)) {
        BITMASK_SET(pressedMask, *key);
        pressedCount += 1;

        if (isCalibrationKey(brl, *key)) return;
        handleKeyPress(brl, *key);
      }

      key += 1;
    }
  }

  if (brl->data->pressedKeys.count > pressedCount) {
    for (unsigned int key=0; key<=MAXIMUM_KEY_VALUE; key+=1) {
      if (!BITMASK_TEST(pressedMask, key)) {
        if (handleKeyRelease(brl, key)) {
          if (brl->data->pressedKeys.count == pressedCount) {
            break;
          }
        }
      }
    }
  }
}

static void
handlePoweringOff (BrailleDisplay *brl) {
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "powering off");
  brl->data->isOffline = 1;
}

static BraillePacketVerifierResult
verifySerialPacket (
  BrailleDisplay *brl,
  const unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      if (byte != ESC) return BRL_PVR_INVALID;
      *length = 3;
      break;

    case 3:
      *length += byte;
      break;

    default:
      break;
  }

  return BRL_PVR_INCLUDE;
}

static size_t
readSerialPacket (BrailleDisplay *brl, void *buffer, size_t size) {
  return readBraillePacket(brl, NULL, buffer, size, verifySerialPacket, NULL);
}

static int
writeSerialPacket (BrailleDisplay *brl, unsigned char type, unsigned char length, const void *data) {
  HW_Packet packet;

  packet.fields.header = ESC;
  packet.fields.type = type;
  packet.fields.length = length;

  if (data) memcpy(packet.fields.data.bytes, data, length);
  length += packet.fields.data.bytes - packet.bytes;

  return writeBraillePacket(brl, NULL, &packet, length);
}

static int
writeSerialRequest (BrailleDisplay *brl, unsigned char type) {
  return writeSerialPacket(brl, type, 0, NULL);
}

static int
writeSerialIdentifyRequest (BrailleDisplay *brl) {
  return writeSerialRequest(brl, HW_MSG_INIT);
}

static size_t
readSerialResponse (BrailleDisplay *brl, void *packet, size_t size) {
  return readSerialPacket(brl, packet, size);
}

static BrailleResponseResult
isSerialIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const HW_Packet *response = packet;

  if (response->fields.type != HW_MSG_INIT_RESP) return BRL_RSP_UNEXPECTED;
  if (!response->fields.data.init.stillInitializing) return BRL_RSP_DONE;

  if (++brl->data->serial.init.resendCount > SERIAL_INIT_RESEND_LIMIT) {
    logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "channel initialization timeout");
    return BRL_RSP_FAIL;
  }

  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "channel still initializing");
  asyncWait(SERIAL_INIT_RESEND_DELAY);

  if (writeSerialIdentifyRequest(brl)) return BRL_RSP_CONTINUE;
  return BRL_RSP_FAIL;
}

static int
probeSerialDisplay (BrailleDisplay *brl) {
  HW_Packet response;

  brl->data->serial.init.resendCount = 0;

  if (probeBrailleDisplay(brl, SERIAL_PROBE_RETRY_LIMIT,
                          NULL, SERIAL_PROBE_RESPONSE_TIMEOUT,
                          writeSerialIdentifyRequest,
                          readSerialResponse, &response, sizeof(response.bytes),
                          isSerialIdentityResponse)) {
    logMessage(LOG_INFO, "detected Humanware device: model=%u cells=%u",
               response.fields.data.init.modelIdentifier,
               response.fields.data.init.cellCount);

    switch (response.fields.data.init.modelIdentifier) {
      case HW_MODEL_BrailleNoteTouch:
        brl->data->isBrailleNoteTouch = 1;
        break;
    }

    brl->textColumns = response.fields.data.init.cellCount;
    setModel(brl);

    writeSerialRequest(brl, HW_MSG_GET_FIRMWARE_VERSION);
    writeSerialRequest(brl, HW_MSG_GET_KEYS);

    return 1;
  }

  return 0;
}

static int
writeSerialCells (BrailleDisplay *brl, const unsigned char *cells, unsigned char count) {
  return writeSerialPacket(brl, HW_MSG_DISPLAY, count, cells);
}

static int
processSerialInputPacket (BrailleDisplay *brl) {
  HW_Packet packet;
  size_t length = readSerialPacket(brl, &packet, sizeof(packet));
  if (!length) return 0;
  brl->data->isOffline = 0;

  switch (packet.fields.type) {
    case HW_MSG_KEYS:
      handlePressedKeysArray(brl, packet.fields.data.bytes, packet.fields.length);
      break;

    case HW_MSG_KEY_DOWN: {
      unsigned char key = packet.fields.data.key.id;
      if (isCalibrationKey(brl, key)) break;

      handleKeyPress(brl, key);
      break;
    }

    case HW_MSG_KEY_UP:
      handleKeyRelease(brl, packet.fields.data.key.id);
      break;

    case HW_MSG_FIRMWARE_VERSION_RESP:
      setFirmwareVersion(brl,
        packet.fields.data.firmwareVersion.major,
        packet.fields.data.firmwareVersion.minor,
        packet.fields.data.firmwareVersion.build);
      break;

    case HW_MSG_KEEP_AWAKE_RESP:
      break;

    case HW_MSG_POWERING_OFF:
      handlePoweringOff(brl);
      break;

    default:
      logUnexpectedPacket(&packet, length);
      break;
  }

  return 1;
}

static int
keepSerialAwake (BrailleDisplay *brl) {
  return writeSerialRequest(brl, HW_MSG_KEEP_AWAKE);
}

static const ProtocolEntry serialProtocol = {
  .name = "serial",
  .probeDisplay = probeSerialDisplay,
  .writeCells = writeSerialCells,
  .processInputPacket = processSerialInputPacket,
  .keepAwake = keepSerialAwake
};

static ssize_t
readHidFeature (
  BrailleDisplay *brl, unsigned char report,
  unsigned char *buffer, size_t size
) {
  if (size > 0) *buffer = 0;
  ssize_t length = gioGetHidFeature(brl->gioEndpoint, report, buffer, size);

  if (length != -1) {
    if ((length > 0) && (*buffer == report)) {
      logInputPacket(buffer, length);
      return length;
    }

    errno = EAGAIN;
  }

  logSystemError("USB HID feature read");
  return -1;
}

static int
writeHidReport (BrailleDisplay *brl, const void *data, size_t size) {
  logOutputPacket(data, size);

  {
    ssize_t result = gioWriteHidReport(brl->gioEndpoint, data, size);
    if (result != -1) return 1;
  }

  logSystemError("HID report write");
  return 0;
}

static BraillePacketVerifierResult
verifyHidPacket (
  BrailleDisplay *brl,
  const unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      switch (byte) {
        case HW_REP_FTR_Capabilities:
          *length = sizeof(HW_CapabilitiesReport);
          break;

        case HW_REP_FTR_Settings:
          *length = sizeof(HW_SettingsReport);
          break;

        case HW_REP_FTR_Configuration:
          *length = sizeof(HW_ConfigurationReport);
          break;

        case HW_REP_IN_PressedKeys:
          *length = brl->data->hid.pressedKeys.reportSize;
          break;

        case HW_REP_FTR_KeepAwake:
          *length = sizeof(HW_KeepAwakeReport);
          break;

        case HW_REP_IN_PoweringOff:
          *length = sizeof(HW_PoweringOffReport);
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
readHidPacket (BrailleDisplay *brl, void *buffer, size_t size) {
  return readBraillePacket(brl, NULL, buffer, size, verifyHidPacket, NULL);
}

static int
probeHidDisplay (BrailleDisplay *brl) {
  HW_CapabilitiesReport capabilities;
  unsigned char *const buffer = (unsigned char *)&capabilities;
  const size_t size = sizeof(capabilities);

  ssize_t length = readHidFeature(brl, HW_REP_FTR_Capabilities, buffer, size);
  if (length == -1) return 0;
  memset(&buffer[length], 0, (size - length));

  setFirmwareVersion(brl,
    getDecimalValue(&capabilities.version.major, 1),
    getDecimalValue(&capabilities.version.minor, 1),
    getDecimalValue(&capabilities.version.build[0], 2));

  brl->textColumns = capabilities.cellCount;
  setModel(brl);

  {
    unsigned char *size = &brl->data->hid.pressedKeys.reportSize;
    *size = 1 + THUMB_KEY_COUNT + brl->textColumns;
    if (brl->data->model->hasBrailleKeys) *size += BRAILLE_KEY_COUNT;
    if (brl->data->model->hasCommandKeys) *size += COMMAND_KEY_COUNT;
    if (brl->data->model->hasJoystick) *size += JOYSTICK_KEY_COUNT;
    if (brl->data->model->hasSecondThumbKeys) *size += THUMB_KEY_COUNT;
  }

  return 1;
}

static int
writeHidCells (BrailleDisplay *brl, const unsigned char *cells, unsigned char count) {
  unsigned char buffer[4 + count];
  unsigned char *byte = buffer;

  *byte++ = HW_REP_OUT_WriteCells;
  *byte++ = 1;
  *byte++ = 0;
  *byte++ = count;
  byte = mempcpy(byte, cells, count);

  return writeHidReport(brl, buffer, byte-buffer);
}

static int
processHidInputPacket (BrailleDisplay *brl) {
  unsigned char packet[0XFF];
  size_t length = readHidPacket(brl, packet, sizeof(packet));
  if (!length) return 0;
  brl->data->isOffline = 0;

  switch (packet[0]) {
    case HW_REP_IN_PressedKeys: {
      const unsigned int offset = 1;

      handlePressedKeysArray(brl, packet+offset, length-offset);
      break;
    }

    case HW_REP_IN_PoweringOff:
      handlePoweringOff(brl);
      break;

    default:
      logUnexpectedPacket(packet, length);
    case HW_REP_FTR_Settings:
    case HW_REP_FTR_Configuration:
      break;
  }

  return 1;
}

static int
keepHidAwake (BrailleDisplay *brl) {
  HW_KeepAwakeReport report;

  memset(&report, 0, sizeof(report));
  report.reportIdentifier = HW_REP_FTR_KeepAwake;

  return writeHidReport(brl, &report, sizeof(report));
}

static const ProtocolEntry hidProtocol = {
  .name = "HID",
  .probeDisplay = probeHidDisplay,
  .writeCells = writeHidCells,
  .processInputPacket = processHidInputPacket,
  .keepAwake = keepHidAwake
};

typedef struct {
  const ProtocolEntry *protocol;
  unsigned isTouch:1;
} ResourceData;

static const ResourceData resourceData_serial = {
  .isTouch = 0, // probing detects if it's a Touch or not
  .protocol = &serialProtocol
};

static const ResourceData resourceData_HID = {
  .isTouch = 0, // only for non-Touch models
  .protocol = &hidProtocol
};

static const ResourceData resourceData_touch = {
  .isTouch = 1,
  .protocol = &hidProtocol
};

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 115200,
    .parity = SERIAL_PARITY_EVEN
  };

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* Brailliant BI 32/40, Brailliant B 80 (serial protocol) */
      .vendor=0X1C71, .product=0XC005, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=2, .outputEndpoint=3,
      .serial = &serialParameters,
      .data = &resourceData_serial,
      .resetDevice = 1
    },

    { /* Brailliant BI 14 (serial protocol) */
      .vendor=0X1C71, .product=0XC021, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .serial = &serialParameters,
      .data = &resourceData_serial,
      .resetDevice = 1
    },

    { /* non-Touch models (HID protocol) */
      .vendor=0X1C71, .product=0XC006,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1,
      .data = &resourceData_HID
    },

    { /* BrailleNote Touch (HID protocol) */
      .vendor=0X1C71, .product=0XC00A,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1,
      .data = &resourceData_touch
    },
  END_USB_CHANNEL_DEFINITIONS

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;
  descriptor.serial.options.applicationData = &resourceData_serial;
  descriptor.serial.options.readyDelay = OPEN_READY_DELAY;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.options.readyDelay = OPEN_READY_DELAY;

  descriptor.bluetooth.channelNumber = 1;
  descriptor.bluetooth.discoverChannel = 1;
  descriptor.bluetooth.options.applicationData = &resourceData_serial;
  descriptor.bluetooth.options.readyDelay = OPEN_READY_DELAY;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    const ResourceData *resourceData = gioGetApplicationData(brl->gioEndpoint);
    brl->data->protocol = resourceData->protocol;
    brl->data->isBrailleNoteTouch = resourceData->isTouch;
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
      if (brl->data->protocol->probeDisplay(brl)) {
        setBrailleKeyTable(brl, brl->data->model->keyTableDefinition);
        makeOutputTable(dotsTable_ISO11548_1);
        brl->data->text.rewrite = 1;
        return 1;
      }

      disconnectResource(brl);
    }

    free(brl->data);
    brl->data = NULL;
  } else {
    logMallocError();
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  disconnectResource(brl);
  free(brl->data);
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  const size_t count = brl->textColumns;

  if (cellsHaveChanged(brl->data->text.cells, brl->buffer, count, NULL, NULL, &brl->data->text.rewrite)) {
    unsigned char cells[count];

    translateOutputCells(cells, brl->data->text.cells, count);
    if (!brl->data->protocol->writeCells(brl, cells, count)) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  while (brl->data->protocol->processInputPacket(brl));
  if (errno != EAGAIN) return BRL_CMD_RESTARTBRL;
  if (brl->data->isOffline) return BRL_CMD_OFFLINE;
  return EOF;
}
