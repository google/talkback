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

#ifndef BRLTTY_INCLUDED_TTB_COMPILE
#define BRLTTY_INCLUDED_TTB_COMPILE

#include <stdio.h>

#include "datafile.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct TextTableDataStruct TextTableData;
extern TextTableData *newTextTableData (void);
extern void destroyTextTableData (TextTableData *ttd);

extern TextTableData *processTextTableLines (FILE *stream, const char *name, DataOperandsProcessor *processOperands);
extern TextTable *makeTextTable (TextTableData *ttd);

typedef TextTableData *TextTableProcessor (FILE *stream, const char *name);
extern TextTableProcessor processTextTableStream;
extern TextTableProcessor processGnomeBrailleStream;
extern TextTableProcessor processLibLouisStream;

extern void *getTextTableItem (TextTableData *ttd, TextTableOffset offset);
extern TextTableHeader *getTextTableHeader (TextTableData *ttd);
extern const unsigned char *getUnicodeCell (TextTableData *ttd, wchar_t character);

extern int setTextTableInput (TextTableData *ttd, wchar_t character, unsigned char dots);
extern int setTextTableGlyph (TextTableData *ttd, wchar_t character, unsigned char dots);
extern int setTextTableCharacter (TextTableData *ttd, wchar_t character, unsigned char dots);
extern void unsetTextTableCharacter (TextTableData *ttd, wchar_t character);
extern int setTextTableByte (TextTableData *ttd, unsigned char byte, unsigned char dots);
extern int addTextTableAlias (TextTableData *ttd, wchar_t from, wchar_t to);

extern UnicodeRowEntry *getUnicodeRowEntry (TextTableData *ttd, wchar_t character, int allocate);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_TTB_COMPILE */
