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
#include <ctype.h>

#include "log.h"
#include "strfmt.h"
#include "alert.h"
#include "brl_cmds.h"
#include "unicode.h"
#include "ascii.h"
#include "scr.h"
#include "core.h"

void
alertLineSkipped (unsigned int *count) {
  const unsigned int interval = 4;

  if (!*count) {
    alert(ALERT_SKIP_FIRST);
  } else if (*count <= interval) {
    alert(ALERT_SKIP_ONE);
  } else if (!(*count % interval)) {
    alert(ALERT_SKIP_SEVERAL);
  }

  *count += 1;
}

int
isTextOffset (int arg, int *first, int *last, int relaxed) {
  int y = arg / brl.textColumns;
  if (y >= brl.textRows) return 0;
  if ((ses->winy + y) >= scr.rows) return 0;

  int x = arg % brl.textColumns;
  if (x < textStart) return 0;
  if ((x -= textStart) >= textCount) return 0;

  if (isContracted) {
    BrailleRowDescriptor *brd = getBrailleRowDescriptor(y);
    if (!brd) return 0;

    int *offsets = brd->contracted.offsets.array;
    if (!offsets) return 0;

    int start = 0;
    int end = 0;

    {
      int textIndex = 0;

      while (textIndex < brd->contracted.length) {
        int cellIndex = offsets[textIndex];

        if (cellIndex != CTB_NO_OFFSET) {
          if (cellIndex > x) {
            end = textIndex - 1;
            break;
          }

          start = textIndex;
        }

        textIndex += 1;
      }

      if (textIndex == brd->contracted.length) end = textIndex - 1;
    }

    if (first) *first = start;
    if (last) *last = end;
  } else {
    if ((ses->winx + x) >= scr.cols) {
      if (!relaxed) return 0;
      x = scr.cols - ses->winx - 1;
    }

    if (prefs.wordWrap) {
      int length = getWordWrapLength(ses->winy, ses->winx, textCount);
      if (length > textCount) length = textCount;
      if (x >= length) x = length - 1;
    }

    if (first) *first = x;
    if (last) *last = x;
  }

  return 1;
}

int
getCharacterCoordinates (int arg, int *row, int *first, int *last, int relaxed) {
  if (arg == BRL_MSK_ARG) {
    if (!SCR_CURSOR_OK()) return 0;
    *row = scr.posy;
    if (first) *first = scr.posx;
    if (last) *last = scr.posx;
  } else {
    if (!isTextOffset(arg, first, last, relaxed)) return 0;
    if (row) *row = ses->winy;
    if (first) *first += ses->winx;
    if (last) *last += ses->winx;
  }

  return 1;
}

static ScreenCharacter
getScreenCharacter (int column, int row) {
  ScreenCharacter character;
  readScreen(column, row, 1, 1, &character);
  return character;
}

STR_BEGIN_FORMATTER(formatCharacterDescription, int column, int row)
  ScreenCharacter character = getScreenCharacter(column, row);

  {
    char name[0X40];

    if (getCharacterName(character.text, name, sizeof(name))) {
      {
        size_t length = strlen(name);
        for (int i=0; i<length; i+=1) name[i] = tolower(name[i]);
      }

      STR_PRINTF(" %s: ", name);
    }
  }

  {
    uint32_t text = character.text;
    STR_PRINTF("U+%04" PRIX32 " (%" PRIu32 "):", text, text);
  }

  {
    STR_PRINTF(" ");

    static const char *const colours[] = {
      /*      */ strtext("black"),
      /*    B */ strtext("blue"),
      /*   G  */ strtext("green"),
      /*   GB */ strtext("cyan"),
      /*  R   */ strtext("red"),
      /*  R B */ strtext("magenta"),
      /*  RG  */ strtext("brown"),
      /*  RGB */ strtext("light grey"),
      /* L    */ strtext("dark grey"),
      /* L  B */ strtext("light blue"),
      /* L G  */ strtext("light green"),
      /* L GB */ strtext("light cyan"),
      /* LR   */ strtext("light red"),
      /* LR B */ strtext("light magenta"),
      /* LRG  */ strtext("yellow"),
      /* LRGB */ strtext("white")
    };

    unsigned char attributes = character.attributes;
    const char *foreground = gettext(colours[attributes & SCR_MASK_FG]);
    const char *background = gettext(colours[(attributes & SCR_MASK_BG) >> 4]);

    // xgettext: This phrase describes the colour of a character on the screen.
    // xgettext: %1$s is the (already translated) foreground colour.
    // xgettext: %2$s is the (already translated) background colour.
    STR_PRINTF(gettext("%1$s on %2$s"), foreground, background);
  }

  if (character.attributes & SCR_ATTR_BLINK) {
    STR_PRINTF(" %s", gettext("blinking"));
  }
STR_END_FORMATTER

static const char *const phoneticWords[] = {
  [' '] = "space",

  ['a'] = "alpha",
  ['b'] = "bravo",
  ['c'] = "charlie",
  ['d'] = "delta",
  ['e'] = "echo",
  ['f'] = "foxtrot",
  ['g'] = "golf",
  ['h'] = "hotel",
  ['i'] = "india",
  ['j'] = "juliet",
  ['k'] = "kilo",
  ['l'] = "lima",
  ['m'] = "mike",
  ['n'] = "november",
  ['o'] = "oscar",
  ['p'] = "papa",
  ['q'] = "quebec",
  ['r'] = "romeo",
  ['s'] = "sierra",
  ['t'] = "tango",
  ['u'] = "uniform",
  ['v'] = "victor",
  ['w'] = "whiskey",
  ['x'] = "x-ray",
  ['y'] = "yankee",
  ['z'] = "zulu",

  ['0'] = "zero",
  ['1'] = "one",
  ['2'] = "two",
  ['3'] = "three",
  ['4'] = "four",
  ['5'] = "five",
  ['6'] = "six",
  ['7'] = "seven",
  ['8'] = "eight",
  ['9'] = "nine",

  ['+'] = "plus",
  ['='] = "equals",
  ['<'] = "less than",
  ['>'] = "greater than",

  ['('] = "left parenthesis",
  [')'] = "right parenthesis",
  ['['] = "left bracket",
  [']'] = "right bracket",
  ['{'] = "left brace",
  ['}'] = "right brace",

  ['"'] = "quote",
  ['\''] = "apostrophe",
  [','] = "comma",
  [';'] = "semicolon",
  [':'] = "colon",
  ['.'] = "period",
  ['!'] = "exclamation",
  ['?'] = "question",

  ['`'] = "grave",
  ['~'] = "tilde",
  ['@'] = "at",
  ['#'] = "number",
  ['$'] = "dollar",
  ['%'] = "percent",
  ['^'] = "circumflex",
  ['&'] = "ampersand",
  ['*'] = "asterisk",
  ['-'] = "dash",
  ['_'] = "underscore",

  ['/'] = "slash",
  ['\\'] = "backslash",
  ['|'] = "vertical bar",

  [ASCII_NUL] = "null",
  [ASCII_SOH] = "start of header",
  [ASCII_STX] = "start of text",
  [ASCII_ETX] = "end of text",
  [ASCII_EOT] = "end of transmission",
  [ASCII_ENQ] = "enquiry",
  [ASCII_ACK] = "acknowledgement",
  [ASCII_BEL] = "bell",
  [ASCII_BS] = "backspace",
  [ASCII_HT] = "horizontal tab",
  [ASCII_LF] = "line feed",
  [ASCII_VT] = "vertical tab",
  [ASCII_FF] = "form feed",
  [ASCII_CR] = "carriage return",
  [ASCII_SO] = "shift out",
  [ASCII_SI] = "shift in",
  [ASCII_DLE] = "data link escape",
  [ASCII_DC1] = "device control one",
  [ASCII_DC2] = "device control two",
  [ASCII_DC3] = "device control three",
  [ASCII_DC4] = "device control four",
  [ASCII_NAK] = "negative acknowledgement",
  [ASCII_SYN] = "synchronous idle",
  [ASCII_ETB] = "end of transmission block",
  [ASCII_CAN] = "cancel",
  [ASCII_EM] = "end of medium",
  [ASCII_SUB] = "substitute",
  [ASCII_ESC] = "escape",
  [ASCII_FS] = "file separator",
  [ASCII_GS] = "group separator",
  [ASCII_RS] = "record separator",
  [ASCII_US] = "unit separator",
  [ASCII_DEL] = "delete",
};

static const char *
getPhoneticWord (wchar_t character) {
  if (character >= ARRAY_COUNT(phoneticWords)) return NULL;
  return phoneticWords[character];
}

STR_BEGIN_FORMATTER(formatPhoneticPhrase, int column, int row)
  wchar_t character = getScreenCharacter(column, row).text;

  wchar_t characters[0X10];
  size_t characterCount = decomposeCharacter(character, characters, ARRAY_COUNT(characters));

  if (!characterCount) {
    characters[0] = character;
    characterCount = 1;
  }

  for (unsigned int characterIndex=0; characterIndex<characterCount; characterIndex+=1) {
    if (characterIndex > 0) {
      STR_PRINTF("%s ", ((characterIndex == 1)? " with": ","));
    }

    character = characters[characterIndex];
    const char *word = getPhoneticWord(character);
    char nameBuffer[0X40];

    if (!word) {
      if (iswupper(character)) {
        wchar_t lowercase = towlower(character);
        word = getPhoneticWord(lowercase);

        if (word) {
          STR_PRINTF("cap ");
          character = lowercase;
        }
      }
    }

    if (!word) {
      if (getCharacterName(character, nameBuffer, sizeof(nameBuffer))) {
        word = nameBuffer;

        {
          char *byte = nameBuffer;

          while (*byte) {
            *byte = tolower((unsigned char)*byte);
            byte += 1;
          }
        }

        {
          const char *space = strchr(word, ' ');

          if (space) {
            size_t length = space - word + 1;
            if (memcmp(word,  "combining ", length) == 0) word += length;
          }
        }
      }
    }

    if (word) {
      if (STR_LENGTH > 0) STR_PRINTF(" ");
      STR_PRINTF("%s", word);
    }
  }
STR_END_FORMATTER
