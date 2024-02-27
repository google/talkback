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

#ifndef BRLAPI_INCLUDED_KEYRANGES
#define BRLAPI_INCLUDED_KEYRANGES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* Header file for the range list management module */

#include "prologue.h"

typedef uint64_t KeyrangeElem;

#define KeyrangeFlags(v) (((v) >> 32) & 0xffffffffull)
#define KeyrangeVal(v) ((v) & 0xffffffffull)

#define KeyrangeElem(flags,val) (((KeyrangeElem)(flags) << 32) | (val))


typedef struct KeyrangeList {
  uint32_t minFlags, maxFlags;
  uint32_t minVal, maxVal;
  struct KeyrangeList *next;
} KeyrangeList;

/* Function : freeKeyrangeList */
/* Frees a whole list */
/* If you want to destroy a whole list, call this function, rather than */
/* calling freeKeyrange on each element, since th latter cares about links */
/* and hence is slower */
extern void freeKeyrangeList(KeyrangeList **l);

/* Function : inKeyrangeList */
/* Determines if the range list l contains x */
/* If yes, returns the adress of the cell [a..b] such that a<=x<=b */
/* If no, returns NULL */
extern KeyrangeList *inKeyrangeList(KeyrangeList *l, KeyrangeElem n);

/* Function : displayKeyrangeList */
/* Prints a range list on stdout */
/* This is for debugging only */
extern void displayKeyrangeList(KeyrangeList *l);

/* Function : addKeyrange */
/* Adds a range to a range list */
/* Return 0 if success, -1 if an error occurs */
extern int addKeyrange(KeyrangeElem x0, KeyrangeElem y0, KeyrangeList **l);

/* Function : removeKeyrange */
/* Removes a range from a range list */
/* Returns 0 if success, -1 if failure */
extern int removeKeyrange(KeyrangeElem x0, KeyrangeElem y0, KeyrangeList **l);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLAPI_INCLUDED_KEYRANGES */
