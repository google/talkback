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

#ifndef BRLTTY_INCLUDED_BRL_DOTS
#define BRLTTY_INCLUDED_BRL_DOTS

#include <strings.h>

#ifdef __MINGW32__
extern int ffs (int i);
#endif /* __MINGW32__ */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* The bits for each braille dot as defined by the ISO 11548-1 standard.
 *
 * From least- to most-significant octal digit:
 * +  the first contains dots 1-3
 * +  the second contains dots 4-6
 * +  the third contains dots 7-8
 * 
 * Here are a few ways to illustrate a braille cell:
 *    By Dot   By Bit   As Octal
 *    Number   Number    Digits
 *     1  4     0  3    001  010
 *     2  5     1  4    002  020
 *     3  6     2  5    004  040
 *     7  8     6  7    100  200
 */

typedef unsigned char BrlDots;

#define BRL_DOT_COUNT 8

#define BRL_DOT(number) (BrlDots)(1 << ((number) - 1))
#define BRL_DOT_1 BRL_DOT(1) /* upper-left dot of standard braille cell */
#define BRL_DOT_2 BRL_DOT(2) /* middle-left dot of standard braille cell */
#define BRL_DOT_3 BRL_DOT(3) /* lower-left dot of standard braille cell */
#define BRL_DOT_4 BRL_DOT(4) /* upper-right dot of standard braille cell */
#define BRL_DOT_5 BRL_DOT(5) /* middle-right dot of standard braille cell */
#define BRL_DOT_6 BRL_DOT(6) /* lower-right dot of standard braille cell */
#define BRL_DOT_7 BRL_DOT(7) /* lower-left dot of computer braille cell */
#define BRL_DOT_8 BRL_DOT(8) /* lower-right dot of computer braille cell */

static inline BrlDots
getLeftDots (unsigned char cell) {
  return cell & (BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_7);
}

static inline BrlDots
getRightDots (unsigned char cell) {
  return cell & (BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6 | BRL_DOT_8);
}

static inline BrlDots
getRightDotsToLeftDots (BrlDots cell) {
  unsigned char ret = 0;
  if (cell & BRL_DOT_4) ret |= BRL_DOT_1;
  if (cell & BRL_DOT_5) ret |= BRL_DOT_2;
  if (cell & BRL_DOT_6) ret |= BRL_DOT_3;
  if (cell & BRL_DOT_8) ret |= BRL_DOT_7;
  return ret;
}

static inline BrlDots
getLeftDotsToRightDots (BrlDots cell) {
  unsigned char ret = 0;
  if (cell & BRL_DOT_1) ret |= BRL_DOT_4;
  if (cell & BRL_DOT_2) ret |= BRL_DOT_5;
  if (cell & BRL_DOT_3) ret |= BRL_DOT_6;
  if (cell & BRL_DOT_7) ret |= BRL_DOT_8;
  return ret;
}

static inline BrlDots
brlNumberToDot (char number) {
  return ((number >= '1') && (number <= '8'))? BRL_DOT(number - '0'): 0;
}

static inline char
brlDotToNumber (BrlDots dot) {
  int shift = ffs(dot);
  return shift? ((char)shift + '0'): 0;
}

typedef BrlDots BrlDotTable[BRL_DOT_COUNT];

typedef char BrlDotNumbersBuffer[BRL_DOT_COUNT + 1];

static inline unsigned int
brlDotsToNumbers (BrlDots dots, BrlDotNumbersBuffer numbers) {
  char *number = numbers;

  while (dots) {
    int shift = ffs(dots) - 1;
    dots -= 1 << shift;
    *number++ = (char)shift + '1';
  }

  *number = 0;
  return (unsigned int)(number - numbers);
}

/* The Unicode row used for literal braille dot representations. */
#define BRL_UNICODE_ROW 0X2800

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BRL_DOTS */
