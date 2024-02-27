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

#ifndef BRLTTY_INCLUDED_CTB_TYPES
#define BRLTTY_INCLUDED_CTB_TYPES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define CTB_NO_OFFSET -1
#define CTB_NO_CURSOR -1

typedef enum {
  CTB_CAP_NONE,
  CTB_CAP_SIGN,
  CTB_CAP_DOT7
} CTB_CapitalizationMode;

typedef struct {
  struct {
    wchar_t *characters;
    unsigned int size;
    unsigned int count;
    unsigned int consumed;
  } input;

  struct {
    unsigned char *cells;
    unsigned int size;
    unsigned int count;
    unsigned int maximum;
  } output;

  struct {
    int *array;
    unsigned int size;
    unsigned int count;
  } offsets;

  int cursorOffset;
  unsigned char expandCurrentWord;
  unsigned char capitalizationMode;
} ContractionCache;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CTB_TYPES */
