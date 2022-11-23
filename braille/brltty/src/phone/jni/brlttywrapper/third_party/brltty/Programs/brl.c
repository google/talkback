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

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

#include "log.h"
#include "parameters.h"
#include "charset.h"
#include "unicode.h"
#include "brl.h"
#include "ttb.h"
#include "ktb.h"
#include "queue.h"

void
constructBrailleDisplay (BrailleDisplay *brl) {
  brl->data = NULL;

  brl->setFirmness = NULL;
  brl->setSensitivity = NULL;
  brl->setAutorepeat = NULL;

  brl->textColumns = 0;
  brl->textRows = 1;
  brl->statusColumns = 0;
  brl->statusRows = 0;
  brl->hideCursor = 0;

  brl->keyBindings = "all";
  brl->keyNames = NULL;
  brl->keyTable = NULL;

  brl->gioEndpoint = NULL;
  brl->writeDelay = 0;

  brl->buffer = NULL;
  brl->isCoreBuffer = 0;

  brl->bufferResized = NULL;
  brl->resizeRequired = 0;

  brl->cursor = BRL_NO_CURSOR;

  brl->noDisplay = 0;
  brl->hasFailed = 0;
  brl->isOffline = 0;
  brl->isSuspended = 0;

  brl->rotateInput = NULL;
  brl->api = NULL;

  brl->acknowledgements.messages = NULL;
  brl->acknowledgements.alarm = NULL;
  brl->acknowledgements.missing.timeout = BRAILLE_MESSAGE_ACKNOWLEDGEMENT_TIMEOUT;
  brl->acknowledgements.missing.count = 0;
  brl->acknowledgements.missing.limit = BRAILLE_MESSAGE_UNACKNOWLEDGEED_LIMIT;
}

void
destructBrailleDisplay (BrailleDisplay *brl) {
  if (brl->acknowledgements.alarm) {
    asyncCancelRequest(brl->acknowledgements.alarm);
    brl->acknowledgements.alarm = NULL;
  }

  if (brl->acknowledgements.messages) {
    deallocateQueue(brl->acknowledgements.messages);
    brl->acknowledgements.messages = NULL;
  }

  if (brl->keyTable) {
    destroyKeyTable(brl->keyTable);
    brl->keyTable = NULL;
  }

  if (brl->buffer) {
    if (brl->isCoreBuffer) free(brl->buffer);
    brl->buffer = NULL;
  }
}

static void
fillRegion (
  wchar_t *text, unsigned char *dots,
  unsigned int start, unsigned int count,
  unsigned int columns, unsigned int rows,
  void *data, unsigned int length,
  void (*fill) (wchar_t *text, unsigned char *dots, void *data)
) {
  text += start;
  dots += start;

  while (rows > 0) {
    unsigned int index = 0;
    size_t amount = length;
    if (amount > count) amount = count;

    while (index < amount) {
      fill(&text[index], &dots[index], data);
      index += 1;
    }
    length -= amount;

    amount = count - index;
    wmemset(&text[index], WC_C(' '), amount);
    memset(&dots[index], 0, amount);

    text += columns;
    dots += columns;
    rows -= 1;
  }
}

static void
fillText (wchar_t *text, unsigned char *dots, void *data) {
  wchar_t **character = data;
  *dots = convertCharacterToDots(textTable, (*text = *(*character)++));
}

void
fillTextRegion (
  wchar_t *text, unsigned char *dots,
  unsigned int start, unsigned int count,
  unsigned int columns, unsigned int rows,
  const wchar_t *characters, size_t length
) {
  fillRegion(text, dots, start, count, columns, rows, &characters, length, fillText);
}

static void
fillDots (wchar_t *text, unsigned char *dots, void *data) {
  unsigned char **cell = data;
  *text = UNICODE_BRAILLE_ROW | (*dots = *(*cell)++);
}

void
fillDotsRegion (
  wchar_t *text, unsigned char *dots,
  unsigned int start, unsigned int count,
  unsigned int columns, unsigned int rows,
  const unsigned char *cells, size_t length
) {
  fillRegion(text, dots, start, count, columns, rows, &cells, length, fillDots);
}


int
setStatusText (BrailleDisplay *brl, const char *text) {
  unsigned int length = brl->statusColumns * brl->statusRows;

  if (braille->writeStatus && (length > 0)) {
    unsigned char cells[length];

    {
      unsigned int index = 0;

      while (index < length) {
        char c;
        wint_t wc;

        if (!(c = text[index])) break;
        if ((wc = convertCharToWchar(c)) == WEOF) wc = WC_C('?');
        cells[index++] = convertCharacterToDots(textTable, wc);
      }

      memset(&cells[index], 0, length-index);
    }

    if (!braille->writeStatus(brl, cells)) return 0;
  }

  return 1;
}

int
clearStatusCells (BrailleDisplay *brl) {
  return setStatusText(brl, "");
}

static void
brailleBufferResized (BrailleDisplay *brl, int infoLevel) {
  logMessage(infoLevel, "Braille Display Dimensions: %d %s, %d %s",
             brl->textRows, (brl->textRows == 1)? "row": "rows",
             brl->textColumns, (brl->textColumns == 1)? "column": "columns");

  memset(brl->buffer, 0, brl->textColumns*brl->textRows);
  if (brl->bufferResized) brl->bufferResized(brl->textRows, brl->textColumns);
}

static int
resizeBrailleBuffer (BrailleDisplay *brl, int resized, int infoLevel) {
  if (brl->resizeRequired) {
    brl->resizeRequired = 0;
    resized = 1;

    if (brl->isCoreBuffer) {
      size_t newSize = brl->textColumns * brl->textRows;
      unsigned char *newAddress = malloc(newSize);

      if (!newAddress) {
        logMallocError();
        return 0;
      }

      if (brl->buffer) free(brl->buffer);
      brl->buffer = newAddress;
    }
  }

  if (resized) brailleBufferResized(brl, infoLevel);
  return 1;
}

int
ensureBrailleBuffer (BrailleDisplay *brl, int infoLevel) {
  brl->resizeRequired = brl->isCoreBuffer = !brl->buffer;
  if ((brl->noDisplay = !brl->textColumns)) brl->textColumns = 1;
  return resizeBrailleBuffer(brl, 1, infoLevel);
}

int
readBrailleCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  int command = braille->readCommand(brl, context);

  resizeBrailleBuffer(brl, 0, LOG_INFO);
  return command;
}

int
canSetBrailleFirmness (BrailleDisplay *brl) {
  return brl->setFirmness != NULL;
}

int
setBrailleFirmness (BrailleDisplay *brl, BrailleFirmness setting) {
  if (!canSetBrailleFirmness(brl)) return 0;
  logMessage(LOG_DEBUG, "setting braille firmness: %d", setting);
  return brl->setFirmness(brl, setting);
}

int
canSetTouchSensitivity (BrailleDisplay *brl) {
  return brl->setSensitivity != NULL;
}

int
setTouchSensitivity (BrailleDisplay *brl, TouchSensitivity setting) {
  if (!canSetTouchSensitivity(brl)) return 0;
  logMessage(LOG_DEBUG, "setting touch sensitivity: %d", setting);
  return brl->setSensitivity(brl, setting);
}

int
canSetBrailleAutorepeat (BrailleDisplay *brl) {
  return brl->setAutorepeat != NULL;
}

int
setBrailleAutorepeat (BrailleDisplay *brl, int on, int delay, int interval) {
  if (!canSetBrailleAutorepeat(brl)) return 0;
  logMessage(LOG_DEBUG, "setting braille autorepeat: %s Delay:%d Interval:%d", 
             (on? "on": "off"), delay, interval);
  return brl->setAutorepeat(brl, on, delay, interval);
}
