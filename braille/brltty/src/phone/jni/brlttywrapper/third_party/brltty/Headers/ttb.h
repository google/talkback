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

#ifndef BRLTTY_INCLUDED_TTB
#define BRLTTY_INCLUDED_TTB

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct TextTableStruct TextTable;

extern TextTable *textTable;

extern TextTable *compileTextTable (const char *name);
extern void destroyTextTable (TextTable *table);

extern char *ensureTextTableExtension (const char *path);
extern char *makeTextTablePath (const char *directory, const char *name);

extern char *selectTextTable (const char *directory);
extern int replaceTextTable (const char *directory, const char *name);

extern unsigned char convertCharacterToDots (TextTable *table, wchar_t character);
extern wchar_t convertDotsToCharacter (TextTable *table, unsigned char dots);

extern void setTryBaseCharacter (TextTable *table, unsigned char yes);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_TTB */
