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

#include "ttb.h"
#include "ttb_internal.h"
#include "ttb_compile.h"

static int inUcsBlock;

static int
getUnicodeCharacter (DataFile *file, wchar_t *character, const char *description) {
  DataOperand string;

  if (getDataOperand(file, &string, description)) {
    if (string.length > 2) {
      if ((string.characters[0] == WC_C('U')) &&
          (string.characters[1] == WC_C('+'))) {
        const wchar_t *digit = &string.characters[2];
        int length = string.length - 2;

        *character = 0;
        while (length) {
          int value;
          int shift;
          if (!isHexadecimalDigit(*digit++, &value, &shift)) break;

          *character <<= shift;
          *character |= value;
          length -= 1;
        }
        if (!length) return 1;
      }
    }

    reportDataError(file, "invalid Unicode character: %.*" PRIws,
                    string.length, string.characters);
  }

  return 0;
}

static int
testBrailleRepresentation (DataFile *file, wchar_t representation, unsigned char *dots) {
  if ((representation & ~UNICODE_CELL_MASK) == UNICODE_BRAILLE_ROW) {
    *dots = representation & UNICODE_CELL_MASK;
    return 1;
  } else {
    reportDataError(file, "invalid braille representation");
  }

  return 0;
}

static DATA_OPERANDS_PROCESSOR(processEncodingOperands) {
  DataOperand encoding;

  if (getDataOperand(file, &encoding, "character encoding name")) {
    if (!isKeyword(WS_C("UTF-8"), encoding.characters, encoding.length)) {
      reportDataError(file, "unsupported character encoding: %.*" PRIws,
                      encoding.length, encoding.characters);
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processDelegateOperands) {
  DataOperand type;

  if (getDataOperand(file, &type, "delegate type")) {
    if (isKeyword(WS_C("FILE"), type.characters, type.length)) {
      DataOperand name;

      if (getDataOperand(file, &name, "file name")) {
        return includeDataFile(file, name.characters, name.length);
      }
    } else {
      return includeDataFile(file, type.characters, type.length);
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processUcsBlockOperands) {
  DataOperand action;

  if (getDataOperand(file, &action, "UCS block action")) {
    const wchar_t *expected = inUcsBlock? WS_C("END"): WS_C("START");

    if (isKeyword(expected, action.characters, action.length)) {
      inUcsBlock = !inUcsBlock;
    } else {
      reportDataError(file, "unexpected UCS block action: %.*" PRIws " (expecting %" PRIws ")",
                      action.length, action.characters, expected);
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processUcsCharOperands) {
  TextTableData *ttd = data;
  DataOperand string;

  if (getDataOperand(file, &string, "character string")) {
    if (string.length == 1) {
      DataOperand representation;

      if (getDataOperand(file, &representation, "braille representation")) {
        if (representation.length == 1) {
          unsigned char dots;

          if (testBrailleRepresentation(file, representation.characters[0], &dots)) {
            if (!setTextTableCharacter(ttd, string.characters[0], dots)) return 0;
          }
        } else {
          reportDataError(file, "multi-cell braille representation not supported");
        }
      }
    } else {
      reportDataError(file, "multi-character string not supported");
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processUnicodeCharOperands) {
  TextTableData *ttd = data;
  wchar_t character;

  if (getUnicodeCharacter(file, &character, "character")) {
    wchar_t representation;

    if (getUnicodeCharacter(file, &representation, "braille representation")) {
      unsigned char dots;

      if (testBrailleRepresentation(file, representation, &dots)) {
        if (!setTextTableCharacter(ttd, character, dots)) return 0;
      }
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processGnomeBrailleOperands) {
  if (inUcsBlock) {
    BEGIN_DATA_DIRECTIVE_TABLE
      {.name=WS_C("UCS-BLOCK"), .processor=processUcsBlockOperands},
      {.name=NULL, .processor=processUcsCharOperands},
    END_DATA_DIRECTIVE_TABLE

    return processDirectiveOperand(file, &directives, "gnome braille UCS block directive", data);
  } else {
    BEGIN_DATA_DIRECTIVE_TABLE
      {.name=WS_C("ENCODING"), .processor=processEncodingOperands},
  //  {.name=WS_C("NAME"), .processor=processNameOperands},
  //  {.name=WS_C("LOCALES"), .processor=processLocalesOperands},
  //  {.name=WS_C("UCS-SUFFIX"), .processor=processUcsSuffixOperands},
      {.name=WS_C("DELEGATE"), .processor=processDelegateOperands},
  //  {.name=WS_C("UTF8-STRING"), .processor=processUtf8StringOperands},
      {.name=WS_C("UCS-BLOCK"), .processor=processUcsBlockOperands},
      {.name=WS_C("UCS-CHAR"), .processor=processUcsCharOperands},
      {.name=WS_C("UNICODE-CHAR"), .processor=processUnicodeCharOperands},
  //  {.name=WS_C("UNKNOWN-CHAR"), .processor=processUnknownCharOperands},
    END_DATA_DIRECTIVE_TABLE

    return processDirectiveOperand(file, &directives, "gnome braille main directive", data);
  }
}

TextTableData *
processGnomeBrailleStream (FILE *stream, const char *name) {
  TextTableData *ttd;

  inUcsBlock = 0;
  if ((ttd = processTextTableLines(stream, name, processGnomeBrailleOperands))) {
    if (inUcsBlock) {
      reportDataError(NULL, "unterminated UCS block");
    }
  };

  return ttd;
}
