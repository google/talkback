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

#include "file.h"
#include "ttb.h"
#include "ttb_internal.h"
#include "ttb_compile.h"

static int
getByteOperand (DataFile *file, unsigned char *byte) {
  DataString string;
  const char *description = "local character";

  if (getDataString(file, &string, 1, description)) {
    if ((string.length == 1) && iswLatin1(string.characters[0])) {
      *byte = string.characters[0];
      return 1;
    } else {
      reportDataError(file, "invalid %s: %.*" PRIws,
                      description, string.length, string.characters);
    }
  }

  return 0;
}

static const char characterDescription[] = "Unicode character";

static int
isCharacterOperand (
  DataFile *file, wchar_t *character,
  const wchar_t *characters, int length
) {
  if (length == 1) {
    wchar_t wc = characters[0];

    if (!(wc & ~UNICODE_CHARACTER_MASK)) {
      *character = wc;
      return 1;
    } else {
      reportDataError(file, "%s out of range: %.*" PRIws,
                      characterDescription, length, characters);
    }
  } else {
    reportDataError(file, "not a single %s: %.*" PRIws,
                    characterDescription, length, characters);
  }

  return 0;
}

static int
getCharacterOperand (DataFile *file, wchar_t *character) {
  DataString string;

  if (getDataString(file, &string, 0, characterDescription)) {
    if (isCharacterOperand(file, character, string.characters, string.length)) {
      return 1;
    }
  }

  return 0;
}

static int
getDotsOperand (DataFile *file, unsigned char *dots) {
  if (findDataOperand(file, "cell")) {
    wchar_t character;

    if (getDataCharacter(file, &character)) {
      int noDots = 0;
      wchar_t enclosed = (character == WC_C('('))? WC_C(')'):
                         0;
      *dots = 0;

      if (!enclosed) {
        if (wcschr(WS_C("0"), character)) {
          noDots = 1;
        } else {
          ungetDataCharacters(file, 1);
        }
      }

      while (getDataCharacter(file, &character)) {
        int space = iswspace(character);

        if (enclosed) {
          if (character == enclosed) {
            enclosed = 0;
            break;
          }

          if (space) continue;
        } else if (space) {
          ungetDataCharacters(file, 1);
          break;
        }

        {
          int dot;

          if (noDots || !brlDotNumberToIndex(character, &dot)) {
            reportDataError(file, "invalid dot number: %.1" PRIws, &character);
            return 0;
          }

          {
            unsigned char bit = brlDotBits[dot];

            if (*dots & bit) {
              reportDataError(file, "duplicate dot number: %.1" PRIws, &character);
              return 0;
            }

            *dots |= bit;
          }
        }
      }

      if (enclosed) {
        reportDataError(file, "incomplete cell");
        return 0;
      }

      return 1;
    }
  }

  return 0;
}

static DATA_OPERANDS_PROCESSOR(processAliasOperands) {
  TextTableData *ttd = data;
  wchar_t from;

  if (getCharacterOperand(file, &from)) {
    wchar_t to;

    if (getCharacterOperand(file, &to)) {
      if (!addTextTableAlias(ttd, from, to)) return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processByteOperands) {
  TextTableData *ttd = data;
  unsigned char byte;

  if (getByteOperand(file, &byte)) {
    unsigned char dots;

    if (getDotsOperand(file, &dots)) {
      if (!setTextTableByte(ttd, byte, dots)) return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processCharOperands) {
  TextTableData *ttd = data;
  wchar_t character;

  if (getCharacterOperand(file, &character)) {
    unsigned char dots;

    if (getDotsOperand(file, &dots)) {
      if (!setTextTableCharacter(ttd, character, dots)) return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processGlyphOperands) {
  TextTableData *ttd = data;
  wchar_t character;

  if (getCharacterOperand(file, &character)) {
    unsigned char dots;

    if (getDotsOperand(file, &dots)) {
      if (!setTextTableGlyph(ttd, character, dots)) return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processInputOperands) {
  TextTableData *ttd = data;
  wchar_t character;

  if (getCharacterOperand(file, &character)) {
    unsigned char dots;

    if (getDotsOperand(file, &dots)) {
      if (!setTextTableInput(ttd, character, dots)) return 0;
    }
  }

  return 1;
}

static DATA_CONDITION_TESTER(testGlyphDefined) {
  TextTableData *ttd = data;

  wchar_t character;
  if (!isCharacterOperand(file, &character, identifier->characters, identifier->length)) return 0;

  UnicodeRowEntry *row = getUnicodeRowEntry(ttd, character, 0);
  if (!row) return 0;

  unsigned int cellNumber = UNICODE_CELL_NUMBER(character);
  return !!BITMASK_TEST(row->cellDefined, cellNumber);
}

static int
processGlyphTestOperands (DataFile *file, int not, void *data) {
  return processConditionOperands(file, testGlyphDefined, not, characterDescription, data);
}

static DATA_OPERANDS_PROCESSOR(processIfGlyphOperands) {
  return processGlyphTestOperands(file, 0, data);
}

static DATA_OPERANDS_PROCESSOR(processIfNotGlyphOperands) {
  return processGlyphTestOperands(file, 1, data);
}

static const char inputDescription[] = "dot number(s)";

static DATA_CONDITION_TESTER(testInputDefined) {
  TextTableData *ttd = data;

  ByteOperand cells;
  if (!parseCellsOperand(file, &cells, identifier->characters, identifier->length)) return 0;

  if (cells.length != 1) {
    reportDataError(file, "not a single %s: %.*" PRIws,
                    inputDescription, identifier->length, identifier->characters);

    return 0;
  }

  const TextTableHeader *header = getTextTableHeader(ttd);
  return !!BITMASK_TEST(header->inputCharacterDefined, cells.bytes[0]);
}

static int
processInputTestOperands (DataFile *file, int not, void *data) {
  return processConditionOperands(file, testInputDefined, not, inputDescription, data);
}

static DATA_OPERANDS_PROCESSOR(processIfInputOperands) {
  return processInputTestOperands(file, 0, data);
}

static DATA_OPERANDS_PROCESSOR(processIfNotInputOperands) {
  return processInputTestOperands(file, 1, data);
}

static DATA_OPERANDS_PROCESSOR(processNativeTextTableOperands) {
  BEGIN_DATA_DIRECTIVE_TABLE
    DATA_NESTING_DIRECTIVES,
    DATA_VARIABLE_DIRECTIVES,
    DATA_CONDITION_DIRECTIVES,
    {.name=WS_C("alias"), .processor=processAliasOperands},
    {.name=WS_C("byte"), .processor=processByteOperands},
    {.name=WS_C("char"), .processor=processCharOperands},
    {.name=WS_C("glyph"), .processor=processGlyphOperands},
    {.name=WS_C("input"), .processor=processInputOperands},
    {.name=WS_C("ifglyph"), .processor=processIfGlyphOperands, .unconditional=1},
    {.name=WS_C("ifnotglyph"), .processor=processIfNotGlyphOperands, .unconditional=1},
    {.name=WS_C("ifinput"), .processor=processIfInputOperands, .unconditional=1},
    {.name=WS_C("ifnotinput"), .processor=processIfNotInputOperands, .unconditional=1},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "text table directive", data);
}

TextTableData *
processTextTableStream (FILE *stream, const char *name) {
  return processTextTableLines(stream, name, processNativeTextTableOperands);
}

TextTable *
compileTextTable (const char *name) {
  TextTable *table = NULL;
  FILE *stream;

  if ((stream = openDataFile(name, "r", 0))) {
    TextTableData *ttd;

    if ((ttd = processTextTableStream(stream, name))) {
      table = makeTextTable(ttd);

      destroyTextTableData(ttd);
    }

    fclose(stream);
  }

  return table;
}
