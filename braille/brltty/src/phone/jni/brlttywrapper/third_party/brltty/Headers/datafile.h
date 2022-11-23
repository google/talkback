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

#ifndef BRLTTY_INCLUDED_DATAFILE
#define BRLTTY_INCLUDED_DATAFILE

#include <stdio.h>

#include "variables.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int setBaseDataVariables (const VariableInitializer *initializers);
extern int setTableDataVariables (const char *tableExtension, const char *subtableExtension);

extern FILE *openDataFile (const char *path, const char *mode, int optional);

typedef struct DataFileStruct DataFile;

#define DATA_OPERANDS_PROCESSOR(name) int name (DataFile *file, void *data)
typedef DATA_OPERANDS_PROCESSOR(DataOperandsProcessor);

typedef enum {
  DFO_NO_COMMENTS = 0X01
} DataFileOptions;

typedef struct {
  DataOperandsProcessor *processOperands;
  void (*logFileName) (const char *name, void *data);
  void *data;
  unsigned char options;
} DataFileParameters;

extern int processDataFile (const char *name, const DataFileParameters *parameters);
extern void reportDataError (DataFile *file, char *format, ...) PRINTF(2, 3);

extern int processDataStream (
  DataFile *includer,
  FILE *stream, const char *name,
  const DataFileParameters *parameters
);

extern int compareKeyword (const wchar_t *keyword, const wchar_t *characters, size_t count);
extern int compareKeywords (const wchar_t *keyword1, const wchar_t *keyword2);

extern int isKeyword (const wchar_t *keyword, const wchar_t *characters, size_t count);
extern int isNumber (int *number, const wchar_t *characters, int length);
extern int isHexadecimalDigit (wchar_t character, int *value, int *shift);
extern int isOctalDigit (wchar_t character, int *value, int *shift);

extern int findDataOperand (DataFile *file, const char *description);
extern int getDataCharacter (DataFile *file, wchar_t *character);
extern int ungetDataCharacters (DataFile *file, unsigned int count);

typedef struct {
  const wchar_t *characters;
  int length;
} DataOperand;

extern int getDataOperand (DataFile *file, DataOperand *operand, const char *description);
extern int getTextOperand (DataFile *file, DataOperand *text, const char *description);
extern void getTextRemaining (DataFile *file, DataOperand *text);

typedef struct {
  unsigned char length;
  wchar_t characters[0XFF];
} DataString;

extern int parseDataString (DataFile *file, DataString *string, const wchar_t *characters, int length, int noUnicode);
extern int getDataString (DataFile *file, DataString *string, int noUnicode, const char *description);

extern int writeHexadecimalCharacter (FILE *stream, wchar_t character);
extern int writeEscapedCharacter (FILE *stream, wchar_t character);
extern int writeEscapedCharacters (FILE *stream, const wchar_t *characters, size_t count);

typedef struct {
  unsigned char length;
  unsigned char bytes[0XFF];
} ByteOperand;

#define CELLS_OPERAND_DELIMITER WC_C('-')
#define CELLS_OPERAND_SPACE WC_C('0')

extern int parseCellsOperand (DataFile *file, ByteOperand *cells, const wchar_t *characters, int length);
extern int getCellsOperand (DataFile *file, ByteOperand *cells, const char *description);

extern int writeDots (FILE *stream, unsigned char cell);
extern int writeDotsCell (FILE *stream, unsigned char cell);
extern int writeDotsCells (FILE *stream, const unsigned char *cells, size_t count);

extern int writeUtf8Cell (FILE *stream, unsigned char cell);
extern int writeUtf8Cells (FILE *stream, const unsigned char *cells, size_t count);

typedef struct {
  const wchar_t *name;
  DataOperandsProcessor *processor;
  unsigned unconditional:1;
} DataDirective;

typedef struct {
  struct {
    const DataDirective *table;
    size_t count;
  } const unsorted;

  struct {
    const DataDirective **table;
    size_t count;
  } sorted;

  const DataDirective *unnamed;
} DataDirectives;

#define BEGIN_DATA_DIRECTIVE_TABLE \
  static const DataDirective unsortedDirectives[] = {

#define END_DATA_DIRECTIVE_TABLE }; \
  static DataDirectives directives = { \
    .unsorted = { \
      .table = unsortedDirectives, \
      .count = ARRAY_COUNT(unsortedDirectives) \
    }, \
    \
    .sorted = { \
      .table = NULL, \
      .count = 0 \
    }, \
    \
    .unnamed = NULL \
  };

extern int processDirectiveOperand (DataFile *file, DataDirectives *directives, const char *description, void *data);

#define DATA_CONDITION_TESTER(name) int name (DataFile *file, const DataOperand *identifier, void *data)
typedef DATA_CONDITION_TESTER(DataConditionTester);

extern int processConditionOperands (
  DataFile *file,
  DataConditionTester *testCondition, int negateCondition,
  const char *description, void *data
);

extern DATA_OPERANDS_PROCESSOR(processIncludeOperands);
extern int includeDataFile (DataFile *file, const wchar_t *name, int length);

#define DATA_NESTING_DIRECTIVES \
  {.name=WS_C("include"), .processor=processIncludeOperands}

extern DATA_OPERANDS_PROCESSOR(processElseOperands);
extern DATA_OPERANDS_PROCESSOR(processEndIfOperands);

#define DATA_CONDITION_DIRECTIVES \
  {.name=WS_C("else"), .processor=processElseOperands, .unconditional=1}, \
  {.name=WS_C("endif"), .processor=processEndIfOperands, .unconditional=1}

extern DATA_OPERANDS_PROCESSOR(processIfVarOperands);
extern DATA_OPERANDS_PROCESSOR(processIfNotVarOperands);
extern DATA_OPERANDS_PROCESSOR(processBeginVariablesOperands);
extern DATA_OPERANDS_PROCESSOR(processEndVariablesOperands);
extern DATA_OPERANDS_PROCESSOR(processListVariablesOperands);
extern DATA_OPERANDS_PROCESSOR(processAssignDefaultOperands);
extern DATA_OPERANDS_PROCESSOR(processAssignOperands);

#define DATA_VARIABLE_DIRECTIVES \
  {.name=WS_C("ifvar"), .processor=processIfVarOperands, .unconditional=1}, \
  {.name=WS_C("ifnotvar"), .processor=processIfNotVarOperands, .unconditional=1}, \
  {.name=WS_C("beginvariables"), .processor=processBeginVariablesOperands}, \
  {.name=WS_C("endvariables"), .processor=processEndVariablesOperands}, \
  {.name=WS_C("listvariables"), .processor=processListVariablesOperands}, \
  {.name=WS_C("assigndefault"), .processor=processAssignDefaultOperands}, \
  {.name=WS_C("assign"), .processor=processAssignOperands}

#define BRL_DOT_COUNT 8
extern const wchar_t brlDotNumbers[BRL_DOT_COUNT];
extern const unsigned char brlDotBits[BRL_DOT_COUNT];
extern int brlDotNumberToIndex (wchar_t number, int *index);
extern int brlDotBitToIndex (unsigned char bit, int *index);

extern int getDotOperand (DataFile *file, int *index);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_DATAFILE */
