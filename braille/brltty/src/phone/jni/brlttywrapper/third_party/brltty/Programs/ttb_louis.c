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

#include "ttb.h"
#include "ttb_internal.h"
#include "ttb_compile.h"

static int
getUnicodeCharacters (DataFile *file, wchar_t *character, int num, const char *description) {
  DataOperand string;
  const wchar_t *c;
  int i;

  if (getDataOperand(file, &string, description)) {
    for (c = string.characters, i = 0; i < num; i++) {
      if (*c == '\\') {
	c++;
	switch (*c) {
	  case WC_C('\\'): character[i] = WC_C('\\'); continue;
	  case WC_C('f'): case WC_C('F'): character[i] = WC_C('\f'); continue;
	  case WC_C('n'): case WC_C('N'): character[i] = WC_C('\n'); continue;
	  case WC_C('r'): case WC_C('R'): character[i] = WC_C('\r'); continue;
	  case WC_C('s'): case WC_C('S'): character[i] = WC_C(' ' ); continue;
	  case WC_C('t'): case WC_C('T'): character[i] = WC_C('\t'); continue;
	  case WC_C('v'): case WC_C('V'): character[i] = WC_C('\v'); continue;
	  case WC_C('x'): case WC_C('X'): {
	    const wchar_t *digit = ++c;
	    int length = string.length - (digit - string.characters);

	    character[i] = 0;
	    while (length) {
	      int value;
	      int shift;
	      if (!isHexadecimalDigit(*digit, &value, &shift)) break;
	      digit++;

	      character[i] <<= shift;
	      character[i] |= value;
	      length -= 1;
	    }
            if (digit == c) goto invalid;
	    c = digit;
	    continue;
	  }
	  default:
          invalid:
	    reportDataError(file, "unknown escape sequence: %.*" PRIws,
                            (int)(c-string.characters+1), string.characters);
	}
      } else {
	character[i] = *c++;
      }
    }
    return 1;
  }

  return 0;
}

static int
getDots (DataFile *file, unsigned char *dots, const char *description) {
  DataOperand string;
  int i;

  *dots = 0;

  if (getDataOperand(file, &string, description)) {
    for (i = 0; i < string.length; i++) {
      if (string.characters[i] >= WC_C('1') && string.characters[i] <= WC_C('8')) {
	*dots |= 1 << (string.characters[i] - WC_C('1'));
      } else if (string.characters[i] == WC_C('-')) {
	reportDataError(file, "no support for multi-cell %.*" PRIws,
			string.length, string.characters);
	return 0;
      }
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processChar) {
  TextTableData *ttd = data;
  wchar_t character;

  if (getUnicodeCharacters(file, &character, 1, "character")) {
    unsigned char dots;

    if (getDots(file, &dots, "braille representation")) {
      if (!setTextTableCharacter(ttd, character, dots)) return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processUplow) {
  TextTableData *ttd = data;
  wchar_t characters[2];

  if (getUnicodeCharacters(file, characters, 2, "characters")) {
    unsigned char dots;

    if (getDots(file, &dots, "braille representation")) {
      if (!setTextTableCharacter(ttd, characters[0], dots)) return 0;
      if (!setTextTableCharacter(ttd, characters[1], dots)) return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processInclude) {
  reportDataError(file, "no support for include");
  return 1;
}

static DATA_OPERANDS_PROCESSOR(processLibLouisOperands) {
  BEGIN_DATA_DIRECTIVE_TABLE
    {.name=WS_C("space"), .processor=processChar},
    {.name=WS_C("punctuation"), .processor=processChar},
    {.name=WS_C("digit"), .processor=processChar},
    {.name=WS_C("uplow"), .processor=processUplow},
    {.name=WS_C("letter"), .processor=processChar},
    {.name=WS_C("lowercase"), .processor=processChar},
    {.name=WS_C("uppercase"), .processor=processChar},
    {.name=WS_C("litdigit"), .processor=processChar},
    {.name=WS_C("sign"), .processor=processChar},
    {.name=WS_C("math"), .processor=processChar},
    {.name=WS_C("decpoint"), .processor=processChar},
    {.name=WS_C("hyphen"), .processor=processChar},
    {.name=WS_C("include"), .processor=processInclude},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "lib louis directive", data);
}

TextTableData *
processLibLouisStream (FILE *stream, const char *name) {
  return processTextTableLines(stream, name, processLibLouisOperands);
}
