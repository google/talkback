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

#include "log.h"
#include "report.h"
#include "api_control.h"
#include "brl_utils.h"
#include "brl_dots.h"
#include "async_wait.h"
#include "ktb.h"

void
drainBrailleOutput (BrailleDisplay *brl, int minimumDelay) {
  int duration = brl->writeDelay + 1;

  brl->writeDelay = 0;
  if (duration < minimumDelay) duration = minimumDelay;
  asyncWait(duration);
}

void
announceBrailleOffline (void) {
  logMessage(LOG_DEBUG, "braille is offline");
  api.updateParameter(BRLAPI_PARAM_DEVICE_ONLINE, 0);
  report(REPORT_BRAILLE_DEVICE_OFFLINE, NULL);
}

void
announceBrailleOnline (void) {
  logMessage(LOG_DEBUG, "braille is online");
  api.updateParameter(BRLAPI_PARAM_DEVICE_ONLINE, 0);
  report(REPORT_BRAILLE_DEVICE_ONLINE, NULL);
}

void
setBrailleOffline (BrailleDisplay *brl) {
  if (!brl->isOffline) {
    brl->isOffline = 1;
    announceBrailleOffline();

    {
      KeyTable *keyTable = brl->keyTable;

      if (keyTable) releaseAllKeys(keyTable);
    }
  }
}

void
setBrailleOnline (BrailleDisplay *brl) {
  if (brl->isOffline) {
    brl->isOffline = 0;
    announceBrailleOnline();

    brl->writeDelay = 0;
  }
}

int
cellsHaveChanged (
  unsigned char *cells, const unsigned char *new, unsigned int count,
  unsigned int *from, unsigned int *to, unsigned char *force
) {
  unsigned int first = 0;

  if (force && *force) {
    *force = 0;
  } else if (memcmp(cells, new, count) != 0) {
    if (to) {
      while (count) {
        unsigned int last = count - 1;
        if (cells[last] != new[last]) break;
        count = last;
      }
    }

    if (from) {
      while (first < count) {
        if (cells[first] != new[first]) break;
        first += 1;
      }
    }
  } else {
    return 0;
  }

  if (from) *from = first;
  if (to) *to = count;

  memcpy(cells+first, new+first, count-first);
  return 1;
}

int
textHasChanged (
  wchar_t *text, const wchar_t *new, unsigned int count,
  unsigned int *from, unsigned int *to, unsigned char *force
) {
  unsigned int first = 0;

  if (force && *force) {
    *force = 0;
  } else if (wmemcmp(text, new, count) != 0) {
    if (to) {
      while (count) {
        unsigned int last = count - 1;
        if (text[last] != new[last]) break;
        count = last;
      }
    }

    if (from) {
      while (first < count) {
        if (text[first] != new[first]) break;
        first += 1;
      }
    }
  } else {
    return 0;
  }

  if (from) *from = first;
  if (to) *to = count;

  wmemcpy(text+first, new+first, count-first);
  return 1;
}

int
cursorHasChanged (int *cursor, int new, unsigned char *force) {
  if (force && *force) {
    *force = 0;
  } else if (new == *cursor) {
    return 0;
  }

  *cursor = new;
  return 1;
}

unsigned char
toLowerDigit (unsigned char upper) {
  unsigned char lower = 0;
  if (upper & BRL_DOT_1) lower |= BRL_DOT_3;
  if (upper & BRL_DOT_2) lower |= BRL_DOT_7;
  if (upper & BRL_DOT_4) lower |= BRL_DOT_6;
  if (upper & BRL_DOT_5) lower |= BRL_DOT_8;
  return lower;
}

/* Dots for landscape (counterclockwise-rotated) digits. */
const DigitsTable landscapeDigits = {
  [ 0] = BRL_DOT_1 | BRL_DOT_5 | BRL_DOT_2,
  [ 1] = BRL_DOT_4,
  [ 2] = BRL_DOT_4 | BRL_DOT_1,
  [ 3] = BRL_DOT_4 | BRL_DOT_5,
  [ 4] = BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_2,
  [ 5] = BRL_DOT_4 | BRL_DOT_2,
  [ 6] = BRL_DOT_4 | BRL_DOT_1 | BRL_DOT_5,
  [ 7] = BRL_DOT_4 | BRL_DOT_1 | BRL_DOT_5 | BRL_DOT_2,
  [ 8] = BRL_DOT_4 | BRL_DOT_1 | BRL_DOT_2,
  [ 9] = BRL_DOT_1 | BRL_DOT_5,
  [10] = BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5
};

/* Format landscape representation of numbers 0 through 99. */
unsigned char
makeLandscapeNumber (int x) {
  return landscapeDigits[(x / 10) % 10] | toLowerDigit(landscapeDigits[x % 10]);  
}

/* Format landscape flag state indicator. */
unsigned char
makeLandscapeFlag (int number, int on) {
  unsigned char dots = landscapeDigits[number % 10];
  if (on) dots |= toLowerDigit(landscapeDigits[10]);
  return dots;
}

/* Dots for seascape (clockwise-rotated) digits. */
const DigitsTable seascapeDigits = {
  [ 0] = BRL_DOT_5 | BRL_DOT_1 | BRL_DOT_4,
  [ 1] = BRL_DOT_2,
  [ 2] = BRL_DOT_2 | BRL_DOT_5,
  [ 3] = BRL_DOT_2 | BRL_DOT_1,
  [ 4] = BRL_DOT_2 | BRL_DOT_1 | BRL_DOT_4,
  [ 5] = BRL_DOT_2 | BRL_DOT_4,
  [ 6] = BRL_DOT_2 | BRL_DOT_5 | BRL_DOT_1,
  [ 7] = BRL_DOT_2 | BRL_DOT_5 | BRL_DOT_1 | BRL_DOT_4,
  [ 8] = BRL_DOT_2 | BRL_DOT_5 | BRL_DOT_4,
  [ 9] = BRL_DOT_5 | BRL_DOT_1,
  [10] = BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5
};

/* Format seascape representation of numbers 0 through 99. */
unsigned char
makeSeascapeNumber (int x) {
  return toLowerDigit(seascapeDigits[(x / 10) % 10]) | seascapeDigits[x % 10];  
}

/* Format seascape flag state indicator. */
unsigned char
makeSeascapeFlag (int number, int on) {
  unsigned char dots = toLowerDigit(seascapeDigits[number % 10]);
  if (on) dots |= seascapeDigits[10];
  return dots;
}

/* Dots for portrait digits - 2 numbers in one cells */
const DigitsTable portraitDigits = {
  [ 0] = BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5,
  [ 1] = BRL_DOT_1,
  [ 2] = BRL_DOT_1 | BRL_DOT_2,
  [ 3] = BRL_DOT_1 | BRL_DOT_4,
  [ 4] = BRL_DOT_1 | BRL_DOT_4 | BRL_DOT_5,
  [ 5] = BRL_DOT_1 | BRL_DOT_5,
  [ 6] = BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4,
  [ 7] = BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5,
  [ 8] = BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_5,
  [ 9] = BRL_DOT_2 | BRL_DOT_4,
  [10] = BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5
};

/* Format portrait representation of numbers 0 through 99. */
unsigned char
makePortraitNumber (int x) {
  return portraitDigits[(x / 10) % 10] | toLowerDigit(portraitDigits[x % 10]);  
}

/* Format portrait flag state indicator. */
unsigned char
makePortraitFlag (int number, int on) {
  unsigned char dots = toLowerDigit(portraitDigits[number % 10]);
  if (on) dots |= portraitDigits[10];
  return dots;
}
