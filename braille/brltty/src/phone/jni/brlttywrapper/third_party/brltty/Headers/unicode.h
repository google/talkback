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

#ifndef BRLTTY_INCLUDED_UNICODE
#define BRLTTY_INCLUDED_UNICODE

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef HAVE_WCHAR_H 
#define UNICODE_REPLACEMENT_CHARACTER 0XFFFD
#else /* HAVE_WCHAR_H */
#define UNICODE_REPLACEMENT_CHARACTER '?'
#endif /* HAVE_WCHAR_H */

#define UNICODE_ZERO_WIDTH_SPACE 0X200B
#define UNICODE_BYTE_ORDER_MARK 0XFEFF

#define UNICODE_BRAILLE_ROW 0X2800

#define UNICODE_SURROGATE_BEGIN 0XD800
#define UNICODE_SURROGATE_END 0XDFFF
#define UNICODE_SURROGATE_SHIFT 10
#define UNICODE_SURROGATE_LOW (1 << UNICODE_SURROGATE_SHIFT)
#define UNICODE_SURROGATE_MASK (UNICODE_SURROGATE_LOW - 1)

#define UNICODE_CELL_BITS 8
#define UNICODE_ROW_BITS 8
#define UNICODE_PLANE_BITS 8
#define UNICODE_GROUP_BITS 7

#define UNICODE_CELL_SHIFT 0
#define UNICODE_ROW_SHIFT (UNICODE_CELL_SHIFT + UNICODE_CELL_BITS)
#define UNICODE_PLANE_SHIFT (UNICODE_ROW_SHIFT + UNICODE_ROW_BITS)
#define UNICODE_GROUP_SHIFT (UNICODE_PLANE_SHIFT + UNICODE_PLANE_BITS)

#define UNICODE_CELLS_PER_ROW (1 << UNICODE_CELL_BITS)
#define UNICODE_ROWS_PER_PLANE (1 << UNICODE_ROW_BITS)
#define UNICODE_PLANES_PER_GROUP (1 << UNICODE_PLANE_BITS)
#define UNICODE_GROUP_COUNT (1 << UNICODE_GROUP_BITS)

#define UNICODE_CELL_MAXIMUM (UNICODE_CELLS_PER_ROW - 1)
#define UNICODE_ROW_MAXIMUM (UNICODE_ROWS_PER_PLANE - 1)
#define UNICODE_PLANE_MAXIMUM (UNICODE_PLANES_PER_GROUP - 1)
#define UNICODE_GROUP_MAXIMUM (UNICODE_GROUP_COUNT - 1)

#define UNICODE_CELL_MASK (UNICODE_CELL_MAXIMUM << UNICODE_CELL_SHIFT)
#define UNICODE_ROW_MASK (UNICODE_ROW_MAXIMUM << UNICODE_ROW_SHIFT)
#define UNICODE_PLANE_MASK (UNICODE_PLANE_MAXIMUM << UNICODE_PLANE_SHIFT)
#define UNICODE_GROUP_MASK (UNICODE_GROUP_MAXIMUM << UNICODE_GROUP_SHIFT)
#define UNICODE_CHARACTER_MASK (UNICODE_CELL_MASK | UNICODE_ROW_MASK | UNICODE_PLANE_MASK | UNICODE_GROUP_MASK)

#define UNICODE_CELL_NUMBER(c) (((c) & UNICODE_CELL_MASK) >> UNICODE_CELL_SHIFT)
#define UNICODE_ROW_NUMBER(c) (((c) & UNICODE_ROW_MASK) >> UNICODE_ROW_SHIFT)
#define UNICODE_PLANE_NUMBER(c) (((c) & UNICODE_PLANE_MASK) >> UNICODE_PLANE_SHIFT)
#define UNICODE_GROUP_NUMBER(c) (((c) & UNICODE_GROUP_MASK) >> UNICODE_GROUP_SHIFT)
#define UNICODE_CHARACTER(group,plane,row,cell) (((group) << UNICODE_GROUP_SHIFT) | ((plane) << UNICODE_PLANE_SHIFT) | ((row) << UNICODE_ROW_SHIFT) | ((cell) << UNICODE_CELL_SHIFT))

extern int getCharacterName (wchar_t character, char *buffer, size_t size);
extern int getCharacterByName (wchar_t *character, const char *name);

extern int getCharacterAlias (wchar_t character, char *buffer, size_t size);
extern int getCharacterByAlias (wchar_t *character, const char *alias);

extern int getCharacterWidth (wchar_t character);

extern int isBrailleCharacter (wchar_t character);

extern int normalizeCharacters (
  size_t *length, const wchar_t *characters,
  wchar_t *buffer, unsigned int *map
);

extern wchar_t getBaseCharacter (wchar_t character);
extern wchar_t getTransliteratedCharacter (wchar_t character);

typedef int CharacterHandler (wchar_t character, void *data);
extern int handleBestCharacter (wchar_t character, CharacterHandler handleCharacter, void *data);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_UNICODE */
