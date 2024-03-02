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

#ifndef BRLTTY_INCLUDED_CRC_PROPERTIES
#define BRLTTY_INCLUDED_CRC_PROPERTIES

#include "crc_definitions.h"
#include "crc_algorithms.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define CRC_BYTE_WIDTH 8
#define CRC_BYTE_INDEXED_TABLE_SIZE (UINT8_MAX + 1)

extern crc_t crcMostSignificantBit (unsigned int width);
extern crc_t crcReflectBits (crc_t fromValue, unsigned int width);
extern void crcReflectByte (uint8_t *byte);
extern void crcReflectValue (crc_t *value, const CRCAlgorithm *algorithm);

typedef struct {
  unsigned int byteShift; // the bit offset of the high-order byte of the value
  crc_t mostSignificantBit; // the most significant bit of the value
  crc_t valueMask; // the mask for removing overflow bits in the value
  const uint8_t *dataTranslationTable; // for optimizing data reflection

  // for preevaluating a common calculation on each data byte
  crc_t remainderCache[CRC_BYTE_INDEXED_TABLE_SIZE];
} CRCProperties;

extern void crcMakeProperties (
  CRCProperties *properties,
  const CRCAlgorithm *algorithm
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CRC_PROPERTIES */
