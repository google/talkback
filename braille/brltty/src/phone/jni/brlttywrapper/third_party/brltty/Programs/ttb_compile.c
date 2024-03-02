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
#include "file.h"
#include "datafile.h"
#include "dataarea.h"
#include "charset.h"
#include "ttb.h"
#include "ttb_internal.h"
#include "ttb_compile.h"

struct TextTableDataStruct {
  DataArea *area;

  struct {
    TextTableAliasEntry *array;
    size_t size;
    size_t count;
  } alias;
};

void *
getTextTableItem (TextTableData *ttd, TextTableOffset offset) {
  return getDataItem(ttd->area, offset);
}

TextTableHeader *
getTextTableHeader (TextTableData *ttd) {
  return getTextTableItem(ttd, 0);
}

static DataOffset
getUnicodeGroupOffset (TextTableData *ttd, wchar_t character, int allocate) {
  unsigned int groupNumber = UNICODE_GROUP_NUMBER(character);
  DataOffset groupOffset = getTextTableHeader(ttd)->unicodeGroups[groupNumber];

  if (!groupOffset && allocate) {
    if (!allocateDataItem(ttd->area, &groupOffset,
                          sizeof(UnicodeGroupEntry),
                          __alignof__(UnicodeGroupEntry))) {
      return 0;
    }

    getTextTableHeader(ttd)->unicodeGroups[groupNumber] = groupOffset;
  }

  return groupOffset;
}

static DataOffset
getUnicodePlaneOffset (TextTableData *ttd, wchar_t character, int allocate) {
  DataOffset groupOffset = getUnicodeGroupOffset(ttd, character, allocate);
  if (!groupOffset) return 0;

  {
    UnicodeGroupEntry *group = getDataItem(ttd->area, groupOffset);
    unsigned int planeNumber = UNICODE_PLANE_NUMBER(character);
    DataOffset planeOffset = group->planes[planeNumber];

    if (!planeOffset && allocate) {
      if (!allocateDataItem(ttd->area, &planeOffset,
                            sizeof(UnicodePlaneEntry),
                            __alignof__(UnicodePlaneEntry))) {
        return 0;
      }

      group = getDataItem(ttd->area, groupOffset);
      group->planes[planeNumber] = planeOffset;
    }

    return planeOffset;
  }
}

static DataOffset
getUnicodeRowOffset (TextTableData *ttd, wchar_t character, int allocate) {
  DataOffset planeOffset = getUnicodePlaneOffset(ttd, character, allocate);
  if (!planeOffset) return 0;

  {
    UnicodePlaneEntry *plane = getDataItem(ttd->area, planeOffset);
    unsigned int rowNumber = UNICODE_ROW_NUMBER(character);
    DataOffset rowOffset = plane->rows[rowNumber];

    if (!rowOffset && allocate) {
      if (!allocateDataItem(ttd->area, &rowOffset,
                            sizeof(UnicodeRowEntry),
                            __alignof__(UnicodeRowEntry))) {
        return 0;
      }

      plane = getDataItem(ttd->area, planeOffset);
      plane->rows[rowNumber] = rowOffset;
    }

    return rowOffset;
  }
}

UnicodeRowEntry *
getUnicodeRowEntry (TextTableData *ttd, wchar_t character, int allocate) {
  DataOffset rowOffset = getUnicodeRowOffset(ttd, character, allocate);
  if (!rowOffset) return NULL;
  return getDataItem(ttd->area, rowOffset);
}

const unsigned char *
getUnicodeCell (TextTableData *ttd, wchar_t character) {
  const UnicodeRowEntry *row = getUnicodeRowEntry(ttd, character, 0);

  if (row) {
    unsigned int cellNumber = UNICODE_CELL_NUMBER(character);
    if (BITMASK_TEST(row->cellDefined, cellNumber)) return &row->cells[cellNumber];
  }

  return NULL;
}

static void
clearTextTableInput (TextTableData *ttd, unsigned char dots, wchar_t character) {
  TextTableHeader *header = getTextTableHeader(ttd);

  if (BITMASK_TEST(header->inputCharacterDefined, dots)) {
    if (header->inputCharacters[dots] == character) {
      header->inputCharacters[dots] = 0;
      BITMASK_CLEAR(header->inputCharacterDefined, dots);
    }
  }
}

int
setTextTableInput (TextTableData *ttd, wchar_t character, unsigned char dots) {
  TextTableHeader *header = getTextTableHeader(ttd);

  if (!BITMASK_TEST(header->inputCharacterDefined, dots)) {
    header->inputCharacters[dots] = character;
    BITMASK_SET(header->inputCharacterDefined, dots);
  }

  return 1;
}

int
setTextTableGlyph (TextTableData *ttd, wchar_t character, unsigned char dots) {
  UnicodeRowEntry *row = getUnicodeRowEntry(ttd, character, 1);

  if (row) {
    unsigned int cellNumber = UNICODE_CELL_NUMBER(character);
    unsigned char *cell = &row->cells[cellNumber];

    if (!BITMASK_TEST(row->cellDefined, cellNumber)) {
      BITMASK_SET(row->cellDefined, cellNumber);
    } else if (*cell != dots) {
      clearTextTableInput(ttd, *cell, character);
    }

    *cell = dots;
    return 1;
  }

  return 0;
}

int
setTextTableCharacter (TextTableData *ttd, wchar_t character, unsigned char dots) {
  if (!setTextTableGlyph(ttd, character, dots)) return 0;
  if (!setTextTableInput(ttd, character, dots)) return 0;
  return 1;
}

void
unsetTextTableCharacter (TextTableData *ttd, wchar_t character) {
  UnicodeRowEntry *row = getUnicodeRowEntry(ttd, character, 0);

  if (row) {
    unsigned int cellNumber = UNICODE_CELL_NUMBER(character);

    if (BITMASK_TEST(row->cellDefined, cellNumber)) {
      unsigned char *cell = &row->cells[cellNumber];

      clearTextTableInput(ttd, *cell, character);
      *cell = 0;
      BITMASK_CLEAR(row->cellDefined, cellNumber);
    }
  }
}

int
setTextTableByte (TextTableData *ttd, unsigned char byte, unsigned char dots) {
  wint_t character = convertCharToWchar(byte);

  if (character != WEOF)
    if (!setTextTableCharacter(ttd, character, dots))
      return 0;

  return 1;
}

int
addTextTableAlias (TextTableData *ttd, wchar_t from, wchar_t to) {
  if (ttd->alias.count == ttd->alias.size) {
    size_t newSize = ttd->alias.size? (ttd->alias.size << 1): 0X10;
    TextTableAliasEntry *newArray;

    if (!(newArray = realloc(ttd->alias.array, ARRAY_SIZE(newArray, newSize)))) {
      logMallocError();
      return 0;
    }

    ttd->alias.array = newArray;
    ttd->alias.size = newSize;
  }

  {
    unsigned int cellNumber = UNICODE_CELL_NUMBER(from);
    UnicodeRowEntry *row = getUnicodeRowEntry(ttd, from, 1);

    if (!row) return 0;
    BITMASK_SET(row->cellAliased, cellNumber);
  }

  {
    TextTableAliasEntry *alias = &ttd->alias.array[ttd->alias.count++];

    memset(alias, 0, sizeof(*alias));
    alias->from = from;
    alias->to = to;
  }

  return 1;
}

TextTableData *
newTextTableData (void) {
  TextTableData *ttd;

  if ((ttd = malloc(sizeof(*ttd)))) {
    memset(ttd, 0, sizeof(*ttd));

    ttd->alias.array = NULL;
    ttd->alias.size = 0;
    ttd->alias.count = 0;

    if ((ttd->area = newDataArea())) {
      if (allocateDataItem(ttd->area, NULL, sizeof(TextTableHeader), __alignof__(TextTableHeader))) {
        return ttd;
      }

      destroyDataArea(ttd->area);
    }

    free(ttd);
  }

  return NULL;
}

void
destroyTextTableData (TextTableData *ttd) {
  if (ttd->alias.array) free(ttd->alias.array);
  destroyDataArea(ttd->area);
  free(ttd);
}

static int
sortTextTableAliasArray (const void *element1, const void *element2) {
  const TextTableAliasEntry *alias1 = element1;
  const TextTableAliasEntry *alias2 = element2;

  wchar_t wc1 = alias1->from;
  wchar_t wc2 = alias2->from;

  if (wc1 < wc2) return -1;
  if (wc1 > wc2) return 1;
  return 0;
}

static int
finishTextTableData (TextTableData *ttd) {
  qsort(ttd->alias.array, ttd->alias.count, sizeof(*ttd->alias.array), sortTextTableAliasArray);

  {
    DataOffset offset;

    if (!saveDataItem(ttd->area, &offset, ttd->alias.array,
                      ARRAY_SIZE(ttd->alias.array, ttd->alias.count),
                      __alignof__(*ttd->alias.array))) {
      return 0;
    }

    {
      TextTableHeader *header = getTextTableHeader(ttd);

      header->aliasArray = offset;
      header->aliasCount = ttd->alias.count;
    }
  }

  return 1;
}

TextTableData *
processTextTableLines (FILE *stream, const char *name, DataOperandsProcessor *processOperands) {
  if (setTableDataVariables(TEXT_TABLE_EXTENSION, TEXT_SUBTABLE_EXTENSION)) {
    TextTableData *ttd;

    if ((ttd = newTextTableData())) {
      const DataFileParameters parameters = {
        .processOperands = processOperands,
        .data = ttd
      };

      if (processDataStream(NULL, stream, name, &parameters)) {
        if (finishTextTableData(ttd)) {
          return ttd;
        }
      }

      destroyTextTableData(ttd);
    }
  }

  return NULL;
}

TextTable *
makeTextTable (TextTableData *ttd) {
  TextTable *table = malloc(sizeof(*table));

  if (table) {
    memset(table, 0, sizeof(*table));

    table->header.fields = getTextTableHeader(ttd);
    table->size = getDataSize(ttd->area);

    table->options.tryBaseCharacter = 1;

    {
      const unsigned char **cell = &table->cells.replacementCharacter;
      *cell = getUnicodeCell(ttd, UNICODE_REPLACEMENT_CHARACTER);
      if (!*cell) *cell = getUnicodeCell(ttd, WC_C('?'));
    }

    resetDataArea(ttd->area);
  }

  return table;
}

void
destroyTextTable (TextTable *table) {
  if (table->size) {
    free(table->header.fields);
    free(table);
  }
}

char *
ensureTextTableExtension (const char *path) {
  return ensureFileExtension(path, TEXT_TABLE_EXTENSION);
}

char *
makeTextTablePath (const char *directory, const char *name) {
  char *subdirectory = makePath(directory, TEXT_TABLES_SUBDIRECTORY);

  if (subdirectory) {
    char *file = makeFilePath(subdirectory, name, TEXT_TABLE_EXTENSION);

    free(subdirectory);
    if (file) return file;
  }

  return NULL;
}

char *
getTextTableForLocale (const char *directory) {
  return getFileForLocale(directory, makeTextTablePath);
}
