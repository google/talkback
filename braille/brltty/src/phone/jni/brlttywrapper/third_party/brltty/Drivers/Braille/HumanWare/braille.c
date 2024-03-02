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
  KEY_NAME_ENTRY(HW_KEY_ThumbPrevious, "ThumbPrevious"),
  KEY_NAME_ENTRY(HW_KEY_ThumbLeft, "ThumbLeft"),
  KEY_NAME_ENTRY(HW_KEY_ThumbRight, "ThumbRight"),
  KEY_NAME_ENTRY(HW_KEY_ThumbNext, "ThumbNext"),
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

BEGIN_KEY_NAME_TABLES(C20)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(M40)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(NLS)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(one)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(BI40X)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
  KEY_NAME_TABLE(command),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(BI20X)
  KEY_NAME_TABLE(routing),
  KEY_NAME_TABLE(thumb),
  KEY_NAME_TABLE(braille),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(BI14)
DEFINE_KEY_TABLE(BI32)
DEFINE_KEY_TABLE(BI40)
DEFINE_KEY_TABLE(B80)
DEFINE_KEY_TABLE(touch)
DEFINE_KEY_TABLE(C20)
DEFINE_KEY_TABLE(M40)
DEFINE_KEY_TABLE(NLS)
DEFINE_KEY_TABLE(one)
DEFINE_KEY_TABLE(BI40X)
DEFINE_KEY_TABLE(BI20X)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(BI14),
  &KEY_TABLE_DEFINITION(BI32),
  &KEY_TABLE_DEFINITION(BI40),
  &KEY_TABLE_DEFINITION(B80),
  &KEY_TABLE_DEFINITION(touch),
  &KEY_TABLE_DEFINITION(C20),
  &KEY_TABLE_DEFINITION(M40),
  &KEY_TABLE_DEFINITION(NLS),
  &KEY_TABLE_DEFINITION(one),
  &KEY_TABLE_DEFINITION(BI40X),
  &KEY_TABLE_DEFINITION(BI20X),
END_KEY_TABLE_LIST

typedef struct {
  const char *modelName;
  const KeyTableDefinition *keyTableDefinition;
  HW_ModelIdentifier modelIdentifier;
  unsigned char pressedKeysReportSize;

  unsigned char hasBrailleKeys:1;
  unsigned char hasCommandKeys:1;
  unsigned char hasJoystick:1;
  unsigned char hasSecondThumbKeys:1;
} ModelEntry;

static const ModelEntry modelEntry_BI14 = {
  .modelName = "Brailliant BI 14",
  .hasBrailleKeys = 1,
  .hasJoystick = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI14)
};

static const ModelEntry modelEntry_BI32 = {
  .modelName = "Brailliant BI 32",
  .hasBrailleKeys = 1,
  .hasCommandKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI32)
};

static const ModelEntry modelEntry_BI40 = {
  .modelName = "Brailliant BI 40",
  .hasBrailleKeys = 1,
  .hasCommandKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI40)
};

static const ModelEntry modelEntry_B80 = {
  .modelName = "Brailliant B 80",
  .hasCommandKeys = 1,
  .hasSecondThumbKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(B80)
};

static const ModelEntry modelEntry_touch = {
  .modelName = "BrailleNote Touch",
  .modelIdentifier = HW_MODEL_HW_BRAILLE_NOTE_TOUCH,
  .hasBrailleKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(touch)
};

static const ModelEntry modelEntry_C20 = {
  .modelName = "APH Chameleon 20",
  .modelIdentifier = HW_MODEL_APH_CHAMELEON_20,
  .hasBrailleKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(C20)
};

static const ModelEntry modelEntry_M40 = {
  .modelName = "APH Mantis Q40",
  .modelIdentifier = HW_MODEL_APH_MANTIS_Q40,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(M40)
};

static const ModelEntry modelEntry_NLS = {
  .modelName = "NLS eReader",
  .modelIdentifier = HW_MODEL_NLS_EREADER,
  .hasBrailleKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(NLS)
};

static const ModelEntry modelEntry_one = {
  .modelName = "HumanWare BrailleOne",
  .modelIdentifier = HW_MODEL_HW_BRAILLE_ONE,
  .hasBrailleKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(one)
};

static const ModelEntry modelEntry_BI40X = {
  .modelName = "Brailliant BI 40X",
  .pressedKeysReportSize = 46,
  .hasBrailleKeys = 1,
  .hasCommandKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI40X)
};

static const ModelEntry modelEntry_BI20X = {
  .modelName = "Brailliant BI 20X",
  .hasBrailleKeys = 1,
  .keyTableDefinition = &KEY_TABLE_DEFINITION(BI20X)
};

static const ModelEntry *modelTable[] = {
  &modelEntry_BI14,
  &modelEntry_BI32,
  &modelEntry_BI40,
  &modelEntry_B80,
  &modelEntry_touch,
  &modelEntry_C20,
  &modelEntry_M40,
  &modelEntry_NLS,
  &modelEntry_one,
  &modelEntry_BI40X,
  &modelEntry_BI20X,
};

static unsigned char modelCount = ARRAY_COUNT(modelTable);

static const ModelEntry *
getModelByIdentifier (HW_ModelIdentifier identifier) {
  if (identifier) {
    const ModelEntry *const *model = modelTable;
    const ModelEntry *const *end = model + modelCount;

    while (model < end) {
      if ((*model)->modelIdentifier == identifier) return *model;
      model += 1;
    }
  }

  logMessage(LOG_CATEGORY(BRAILLE_DRIVER),
    "unknown model identifier: %u", identifier
  );

  return NULL;
}

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
getModelByCellCount (BrailleDisplay *brl) {
  unsigned int cellCount = brl->textColumns;

  switch (cellCount) {
    case 14: return &modelEntry_BI14;
    case 32: return &modelEntry_BI32;
    case 40: return &modelEntry_BI40;
    case 80: return &modelEntry_B80;

    default:
      logMessage(LOG_WARNING, "unknown cell count: %u", cellCount);
      return NULL;
  }
}

static int
setModel (BrailleDisplay *brl) {
  if (!brl->data->model) {
    if (!(brl->data->model = getModelByCellCount(brl))) {
      return 0;
    }
  }

  logMessage(LOG_DEBUG, "Model Name: %s", brl->data->model->modelName);
  return 1;
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
  unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      if (byte != ASCII_ESC) return BRL_PVR_INVALID;
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

  packet.fields.header = ASCII_ESC;
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

    {
      unsigned char identifier = response.fields.data.init.modelIdentifier;
      const ModelEntry *model = getModelByIdentifier(identifier);

      if (model) {
        if (!brl->data->model) {
          brl->data->model = model;
        } else if (model != brl->data->model) {
        }
      }
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
  BrailleDisplay *brl, HidReportIdentifier identifier,
  unsigned char *buffer, size_t size
) {
  ssize_t length = gioGetHidFeature(brl->gioEndpoint, identifier, buffer, size);

  if (length != -1) {
    if ((length > 0) && (*buffer == identifier)) {
      logInputPacket(buffer, length);
      return length;
    }

    errno = EAGAIN;
  }

  logSystemError("HID feature read");
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
  unsigned char *bytes, size_t size,
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

static size_t
getPressedKeysReportSize (BrailleDisplay *brl) {
  {
    size_t size = gioGetHidInputSize(brl->gioEndpoint, HW_REP_IN_PressedKeys);
    if (size) return size;
  }

  {
    size_t size = brl->data->model->pressedKeysReportSize;
    if (size) return size;
  }

  size_t size = 1;
  size += brl->textColumns;
  size += THUMB_KEY_COUNT;
  if (brl->data->model->hasBrailleKeys) size += BRAILLE_KEY_COUNT;
  if (brl->data->model->hasCommandKeys) size += COMMAND_KEY_COUNT;
  if (brl->data->model->hasJoystick) size += JOYSTICK_KEY_COUNT;
  if (brl->data->model->hasSecondThumbKeys) size += THUMB_KEY_COUNT;
  return size;
}

static int
probeHidDisplay (BrailleDisplay *brl) {
  brl->textColumns = 0;

  if (!brl->textColumns) {
    size_t size = gioGetHidOutputSize(brl->gioEndpoint, HW_REP_OUT_WriteCells);
    if (size > 4) brl->textColumns = size - 4;
  }

  if (!brl->textColumns) {
    HW_CapabilitiesReport capabilities;
    unsigned char *const buffer = (unsigned char *)&capabilities;
    const size_t size = sizeof(capabilities);

    ssize_t length = readHidFeature(brl, HW_REP_FTR_Capabilities, buffer, size);
    if (length == -1) return 0;
    memset(&buffer[length], 0, (size - length));

    setFirmwareVersion(brl,
      getDecimalValue(&capabilities.version.major, 1),
      getDecimalValue(&capabilities.version.minor, 1),
      getDecimalValue(&capabilities.version.build[0], 2)
    );

    brl->textColumns = capabilities.cellCount;
  }

  {
    unsigned char *size = &brl->data->hid.pressedKeys.reportSize;
    *size = getPressedKeysReportSize(brl);

    logMessage(LOG_CATEGORY(BRAILLE_DRIVER),
      "pressed keys report size: %u", *size
    );
  }

  if (!setModel(brl)) return 0;
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
  const ModelEntry *model;
} ResourceData;

static const ResourceData resourceData_serial_generic = {
  .protocol = &serialProtocol
};

static const ResourceData resourceData_serial_BI14 = {
  .model = &modelEntry_BI14,
  .protocol = &serialProtocol
};

static const ResourceData resourceData_serial_C20 = {
  .model = &modelEntry_C20,
  .protocol = &serialProtocol
};

static const ResourceData resourceData_serial_M40 = {
  .model = &modelEntry_M40,
  .protocol = &serialProtocol
};

static const ResourceData resourceData_serial_NLS = {
  .model = &modelEntry_NLS,
  .protocol = &serialProtocol
};

static const ResourceData resourceData_serial_one = {
  .model = &modelEntry_one,
  .protocol = &serialProtocol
};

static const ResourceData resourceData_HID_generic = {
  .protocol = &hidProtocol
};

static const ResourceData resourceData_HID_touch = {
  .model = &modelEntry_touch,
  .protocol = &hidProtocol
};

static const ResourceData resourceData_HID_C20 = {
  .model = &modelEntry_C20,
  .protocol = &hidProtocol
};

static const ResourceData resourceData_HID_M40 = {
  .model = &modelEntry_M40,
  .protocol = &hidProtocol
};

static const ResourceData resourceData_HID_NLS = {
  .model = &modelEntry_NLS,
  .protocol = &hidProtocol
};

static const ResourceData resourceData_HID_one = {
  .model = &modelEntry_one,
  .protocol = &hidProtocol
};

static const ResourceData resourceData_HID_BI40X = {
  .model = &modelEntry_BI40X,
  .protocol = &hidProtocol
};

static const ResourceData resourceData_HID_BI20X = {
  .model = &modelEntry_BI20X,
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
      .data = &resourceData_serial_generic,
      .resetDevice = 1
    },

    { /* Brailliant BI 14 (serial protocol) */
      .vendor=0X1C71, .product=0XC021, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=1,
      .serial = &serialParameters,
      .data = &resourceData_serial_BI14,
      .resetDevice = 1
    },

    { /* APH Chameleon 20 (serial protocol) */
      .vendor=0X1C71, .product=0XC104, 
      .configuration=1, .interface=5, .alternative=0,
      .inputEndpoint=10, .outputEndpoint=11,
      .serial = &serialParameters,
      .data = &resourceData_serial_C20,
      .resetDevice = 1
    },

    { /* APH Mantis Q40 (serial protocol) */
      .vendor=0X1C71, .product=0XC114, 
      .configuration=1, .interface=5, .alternative=0,
      .inputEndpoint=10, .outputEndpoint=11,
      .serial = &serialParameters,
      .data = &resourceData_serial_M40,
      .resetDevice = 1
    },

    { /* NLS eReader (serial protocol) */
      .vendor=0X1C71, .product=0XCE04, 
      .configuration=1, .interface=5, .alternative=0,
      .inputEndpoint=10, .outputEndpoint=11,
      .serial = &serialParameters,
      .data = &resourceData_serial_NLS,
      .resetDevice = 1
    },

    { /* Humanware BrailleOne (serial protocol) */
      .vendor=0X1C71, .product=0XC124, 
      .configuration=1, .interface=5, .alternative=0,
      .inputEndpoint=10, .outputEndpoint=11,
      .serial = &serialParameters,
      .data = &resourceData_serial_one,
      .resetDevice = 1
    },

    { /* non-Touch models (HID protocol) */
      .vendor=0X1C71, .product=0XC006,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1,
      .data = &resourceData_HID_generic
    },

    { /* BrailleNote Touch (HID protocol) */
      .vendor=0X1C71, .product=0XC00A,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1,
      .data = &resourceData_HID_touch
    },

    { /* APH Chameleon 20 (HID protocol, firmware 1.0) */
      .vendor=0X1C71, .product=0XC101, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=4, .outputEndpoint=5,
      .verifyInterface = 1,
      .data = &resourceData_HID_C20,
      .resetDevice = 1
    },

    { /* APH Chameleon 20 (HID protocol, firmware 1.1) */
      .vendor=0X1C71, .product=0XC101, 
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface = 1,
      .data = &resourceData_HID_C20,
      .resetDevice = 1
    },

    { /* APH Mantis Q40 (HID protocol, firmware 1.0) */
      .vendor=0X1C71, .product=0XC111, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=4, .outputEndpoint=5,
      .verifyInterface = 1,
      .data = &resourceData_HID_M40,
      .resetDevice = 1
    },

    { /* APH Mantis Q40 (HID protocol, firmware 1.1) */
      .vendor=0X1C71, .product=0XC111, 
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface = 1,
      .data = &resourceData_HID_M40,
      .resetDevice = 1
    },

    { /* NLS eReader (HID protocol, firmware 1.0) */
      .vendor=0X1C71, .product=0XCE01, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=4, .outputEndpoint=5,
      .verifyInterface = 1,
      .data = &resourceData_HID_NLS,
      .resetDevice = 1
    },

    { /* NLS eReader (HID protocol, firmware 1.1) */
      .vendor=0X1C71, .product=0XCE01, 
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface = 1,
      .data = &resourceData_HID_NLS,
      .resetDevice = 1
    },

    { /* Humanware BrailleOne (HID protocol, firmware 1.0) */
      .vendor=0X1C71, .product=0XC121, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=4, .outputEndpoint=5,
      .verifyInterface = 1,
      .data = &resourceData_HID_one,
      .resetDevice = 1
    },

    { /* Humanware BrailleOne (HID protocol, firmware 1.1) */
      .vendor=0X1C71, .product=0XC121, 
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface = 1,
      .data = &resourceData_HID_one,
      .resetDevice = 1
    },

    { /* Humanware Brailliant BI 40X (HID protocol, firmware 1.0) */
      .vendor=0X1C71, .product=0XC131, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=4, .outputEndpoint=5,
      .verifyInterface = 1,
      .data = &resourceData_HID_BI40X,
      .resetDevice = 1
    },

    { /* Humanware Brailliant BI 40X (HID protocol, firmware 1.1) */
      .vendor=0X1C71, .product=0XC131, 
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface = 1,
      .data = &resourceData_HID_BI40X,
      .resetDevice = 1
    },

    { /* Humanware Brailliant BI 20X (HID protocol, firmware 1.0) */
      .vendor=0X1C71, .product=0XC141, 
      .configuration=1, .interface=1, .alternative=0,
      .inputEndpoint=4, .outputEndpoint=5,
      .verifyInterface = 1,
      .data = &resourceData_HID_BI20X,
      .resetDevice = 1
    },

    { /* Humanware Brailliant BI 20X (HID protocol, firmware 1.1) */
      .vendor=0X1C71, .product=0XC141, 
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=1, .outputEndpoint=2,
      .verifyInterface = 1,
      .data = &resourceData_HID_BI20X,
      .resetDevice = 1
    },
  END_USB_CHANNEL_DEFINITIONS

  BEGIN_HID_MODEL_TABLE
    { .name = "APH Chameleon 20",
      .data = &resourceData_HID_C20,
    },

    { .name = "APH Mantis Q40",
      .data = &resourceData_HID_M40,
    },

    { .name = "NLS eReader Humanware",
      .data = &resourceData_HID_NLS,
    },

    { .name = "Humanware BrailleOne",
      .data = &resourceData_HID_one,
    },

    { .name = "Brailliant BI 40X",
      .data = &resourceData_HID_BI40X,
    },

    { .name = "Brailliant BI 20X",
      .data = &resourceData_HID_BI20X,
    },
  END_HID_MODEL_TABLE

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  descriptor.serial.parameters = &serialParameters;
  descriptor.serial.options.applicationData = &resourceData_serial_generic;
  descriptor.serial.options.readyDelay = OPEN_READY_DELAY;

  descriptor.usb.channelDefinitions = usbChannelDefinitions;
  descriptor.usb.options.readyDelay = OPEN_READY_DELAY;

  descriptor.bluetooth.channelNumber = 1;
  descriptor.bluetooth.discoverChannel = 1;
  descriptor.bluetooth.options.applicationData = &resourceData_serial_generic;
  descriptor.bluetooth.options.readyDelay = OPEN_READY_DELAY;

  descriptor.hid.modelTable = hidModelTable;

  if (connectBrailleResource(brl, identifier, &descriptor, NULL)) {
    const ResourceData *resourceData = gioGetApplicationData(brl->gioEndpoint);
    brl->data->protocol = resourceData->protocol;
    brl->data->model = resourceData->model;
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
