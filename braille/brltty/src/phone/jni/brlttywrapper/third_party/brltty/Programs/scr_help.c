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

#include "log.h"
#include "strfmt.h"
#include "scr.h"
#include "scr_help.h"

typedef struct {
  wchar_t *characters;
  size_t length;
} HelpLineEntry;

typedef struct {
  HelpLineEntry *lineTable;
  unsigned int lineLimit;

  unsigned int lineCount;
  size_t lineLength;

  unsigned char cursorRow;
  unsigned char cursorColumn;
} HelpPageEntry;

static HelpPageEntry *pageTable;
static unsigned int pageLimit;
static unsigned int pageCount;
static unsigned int pageIndex;

static void
initializePageTable (void) {
  pageTable = NULL;
  pageLimit = 0;
  pageCount = 0;
  pageIndex = 0;
}

static void
initializePage (HelpPageEntry *page) {
  page->lineTable = NULL;
  page->lineLimit = 0;

  page->lineCount = 0;
  page->lineLength = 1;

  page->cursorRow = 0;
  page->cursorColumn = 0;
}

static unsigned int
addPage (void) {
  if (pageCount == pageLimit) {
    unsigned int newLimit = pageLimit + 1;
    HelpPageEntry *newTable = realloc(pageTable, ARRAY_SIZE(newTable, newLimit));

    if (!newTable) {
      logMallocError();
      return 0;
    }

    pageTable = newTable;
    pageLimit = newLimit;
  }

  {
    HelpPageEntry *page = &pageTable[pageCount];
    initializePage(page);
  }

  return pageCount += 1;
}

static void
clearPage (HelpPageEntry *page) {
  if (page->lineTable) {
    while (page->lineCount) {
      HelpLineEntry *line = &page->lineTable[--page->lineCount];

      if (line->characters) free(line->characters);
    }

    free(page->lineTable);
    initializePage(page);
  }
}

static int
addLine (HelpPageEntry *page, const wchar_t *characters) {
  if (page->lineCount == page->lineLimit) {
    unsigned int newLimit = page->lineLimit? page->lineLimit<<1: 0X40;
    HelpLineEntry *newTable = realloc(page->lineTable, ARRAY_SIZE(newTable, newLimit));

    if (!newTable) {
      logMallocError();
      return 0;
    }

    page->lineTable = newTable;
    page->lineLimit = newLimit;
  }

  {
    HelpLineEntry *line = &page->lineTable[page->lineCount];
    size_t length = wcslen(characters);
    if ((line->length = length) > page->lineLength) page->lineLength = length;

    if (!(line->characters = malloc(ARRAY_SIZE(line->characters, length)))) {
      logMallocError();
      return 0;
    }

    wmemcpy(line->characters, characters, length);
  }

  page->lineCount += 1;
  return 1;
}

static HelpPageEntry *
getPage (void) {
  if (pageIndex < pageCount) return &pageTable[pageIndex];
  logMessage(LOG_WARNING, "help page index out of range: %u >= %u", pageIndex, pageCount);
  return NULL;
}

static int
construct_HelpScreen (void) {
  initializePageTable();
  return 1;
}

static void
destruct_HelpScreen (void) {
  if (pageTable) {
    while (pageCount) clearPage(&pageTable[--pageCount]);
    free(pageTable);
  }

  initializePageTable();
}

static unsigned int
addPage_HelpScreen (void) {
  return addPage();
}

static unsigned int
getPageCount_HelpScreen (void) {
  return pageCount;
}

static unsigned int
getPageNumber_HelpScreen (void) {
  return pageIndex + 1;
}

static int
setPageNumber_HelpScreen (unsigned int number) {
  if ((number < 1) || (number > pageCount)) return 0;
  pageIndex = number - 1;
  return 1;
}

static int
clearPage_HelpScreen (void) {
  HelpPageEntry *page = getPage();

  if (!page) return 0;
  clearPage(page);
  return 1;
}

static int
addLine_HelpScreen (const wchar_t *characters) {
  HelpPageEntry *page = getPage();
  if (!page) return 0;
  return addLine(page, characters);
}

static unsigned int 
getLineCount_HelpScreen (void) {
  HelpPageEntry *page = getPage();

  return page? page->lineCount: 0;
}

static int
currentVirtualTerminal_HelpScreen (void) {
  return userVirtualTerminal(pageIndex);
}

static const char *
getTitle_HelpScreen (void) {
  return gettext("Help Screen");
}

static void
describe_HelpScreen (ScreenDescription *description) {
  const HelpPageEntry *page = getPage();

  if (page) {
    description->posx = page->cursorColumn;
    description->posy = page->cursorRow;
    description->cols = page->lineLength;
    description->rows = page->lineCount;
    description->number = currentVirtualTerminal_HelpScreen();
  } else {
    description->unreadable = gettext("help screen not readable");
  }
}

static int
readCharacters_HelpScreen (const ScreenBox *box, ScreenCharacter *buffer) {
  const HelpPageEntry *page = getPage();

  if (page) {
    if (validateScreenBox(box, page->lineLength, page->lineCount)) {
      ScreenCharacter *character = buffer;
      int row;

      for (row=0; row<box->height; row+=1) {
        const HelpLineEntry *line = &page->lineTable[box->top + row];
        int column;

        for (column=0; column<box->width; column+=1) {
          int index = box->left + column;

          if (index < line->length) {
            character->text = line->characters[index];
          } else {
            character->text = WC_C(' ');
          }

          character->attributes = SCR_COLOUR_DEFAULT;
          character += 1;
        }
      }

      return 1;
    }
  }

  return 0;
}

static int
insertKey_HelpScreen (ScreenKey key) {
  HelpPageEntry *page = getPage();

  if (page) {
    switch (key) {
      case SCR_KEY_CURSOR_UP:
        if (page->cursorRow > 0) {
          page->cursorRow -= 1;
          return 1;
        }
        break;

      case SCR_KEY_CURSOR_DOWN:
        if (page->cursorRow < (page->lineCount - 1)) {
          page->cursorRow += 1;
          return 1;
        }
        break;

      case SCR_KEY_CURSOR_LEFT:
        if (page->cursorColumn > 0) {
          page->cursorColumn -= 1;
          return 1;
        }
        break;

      case SCR_KEY_CURSOR_RIGHT:
        if (page->cursorColumn < (page->lineLength - 1)) {
          page->cursorColumn += 1;
          return 1;
        }
        break;

      default:
        break;
    }
  }

  return 0;
}

static int
routeCursor_HelpScreen (int column, int row, int screen) {
  HelpPageEntry *page = getPage();
  if (!page) return 0;

  if (row != -1) {
    if ((row < 0) || (row >= page->lineCount)) return 0;
    page->cursorRow = row;
  }

  if (column != -1) {
    if ((column < 0) || (column >= page->lineLength)) return 0;
    page->cursorColumn = column;
  }

  return 1;
}

void
initializeHelpScreen (HelpScreen *help) {
  initializeBaseScreen(&help->base);
  help->base.currentVirtualTerminal = currentVirtualTerminal_HelpScreen;
  help->base.getTitle = getTitle_HelpScreen;
  help->base.describe = describe_HelpScreen;
  help->base.readCharacters = readCharacters_HelpScreen;
  help->base.insertKey = insertKey_HelpScreen;
  help->base.routeCursor = routeCursor_HelpScreen;

  help->construct = construct_HelpScreen;
  help->destruct = destruct_HelpScreen;

  help->addPage = addPage_HelpScreen;
  help->getPageCount = getPageCount_HelpScreen;
  help->getPageNumber = getPageNumber_HelpScreen;
  help->setPageNumber = setPageNumber_HelpScreen;

  help->clearPage = clearPage_HelpScreen;
  help->addLine = addLine_HelpScreen;
  help->getLineCount = getLineCount_HelpScreen;
}
