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

/* Source file for range list management module */
/* For a description of what each function does, see rangelist.h */

#include <stdio.h>

#include "brlapi_keyranges.h"
#include "log.h"

static int inKeyrange(KeyrangeList *l, KeyrangeElem e)
{
  uint32_t flags = KeyrangeFlags(e);
  uint32_t val = KeyrangeVal(e);
  return (l->minVal <= val && val <= l->maxVal && (flags | l->minFlags) == flags && ((flags & ~l->maxFlags) == 0));
}

/* Function : createKeyrange */
static KeyrangeList **createKeyrange(KeyrangeList **p, uint32_t minFlags, uint32_t minVal, uint32_t maxFlags, uint32_t maxVal, KeyrangeList *n)
{
  KeyrangeList *c = malloc(sizeof(KeyrangeList));
  if (c==NULL) return NULL;
  c->minFlags = minFlags; c->minVal = minVal;
  c->maxFlags = maxFlags; c->maxVal = maxVal;
  c->next = n;
  *p = c;
  return &c->next;
}

/* Function : freeKeyrange */
static void freeKeyrange(KeyrangeList **p, KeyrangeList *c)
{
  if (c==NULL) return;
  *p = c->next;
  free(c);
}

/* Function : freeKeyrangeList */
void freeKeyrangeList(KeyrangeList **l)
{
  KeyrangeList *p1, *p2;
  if (l==NULL) return;
  p2 = *l;
  while (p2!=NULL) {
    p1 = p2;
    p2 = p1->next;
    free(p1);
  }
  *l = NULL;
}

/* Function : inKeyrangeList */
KeyrangeList *inKeyrangeList(KeyrangeList *l, KeyrangeElem n)
{
  KeyrangeList *c = l;
  while (c!=NULL) {
    if (inKeyrange(c, n)) return c;
    c = c->next;
  }
  return NULL;
}

/* Function : DisplayKeyrangeList */
void DisplayKeyrangeList(KeyrangeList *l)
{
  if (l==NULL) printf("emptyset");
  else {
    KeyrangeList *c = l;
    while (1) {
      printf("[%lx(%lx)..%lx(%lx)]",(unsigned long)c->minVal,(unsigned long)c->minFlags,(unsigned long)c->maxVal,(unsigned long)c->maxFlags);
      if (c->next==NULL) break;
      printf(",");
      c = c->next;
    }
  }
  printf("\n");
}

/* Function : addKeyrange */
int addKeyrange(KeyrangeElem x0, KeyrangeElem y0, KeyrangeList **l)
{
  KeyrangeList *c;
  uint32_t minFlags = KeyrangeFlags(x0) & KeyrangeFlags(y0);
  uint32_t maxFlags = KeyrangeFlags(x0) | KeyrangeFlags(y0);
  uint32_t minVal   = MIN(KeyrangeVal(x0), KeyrangeVal(y0));
  uint32_t maxVal   = MAX(KeyrangeVal(x0), KeyrangeVal(y0));
  KeyrangeElem min = KeyrangeElem(minFlags, minVal);
  KeyrangeElem max = KeyrangeElem(maxFlags, maxVal);

  logMessage(LOG_DEBUG, "adding range [%"PRIx32"(%"PRIx32")..%"PRIx32"(%"PRIx32")]", minVal, minFlags, maxVal, maxFlags);

  c = *l;
  while (c) {
    if (inKeyrange(c, min) && inKeyrange(c, max))
      /* Falls completely within an existing range */
      return 0;

    if (c->minVal <= maxVal && maxVal <= c->maxVal && minFlags == c->minFlags && maxFlags == c->maxFlags) {
      /* May just change lower bound */
      /* Note that minVal can't be >= c->minVal */
      c->minVal = minVal;
      return 0;
    }

    if (c->minVal <= minVal && minVal <= c->maxVal && minFlags == c->minFlags && maxFlags == c->maxFlags) {
      /* May just change upper bound */
      /* Note that maxVal can't be <= c->maxVal */
      c->maxVal = maxVal;
      return 0;
    }

    c = c->next;
  }

  /* Else things are not easy, just add */
  if ((createKeyrange(l,minFlags,minVal,maxFlags,maxVal,*l)) == NULL) return -1;
  return 0;
}

int removeKeyrange(KeyrangeElem x0, KeyrangeElem y0, KeyrangeList **l)
{
  uint32_t minFlags = KeyrangeFlags(x0) & KeyrangeFlags(y0);
  uint32_t maxFlags = KeyrangeFlags(x0) | KeyrangeFlags(y0);
  uint32_t minVal   = MIN(KeyrangeVal(x0), KeyrangeVal(y0));
  uint32_t maxVal   = MAX(KeyrangeVal(x0), KeyrangeVal(y0));
  KeyrangeList *c, **p, *tmp;
  int i;

  if ((l==NULL) || (*l==NULL)) return 0;

  logMessage(LOG_DEBUG, "removing range [%"PRIx32"(%"PRIx32")..%"PRIx32"(%"PRIx32")]", minVal, minFlags, maxVal, maxFlags);

  /* Need to intersect with every range */
  p = l; c = *p;
  while (c) {
    if (c->minVal > maxVal || c->maxVal < minVal ||
        !(c->maxFlags | ~minFlags) || !(~c->minFlags | maxFlags)) {
      /* don't intersect */
      p = &c->next;
      c = *p;
      continue;
    }

    if (minVal <= c->minVal && maxVal >= c->maxVal &&
        (c->minFlags | minFlags) == c->minFlags &&
        (c->maxFlags & ~maxFlags) == 0) {
      /* range falls completely in deletion range, just drop it */
      tmp = c; c = c->next;
      freeKeyrange(p,tmp);
      continue;
    }

    /* Partly intersect */

    if (c->minVal < minVal) {
      /* lower part should be kept intact, save it. */
      p = createKeyrange(p, c->minFlags, c->minVal, c->maxFlags, minVal - 1, c);
      if (p == NULL) return -1;
      c->minVal = minVal;
    }

    if (c->maxVal > maxVal) {
      /* upper part should be kept intact, save it. */
      p = createKeyrange(p, c->minFlags, maxVal + 1, c->maxFlags, c->maxVal, c);
      if (p == NULL) return -1;
      c->maxVal = maxVal;
    }

    /* Now values are the same, tinker with flags */
    for (i=0; i<32; i++) {
      uint32_t mask = 1<<i;

      if ((!(c->maxFlags & mask) &&  (minFlags & mask)) ||
          ( (c->minFlags & mask) && !(maxFlags & mask)))
	/* don't intersect on this flag */
	continue;

      if (!(c->minFlags & mask) &&  (minFlags & mask)) {
        /* && (c->maxFlags & mask) */
	/* part without flag i should be kept intact, save it */
        p = createKeyrange(p, c->minFlags, c->minVal, c->maxFlags & ~mask, c->maxVal, c);
        if (p == NULL) return -1;
	/* now handling part with flag i */
        c->minFlags |= mask;
      }

      if ( (c->maxFlags & mask) && !(maxFlags & mask)) {
        /* && !(c->minFlags & mask) */
	/* part with flag i should be kept intact, save it */
        p = createKeyrange(p, c->minFlags | mask, c->minVal, c->maxFlags, c->maxVal, c);
        if (p == NULL) return -1;
	/* now handling part without flag i */
        c->maxFlags &= ~mask;
      }

      if (!(c->maxFlags | ~minFlags) || !(~c->minFlags | maxFlags))
        /* don't intersect any more*/
	break;
    }
    if (i<32) {
      /* don't intersect any more, keep it */
      break;
    } else {
      /* remaining intersection, drop it */
      tmp = c; c = c->next;
      freeKeyrange(p,tmp);
    }
  }

  return 0;
}
