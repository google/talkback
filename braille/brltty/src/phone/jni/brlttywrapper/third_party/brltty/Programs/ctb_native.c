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
#include "ctb_translate.h"
#include "ttb.h"
#include "brl_dots.h"
#include "unicode.h"
#include "utf8.h"

#ifdef HAVE_ICU
#include <unicode/uchar.h>

typedef struct {
  unsigned int index;
  ULineBreak after;
  ULineBreak before;
  ULineBreak previous;
  ULineBreak indirect;
} LineBreakOpportunitiesState;

static void
prepareLineBreakOpportunitiesState (LineBreakOpportunitiesState *lbo) {
  lbo->index = 0;
  lbo->after = U_LB_SPACE;
  lbo->before = lbo->after;
  lbo->previous = lbo->before;
  lbo->indirect = U_LB_SPACE;
}

static void
findLineBreakOpportunities (
  BrailleContractionData *bcd,
  LineBreakOpportunitiesState *lbo,
  unsigned char *opportunities,
  const wchar_t *characters, unsigned int end
) {
  /* UAX #14: Line Breaking Properties
   * http://unicode.org/reports/tr14/
   * Section 6: Line Breaking Algorithm
   *
   * !  Mandatory break at the indicated position
   * ^  No break allowed at the indicated position
   * _  Break allowed at the indicated position
   *
   * H  ideographs
   * h  small kana
   * 9  digits
   */

  while (lbo->index <= end) {
    unsigned char *opportunity = &opportunities[lbo->index];

    lbo->previous = lbo->before;
    lbo->before = lbo->after;
    lbo->after = u_getIntPropertyValue(characters[lbo->index], UCHAR_LINE_BREAK);
    lbo->index += 1;

    /* LB9  Do not break a combining character sequence.
     */
    if (lbo->after == U_LB_COMBINING_MARK) {
      /* LB10: Treat any remaining combining mark as AL.
       */
      if ((lbo->before == U_LB_MANDATORY_BREAK) ||
          (lbo->before == U_LB_CARRIAGE_RETURN) ||
          (lbo->before == U_LB_LINE_FEED) ||
          (lbo->before == U_LB_NEXT_LINE) ||
          (lbo->before == U_LB_SPACE) ||
          (lbo->before == U_LB_ZWSPACE)) {
        lbo->before = U_LB_ALPHABETIC;
      }

      /* treat it as if it has the line breaking class of the base character
       */
      lbo->after = lbo->before;
      *opportunity = 0;
      continue;
    }

    if (lbo->before != U_LB_SPACE) lbo->indirect = lbo->before;

    /* LB2: Never break at the start of text.
     * sot ×
     */
    if (opportunity == opportunities) {
      *opportunity = 0;
      continue;
    }

    /* LB4: Always break after hard line breaks
     * BK !
     */
    if (lbo->before == U_LB_MANDATORY_BREAK) {
      *opportunity = 1;
      continue;
    }

    /* LB5: Treat CR followed by LF, as well as CR, LF, and NL as hard line breaks.
     * CR ^ LF
     * CR !
     * LF !
     * NL !
     */
    if ((lbo->before == U_LB_CARRIAGE_RETURN) && (lbo->after == U_LB_LINE_FEED)) {
      *opportunity = 0;
      continue;
    }
    if ((lbo->before == U_LB_CARRIAGE_RETURN) ||
        (lbo->before == U_LB_LINE_FEED) ||
        (lbo->before == U_LB_NEXT_LINE)) {
      *opportunity = 1;
      continue;
    }

    /* LB6: Do not break before hard line breaks.
     * ^ ( BK | CR | LF | NL )
     */
    if ((lbo->after == U_LB_MANDATORY_BREAK) ||
        (lbo->after == U_LB_CARRIAGE_RETURN) ||
        (lbo->after == U_LB_LINE_FEED) ||
        (lbo->after == U_LB_NEXT_LINE)) {
      *opportunity = 0;
      continue;
    }

    /* LB7: Do not break before spaces or zero width space.
     * ^ SP
     * ^ ZW
     */
    if ((lbo->after == U_LB_SPACE) || (lbo->after == U_LB_ZWSPACE)) {
      *opportunity = 0;
      continue;
    }

    /* LB8: Break after zero width space.
     * ZW _
     */
    if (lbo->before == U_LB_ZWSPACE) {
      *opportunity = 1;
      continue;
    }

    /* LB11: Do not break before or after Word joiner and related characters.
     * ^ WJ
     * WJ ^
     */
    if ((lbo->before == U_LB_WORD_JOINER) || (lbo->after == U_LB_WORD_JOINER)) {
      *opportunity = 0;
      continue;
    }

    /* LB12: Do not break before or after NBSP and related characters.
     * [^SP] ^ GL
     * GL ^
     */
    if ((lbo->before != U_LB_SPACE) && (lbo->after == U_LB_GLUE)) {
      *opportunity = 0;
      continue;
    }
    if (lbo->before == U_LB_GLUE) {
      *opportunity = 0;
      continue;
    }

    /* LB13: Do not break before ‘]' or ‘!' or ‘;' or ‘/', even after spaces.
     * ^ CL
     * ^ EX
     * ^ IS
     * ^ SY
     */
    if ((lbo->after == U_LB_CLOSE_PUNCTUATION) ||
        (lbo->after == U_LB_EXCLAMATION) ||
        (lbo->after == U_LB_INFIX_NUMERIC) ||
        (lbo->after == U_LB_BREAK_SYMBOLS)) {
      *opportunity = 0;
      continue;
    }

    /* LB14: Do not break after ‘[', even after spaces.
     * OP SP* ^
     */
    if (lbo->indirect == U_LB_OPEN_PUNCTUATION) {
      *opportunity = 0;
      continue;
    }

    /* LB15: Do not break within ‘"[', even with intervening spaces.
     * QU SP* ^ OP
     */
    if ((lbo->indirect == U_LB_QUOTATION) && (lbo->after == U_LB_OPEN_PUNCTUATION)) {
      *opportunity = 0;
      continue;
    }

    /* LB16: Do not break within ‘]h', even with intervening spaces.
     * CL SP* ^ NS
     */
    if ((lbo->indirect == U_LB_CLOSE_PUNCTUATION) && (lbo->after == U_LB_NONSTARTER)) {
      *opportunity = 0;
      continue;
    }

    /* LB17: Do not break within ‘ــ', even with intervening spaces.
     * B2 SP* ^ B2
     */
    if ((lbo->indirect == U_LB_BREAK_BOTH) && (lbo->after == U_LB_BREAK_BOTH)) {
      *opportunity = 0;
      continue;
    }

    /* LB18: Break after spaces.
     * SP _
     */
    if (lbo->before == U_LB_SPACE) {
      *opportunity = 1;
      continue;
    }

    /* LB19: Do not break before or after  quotation marks.
     * ^ QU
     * QU ^
     */
    if ((lbo->before == U_LB_QUOTATION) || (lbo->after == U_LB_QUOTATION)) {
      *opportunity = 0;
      continue;
    }

    /* LB20: Break before and after unresolved.
     * _ CB
     * CB _
     */
    if ((lbo->after == U_LB_CONTINGENT_BREAK) || (lbo->before == U_LB_CONTINGENT_BREAK)) {
      *opportunity = 1;
      continue;
    }

    /* LB21: Do not break before hyphen-minus, other hyphens,
     *       fixed-width spaces, small kana, and other non-starters,
     *       or lbo->after acute accents.
     * ^ BA
     * ^ HY
     * ^ NS
     * BB ^
     */
    if ((lbo->after == U_LB_BREAK_AFTER) ||
        (lbo->after == U_LB_HYPHEN) ||
        (lbo->after == U_LB_NONSTARTER) ||
        (lbo->before == U_LB_BREAK_BEFORE)) {
      *opportunity = 0;
      continue;
    }

    /* LB22: Do not break between two ellipses,
     *       or between letters or numbers and ellipsis.
     * AL ^ IN
     * ID ^ IN
     * IN ^ IN
     * NU ^ IN
     */
    if ((lbo->after == U_LB_INSEPARABLE) &&
        ((lbo->before == U_LB_ALPHABETIC) ||
         (lbo->before == U_LB_IDEOGRAPHIC) ||
         (lbo->before == U_LB_INSEPARABLE) ||
         (lbo->before == U_LB_NUMERIC))) {
      *opportunity = 0;
      continue;
    }

    /* LB23: Do not break within ‘a9', ‘3a', or ‘H%'.
     * ID ^ PO
     * AL ^ NU
     * NU ^ AL
     */
    if (((lbo->before == U_LB_IDEOGRAPHIC) && (lbo->after == U_LB_POSTFIX_NUMERIC)) ||
        ((lbo->before == U_LB_ALPHABETIC) && (lbo->after == U_LB_NUMERIC)) ||
        ((lbo->before == U_LB_NUMERIC) && (lbo->after == U_LB_ALPHABETIC))) {
      *opportunity = 0;
      continue;
    }

    /* LB24: Do not break between prefix and letters or ideographs.
     * PR ^ ID
     * PR ^ AL
     * PO ^ AL
     */
    if (((lbo->before == U_LB_PREFIX_NUMERIC) && (lbo->after == U_LB_IDEOGRAPHIC)) ||
        ((lbo->before == U_LB_PREFIX_NUMERIC) && (lbo->after == U_LB_ALPHABETIC)) ||
        ((lbo->before == U_LB_POSTFIX_NUMERIC) && (lbo->after == U_LB_ALPHABETIC))) {
      *opportunity = 0;
      continue;
    }

    /* LB25:  Do not break between the following pairs of classes relevant to numbers:
     * CL ^ PO
     * CL ^ PR
     * NU ^ PO
     * NU ^ PR
     * PO ^ OP
     * PO ^ NU
     * PR ^ OP
     * PR ^ NU
     * HY ^ NU
     * IS ^ NU
     * NU ^ NU
     * SY ^ NU
     */
    if (((lbo->before == U_LB_CLOSE_PUNCTUATION) && (lbo->after == U_LB_POSTFIX_NUMERIC)) ||
        ((lbo->before == U_LB_CLOSE_PUNCTUATION) && (lbo->after == U_LB_PREFIX_NUMERIC)) ||
        ((lbo->before == U_LB_NUMERIC) && (lbo->after == U_LB_POSTFIX_NUMERIC)) ||
        ((lbo->before == U_LB_NUMERIC) && (lbo->after == U_LB_PREFIX_NUMERIC)) ||
        ((lbo->before == U_LB_POSTFIX_NUMERIC) && (lbo->after == U_LB_OPEN_PUNCTUATION)) ||
        ((lbo->before == U_LB_POSTFIX_NUMERIC) && (lbo->after == U_LB_NUMERIC)) ||
        ((lbo->before == U_LB_PREFIX_NUMERIC) && (lbo->after == U_LB_OPEN_PUNCTUATION)) ||
        ((lbo->before == U_LB_PREFIX_NUMERIC) && (lbo->after == U_LB_NUMERIC)) ||
        ((lbo->before == U_LB_HYPHEN) && (lbo->after == U_LB_NUMERIC)) ||
        ((lbo->before == U_LB_INFIX_NUMERIC) && (lbo->after == U_LB_NUMERIC)) ||
        ((lbo->before == U_LB_NUMERIC) && (lbo->after == U_LB_NUMERIC)) ||
        ((lbo->before == U_LB_BREAK_SYMBOLS) && (lbo->after == U_LB_NUMERIC))) {
      *opportunity = 0;
      continue;
    }

    /* LB26: Do not break a Korean syllable.
     * JL ^ (JL | JV | H2 | H3)
     * (JV | H2) ^ (JV | JT)
     * (JT | H3) ^ JT
     */
    if ((lbo->before == U_LB_JL) &&
        ((lbo->after == U_LB_JL) ||
         (lbo->after == U_LB_JV) ||
         (lbo->after == U_LB_H2) ||
         (lbo->after == U_LB_H3))) {
      *opportunity = 0;
      continue;
    }
    if (((lbo->before == U_LB_JV) || (lbo->before == U_LB_H2)) &&
        ((lbo->after == U_LB_JV) || (lbo->after == U_LB_JT))) {
      *opportunity = 0;
      continue;
    }
    if (((lbo->before == U_LB_JT) || (lbo->before == U_LB_H3)) &&
        (lbo->after == U_LB_JT)) {
      *opportunity = 0;
      continue;
    }

    /* LB27: Treat a Korean Syllable Block the same as ID.
     * (JL | JV | JT | H2 | H3) ^ IN
     * (JL | JV | JT | H2 | H3) ^ PO
     * PR ^ (JL | JV | JT | H2 | H3)
     */
    if (((lbo->before == U_LB_JL) || (lbo->before == U_LB_JV) || (lbo->before == U_LB_JT) ||
         (lbo->before == U_LB_H2) || (lbo->before == U_LB_H3)) &&
        (lbo->after == U_LB_INSEPARABLE)) {
      *opportunity = 0;
      continue;
    }
    if (((lbo->before == U_LB_JL) || (lbo->before == U_LB_JV) || (lbo->before == U_LB_JT) ||
         (lbo->before == U_LB_H2) || (lbo->before == U_LB_H3)) &&
        (lbo->after == U_LB_POSTFIX_NUMERIC)) {
      *opportunity = 0;
      continue;
    }
    if ((lbo->before == U_LB_PREFIX_NUMERIC) &&
        ((lbo->after == U_LB_JL) || (lbo->after == U_LB_JV) || (lbo->after == U_LB_JT) ||
         (lbo->after == U_LB_H2) || (lbo->after == U_LB_H3))) {
      *opportunity = 0;
      continue;
    }

    /* LB28: Do not break between alphabetics.
     * AL ^ AL
     */
    if ((lbo->before == U_LB_ALPHABETIC) && (lbo->after == U_LB_ALPHABETIC)) {
      *opportunity = 0;
      continue;
    }

    /* LB29: Do not break between numeric punctuation and alphabetics.
     * IS ^ AL
     */
    if ((lbo->before == U_LB_INFIX_NUMERIC) && (lbo->after == U_LB_ALPHABETIC)) {
      *opportunity = 0;
      continue;
    }

    /* LB30: Do not break between letters, numbers, or ordinary symbols
     *       and opening or closing punctuation.
     * (AL | NU) ^ OP
     * CL ^ (AL | NU)
     */
    if (((lbo->before == U_LB_ALPHABETIC) || (lbo->before == U_LB_NUMERIC)) &&
        (lbo->after == U_LB_OPEN_PUNCTUATION)) {
      *opportunity = 0;
      continue;
    }
    if ((lbo->before == U_LB_CLOSE_PUNCTUATION) &&
        ((lbo->after == U_LB_ALPHABETIC) || (lbo->after == U_LB_NUMERIC))) {
      *opportunity = 0;
      continue;
    }

    /* Unix options begin with a minus sign. */
    if ((lbo->before == U_LB_HYPHEN) &&
        (lbo->after != U_LB_SPACE) &&
        (lbo->previous == U_LB_SPACE)) {
      *opportunity = 0;
      continue;
    }

    /* LB31: Break everywhere else.
     * ALL _
     * _ ALL
     */
    *opportunity = 1;
  }
}

#else /* HAVE_ICU */
typedef struct {
  unsigned int index;
  int wasSpace;
} LineBreakOpportunitiesState;

static void
prepareLineBreakOpportunitiesState (LineBreakOpportunitiesState *lbo) {
  lbo->index = 0;
  lbo->wasSpace = 0;
}

static void
findLineBreakOpportunities (
  BrailleContractionData *bcd,
  LineBreakOpportunitiesState *lbo,
  unsigned char *opportunities,
  const wchar_t *characters, unsigned int end
) {
  while (lbo->index <= end) {
    int isSpace = testCharacter(bcd, characters[lbo->index], CTC_Space);
    opportunities[lbo->index] = lbo->wasSpace && !isSpace;

    lbo->wasSpace = isSpace;
    lbo->index += 1;
  }
}
#endif /* HAVE_ICU */

static int
isLineBreakOpportunity (
  BrailleContractionData *bcd,
  LineBreakOpportunitiesState *lbo,
  unsigned char *opportunities
) {
  unsigned int index = getInputConsumed(bcd);
  if (index == getInputCount(bcd)) return 1;

  findLineBreakOpportunities(bcd, lbo, opportunities, bcd->input.begin, index);
  return opportunities[index];
}

static inline ContractionTableHeader *
getContractionTableHeader (BrailleContractionData *bcd) {
  return bcd->table->data.internal.header.fields;
}

static inline const void *
getContractionTableItem (BrailleContractionData *bcd, ContractionTableOffset offset) {
  return &bcd->table->data.internal.header.bytes[offset];
}

static const ContractionTableCharacter *
getContractionTableCharacter (BrailleContractionData *bcd, wchar_t character) {
  const ContractionTableCharacter *characters = getContractionTableItem(bcd, getContractionTableHeader(bcd)->characters);
  int first = 0;
  int last = getContractionTableHeader(bcd)->characterCount - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    const ContractionTableCharacter *ctc = &characters[current];

    if (ctc->value < character) {
      first = current + 1;
    } else if (ctc->value > character) {
      last = current - 1;
    } else {
      return ctc;
    }
  }

  return NULL;
}

static int
addRule (BrailleContractionData *bcd, ContractionTableRule *rule) {
  ContractionTable *table = bcd->table;

  if (table->rules.count == table->rules.size) {
    size_t newSize = table->rules.size + 10;
    ContractionTableRule **newArray = realloc(table->rules.array, ARRAY_SIZE(newArray, newSize));

    if (!newArray) {
      logMallocError();
      return 0;
    }

    table->rules.array = newArray;
    table->rules.size = newSize;
  }

  table->rules.array[table->rules.count++] = rule;
  return 1;
}

static size_t
makeDecomposedBraille (BrailleContractionData *bcd, wchar_t character, BYTE *cells, size_t size) {
  wchar_t characters[0X10];
  size_t characterCount = decomposeCharacter(character, characters, ARRAY_COUNT(characters));

  if (characterCount > 1) {
    BYTE *from = cells;
    const BYTE *end = from + size;
    unsigned int characterIndex = 1;

    while (1) {
      wchar_t character = characters[characterIndex];
      const CharacterEntry *entry = getCharacterEntry(bcd, character);
      if (!entry) break;
      if (character != entry->value) break;

      const ContractionTableRule *rule = entry->always;
      if (!rule) break;

      unsigned int cellCount = rule->replen;
      if (!cellCount) break;
      if ((end - from) < cellCount) break;
      from = mempcpy(from, &rule->findrep[rule->findlen], cellCount);

      if (!characterIndex) return from - cells;
      if (++characterIndex == characterCount) characterIndex = 0;
    }
  }

  return 0;
}

typedef struct {
  BrailleContractionData *bcd;
  CharacterEntry *character;
} SetAlwaysRuleData;

static int
setAlwaysRule (wchar_t character, void *data) {
  SetAlwaysRuleData *sar = data;
  BrailleContractionData *bcd = sar->bcd;

  CharacterEntry *entry = sar->character;
  const ContractionTableCharacter *ctc = getContractionTableCharacter(bcd, character);

  if (ctc) {
    ContractionTableOffset offset = ctc->always;

    if (offset) {
      const ContractionTableRule *rule = getContractionTableItem(bcd, offset);

      if (rule->replen) {
        entry->always = rule;
        return 1;
      }
    }
  }

  if (character == entry->value) {
    BYTE cells[0X100];
    size_t count = makeDecomposedBraille(bcd, character, cells, sizeof(cells));

    {
      unsigned int position;
      findCharacterEntry(bcd, character, &position);

      entry = &bcd->table->characters.array[position];
      sar->character = entry;
    }

    if (count) {
      ContractionTableRule *rule;
      size_t size = sizeof(*rule) + sizeof(character) + count;

      if ((rule = malloc(size))) {
        memset(rule, 0, sizeof(*rule));
        rule->opcode = CTO_Always;

        rule->findrep[0] = character;
        memcpy(&rule->findrep[rule->findlen = 1], cells, (rule->replen = count));

        if (addRule(bcd, rule)) {
          entry->always = rule;
          return 1;
        }

        free(rule);
      }
    }
  }

  return 0;
}

static wchar_t
toLowerCase (BrailleContractionData *bcd, wchar_t character) {
  const CharacterEntry *entry = getCharacterEntry(bcd, character);
  return entry? entry->lowercase: character;
}

static const ContractionTableRule *
getAlwaysRule (BrailleContractionData *bcd, wchar_t character) {
  const CharacterEntry *entry = getCharacterEntry(bcd, toLowerCase(bcd, character));
  return entry? entry->always: NULL;
}

static wchar_t
getBestCharacter (BrailleContractionData *bcd, wchar_t character) {
  const ContractionTableRule *rule = getAlwaysRule(bcd, character);
  return rule? rule->findrep[0]: 0;
}

static int
sameCharacters (BrailleContractionData *bcd, wchar_t character1, wchar_t character2) {
  wchar_t best1 = getBestCharacter(bcd, character1);
  return best1 && (best1 == getBestCharacter(bcd, character2));
}

static int
matchCurrentRule (BrailleContractionData *bcd) {
  const wchar_t *input = bcd->input.current;
  const wchar_t *find = bcd->current.rule->findrep;
  const wchar_t *findEnd = find + bcd->current.length;

  while (find < findEnd) {
    if (toLowerCase(bcd, *input++) != toLowerCase(bcd, *find++)) {
      return 0;
    }
  }

  return 1;
}

static void
setBefore (BrailleContractionData *bcd) {
  bcd->current.before = (bcd->input.current == bcd->input.begin)? WC_C(' '): bcd->input.current[-1];
}

static void
setAfter (BrailleContractionData *bcd, int length) {
  bcd->current.after = (bcd->input.current + length < bcd->input.end)? bcd->input.current[length]: WC_C(' ');
}

static int
isBeginning (BrailleContractionData *bcd) {
  const wchar_t *ptr = bcd->input.current;

  while (ptr > bcd->input.begin) {
    if (!testCharacter(bcd, *--ptr, CTC_Punctuation)) {
      if (!testCharacter(bcd, *ptr, CTC_Space)) return 0;
      break;
    }
  }

  return 1;
}

static int
isEnding (BrailleContractionData *bcd) {
  const wchar_t *ptr = bcd->input.current + bcd->current.length;

  while (ptr < bcd->input.end) {
    if (!testCharacter(bcd, *ptr, CTC_Punctuation)) {
      if (!testCharacter(bcd, *ptr, CTC_Space)) return 0;
      break;
    }

    ptr += 1;
  }

  return 1;
}

static void
setCurrentRule (BrailleContractionData *bcd, const ContractionTableRule *rule) {
  bcd->current.rule = rule;
  bcd->current.opcode = bcd->current.rule->opcode;
  bcd->current.length = bcd->current.rule->findlen;
  setAfter(bcd, bcd->current.length);
}

static int
selectRule (BrailleContractionData *bcd, int length) {
  if (length < 1) return 0;

  int ruleOffset;
  int maximumLength;

  if (length == 1) {
    wchar_t character = toLowerCase(bcd, *bcd->input.current);
    const ContractionTableCharacter *ctc = getContractionTableCharacter(bcd, character);

    if (!ctc) {
      const CharacterEntry *entry = getCharacterEntry(bcd, character);
      if (!entry) return 0;

      const ContractionTableRule *rule = entry->always;
      if (!rule) return 0;

      setCurrentRule(bcd, rule);
      return 1;
    }

    ruleOffset = ctc->rules;
    maximumLength = 1;
  } else {
    const wchar_t characters[] = {
      toLowerCase(bcd, bcd->input.current[0]),
      toLowerCase(bcd, bcd->input.current[1]),
    };

    ruleOffset = getContractionTableHeader(bcd)->rules[CTH(characters)];
    maximumLength = 0;
  }

  while (ruleOffset) {
    setCurrentRule(bcd, getContractionTableItem(bcd, ruleOffset));

    if ((length == 1) ||
        ((bcd->current.length <= length) &&
         matchCurrentRule(bcd))) {
      if (!maximumLength) {
        maximumLength = bcd->current.length;

        if (prefs.capitalizationMode != CTB_CAP_NONE) {
          typedef enum {CS_Any, CS_Lower, CS_UpperSingle, CS_UpperMultiple} CapitalizationState;
#define STATE(c) (testCharacter(bcd, (c), CTC_UpperCase)? CS_UpperSingle: testCharacter(bcd, (c), CTC_LowerCase)? CS_Lower: CS_Any)

          CapitalizationState current = STATE(bcd->current.before);

          for (int i=0; i<bcd->current.length; i+=1) {
            wchar_t character = bcd->input.current[i];
            CapitalizationState next = STATE(character);

            if (i > 0) {
              if (((current == CS_Lower) && (next == CS_UpperSingle)) ||
                  ((current == CS_UpperMultiple) && (next == CS_Lower))) {
                maximumLength = i;
                break;
              }

              if ((prefs.capitalizationMode != CTB_CAP_SIGN) &&
                  (next == CS_UpperSingle)) {
                maximumLength = i;
                break;
              }
            }

            if ((prefs.capitalizationMode == CTB_CAP_SIGN) && (current > CS_Lower) && (next == CS_UpperSingle)) {
              current = CS_UpperMultiple;
            } else if (next != CS_Any) {
              current = next;
            } else if (current == CS_Any) {
              current = CS_Lower;
            }
          }

#undef STATE
        }
      }

      if ((bcd->current.length <= maximumLength) &&
          (!bcd->current.rule->after || testBefore(bcd, bcd->current.rule->after)) &&
          (!bcd->current.rule->before || testAfter(bcd, bcd->current.rule->before))) {
        switch (bcd->current.opcode) {
          case CTO_Always:
          case CTO_Repeatable:
          case CTO_Literal:
          case CTO_Replace:
            return 1;

          case CTO_LargeSign:
          case CTO_LastLargeSign:
            if (!isBeginning(bcd) || !isEnding(bcd)) bcd->current.opcode = CTO_Always;
            return 1;

          case CTO_WholeWord:
            if (testBefore(bcd, CTC_Space|CTC_Punctuation) &&
                testAfter(bcd, CTC_Space|CTC_Punctuation))
              return 1;
            break;

          case CTO_Contraction:
            if ((bcd->input.current > bcd->input.begin) && sameCharacters(bcd, bcd->input.current[-1], WC_C('\''))) break;
            if (isBeginning(bcd) && isEnding(bcd)) return 1;
            break;

          case CTO_LowWord:
            if (testBefore(bcd, CTC_Space) && testAfter(bcd, CTC_Space) &&
                (bcd->previous.opcode != CTO_JoinedWord) &&
                ((bcd->output.current == bcd->output.begin) || !bcd->output.current[-1]))
              return 1;
            break;

          case CTO_JoinedWord:
            if (testBefore(bcd, CTC_Space|CTC_Punctuation) &&
                !sameCharacters(bcd, bcd->current.before, WC_C('-')) &&
                (bcd->output.current + bcd->current.rule->replen < bcd->output.end)) {
              const wchar_t *end = bcd->input.current + bcd->current.length;
              const wchar_t *ptr = end;

              while (ptr < bcd->input.end) {
                if (!testCharacter(bcd, *ptr, CTC_Space)) {
                  if (!testCharacter(bcd, *ptr, CTC_Letter)) break;
                  if (ptr == end) break;
                  return 1;
                }

                if (ptr++ == bcd->input.cursor) break;
              }
            }
            break;

          case CTO_SuffixableWord:
            if (testBefore(bcd, CTC_Space|CTC_Punctuation) &&
                testAfter(bcd, CTC_Space|CTC_Letter|CTC_Punctuation))
              return 1;
            break;

          case CTO_PrefixableWord:
            if (testBefore(bcd, CTC_Space|CTC_Letter|CTC_Punctuation) &&
                testAfter(bcd, CTC_Space|CTC_Punctuation))
              return 1;
            break;

          case CTO_BegWord:
            if (testBefore(bcd, CTC_Space|CTC_Punctuation) &&
                testAfter(bcd, CTC_Letter))
              return 1;
            break;

          case CTO_BegMidWord:
            if (testBefore(bcd, CTC_Letter|CTC_Space|CTC_Punctuation) &&
                testAfter(bcd, CTC_Letter))
              return 1;
            break;

          case CTO_MidWord:
            if (testBefore(bcd, CTC_Letter) && testAfter(bcd, CTC_Letter))
              return 1;
            break;

          case CTO_MidEndWord:
            if (testBefore(bcd, CTC_Letter) &&
                testAfter(bcd, CTC_Letter|CTC_Space|CTC_Punctuation))
              return 1;
            break;

          case CTO_EndWord:
            if (testBefore(bcd, CTC_Letter) &&
                testAfter(bcd, CTC_Space|CTC_Punctuation))
              return 1;
            break;

          case CTO_BegNum:
            if (testBefore(bcd, CTC_Space|CTC_Punctuation) &&
                testAfter(bcd, CTC_Digit))
              return 1;
            break;

          case CTO_MidNum:
            if (testBefore(bcd, CTC_Digit) && testAfter(bcd, CTC_Digit))
              return 1;
            break;

          case CTO_EndNum:
            if (testBefore(bcd, CTC_Digit) &&
                testAfter(bcd, CTC_Space|CTC_Punctuation))
              return 1;
            break;

          case CTO_PrePunc:
            if (testCurrent(bcd, CTC_Punctuation) && isBeginning(bcd) && !isEnding(bcd)) return 1;
            break;

          case CTO_PostPunc:
            if (testCurrent(bcd, CTC_Punctuation) && !isBeginning(bcd) && isEnding(bcd)) return 1;
            break;

          default:
            break;
        }
      }
    }

    ruleOffset = bcd->current.rule->next;
  }

  return 0;
}

static int
putCells (BrailleContractionData *bcd, const BYTE *cells, int count) {
  if (bcd->output.current + count > bcd->output.end) return 0;
  bcd->output.current = mempcpy(bcd->output.current, cells, count);
  return 1;
}

static int
putCell (BrailleContractionData *bcd, BYTE byte) {
  return putCells(bcd, &byte, 1);
}

static int
putReplace (BrailleContractionData *bcd, const ContractionTableRule *rule, wchar_t character) {
  const BYTE *cells = (BYTE *)&rule->findrep[rule->findlen];
  int count = rule->replen;

  if ((prefs.capitalizationMode == CTB_CAP_DOT7) &&
      testCharacter(bcd, character, CTC_UpperCase)) {
    if (!putCell(bcd, *cells++ | BRL_DOT_7)) return 0;
    if (!(count -= 1)) return 1;
  }

  return putCells(bcd, cells, count);
}

static int
putCharacter (BrailleContractionData *bcd, wchar_t character) {
  {
    const ContractionTableRule *rule = getAlwaysRule(bcd, character);
    if (rule) return putReplace(bcd, rule, character);
  }

  if (isBrailleCharacter(character)) {
    return putCell(bcd, (character & UNICODE_CELL_MASK));
  }

  if (textTable) {
    unsigned char dots = convertCharacterToDots(textTable, character);
    return putCell(bcd, dots);
  }

  {
    const wchar_t replacementCharacter = getReplacementCharacter();

    if (replacementCharacter != character) {
      const ContractionTableRule *rule = getAlwaysRule(bcd, replacementCharacter);
      if (rule) return putReplace(bcd, rule, replacementCharacter);
    }
  }

  return putCell(bcd, (BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6 | BRL_DOT_7 | BRL_DOT_8));
}

static int
putSequence (BrailleContractionData *bcd, ContractionTableOffset offset) {
  const BYTE *sequence = getContractionTableItem(bcd, offset);
  return putCells(bcd, sequence+1, *sequence);
}

static void
clearRemainingOffsets (BrailleContractionData *bcd) {
  const wchar_t *next = bcd->input.current + bcd->current.length;
  while (++bcd->input.current < next) clearOffset(bcd);
}

static int
contractText_native (BrailleContractionData *bcd) {
  bcd->previous.opcode = CTO_None;

  const wchar_t *srcword = NULL;
  const wchar_t *srcjoin = NULL;
  const wchar_t *literal = NULL;

  BYTE *destword = NULL;
  BYTE *destjoin = NULL;
  BYTE *destlast = NULL;

  unsigned char lineBreakOpportunities[getInputCount(bcd) + 1];
  LineBreakOpportunitiesState lbo;
  prepareLineBreakOpportunitiesState(&lbo);

  while (bcd->input.current < bcd->input.end) {
    int wasLiteral = bcd->input.current == literal;

    destlast = bcd->output.current;
    setOffset(bcd);
    setBefore(bcd);

    if (literal)
      if (bcd->input.current >= literal)
        if (testCurrent(bcd, CTC_Space) || testPrevious(bcd, CTC_Space))
          literal = NULL;

    if ((!literal && selectRule(bcd, getInputUnconsumed(bcd))) || selectRule(bcd, 1)) {
      if (!literal &&
          ((bcd->current.opcode == CTO_Literal) ||
           (prefs.expandCurrentWord &&
            (bcd->input.cursor >= bcd->input.current) &&
            (bcd->input.cursor < (bcd->input.current + bcd->current.length))))) {
        literal = bcd->input.current + bcd->current.length;

        if (!testCurrent(bcd, CTC_Space)) {
          if (destjoin) {
            bcd->input.current = srcjoin;
            bcd->output.current = destjoin;
          } else {
            bcd->input.current = bcd->input.begin;
            bcd->output.current = bcd->output.begin;
          }
        }

        continue;
      }

      if (bcd->current.opcode == CTO_Replace) {
        const ContractionTableRule *rule = bcd->current.rule;

        size_t size = rule->replen + 1;
        wchar_t characters[size];
        wchar_t *to = characters;
        const char *from = (const char *)&rule->findrep[rule->findlen];
        convertUtf8ToWchars(&from, &to, size);

        const wchar_t *inputBuffer = characters;
        int inputLength = to - characters;
        int outputLength = bcd->output.end - bcd->output.current;

        contractText(
          bcd->table, NULL,
          inputBuffer, &inputLength,
          bcd->output.current, &outputLength,
          NULL, CTB_NO_CURSOR
        );

        bcd->output.current += outputLength;
        clearRemainingOffsets(bcd);
        continue;
      }

      if (getContractionTableHeader(bcd)->numberSign && (bcd->previous.opcode != CTO_MidNum) &&
          !testBefore(bcd, CTC_Digit) && testCurrent(bcd, CTC_Digit)) {
        if (!putSequence(bcd, getContractionTableHeader(bcd)->numberSign)) break;
      } else if (getContractionTableHeader(bcd)->letterSign && testCurrent(bcd, CTC_Letter)) {
        if ((bcd->current.opcode == CTO_Contraction) ||
            ((bcd->current.opcode != CTO_EndNum) && testBefore(bcd, CTC_Digit)) ||
            (testCurrent(bcd, CTC_Letter) &&
             (bcd->current.opcode == CTO_Always) &&
             (bcd->current.length == 1) &&
             testBefore(bcd, CTC_Space) &&
             (((bcd->input.current + 1) == bcd->input.end) ||
              testNext(bcd, CTC_Space) ||
              (testNext(bcd, CTC_Punctuation) &&
               !sameCharacters(bcd, bcd->input.current[1], WC_C('.')) &&
               !sameCharacters(bcd, bcd->input.current[1], WC_C('\'')))))) {
          if (!putSequence(bcd, getContractionTableHeader(bcd)->letterSign)) break;
        }
      }

      if (prefs.capitalizationMode == CTB_CAP_SIGN) {
        if (testCurrent(bcd, CTC_UpperCase)) {
          if (!testBefore(bcd, CTC_UpperCase)) {
            if (getContractionTableHeader(bcd)->beginCapitalSign &&
                (bcd->input.current + 1 < bcd->input.end) && testNext(bcd, CTC_UpperCase)) {
              if (!putSequence(bcd, getContractionTableHeader(bcd)->beginCapitalSign)) break;
            } else if (getContractionTableHeader(bcd)->capitalSign) {
              if (!putSequence(bcd, getContractionTableHeader(bcd)->capitalSign)) break;
            }
          }
        } else if (testCurrent(bcd, CTC_LowerCase)) {
          if (getContractionTableHeader(bcd)->endCapitalSign && (bcd->input.current - 2 >= bcd->input.begin) &&
              testPrevious(bcd, CTC_UpperCase) && testRelative(bcd, -2, CTC_UpperCase)) {
            if (!putSequence(bcd, getContractionTableHeader(bcd)->endCapitalSign)) break;
          }
        }
      }

      switch (bcd->current.opcode) {
        case CTO_LargeSign:
        case CTO_LastLargeSign:
          if ((bcd->previous.opcode == CTO_LargeSign) && !wasLiteral) {
            while ((bcd->output.current > bcd->output.begin) && !bcd->output.current[-1]) bcd->output.current -= 1;
            setOffset(bcd);

            {
              BYTE **destptrs[] = {&destword, &destjoin, &destlast, NULL};
              BYTE ***destptr = destptrs;

              while (*destptr) {
                if (**destptr && (**destptr > bcd->output.current)) **destptr = bcd->output.current;
                destptr += 1;
              }
            }
          }
          break;

        default:
          break;
      }

      if (bcd->current.rule->replen &&
          !((bcd->current.opcode == CTO_Always) && (bcd->current.length == 1))) {
        if (!putReplace(bcd, bcd->current.rule, *bcd->input.current)) goto done;
        clearRemainingOffsets(bcd);
      } else {
        const wchar_t *srclim = bcd->input.current + bcd->current.length;
        while (1) {
          if (!putCharacter(bcd, *bcd->input.current)) goto done;
          if (++bcd->input.current == srclim) break;
          setOffset(bcd);
        }
      }

      {
        const wchar_t *srcorig = bcd->input.current;
        const wchar_t *srcbeg = NULL;
        BYTE *destbeg = NULL;

        switch (bcd->current.opcode) {
          case CTO_Repeatable: {
            const wchar_t *srclim = bcd->input.end - bcd->current.length;

            srcbeg = bcd->input.current - bcd->current.length;
            destbeg = destlast;

            while ((bcd->input.current <= srclim) && matchCurrentRule(bcd)) {
              clearOffset(bcd);
              clearRemainingOffsets(bcd);
            }

            break;
          }

          case CTO_JoinedWord:
            srcbeg = bcd->input.current;
            destbeg = bcd->output.current;

            while ((bcd->input.current < bcd->input.end) && testCurrent(bcd, CTC_Space)) {
              clearOffset(bcd);
              bcd->input.current += 1;
            }
            break;

          default:
            break;
        }

        if (srcbeg && (bcd->input.cursor >= srcbeg) && (bcd->input.cursor < bcd->input.current)) {
          int repeat = !literal;
          literal = bcd->input.current;

          if (repeat) {
            bcd->input.current = srcbeg;
            bcd->output.current = destbeg;
            continue;
          }

          bcd->input.current = srcorig;
        }
      }
    } else {
      bcd->current.opcode = CTO_Always;
      if (!putCharacter(bcd, *bcd->input.current)) break;
      bcd->input.current += 1;
    }

    if (isLineBreakOpportunity(bcd, &lbo, lineBreakOpportunities)) {
      srcjoin = bcd->input.current;
      destjoin = bcd->output.current;

      if (bcd->current.opcode != CTO_JoinedWord) {
        srcword = bcd->input.current;
        destword = bcd->output.current;
      }
    }

    if ((bcd->output.current == bcd->output.begin) || bcd->output.current[-1]) {
      bcd->previous.opcode = bcd->current.opcode;
    }
  }

done:
  if (bcd->input.current < bcd->input.end) {
    if (destword && (destword > bcd->output.begin) &&
        (!(testPrevious(bcd, CTC_Space) || testCurrent(bcd, CTC_Space)) ||
         (bcd->previous.opcode == CTO_JoinedWord))) {
      bcd->input.current = srcword;
      bcd->output.current = destword;
    } else if (destlast) {
      bcd->output.current = destlast;
    }
  }

  return 1;
}

static void
finishCharacterEntry_native (BrailleContractionData *bcd, CharacterEntry *entry) {
  wchar_t character = entry->value;

  {
    const ContractionTableCharacter *ctc = getContractionTableCharacter(bcd, character);
    if (ctc) entry->attributes |= ctc->attributes;
  }

  {
    SetAlwaysRuleData sar = {
      .bcd = bcd,
      .character = entry
    };

    int ok = (character == getReplacementCharacter())?
             setAlwaysRule(character, &sar):
             handleBestCharacter(character, setAlwaysRule, &sar);

    if (!ok) entry->always = NULL;
  }
}

static const ContractionTableTranslationMethods nativeTranslationMethods = {
  .contractText = contractText_native,
  .finishCharacterEntry = finishCharacterEntry_native
};

const ContractionTableTranslationMethods *
getContractionTableTranslationMethods_native (void) {
  return &nativeTranslationMethods;
}
