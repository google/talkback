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

#ifndef BRLTTY_INCLUDED_BITMASK
#define BRLTTY_INCLUDED_BITMASK

#include "prologue.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* These macros are meant for internal use only. */
#define BITMASK_ELEMENT_SIZE(element) (sizeof(element) * 8)
#define BITMASK_INDEX(bit,size) ((bit) / (size))
#define BITMASK_SHIFT(bit,size) ((bit) % (size))
#define BITMASK_ELEMENT_COUNT(bits,size) (BITMASK_INDEX((bits)-1, (size)) + 1)
#define BITMASK_ELEMENT(name,bit) ((name)[BITMASK_INDEX((bit), BITMASK_ELEMENT_SIZE((name)[0]))])
#define BITMASK_BIT(name,bit) (UINTMAX_C(1) << BITMASK_SHIFT((bit), BITMASK_ELEMENT_SIZE((name)[0])))

/* These macros are for public use. */
#define BITMASK(name,bits,type) unsigned type name[BITMASK_ELEMENT_COUNT((bits), BITMASK_ELEMENT_SIZE(type))]
#define BITMASK_SIZE(name) BITMASK_ELEMENT_SIZE((name))
#define BITMASK_ZERO(name) memset((name), 0, sizeof(name))
#define BITMASK_CLEAR(name,bit) (BITMASK_ELEMENT((name), (bit)) &= ~BITMASK_BIT((name), (bit)))
#define BITMASK_SET(name,bit) (BITMASK_ELEMENT((name), (bit)) |= BITMASK_BIT((name), (bit)))
#define BITMASK_TEST(name,bit) (BITMASK_ELEMENT((name), (bit)) & BITMASK_BIT((name), (bit)))

static inline unsigned char
popcount (unsigned int bits) {
#ifdef HAVE_BUILTIN_POPCOUNT
  return __builtin_popcount(bits);
#else /* __builtin_popcount */
  unsigned char count = 0;

  while (bits) {
    if (bits & 0X1) count += 1;
    bits >>= 1;
  }

  return count;
#endif /* __builtin_popcount */
}

#define BITMASK_COUNT(name,variable) \
unsigned int variable = 0; \
for (int i=0; i<ARRAY_COUNT(name); i+=1) variable += popcount(name[i]);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BITMASK */
