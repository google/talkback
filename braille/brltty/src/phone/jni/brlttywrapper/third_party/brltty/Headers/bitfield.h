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

#ifndef BRLTTY_INCLUDED_BITFIELD
#define BRLTTY_INCLUDED_BITFIELD

#include "prologue.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define HIGH_NIBBLE(byte) ((byte) & 0XF0)
#define LOW_NIBBLE(byte) ((byte) & 0XF)

static inline void
swapBytes (unsigned char *byte1, unsigned char *byte2) {
  unsigned char byte = *byte1;
  *byte1 = *byte2;
  *byte2 = byte;
}

typedef union {
  uint8_t bytes[0];
  uint16_t u16;
  uint32_t u32;
  uint64_t u64;
} BytesOverlay;

#define GET_ENDIAN_FUNCTION(which, bits) \
  static inline uint##bits##_t get##which##Endian##bits (uint##bits##_t from)

#define PUT_ENDIAN_FUNCTION(which, bits) \
  static inline void put##which##Endian##bits (uint##bits##_t *to, uint##bits##_t from)

#define DEFINE_PHYSICAL_ENDIAN_FUNCTIONS(bits) \
  GET_ENDIAN_FUNCTION(Native, bits) { \
    return from; \
  } \
  \
  GET_ENDIAN_FUNCTION(Other, bits) { \
    BytesOverlay overlay = {.u##bits = from}; \
    uint8_t *first = overlay.bytes; \
    uint8_t *second = first + (bits / 8); \
    \
    do { \
      swapBytes(first++, --second); \
    } while (first != second); \
    \
    return overlay.u##bits; \
  } \
  \
  PUT_ENDIAN_FUNCTION(Native, bits) { \
    *to = getNativeEndian##bits(from); \
  } \
  \
  PUT_ENDIAN_FUNCTION(Other, bits) { \
    *to = getOtherEndian##bits(from); \
  }

DEFINE_PHYSICAL_ENDIAN_FUNCTIONS(16)
DEFINE_PHYSICAL_ENDIAN_FUNCTIONS(32)
DEFINE_PHYSICAL_ENDIAN_FUNCTIONS(64)

#define DEFINE_ENDIAN_FUNCTIONS_FOR_BITS(bits, logical, physical) \
  GET_ENDIAN_FUNCTION(logical, bits) { \
    return get##physical##Endian##bits(from); \
  } \
  \
  PUT_ENDIAN_FUNCTION(logical, bits) { \
    put##physical##Endian##bits(to, from); \
  }

#define DEFINE_ENDIAN_FUNCTIONS(logical, physical) \
  DEFINE_ENDIAN_FUNCTIONS_FOR_BITS(16, logical, physical) \
  DEFINE_ENDIAN_FUNCTIONS_FOR_BITS(32, logical, physical) \
  DEFINE_ENDIAN_FUNCTIONS_FOR_BITS(64, logical, physical)

#ifdef WORDS_BIGENDIAN
  DEFINE_ENDIAN_FUNCTIONS(Little, Other)
  DEFINE_ENDIAN_FUNCTIONS(Big, Native)
#else /* WORDS_BIGENDIAN */
  DEFINE_ENDIAN_FUNCTIONS(Little, Native)
  DEFINE_ENDIAN_FUNCTIONS(Big, Other)
#endif /* WORDS_BIGENDIAN */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BITFIELD */
