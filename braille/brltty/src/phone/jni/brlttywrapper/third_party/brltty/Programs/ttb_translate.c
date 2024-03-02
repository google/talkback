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

#include "log.h"
#include "lock.h"
#include "file.h"
#include "charset.h"
#include "ttb.h"
#include "ttb_internal.h"
#include "brl_dots.h"
#include "brl_types.h"
#include "prefs.h"

static const unsigned char internalTextTableBytes[] = {
#include "ttb.auto.h"
};

static TextTable internalTextTable = {
  .header.bytes = internalTextTableBytes,
  .size = 0
};

TextTable *textTable = &internalTextTable;

static LockDescriptor *
getTextTableLock (void) {
  static LockDescriptor *lock = NULL;
  return getLockDescriptor(&lock, "text-table");
}

void
lockTextTable (void) {
  obtainExclusiveLock(getTextTableLock());
}

void
unlockTextTable (void) {
  releaseLock(getTextTableLock());
}

static inline const void *
getTextTableItem (TextTable *table, TextTableOffset offset) {
  return &table->header.bytes[offset];
}

static inline const UnicodeGroupEntry *
getUnicodeGroupEntry (TextTable *table, wchar_t character) {
  TextTableOffset offset = table->header.fields->unicodeGroups[UNICODE_GROUP_NUMBER(character)];
  if (offset) return getTextTableItem(table, offset);
  return NULL;
}

static inline const UnicodePlaneEntry *
getUnicodePlaneEntry (TextTable *table, wchar_t character) {
  const UnicodeGroupEntry *group = getUnicodeGroupEntry(table, character);

  if (group) {
    TextTableOffset offset = group->planes[UNICODE_PLANE_NUMBER(character)];
    if (offset) return getTextTableItem(table, offset);
  }

  return NULL;
}

static inline const UnicodeRowEntry *
getUnicodeRowEntry (TextTable *table, wchar_t character) {
  const UnicodePlaneEntry *plane = getUnicodePlaneEntry(table, character);

  if (plane) {
    TextTableOffset offset = plane->rows[UNICODE_ROW_NUMBER(character)];
    if (offset) return getTextTableItem(table, offset);
  }

  return NULL;
}

static inline const unsigned char *
getUnicodeCell (TextTable *table, wchar_t character) {
  const UnicodeRowEntry *row = getUnicodeRowEntry(table, character);

  if (row) {
    unsigned int cellNumber = UNICODE_CELL_NUMBER(character);
    if (BITMASK_TEST(row->cellDefined, cellNumber)) return &row->cells[cellNumber];
  }

  return NULL;
}

void
setTryBaseCharacter (TextTable *table, unsigned char yes) {
  table->options.tryBaseCharacter = yes;
}

static int
searchTextTableAlias (const void *target, const void *element) {
  const wchar_t *reference = target;
  const TextTableAliasEntry *alias = element;

  if (*reference < alias->from) return -1;
  if (*reference > alias->from) return 1;
  return 0;
}

const TextTableAliasEntry *
locateTextTableAlias (wchar_t character, const TextTableAliasEntry *array, size_t count) {
  const TextTableAliasEntry *alias = bsearch(
    &character, array, count, sizeof(*array), searchTextTableAlias
  );

  if (alias) return alias;
  return NULL;
}

static const TextTableAliasEntry *
findTextTableAlias (TextTable *table, wchar_t character) {
  const TextTableHeader *header = table->header.fields;

  return locateTextTableAlias(character, getTextTableItem(table, header->aliasArray), header->aliasCount);
}

static int
getDotsForAliasedCharacter (TextTable *table, wchar_t *character, unsigned char *dots) {
  unsigned int iterationLimit = 0X10;
  wchar_t characterEncountered[iterationLimit];
  unsigned int iterationNumber = 0;

  while (iterationNumber < iterationLimit) {
    if (wmemchr(characterEncountered, *character, iterationNumber)) break;
    characterEncountered[iterationNumber++] = *character;
    const UnicodeRowEntry *row = getUnicodeRowEntry(table, *character);

    if (row) {
      unsigned int cellNumber = UNICODE_CELL_NUMBER(*character);

      if (BITMASK_TEST(row->cellDefined, cellNumber)) {
        *dots = row->cells[cellNumber];
        return 1;
      }

      if (BITMASK_TEST(row->cellAliased, cellNumber)) {
        const TextTableAliasEntry *alias = findTextTableAlias(table, *character);

        if (alias) {
          *character = alias->to;
          continue;
        }
      }
    }

    break;
  }

  return 0;
}

typedef struct {
  TextTable *const table;
  unsigned char dots;
} SetBrailleRepresentationData;

static int
setBrailleRepresentation (wchar_t character, void *data) {
  SetBrailleRepresentationData *sbr = data;
  const unsigned char *cell = getUnicodeCell(sbr->table, character);

  if (cell) {
    sbr->dots = *cell;
    return 1;
  }

  return 0;
}

unsigned char
convertCharacterToDots (TextTable *table, wchar_t character) {
  uint32_t row = character & ~UNICODE_CELL_MASK;

  switch (row) {
#if WCHAR_MAX >= UINT16_MAX
    case 0XF000: {
      wint_t wc = convertCharToWchar(character & UNICODE_CELL_MASK);
      if (wc == WEOF) break;
      character = wc;
    }
    /* fall through */
#endif /* WCHAR_MAX >= UINT16_MAX */

    default: {
      {
        unsigned char dots;
        if (getDotsForAliasedCharacter(table, &character, &dots)) return dots;
      }

      if (character == UNICODE_REPLACEMENT_CHARACTER) break;

      if (table->options.tryBaseCharacter) {
        SetBrailleRepresentationData sbr = {
          .table = table,
          .dots = 0
        };

        if (handleBestCharacter(character, setBrailleRepresentation, &sbr)) {
          return sbr.dots;
        }
      }

      break;
    }
  }

  if (row == UNICODE_BRAILLE_ROW) {
    return character & UNICODE_CELL_MASK;
  }

  {
    const unsigned char *cell = table->cells.replacementCharacter;
    if (cell) return *cell;
  }

  return BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6 | BRL_DOT_7 | BRL_DOT_8;
}

wchar_t
convertDotsToCharacter (TextTable *table, unsigned char dots) {
  const TextTableHeader *header = table->header.fields;
  if (BITMASK_TEST(header->inputCharacterDefined, dots)) return header->inputCharacters[dots];
  return UNICODE_REPLACEMENT_CHARACTER;
}

wchar_t
convertInputToCharacter (unsigned char dots) {
  switch (prefs.brailleTypingMode) {
    case BRL_TYPING_TEXT:
      return convertDotsToCharacter(textTable, dots);

    default:
      logMessage(LOG_WARNING, "unknown braille typing mode: %u", prefs.brailleTypingMode);
      /* fall through */
    case BRL_TYPING_DOTS:
      return UNICODE_BRAILLE_ROW | dots;
  }
}

int
replaceTextTable (const char *directory, const char *name) {
  TextTable *newTable = NULL;

  if (*name) {
    char *path;

    if ((path = makeTextTablePath(directory, name))) {
      logMessage(LOG_DEBUG, "compiling text table: %s", path);

      if (!(newTable = compileTextTable(path))) {
        logMessage(LOG_ERR, "%s: %s", gettext("cannot compile text table"), path);
      }

      free(path);
    }
  } else {
    newTable = &internalTextTable;
  }

  if (newTable) {
    TextTable *oldTable = textTable;

    lockTextTable();
      textTable = newTable;
    unlockTextTable();

    destroyTextTable(oldTable);
    return 1;
  }

  logMessage(LOG_ERR, "%s: %s", gettext("cannot load text table"), name);
  return 0;
}

size_t
getTextTableRowsMask (TextTable *table, uint8_t *mask, size_t size) {
  size_t result = 0;
  memset(mask, 0, size);

  for (unsigned int groupNumber=0; groupNumber<UNICODE_GROUP_COUNT; groupNumber+=1) {
    TextTableOffset groupOffset = table->header.fields->unicodeGroups[groupNumber];

    if (groupOffset) {
      const UnicodeGroupEntry *group = getTextTableItem(table, groupOffset);

      for (unsigned int planeNumber=0; planeNumber<UNICODE_PLANES_PER_GROUP; planeNumber+=1) {
        TextTableOffset planeOffset = group->planes[planeNumber];

        if (planeOffset) {
          const UnicodePlaneEntry *plane = getTextTableItem(table, planeOffset);

          for (unsigned int rowNumber=0; rowNumber<UNICODE_ROWS_PER_PLANE; rowNumber+=1) {
            TextTableOffset rowOffset = plane->rows[rowNumber];

            if (rowOffset) {
              uint32_t row = UNICODE_CHARACTER(groupNumber, planeNumber, rowNumber, 0) >> UNICODE_ROW_SHIFT;
              uint32_t index = row / 8;
              if (index >= size) goto done;
              mask[index] |= 1 << (row % 8);
              result = index + 1;
            }
          }
        }
      }
    }
  }

done:
  return result;
}

int
getTextTableRowCells (TextTable *table, uint32_t rowIndex, uint8_t *cells, uint8_t *defined) {
  wchar_t character = rowIndex << UNICODE_ROW_SHIFT;
  const UnicodeRowEntry *row = getUnicodeRowEntry(table, character);
  if (!row) return 0;

  int maskIndex = -1;
  uint8_t maskBit = 0;

  for (unsigned int cellNumber=0; cellNumber<UNICODE_CELLS_PER_ROW; cellNumber+=1) {
    unsigned char *cell = &cells[cellNumber];
    *cell = 0;

    if (!maskBit) {
      defined[++maskIndex] = 0;
      maskBit = 1;
    }

    if (BITMASK_TEST(row->cellDefined, cellNumber)) {
      *cell = row->cells[cellNumber];
      defined[maskIndex] |= maskBit;
    } else if (BITMASK_TEST(row->cellAliased, cellNumber)) {
      wchar_t wc = character | (cellNumber << UNICODE_CELL_SHIFT);
      unsigned char dots;

      if (getDotsForAliasedCharacter(table, &wc, &dots)) {
        *cell = dots;
        defined[maskIndex] |= maskBit;
      }
    }

    maskBit <<= 1;
  }

  return 1;
}
