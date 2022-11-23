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
#include <string.h>
#include <errno.h>
 
#include "log.h"
#include "parse.h"
#include "file.h"
#include "ctb.h"
#include "ctb_internal.h"
#include "datafile.h"
#include "dataarea.h"
#include "brl_dots.h"
#include "cldr.h"
#include "unicode.h"
#include "charset.h"
#include "hostcmd.h"

static const wchar_t *const characterClassNames[] = {
  WS_C("space"),
  WS_C("letter"),
  WS_C("digit"),
  WS_C("punctuation"),
  WS_C("uppercase"),
  WS_C("lowercase"),
  NULL
};

struct CharacterClass {
  struct CharacterClass *next;
  ContractionTableCharacterAttributes attribute;
  BYTE length;
  wchar_t name[1];
};

static const wchar_t *const opcodeNames[CTO_None] = {
  [CTO_CapitalSign] = WS_C("capsign"),
  [CTO_BeginCapitalSign] = WS_C("begcaps"),
  [CTO_EndCapitalSign] = WS_C("endcaps"),

  [CTO_EnglishLetterSign] = WS_C("letsign"),
  [CTO_NumberSign] = WS_C("numsign"),

  [CTO_Literal] = WS_C("literal"),
  [CTO_Always] = WS_C("always"),
  [CTO_Repeatable] = WS_C("repeatable"),

  [CTO_LargeSign] = WS_C("largesign"),
  [CTO_LastLargeSign] = WS_C("lastlargesign"),
  [CTO_WholeWord] = WS_C("word"),
  [CTO_JoinedWord] = WS_C("joinword"),
  [CTO_LowWord] = WS_C("lowword"),
  [CTO_Contraction] = WS_C("contraction"),

  [CTO_SuffixableWord] = WS_C("sufword"),
  [CTO_PrefixableWord] = WS_C("prfword"),
  [CTO_BegWord] = WS_C("begword"),
  [CTO_BegMidWord] = WS_C("begmidword"),
  [CTO_MidWord] = WS_C("midword"),
  [CTO_MidEndWord] = WS_C("midendword"),
  [CTO_EndWord] = WS_C("endword"),

  [CTO_PrePunc] = WS_C("prepunc"),
  [CTO_PostPunc] = WS_C("postpunc"),

  [CTO_BegNum] = WS_C("begnum"),
  [CTO_MidNum] = WS_C("midnum"),
  [CTO_EndNum] = WS_C("endnum"),

  [CTO_Class] = WS_C("class"),
  [CTO_After] = WS_C("after"),
  [CTO_Before] = WS_C("before"),

  [CTO_Replace] = WS_C("replace")
};

typedef struct {
  DataArea *area;

  ContractionTableCharacter *characterTable;
  int characterTableSize;
  int characterEntryCount;

  struct CharacterClass *characterClasses;
  ContractionTableCharacterAttributes characterClassAttribute;

  unsigned char opcodeNameLengths[CTO_None];
} ContractionTableData;

static inline ContractionTableHeader *
getContractionTableHeader (ContractionTableData *ctd) {
  return getDataItem(ctd->area, 0);
}

static ContractionTableCharacter *
getCharacterEntry (wchar_t character, ContractionTableData *ctd) {
  int first = 0;
  int last = ctd->characterEntryCount - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    ContractionTableCharacter *entry = &ctd->characterTable[current];

    if (entry->value < character) {
      first = current + 1;
    } else if (entry->value > character) {
      last = current - 1;
    } else {
      return entry;
    }
  }

  if (ctd->characterEntryCount == ctd->characterTableSize) {
    int newSize = ctd->characterTableSize;
    newSize = newSize? newSize<<1: 0X80;

    {
      ContractionTableCharacter *newTable = realloc(ctd->characterTable, (newSize * sizeof(*newTable)));

      if (!newTable) {
        logMallocError();
        return NULL;
      }

      ctd->characterTable = newTable;
      ctd->characterTableSize = newSize;
    }
  }

  memmove(&ctd->characterTable[first+1],
          &ctd->characterTable[first],
          (ctd->characterEntryCount - first) * sizeof(*ctd->characterTable));
  ctd->characterEntryCount += 1;

  {
    ContractionTableCharacter *entry = &ctd->characterTable[first];
    memset(entry, 0, sizeof(*entry));
    entry->value = character;
    return entry;
  }
}

static int
saveCharacterTable (ContractionTableData *ctd) {
  DataOffset offset;
  if (!ctd->characterEntryCount) return 1;
  if (!saveDataItem(ctd->area, &offset, ctd->characterTable,
                    ctd->characterEntryCount * sizeof(ctd->characterTable[0]),
                    __alignof__(ctd->characterTable[0])))
    return 0;

  {
    ContractionTableHeader *header = getContractionTableHeader(ctd);
    header->characters = offset;
    header->characterCount = ctd->characterEntryCount;
  }

  return 1;
}

static ContractionTableRule *
addByteRule (
  DataFile *file,
  ContractionTableOpcode opcode,
  const DataString *find,
  const ByteOperand *replace,
  ContractionTableCharacterAttributes after,
  ContractionTableCharacterAttributes before,
  ContractionTableData *ctd
) {
  DataOffset ruleOffset;
  int ruleSize = sizeof(ContractionTableRule) - sizeof(find->characters[0]);
  if (find) ruleSize += find->length * sizeof(find->characters[0]);
  if (replace) ruleSize += replace->length;

  if (allocateDataItem(ctd->area, &ruleOffset, ruleSize, __alignof__(ContractionTableRule))) {
    ContractionTableRule *newRule = getDataItem(ctd->area, ruleOffset);

    newRule->opcode = opcode;
    newRule->after = after;
    newRule->before = before;

    if (find) {
      wmemcpy(&newRule->findrep[0], &find->characters[0],
              (newRule->findlen = find->length));
    } else {
      newRule->findlen = 0;
    }

    if (replace) {
      memcpy(&newRule->findrep[newRule->findlen], &replace->bytes[0],
             (newRule->replen = replace->length));
    } else {
      newRule->replen = 0;
    }

    /*link new rule into table.*/
    {
      ContractionTableOffset *offsetAddress;

      if (newRule->findlen == 1) {
        ContractionTableCharacter *ctc = getCharacterEntry(newRule->findrep[0], ctd);
        if (!ctc) return NULL;

        switch (newRule->opcode) {
          case CTO_Repeatable:
            if (ctc->always) break;
            /* fall through */
          case CTO_Always:
            ctc->always = ruleOffset;
            /* fall through */
          default:
            break;
        }

        if (iswupper(ctc->value)) {
          ctc = getCharacterEntry(towlower(ctc->value), ctd);
          if (!ctc) return NULL;
        }

        offsetAddress = &ctc->rules;
      } else {
        offsetAddress = &getContractionTableHeader(ctd)->rules[CTH(newRule->findrep)];
      }

      while (*offsetAddress) {
        ContractionTableRule *currentRule = getDataItem(ctd->area, *offsetAddress);

        if (newRule->findlen > currentRule->findlen) break;

        if (newRule->findlen == currentRule->findlen) {
          if ((newRule->opcode == currentRule->opcode) &&
              (newRule->after == currentRule->after) &&
              (newRule->before == currentRule->before) &&
              (wmemcmp(newRule->findrep, currentRule->findrep, newRule->findlen) == 0))
            break;

          if ((currentRule->opcode == CTO_Always) && (newRule->opcode != CTO_Always))
            break;
        }

        offsetAddress = &currentRule->next;
      }

      newRule->next = *offsetAddress;
      *offsetAddress = ruleOffset;
    }

    return newRule;
  }

  return NULL;
}

static ContractionTableRule *
addTextRule (
  DataFile *file,
  ContractionTableOpcode opcode,
  const DataString *find,
  const DataString *replace,
  ContractionTableCharacterAttributes after,
  ContractionTableCharacterAttributes before,
  ContractionTableData *ctd
) {
  ByteOperand text;
  unsigned char *to = text.bytes;
  const unsigned char *toEnd = to + ARRAY_COUNT(text.bytes);

  const wchar_t *from = replace->characters;
  const wchar_t *fromEnd = from + replace->length;

  while (from < fromEnd) {
    Utf8Buffer buffer;
    size_t length = convertWcharToUtf8(*from++, buffer);

    if (length > (toEnd - to)) {
      reportDataError(file, "replace text too long");
      break;
    }

    to = mempcpy(to, buffer, length);
  }

  text.length = to - text.bytes;
  return addByteRule(file, opcode, find, &text, after, before, ctd);
}

static const struct CharacterClass *
findCharacterClass (const wchar_t *name, int length, ContractionTableData *ctd) {
  const struct CharacterClass *class = ctd->characterClasses;

  while (class) {
    if (length == class->length)
      if (wmemcmp(name, class->name, length) == 0)
        return class;

    class = class->next;
  }

  return NULL;
}

static struct CharacterClass *
addCharacterClass (DataFile *file, const wchar_t *name, int length, ContractionTableData *ctd) {
  struct CharacterClass *class;

  if (ctd->characterClassAttribute) {
    if ((class = malloc(sizeof(*class) + ((length - 1) * sizeof(class->name[0]))))) {
      memset(class, 0, sizeof(*class));
      wmemcpy(class->name, name, (class->length = length));

      class->attribute = ctd->characterClassAttribute;
      ctd->characterClassAttribute <<= 1;

      class->next = ctd->characterClasses;
      ctd->characterClasses = class;
      return class;
    } else {
      logMallocError();
    }
  } else {
    reportDataError(file, "character class table overflow: %.*" PRIws, length, name);
  }

  return NULL;
}

static int
getCharacterClass (DataFile *file, const struct CharacterClass **class, ContractionTableData *ctd) {
  DataOperand operand;

  if (getDataOperand(file, &operand, "character class name")) {
    if ((*class = findCharacterClass(operand.characters, operand.length, ctd))) return 1;
    reportDataError(file, "character class not defined: %.*" PRIws, operand.length, operand.characters);
  }

  return 0;
}

static void
deallocateCharacterClasses (ContractionTableData *ctd) {
  while (ctd->characterClasses) {
    struct CharacterClass *class = ctd->characterClasses;
    ctd->characterClasses = ctd->characterClasses->next;
    free(class);
  }
}

static int
allocateCharacterClasses (ContractionTableData *ctd) {
  const wchar_t *const *name = characterClassNames;

  while (*name) {
    if (!addCharacterClass(NULL, *name, wcslen(*name), ctd)) {
      deallocateCharacterClasses(ctd);
      return 0;
    }

    name += 1;
  }

  return 1;
}

const wchar_t *
getContractionTableOpcodeName (ContractionTableOpcode opcode) {
  return opcodeNames[opcode];
}

static ContractionTableOpcode
getOpcode (DataFile *file, ContractionTableData *ctd) {
  DataOperand operand;

  if (getDataOperand(file, &operand, "opcode")) {
    ContractionTableOpcode opcode;

    for (opcode=0; opcode<CTO_None; opcode+=1)
      if (operand.length == ctd->opcodeNameLengths[opcode])
        if (wmemcmp(operand.characters, opcodeNames[opcode], operand.length) == 0)
          return opcode;

    reportDataError(file, "opcode not defined: %.*" PRIws, operand.length, operand.characters);
  }

  return CTO_None;
}

static int
saveCellsOperand (DataFile *file, DataOffset *offset, const ByteOperand *sequence, ContractionTableData *ctd) {
  if (allocateDataItem(ctd->area, offset, sequence->length+1, __alignof__(BYTE))) {
    BYTE *address = getDataItem(ctd->area, *offset);
    memcpy(address+1, sequence->bytes, (*address = sequence->length));
    return 1;
  }
  return 0;
}

static int
getReplacePattern (DataFile *file, ByteOperand *replace) {
  DataOperand operand;

  if (getDataOperand(file, &operand, "replacement pattern")) {
    if ((operand.length == 1) && (operand.characters[0] == WC_C('='))) {
      replace->length = 0;
      return 1;
    }

    if (parseCellsOperand(file, replace, operand.characters, operand.length)) return 1;
  }

  return 0;
}

static int
getFindText (DataFile *file, DataString *find) {
  return getDataString(file, find, 0, "find text");
}

static int
getReplaceText (DataFile *file, DataString *replace) {
  return getDataString(file, replace, 0, "replace text");
}

static DATA_OPERANDS_PROCESSOR(processContractionTableDirective) {
  ContractionTableData *ctd = data;

  ContractionTableCharacterAttributes after = 0;
  ContractionTableCharacterAttributes before = 0;

  while (1) {
    ContractionTableOpcode opcode;

    switch ((opcode = getOpcode(file, ctd))) {
      case CTO_None:
        break;

      case CTO_Always:
      case CTO_LargeSign:
      case CTO_LastLargeSign:
      case CTO_WholeWord:
      case CTO_JoinedWord:
      case CTO_LowWord:
      case CTO_SuffixableWord:
      case CTO_PrefixableWord:
      case CTO_BegWord:
      case CTO_BegMidWord:
      case CTO_MidWord:
      case CTO_MidEndWord:
      case CTO_EndWord:
      case CTO_PrePunc:
      case CTO_PostPunc:
      case CTO_BegNum:
      case CTO_MidNum:
      case CTO_EndNum:
      case CTO_Repeatable: {
        DataString find;
        ByteOperand replace;

        if (getFindText(file, &find))
          if (getReplacePattern(file, &replace))
            if (!addByteRule(file, opcode, &find, &replace, after, before, ctd))
              return 0;

        break;
      }

      case CTO_Contraction:
      case CTO_Literal: {
        DataString find;
        if (getFindText(file, &find))
          if (!addByteRule(file, opcode, &find, NULL, after, before, ctd))
            return 0;
        break;
      }

      case CTO_CapitalSign: {
        ByteOperand cells;
        if (getCellsOperand(file, &cells, "capital sign")) {
          DataOffset offset;
          if (!saveCellsOperand(file, &offset, &cells, ctd)) return 0;
          getContractionTableHeader(ctd)->capitalSign = offset;
        }
        break;
      }

      case CTO_BeginCapitalSign: {
        ByteOperand cells;
        if (getCellsOperand(file, &cells, "begin capital sign")) {
          DataOffset offset;
          if (!saveCellsOperand(file, &offset, &cells, ctd)) return 0;
          getContractionTableHeader(ctd)->beginCapitalSign = offset;
        }
        break;
      }

      case CTO_EndCapitalSign: {
        ByteOperand cells;
        if (getCellsOperand(file, &cells, "end capital sign")) {
          DataOffset offset;
          if (!saveCellsOperand(file, &offset, &cells, ctd)) return 0;
          getContractionTableHeader(ctd)->endCapitalSign = offset;
        }
        break;
      }

      case CTO_EnglishLetterSign: {
        ByteOperand cells;
        if (getCellsOperand(file, &cells, "letter sign")) {
          DataOffset offset;
          if (!saveCellsOperand(file, &offset, &cells, ctd)) return 0;
          getContractionTableHeader(ctd)->englishLetterSign = offset;
        }
        break;
      }

      case CTO_NumberSign: {
        ByteOperand cells;
        if (getCellsOperand(file, &cells, "number sign")) {
          DataOffset offset;
          if (!saveCellsOperand(file, &offset, &cells, ctd)) return 0;
          getContractionTableHeader(ctd)->numberSign = offset;
        }
        break;
      }

      case CTO_Class: {
        DataOperand name;

        if (getDataOperand(file, &name, "character class name")) {
          const struct CharacterClass *class;

          if ((class = findCharacterClass(name.characters, name.length, ctd))) {
            reportDataError(file, "character class already defined: %.*" PRIws,
                            name.length, name.characters);
          } else if ((class = addCharacterClass(file, name.characters, name.length, ctd))) {
            DataString characters;

            if (getDataString(file, &characters, 0, "characters")) {
              for (int index=0; index<characters.length; index+=1) {
                wchar_t character = characters.characters[index];
                ContractionTableCharacter *entry = getCharacterEntry(character, ctd);
                if (!entry) return 0;
                entry->attributes |= class->attribute;
              }
            }
          }
        }

        break;
      }

      {
        ContractionTableCharacterAttributes *attributes;
        const struct CharacterClass *class;

      case CTO_After:
        attributes = &after;
        goto doClass;
      case CTO_Before:
        attributes = &before;
      doClass:

        if (getCharacterClass(file, &class, ctd)) {
          *attributes |= class->attribute;
          continue;
        }
        break;
      }

      case CTO_Replace: {
        DataString find;
        DataString replace;

        if (getFindText(file, &find))
          if (getReplaceText(file, &replace))
            if (!addTextRule(file, opcode, &find, &replace, after, before, ctd))
              return 0;

        break;
      }

      default:
        reportDataError(file, "unimplemented opcode: %" PRIws, opcodeNames[opcode]);
        break;
    }

    return 1;
  }
}

typedef struct {
  DataFile *file;
  ContractionTableData *ctd;
} AnnotationHandlerData;

static CLDR_ANNOTATION_HANDLER(handleAnnotation) {
  const AnnotationHandlerData *ahd = parameters->data;
  DataFile *file = ahd->file;
  ContractionTableData *ctd = ahd->ctd;

  DataString find;
  const char *findUTF8 = parameters->sequence;
  size_t findSize = strlen(findUTF8) + 1;
  wchar_t findCharacters[findSize];
  {
    const char *byte = findUTF8;
    wchar_t *character = findCharacters;
    convertUtf8ToWchars(&byte, &character, findSize);
    size_t length = character - findCharacters;

    if (length > ARRAY_COUNT(find.characters)) {
      reportDataError(file, "CLDR sequence too long");
      return 1;
    }

    wmemcpy(find.characters, findCharacters, (find.length = length));
  }

  ByteOperand replace;
  {
    const char *string = parameters->name;
    size_t length = strlen(string);
    size_t size = sizeof(replace.bytes);

    if (length > size) {
      reportDataError(file, "CLDR name too long");
      return 1;
    }

    memcpy(replace.bytes, string, (replace.length = length));
  }

  return !!addByteRule(file, CTO_Replace, &find, &replace, 0, 0, ctd);
}

static DATA_OPERANDS_PROCESSOR(processCLDROperands) {
  ContractionTableData *ctd = data;
  DataOperand operand;

  if (getDataOperand(file, &operand, "CLDR annotations file name/path")) {
    char *name = makeUtf8FromWchars(operand.characters, operand.length, NULL);

    if (name) {
      AnnotationHandlerData ahd = {
        .file = file,
        .ctd = ctd
      };

      cldrParseFile(name, handleAnnotation, &ahd);
      free(name);
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processContractionTableOperands) {
  BEGIN_DATA_DIRECTIVE_TABLE
    DATA_NESTING_DIRECTIVES,
    {.name=WS_C("cldr"), .processor=processCLDROperands},
    {.name=NULL, .processor=processContractionTableDirective},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "contraction table directive", data);
}

static void
initializeCommonFields (ContractionTable *table) {
  table->characters.array = NULL;
  table->characters.size = 0;
  table->characters.count = 0;

  table->cache.input.characters = NULL;
  table->cache.input.size = 0;
  table->cache.input.count = 0;

  table->cache.output.cells = NULL;
  table->cache.output.size = 0;
  table->cache.output.count = 0;

  table->cache.offsets.array = NULL;
  table->cache.offsets.size = 0;
  table->cache.offsets.count = 0;
}

static void
destroyCommonFields (ContractionTable *table) {
  if (table->characters.array) {
    free(table->characters.array);
    table->characters.array = NULL;
  }

  if (table->cache.input.characters) {
    free(table->cache.input.characters);
    table->cache.input.characters = NULL;
  }

  if (table->cache.output.cells) {
    free(table->cache.output.cells);
    table->cache.output.cells = NULL;
  }

  if (table->cache.offsets.array) {
    free(table->cache.offsets.array);
    table->cache.offsets.array = NULL;
  }
}

static void
destroyContractionTable_native (ContractionTable *table) {
  destroyCommonFields(table);

  if (table->data.internal.size) {
    free(table->data.internal.header.fields);
    free(table);
  }
}

static const ContractionTableManagementMethods nativeManagementMethods = {
  .destroy = destroyContractionTable_native
};

static ContractionTable *
compileContractionTable_native (const char *fileName) {
  ContractionTable *table = NULL;

  if (setTableDataVariables(CONTRACTION_TABLE_EXTENSION, CONTRACTION_SUBTABLE_EXTENSION)) {
    ContractionTableData ctd;
    memset(&ctd, 0, sizeof(ctd));

    ctd.characterTable = NULL;
    ctd.characterTableSize = 0;
    ctd.characterEntryCount = 0;

    ctd.characterClasses = NULL;
    ctd.characterClassAttribute = 1;

    {
      ContractionTableOpcode opcode;

      for (opcode=0; opcode<CTO_None; opcode+=1)
        ctd.opcodeNameLengths[opcode] = wcslen(opcodeNames[opcode]);
    }

    if ((ctd.area = newDataArea())) {
      if (allocateDataItem(ctd.area, NULL, sizeof(ContractionTableHeader), __alignof__(ContractionTableHeader))) {
        if (allocateCharacterClasses(&ctd)) {
          const DataFileParameters parameters = {
            .processOperands = processContractionTableOperands,
            .data = &ctd
          };

          if (processDataFile(fileName, &parameters)) {
            if (saveCharacterTable(&ctd)) {
              if ((table = malloc(sizeof(*table)))) {
                table->managementMethods = &nativeManagementMethods;
                table->translationMethods = getContractionTableTranslationMethods_native();
                initializeCommonFields(table);

                table->data.internal.header.fields = getContractionTableHeader(&ctd);
                table->data.internal.size = getDataSize(ctd.area);
                resetDataArea(ctd.area);
              } else {
                logMallocError();
              }
            }
          }

          deallocateCharacterClasses(&ctd);
        }
      }

      destroyDataArea(ctd.area);
    }

    if (ctd.characterTable) free(ctd.characterTable);
  }

  return table;
}

int
startContractionCommand (ContractionTable *table) {
  if (!table->data.external.commandStarted) {
    const char *command[] = {table->data.external.command, NULL};
    HostCommandOptions options;

    initializeHostCommandOptions(&options);
    options.asynchronous = 1;
    options.standardInput = &table->data.external.standardInput;
    options.standardOutput = &table->data.external.standardOutput;

    logMessage(LOG_DEBUG, "starting external contraction table: %s", table->data.external.command);
    if (runHostCommand(command, &options) != 0) return 0;
    logMessage(LOG_DEBUG, "external contraction table started: %s", table->data.external.command);

    table->data.external.commandStarted = 1;
  }

  return 1;
}

void
stopContractionCommand (ContractionTable *table) {
  if (table->data.external.commandStarted) {
    fclose(table->data.external.standardInput);
    fclose(table->data.external.standardOutput);

    logMessage(LOG_DEBUG, "external contraction table stopped: %s", table->data.external.command);
    table->data.external.commandStarted = 0;
  }
}

static void
destroyContractionTable_external (ContractionTable *table) {
  stopContractionCommand(table);
  if (table->data.external.input.buffer) free(table->data.external.input.buffer);
  free(table->data.external.command);

  destroyCommonFields(table);
  free(table);
}

static const ContractionTableManagementMethods externalManagementMethods = {
  .destroy = destroyContractionTable_external
};

static ContractionTable *
compileContractionTable_external (const char *fileName) {
  ContractionTable *table;

  if ((table = malloc(sizeof(*table)))) {
    memset(table, 0, sizeof(*table));

    if ((table->data.external.command = strdup(fileName))) {
      table->managementMethods = &externalManagementMethods;
      table->translationMethods = getContractionTableTranslationMethods_external();
      initializeCommonFields(table);

      table->data.external.commandStarted = 0;

      table->data.external.input.buffer = NULL;
      table->data.external.input.size = 0;

      if (startContractionCommand(table)) {
        return table;
      }

      free(table->data.external.command);
    } else {
      logMallocError();
    }

    free(table);
  } else {
    logMallocError();
  }

  return NULL;
}

#ifdef LOUIS_TABLES_DIRECTORY
static void
destroyContractionTable_louis (ContractionTable *table) {
  free(table->data.louis.tableList);

  destroyCommonFields(table);
  free(table);
}

static const ContractionTableManagementMethods louisManagementMethods = {
  .destroy = destroyContractionTable_louis
};

static ContractionTable *
compileContractionTable_louis (const char *fileName) {
  ContractionTable *table;

  if ((table = malloc(sizeof(*table)))) {
    memset(table, 0, sizeof(*table));

    if ((table->data.louis.tableList = strdup(fileName))) {
      table->managementMethods = &louisManagementMethods;
      table->translationMethods = getContractionTableTranslationMethods_louis();
      initializeCommonFields(table);

      return table;
    } else {
      logMallocError();
    }

    free(table);
  } else {
    logMallocError();
  }

  return NULL;
}
#endif /* LOUIS_TABLES_DIRECTORY */

typedef ContractionTable *ContractionTableCompileFunction (const char *fileName);

typedef struct {
  const char *qualifier;
  ContractionTableCompileFunction *compile;
  const char *directory;
} ContractionTableQualifierEntry;

static const ContractionTableQualifierEntry contractionTableQualifierTable[] = {
#ifdef LOUIS_TABLES_DIRECTORY
  { .qualifier = "louis",
    .compile = &compileContractionTable_louis,
    .directory = LOUIS_TABLES_DIRECTORY
  },
#endif /* LOUIS_TABLES_DIRECTORY */

  { .qualifier = NULL }
};

const ContractionTableQualifierEntry *
getContractionTableQualifierEntry (const char **fileName) {
  const ContractionTableQualifierEntry *entry = contractionTableQualifierTable;

  while (entry->qualifier) {
    if (hasQualifier(fileName, entry->qualifier)) return entry;
    entry += 1;
  }

  return NULL;
}

ContractionTable *
compileContractionTable (const char *fileName) {
  ContractionTableCompileFunction *compile = NULL;
  const ContractionTableQualifierEntry *ctq = getContractionTableQualifierEntry(&fileName);

  if (ctq) {
    compile = ctq->compile;
  } else {
    if (!hasNoQualifier(fileName)) {
      logMessage(LOG_ERR, "unsupported contraction table: %s", fileName);
      return NULL;
    }

    if (testProgramPath(fileName)) {
      compile = &compileContractionTable_external;
    } else {
      compile = &compileContractionTable_native;
    }
  }

  return compile(fileName);
}

void
destroyContractionTable (ContractionTable *table) {
  table->managementMethods->destroy(table);
}

char *
ensureContractionTableExtension (const char *path) {
  return ensureFileExtension(path, CONTRACTION_TABLE_EXTENSION);
}

char *
makeContractionTablePath (const char *directory, const char *name) {
  const char *qualifier = name;
  const ContractionTableQualifierEntry *ctq = getContractionTableQualifierEntry(&name);
  if (!ctq) hasQualifier(&name, NULL);
  int qualifierLength = name - qualifier;

  char *path;
  const char *extension;

  if (ctq && ctq->directory) {
    if (!(path = strdup(ctq->directory))) logMallocError();
    extension = NULL;
  } else {
    path = makePath(directory, CONTRACTION_TABLES_SUBDIRECTORY);
    extension = CONTRACTION_TABLE_EXTENSION;
  }

  if (path) {
    char *file = makeFilePath(path, name, extension);

    free(path);
    path = NULL;

    if (file) {
      if (qualifierLength) {
        char buffer[qualifierLength + strlen(file) + 1];
        snprintf(buffer, sizeof(buffer), "%.*s%s",
                 qualifierLength, qualifier, file);

        free(file);
        if (!(file = strdup(buffer))) logMallocError();
      }

      if (file) return file;
    }
  }

  return NULL;
}
