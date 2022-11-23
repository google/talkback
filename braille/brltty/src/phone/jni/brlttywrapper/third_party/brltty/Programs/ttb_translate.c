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

#include "log.h"
#include "file.h"
#include "charset.h"
#include "ttb.h"
#include "ttb_internal.h"
#include "brl_dots.h"

static const unsigned char internalTextTableBytes[] = {
#include "text.auto.h"
};

static TextTable internalTextTable = {
  .header.bytes = internalTextTableBytes,
  .size = 0
};

TextTable *textTable = &internalTextTable;

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
getUnicodeCellEntry (TextTable *table, wchar_t character) {
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

typedef struct {
  TextTable *const table;
  unsigned char dots;
} SetBrailleRepresentationData;

static int
setBrailleRepresentation (wchar_t character, void *data) {
  SetBrailleRepresentationData *sbr = data;
  const unsigned char *cell = getUnicodeCellEntry(sbr->table, character);

  if (cell) {
    sbr->dots = *cell;
    return 1;
  }

  return 0;
}

unsigned char
convertCharacterToDots (TextTable *table, wchar_t character) {
  switch (character & ~UNICODE_CELL_MASK) {
    case UNICODE_BRAILLE_ROW:
      return character & UNICODE_CELL_MASK;

    case 0XF000: {
      wint_t wc = convertCharToWchar(character & UNICODE_CELL_MASK);
      if (wc == WEOF) break;
      character = wc;
      /* fall through */
    }

    default: {
      {
        unsigned int iterationLimit = 0X10;
        wchar_t characterEncountered[iterationLimit];
        unsigned int iterationNumber = 0;

        while (iterationNumber < iterationLimit) {
          if (wmemchr(characterEncountered, character, iterationNumber)) break;
          characterEncountered[iterationNumber++] = character;
          const UnicodeRowEntry *row = getUnicodeRowEntry(table, character);

          if (row) {
            unsigned int cellNumber = UNICODE_CELL_NUMBER(character);

            if (BITMASK_TEST(row->cellDefined, cellNumber)) {
              return row->cells[cellNumber];
            }

            if (BITMASK_TEST(row->cellAliased, cellNumber)) {
              const TextTableAliasEntry *alias = findTextTableAlias(table, character);

              if (alias) {
                character = alias->to;
                continue;
              }
            }
          }

          break;
        }
      }

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

  {
    const unsigned char *cell;

    if ((cell = getUnicodeCellEntry(table, UNICODE_REPLACEMENT_CHARACTER))) return *cell;
    if ((cell = getUnicodeCellEntry(table, WC_C('?')))) return *cell;
  }

  return BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6 | BRL_DOT_7 | BRL_DOT_8;
}

wchar_t
convertDotsToCharacter (TextTable *table, unsigned char dots) {
  const TextTableHeader *header = table->header.fields;
  if (BITMASK_TEST(header->dotsCharacterDefined, dots)) return header->dotsToCharacter[dots];
  return UNICODE_REPLACEMENT_CHARACTER;
}

int
replaceTextTable (const char *directory, const char *name) {
  TextTable *newTable = NULL;

  if (name) {
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

    textTable = newTable;
    destroyTextTable(oldTable);
    return 1;
  }

  logMessage(LOG_ERR, "%s: %s", gettext("cannot load text table"), name);
  return 0;
}
