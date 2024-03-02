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

#ifndef BRLTTY_INCLUDED_TTB_INTERNAL
#define BRLTTY_INCLUDED_TTB_INTERNAL

#include "bitmask.h"
#include "unicode.h"
#include "dataarea.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef uint32_t TextTableOffset;

#define CHARSET_BYTE_BITS 8
#define CHARSET_BYTE_COUNT (1 << CHARSET_BYTE_BITS)
#define CHARSET_BYTE_MAXIMUM (CHARSET_BYTE_COUNT - 1)

typedef struct {
  unsigned char cells[UNICODE_CELLS_PER_ROW];
  BITMASK(cellDefined, UNICODE_CELLS_PER_ROW, char);
  BITMASK(cellAliased, UNICODE_CELLS_PER_ROW, char);
} UnicodeRowEntry;

typedef struct {
  TextTableOffset rows[UNICODE_ROWS_PER_PLANE];
} UnicodePlaneEntry;

typedef struct {
  TextTableOffset planes[UNICODE_PLANES_PER_GROUP];
} UnicodeGroupEntry;

typedef struct {
  wchar_t from;
  wchar_t to;
} TextTableAliasEntry;

typedef struct {
  TextTableOffset unicodeGroups[UNICODE_GROUP_COUNT];
  wchar_t inputCharacters[0X100];
  BITMASK(inputCharacterDefined, 0X100, char);
  DataOffset aliasArray;
  uint32_t aliasCount;
} TextTableHeader;

struct TextTableStruct {
  union {
    TextTableHeader *fields;
    const unsigned char *bytes;
  } header;

  size_t size;

  struct {
    unsigned char tryBaseCharacter;
  } options;

  struct {
    const unsigned char *replacementCharacter;
  } cells;
};

extern const TextTableAliasEntry *locateTextTableAlias (
  wchar_t character, const TextTableAliasEntry *array, size_t count
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_TTB_INTERNAL */
