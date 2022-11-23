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

/* FreedomScientific/braille.c - Braille display library
 * Freedom Scientific's Focus and PacMate series
 * Author: Dave Mielke <dave@mielke.cc>
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <errno.h>

#include "log.h"
#include "parse.h"
#include "async_alarm.h"

#define BRLSTAT ST_AlvaStyle
#define BRL_HAVE_PACKET_IO
#include "brl_driver.h"
#include "brldefs-fs.h"

BEGIN_KEY_NAME_TABLE(common)
  KEY_NAME_ENTRY(FS_KEY_LeftAdvance, "LeftAdvance"),
  KEY_NAME_ENTRY(FS_KEY_RightAdvance, "RightAdvance"),
  KEY_NAME_ENTRY(FS_KEY_LeftGdf, "LeftSelector"),
  KEY_NAME_ENTRY(FS_KEY_RightGdf, "RightSelector"),

  KEY_GROUP_ENTRY(FS_GRP_RoutingKeys, "RoutingKey"),
  KEY_GROUP_ENTRY(FS_GRP_NavrowKeys, "NavrowKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(wheel)
  KEY_NAME_ENTRY(FS_KEY_LeftWheel, "LeftWheelPress"),
  KEY_NAME_ENTRY(FS_KEY_RightWheel, "RightWheelPress"),

  KEY_NAME_ENTRY(FS_KEY_WHEEL+0, "LeftWheelUp"),
  KEY_NAME_ENTRY(FS_KEY_WHEEL+1, "LeftWheelDown"),
  KEY_NAME_ENTRY(FS_KEY_WHEEL+2, "RightWheelDown"),
  KEY_NAME_ENTRY(FS_KEY_WHEEL+3, "RightWheelUp"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(nav)
  KEY_NAME_ENTRY(FS_KEY_LeftWheel, "LeftNavPress"),
  KEY_NAME_ENTRY(FS_KEY_RightWheel, "RightNavPress"),

  KEY_NAME_ENTRY(FS_KEY_WHEEL+0, "LeftNavUp"),
  KEY_NAME_ENTRY(FS_KEY_WHEEL+1, "LeftNavDown"),
  KEY_NAME_ENTRY(FS_KEY_WHEEL+2, "RightNavDown"),
  KEY_NAME_ENTRY(FS_KEY_WHEEL+3, "RightNavUp"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(focus)
  KEY_NAME_ENTRY(FS_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(FS_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(FS_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(FS_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(FS_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(FS_KEY_Dot6, "Dot6"),
  KEY_NAME_ENTRY(FS_KEY_Dot7, "Dot7"),
  KEY_NAME_ENTRY(FS_KEY_Dot8, "Dot8"),

  KEY_NAME_ENTRY(FS_KEY_Space, "Space"),
  KEY_NAME_ENTRY(FS_KEY_LeftShift, "LeftShift"),
  KEY_NAME_ENTRY(FS_KEY_RightShift, "RightShift"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(bumpers)
  KEY_NAME_ENTRY(FS_KEY_LeftBumperUp, "LeftBumperUp"),
  KEY_NAME_ENTRY(FS_KEY_LeftBumperDown, "LeftBumperDown"),
  KEY_NAME_ENTRY(FS_KEY_RightBumperUp, "RightBumperUp"),
  KEY_NAME_ENTRY(FS_KEY_RightBumperDown, "RightBumperDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(rockers)
  KEY_NAME_ENTRY(FS_KEY_LeftRockerUp, "LeftRockerUp"),
  KEY_NAME_ENTRY(FS_KEY_LeftRockerDown, "LeftRockerDown"),
  KEY_NAME_ENTRY(FS_KEY_RightRockerUp, "RightRockerUp"),
  KEY_NAME_ENTRY(FS_KEY_RightRockerDown, "RightRockerDown"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(pacmate)
  KEY_NAME_ENTRY(FS_KEY_HOT+0, "Hot1"),
  KEY_NAME_ENTRY(FS_KEY_HOT+1, "Hot2"),
  KEY_NAME_ENTRY(FS_KEY_HOT+2, "Hot3"),
  KEY_NAME_ENTRY(FS_KEY_HOT+3, "Hot4"),
  KEY_NAME_ENTRY(FS_KEY_HOT+4, "Hot5"),
  KEY_NAME_ENTRY(FS_KEY_HOT+5, "Hot6"),
  KEY_NAME_ENTRY(FS_KEY_HOT+6, "Hot7"),
  KEY_NAME_ENTRY(FS_KEY_HOT+7, "Hot8"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(focus_basic)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(nav),
  KEY_NAME_TABLE(focus),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(focus_large)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(nav),
  KEY_NAME_TABLE(focus),
  KEY_NAME_TABLE(bumpers),
  KEY_NAME_TABLE(rockers),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(focus_small)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(nav),
  KEY_NAME_TABLE(focus),
  KEY_NAME_TABLE(rockers),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(pacmate)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(wheel),
  KEY_NAME_TABLE(pacmate),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(focus_basic)
DEFINE_KEY_TABLE(focus_large)
DEFINE_KEY_TABLE(focus_small)
DEFINE_KEY_TABLE(pacmate)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(focus_basic),
  &KEY_TABLE_DEFINITION(focus_large),
  &KEY_TABLE_DEFINITION(focus_small),
  &KEY_TABLE_DEFINITION(pacmate),
END_KEY_TABLE_LIST

typedef enum {
  PKT_QUERY  = 0X00, /* host->unit: request device information */
  PKT_ACK    = 0X01, /* unit->host: acknowledge packet receipt */
  PKT_NAK    = 0X02, /* unit->host: negative acknowledge, report error */
  PKT_KEY    = 0X03, /* unit->host: key event */
  PKT_BUTTON = 0X04, /* unit->host: routing button event */
  PKT_WHEEL  = 0X05, /* unit->host: whiz wheel event */
  PKT_HVADJ  = 0X08, /* host->unit: set braille display voltage */
  PKT_BEEP   = 0X09, /* host->unit: sound short beep */
  PKT_CONFIG = 0X0F, /* host->unit: configure device options */
  PKT_INFO   = 0X80, /* unit->host: response to query packet */
  PKT_WRITE  = 0X81, /* host->unit: write to braille display */
  PKT_EXTKEY = 0X82  /* unit->host: extended keys event */
} PacketType;

typedef enum {
  PKT_ERR_TIMEOUT   = 0X30, /* no data received from host for a while */
  PKT_ERR_CHECKSUM  = 0X31, /* incorrect checksum */
  PKT_ERR_TYPE      = 0X32, /* unsupported packet type */
  PKT_ERR_PARAMETER = 0X33, /* invalid parameter */
  PKT_ERR_SIZE      = 0X34, /* write size too large */
  PKT_ERR_POSITION  = 0X35, /* write position too large */
  PKT_ERR_OVERRUN   = 0X36, /* message queue overflow */
  PKT_ERR_POWER     = 0X37, /* insufficient USB power */
  PKT_ERR_SPI       = 0X38  /* timeout on SPI bus */
} PacketError;

typedef enum {
  PKT_EXT_HVADJ    = 0X08, /* error in varibraille packet */
  PKT_EXT_BEEP     = 0X09, /* error in beep packet */
  PKT_EXT_CLEAR    = 0X31, /* error in ClearMsgBuf function */
  PKT_EXT_LOOP     = 0X32, /* timing loop in ParseCommands function */
  PKT_EXT_TYPE     = 0X33, /* unknown packet type in ParseCommands function */
  PKT_EXT_CMDWRITE = 0X34, /* error in CmdWrite function */
  PKT_EXT_UPDATE   = 0X7E, /* error in update packet */
  PKT_EXT_DIAG     = 0X7F, /* error in diag packet */
  PKT_EXT_QUERY    = 0X80, /* error in query packet */
  PKT_EXT_WRITE    = 0X81  /* error in write packet */
} PacketExtended;

typedef enum {
  OPT_EXTKEY = 0X01  /* send extended key events */
} UnitOption;

typedef struct {
  unsigned char type;
  unsigned char arg1;
  unsigned char arg2;
  unsigned char arg3;
} PacketHeader;

#define PACKET_PAYLOAD_INFO_MANUFACTURER_SIZE 24
#define PACKET_PAYLOAD_INFO_MODEL_SIZE 16
#define PACKET_PAYLOAD_INFO_FIRMWARE_SIZE 8

typedef struct {
  PacketHeader header;

  union {
    unsigned char bytes[0X100];

    struct {
      char manufacturer[PACKET_PAYLOAD_INFO_MANUFACTURER_SIZE];
      char model[PACKET_PAYLOAD_INFO_MODEL_SIZE];
      char firmware[PACKET_PAYLOAD_INFO_FIRMWARE_SIZE];
    } info;

    struct {
      unsigned char bytes[4];
    } extkey;
  } payload;
} Packet;

typedef struct {
  const KeyTableDefinition *keyTableDefinition;
  signed char hotkeysRow;
} ModelTypeEntry;

typedef enum {
  MOD_TYPE_Focus,
  MOD_TYPE_PacMate
} ModelType;

static const ModelTypeEntry modelTypeTable[] = {
  [MOD_TYPE_Focus] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(focus_basic),
    .hotkeysRow = -1
  },

  [MOD_TYPE_PacMate] = {
    .keyTableDefinition = &KEY_TABLE_DEFINITION(pacmate),
    .hotkeysRow = 1
  }
};

typedef struct {
  const char *identifier;
  const DotsTable *dotsTable;
  unsigned char cellCount;
  unsigned char type;
} ModelEntry;

static const DotsTable dotsTable_Focus1 = {
  0X01, 0X02, 0X04, 0X10, 0X20, 0X40, 0X08, 0X80
};

static const ModelEntry modelTable[] = {
  { .identifier = "Focus 14",
    .dotsTable = &dotsTable_ISO11548_1,
    .cellCount = 14,
    .type = MOD_TYPE_Focus
  },

  { .identifier = "Focus 40",
    .dotsTable = &dotsTable_ISO11548_1,
    .cellCount = 40,
    .type = MOD_TYPE_Focus
  },

  { .identifier = "Focus 44",
    .dotsTable = &dotsTable_Focus1,
    .cellCount = 44,
    .type = MOD_TYPE_Focus
  },

  { .identifier = "Focus 70",
    .dotsTable = &dotsTable_Focus1,
    .cellCount = 70,
    .type = MOD_TYPE_Focus
  },

  { .identifier = "Focus 80",
    .dotsTable = &dotsTable_ISO11548_1,
    .cellCount = 80,
    .type = MOD_TYPE_Focus
  },

  { .identifier = "Focus 84",
    .dotsTable = &dotsTable_Focus1,
    .cellCount = 84,
    .type = MOD_TYPE_Focus
  },

  { .identifier = "pm display 20",
    .dotsTable = &dotsTable_ISO11548_1,
    .cellCount = 20,
    .type = MOD_TYPE_PacMate
  },

  { .identifier = "pm display 40",
    .dotsTable = &dotsTable_ISO11548_1,
    .cellCount = 40,
    .type = MOD_TYPE_PacMate
  },

  { .identifier = NULL }
};

typedef void (*AcknowledgementHandler) (BrailleDisplay *brl, int ok);

struct BrailleDataStruct {
  int queryAcknowledged;
  const ModelEntry *model;
  const KeyTableDefinition *keyTableDefinition;

  ModelEntry genericModelEntry;
  char genericModelIdentifier[PACKET_PAYLOAD_INFO_MODEL_SIZE];

  unsigned char outputBuffer[UINT8_MAX + 1];
  int writeFirst;
  int writeLast;
  int writingFirst;
  int writingLast;

  AcknowledgementHandler acknowledgementHandler;
  AsyncHandle missingAcknowledgementAlarm;

  unsigned char configFlags;
  int firmnessSetting;

  int outputPayloadLimit;

  uint64_t oldKeys;
};

static int
writePacket (
  BrailleDisplay *brl,
  unsigned char type,
  unsigned char arg1,
  unsigned char arg2,
  unsigned char arg3,
  const unsigned char *data
) {
  Packet packet;
  int size = sizeof(packet.header);
  unsigned char checksum = 0;

  checksum -= (packet.header.type = type);
  checksum -= (packet.header.arg1 = arg1);
  checksum -= (packet.header.arg2 = arg2);
  checksum -= (packet.header.arg3 = arg3);

  if (data) {
    unsigned char length = packet.header.arg1;
    int index;

    for (index=0; index<length; index+=1)
      checksum -= (packet.payload.bytes[index] = data[index]);

    packet.payload.bytes[length] = checksum;
    size += length + 1;
  }

  return writeBraillePacket(brl, NULL, &packet, size);
}

static void
logNegativeAcknowledgement (const Packet *packet) {
  const char *problem;
  const char *component;

  switch (packet->header.arg1) {
    default:
      problem = "unknown problem";
      break;
    case PKT_ERR_TIMEOUT:
      problem = "timeout during packet transmission";
      break;
    case PKT_ERR_CHECKSUM:
      problem = "incorrect checksum";
      break;
    case PKT_ERR_TYPE:
      problem = "unknown packet type";
      break;
    case PKT_ERR_PARAMETER:
      problem = "invalid parameter value";
      break;
    case PKT_ERR_SIZE:
      problem = "write size too large";
      break;
    case PKT_ERR_POSITION:
      problem = "write start too large";
      break;
    case PKT_ERR_OVERRUN:
      problem = "message FIFO overflow";
      break;
    case PKT_ERR_POWER:
      problem = "insufficient USB power";
      break;
    case PKT_ERR_SPI:
      problem = "SPI bus timeout";
      break;
  }

  switch (packet->header.arg2) {
    default:
      component = "unknown component";
      break;
    case PKT_EXT_HVADJ:
      component = "VariBraille packet";
      break;
    case PKT_EXT_BEEP:
      component = "beep packet";
      break;
    case PKT_EXT_CLEAR:
      component = "ClearMsgBuf function";
      break;
    case PKT_EXT_LOOP:
      component = "timing loop of ParseCommands function";
      break;
    case PKT_EXT_TYPE:
      component = "ParseCommands function";
      break;
    case PKT_EXT_CMDWRITE:
      component = "CmdWrite function";
      break;
    case PKT_EXT_UPDATE:
      component = "update packet";
      break;
    case PKT_EXT_DIAG:
      component = "diag packet";
      break;
    case PKT_EXT_QUERY:
      component = "query packet";
      break;
    case PKT_EXT_WRITE:
      component = "write packet";
      break;
  }

  logMessage(LOG_WARNING, "Negative Acknowledgement: [%02X] %s in [%02X] %s",
             packet->header.arg1, problem,
             packet->header.arg2, component);
}

static void
handleConfigAcknowledgement (BrailleDisplay *brl, int ok) {
  brl->data->configFlags = 0;
}

static void
handleFirmnessAcknowledgement (BrailleDisplay *brl, int ok) {
  brl->data->firmnessSetting = -1;
}

static void
handleWriteAcknowledgement (BrailleDisplay *brl, int ok) {
  if (!ok) {
    if ((brl->data->writeFirst == -1) ||
        (brl->data->writingFirst < brl->data->writeFirst))
      brl->data->writeFirst = brl->data->writingFirst;

    if ((brl->data->writeLast == -1) ||
        (brl->data->writingLast > brl->data->writeLast))
      brl->data->writeLast = brl->data->writingLast;
  }
}

static int handleAcknowledgement (BrailleDisplay *brl, int ok);

ASYNC_ALARM_CALLBACK(handleMissingAcknowledgementAlarm) {
  BrailleDisplay *brl = parameters->data;

  asyncDiscardHandle(brl->data->missingAcknowledgementAlarm);
  brl->data->missingAcknowledgementAlarm = NULL;

  logMessage(LOG_WARNING, "missing ACK: assuming NAK");
  handleAcknowledgement(brl, 0);
}

static int
setMissingAcknowledgementAlarm (BrailleDisplay *brl, int timeout) {
  if (!brl->data->missingAcknowledgementAlarm) {
    if (!asyncNewRelativeAlarm(&brl->data->missingAcknowledgementAlarm, timeout,
                         handleMissingAcknowledgementAlarm, brl)) {
      return 0;
    }
  }

  return 1;
}

static void
cancelMissingAcknowledgementAlarm (BrailleDisplay *brl) {
  if (brl->data->missingAcknowledgementAlarm) {
    asyncCancelRequest(brl->data->missingAcknowledgementAlarm);
    brl->data->missingAcknowledgementAlarm = NULL;
  }
}

static void
setAcknowledgementHandler (BrailleDisplay *brl, AcknowledgementHandler handler) {
  brl->data->acknowledgementHandler = handler;
  setMissingAcknowledgementAlarm(brl, 500);
}

static int
writeRequest (BrailleDisplay *brl) {
  if (brl->data->acknowledgementHandler) return 1;

  if (brl->data->configFlags) {
    if (!writePacket(brl, PKT_CONFIG, brl->data->configFlags, 0, 0, NULL)) {
      return 0;
    }

    setAcknowledgementHandler(brl, handleConfigAcknowledgement);
    return 1;
  }

  if (brl->data->firmnessSetting >= 0) {
    if (!writePacket(brl, PKT_HVADJ, brl->data->firmnessSetting, 0, 0, NULL)) {
      return 0;
    }

    setAcknowledgementHandler(brl, handleFirmnessAcknowledgement);
    return 1;
  }

  if (brl->data->writeLast != -1) {
    unsigned int count = brl->data->writeLast + 1 - brl->data->writeFirst;
    unsigned char buffer[count];
    int truncate = count > brl->data->outputPayloadLimit;

    if (truncate) count = brl->data->outputPayloadLimit;
    translateOutputCells(buffer, &brl->data->outputBuffer[brl->data->writeFirst], count);
    if (!writePacket(brl, PKT_WRITE, count, brl->data->writeFirst, 0, buffer)) {
      return 0;
    }

    setAcknowledgementHandler(brl, handleWriteAcknowledgement);
    brl->data->writingFirst = brl->data->writeFirst;

    if (truncate) {
      brl->data->writingLast = (brl->data->writeFirst += count) - 1;
    } else {
      brl->data->writingLast = brl->data->writeLast;
      brl->data->writeFirst = -1;
      brl->data->writeLast = -1;
    }

    return 1;
  }

  return 1;
}

static int
handleAcknowledgement (BrailleDisplay *brl, int ok) {
  brl->data->acknowledgementHandler(brl, ok);
  brl->data->acknowledgementHandler = NULL;
  return writeRequest(brl);
}

static void
updateCells (
  BrailleDisplay *brl,
  const unsigned char *cells,
  unsigned char count,
  unsigned char offset
) {
  unsigned int from;
  unsigned int to;

  if (cellsHaveChanged(&brl->data->outputBuffer[offset], cells, count, &from, &to, NULL)) {
    int first = from + offset;
    int last = to + offset - 1;

    if ((brl->data->writeFirst == -1) || (first < brl->data->writeFirst))
      brl->data->writeFirst = first;

    if (last > brl->data->writeLast) brl->data->writeLast = last;
  }
}

typedef struct {
  unsigned char checksum;
} ReadPacketData;

static BraillePacketVerifierResult
verifyPacket (
  BrailleDisplay *brl,
  const unsigned char *bytes, size_t size,
  size_t *length, void *data
) {
  ReadPacketData *rpd = data;
  unsigned char byte = bytes[size-1];

  switch (size) {
    case 1:
      switch (byte) {
        case PKT_ACK:
        case PKT_NAK:
        case PKT_KEY:
        case PKT_EXTKEY:
        case PKT_BUTTON:
        case PKT_WHEEL:
        case PKT_INFO:
          *length = sizeof(PacketHeader);
          break;

        default:
          return BRL_PVR_INVALID;
      }

      rpd->checksum = 0;
      break;

    case 2:
      if (bytes[0] & 0X80) *length += byte + 1;
      break;

    default:
      break;
  }

  rpd->checksum -= byte;
  if ((size == *length) && (size > sizeof(PacketHeader)) && rpd->checksum) return BRL_PVR_INVALID;

  return BRL_PVR_INCLUDE;
}

static size_t
readPacket (BrailleDisplay *brl, Packet *packet) {
  ReadPacketData rpd;

  return readBraillePacket(brl, NULL, packet, sizeof(*packet), verifyPacket, &rpd);
}

static size_t
getPacket (BrailleDisplay *brl, Packet *packet) {
  while (1) {
    size_t count = readPacket(brl, packet);

    if (count > 0) {
      switch (packet->header.type) {
        {
          int ok;

        case PKT_NAK:
          cancelMissingAcknowledgementAlarm(brl);
          logNegativeAcknowledgement(packet);

          if (!brl->data->acknowledgementHandler) {
            logMessage(LOG_WARNING, "unexpected NAK");
            continue;
          }

          switch (packet->header.arg1) {
            case PKT_ERR_TIMEOUT: {
              int originalLimit = brl->data->outputPayloadLimit;

              if (brl->data->outputPayloadLimit > brl->data->model->cellCount)
                brl->data->outputPayloadLimit = brl->data->model->cellCount;

              if (brl->data->outputPayloadLimit > 1)
                brl->data->outputPayloadLimit -= 1;

              if (brl->data->outputPayloadLimit != originalLimit) {
                logMessage(LOG_WARNING, "maximum payload length reduced from %d to %d",
                           originalLimit, brl->data->outputPayloadLimit);
              }

              break;
            }
          }

          ok = 0;
          goto doAcknowledgement;

        case PKT_ACK:
          cancelMissingAcknowledgementAlarm(brl);

          if (!brl->data->acknowledgementHandler) {
            logMessage(LOG_WARNING, "unexpected ACK");
            continue;
          }

          ok = 1;
          goto doAcknowledgement;

        doAcknowledgement:
          if (handleAcknowledgement(brl, ok)) continue;
          count = 0;
          break;
        }

        default:
          break;
      }
    }

    return count;
  }
}

static int
setFirmness (BrailleDisplay *brl, BrailleFirmness setting) {
  brl->data->firmnessSetting = setting * 0XFF / BRL_FIRMNESS_MAXIMUM;
  return writeRequest(brl);
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .baud = 57600
  };

  BEGIN_USB_CHANNEL_DEFINITIONS
    { /* Focus 1 */
      .vendor=0X0F4E, .product=0X0100,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=2, .outputEndpoint=1
    },

    { /* PAC Mate */
      .vendor=0X0F4E, .product=0X0111,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=2, .outputEndpoint=1
    },

    { /* Focus 2 */
      .vendor=0X0F4E, .product=0X0112,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=2, .outputEndpoint=1
    },

    { /* Focus Blue */
      .vendor=0X0F4E, .product=0X0114,
      .configuration=1, .interface=0, .alternative=0,
      .inputEndpoint=2, .outputEndpoint=1,
      .disableEndpointReset = 1
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
setModel (BrailleDisplay *brl, const char *modelName, const char *firmware) {
  brl->data->model = modelTable;
  while (brl->data->model->identifier) {
    if (strcmp(brl->data->model->identifier, modelName) == 0) break;
    brl->data->model += 1;
  }

  if (!brl->data->model->identifier) {
    logMessage(LOG_WARNING, "Detected unknown model: %s", modelName);

    brl->data->model = &brl->data->genericModelEntry;
    memset(&brl->data->genericModelEntry, 0, sizeof(brl->data->genericModelEntry));

    brl->data->genericModelEntry.identifier = "Generic";
    brl->data->genericModelEntry.cellCount = 20;
    brl->data->genericModelEntry.dotsTable = &dotsTable_ISO11548_1;
    brl->data->genericModelEntry.type = MOD_TYPE_PacMate;

    {
      typedef struct {
	const char *identifier;
	const DotsTable *dotsTable;
      } ExceptionEntry;

      static const ExceptionEntry exceptionTable[] = {
	{"Focus", &dotsTable_Focus1},
	{NULL   , NULL         }
      };
      const ExceptionEntry *exception = exceptionTable;

      while (exception->identifier) {
	if (strncmp(exception->identifier, modelName, strlen(exception->identifier)) == 0) {
	  brl->data->genericModelEntry.dotsTable = exception->dotsTable;
	  break;
	}

	exception += 1;
      }
    }

    {
      const char *word = strrchr(modelName, ' ');

      if (word) {
	unsigned int size;

	if (isUnsignedInteger(&size, ++word)) {
          if (size <= ARRAY_COUNT(brl->data->outputBuffer)) {
            brl->data->genericModelEntry.cellCount = size;

            snprintf(brl->data->genericModelIdentifier, sizeof(brl->data->genericModelIdentifier),
                     "%s %d",
                     brl->data->genericModelEntry.identifier,
                     brl->data->genericModelEntry.cellCount);

            brl->data->genericModelEntry.identifier = brl->data->genericModelIdentifier;
          }
	}
      }
    }
  }

  if (brl->data->model) {
    brl->data->keyTableDefinition = modelTypeTable[brl->data->model->type].keyTableDefinition;
    makeOutputTable(brl->data->model->dotsTable[0]);

    memset(brl->data->outputBuffer, 0, brl->data->model->cellCount);
    brl->data->writeFirst = 0;
    brl->data->writeLast = brl->data->model->cellCount - 1;

    brl->data->acknowledgementHandler = NULL;
    brl->data->missingAcknowledgementAlarm = NULL;
    brl->data->configFlags = 0;
    brl->data->firmnessSetting = -1;

    if (brl->data->model->type == MOD_TYPE_Focus) {
      unsigned char firmwareVersion = firmware[0] - '0';

      if (firmwareVersion >= 3) {
	brl->data->configFlags |= 0X02;

	if (brl->data->model->cellCount < 80) {
	  brl->data->keyTableDefinition = &KEY_TABLE_DEFINITION(focus_small);
	} else {
	  brl->data->keyTableDefinition = &KEY_TABLE_DEFINITION(focus_large);
	}
      }
    }

    brl->data->oldKeys = 0;

    logMessage(LOG_INFO, "Detected %s: cells=%d, firmware=%s",
	       brl->data->model->identifier,
	       brl->data->model->cellCount,
	       firmware);

    return 1;
  }

  return 0;
}

static int
writeIdentityRequest (BrailleDisplay *brl) {
  brl->data->queryAcknowledged = 0;
  brl->data->model = NULL;
  return writePacket(brl, PKT_QUERY, 0, 0, 0, NULL);
}

static size_t
readResponse (BrailleDisplay *brl, void *packet, size_t size) {
  return readPacket(brl, packet);
}

static BrailleResponseResult
isIdentityResponse (BrailleDisplay *brl, const void *packet, size_t size) {
  const Packet *response = packet;

  switch (response->header.type) {
    case PKT_INFO:
      if (!setModel(brl, response->payload.info.model, response->payload.info.firmware)) return BRL_RSP_FAIL;
      break;

    case PKT_ACK:
      brl->data->queryAcknowledged = 1;
      break;

    case PKT_NAK:
      logNegativeAcknowledgement(response);
      brl->data->queryAcknowledged = 0;
      brl->data->model = NULL;
      return BRL_RSP_CONTINUE;

    default:
      return BRL_RSP_UNEXPECTED;
  }

  return (brl->data->queryAcknowledged && brl->data->model)? BRL_RSP_DONE: BRL_RSP_CONTINUE;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));
    brl->data->outputPayloadLimit = 0XFF;

    if (connectResource(brl, device)) {
      Packet response;

      if (probeBrailleDisplay(brl, 2, NULL, 100,
                              writeIdentityRequest,
                              readResponse, &response, sizeof(response),
                              isIdentityResponse)) {
        logMessage(LOG_DEBUG, "Manufacturer: %s", response.payload.info.manufacturer);
        logMessage(LOG_DEBUG, "Model: %s", response.payload.info.model);
        logMessage(LOG_DEBUG, "Firmware: %s", response.payload.info.firmware);

        brl->textColumns = brl->data->model->cellCount;
        brl->textRows = 1;

        setBrailleKeyTable(brl, brl->data->keyTableDefinition);
        brl->setFirmness = setFirmness;

        return writeRequest(brl);
      }

      disconnectBrailleResource(brl, NULL);
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
  cancelMissingAcknowledgementAlarm(brl);
  disconnectBrailleResource(brl, NULL);

  if (brl->data) {
    free(brl->data);
    brl->data = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  updateCells(brl, brl->buffer, brl->data->model->cellCount, 0);
  return writeRequest(brl);
}

static void
updateKeys (BrailleDisplay *brl, uint64_t newKeys, KeyNumber keyBase, unsigned char keyCount) {
  const KeyGroup group = FS_GRP_NavigationKeys;
  KeyNumber number = keyBase;

  KeyNumber pressKeys[keyCount];
  unsigned int pressCount = 0;

  uint64_t keyBit = UINT64_C(0X1) << keyBase;
  newKeys <<= keyBase;
  newKeys |= brl->data->oldKeys & ~(((UINT64_C(0X1) << keyCount) - 1) << keyBase);

  while (brl->data->oldKeys != newKeys) {
    uint64_t oldKey = brl->data->oldKeys & keyBit;
    uint64_t newKey = newKeys & keyBit;

    if (oldKey && !newKey) {
      enqueueKeyEvent(brl, group, number, 0);
      brl->data->oldKeys &= ~keyBit;
    } else if (newKey && !oldKey) {
      pressKeys[pressCount++] = number;
      brl->data->oldKeys |= keyBit;
    }

    keyBit <<= 1;
    number += 1;
  }

  while (pressCount) enqueueKeyEvent(brl, group, pressKeys[--pressCount], 1);
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  Packet packet;
  size_t count;

  while ((count = getPacket(brl, &packet))) {
    switch (packet.header.type) {
      case PKT_KEY: {
        uint64_t newKeys = packet.header.arg1 |
                           (packet.header.arg2 << 8) |
                           (packet.header.arg3 << 16);

        updateKeys(brl, newKeys, 0, 24);
        continue;
      }

      case PKT_EXTKEY: {
        uint64_t newKeys = packet.payload.extkey.bytes[0];

        updateKeys(brl, newKeys, 24, 8);
        continue;
      }

      case PKT_BUTTON: {
        KeyNumber number = packet.header.arg1;
        unsigned char press = (packet.header.arg2 & 0X01) != 0;
        KeyGroup group = packet.header.arg3;

        if (group == modelTypeTable[brl->data->model->type].hotkeysRow) {
          static const KeyNumber keys[] = {
            FS_KEY_LeftGdf,
            FS_KEY_HOT+0, FS_KEY_HOT+1, FS_KEY_HOT+2, FS_KEY_HOT+3,
            FS_KEY_HOT+4, FS_KEY_HOT+5, FS_KEY_HOT+6, FS_KEY_HOT+7,
            FS_KEY_RightGdf
          };

          static const unsigned char keyCount = ARRAY_COUNT(keys);
          const unsigned char base = (brl->data->model->cellCount - keyCount) / 2;

          if (number < base) {
            number = FS_KEY_LeftAdvance;
          } else if ((number -= base) >= keyCount) {
            number = FS_KEY_RightAdvance;
          } else {
            number = keys[number];
          }

          group = FS_GRP_NavigationKeys;
        } else {
          group += 1;
        }

        enqueueKeyEvent(brl, group, number, press);
        continue;
      }

      case PKT_WHEEL: {
        const KeyGroup group = FS_GRP_NavigationKeys;
        const KeyNumber number = FS_KEY_WHEEL + ((packet.header.arg1 >> 3) & 0X7);
        unsigned int count = packet.header.arg1 & 0X7;

        while (count) {
          enqueueKey(brl, group, number);
          count -= 1;
        }

        continue;
      }

      default:
        break;
    }

    logUnexpectedPacket(&packet, count);
  }

  return (errno == EAGAIN)? EOF: BRL_CMD_RESTARTBRL;
}

static ssize_t
brl_readPacket (BrailleDisplay *brl, void *buffer, size_t length) {
  Packet packet;
  size_t count = readPacket(brl, &packet);

  if (count == 0) return (errno == EAGAIN)? 0: -1;
  if (count > sizeof(packet.header)) count -= 1;

  if (length < count) {
    logMessage(LOG_WARNING,
               "Input packet buffer too small:"
               " %"PRIsize
               " < %"PRIsize,
               length, count);
    count = length;
  }

  memcpy(buffer, &packet, count);
  return count;
}

static ssize_t
brl_writePacket (BrailleDisplay *brl, const void *packet, size_t length) {
  const unsigned char *bytes = packet;
  size_t size = 4;

  if (length >= size) {
    int hasPayload = 0;

    if (bytes[0] & 0X80) {
      size += bytes[1];
      hasPayload = 1;
    }

    if (length >= size) {
      if (length > size) {
        logMessage(LOG_WARNING,
                   "output packet buffer larger than necessary:"
                   " %"PRIsize
                   " > %"PRIsize,
                   length, size);
      }

      return writePacket(brl, bytes[0], bytes[1], bytes[2], bytes[3],
                         (hasPayload? &bytes[4]: NULL))?
             size: -1;
    }
  }

  logMessage(LOG_WARNING,
             "output packet buffer too small:"
             " %"PRIsize
             " < %"PRIsize,
             length, size);

  errno = EIO;
  return -1;
}

static int
brl_reset (BrailleDisplay *brl) {
  return 0;
}
