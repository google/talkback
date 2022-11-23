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
#include "report.h"
#include "queue.h"
#include "async_alarm.h"
#include "brl_base.h"
#include "brl_utils.h"
#include "brl_dots.h"
#include "prefs.h"
#include "kbd_keycodes.h"
#include "io_generic.h"
#include "cmd_queue.h"
#include "ktb.h"

const DotsTable dotsTable_ISO11548_1 = {
  BRL_DOT_1, BRL_DOT_2, BRL_DOT_3, BRL_DOT_4,
  BRL_DOT_5, BRL_DOT_6, BRL_DOT_7, BRL_DOT_8
};

const DotsTable dotsTable_rotated = {
  BRL_DOT_8, BRL_DOT_6, BRL_DOT_5, BRL_DOT_7,
  BRL_DOT_3, BRL_DOT_2, BRL_DOT_4, BRL_DOT_1
};

void
makeTranslationTable (const DotsTable dots, TranslationTable table) {
  int byte;

  for (byte=0; byte<TRANSLATION_TABLE_SIZE; byte+=1) {
    unsigned char cell = 0;
    int dot;

    for (dot=0; dot<DOTS_TABLE_SIZE; dot+=1) {
      if (byte & dotsTable_ISO11548_1[dot]) {
        cell |= dots[dot];
      }
    }

    table[byte] = cell;
  }
}

void
reverseTranslationTable (const TranslationTable from, TranslationTable to) {
  int byte;
  memset(to, 0, sizeof(TranslationTable));
  for (byte=TRANSLATION_TABLE_SIZE-1; byte>=0; byte--) to[from[byte]] = byte;
}

static inline void *
translateCells (
  const TranslationTable table,
  unsigned char *target, const unsigned char *source, size_t count
) {
  if (table) {
    while (count--) *target++ = table[*source++];
    return target;
  }

  if (target == source) return target + count;
  return mempcpy(target, source, count);
}

static inline unsigned char
translateCell (const TranslationTable table, unsigned char cell) {
  return table? table[cell]: cell;
}

static TranslationTable internalOutputTable;
static const unsigned char *outputTable;

void
setOutputTable (const TranslationTable table) {
  outputTable = table;
}

void
makeOutputTable (const DotsTable dots) {
  if (memcmp(dots, dotsTable_ISO11548_1, DOTS_TABLE_SIZE) == 0) {
    outputTable = NULL;
  } else {
    makeTranslationTable(dots, internalOutputTable);
    outputTable = internalOutputTable;
  }
}

void *
translateOutputCells (unsigned char *target, const unsigned char *source, size_t count) {
  return translateCells(outputTable, target, source, count);
}

unsigned char
translateOutputCell (unsigned char cell) {
  return translateCell(outputTable, cell);
}

static TranslationTable internalInputTable;
static const unsigned char *inputTable;

void
makeInputTable (void) {
  if (outputTable) {
    reverseTranslationTable(outputTable, internalInputTable);
    inputTable = internalInputTable;
  } else {
    inputTable = NULL;
  }
}

void *
translateInputCells (unsigned char *target, const unsigned char *source, size_t count) {
  return translateCells(inputTable, target, source, count);
}

unsigned char
translateInputCell (unsigned char cell) {
  return translateCell(inputTable, cell);
}

void
applyBrailleDisplayOrientation (unsigned char *cells, size_t count) {
  switch (prefs.brailleDisplayOrientation) {
    case BRL_ORIENTATION_ROTATED: {
      static TranslationTable rotateTable = {[1] = 0};

      const unsigned char *source = cells;
      const unsigned char *end = source + count;

      unsigned char buffer[count];
      unsigned char *target = &buffer[count];

      if (!rotateTable[1]) {
        makeTranslationTable(dotsTable_rotated, rotateTable);
      }

      while (source < end) *--target = rotateTable[*source++];
      memcpy(cells, buffer, count);
      break;
    }

    default:
    case BRL_ORIENTATION_NORMAL:
      break;
  }
}

int
awaitBrailleInput (BrailleDisplay *brl, int timeout) {
  return gioAwaitInput(brl->gioEndpoint, timeout);
}

int
connectBrailleResource (
  BrailleDisplay *brl,
  const char *identifier,
  const GioDescriptor *descriptor,
  BrailleSessionInitializer *initializeSession
) {
  if ((brl->gioEndpoint = gioConnectResource(identifier, descriptor))) {
    if (!initializeSession || initializeSession(brl)) {
      if (gioDiscardInput(brl->gioEndpoint)) {
        return 1;
      }
    }

    gioDisconnectResource(brl->gioEndpoint);
    brl->gioEndpoint = NULL;
  }

  return 0;
}

void
disconnectBrailleResource (
  BrailleDisplay *brl,
  BrailleSessionEnder *endSession
) {
  if (brl->gioEndpoint) {
    if (endSession) endSession(brl);
    drainBrailleOutput(brl, 0);
    gioDisconnectResource(brl->gioEndpoint);
    brl->gioEndpoint = NULL;
  }
}

size_t
readBraillePacket (
  BrailleDisplay *brl,
  GioEndpoint *endpoint,
  void *packet, size_t size,
  BraillePacketVerifier *verifyPacket, void *data
) {
  unsigned char *bytes = packet;
  size_t count = 0;
  size_t length = 1;
  int started = 0;

  if (!endpoint) endpoint = brl->gioEndpoint;

  while (1) {
    unsigned char byte;

    if (!gioReadByte(endpoint, &byte, started)) {
      if (count > 0) logPartialPacket(bytes, count);
      return 0;
    }

  gotByte:
    started = 1;

    if (count < size) {
      bytes[count++] = byte;

      {
        BraillePacketVerifierResult result = verifyPacket(brl, bytes, count, &length, data);

        switch (result) {
          case BRL_PVR_EXCLUDE:
            count -= 1;
          case BRL_PVR_INCLUDE:
            break;

          default:
            logMessage(LOG_WARNING, "unimplemented braille packet verifier result: %u", result);
            /* fall through */
          case BRL_PVR_INVALID:
            started = 0;

            if (--count) {
              logShortPacket(bytes, count);
              count = 0;
              length = 1;
              goto gotByte;
            }

            logIgnoredByte(byte);
            continue;
        }
      }

      if (count >= length) {
        logInputPacket(bytes, length);
        return length;
      }
    } else {
      if (count++ == size) logTruncatedPacket(bytes, size);
      logDiscardedByte(byte);
    }
  }
}

int
writeBraillePacket (
  BrailleDisplay *brl, GioEndpoint *endpoint,
  const void *packet, size_t size
) {
  if (!endpoint) endpoint = brl->gioEndpoint;
  logOutputPacket(packet, size);
  if (gioWriteData(endpoint, packet, size) == -1) return 0;

  if (endpoint == brl->gioEndpoint) {
    brl->writeDelay += gioGetMillisecondsToTransfer(endpoint, size);
  }

  return 1;
}

typedef struct {
  GioEndpoint *endpoint;
  int type;
  size_t size;
  unsigned char packet[0];
} BrailleMessage;

static void
logBrailleMessage (BrailleMessage *msg, const char *action) {
  logBytes(LOG_CATEGORY(OUTPUT_PACKETS), "%s", msg->packet, msg->size, action);
}

static void
deallocateBrailleMessage (BrailleMessage *msg) {
  free(msg);
}

static void setBrailleMessageAlarm (BrailleDisplay *brl);

static int
writeNextBrailleMessage (BrailleDisplay *brl) {
  int ok = 1;

  if (brl->acknowledgements.messages) {
    BrailleMessage *msg = dequeueItem(brl->acknowledgements.messages);

    if (msg) {
      int written;

      logBrailleMessage(msg, "dequeued");
      written = writeBraillePacket(brl, msg->endpoint, msg->packet, msg->size);

      deallocateBrailleMessage(msg);
      msg = NULL;

      if (written) {
        setBrailleMessageAlarm(brl);
        return 1;
      }

      ok = 0;
    }
  }

  if (brl->acknowledgements.alarm) {
    asyncCancelRequest(brl->acknowledgements.alarm);
    brl->acknowledgements.alarm = NULL;
  }

  return ok;
}

int
acknowledgeBrailleMessage (BrailleDisplay *brl) {
  logMessage(LOG_CATEGORY(OUTPUT_PACKETS), "acknowledged");
  brl->acknowledgements.missing.count = 0;
  return writeNextBrailleMessage(brl);
}

ASYNC_ALARM_CALLBACK(handleBrailleMessageTimeout) {
  BrailleDisplay *brl = parameters->data;

  asyncDiscardHandle(brl->acknowledgements.alarm);
  brl->acknowledgements.alarm = NULL;

  if ((brl->acknowledgements.missing.count += 1) < brl->acknowledgements.missing.limit) {
    logMessage(LOG_WARNING, "missing braille message acknowledgement");
    writeNextBrailleMessage(brl);
  } else {
    logMessage(LOG_WARNING, "too many missing braille message acknowledgements");
    brl->hasFailed = 1;
  }
}

static void
setBrailleMessageAlarm (BrailleDisplay *brl) {
  if (brl->acknowledgements.alarm) {
    asyncResetAlarmIn(brl->acknowledgements.alarm, brl->acknowledgements.missing.timeout);
  } else {
    asyncNewRelativeAlarm(&brl->acknowledgements.alarm, brl->acknowledgements.missing.timeout,
                          handleBrailleMessageTimeout, brl);
  }
}

static int
findOldBrailleMessage (const void *item, void *data) {
  const BrailleMessage *old = item;
  const BrailleMessage *new = data;

  return old->type == new->type;
}

static void
deallocateBrailleMessageItem (void *item, void *data) {
  BrailleMessage *msg = item;

  deallocateBrailleMessage(msg);
}

int
writeBrailleMessage (
  BrailleDisplay *brl, GioEndpoint *endpoint,
  int type,
  const void *packet, size_t size
) {
  if (brl->acknowledgements.alarm) {
    BrailleMessage *msg;

    if (!brl->acknowledgements.messages) {
      if (!(brl->acknowledgements.messages = newQueue(deallocateBrailleMessageItem, NULL))) {
        return 0;
      }
    }

    if ((msg = malloc(sizeof(*msg) + size))) {
      memset(msg, 0, sizeof(*msg));
      msg->endpoint = endpoint;
      msg->type = type;
      msg->size = size;
      memcpy(msg->packet, packet, size);

      {
        Element *element = findElement(brl->acknowledgements.messages, findOldBrailleMessage, msg);

        if (element) {
          logBrailleMessage(getElementItem(element), "unqueued");
          deleteElement(element);
        }
      }

      if (enqueueItem(brl->acknowledgements.messages, msg)) {
        logBrailleMessage(msg, "enqueued");
        return 1;
      }

      logBrailleMessage(msg, "discarded");
      free(msg);
    } else {
      logMallocError();
    }
  } else if (writeBraillePacket(brl, endpoint, packet, size)) {
    setBrailleMessageAlarm(brl);
    return 1;
  }

  return 0;
}

int
probeBrailleDisplay (
  BrailleDisplay *brl, unsigned int retryLimit,
  GioEndpoint *endpoint, int inputTimeout,
  BrailleRequestWriter *writeRequest,
  BraillePacketReader *readPacket, void *responsePacket, size_t responseSize,
  BrailleResponseHandler *handleResponse
) {
  unsigned int retryCount = 0;

  if (!endpoint) endpoint = brl->gioEndpoint;

  while (writeRequest(brl)) {
    drainBrailleOutput(brl, 0);

    while (gioAwaitInput(endpoint, inputTimeout)) {
      size_t size = readPacket(brl, responsePacket, responseSize);
      if (!size) break;

      {
        BrailleResponseResult result = handleResponse(brl, responsePacket, size);

        switch (result) {
          case BRL_RSP_DONE:
            return 1;

          case BRL_RSP_UNEXPECTED:
            logUnexpectedPacket(responsePacket, size);
          case BRL_RSP_CONTINUE:
            break;

          default:
            logMessage(LOG_WARNING, "unimplemented braille response result: %u", result);
          case BRL_RSP_FAIL:
            return 0;
        }
      }
    }

    if (errno != EAGAIN)
#ifdef ETIMEDOUT
      if (errno != ETIMEDOUT)
#endif /* ETIMEDOUT */
        break;

    if (retryCount == retryLimit) break;
    retryCount += 1;
  }

  return 0;
}

void
releaseBrailleKeys (BrailleDisplay *brl) {
  releaseAllKeys(brl->keyTable);
}

typedef struct {
  const KeyGroup group;

  KeyNumberSet set;
} IncludeKeyNumberData;

static int
includeKeyNumber (const KeyNameEntry *kne, void *data) {
  if (kne) {
    IncludeKeyNumberData *ikn = data;

    if (kne->value.group == ikn->group) ikn->set |= KEY_NUMBER_BIT(kne->value.number);
  }

  return 1;
}

KeyNumberSet
makeKeyNumberSet (KEY_NAME_TABLES_REFERENCE keys, KeyGroup group) {
  IncludeKeyNumberData ikn = {
    .group = group,

    .set = 0
  };

  forEachKeyName(keys, includeKeyNumber, &ikn);
  return ikn.set;
}

int
enqueueKeyEvent (
  BrailleDisplay *brl,
  KeyGroup group, KeyNumber number, int press
) {
  report(REPORT_BRAILLE_KEY_EVENT, NULL);

  if (brl->api) {
    if (brl->api->handleKeyEvent(group, number, press)) {
      return 1;
    }
  }

  if (brl->keyTable) {
    switch (prefs.brailleDisplayOrientation) {
      case BRL_ORIENTATION_ROTATED:
        if (brl->rotateInput) brl->rotateInput(brl, &group, &number);
        break;

      default:
      case BRL_ORIENTATION_NORMAL:
        break;
    }

    processKeyEvent(brl->keyTable, getCurrentCommandContext(), group, number, press);
    return 1;
  }

  return 0;
}

int
enqueueKeyEvents (
  BrailleDisplay *brl,
  KeyNumberSet set, KeyGroup group, KeyNumber number, int press
) {
  while (set) {
    if (set & 0X1) {
      if (!enqueueKeyEvent(brl, group, number, press)) {
        return 0;
      }
    }

    set >>= 1;
    number += 1;
  }

  return 1;
}

int
enqueueKey (
  BrailleDisplay *brl,
  KeyGroup group, KeyNumber number
) {
  if (enqueueKeyEvent(brl, group, number, 1)) {
    if (enqueueKeyEvent(brl, group, number, 0)) {
      return 1;
    }
  }

  return 0;
}

int
enqueueKeys (
  BrailleDisplay *brl,
  KeyNumberSet set, KeyGroup group, KeyNumber number
) {
  KeyNumber stack[UINT8_MAX + 1];
  unsigned char count = 0;

  while (set) {
    if (set & 0X1) {
      if (!enqueueKeyEvent(brl, group, number, 1)) return 0;
      stack[count++] = number;
    }

    set >>= 1;
    number += 1;
  }

  while (count) {
    if (!enqueueKeyEvent(brl, group, stack[--count], 0)) {
      return 0;
    }
  }

  return 1;
}

int
enqueueUpdatedKeys (
  BrailleDisplay *brl,
  KeyNumberSet new, KeyNumberSet *old, KeyGroup group, KeyNumber number
) {
  KeyNumberSet bit = KEY_NUMBER_BIT(0);
  KeyNumber stack[UINT8_MAX + 1];
  unsigned char count = 0;

  while (*old != new) {
    if ((new & bit) && !(*old & bit)) {
      stack[count++] = number;
      *old |= bit;
    } else if (!(new & bit) && (*old & bit)) {
      if (!enqueueKeyEvent(brl, group, number, 0)) return 0;
      *old &= ~bit;
    }

    number += 1;
    bit <<= 1;
  }

  while (count) {
    if (!enqueueKeyEvent(brl, group, stack[--count], 1)) {
      return 0;
    }
  }

  return 1;
}

int
enqueueUpdatedKeyGroup (
  BrailleDisplay *brl,
  unsigned int count,
  const unsigned char *new,
  unsigned char *old,
  KeyGroup group
) {
  KeyNumber pressStack[count];
  unsigned char pressCount = 0;
  KeyNumber base = 0;

  while (base < count) {
    KeyNumber number = base;
    unsigned char bit = 1;

    while (*old != *new) {
      unsigned char isPressed = *new & bit;
      unsigned char wasPressed = *old & bit;

      if (isPressed && !wasPressed) {
        *old |= bit;
        pressStack[pressCount++] = number;
      } else if (wasPressed && !isPressed) {
        *old &= ~bit;
        enqueueKeyEvent(brl, group, number, 0);
      }

      if (++number == count) break;
      bit <<= 1;
    }

    old += 1;
    new += 1;
    base += 8;
  }

  while (pressCount > 0) {
    enqueueKeyEvent(brl, group, pressStack[--pressCount], 1);
  }

  return 1;
}

int
enqueueXtScanCode (
  BrailleDisplay *brl,
  unsigned char key, unsigned char escape,
  KeyGroup group00, KeyGroup groupE0, KeyGroup groupE1
) {
  KeyGroup group;

  switch (escape) {
    case XT_MOD_00: group = group00; break;
    case XT_MOD_E0: group = groupE0; break;
    case XT_MOD_E1: group = groupE1; break;

    default:
      logMessage(LOG_WARNING, "unsupported XT scan code: %02X %02X", escape, key);
      return 0;
  }

  return enqueueKey(brl, group, key);
}
