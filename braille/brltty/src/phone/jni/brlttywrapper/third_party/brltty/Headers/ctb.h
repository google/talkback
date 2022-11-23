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

#ifndef BRLTTY_INCLUDED_CTB
#define BRLTTY_INCLUDED_CTB

#include "ctb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct ContractionTableStruct ContractionTable;

extern ContractionTable *compileContractionTable (const char *fileName);
extern void destroyContractionTable (ContractionTable *table);
extern void contractText (
  ContractionTable *contractionTable, /* Pointer to translation table */
  const wchar_t *inputBuffer, /* What is to be translated */
  int *inputLength, /* Its length */
  unsigned char *outputBuffer, /* Where the translation is to go */
  int *outputLength, /* length of this area */
  int *offsetsMap, /* Array of offsets of translated chars in source */
  int cursorOffset /* Position of coursor in source */
);

extern char *ensureContractionTableExtension (const char *path);
extern char *makeContractionTablePath (const char *directory, const char *name);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CTB */
