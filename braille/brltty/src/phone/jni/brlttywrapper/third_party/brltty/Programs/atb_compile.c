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

#include "file.h"
#include "datafile.h"
#include "dataarea.h"
#include "atb.h"
#include "atb_internal.h"

typedef struct {
  unsigned char attribute;
  unsigned char operation;
} DotData;

typedef struct {
  DataArea *area;
  DotData dots[8];
} AttributesTableData;

static inline AttributesTableHeader *
getAttributesTableHeader (AttributesTableData *atd) {
  return getDataItem(atd->area, 0);
}

static int
makeAttributesToDots (AttributesTableData *atd) {
  unsigned char *table = getAttributesTableHeader(atd)->attributesToDots;
  unsigned char bits = 0;

  do {
    unsigned char *cell = &table[bits];
    unsigned char dotIndex;

    *cell = 0;
    for (dotIndex=0; dotIndex<BRL_DOT_COUNT; dotIndex+=1) {
      const DotData *dot = &atd->dots[dotIndex];
      int isSet = bits & dot->attribute;
      if (!isSet == !dot->operation) *cell |= brlDotBits[dotIndex];
    }
  } while ((bits += 1));

  return 1;
}

static int
parseAttributeOperand (DataFile *file, unsigned char *bit, unsigned char *operation, const wchar_t *characters, int length) {
  if (length > 1) {
    static const wchar_t operators[] = {'~', '='};
    const wchar_t *operator = wmemchr(operators, characters[0], ARRAY_COUNT(operators));

    if (operator) {
      typedef struct {
        const wchar_t *name;
        unsigned char bit;
      } AttributeEntry;

      static const AttributeEntry attributeTable[] = {
        {WS_C("fg-blue")  , 0X01},
        {WS_C("fg-green") , 0X02},
        {WS_C("fg-red")   , 0X04},
        {WS_C("fg-bright"), 0X08},
        {WS_C("bg-blue")  , 0X10},
        {WS_C("bg-green") , 0X20},
        {WS_C("bg-red")   , 0X40},
        {WS_C("blink")    , 0X80},

        {WS_C("bit0"), 0X01},
        {WS_C("bit1"), 0X02},
        {WS_C("bit2"), 0X04},
        {WS_C("bit3"), 0X08},
        {WS_C("bit4"), 0X10},
        {WS_C("bit5"), 0X20},
        {WS_C("bit6"), 0X40},
        {WS_C("bit7"), 0X80},

        {WS_C("bit01"), 0X01},
        {WS_C("bit02"), 0X02},
        {WS_C("bit04"), 0X04},
        {WS_C("bit08"), 0X08},
        {WS_C("bit10"), 0X10},
        {WS_C("bit20"), 0X20},
        {WS_C("bit40"), 0X40},
        {WS_C("bit80"), 0X80},

        {NULL, 0X00}
      };

      const AttributeEntry *attribute = attributeTable;

      characters += 1, length -= 1;

      while (attribute->name) {
        if ((length == wcslen(attribute->name)) &&
            (wmemcmp(characters, attribute->name, length) == 0)) {
          *bit = attribute->bit;
          *operation = operator - operators;
          return 1;
        }

        attribute += 1;
      }

      reportDataError(file, "invalid attribute name: %.*" PRIws, length, characters);
    } else {
      reportDataError(file, "invalid attribute operator: %.1" PRIws, &characters[0]);
    }
  }

  return 0;
}

static int
getAttributeOperand (DataFile *file, unsigned char *bit, unsigned char *operation) {
  DataOperand attribute;

  if (getDataOperand(file, &attribute, "attribute"))
    if (parseAttributeOperand(file, bit, operation, attribute.characters, attribute.length))
      return 1;

  return 0;
}

static DATA_OPERANDS_PROCESSOR(processDotOperands) {
  AttributesTableData *atd = data;
  int dotIndex;

  if (getDotOperand(file, &dotIndex)) {
    DotData *dot = &atd->dots[dotIndex];

    if (getAttributeOperand(file, &dot->attribute, &dot->operation)) return 1;
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processAttributesTableOperands) {
  BEGIN_DATA_DIRECTIVE_TABLE
    DATA_NESTING_DIRECTIVES,
    {.name=WS_C("dot"), .processor=processDotOperands},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "attributes table directive", data);
}

AttributesTable *
compileAttributesTable (const char *name) {
  AttributesTable *table = NULL;

  if (setTableDataVariables(ATTRIBUTES_TABLE_EXTENSION, ATTRIBUTES_SUBTABLE_EXTENSION)) {
    AttributesTableData atd;
    memset(&atd, 0, sizeof(atd));

    if ((atd.area = newDataArea())) {
      if (allocateDataItem(atd.area, NULL, sizeof(AttributesTableHeader), __alignof__(AttributesTableHeader))) {
        const DataFileParameters parameters = {
          .processOperands = processAttributesTableOperands,
          .data = &atd
        };

        if (processDataFile(name, &parameters)) {
          if (makeAttributesToDots(&atd)) {
            if ((table = malloc(sizeof(*table)))) {
              table->header.fields = getAttributesTableHeader(&atd);
              table->size = getDataSize(atd.area);
              resetDataArea(atd.area);
            }
          }
        }
      }

      destroyDataArea(atd.area);
    }
  }

  return table;
}

void
destroyAttributesTable (AttributesTable *table) {
  if (table->size) {
    free(table->header.fields);
    free(table);
  }
}

char *
ensureAttributesTableExtension (const char *path) {
  return ensureFileExtension(path, ATTRIBUTES_TABLE_EXTENSION);
}

char *
makeAttributesTablePath (const char *directory, const char *name) {
  char *subdirectory = makePath(directory, ATTRIBUTES_TABLES_SUBDIRECTORY);

  if (subdirectory) {
    char *file = makeFilePath(subdirectory, name, ATTRIBUTES_TABLE_EXTENSION);

    free(subdirectory);
    if (file) return file;
  }

  return NULL;
}
