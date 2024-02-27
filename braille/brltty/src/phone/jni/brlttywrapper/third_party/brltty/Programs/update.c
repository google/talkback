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

#include <stdio.h>
#include <string.h>

#include "parameters.h"
#include "log.h"
#include "alert.h"
#include "report.h"
#include "strfmt.h"
#include "update.h"
#include "async_handle.h"
#include "async_alarm.h"
#include "timing.h"
#include "unicode.h"
#include "charset.h"
#include "ttb.h"
#include "atb.h"
#include "brl_dots.h"
#include "spk.h"
#include "scr.h"
#include "scr_special.h"
#include "scr_utils.h"
#include "prefs.h"
#include "status.h"
#include "blink.h"
#include "routing.h"
#include "api_control.h"
#include "core.h"

static void
overlayAttributesUnderline (unsigned char *cell, unsigned char attributes) {
  unsigned char dots;

  switch (attributes) {
    case SCR_COLOUR_FG_DARK_GREY | SCR_COLOUR_BG_BLACK:
    case SCR_COLOUR_FG_LIGHT_GREY | SCR_COLOUR_BG_BLACK:
    case SCR_COLOUR_FG_LIGHT_GREY | SCR_COLOUR_BG_BLUE:
    case SCR_COLOUR_FG_BLACK | SCR_COLOUR_BG_CYAN:
      return;

    case SCR_COLOUR_FG_BLACK | SCR_COLOUR_BG_LIGHT_GREY:
      dots = BRL_DOT_7 | BRL_DOT_8;
      break;

    case SCR_COLOUR_FG_WHITE | SCR_COLOUR_BG_BLACK:
    default:
      dots = BRL_DOT_8;
      break;
  }

  {
    BlinkDescriptor *blink = &attributesUnderlineBlinkDescriptor;

    requireBlinkDescriptor(blink);
    if (isBlinkVisible(blink)) *cell |= dots;
  }
}

static void
readBrailleWindow (ScreenCharacter *characters, size_t count) {
  int screenColumns = MIN(textCount, scr.cols-ses->winx);
  int screenRows = MIN(brl.textRows, scr.rows-ses->winy);

  if (prefs.wordWrap) {
    int length = getWordWrapLength(ses->winy, ses->winx, screenColumns);
    if (length < screenColumns) screenColumns = length;
  }

  if (screenColumns > 0) {
    readScreen(ses->winx, ses->winy, screenColumns, screenRows, characters);
  }

  if (screenColumns < textCount) {
    /* We got a rectangular piece of text with readScreen but the display
     * is in an off-right position with some cells at the end blank
     * so we'll insert these cells and blank them.
     */

    {
      int lastRow = screenRows - 1;
      const ScreenCharacter *source = characters + (lastRow * screenColumns);
      ScreenCharacter *target = characters + (lastRow * textCount);
      size_t size = screenColumns * sizeof(*target);

      while (source > characters) {
        memmove(target, source, size);
        source -= screenColumns;
        target -= textCount;
      }
    }

    {
      ScreenCharacter *row = characters + screenColumns;
      const ScreenCharacter *end = characters + (screenRows * textCount);
      size_t count = textCount - screenColumns;

      while (row < end) {
        clearScreenCharacters(row, count);
        row += textCount;
      }
    }
  }

  if (screenRows < brl.textRows) {
    clearScreenCharacters(
      characters + (screenRows * textCount),
      (brl.textRows - screenRows) * textCount
    );
  }
}

typedef void ScreenCharacterTranslator (
  const ScreenCharacter *character, unsigned char *cell, wchar_t *text
);

static void
translateScreenCharacter_text (
  const ScreenCharacter *character, unsigned char *cell, wchar_t *text
) {
  *cell = convertCharacterToDots(textTable, character->text);
  *text = character->text;

  {
    const unsigned char dots = BRL_DOT_7 | BRL_DOT_8;

    if (*cell & dots) {
      if (isSixDotComputerBraille()) {
        *cell &= ~dots;
      }
    }
  }

  if (prefs.showAttributes) {
    overlayAttributesUnderline(cell, character->attributes);
  }

  {
    BlinkDescriptor *blink = &uppercaseLettersBlinkDescriptor;

    if (isBlinkEnabled(blink)) {
      if (iswupper(character->text)) {
        requireBlinkDescriptor(blink);
        if (!isBlinkVisible(blink)) *cell = 0;
      }
    }
  }
}

static void
translateScreenCharacter_attributes (
  const ScreenCharacter *character, unsigned char *cell, wchar_t *text
) {
  *text = UNICODE_BRAILLE_ROW | (*cell = convertAttributesToDots(attributesTable, character->attributes));
}

static void
translateBrailleWindow (
  const ScreenCharacter *characters, wchar_t *textBuffer
) {
  ScreenCharacterTranslator *translateScreenCharacter =
    ses->displayMode?
    translateScreenCharacter_attributes:
    translateScreenCharacter_text;

  for (unsigned int row=0; row<brl.textRows; row+=1) {
    const ScreenCharacter *character = &characters[row * textCount];
    const ScreenCharacter *end = character + textCount;

    unsigned int start = (row * brl.textColumns) + textStart;
    unsigned char *cell = &brl.buffer[start];
    wchar_t *text = &textBuffer[start];

    while (character < end) {
      translateScreenCharacter(character++, cell++, text++);
    }
  }
}

int isContracted = 0;
int contractedTrack = 0;

static void
constructContractionCache (ContractionCache *cache) {
  cache->input.characters = NULL;
  cache->input.size = 0;
  cache->input.count = 0;

  cache->output.cells = NULL;
  cache->output.size = 0;
  cache->output.count = 0;

  cache->offsets.array = NULL;
  cache->offsets.size = 0;
  cache->offsets.count = 0;
}

static void
constructBrailleRowDescriptor (BrailleRowDescriptor *brd) {
  constructContractionCache(&brd->contracted.cache);
  brd->contracted.length = 0;
  brd->contracted.offsets.array = NULL;
  brd->contracted.offsets.size = 0;
}

BrailleRowDescriptor *
getBrailleRowDescriptor (unsigned int row) {
  if (row >= brl.rowDescriptors.size) {
    size_t newSize = row + 1;

    BrailleRowDescriptor *newArray = realloc(
      brl.rowDescriptors.array,
      ARRAY_SIZE(brl.rowDescriptors.array, newSize)
    );

    if (!newArray) {
      logMallocError();
      return NULL;
    }

    while (brl.rowDescriptors.size < newSize) {
      BrailleRowDescriptor *brd = &newArray[brl.rowDescriptors.size++];
      constructBrailleRowDescriptor(brd);
    }

    brl.rowDescriptors.array = newArray;
  }

  return &brl.rowDescriptors.array[row];
}

static int
ensureContractedOffsetsSize (BrailleRowDescriptor *brd, size_t size) {
  if (++size > brd->contracted.offsets.size) {
    size_t newSize = 1;
    while (newSize < size) newSize <<= 1;

    int *newArrayets = realloc(
      brd->contracted.offsets.array,
      ARRAY_SIZE(brd->contracted.offsets.array, newSize)
    );

    if (!newArrayets) {
      logMallocError();
      return 0;
    }

    brd->contracted.offsets.array = newArrayets;
    brd->contracted.offsets.size = newSize;
  }

  return 1;
}

int
getCursorOffsetForContracting (void) {
  if (scr.posy != ses->winy) return CTB_NO_CURSOR;
  if (scr.posx < ses->winx) return CTB_NO_CURSOR;
  return scr.posx - ses->winx;
}

static int
contractScreenRow (BrailleRowDescriptor *brd, unsigned int screenRow, unsigned char *cells, unsigned int cellCount) {
  int isCursorRow = scr.posy == ses->winy;

  int inputLength = scr.cols - ses->winx;
  wchar_t inputText[inputLength];
  int outputLength = cellCount;

  ScreenCharacter inputCharacters[inputLength];
  readScreen(ses->winx, screenRow, inputLength, 1, inputCharacters);

  for (int i=0; i<inputLength; i+=1) {
    inputText[i] = inputCharacters[i].text;
  }

  ensureContractedOffsetsSize(brd, inputLength);
  int *offsetsArray = brd->contracted.offsets.array;

  contractText(
    contractionTable, &brd->contracted.cache,
    inputText, &inputLength,
    cells, &outputLength,
    offsetsArray, getCursorOffsetForContracting()
  );

  {
    int inputEnd = inputLength;

    if (contractedTrack && isCursorRow) {
      if (outputLength == cellCount) {
        int inputIndex = inputEnd;

        while (inputIndex) {
          int offset = offsetsArray[--inputIndex];

          if (offset != CTB_NO_OFFSET) {
            if (offset != outputLength) break;
            inputEnd = inputIndex;
          }
        }
      }

      if (scr.posx >= (ses->winx + inputEnd)) {
        int offset = 0;
        int length = scr.cols - ses->winx;
        int onSpace = 0;

        while (offset < length) {
          if ((iswspace(inputCharacters[offset].text) != 0) != onSpace) {
            if (onSpace) break;
            onSpace = 1;
          }

          offset += 1;
        }

        if ((offset += ses->winx) > scr.posx) {
          ses->winx = scr.posx;
        } else {
          ses->winx = offset;
        }

        return 0;
      }
    }
  }

  if (ses->displayMode || prefs.showAttributes) {
    int outputOffset = 0;
    unsigned char attributes = 0;
    unsigned char attributesBuffer[outputLength];

    for (int inputOffset=0; inputOffset<inputLength; inputOffset+=1) {
      int offset = offsetsArray[inputOffset];

      if (offset != CTB_NO_OFFSET) {
        while (outputOffset < offset) attributesBuffer[outputOffset++] = attributes;
        attributes = 0;
      }

      attributes |= inputCharacters[inputOffset].attributes;
    }

    while (outputOffset < outputLength) {
      attributesBuffer[outputOffset++] = attributes;
    }

    if (ses->displayMode) {
      for (unsigned int i=0; i<outputLength; i+=1) {
        cells[i] = convertAttributesToDots(attributesTable, attributesBuffer[i]);
      }
    } else {
      for (unsigned int i=0; i<outputLength; i+=1) {
        overlayAttributesUnderline(&cells[i], attributesBuffer[i]);
      }
    }
  }

  brd->contracted.length = inputLength;
  return 1;
}

static int
generateContractedBraille (wchar_t *text) {
  unsigned int brailleRow = 0;
  unsigned char *cells = &brl.buffer[textStart];
  text += textStart;

  while (brailleRow < brl.textRows) {
    unsigned int screenRow = brailleRow + ses->winy;
    if (screenRow >= scr.rows) break;

    BrailleRowDescriptor *brd = getBrailleRowDescriptor(brailleRow);
    if (!contractScreenRow(brd, screenRow, cells, textCount)) return 0;

    for (unsigned int i=0; i<textCount; i+=1) {
      text[i] = UNICODE_BRAILLE_ROW | cells[i];
    }

    cells += brl.textColumns;
    text += brl.textColumns;
    brailleRow += 1;
  }

  while (brailleRow < brl.textRows) {
    memset(cells, 0, textCount);
    wmemset(text, WC_C(' '), textCount);

    cells += brl.textColumns;
    text += brl.textColumns;
    brailleRow += 1;
  }

  return 1;
}

static int
checkScreenPointer (void) {
  int moved = 0;
  int column, row;

  if (prefs.trackScreenPointer && getScreenPointer(&column, &row)) {
    if (column != ses->ptrx) {
      if (ses->ptrx >= 0) moved = 1;
      ses->ptrx = column;
    }

    if (row != ses->ptry) {
      if (ses->ptry >= 0) moved = 1;
      ses->ptry = row;
    }

    if (moved) {
      if (column < ses->winx) {
        ses->winx = column;
      } else if (column >= (int)(ses->winx + textCount)) {
        ses->winx = column + 1 - textCount;
      }

      if (row < ses->winy) {
        ses->winy = row;
      } else if (row >= (int)(ses->winy + brl.textRows)) {
        ses->winy = row + 1 - brl.textRows;
      }
    }
  } else {
    ses->ptrx = ses->ptry = -1;
  }

  return moved;
}

static void
highlightBrailleWindowLocation (void) {
  if (prefs.highlightBrailleWindowLocation) {
    int left = ses->winx;
    int right = left;

    int top = ses->winy;
    int bottom = top;

    if (!prefs.showAttributes) {
      if ((right += textCount) > scr.cols) right = scr.cols;
      right -= 1;

      if ((bottom += brl.textRows) > scr.rows) bottom = scr.rows;
      bottom -= 1;
    }

    highlightScreenRegion(left, right, top, bottom);
  }
}

static const unsigned char cursorStyles[] = {
  [csBottomDots] = (BRL_DOT_7 | BRL_DOT_8),
  [csAllDots] = BRL_DOTS_ALL,
  [csLowerLeftDot] = (BRL_DOT_7),
  [csLowerRightDot] = (BRL_DOT_8),
  [csNoDots] = (0)
};

unsigned char
getCursorDots (const unsigned char *setting) {
  if (*setting >= ARRAY_COUNT(cursorStyles)) return 0;
  return cursorStyles[*setting];
}

int
setCursorDots (unsigned char *setting, unsigned char dots) {
  for (unsigned char style=0; style<ARRAY_COUNT(cursorStyles); style+=1) {
    if (dots == cursorStyles[style]) {
      *setting = style;
      return 1;
    }
  }

  return 0;
}

unsigned char
getScreenCursorDots (void) {
  return getCursorDots(&prefs.screenCursorStyle);
}

int
setScreenCursorDots (unsigned char dots) {
  return setCursorDots(&prefs.screenCursorStyle, dots);
}

unsigned char
getSpeechCursorDots (void) {
  return getCursorDots(&prefs.speechCursorStyle);
}

int
setSpeechCursorDots (unsigned char dots) {
  return setCursorDots(&prefs.speechCursorStyle, dots);
}

unsigned char
mapCursorDots (unsigned char dots) {
  if (!hasEightDotCells(&brl)) {
    brlRemapDot(&dots, BRL_DOT_7, BRL_DOT_3);
    brlRemapDot(&dots, BRL_DOT_8, BRL_DOT_6);
  }

  return dots;
}

static int
getScreenCursorPosition (int x, int y) {
  if (y < ses->winy) return BRL_NO_CURSOR;
  if (y >= scr.rows) return BRL_NO_CURSOR;
  if (y >= (int)(ses->winy + brl.textRows)) return BRL_NO_CURSOR;

  if (x < ses->winx) return BRL_NO_CURSOR;
  if (x >= scr.cols) return BRL_NO_CURSOR;

  int rowIndex = y - ses->winy;
  int rowPosition = (rowIndex * brl.textColumns) + textStart;

  if (isContracted) {
    BrailleRowDescriptor *brd = getBrailleRowDescriptor(rowIndex);
    if (!brd) return BRL_NO_CURSOR;

    int *offsets = brd->contracted.offsets.array;
    if (!offsets) return BRL_NO_CURSOR;

    x -= ses->winx;
    if (x >= brd->contracted.length) return BRL_NO_CURSOR;

    while (x >= 0) {
      int offset = offsets[x];

      if (offset != CTB_NO_OFFSET) {
        if (offset < textCount) return rowPosition + offset;
        break;
      }

      x -= 1;
    }
  } else if (x < (int)(ses->winx + textCount)) {
    return rowPosition + (x - ses->winx);
  }

  return BRL_NO_CURSOR;
}

static int
writeStatusCells (void) {
  if (braille->writeStatus) {
    const unsigned char *fields = prefs.statusFields;
    unsigned int length = getStatusFieldsLength(fields);

    if (length > 0) {
      unsigned int count = brl.statusColumns * brl.statusRows;
      if (count < length) count = length;
      unsigned char cells[count];

      memset(cells, 0, count);
      renderStatusFields(fields, cells);
      if (!braille->writeStatus(&brl, cells)) return 0;
    } else if (!clearStatusCells(&brl)) {
      return 0;
    }
  }

  return 1;
}

static inline char
getScreenCursorTrackingCharacter (void) {
  return ses->trackScreenCursor? 't': ' ';
}

static inline char
getScreenCursorVisibilityCharacter (void) {
  return prefs.showScreenCursor? 'c': ' ';
}

static inline char
getAttributesUnderlineVisibilityCharacter (void) {
  return prefs.showAttributes? 'u': ' ';
}

static inline char
getSpecialScreenCharacter (void) {
  if (isSpecialScreen(SCR_FROZEN)) return 'f';
  if (isSpecialScreen(SCR_HELP)) return 'h';
  if (isSpecialScreen(SCR_MENU)) return 'm';
  return ' ';
}

static inline char
getBrailleVariantCharacter (void) {
  return ses->displayMode? 'a':
         isContractedBraille()? 'c':
         isSixDotComputerBraille()? '6': '8';
}

static inline char
getBrailleKeyboardCharacter (void) {
  if (!prefs.brailleKeyboardEnabled) return 'd';
  if (prefs.brailleTypingMode) return 'b';
  return ' ';
}

static inline char
getSpeechCursorVisibilityCharacter (void) {
  return prefs.showSpeechCursor? 's': ' ';
}

static int
renderInfoLine (void) {
  brl.cursor = BRL_NO_CURSOR;

  static const char mode[] = "info";
  if (!setStatusText(&brl, mode)) return 0;

  /* We must be careful. Some displays (e.g. Braille Lite 18)
   * are very small, and others (e.g. Bookworm) are even smaller.
   * Also, some displays (e.g. Braille Me) have only six dots per cell.
   */
  const size_t size = brl.textColumns * brl.textRows;
  int compact = (size < 22) && hasEightDotCells(&brl);

  static const unsigned char compactFields[] = {
    sfCursorAndWindowColumn2, sfCursorAndWindowRow2, sfStateDots, sfEnd
  };

  static unsigned int compactLength = 0;
  if (!compactLength) compactLength = getStatusFieldsLength(compactFields);
  unsigned char compactCells[compactLength];

  size_t length;
  char text[size + 1];
  STR_BEGIN(text, sizeof(text));

  if (compact) {
    memset(compactCells, 0, compactLength);
    renderStatusFields(compactFields, compactCells);

    {
      unsigned int counter = compactLength;
      while (counter--) STR_PRINTF("x");
    }
  } else {
    STR_PRINTF(
      "%02d:%02d %02d:%02d",
      SCR_COLUMN_NUMBER(ses->winx), SCR_ROW_NUMBER(ses->winy),
      SCR_COLUMN_NUMBER(scr.posx), SCR_ROW_NUMBER(scr.posy)
    );
  }

  STR_PRINTF(
    " %02d %c%c%c%c%c%c%c",
    scr.number, 
    getScreenCursorTrackingCharacter(),
    getScreenCursorVisibilityCharacter(),
    getAttributesUnderlineVisibilityCharacter(),
    getSpeechCursorVisibilityCharacter(),
    getSpecialScreenCharacter(),
    getBrailleVariantCharacter(),
    getBrailleKeyboardCharacter()
  );

  if ((STR_LENGTH + 6) <= size) {
    TimeFormattingData fmt;
    getTimeFormattingData(&fmt);

    STR_PRINTF(" ");
    STR_FORMAT(formatBrailleTime, &fmt);

    if (prefs.showSeconds) {
      scheduleUpdateIn("info clock second", millisecondsTillNextSecond(&fmt.value));
    } else {
      scheduleUpdateIn("info clock minute", millisecondsTillNextMinute(&fmt.value));
    }
  }

  length = STR_LENGTH;
  STR_END;

  if (length > size) length = size;
  wchar_t characters[length];

  {
    unsigned int threshold = compact? compactLength: 0;

    for (unsigned int i=0; i<length; i+=1) {
      wint_t character;

      if (i < threshold) {
        character = UNICODE_BRAILLE_ROW | compactCells[i];
      } else {
        character = convertCharToWchar(text[i]);
        if (character == WEOF) character = WC_C('?');
      }

      characters[i] = character;
    }
  }

  return writeBrailleCharacters(mode, characters, length);
}

static int
saveScreenCharacters (
  ScreenCharacter **buffer, size_t *size,
  const ScreenCharacter *characters, size_t count
) {
  size_t newSize = count * sizeof(*characters);

  if (newSize > *size) {
    ScreenCharacter *newBuffer = malloc(newSize);

    if (!newBuffer) {
      logMallocError();
      return 0;
    }

    if (*buffer) free(*buffer);
    *buffer = newBuffer;
    *size = newSize;
  }

  memcpy(*buffer, characters, newSize);
  return 1;
}

static void
checkScreenScroll (int track) {
  const int rowCount = 3;

  static int oldScreen = -1;
  static int oldRow = -1;
  static int oldWidth = 0;
  static size_t oldSize = 0;
  static ScreenCharacter *oldCharacters = NULL;

  int newScreen = scr.number;
  int newWidth = scr.cols;
  size_t newCount = newWidth * rowCount;
  ScreenCharacter newCharacters[newCount];

  int newRow = ses->winy;
  int newTop = newRow - (rowCount - 1);

  if (newTop < 0) {
    newCount = 0;
  } else {
    readScreenRows(newTop, newWidth, rowCount, newCharacters);

    if (track && prefs.trackScreenScroll && oldCharacters &&
        (newScreen == oldScreen) && (newWidth == oldWidth) &&
        (newRow == oldRow)) {
      while (newTop > 0) {
        if ((scr.posy >= newTop) && (scr.posy <= newRow)) break;

        if (isSameRow(oldCharacters, newCharacters, newCount, isSameCharacter)) {
          if (newRow != ses->winy) {
            ses->winy = newRow;
            alert(ALERT_SCROLL_UP);
          }

          break;
        }

        readScreenRows(--newTop, newWidth, rowCount, newCharacters);
        newRow -= 1;
      }
    }
  }

  if (saveScreenCharacters(&oldCharacters, &oldSize, newCharacters, newCount)) {
    oldScreen = newScreen;
    oldRow = ses->winy;
    oldWidth = newWidth;
  }
}

static int oldwinx;
static int oldwiny;

#ifdef ENABLE_SPEECH_SUPPORT
static int wasAutospeaking;

void
autospeak (AutospeakMode mode) {
  static int oldScreen = -1;
  static int oldX = -1;
  static int oldY = -1;
  static int oldWidth = 0;
  static ScreenCharacter *oldCharacters = NULL;
  static size_t oldSize = 0;
  static int cursorAssumedStable = 0;

  int newScreen = scr.number;
  int newX = scr.posx;
  int newY = scr.posy;
  int newWidth = scr.cols;
  ScreenCharacter newCharacters[newWidth];

  readScreenRow(ses->winy, newWidth, newCharacters);

  if (!spk.track.isActive) {
    const ScreenCharacter *characters = newCharacters;
    int column = 0;
    int count = newWidth;
    const char *reason = NULL;
    int indent = 0;

    if (mode == AUTOSPEAK_FORCE) {
      reason = "current line";
    } else if (!oldCharacters) {
      reason = "initial line";
      count = 0;
    } else if ((newScreen != oldScreen) || (ses->winy != oldwiny) || (newWidth != oldWidth)) {
      if (!prefs.autospeakSelectedLine) count = 0;
      reason = "line selected";
      if (prefs.autospeakLineIndent) indent = 1;
    } else {
      int onScreen = (newX >= 0) && (newX < newWidth);

      if (!isSameRow(newCharacters, oldCharacters, newWidth, isSameText)) {
        if ((newY == ses->winy) && (newY == oldY) && onScreen) {
          /* Sometimes the cursor moves after the screen content has been
           * updated. Make sure we don't race ahead of such a cursor move
           * before assuming that it is actually stable.
           */
	  if ((newX == oldX) && !cursorAssumedStable) {
	    scheduleUpdate("autospeak cursor stability check");
	    cursorAssumedStable = 1;
	    return;
	  }

          if ((newX == oldX) &&
              isSameRow(newCharacters, oldCharacters, newX, isSameText)) {
            int oldLength = oldWidth;
            int newLength = newWidth;
            int x = newX;

            while (oldLength > oldX) {
              if (!iswspace(oldCharacters[oldLength-1].text)) break;
              oldLength -= 1;
            }
            if (oldLength < oldWidth) oldLength += 1;

            while (newLength > newX) {
              if (!iswspace(newCharacters[newLength-1].text)) break;
              newLength -= 1;
            }
            if (newLength < newWidth) newLength += 1;

            while (1) {
              int done = 1;

              if (x < newLength) {
                if (isSameRow(newCharacters+x, oldCharacters+oldX, newWidth-x, isSameText)) {
                  column = newX;
                  count = prefs.autospeakInsertedCharacters? (x - newX): 0;
                  reason = "characters inserted after cursor";
                  goto autospeak;
                }

                done = 0;
              }

              if (x < oldLength) {
                if (isSameRow(newCharacters+newX, oldCharacters+x, oldWidth-x, isSameText)) {
                  characters = oldCharacters;
                  column = oldX;
                  count = prefs.autospeakDeletedCharacters? (x - oldX): 0;
                  reason = "characters deleted after cursor";
                  goto autospeak;
                }

                done = 0;
              }

              if (done) break;
              x += 1;
            }
          }

          if (oldX < 0) oldX = 0;
          if ((newX > oldX) &&
              isSameRow(newCharacters, oldCharacters, oldX, isSameText) &&
              isSameRow(newCharacters+newX, oldCharacters+oldX, newWidth-newX, isSameText)) {
            column = oldX;
            count = newX - oldX;

            if (prefs.autospeakCompletedWords) {
              int last = column + count - 1;

              if (iswspace(characters[last].text)) {
                int first = column;

                while (first > 0) {
                  if (iswspace(characters[--first].text)) {
                    first += 1;
                    break;
                  }
                }

                if (first < column) {
                  while (last >= first) {
                    if (!iswspace(characters[last].text)) break;
                    last -= 1;
                  }

                  if (++last > first) {
                    column = first;
                    count = (last + 1) - first;
                    reason = "word inserted";
                    goto autospeak;
                  }
                }
              }
            }

            if (!prefs.autospeakInsertedCharacters) count = 0;
            reason = "characters inserted before cursor";
            goto autospeak;
          }

          if (oldX >= oldWidth) oldX = oldWidth - 1;
          if ((newX < oldX) &&
              isSameRow(newCharacters, oldCharacters, newX, isSameText) &&
              isSameRow(newCharacters+newX, oldCharacters+oldX, oldWidth-oldX, isSameText)) {
            characters = oldCharacters;
            column = newX;
            count = prefs.autospeakDeletedCharacters? (oldX - newX): 0;
            reason = "characters deleted before cursor";
            goto autospeak;
          }
        }

        while (newCharacters[column].text == oldCharacters[column].text) ++column;
        while (newCharacters[count-1].text == oldCharacters[count-1].text) --count;
        count -= column;
        if (!prefs.autospeakReplacedCharacters) count = 0;
        reason = "characters replaced";
      } else if ((newY == ses->winy) && ((newX != oldX) || (newY != oldY)) && onScreen) {
        column = newX;
        count = prefs.autospeakSelectedCharacter? 1: 0;
        reason = "character selected";

        if (prefs.autospeakCompletedWords) {
          if ((newX > oldX) && (column >= 2)) {
            int length = newWidth;

            while (length > 0) {
              if (!iswspace(characters[--length].text)) {
                length += 1;
                break;
              }
            }

            if ((length + 1) == column) {
              int first = length - 1;

              while (first > 0) {
                if (iswspace(characters[--first].text)) {
                  first += 1;
                  break;
                }
              }

              if ((length -= first) > 0) {
                column = first;
                count = length + 1;
                reason = "word appended";
                goto autospeak;
              }
            }
          }
        }
      } else {
        count = 0;
      }
    }

  autospeak:
    if (mode == AUTOSPEAK_SILENT) count = 0;

    characters += column;
    int interrupt = 1;

    if (indent) {
      if (speakIndent(characters, count, 0)) {
        interrupt = 0;
      }
    }

    if (count && (scr.quality >= autospeakMinimumScreenContentQuality)) {
      if (!reason) reason = "unknown reason";

      logMessage(LOG_CATEGORY(SPEECH_EVENTS),
        "autospeak: %s: [%d,%d] %d.%d",
        reason, ses->winx, ses->winy, column, count
      );

      speakCharacters(characters, count, 0, interrupt);
    }
  }

  if (saveScreenCharacters(&oldCharacters, &oldSize, newCharacters, newWidth)) {
    oldScreen = newScreen;
    oldX = newX;
    oldY = newY;
    oldWidth = newWidth;
    cursorAssumedStable = 0;
  }
}

void
suppressAutospeak (void) {
  if (isAutospeakActive()) {
    autospeak(AUTOSPEAK_SILENT);
    oldwinx = ses->winx;
    oldwiny = ses->winy;
  }
}
#endif /* ENABLE_SPEECH_SUPPORT */

void
reportBrailleWindowMoved (void) {
  const BrailleWindowMovedReport data = {
    .screen = {
      .column = ses->winx,
      .row = ses->winy
    },

    .text = {
      .count = textCount
    }
  };

  report(REPORT_BRAILLE_WINDOW_MOVED, &data);
}

int
writeBrailleWindow (BrailleDisplay *brl, const wchar_t *text, unsigned char quality) {
  {
    const BrailleWindowUpdatedReport data = {
      .cells = &brl->buffer[textStart],
      .count = textCount
    };

    report(REPORT_BRAILLE_WINDOW_UPDATED, &data);
  }

  brl->quality = quality;
  return braille->writeWindow(brl, text);
}

static void
doUpdate (void) {
  logMessage(LOG_CATEGORY(UPDATE_EVENTS), "starting");
  unrequireAllBlinkDescriptors();
  refreshScreen();
  updateSessionAttributes();
  api.flushOutput();

  if (scr.unreadable) {
    logMessage(LOG_CATEGORY(UPDATE_EVENTS), "screen unreadable: %s", scr.unreadable);
  } else {
    logMessage(LOG_CATEGORY(UPDATE_EVENTS), "screen: #%d %dx%d [%d,%d]",
               scr.number, scr.cols, scr.rows, scr.posx, scr.posy);
  }

  if (opt_releaseDevice) {
    if (scr.unreadable) {
      if (canBraille()) {
        logMessage(LOG_DEBUG, "suspending braille driver");
        writeStatusCells();
        writeBrailleText("wrn", scr.unreadable);
        api.suspendDriver();
        brl.isSuspended = 1;
        logMessage(LOG_DEBUG, "braille driver suspended");
      }
    } else {
      if (brl.isSuspended) {
        logMessage(LOG_DEBUG, "resuming braille driver");
        forgetDevices();
        brl.isSuspended = !api.resumeDriver();

        if (brl.isSuspended) {
          logMessage(LOG_DEBUG, "braille driver not resumed");
        } else {
          logMessage(LOG_DEBUG, "braille driver resumed");
        }
      }
    }
  }

  int screenPointerHasMoved = 0;
  int trackScreenScroll = 0;

  if (ses->trackScreenCursor) {
#ifdef ENABLE_SPEECH_SUPPORT
    if (!spk.track.isActive)
#endif /* ENABLE_SPEECH_SUPPORT */
    {
      /* If screen cursor moves while blinking is on */
      if (prefs.blinkingScreenCursor) {
        if (scr.posy != ses->trky) {
          /* turn off cursor to see what's under it while changing lines */
          setBlinkState(&screenCursorBlinkDescriptor, 0);
        } else if (scr.posx != ses->trkx) {
          /* turn on cursor to see it moving on the line */
          setBlinkState(&screenCursorBlinkDescriptor, 1);
        }
      }

      /* If the cursor moves in cursor tracking mode: */
      if (!isRouting()) {
        if ((scr.posx != ses->trkx) || (scr.posy != ses->trky)) {
          int oldx = ses->winx;
          int oldy = ses->winy;
          trackScreenCursor(0);

          logMessage(LOG_CATEGORY(CURSOR_TRACKING),
                     "scr=%u csr=[%u,%u]->[%u,%u] win=[%u,%u]->[%u,%u]",
                     scr.number,
                     ses->trkx, ses->trky, scr.posx, scr.posy,
                     oldx, oldy, ses->winx, ses->winy);

          ses->spkx = ses->trkx = scr.posx;
          ses->spky = ses->trky = scr.posy;
        } else if (checkScreenPointer()) {
          screenPointerHasMoved = 1;
        } else {
          trackScreenScroll = 1;
        }
      }
    }
  } else {
    trackScreenScroll = 1;
  }

  checkScreenScroll(trackScreenScroll);

#ifdef ENABLE_SPEECH_SUPPORT
  if (spk.canAutospeak) {
    int isAutospeaking = isAutospeakActive();

    if (isAutospeaking) {
      autospeak(wasAutospeaking? AUTOSPEAK_CHANGES: AUTOSPEAK_FORCE);
    } else if (wasAutospeaking) {
      muteSpeech(&spk, "autospeak disabled");
    }

    wasAutospeaking = isAutospeaking;
  }
#endif /* ENABLE_SPEECH_SUPPORT */

  /* There are a few things to take care of if the display has moved. */
  if ((ses->winx != oldwinx) || (ses->winy != oldwiny)) {
    if (!screenPointerHasMoved) highlightBrailleWindowLocation();

    /* Attributes are blinking.
     * We could check to see if we changed screen, but that doesn't
     * really matter... this is mainly for when you are hunting up/down
     * for the line with attributes.
     */
    setBlinkState(&attributesUnderlineBlinkDescriptor, 1);
    /* problem: this still doesn't help when the braille window is
     * stationnary and the attributes themselves are moving
     * (example: tin).
     */

    if ((ses->spky < ses->winy) || (ses->spky >= (ses->winy + brl.textRows))) ses->spky = ses->winy;
    if ((ses->spkx < ses->winx) || (ses->spkx >= (ses->winx + textCount))) ses->spkx = ses->winx;

    oldwinx = ses->winx;
    oldwiny = ses->winy;
  }

  if (!brl.isOffline && canBraille()) {
    api.claimDriver();

    if (infoMode) {
      if (!renderInfoLine()) brl.hasFailed = 1;
    } else {
      const unsigned int windowLength = brl.textColumns * brl.textRows;
      memset(brl.buffer, 0, windowLength);

      wchar_t textBuffer[windowLength];
      wmemset(textBuffer, WC_C(' '), windowLength);

      unsigned int textLength = textCount * brl.textRows;
      isContracted = isContracting();

      if (isContracted) {
        while (1) {
          int generated = generateContractedBraille(textBuffer);
          contractedTrack = 0;
          if (generated) break;
        }
      } else {
        ScreenCharacter characters[textLength];
        readBrailleWindow(characters, ARRAY_COUNT(characters));
        translateBrailleWindow(characters, textBuffer);
      }

      if ((brl.cursor = getScreenCursorPosition(scr.posx, scr.posy)) != BRL_NO_CURSOR) {
        if (showScreenCursor()) {
          BlinkDescriptor *blink = &screenCursorBlinkDescriptor;
          requireBlinkDescriptor(blink);

          if (isBlinkVisible(blink)) {
            brl.buffer[brl.cursor] |= mapCursorDots(getScreenCursorDots());
          }
        }
      }

      if (prefs.showSpeechCursor) {
        int position = getScreenCursorPosition(ses->spkx, ses->spky);

        if (position != BRL_NO_CURSOR) {
          if (position != brl.cursor) {
            BlinkDescriptor *blink = &speechCursorBlinkDescriptor;
            requireBlinkDescriptor(blink);

            if (isBlinkVisible(blink)) {
              brl.buffer[position] |= mapCursorDots(getSpeechCursorDots());
            }
          }
        }
      }

      if (statusCount > 0) {
        const unsigned char *fields = prefs.statusFields;
        unsigned int length = getStatusFieldsLength(fields);

        if (length > 0) {
          unsigned char cells[length];
          memset(cells, 0, length);
          renderStatusFields(fields, cells);
          fillDotsRegion(textBuffer, brl.buffer,
                         statusStart, statusCount, brl.textColumns, brl.textRows,
                         cells, length);
        }

        fillStatusSeparator(textBuffer, brl.buffer);
      }

      if (!(writeStatusCells() && writeBrailleWindow(&brl, textBuffer, scr.quality))) brl.hasFailed = 1;
    }

    api.releaseDriver();
  }

  resetAllBlinkDescriptors();
  logMessage(LOG_CATEGORY(UPDATE_EVENTS), "finished");
}

static void setUpdateAlarm (void);
static AsyncHandle updateAlarm;
static int updateSuspendCount;

static TimeValue updateTime;
static TimeValue earliestTime;

static void
enforceEarliestTime (void) {
  if (compareTimeValues(&updateTime, &earliestTime) < 0) {
    updateTime = earliestTime;
  }
}

static void
setUpdateDelay (int delay) {
  getMonotonicTime(&earliestTime);
  adjustTimeValue(&earliestTime, delay);
  enforceEarliestTime();
}

static void
setUpdateTime (int delay, const TimeValue *from, int ifEarlier) {
  TimeValue time;

  if (from) {
    time = *from;
  } else {
    getMonotonicTime(&time);
  }

  adjustTimeValue(&time, delay);

  if (!ifEarlier || (millisecondsBetween(&updateTime, &time) < 0)) {
    updateTime = time;
    enforceEarliestTime();
  }
}

void
scheduleUpdateIn (const char *reason, int delay) {
  setUpdateTime(delay, NULL, 1);
  if (updateAlarm) asyncResetAlarmTo(updateAlarm, &updateTime);
  logMessage(LOG_CATEGORY(UPDATE_EVENTS), "scheduled: %s", reason);
}

void
scheduleUpdate (const char *reason) {
  scheduleUpdateIn(reason, 0);
}

ASYNC_ALARM_CALLBACK(handleUpdateAlarm) {
  asyncDiscardHandle(updateAlarm);
  updateAlarm = NULL;

  suspendUpdates();
  setUpdateTime((pollScreen()? SCREEN_UPDATE_POLL_INTERVAL: (SECS_PER_DAY * MSECS_PER_SEC)),
                parameters->now, 0);

  {
    int oldColumn = ses->winx;
    int oldRow = ses->winy;

    doUpdate();

    if ((ses->winx != oldColumn) || (ses->winy != oldRow)) {
      reportBrailleWindowMoved();
    }
  }

  setUpdateDelay(MAX((brl.writeDelay + 1), UPDATE_SCHEDULE_DELAY));
  brl.writeDelay = 0;

  resumeUpdates(0);
}

static void
setUpdateAlarm (void) {
  if (!updateSuspendCount && !updateAlarm) {
    asyncNewAbsoluteAlarm(&updateAlarm, &updateTime, handleUpdateAlarm, NULL);
  }
}

static ReportListenerInstance *updateBrailleDeviceOnlineListener = NULL;

REPORT_LISTENER(handleUpdateBrailleDeviceOnline) {
  scheduleUpdate("braille online");
}

void
beginUpdates (void) {
  logMessage(LOG_CATEGORY(UPDATE_EVENTS), "begin");

  setUpdateDelay(0);
  setUpdateTime(0, NULL, 0);

  updateAlarm = NULL;
  updateSuspendCount = 0;

  oldwinx = -1;
  oldwiny = -1;

#ifdef ENABLE_SPEECH_SUPPORT
  wasAutospeaking = 0;
#endif /* ENABLE_SPEECH_SUPPORT */

  updateBrailleDeviceOnlineListener = registerReportListener(REPORT_BRAILLE_DEVICE_ONLINE, handleUpdateBrailleDeviceOnline, NULL);
}

void
suspendUpdates (void) {
  if (updateAlarm) {
    asyncCancelRequest(updateAlarm);
    updateAlarm = NULL;
  }

  updateSuspendCount += 1;
  logMessage(LOG_CATEGORY(UPDATE_EVENTS), "suspend: %u", updateSuspendCount);
}

void
resumeUpdates (int refresh) {
  if (!--updateSuspendCount) {
    setUpdateAlarm();
    if (refresh) scheduleUpdate("updates resumed");
  }

  logMessage(LOG_CATEGORY(UPDATE_EVENTS), "resume: %u", updateSuspendCount);
}
