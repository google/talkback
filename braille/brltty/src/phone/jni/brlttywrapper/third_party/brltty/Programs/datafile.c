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
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <sys/stat.h>

#include "log.h"
#include "strfmt.h"
#include "file.h"
#include "queue.h"
#include "datafile.h"
#include "variables.h"
#include "charset.h"
#include "unicode.h"
#include "brl_dots.h"

struct DataFileStruct {
  const char *const name;
  const DataFileParameters *const parameters;
  DataFile *const includer;
  int line;

  struct {
    dev_t device;
    ino_t file;
  } identity;

  Queue *conditions;
  VariableNestingLevel *variables;

  const wchar_t *start;
  const wchar_t *end;
};

const wchar_t brlDotNumbers[BRL_DOT_COUNT] = {
  WC_C('1'), WC_C('2'), WC_C('3'), WC_C('4'),
  WC_C('5'), WC_C('6'), WC_C('7'), WC_C('8')
};
const unsigned char brlDotBits[BRL_DOT_COUNT] = {
  BRL_DOT_1, BRL_DOT_2, BRL_DOT_3, BRL_DOT_4,
  BRL_DOT_5, BRL_DOT_6, BRL_DOT_7, BRL_DOT_8
};

int
brlDotNumberToIndex (wchar_t number, int *index) {
  const wchar_t *character = wmemchr(brlDotNumbers, number, ARRAY_COUNT(brlDotNumbers));
  if (!character) return 0;
  *index = character - brlDotNumbers;
  return 1;
}

int
brlDotBitToIndex (unsigned char bit, int *index) {
  const unsigned char *cell = memchr(brlDotBits, bit, ARRAY_COUNT(brlDotBits));
  if (!cell) return 0;
  *index = cell - brlDotBits;
  return 1;
}

void
reportDataError (DataFile *file, char *format, ...) {
  char message[0X200];

  {
    const char *name = NULL;
    const int *line = NULL;
    va_list args;

    if (file) {
      name = file->name;
      if (file->line) line = &file->line;
    }

    va_start(args, format);
    formatInputError(message, sizeof(message), name, line, format, args);
    va_end(args);
  }

  logMessage(LOG_WARNING, "%s", message);
}

int
compareKeyword (const wchar_t *keyword, const wchar_t *characters, size_t count) {
  while (count > 0) {
    wchar_t character1;
    wchar_t character2;

    if (!(character1 = *keyword)) return -1;
    keyword += 1;

    character1 = towlower(character1);
    character2 = towlower(*characters++);

    if (character1 < character2) return -1;
    if (character1 > character2) return 1;

    count -= 1;
  }

  return *keyword? 1: 0;
}

int
compareKeywords (const wchar_t *keyword1, const wchar_t *keyword2) {
  return compareKeyword(keyword1, keyword2, wcslen(keyword2));
}

int
isKeyword (const wchar_t *keyword, const wchar_t *characters, size_t count) {
  return compareKeyword(keyword, characters, count) == 0;
}

int
isHexadecimalDigit (wchar_t character, int *value, int *shift) {
  if ((character >= WC_C('0')) && (character <= WC_C('9'))) {
    *value = character - WC_C('0');
  } else if ((character >= WC_C('a')) && (character <= WC_C('f'))) {
    *value = character - WC_C('a') + 10;
  } else if ((character >= WC_C('A')) && (character <= WC_C('F'))) {
    *value = character - WC_C('A') + 10;
  } else {
    return 0;
  }

  *shift = 4;
  return 1;
}

int
isOctalDigit (wchar_t character, int *value, int *shift) {
  if ((character < WC_C('0')) || (character > WC_C('7'))) return 0;
  *value = character - WC_C('0');
  *shift = 3;
  return 1;
}

int
isNumber (int *number, const wchar_t *characters, int length) {
  if (length > 0) {
    char string[length + 1];
    string[length] = 0;

    {
      int index;

      for (index=0; index<length; index+=1) {
        wchar_t wc = characters[index];
        if (!iswLatin1(wc)) return 0;
        string[index] = wc;
      }
    }

    {
      char *end;
      long value = strtol(string, &end, 0);

      if (!*end) {
        *number = value;
        return 1;
      }
    }
  }

  return 0;
}

static VariableNestingLevel *baseDataVariables = NULL;
static VariableNestingLevel *currentDataVariables = NULL;

static VariableNestingLevel *
getBaseDataVariables (void) {
  if (baseDataVariables) {
    releaseVariableNestingLevel(currentDataVariables);
    deleteVariables(baseDataVariables);
  } else {
    VariableNestingLevel *globalVariables = getGlobalVariables(1);
    if (!globalVariables) return NULL;

    VariableNestingLevel *baseVariables = newVariableNestingLevel(globalVariables, "base");
    if (!baseVariables) return NULL;

    baseDataVariables = claimVariableNestingLevel(baseVariables);
  }

  currentDataVariables = claimVariableNestingLevel(baseDataVariables);
  return baseDataVariables;
}

int
setBaseDataVariables (const VariableInitializer *initializers) {
  VariableNestingLevel *variables = getBaseDataVariables();
  if (!variables) return 0;
  return setStringVariables(variables, initializers);
}

int
setTableDataVariables (const char *tableExtension, const char *subtableExtension) {
  const VariableInitializer initializers[] = {
    { .name = "tableExtension", .value = tableExtension },
    { .name = "subtableExtension", .value = subtableExtension },
    { .name = NULL }
  };

  return setBaseDataVariables(initializers);
}

static int
pushDataVariableNestingLevel (void) {
  VariableNestingLevel *variables = newVariableNestingLevel(currentDataVariables, NULL);
  if (!variables) return 0;

  releaseVariableNestingLevel(currentDataVariables);
  currentDataVariables = claimVariableNestingLevel(variables);

  return 1;
}

int
findDataOperand (DataFile *file, const char *description) {
  file->start = file->end;

  while (iswspace(file->start[0])) file->start += 1;
  if (*(file->end = file->start)) return 1;
  if (description) reportDataError(file, "%s not specified", description);
  return 0;
}

int
getDataCharacter (DataFile *file, wchar_t *character) {
  if (!*file->end) return 0;
  *character = *file->end++;
  return 1;
}

int
ungetDataCharacters (DataFile *file, unsigned int count) {
  unsigned int maximum = file->end - file->start;

  if (count > maximum) {
    reportDataError(file, "unget character count out of range: %u > %u",
                    count, maximum);
    return 0;
  }

  file->end -= count;
  return 1;
}

void
getTextRemaining (DataFile *file, DataOperand *text) {
  file->end = file->start + wcslen(file->start);
  text->characters = file->start;
  text->length = file->end - file->start;
}

int
getTextOperand (DataFile *file, DataOperand *text, const char *description) {
  if (!findDataOperand(file, description)) return 0;
  getTextRemaining(file, text);

  while (text->length) {
    unsigned int newLength = text->length - 1;
    if (!iswspace(text->characters[newLength])) break;
    text->length = newLength;
  }

  return 1;
}

int
getDataOperand (DataFile *file, DataOperand *operand, const char *description) {
  if (!findDataOperand(file, description)) return 0;

  do {
    file->end += 1;
  } while (file->end[0] && !iswspace(file->end[0]));

  operand->characters = file->start;
  operand->length = file->end - file->start;
  return 1;
}

int
parseDataString (DataFile *file, DataString *string, const wchar_t *characters, int length, int noUnicode) {
  int index = 0;

  string->length = 0;

  while (index < length) {
    wchar_t character = characters[index];
    DataOperand substitution = {
      .characters = &character,
      .length = 1
    };

    if (character == WC_C('\\')) {
      int start = index;
      const char *problem = strtext("invalid escape sequence");
      int ok = 0;

      if (++index < length) {
        switch ((character = characters[index])) {
          case WC_C('#'):
          case WC_C('\\'):
            ok = 1;
            break;

          case WC_C('b'):
            character = WC_C('\b');
            ok = 1;
            break;

          case WC_C('f'):
            character = WC_C('\f');
            ok = 1;
            break;

          case WC_C('n'):
            character = WC_C('\n');
            ok = 1;
            break;

          case WC_C('r'):
            character = WC_C('\r');
            ok = 1;
            break;

          case WC_C('s'):
            character = WC_C(' ');
            ok = 1;
            break;

          case WC_C('t'):
            character = WC_C('\t');
            ok = 1;
            break;

          case WC_C('v'):
            character = WC_C('\v');
            ok = 1;
            break;

          {
            uint32_t result;
            int count;
            int (*isDigit) (wchar_t character, int *value, int *shift);

          case WC_C('o'):
            count = 3;
            isDigit = isOctalDigit;
            goto doNumber;

          case WC_C('U'):
            if (noUnicode) break;
            count = 8;
            goto doHexadecimal;

          case WC_C('u'):
            if (noUnicode) break;
            count = 4;
            goto doHexadecimal;

          case WC_C('X'):
          case WC_C('x'):
            count = 2;
            goto doHexadecimal;

          doHexadecimal:
            isDigit = isHexadecimalDigit;
            goto doNumber;

          doNumber:
            result = 0;

            while (++index < length) {
              {
                int value;
                int shift;

                if (!isDigit(characters[index], &value, &shift)) break;
                result = (result << shift) | value;
              }

              if (!--count) {
                if (result > WCHAR_MAX) {
                  problem = NULL;
                } else {
                  character = result;
                  ok = 1;
                }

                break;
              }
            }

            break;
          }

          case WC_C('{'): {
            const wchar_t *first = &characters[++index];
            const wchar_t *end = wmemchr(first, WC_C('}'), length-index);

            if (end) {
              int count = end - first;
              index += count;

              const Variable *variable = findReadableVariable(currentDataVariables, first, count);

              if (variable) {
                getVariableValue(variable, &substitution.characters, &substitution.length);
                ok = 1;
              }
            } else {
              index = length - 1;
            }

            break;
          }

          case WC_C('<'): {
            const wchar_t *first = &characters[++index];
            const wchar_t *end = wmemchr(first, WC_C('>'), length-index);

            if (noUnicode) break;

            if (end) {
              int count = end - first;
              index += count;

              {
                char name[count+1];

                {
                  unsigned int i;

                  for (i=0; i<count; i+=1) {
                    wchar_t wc = first[i];

                    if (wc == WC_C('_')) wc = WC_C(' ');
                    if (!iswLatin1(wc)) break;
                    name[i] = wc;
                  }

                  if (i < count) break;
                  name[i] = 0;
                }

                if (getCharacterByName(&character, name)) ok = 1;
              }
            } else {
              index = length - 1;
            }

            break;
          }
        }
      }

      if (!ok) {
        if (index < length) index += 1;

        if (problem) {
          reportDataError(file, "%s: %.*" PRIws,
                          gettext(problem),
                          index-start, &characters[start]);
        }

        return 0;
      }
    }
    index += 1;

    {
      unsigned int newLength = string->length + substitution.length;

      /* there needs to be room for a trailing NUL */
      if (newLength >= ARRAY_COUNT(string->characters)) {
        reportDataError(file, "string operand too long");
        return 0;
      }

      wmemcpy(&string->characters[string->length], substitution.characters, substitution.length);
      string->length = newLength;
    }
  }
  string->characters[string->length] = 0;

  return 1;
}

int
getDataString (DataFile *file, DataString *string, int noUnicode, const char *description) {
  DataOperand operand;

  if (getDataOperand(file, &operand, description))
    if (parseDataString(file, string, operand.characters, operand.length, noUnicode))
      return 1;

  return 0;
}

int
writeHexadecimalCharacter (FILE *stream, wchar_t character) {
  uint32_t value = character;

  if (value < 0X100) {
    return fprintf(stream, "\\x%02" PRIX32, value) != EOF;
  } else if (value < 0X10000) {
    return fprintf(stream, "\\u%04" PRIX32, value) != EOF;
  } else {
    return fprintf(stream, "\\U%08" PRIX32, value) != EOF;
  }
}

int
writeEscapedCharacter (FILE *stream, wchar_t character) {
  {
    static const char escapes[] = {
      [' ']  = 's',
      ['\\'] = '\\'
    };

    if (character < ARRAY_COUNT(escapes)) {
      char escape = escapes[character];

      if (escape) {
        return fprintf(stream, "\\%c", escape) != EOF;
      }
    }
  }

  if (iswspace(character) || iswcntrl(character)) return writeHexadecimalCharacter(stream, character);
  return writeUtf8Character(stream, character);
}

int
writeEscapedCharacters (FILE *stream, const wchar_t *characters, size_t count) {
  const wchar_t *end = characters + count;

  while (characters < end)
    if (!writeEscapedCharacter(stream, *characters++))
      return 0;

  return 1;
}

static int
parseDotOperand (DataFile *file, int *index, const wchar_t *characters, int length) {
  if (length == 1)
    if (brlDotNumberToIndex(characters[0], index))
      return 1;

  reportDataError(file, "invalid braille dot number: %.*" PRIws, length, characters);
  return 0;
}

int
getDotOperand (DataFile *file, int *index) {
  DataOperand number;

  if (getDataOperand(file, &number, "dot number"))
    if (parseDotOperand(file, index, number.characters, number.length))
      return 1;

  return 0;
}

int
parseCellsOperand (DataFile *file, ByteOperand *cells, const wchar_t *characters, int length) {
  unsigned char cell = 0;
  int start = 0;
  int index;

  cells->length = 0;

  for (index=0; index<length; index+=1) {
    int started = index != start;
    wchar_t character = characters[index];

    switch (character) {
      {
        int dot;

      case WC_C('1'):
        dot = BRL_DOT_1;
        goto doDot;

      case WC_C('2'):
        dot = BRL_DOT_2;
        goto doDot;

      case WC_C('3'):
        dot = BRL_DOT_3;
        goto doDot;

      case WC_C('4'):
        dot = BRL_DOT_4;
        goto doDot;

      case WC_C('5'):
        dot = BRL_DOT_5;
        goto doDot;

      case WC_C('6'):
        dot = BRL_DOT_6;
        goto doDot;

      case WC_C('7'):
        dot = BRL_DOT_7;
        goto doDot;

      case WC_C('8'):
        dot = BRL_DOT_8;
        goto doDot;

      doDot:
        if (started && !cell) goto invalid;

        if (cell & dot) {
          reportDataError(file, "dot specified more than once: %.1" PRIws, &character);
          return 0;
        }

        cell |= dot;
        break;
      }

      case CELLS_OPERAND_SPACE:
        if (started) goto invalid;
        break;

      case CELLS_OPERAND_DELIMITER:
        if (!started) {
          reportDataError(file, "missing cell specification: %.*" PRIws,
                          length-index, &characters[index]);
          return 0;
        }

        cells->bytes[cells->length++] = cell;

        if (cells->length == ARRAY_COUNT(cells->bytes)) {
          reportDataError(file, "cells operand too long");
          return 0;
        }

        cell = 0;
        start = index + 1;
        break;

      default:
      invalid:
        reportDataError(file, "invalid dot number: %.1" PRIws, &character);
        return 0;
    }
  }

  if (index == start) {
    reportDataError(file, "missing cell specification");
    return 0;
  }

  cells->bytes[cells->length++] = cell;		/*last cell */
  return 1;
}

int
getCellsOperand (DataFile *file, ByteOperand *cells, const char *description) {
  DataOperand operand;

  if (getDataOperand(file, &operand, description))
    if (parseCellsOperand(file, cells, operand.characters, operand.length))
      return 1;

  return 0;
}

int
writeDots (FILE *stream, unsigned char cell) {
  unsigned int dot;

  for (dot=1; dot<=BRL_DOT_COUNT; dot+=1) {
    if (cell & BRL_DOT(dot)) {
      if (fprintf(stream, "%u", dot) == EOF) return 0;
    }
  }

  return 1;
}

int
writeDotsCell (FILE *stream, unsigned char cell) {
  if (!cell) return fputc('0', stream) != EOF;
  return writeDots(stream, cell);
}

int
writeDotsCells (FILE *stream, const unsigned char *cells, size_t count) {
  const unsigned char *cell = cells;
  const unsigned char *end = cells + count;

  while (cell < end) {
    if (cell != cells)
      if (fputc('-', stream) == EOF)
        return 0;

    if (!writeDotsCell(stream, *cell++)) return 0;
  }

  return 1;
}

int
writeUtf8Cell (FILE *stream, unsigned char cell) {
  return writeUtf8Character(stream, (UNICODE_BRAILLE_ROW | cell));
}

int
writeUtf8Cells (FILE *stream, const unsigned char *cells, size_t count) {
  const unsigned char *end = cells + count;

  while (cells < end)
    if (!writeUtf8Cell(stream, *cells++))
      return 0;

  return 1;
}

typedef struct {
  unsigned canInclude:1;
  unsigned isIncluding:1;
  unsigned inElse:1;
} DataCondition;

static inline int
shallInclude (const DataCondition *condition) {
  return condition->canInclude && condition->isIncluding;
}

static void
deallocateDataCondition (void *item, void *data UNUSED) {
  DataCondition *condition = item;

  free(condition);
}

static Element *
getInnermostDataCondition (DataFile *file) {
  return getStackHead(file->conditions);
}

static Element *
getCurrentDataCondition (DataFile *file) {
  {
    Element *element = getInnermostDataCondition(file);

    if (element) return element;
  }

  reportDataError(file, "no outstanding condition");
  return NULL;
}

static int
removeDataCondition (DataFile *file, Element *element, int identifier) {
  if (!(element && (identifier == getElementIdentifier(element)))) return 0;
  deleteElement(element);
  return 1;
}

static Element *
pushDataCondition (
  DataFile *file, const DataString *name,
  DataConditionTester *testCondition, int negateCondition
) {
  DataCondition *condition;

  if ((condition = malloc(sizeof(*condition)))) {
    memset(condition, 0, sizeof(*condition));
    condition->inElse = 0;

    {
      const DataOperand identifier = {
        .characters = name->characters,
        .length = name->length
      };

      condition->isIncluding = testCondition(file, &identifier, file->parameters->data);
      if (negateCondition) condition->isIncluding = !condition->isIncluding;
    }

    {
      Element *element = getInnermostDataCondition(file);

      if (element) {
        const DataCondition *parent = getElementItem(element);

        condition->canInclude = shallInclude(parent);
      } else {
        condition->canInclude = 1;
      }
    }

    {
      Element *element = enqueueItem(file->conditions, condition);

      if (element) return element;
    }

    free(condition);
  } else {
    logMallocError();
  }

  return NULL;
}

static int
testDataCondition (DataFile *file) {
  Element *element = getInnermostDataCondition(file);

  if (element) {
    const DataCondition *condition = getElementItem(element);

    if (!shallInclude(condition)) return 0;
  }

  return 1;
}

static int
compareDataDirectiveNames (const wchar_t *name1, const wchar_t *name2) {
  return compareKeywords(name1, name2);
}

static int
sortDataDirectivesByName (const void *element1, const void *element2) {
  const DataDirective *const *directive1 = element1;
  const DataDirective *const *directive2 = element2;

  return compareDataDirectiveNames((*directive1)->name, (*directive2)->name);
}

static int
searchDataDirectiveByName (const void *target, const void *element) {
  const wchar_t *name = target;
  const DataDirective *const *directive = element;

  return compareDataDirectiveNames(name, (*directive)->name);
}

static const DataDirective *
findDataDirectiveByName (const DataDirectives *directives, const wchar_t *name) {
  DataDirective **directive = bsearch(
    name, directives->sorted.table, directives->sorted.count,
    sizeof(*directives->sorted.table), searchDataDirectiveByName
  );

  return directive? *directive: NULL;
}

static int
prepareDataDirectives (DataDirectives *directives) {
  if (!directives->sorted.table) {
    static const DataDirective unnamed = {
      .name = NULL,
      .processor = NULL
    };

    directives->unnamed = &unnamed;
    directives->sorted.count = 0;

    if (!(directives->sorted.table = malloc(ARRAY_SIZE(directives->sorted.table, directives->unsorted.count)))) {
      logMallocError();
      return 0;
    }

    {
      const DataDirective *directive = directives->unsorted.table;
      const DataDirective *end = directive + directives->unsorted.count;

      while (directive < end) {
        if (directive->name) {
          directives->sorted.table[directives->sorted.count++] = directive;
        } else {
          directives->unnamed = directive;
        }

        directive += 1;
      }
    }

    qsort(directives->sorted.table, directives->sorted.count,
          sizeof(*directives->sorted.table), sortDataDirectivesByName);
  }

  return 1;
}

int
processDirectiveOperand (DataFile *file, DataDirectives *directives, const char *description, void *data) {
  DataOperand name;

  if (getDataOperand(file, &name, description)) {
    const DataDirective *directive;

    if (!prepareDataDirectives(directives)) return 0;

    {
      wchar_t string[name.length + 1];

      wmemcpy(string, name.characters, name.length);
      string[name.length] = 0;

      if (!(directive = findDataDirectiveByName(directives, string))) {
        directive = directives->unnamed;
        ungetDataCharacters(file, name.length);
      }
    }

    if (!(directive->unconditional || testDataCondition(file))) return 1;
    if (directive->processor) return directive->processor(file, data);

    reportDataError(file, "unknown %s: %.*" PRIws,
                    description, name.length, name.characters);
  }

  return 1;
}

static int
processDataOperands (DataFile *file) {
  return file->parameters->processOperands(file, file->parameters->data);
}

static int
processDataCharacters (DataFile *file, const wchar_t *line) {
  file->end = file->start = line;

  if (!(file->parameters->options & DFO_NO_COMMENTS)) {
    if (!findDataOperand(file, NULL)) return 1;
    if (file->start[0] == WC_C('#')) return 1;
  }

  return processDataOperands(file);
}

static int
processConditionSubdirective (DataFile *file, Element *element) {
  int identifier = getElementIdentifier(element);

  if (findDataOperand(file, NULL)) {
    int result = processDataOperands(file);
    removeDataCondition(file, element, identifier);
    return result;
  }

  return 1;
}

int
processConditionOperands (
  DataFile *file,
  DataConditionTester *testCondition, int negateCondition,
  const char *description, void *data
) {
  DataString name;

  if (getDataString(file, &name, 1, description)) {
    Element *element = pushDataCondition(file, &name, testCondition, negateCondition);

    if (!element) return 0;
    if (!processConditionSubdirective(file, element)) return 0;
  }

  return 1;
}

static DATA_CONDITION_TESTER(testVariableDefined) {
  return !!findReadableVariable(currentDataVariables, identifier->characters, identifier->length);
}

static int
processVariableTestOperands (DataFile *file, int not, void *data) {
  return processConditionOperands(file, testVariableDefined, not, "variable name", data);
}

DATA_OPERANDS_PROCESSOR(processIfVarOperands) {
  return processVariableTestOperands(file, 0, data);
}

DATA_OPERANDS_PROCESSOR(processIfNotVarOperands) {
  return processVariableTestOperands(file, 1, data);
}

DATA_OPERANDS_PROCESSOR(processBeginVariablesOperands) {
  return pushDataVariableNestingLevel();
}

DATA_OPERANDS_PROCESSOR(processEndVariablesOperands) {
  if (currentDataVariables == file->variables) {
    reportDataError(file, "no nested variables");
  } else {
    currentDataVariables = removeVariableNestingLevel(currentDataVariables);
  }

  return 1;
}

DATA_OPERANDS_PROCESSOR(processListVariablesOperands) {
  listVariables(currentDataVariables);
  return 1;
}

static int
processVariableAssignmentOperands (DataFile *file, int ifNotSet, void *data) {
  DataOperand name;

  if (getDataOperand(file, &name, "variable name")) {
    DataString value;

    if (!getDataString(file, &value, 0, NULL)) {
      value.length = 0;
    }

    if (ifNotSet) {
      const Variable *variable = findReadableVariable(currentDataVariables, name.characters, name.length);

      if (variable) return 1;
    }

    {
      Variable *variable = findWritableVariable(currentDataVariables, name.characters, name.length);

      if (variable) {
        if (setVariable(variable, value.characters, value.length)) return 1;
      }
    }
  }

  return 1;
}

DATA_OPERANDS_PROCESSOR(processAssignDefaultOperands) {
  return processVariableAssignmentOperands(file, 1, data);
}

DATA_OPERANDS_PROCESSOR(processAssignOperands) {
  return processVariableAssignmentOperands(file, 0, data);
}

DATA_OPERANDS_PROCESSOR(processElseOperands) {
  Element *element = getCurrentDataCondition(file);

  if (element) {
    DataCondition *condition = getElementItem(element);

    if (condition->inElse) {
      reportDataError(file, "already in else");
    } else {
      condition->inElse = 1;
      condition->isIncluding = !condition->isIncluding;
      if (!processConditionSubdirective(file, element)) return 0;
    }
  }

  return 1;
}

DATA_OPERANDS_PROCESSOR(processEndIfOperands) {
  Element *element = getCurrentDataCondition(file);

  if (element) {
    removeDataCondition(file, element, getElementIdentifier(element));
  }

  return 1;
}

static int
isDataFileIncluded (DataFile *file, const char *path) {
  struct stat info;

  if (stat(path, &info) != -1) {
    while (file) {
      if ((file->identity.device == info.st_dev) && (file->identity.file == info.st_ino)) return 1;
      file = file->includer;
    }
  }

  return 0;
}

static FILE *
openIncludedDataFile (DataFile *includer, const char *path, const char *mode, int optional) {
  const char *const *overrideDirectories = getAllOverrideDirectories();
  const char *overrideDirectory = NULL;
  char *overridePath = NULL;

  int writable = (*mode == 'w') || (*mode == 'a');
  const char *name = locatePathName(path);
  FILE *file;

  if (overrideDirectories) {
    const char *const *directory = overrideDirectories;

    while (*directory) {
      if (**directory) {
        char *path = makePath(*directory, name);

        if (path) {
          if (!isDataFileIncluded(includer, path)) {
            if (testFilePath(path)) {
              file = openFile(path, mode, optional);
              overrideDirectory = *directory;
              overridePath = path;
              goto done;
            }
          }

          free(path);
        }
      }

      directory += 1;
    }
  }

  if (isDataFileIncluded(includer, path)) {
    logMessage(LOG_WARNING, "data file include loop: %s", path);
    file = NULL;
    errno = ENOENT;
  } else if (!(file = openFile(path, mode, optional))) {
    if (writable) {
      if (errno == ENOENT) {
        char *directory = getPathDirectory(path);

        if (directory) {
          int exists = ensureDirectory(directory);
          free(directory);

          if (exists) {
            file = openFile(path, mode, optional);
            goto done;
          }
        }
      }

      if ((errno == EACCES) || (errno == EROFS)) {
        if ((overrideDirectory = getPrimaryOverrideDirectory())) {
          if ((overridePath = makePath(overrideDirectory, name))) {
            if (ensureDirectory(overrideDirectory)) {
              file = openFile(overridePath, mode, optional);
              goto done;
            }
          }
        }
      }
    }
  }

done:
  if (overridePath) free(overridePath);
  return file;
}

FILE *
openDataFile (const char *path, const char *mode, int optional) {
  return openIncludedDataFile(NULL, path, mode, optional);
}

int
includeDataFile (DataFile *file, const wchar_t *name, int length) {
  int ok = 0;

  const char *prefixAddress = file->name;
  size_t prefixLength = 0;

  size_t suffixLength;
  char *suffixAddress = makeUtf8FromWchars(name, length, &suffixLength);

  if (suffixAddress) {
    if (!isAbsolutePath(suffixAddress)) {
      const char *prefixEnd = strrchr(prefixAddress, '/');
      if (prefixEnd) prefixLength = prefixEnd - prefixAddress + 1;
    }

    {
      char path[prefixLength + suffixLength + 1];
      FILE *stream;

      snprintf(path, sizeof(path), "%.*s%.*s",
               (int)prefixLength, prefixAddress,
               (int)suffixLength, suffixAddress);

      if ((stream = openIncludedDataFile(file, path, "r", 0))) {
        if (processDataStream(file, stream, path, file->parameters)) ok = 1;
        fclose(stream);
      }
    }

    free(suffixAddress);
  } else {
    logMallocError();
  }

  return ok;
}

DATA_OPERANDS_PROCESSOR(processIncludeOperands) {
  DataString path;

  if (getDataString(file, &path, 0, "include file path"))
    if (!includeDataFile(file, path.characters, path.length))
      return 0;

  return 1;
}

static int
processDataLine (char *line, void *dataAddress) {
  DataFile *file = dataAddress;
  size_t size = strlen(line) + 1;
  const char *byte = line;
  wchar_t characters[size];
  wchar_t *character = characters;

  file->line += 1;
  convertUtf8ToWchars(&byte, &character, size);

  if (*byte) {
    unsigned int offset = byte - line;
    reportDataError(file, "illegal UTF-8 character at offset %u", offset);
    return 1;
  }

  return processDataCharacters(file, characters);
}

int
processDataStream (
  DataFile *includer,
  FILE *stream, const char *name,
  const DataFileParameters *parameters
) {
  int ok = 0;

  if (parameters->logFileName) {
    parameters->logFileName(name, parameters->data);
  } else {
    logMessage(LOG_DEBUG, "including data file: %s", name);
  }

  DataFile file = {
    .name = name,
    .parameters = parameters,
    .includer = includer,
    .line = 0,
  };

  {
    struct stat info;

    if (fstat(fileno(stream), &info) != -1) {
      file.identity.device = info.st_dev;
      file.identity.file = info.st_ino;
    }
  }

  VariableNestingLevel *oldVariables = currentDataVariables;

  if ((file.variables = newVariableNestingLevel(oldVariables, name))) {
    currentDataVariables = claimVariableNestingLevel(file.variables);

    if ((file.conditions = newQueue(deallocateDataCondition, NULL))) {
      if (processLines(stream, processDataLine, &file)) ok = 1;

      if (getInnermostDataCondition(&file)) {
        reportDataError(&file, "outstanding condition at end of file");
      }

      deallocateQueue(file.conditions);
    }

    releaseVariableNestingLevel(currentDataVariables);
    currentDataVariables = oldVariables;
  }

  return ok;
}

int
processDataFile (const char *name, const DataFileParameters *parameters) {
  int ok = 0;
  FILE *stream;

  if ((stream = openDataFile(name, "r", 0))) {
    if (processDataStream(NULL, stream, name, parameters)) ok = 1;
    fclose(stream);
  }

  return ok;
}
