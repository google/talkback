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

#include "status.h"
#include "timing.h"
#include "update.h"
#include "brl_dots.h"
#include "brl_utils.h"
#include "scr_special.h"
#include "core.h"
#include "prefs.h"
#include "ttb.h"

static void
renderCharacter (unsigned char *cell, char character) {
  *cell = convertCharacterToDots(textTable, character);
}

static void
renderDigitUpper (unsigned char *cell, int digit) {
  *cell |= portraitDigits[digit % 10];
}

static void
renderDigitLower (unsigned char *cell, int digit) {
  *cell |= toLowerDigit(portraitDigits[digit % 10]);
}

static void
renderNumberVertical (unsigned char *cell, int number) {
  renderDigitUpper(cell, number/10);
  renderDigitLower(cell, number);
}

static void
renderNumberUpper2 (unsigned char *cells, int number) {
  renderDigitUpper(&cells[0], number/10);
  renderDigitUpper(&cells[1], number);
}

static void
renderNumberLower2 (unsigned char *cells, int number) {
  renderDigitLower(&cells[0], number/10);
  renderDigitLower(&cells[1], number);
}

static void
renderNumbers2 (unsigned char *cells, int upper, int lower) {
  renderNumberUpper2(&cells[0], upper);
  renderNumberLower2(&cells[0], lower);
}

static void
renderNumberUpper3 (unsigned char *cells, int number) {
  renderDigitUpper(&cells[0], number/100);
  renderDigitUpper(&cells[1], number/10);
  renderDigitUpper(&cells[2], number);
}

static void
renderNumberLower3 (unsigned char *cells, int number) {
  renderDigitLower(&cells[0], number/100);
  renderDigitLower(&cells[1], number/10);
  renderDigitLower(&cells[2], number);
}

static void
renderNumbers3 (unsigned char *cells, int upper, int lower) {
  renderNumberUpper3(&cells[0], upper);
  renderNumberLower3(&cells[0], lower);
}

static void
renderCoordinates2 (unsigned char *cells, int column, int row) {
  renderNumbers2(&cells[0], row, column);
}

static void
renderCoordinates3 (unsigned char *cells, int column, int row) {
  renderNumbers3(&cells[0], row, column);
}

static void
renderCoordinatesAlphabetic (unsigned char *cell, int column, int row) {
  /* the coordinates are presented as an underlined letter as the Alva DOS TSR */
  if (!SCR_COORDINATES_OK(column, row)) {
    *cell = convertCharacterToDots(textTable, WC_C('z'));
  } else {
    const int32_t height = 25;
    const int32_t frequency = row / height;

    if (frequency) {
      const int32_t interval = NSECS_PER_SEC / (frequency * 2);
      TimeValue time;

      getMonotonicTime(&time);
      scheduleUpdateIn("alva status field",
                       (((interval - (time.nanoseconds % interval)) / NSECS_PER_MSEC) + 1));

      if (!((time.nanoseconds / interval) % 2)) {
        *cell = 0;
        return;
      }
    }

    *cell = convertCharacterToDots(textTable, ((row % height) + WC_C('a')))
          | ((column / textCount) << 6);
  }
}

typedef void (*RenderStatusField) (unsigned char *cells);

static void
renderStatusField_cursorColumn (unsigned char *cells) {
  renderNumberVertical(cells, SCR_COLUMN_NUMBER(scr.posx));
}

static void
renderStatusField_cursorRow (unsigned char *cells) {
  renderNumberVertical(cells, SCR_ROW_NUMBER(scr.posy));
}

static void
renderStatusField_windowColumn (unsigned char *cells) {
  renderNumberVertical(cells, SCR_COLUMN_NUMBER(ses->winx));
}

static void
renderStatusField_windowRow (unsigned char *cells) {
  renderNumberVertical(cells, SCR_ROW_NUMBER(ses->winy));
}

static void
renderStatusField_cursorCoordinates2 (unsigned char *cells) {
  renderCoordinates2(cells, SCR_COLUMN_NUMBER(scr.posx), SCR_ROW_NUMBER(scr.posy));
}

static void
renderStatusField_windowCoordinates2 (unsigned char *cells) {
  renderCoordinates2(cells, SCR_COLUMN_NUMBER(ses->winx), SCR_ROW_NUMBER(ses->winy));
}

static void
renderStatusField_cursorCoordinates3 (unsigned char *cells) {
  renderCoordinates3(cells, SCR_COLUMN_NUMBER(scr.posx), SCR_ROW_NUMBER(scr.posy));
}

static void
renderStatusField_windowCoordinates3 (unsigned char *cells) {
  renderCoordinates3(cells, SCR_COLUMN_NUMBER(ses->winx), SCR_ROW_NUMBER(ses->winy));
}

static void
renderStatusField_cursorAndWindowColumn2 (unsigned char *cells) {
  renderNumbers2(cells,
    SCR_COLUMN_NUMBER(scr.posx),
    SCR_COLUMN_NUMBER(ses->winx)
  );
}

static void
renderStatusField_cursorAndWindowRow2 (unsigned char *cells) {
  renderNumbers2(cells,
    SCR_ROW_NUMBER(scr.posy),
    SCR_ROW_NUMBER(ses->winy)
  );
}

static void
renderStatusField_cursorAndWindowColumn3 (unsigned char *cells) {
  renderNumbers3(cells,
    SCR_COLUMN_NUMBER(scr.posx),
    SCR_COLUMN_NUMBER(ses->winx)
  );
}

static void
renderStatusField_cursorAndWindowRow3 (unsigned char *cells) {
  renderNumbers3(cells,
    SCR_ROW_NUMBER(scr.posy),
    SCR_ROW_NUMBER(ses->winy)
  );
}

static void
renderStatusField_screenNumber (unsigned char *cells) {
  char character =
    isSpecialScreen(SCR_HELP)  ? 'h':
    isSpecialScreen(SCR_MENU)  ? 'm':
    isSpecialScreen(SCR_FROZEN)? 'f':
    0;

  if (character) {
    renderCharacter(cells, character);
  } else {
    renderNumberVertical(cells, scr.number);
  }
}

static void
renderStatusField_stateDots (unsigned char *cells) {
  cells[0] = (isSpecialScreen(SCR_FROZEN)  ? BRL_DOT_1: 0)
           | (prefs.showScreenCursor       ? BRL_DOT_4: 0)
           | (ses->displayMode             ? BRL_DOT_2: 0)
           | (prefs.showAttributes         ? BRL_DOT_5: 0)
           | (prefs.alertTunes             ? BRL_DOT_3: 0)
           | (prefs.brailleTypingMode      ? BRL_DOT_6: 0)
           | (ses->trackScreenCursor       ? BRL_DOT_7: 0)
           | (prefs.brailleKeyboardEnabled ? BRL_DOT_8: 0)
           ;
}

static void
renderStatusField_stateLetter (unsigned char *cells) {
  renderCharacter(cells,
    ses->displayMode            ? WC_C('a'):
    isSpecialScreen(SCR_HELP)   ? WC_C('h'):
    isSpecialScreen(SCR_MENU)   ? WC_C('m'):
    isSpecialScreen(SCR_FROZEN) ? WC_C('f'):
    ses->trackScreenCursor      ? WC_C('t'):
    WC_C(' ')
  );
}

static void
renderStatusField_time (unsigned char *cells) {
  TimeValue value;
  getCurrentTime(&value);
  scheduleUpdateIn("time status field", millisecondsTillNextMinute(&value));

  TimeComponents components;
  expandTimeValue(&value, &components);
  renderNumbers2(cells, components.hour, components.minute);
}

static void
renderStatusField_alphabeticWindowCoordinates (unsigned char *cells) {
  renderCoordinatesAlphabetic(cells, ses->winx, ses->winy);
}

static void
renderStatusField_alphabeticCursorCoordinates (unsigned char *cells) {
  renderCoordinatesAlphabetic(cells, scr.posx, scr.posy);
}

static void
renderStatusField_generic (unsigned char *cells) {
  cells[GSC_FIRST] = GSC_MARKER;
  cells[gscBrailleWindowColumn] = SCR_COLUMN_NUMBER(ses->winx);
  cells[gscBrailleWindowRow] = SCR_ROW_NUMBER(ses->winy);
  cells[gscScreenCursorColumn] = SCR_COLUMN_NUMBER(scr.posx);
  cells[gscScreenCursorRow] = SCR_ROW_NUMBER(scr.posy);
  cells[gscScreenNumber] = scr.number;
  cells[gscFrozenScreen] = isSpecialScreen(SCR_FROZEN);
  cells[gscDisplayMode] = ses->displayMode;
  cells[gscSixDotComputerBraille] = isSixDotComputerBraille();
  cells[gscContractedBraille] = isContractedBraille();
  cells[gscSlidingBrailleWindow] = prefs.slidingBrailleWindow;
  cells[gscSkipIdenticalLines] = prefs.skipIdenticalLines;
  cells[gscSkipBlankBrailleWindows] = prefs.skipBlankBrailleWindows;
  cells[gscShowScreenCursor] = prefs.showScreenCursor;
  cells[gscHideScreenCursor] = ses->hideScreenCursor;
  cells[gscTrackScreenCursor] = ses->trackScreenCursor;
  cells[gscScreenCursorStyle] = prefs.screenCursorStyle;
  cells[gscBlinkingScreenCursor] = prefs.blinkingScreenCursor;
  cells[gscShowAttributes] = prefs.showAttributes;
  cells[gscBlinkingAttributes] = prefs.blinkingAttributes;
  cells[gscBlinkingCapitals] = prefs.blinkingCapitals;
  cells[gscAlertTunes] = prefs.alertTunes;
  cells[gscAutorepeat] = prefs.autorepeatEnabled;
  cells[gscAutospeak] = prefs.autospeak;
  cells[gscBrailleTypingMode] = prefs.brailleTypingMode;
}

static void
renderStatusField_space (unsigned char *cells) {
  cells[0] = 0;
}

typedef struct {
  RenderStatusField render;
  unsigned char length;
} StatusFieldEntry;

static const StatusFieldEntry statusFieldTable[] = {
  [sfEnd] = {
    .render = NULL,
    .length = 0
  }
  ,
  [sfWindowCoordinates2] = {
    .render = renderStatusField_windowCoordinates2,
    .length = 2
  }
  ,
  [sfWindowColumn] = {
    .render = renderStatusField_windowColumn,
    .length = 1
  }
  ,
  [sfWindowRow] = {
    .render = renderStatusField_windowRow,
    .length = 1
  }
  ,
  [sfCursorCoordinates2] = {
    .render = renderStatusField_cursorCoordinates2,
    .length = 2
  }
  ,
  [sfCursorColumn] = {
    .render = renderStatusField_cursorColumn,
    .length = 1
  }
  ,
  [sfCursorRow] = {
    .render = renderStatusField_cursorRow,
    .length = 1
  }
  ,
  [sfCursorAndWindowColumn2] = {
    .render = renderStatusField_cursorAndWindowColumn2,
    .length = 2
  }
  ,
  [sfCursorAndWindowRow2] = {
    .render = renderStatusField_cursorAndWindowRow2,
    .length = 2
  }
  ,
  [sfScreenNumber] = {
    .render = renderStatusField_screenNumber,
    .length = 1
  }
  ,
  [sfStateDots] = {
    .render = renderStatusField_stateDots,
    .length = 1
  }
  ,
  [sfStateLetter] = {
    .render = renderStatusField_stateLetter,
    .length = 1
  }
  ,
  [sfTime] = {
    .render = renderStatusField_time,
    .length = 2
  }
  ,
  [sfAlphabeticWindowCoordinates] = {
    .render = renderStatusField_alphabeticWindowCoordinates,
    .length = 1
  }
  ,
  [sfAlphabeticCursorCoordinates] = {
    .render = renderStatusField_alphabeticCursorCoordinates,
    .length = 1
  }
  ,
  [sfGeneric] = {
    .render = renderStatusField_generic,
    .length = GSC_COUNT
  },

  [sfCursorCoordinates3] = {
    .render = renderStatusField_cursorCoordinates3,
    .length = 3
  }
  ,
  [sfWindowCoordinates3] = {
    .render = renderStatusField_windowCoordinates3,
    .length = 3
  }
  ,
  [sfCursorAndWindowColumn3] = {
    .render = renderStatusField_cursorAndWindowColumn3,
    .length = 3
  }
  ,
  [sfCursorAndWindowRow3] = {
    .render = renderStatusField_cursorAndWindowRow3,
    .length = 3
  }
  ,
  [sfSpace] = {
    .render = renderStatusField_space,
    .length = 1
  },
};

static const unsigned int statusFieldCount = ARRAY_COUNT(statusFieldTable);

unsigned int
getStatusFieldsLength (const unsigned char *fields) {
  unsigned int length = 0;
  while (*fields != sfEnd) length += statusFieldTable[*fields++].length;
  return length;
}

void
renderStatusFields (const unsigned char *fields, unsigned char *cells) {
  while (*fields != sfEnd) {
    StatusField field = *fields++;

    if (field < statusFieldCount) {
      const StatusFieldEntry *sf = &statusFieldTable[field];
      sf->render(cells);
      cells += sf->length;
    }
  }
}
